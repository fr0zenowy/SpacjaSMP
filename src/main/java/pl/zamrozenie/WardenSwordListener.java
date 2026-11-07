package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.NamespacedKey;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WardenSwordListener implements Listener {

    private static final int MAX_CHARGE = 100;
    private static final int CHARGE_PER_HIT = 5;
    private static final double BOOM_DAMAGE = 10.0;
    private static final double BOOM_RANGE = 24.0;
    private static final double BOOM_RADIUS = 1.35;
    private static final float CHARGED_SWING = 0.9f;
    private static final int BAR_LEN = 10;

    private final ZamrozeniePlugin plugin;
    private final LegendaryItems items;
    private final NamespacedKey chargeKey;
    private final Set<UUID> booming = new HashSet<>();
    private final Map<UUID, Float> swingCharge = new ConcurrentHashMap<>();

    public WardenSwordListener(ZamrozeniePlugin plugin, LegendaryItems items) {
        this.plugin = plugin;
        this.items = items;
        this.chargeKey = new NamespacedKey(plugin, "warden_charge");
        startActionBar();
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        if (!isWarden(player.getInventory().getItemInMainHand())) return;
        swingCharge.put(player.getUniqueId(), player.getAttackCooldown());
    }

    @EventHandler
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!isWarden(player.getInventory().getItemInMainHand())) return;
        swingCharge.put(player.getUniqueId(), player.getAttackCooldown());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (booming.contains(attacker.getUniqueId())) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (victim instanceof ArmorStand) return;
        if (event.getDamage() <= 0) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!isWarden(weapon)) return;
        if (!wasChargedSwing(attacker)) return;

        int charge = getCharge(weapon);
        if (charge >= MAX_CHARGE) return;
        int next = Math.min(MAX_CHARGE, charge + CHARGE_PER_HIT);
        setCharge(weapon, next);

        Location loc = victim.getLocation().add(0, 1, 0);
        World world = victim.getWorld();
        world.spawnParticle(Particle.SCULK_SOUL, loc, 4, 0.2, 0.3, 0.2, 0.01);
        if (next >= MAX_CHARGE) {
            world.spawnParticle(Particle.SONIC_BOOM, loc, 1, 0, 0, 0, 0);
            world.playSound(attacker.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.7f, 1.4f);
            attacker.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "RAGE "
                    + ChatColor.DARK_AQUA + "— Shift + PPM, aby wystrzelić sonic boom!");
        }
    }

    @EventHandler
    public void onRage(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isWarden(weapon)) return;

        event.setCancelled(true);
        int charge = getCharge(weapon);
        if (charge < MAX_CHARGE) {
            player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.6f, 0.6f);
            return;
        }

        setCharge(weapon, 0);
        fireSonicBoom(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10 * 20, 1, false, true, true));
    }

    private void fireSonicBoom(Player player) {
        World world = player.getWorld();
        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        booming.add(player.getUniqueId());

        world.playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.1f, 1.0f);
        world.playSound(start, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.5f, 1.6f);
        player.getWorld().spawnParticle(Particle.SONIC_BOOM, start.clone().add(dir), 1, 0, 0, 0, 0);

        Set<UUID> hit = new HashSet<>();
        try {
            for (double d = 1.0; d <= BOOM_RANGE; d += 0.55) {
                Location point = start.clone().add(dir.clone().multiply(d));
                Block block = point.getBlock();
                if (block.getType().isSolid() && !block.isPassable()) break;

                if ((int) (d * 10) % 11 == 0) {
                    world.spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
                }
                world.spawnParticle(Particle.SCULK_SOUL, point, 2, 0.08, 0.08, 0.08, 0.002);
                world.spawnParticle(Particle.SCULK_CHARGE_POP, point, 1, 0.05, 0.05, 0.05, 0);

                for (var entity : world.getNearbyEntities(point, BOOM_RADIUS, BOOM_RADIUS, BOOM_RADIUS)) {
                    if (!(entity instanceof LivingEntity living) || living instanceof ArmorStand) continue;
                    if (living.getUniqueId().equals(player.getUniqueId())) continue;
                    if (!hit.add(living.getUniqueId())) continue;
                    applyTrueDamage(living, player, point);
                }
            }
        } finally {
            plugin.getServer().getScheduler().runTask(plugin, () -> booming.remove(player.getUniqueId()));
        }
    }

    private void applyTrueDamage(LivingEntity victim, Player attacker, Location at) {
        DamageSource source = DamageSource.builder(DamageType.SONIC_BOOM)
                .withCausingEntity(attacker)
                .withDirectEntity(attacker)
                .withDamageLocation(at)
                .build();
        victim.damage(BOOM_DAMAGE, source);
        victim.getWorld().spawnParticle(Particle.SONIC_BOOM, victim.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WARDEN_HURT, 0.8f, 1.3f);
    }

    private void startActionBar() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (plugin.getCombatTag() != null && plugin.getCombatTag().isTagged(player)) continue;
                    ItemStack held = player.getInventory().getItemInMainHand();
                    if (!isWarden(held)) continue;
                    showBar(player, getCharge(held));
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private boolean wasChargedSwing(Player attacker) {
        Float stored = swingCharge.remove(attacker.getUniqueId());
        float strength = stored != null ? stored : attacker.getAttackCooldown();
        return strength >= CHARGED_SWING;
    }

    private void showBar(Player player, int charge) {
        int filled = Math.round(charge * BAR_LEN / (float) MAX_CHARGE);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < BAR_LEN; i++) {
            if (i < filled) {
                bar.append(charge >= MAX_CHARGE ? ChatColor.AQUA : ChatColor.DARK_AQUA).append("|");
            } else {
                bar.append(ChatColor.DARK_GRAY).append("|");
            }
        }

        String text;
        if (charge >= MAX_CHARGE) {
            text = ChatColor.AQUA + "" + ChatColor.BOLD + "⚡ " + charge + "% "
                    + ChatColor.DARK_GRAY + "[" + bar + ChatColor.DARK_GRAY + "]";
        } else {
            ChatColor pct = charge >= 75 ? ChatColor.AQUA : ChatColor.DARK_AQUA;
            text = pct + "⚡ " + charge + "% " + ChatColor.DARK_GRAY + "[" + bar + ChatColor.DARK_GRAY + "]";
        }
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(text));
    }

    private boolean isWarden(ItemStack item) {
        return LegendaryItems.WARDEN_SWORD.equals(items.getId(item));
    }

    private int getCharge(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer raw = item.getItemMeta().getPersistentDataContainer().get(chargeKey, PersistentDataType.INTEGER);
        if (raw == null) return 0;
        return Math.max(0, Math.min(MAX_CHARGE, raw));
    }

    private void setCharge(ItemStack item, int charge) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(chargeKey, PersistentDataType.INTEGER, charge);
        item.setItemMeta(meta);
    }
}
