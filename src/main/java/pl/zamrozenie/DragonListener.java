package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DragonListener implements Listener {

    private final DragonManager dragonManager;

    public DragonListener(DragonManager dragonManager) {
        this.dragonManager = dragonManager;
    }

    /**
     * Przechwytuje każde trafienie smoka.
     * Zerujemy prawdziwy damage (smok zostaje przy pełnym HP → AI działa normalnie)
     * i śledzimy sumę damage plugin-side. Gdy suma osiągnie próg, zabijamy smoka ręcznie.
     * 
     * BLOKUJE: 
     * - Vanilla Mace (Material.MACE)
     * - Vanilla Trident (Material.TRIDENT) - melee i rzucony
     * - Spear projectiles (wykrywane przez EntityType lub display name)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) return;

        EnderDragon dragon = dragonManager.getDragon();
        if (dragon == null || !dragon.equals(event.getEntity())) return;

        Player attacker = null;
        Entity damager = event.getDamager();
        
        // Przypadek 1: Bezpośredni atak gracza (melee)
        if (damager instanceof Player p) {
            attacker = p;
            
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            Material weaponType = weapon.getType();
            
            // BLOKADA: Mace
            if (weaponType == Material.MACE) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "Nie mozesz uzywac mace przeciwko Smokowi Kresu!");
                return;
            }
            
            // BLOKADA: Trident
            if (weaponType == Material.TRIDENT) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "Nie mozesz uzywac tridentu przeciwko Smokowi Kresu!");
                return;
            }
            
            // BLOKADA: Wszystkie typy Spearów (sprawdzamy przez nazwę Material Type)
            // Minecraft 1.21+ dodał speary, ale Spigot API może ich jeszcze nie mieć w enum
            String materialName = weaponType.name();
            if (materialName.contains("SPEAR")) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "Nie mozesz uzywac speara przeciwko Smokowi Kresu!");
                return;
            }
            
            // BLOKADA: Fallback - sprawdź display name (dla custom itemów)
            if (weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName()) {
                String displayName = ChatColor.stripColor(weapon.getItemMeta().getDisplayName()).toLowerCase();
                if (displayName.contains("spear")) {
                    event.setCancelled(true);
                    attacker.sendMessage(ChatColor.RED + "Nie mozesz uzywac speara przeciwko Smokowi Kresu!");
                    return;
                }
            }
        }
        // Przypadek 2: Projectile (trident rzucony, może też spear projectile)
        else if (damager instanceof Trident trident) {
            if (trident.getShooter() instanceof Player p) {
                event.setCancelled(true);
                p.sendMessage(ChatColor.RED + "Nie mozesz uzywac tridentu przeciwko Smokowi Kresu!");
                return;
            }
        }
        // Przypadek 3: Inne projectile które mogą być spearami
        else if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player p) {
                attacker = p;
                // Sprawdź czy to spear projectile (według entity type name lub custom name)
                String entityName = projectile.getType().name().toLowerCase();
                if (entityName.contains("spear")) {
                    event.setCancelled(true);
                    p.sendMessage(ChatColor.RED + "Nie mozesz uzywac speara przeciwko Smokowi Kresu!");
                    return;
                }
            }
        }
        
        if (attacker == null) return;

        double dmg = event.getFinalDamage();

        // Zeruj prawdziwy damage — smok nie traci HP, jego AI pozostaje sprawne
        event.setDamage(0);

        dragonManager.registerDamage(attacker, dmg);
    }

    /** Obsługuje śmierć smoka (wywoływaną przez plugin gdy suma damage osiągnie próg). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!dragonManager.isActive()) return;
        if (!(event.getEntity() instanceof EnderDragon)) return;

        EnderDragon dragon = dragonManager.getDragon();
        if (dragon == null || !dragon.equals(event.getEntity())) return;

        event.getDrops().clear();
        event.setDroppedExp(0);
        DropManager dm = dragonManager.getPlugin().getDropManager();
        if (dm != null) dm.addToDeath(DropSource.WLADCA_ENDU, event);

        dragonManager.onDragonDeath();
    }

    /**
     * Blokuje aktywację vanilla Smoka Kresu poprzez umieszczanie kryształów Endu
     * podczas gdy event smoka pluginu jest aktywny.
     * 
     * Vanilla mechnika: 4 kryształy wokół portalu → respawn smoka
     * Gdy nasz event jest aktywny, gracze NIE MOGĄ przywołać vanilla smoka.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderCrystalPlace(PlayerInteractEvent event) {
        if (!dragonManager.isActive()) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Sprawdź czy gracz próbuje umieścić kryształ Endu
        if (item != null && item.getType() == Material.END_CRYSTAL) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + 
                "[SMOK] " + ChatColor.RED + 
                "Nie mozesz aktywowac vanilla smoka podczas eventu Smoka Kresu!");
            player.sendMessage(ChatColor.GRAY + 
                "Poczekaj az event sie zakonczy.");
        }
    }
}
