package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cooldowny broni:
 *  - MACE  : 45s po trafieniu (vanilla overlay).
 *  - SPEAR : 20s po obrazeniach / puszczeniu naladowanego PPM.
 *  - SPEAR + Lunge I/II/III : po LPM Lunge znika z TEJ wloczni na 15s,
 *    potem wraca tylko jesli gracz nadal ja ma. Wyrzucona i nie podniesiona
 *    przed koncem CD = Lunge ginie na amen.
 */
public class WeaponCooldownListener implements Listener {

    public static final int MACE_COOLDOWN_TICKS = 45 * 20;
    public static final int SPEAR_COOLDOWN_TICKS = 20 * 20;
    public static final int SPEAR_LUNGE_CLICK_TICKS = 15 * 20;

    public static final List<Material> SPEARS = Arrays.stream(Material.values())
            .filter(m -> !m.isLegacy() && isSpear(m))
            .collect(Collectors.toList());

    public static boolean isSpear(Material m) {
        if (m == null || m.isLegacy()) return false;
        String name = m.name();
        return name.equals("SPEAR") || name.endsWith("_SPEAR");
    }

    public static boolean isSpear(ItemStack item) {
        return item != null && isSpear(item.getType());
    }

    private static Enchantment lungeEnchant() {
        return Enchantment.getByKey(NamespacedKey.minecraft("lunge"));
    }

    public static int lungeLevel(ItemStack item) {
        Enchantment lunge = lungeEnchant();
        if (lunge == null || !isSpear(item)) return 0;
        int level = item.getEnchantmentLevel(lunge);
        return (level >= 1 && level <= 3) ? level : 0;
    }

    private static int spearCooldown(Player p) {
        int max = 0;
        for (Material m : SPEARS) max = Math.max(max, p.getCooldown(m));
        return max;
    }

    private final Plugin plugin;
    private final NamespacedKey tokenKey;
    private final NamespacedKey savedLevelKey;
    private final Map<UUID, Long> spearCharging = new HashMap<>();
    private final Map<UUID, Long> lastNotice = new HashMap<>();
    /** token itemu -> oczekujace przywrocenie Lunge */
    private final Map<UUID, PendingLunge> pendingLunge = new HashMap<>();
    private BukkitTask task;
    private long tickCounter = 0;

    public WeaponCooldownListener(Plugin plugin) {
        this.plugin = plugin;
        this.tokenKey = new NamespacedKey(plugin, "lunge_cd_token");
        this.savedLevelKey = new NamespacedKey(plugin, "lunge_cd_level");
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        spearCharging.clear();
        pendingLunge.clear();
    }

    // ─── DAMAGE ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        Entity damager = event.getDamager();
        if (!(damager instanceof Player attacker)) return;

        ItemStack hand = attacker.getInventory().getItemInMainHand();
        Material type = hand.getType();

