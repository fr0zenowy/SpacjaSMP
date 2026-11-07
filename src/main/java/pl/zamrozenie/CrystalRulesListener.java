package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Iterator;

public class CrystalRulesListener implements Listener {

    private static final double MINECART_TNT_MULTIPLIER = 0.8;

    public CrystalRulesListener(ZamrozeniePlugin plugin) {
        removeCrystalRecipes();
        plugin.getServer().getScheduler().runTask(plugin, this::removeExistingCrystals);
    }

    private void removeCrystalRecipes() {
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            ItemStack result = recipe.getResult();
            if (result != null && result.getType() == Material.END_CRYSTAL) {
                it.remove();
            }
        }
    }

    private void removeExistingCrystals() {
        for (World world : Bukkit.getWorlds()) {
            for (EnderCrystal crystal : world.getEntitiesByClass(EnderCrystal.class)) {
                crystal.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceItem(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.END_CRYSTAL) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceEntity(EntityPlaceEvent event) {
        if (event.getEntity() instanceof EnderCrystal) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof EnderCrystal) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && result.getType() == Material.END_CRYSTAL) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getRecipe() == null ? null : event.getRecipe().getResult();
        if (result != null && result.getType() == Material.END_CRYSTAL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMinecartTnt(EntityDamageByEntityEvent event) {
        if (!isMinecartTnt(event)) return;
        event.setDamage(event.getDamage() * MINECART_TNT_MULTIPLIER);
    }

    private boolean isMinecartTnt(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof ExplosiveMinecart) return true;
        if (event.getDamager() instanceof TNTPrimed tnt) {
            return tnt.getSource() instanceof ExplosiveMinecart
                    || tnt.getVehicle() instanceof ExplosiveMinecart;
        }
        return false;
    }
}
