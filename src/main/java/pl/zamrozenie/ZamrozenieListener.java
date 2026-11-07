package pl.zamrozenie;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

public class ZamrozenieListener implements Listener {

    private final ZamrozenieManager manager;

    public ZamrozenieListener(ZamrozenieManager manager) {
        this.manager = manager;
    }

    private boolean isFrozenPlayer(Player player) {
        return manager.isFrozen();
    }

    // ────────────────────────────── Ruch ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!manager.isFrozen()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location stay = from.clone();
            stay.setYaw(to.getYaw());
            stay.setPitch(to.getPitch());
            event.setTo(stay);
        }
    }

    // ────────────────────────────── Komendy ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!manager.isFrozen()) return;

        Player player = event.getPlayer();

        if (player.hasPermission("zamrozenie.use")) {
            String msg = event.getMessage().toLowerCase();
            if (msg.startsWith("/zamrozenie") || msg.startsWith("/zamrożenie") || msg.startsWith("/wysrodkuj")) {
                return;
            }
        }

        event.setCancelled(true);
        player.sendMessage(org.bukkit.ChatColor.RED + "Nie mozesz uzywac komend podczas zamrozenia serwera.");
    }

    // ────────────────────────────── Niszczenie bloków ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (!manager.isFrozen()) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!manager.isFrozen()) return;
        event.setCancelled(true);
    }

    // ────────────────────────────── Stawianie bloków ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!manager.isFrozen()) return;
        event.setCancelled(true);
    }

    // ────────────────────────────── Interakcje ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!manager.isFrozen()) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!manager.isFrozen()) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!manager.isFrozen()) return;
        event.setCancelled(true);
    }

    // ────────────────────────────── Przedmioty ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!manager.isFrozen()) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!manager.isFrozen()) return;
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (!manager.isFrozen()) return;
        event.setCancelled(true);
    }

    // ────────────────────────────── Ekwipunek ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!manager.isFrozen()) return;
        if (event.getWhoClicked() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!manager.isFrozen()) return;
        if (event.getWhoClicked() instanceof Player) {
            event.setCancelled(true);
        }
    }

    // ────────────────────────────── Jedzenie ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (!manager.isFrozen()) return;
        event.setCancelled(true);
    }

    // ────────────────────────────── Atakowanie ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!manager.isFrozen()) return;
        if (event.getDamager() instanceof Player) {
            event.setCancelled(true);
        }
    }

    // ────────────────────────────── Wieszaki / Ramki ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!manager.isFrozen()) return;
        if (event.getRemover() instanceof Player) {
            event.setCancelled(true);
        }
    }

    // ────────────────────────────── Pojazdy ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!manager.isFrozen()) return;
        if (event.getAttacker() instanceof Player) {
            event.setCancelled(true);
        }
    }

    // ────────────────────────────── Wiadra ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!manager.isFrozen()) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        if (!manager.isFrozen()) return;
        event.setCancelled(true);
    }

    // ────────────────────────────── Obrażenia graczy ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!manager.isFrozen()) return;
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    // ────────────────────────────── Nowe moby podczas zamrożenia ──────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!manager.isFrozen()) return;
        if (event.isCancelled()) return;
        if (event.getEntity() instanceof Mob mob) {
            org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.Bukkit.getPluginManager().getPlugin("Zamrozenie"),
                () -> mob.setAI(false)
            );
        }
    }

    // ────────────────────────────── Wejście na serwer ──────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!manager.isFrozen()) return;
        manager.sendTitleToPlayer(event.getPlayer());
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            org.bukkit.Bukkit.getPluginManager().getPlugin("Zamrozenie"),
            () -> manager.sendTitleToPlayer(event.getPlayer()),
            5L
        );
    }
}
