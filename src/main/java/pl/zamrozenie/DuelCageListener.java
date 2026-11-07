package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

public class DuelCageListener implements Listener {

    private final DuelCageManager duels;

    public DuelCageListener(DuelCageManager duels) {
        this.duels = duels;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (duels.isInDuel(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (duels.isInDuel(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) return;

        boolean attackerInDuel = duels.isInDuel(attacker);
        boolean victimInDuel = duels.isInDuel(victim);
        if (!attackerInDuel && !victimInDuel) return;

        if (!duels.canPlayersFight(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!duels.isInDuel(victim)) return;

        UUID killer = null;
        Player k = victim.getKiller();
        if (k != null && duels.isInDuel(k)) killer = k.getUniqueId();

        duels.endDuelForPlayer(victim.getUniqueId(), killer);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (duels.isInDuel(event.getPlayer())) {
            duels.endDuelForPlayer(event.getPlayer().getUniqueId(), null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!duels.isInDuel(event.getPlayer())) return;
        if (event.getItem() == null || event.getItem().getType() != Material.ENDER_PEARL) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "Nie mozesz rzucac perel w klatce!");
    }

    /** Blokada ucieczki perlem / chorem z klatki. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (!duels.isInDuel(p)) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Nie mozesz uciec z klatki!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.isGliding()) return;
        if (!duels.isInDuel(player)) return;

        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "Nie mozesz latac Elytra w klatce!");
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
