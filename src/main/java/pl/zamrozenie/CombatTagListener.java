package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

public class CombatTagListener implements Listener {

    private final CombatTagManager combat;

    public CombatTagListener(CombatTagManager combat) {
        this.combat = combat;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getFinalDamage() <= 0) return;
        Player attacker = attacker(event);
        if (attacker == null || attacker.equals(victim)) return;
        combat.tagPvP(attacker, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearl(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        combat.tag(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRiptideTry(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!combat.isTagged(player)) return;
        ItemStack item = event.getItem();
        if (!hasRiptide(item)) return;
        if (player.getCooldown(Material.TRIDENT) <= 0) return;
        event.setCancelled(true);
        int sec = Math.max(1, (int) Math.ceil(player.getCooldown(Material.TRIDENT) / 20.0));
        player.sendMessage(ChatColor.RED + "Riptide jest na cooldownie jeszcze " + ChatColor.WHITE + sec + "s"
                + ChatColor.RED + ".");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        if (!combat.isTagged(player)) return;
        int ticks = combat.riptideCooldownTicks();
        if (ticks < 1) return;
        player.setCooldown(Material.TRIDENT, ticks);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (combat.isCombatLogKill(player.getUniqueId())) {
            event.setKeepInventory(false);
            event.getDrops().clear();
            Player killer = player.getKiller();
            if (killer != null) {
                event.setDeathMessage(ChatColor.RED + player.getName() + ChatColor.GRAY
                        + " wylogował się podczas walki z " + ChatColor.WHITE + killer.getName() + ChatColor.GRAY + ".");
            } else {
                event.setDeathMessage(ChatColor.RED + player.getName() + ChatColor.GRAY
                        + " wylogował się podczas walki.");
            }
        }
        combat.untag(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        combat.punishLogout(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!combat.shouldForceRespawn(player.getUniqueId())) return;
        Location spawn = combat.respawnLocation(player);
        combat.consumeForcedRespawn(player.getUniqueId());
        player.teleport(spawn);
        combat.getPlugin().getServer().getScheduler().runTask(combat.getPlugin(), () -> {
            if (player.isOnline()) player.teleport(spawn);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!combat.isTagged(player)) return;
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "Nie możesz używać komend podczas walki!");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnderChestClick(PlayerInteractEvent event) {
        if (!combat.isTagged(event.getPlayer())) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "Nie możesz otwierać ender chesta podczas walki!");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!combat.isTagged(player)) return;
        InventoryType type = event.getInventory().getType();
        if (type != InventoryType.ENDER_CHEST && type != InventoryType.SHULKER_BOX) return;
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "Nie możesz tego otwierać podczas walki!");
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static boolean hasRiptide(ItemStack item) {
        if (item == null || item.getType() != Material.TRIDENT) return false;
        Enchantment riptide = Enchantment.getByKey(NamespacedKey.minecraft("riptide"));
        return riptide != null && item.getEnchantmentLevel(riptide) > 0;
    }
}
