package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener dla GUI Smoka Kresu.
 * - Blokuje wszelkie interakcje (GUI jest read-only)
 * - Zarządza rotacją itemów w slocie 40 co 1 sekundę
 */
public class DragonGuiListener implements Listener {

    private final Plugin plugin;
    private final DragonManager dragonManager;
    
    // Mapa graczy oglądających GUI -> task rotacji
    private final Map<UUID, BukkitTask> rotationTasks = new HashMap<>();
    // Stan rotacji dla każdego gracza (true = Brzuszek Smoka, false = Smoczy Szpon)
    private final Map<UUID, Boolean> rotationState = new HashMap<>();

    public DragonGuiListener(Plugin plugin, DragonManager dragonManager) {
        this.plugin = plugin;
        this.dragonManager = dragonManager;
    }

    /**
     * Blokuje wszelkie kliknięcia w GUI Smoka (read-only).
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        if (event.getInventory().getHolder() instanceof DragonGuiHolder) {
            event.setCancelled(true); // Blokuj wszelkie interakcje
        }
    }

    /**
     * Rozpoczyna rotację itemów gdy gracz otwiera GUI Smoka.
     */
    @EventHandler
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        if (event.getInventory().getHolder() instanceof DragonGuiHolder) {
            Player player = (Player) event.getPlayer();
            UUID playerId = player.getUniqueId();
            
            // Ustaw początkowy stan (Brzuszek Smoka)
            rotationState.put(playerId, true);
            
            // Rozpocznij task rotacji co 1 sekundę (20 ticków)
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                InventoryView view = player.getOpenInventory();
                Inventory topInv = view.getTopInventory();
                
                // Sprawdź czy gracz nadal ma otwarte GUI Smoka
                if (!(topInv.getHolder() instanceof DragonGuiHolder)) {
                    stopRotation(playerId);
                    return;
                }
                
                // Przełącz stan
                boolean isDragonBelly = rotationState.getOrDefault(playerId, true);
                rotationState.put(playerId, !isDragonBelly);
                
                // Ustaw odpowiedni item w slocie 40
                if (isDragonBelly) {
                    // Zmień na Smoczy Szpon (Netherite Sword)
                    topInv.setItem(40, dragonManager.createDragonClaw());
                } else {
                    // Zmień na Brzuszek Smoka (Shulker Shell)
                    topInv.setItem(40, dragonManager.createDragonBelly());
                }
                
                player.updateInventory(); // Zaktualizuj widok gracza
                
            }, 20L, 20L); // Start po 1s, powtarzaj co 1s
            
            rotationTasks.put(playerId, task);
        }
    }

    /**
     * Zatrzymuje rotację gdy gracz zamyka GUI.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        if (event.getInventory().getHolder() instanceof DragonGuiHolder) {
            Player player = (Player) event.getPlayer();
            stopRotation(player.getUniqueId());
        }
    }

    /**
     * Zatrzymuje task rotacji dla danego gracza.
     */
    private void stopRotation(UUID playerId) {
        BukkitTask task = rotationTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        rotationState.remove(playerId);
    }

    /**
     * Zatrzymuje wszystkie aktywne rotacje (np. przy wyłączaniu pluginu).
     */
    public void stopAllRotations() {
        for (BukkitTask task : rotationTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        rotationTasks.clear();
        rotationState.clear();
    }
}