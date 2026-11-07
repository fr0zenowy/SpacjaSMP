package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Pasywne efekty legendarnych elementow ekwipunku (np. Dynia Bossa).
 */
public class LegendaryArmorListener implements Listener {

    private static final int REFRESH_TICKS = 15;

    private final ZamrozeniePlugin plugin;
    private final LegendaryItems legendary;
    private BukkitTask refreshTask;

    public LegendaryArmorListener(ZamrozeniePlugin plugin, LegendaryItems legendary) {
        this.plugin = plugin;
        this.legendary = legendary;
    }

    public void start() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                refreshBossPumpkin(player);
            }
        }, REFRESH_TICKS, REFRESH_TICKS);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshBossPumpkin(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // efekt sam wygasnie po zdjeciu helmu
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshBossPumpkin(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> refreshBossPumpkin(player));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> refreshBossPumpkin(player));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> refreshBossPumpkin(player));
    }

    private void refreshBossPumpkin(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (LegendaryItems.BOSS_PUMPKIN.equals(legendary.getId(helmet))) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE, 40, 0, false, false, true));
        }
    }
}
