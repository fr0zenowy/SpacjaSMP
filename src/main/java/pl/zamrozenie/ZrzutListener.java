package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ZrzutListener implements Listener {

    private final ZrzutManager manager;
    private final LegendaryItems legendary;
    private final DuelCageManager duels;

    /** Czas (millis) do ktorego gracz jest odporny na obrazenia z upadku po uzyciu mobilnosci. */
    private final Map<UUID, Long> noFallUntil = new HashMap<>();
    private final Map<UUID, Long> grappleCooldown = new HashMap<>();

    public ZrzutListener(ZrzutManager manager, LegendaryItems legendary, DuelCageManager duels) {
        this.manager = manager;
        this.legendary = legendary;
        this.duels = duels;
    }

    // ─── Interakcja ze skrzynia + legendarne PPM ─────────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Klik na skrzynie zrzutu
        if ((event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK)
                && event.getClickedBlock() != null
                && manager.isCrateBlock(event.getClickedBlock())) {
            event.setCancelled(true);
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getHand() == EquipmentSlot.HAND) {
                manager.handleCrateClick(player);
            }
            return;
        }

        // Legendarne zdolnosci na PPM (tylko reka glowna)
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        String id = legendary.getId(item);
        if (id == null) return;

        if (LegendaryItems.FURY.equals(id)) {
            event.setCancelled(true);
            useFury(player);
        }
    }

    private void actionBar(Player player, String msg) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    private void useFury(Player player) {
        if (player.getCooldown(Material.BLAZE_ROD) > 0) {
            actionBar(player, ChatColor.RED + "Furia Berserkera sie laduje...");
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 2, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2, false, true, true));
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1.2f);
        player.setCooldown(Material.BLAZE_ROD, LegendaryItems.FURY_CD_TICKS);
    }

    @EventHandler
    public void onRiftThornHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!LegendaryItems.RIFT_THORN.equals(legendary.getId(item))) return;

        if (attacker.getCooldown(Material.PRISMARINE_SHARD) > 0) {
            actionBar(attacker, ChatColor.RED + "Kolce Rozdarcia sie laduja...");
            return;
        }
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 0, false, true, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true, true));
        victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.05);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.4f);
        attacker.setCooldown(Material.PRISMARINE_SHARD, LegendaryItems.RIFT_THORN_CD_TICKS);
    }

    @EventHandler
    public void onSnareHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!LegendaryItems.SNARE.equals(legendary.getId(item))) return;

        if (attacker.getCooldown(Material.GHAST_TEAR) > 0) {
            actionBar(attacker, ChatColor.RED + "Klatka Pojedynku sie laduje...");
            return;
        }
        victim.getWorld().spawnParticle(Particle.PORTAL, victim.getLocation().add(0, 1, 0), 50, 0.5, 1, 0.5, 0.5);
        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1f, 1f);
        attacker.setCooldown(Material.GHAST_TEAR, LegendaryItems.SNARE_CD_TICKS);
        duels.startDuel(attacker, victim);
    }

    // ─── Hak z Lina (grapple) ─────────────────────────────────────────────────────────

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (!LegendaryItems.GRAPPLE.equals(legendary.getId(rod))) return;

        switch (event.getState()) {
            case IN_GROUND, REEL_IN, FAILED_ATTEMPT, CAUGHT_ENTITY -> {
                long now = System.currentTimeMillis();
                Long cd = grappleCooldown.get(player.getUniqueId());
                if (cd != null && cd > now) {
                    actionBar(player, ChatColor.RED + "Hak sie laduje...");
                    return;
                }
                Location hook = event.getHook().getLocation();
                Vector dir = hook.toVector().subtract(player.getLocation().toVector());
                double dist = dir.length();
                if (dist < 1.5) return;
                dir.normalize();
                double power = Math.min(dist * 0.18 + 0.5, 2.2);
                Vector v = dir.multiply(power);
                v.setY(Math.max(v.getY() + 0.2, 0.42));
                player.setVelocity(v);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1.2f);
                grappleCooldown.put(player.getUniqueId(), now + LegendaryItems.GRAPPLE_CD_TICKS * 50L);
                noFallUntil.put(player.getUniqueId(), now + 6000);
            }
            default -> {}
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Long until = noFallUntil.get(player.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    // ─── Blokada spearów podczas zrzutu ──────────────────────────────────────────────

    /**
     * Blokuje rzucanie spearów podczas timera zrzutu (15 minut countdown).
     * Zostawia zwykłe hitanie lewym przyciskiem myszy (melee).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpearThrow(ProjectileLaunchEvent event) {
        // Sprawdź czy zrzut jest aktywny (LOCKED lub OPEN)
        if (manager.getPhase() != ZrzutManager.Phase.LOCKED &&
            manager.getPhase() != ZrzutManager.Phase.OPEN) {
            return;
        }

        // Sprawdź czy to spear projectile
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        
        String entityName = event.getEntity().getType().name().toLowerCase();
        if (!entityName.contains("spear")) return;

        // Zablokuj rzucanie speara
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "Nie możesz używać spearów podczas zrzutu!");
    }

    /**
     * Blokuje aktywację spearów prawym przyciskiem myszy podczas zrzutu.
     * Sprawdza item w głównej ręce gracza, nie item na który patrzy.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpearRightClick(PlayerInteractEvent event) {
        // Sprawdź czy zrzut jest aktywny (LOCKED lub OPEN)
        if (manager.getPhase() != ZrzutManager.Phase.LOCKED &&
            manager.getPhase() != ZrzutManager.Phase.OPEN) {
            return;
        }

        // Tylko prawy przycisk myszy
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Tylko główna ręka
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        // Sprawdź item w ręce gracza, nie item na który patrzy
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;

        Material itemType = item.getType();

        // Sprawdź wszystkie możliwe typy spearów (Minecraft 1.21+)
        // NETHERITE_SPEAR, DIAMOND_SPEAR, IRON_SPEAR, GOLDEN_SPEAR, STONE_SPEAR, WOODEN_SPEAR
        if (itemType == Material.NETHERITE_SPEAR ||
            itemType == Material.DIAMOND_SPEAR ||
            itemType == Material.IRON_SPEAR ||
            itemType == Material.GOLDEN_SPEAR ||
            itemType == Material.STONE_SPEAR ||
            itemType == Material.WOODEN_SPEAR) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Nie możesz używać spearów podczas zrzutu!");
            return;
        }

        // Fallback - sprawdź przez nazwę Material (dla przyszłych typów)
        String materialName = itemType.name();
        if (materialName.contains("SPEAR")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Nie możesz używać spearów podczas zrzutu!");
            return;
        }

        // Fallback 2 - sprawdź display name (dla custom itemów)
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
            if (displayName.contains("spear")) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Nie możesz używać spearów podczas zrzutu!");
            }
        }
    }

    // ─── Zagadki (GUI) ───────────────────────────────────────────────────────────────

    @EventHandler
    public void onPuzzleClick(InventoryClickEvent event) {
        var holder = event.getInventory().getHolder();
        // GUI losowania legendy – zablokuj wszelkie klikniecia
        if (holder instanceof ZrzutSpinHolder) {
            event.setCancelled(true);
            return;
        }
        if (!(holder instanceof ZrzutPuzzleHolder puzzle)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
        manager.handlePuzzleClick(player, puzzle, event.getSlot(), event.getCurrentItem());
    }

    @EventHandler
    public void onPuzzleDrag(InventoryDragEvent event) {
        var holder = event.getInventory().getHolder();
        if (holder instanceof ZrzutPuzzleHolder || holder instanceof ZrzutSpinHolder) {
            event.setCancelled(true);
        }
    }

    /** Po zamknieciu skrzyni z lootem przenies reszte do ekwipunku (nadmiar na ziemie). */
    @EventHandler
    public void onLootClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ZrzutLootHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        for (ItemStack it : event.getInventory().getContents()) {
            if (it == null || it.getType() == Material.AIR) continue;
            var overflow = player.getInventory().addItem(it);
            for (ItemStack o : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), o);
            }
        }
        event.getInventory().clear();
    }

    // ─── Ochrona skrzyni ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (manager.isCrateBlock(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Nie mozesz zniszczyc skrzyni zrzutu.");
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(manager::isCrateBlock);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(manager::isCrateBlock);
    }

    // ─── Dolaczanie gracza ───────────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.restoreOnJoin(event.getPlayer());
        manager.addPlayerToBossBar(event.getPlayer());
        duels.handleJoin(event.getPlayer());
    }
}