        if (type == Material.MACE) {
            if (attacker.getCooldown(Material.MACE) > 0) {
                event.setCancelled(true);
                notice(attacker, "Mace", attacker.getCooldown(Material.MACE));
                return;
            }
            attacker.setCooldown(Material.MACE, MACE_COOLDOWN_TICKS);
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.6f);
            return;
        }

        if (!isSpear(type)) return;

        if (lungeLevel(hand) >= 1) {
            startLungeCooldown(attacker, EquipmentSlot.HAND);
            return;
        }
        if (hasPendingToken(hand)) return;

        if (spearCooldown(attacker) > 0) {
            event.setCancelled(true);
            notice(attacker, "Spear", spearCooldown(attacker));
            return;
        }
        applySpearCooldown(attacker);
    }

    // ─── LPM / PPM ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!isSpear(item)) return;
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (lungeLevel(item) >= 1) startLungeCooldown(player, event.getHand());
            return;
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (lungeLevel(item) >= 1 || hasPendingToken(item)) return;

        if (spearCooldown(player) > 0) {
            event.setCancelled(true);
            notice(player, "Spear", spearCooldown(player));
            return;
        }
        spearCharging.put(player.getUniqueId(), tickCounter);
    }

    // ─── LUNGE: zdejmij / przywroc ─────────────────────────────────────────────

    private void startLungeCooldown(Player player, EquipmentSlot slot) {
        ItemStack item = slot == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        int level = lungeLevel(item);
        if (level < 1) return;
        Enchantment lunge = lungeEnchant();
        if (lunge == null) return;

        UUID token = UUID.randomUUID();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.removeEnchant(lunge);
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.STRING, token.toString());
        meta.getPersistentDataContainer().set(savedLevelKey, PersistentDataType.INTEGER, level);
        item.setItemMeta(meta);
        if (slot == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(item);
        else player.getInventory().setItemInMainHand(item);

        pendingLunge.put(token, new PendingLunge(
                player.getUniqueId(), token, level, System.currentTimeMillis() + SPEAR_LUNGE_CLICK_TICKS * 50L));
        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 0.35f, 1.4f);
    }

    private void tick() {
        tickCounter++;
        tickCharging();
        tickLungeRestore();
    }

    private void tickCharging() {
        if (spearCharging.isEmpty()) return;
        long now = tickCounter;
        Iterator<Map.Entry<UUID, Long>> it = spearCharging.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) { it.remove(); continue; }
            boolean holding = isSpear(p.getInventory().getItemInMainHand())
                    || isSpear(p.getInventory().getItemInOffHand());
            if (!holding) { it.remove(); continue; }
            if (p.isHandRaised()) continue;
            long held = now - e.getValue();
            it.remove();
            if (held >= 2 && spearCooldown(p) == 0) applySpearCooldown(p);
        }
    }

    private void tickLungeRestore() {
        if (pendingLunge.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<UUID> done = new ArrayList<>();
        for (PendingLunge pending : pendingLunge.values()) {
            if (now < pending.restoreAtMs) continue;
            done.add(pending.token);
            Player owner = Bukkit.getPlayer(pending.owner);
            if (owner != null && owner.isOnline() && restoreLungeInInventory(owner, pending)) {
                continue;
            }
            // nie ma u gracza — Lunge ginie, czyscymy znacznik z lezacych dropow
            stripExpiredTokenFromWorld(pending.token);
        }
        for (UUID token : done) pendingLunge.remove(token);
    }

    private boolean restoreLungeInInventory(Player player, PendingLunge pending) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        boolean found = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (!matchesToken(it, pending.token)) continue;
            contents[i] = withLungeRestored(it, pending.level);
            found = true;
        }
        if (found) inv.setContents(contents);

        ItemStack off = inv.getItemInOffHand();
        if (matchesToken(off, pending.token)) {
            inv.setItemInOffHand(withLungeRestored(off, pending.level));
            found = true;
        }
        ItemStack cursor = player.getOpenInventory().getCursor();
        if (matchesToken(cursor, pending.token)) {
            player.getOpenInventory().setCursor(withLungeRestored(cursor, pending.level));
            found = true;
        }
        return found;
    }

    private ItemStack withLungeRestored(ItemStack item, int level) {
        ItemStack out = item.clone();
        ItemMeta meta = out.getItemMeta();
        if (meta == null) return out;
        Enchantment lunge = lungeEnchant();
        if (lunge != null) meta.addEnchant(lunge, level, true);
        meta.getPersistentDataContainer().remove(tokenKey);
        meta.getPersistentDataContainer().remove(savedLevelKey);
        out.setItemMeta(meta);
        return out;
    }

    private void stripExpiredTokenFromWorld(UUID token) {
        for (var world : Bukkit.getWorlds()) {
            for (Item drop : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = drop.getItemStack();
                if (!matchesToken(stack, token)) continue;
                ItemMeta meta = stack.getItemMeta();
                if (meta == null) continue;
                meta.getPersistentDataContainer().remove(tokenKey);
                meta.getPersistentDataContainer().remove(savedLevelKey);
                stack.setItemMeta(meta);
                drop.setItemStack(stack);
            }
        }
    }

    private boolean hasPendingToken(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String raw = item.getItemMeta().getPersistentDataContainer().get(tokenKey, PersistentDataType.STRING);
        if (raw == null) return false;
        try {
            return pendingLunge.containsKey(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean matchesToken(ItemStack item, UUID token) {
        if (item == null || !item.hasItemMeta()) return false;
        String raw = item.getItemMeta().getPersistentDataContainer().get(tokenKey, PersistentDataType.STRING);
        return raw != null && token.toString().equals(raw);
    }

    /** Podniesiony po wygasnieciu CD — Lunge juz nie wraca, tylko czyscimy znacznik. */
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (!isSpear(stack) || !stack.hasItemMeta()) return;
        String raw = stack.getItemMeta().getPersistentDataContainer().get(tokenKey, PersistentDataType.STRING);
        if (raw == null) return;
        UUID token;
        try {
            token = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (pendingLunge.containsKey(token)) return;
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().remove(tokenKey);
        meta.getPersistentDataContainer().remove(savedLevelKey);
        stack.setItemMeta(meta);
        event.getItem().setItemStack(stack);
    }

    private void applySpearCooldown(Player player) {
        if (spearCooldown(player) > 0) return;
        for (Material m : SPEARS) player.setCooldown(m, SPEAR_COOLDOWN_TICKS);
        spearCharging.remove(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 0.35f, 1.4f);
    }

    private void notice(Player player, String weapon, int ticksLeft) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUniqueId());
        if (last != null && now - last < 1000) return;
        lastNotice.put(player.getUniqueId(), now);
        int sec = Math.max(1, (int) Math.ceil(ticksLeft / 20.0));
        player.sendMessage(ChatColor.DARK_GRAY + "\u00BB " + ChatColor.RED + weapon
                + ChatColor.GRAY + " jest na cooldownie jeszcze " + ChatColor.WHITE + sec + "s" + ChatColor.GRAY + ".");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        spearCharging.remove(event.getPlayer().getUniqueId());
        lastNotice.remove(event.getPlayer().getUniqueId());
    }

    private static final class PendingLunge {
        final UUID owner;
        final UUID token;
        final int level;
        final long restoreAtMs;

        PendingLunge(UUID owner, UUID token, int level, long restoreAtMs) {
            this.owner = owner;
            this.token = token;
            this.level = level;
            this.restoreAtMs = restoreAtMs;
        }
    }
}
