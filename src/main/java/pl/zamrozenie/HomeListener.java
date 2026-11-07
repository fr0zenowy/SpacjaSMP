package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class HomeListener implements Listener {

    private final HomeManager homeManager;

    public HomeListener(HomeManager homeManager) {
        this.homeManager = homeManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Identyfikacja po holderze zamiast tytułu — niezawodne w każdej wersji
        if (!(event.getInventory().getHolder() instanceof HomeInventoryHolder)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        int slot = switch (event.getSlot()) {
            case HomeInventoryHolder.HOME_1_SLOT, HomeInventoryHolder.SET_1_SLOT, HomeInventoryHolder.DELETE_1_SLOT -> 1;
            case HomeInventoryHolder.HOME_2_SLOT, HomeInventoryHolder.SET_2_SLOT, HomeInventoryHolder.DELETE_2_SLOT -> 2;
            default -> -1;
        };
        if (slot == -1) return;

        if (event.getSlot() == HomeInventoryHolder.HOME_1_SLOT || event.getSlot() == HomeInventoryHolder.HOME_2_SLOT) {
            player.closeInventory();
            homeManager.teleportToHome(player, slot);
        } else if (event.getSlot() == HomeInventoryHolder.SET_1_SLOT || event.getSlot() == HomeInventoryHolder.SET_2_SLOT) {
            homeManager.setHome(player, slot, player.getLocation());
            player.sendMessage(ChatColor.GREEN + "✔ Home " + slot + " ustawiony.");
            HomeCommand.openGui(player);
        } else if (event.getSlot() == HomeInventoryHolder.DELETE_1_SLOT || event.getSlot() == HomeInventoryHolder.DELETE_2_SLOT) {
            if (!homeManager.hasHome(player, slot)) {
                player.sendMessage(ChatColor.RED + "Nie masz ustawionego home " + slot + ".");
                return;
            }
            homeManager.deleteHome(player, slot);
            player.sendMessage(ChatColor.YELLOW + "Home " + slot + " został usunięty.");
            HomeCommand.openGui(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        homeManager.cancelWarmup(event.getPlayer().getUniqueId());
    }
}
