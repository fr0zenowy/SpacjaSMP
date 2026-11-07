package pl.zamrozenie;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;

/** Mace nie da sie enchantowac (stol, kowadlo, smithing). */
public class MaceEnchantBlockListener implements Listener {

    private static boolean isMace(ItemStack item) {
        return item != null && item.getType() == Material.MACE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (isMace(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (isMace(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getItem(0);
        ItemStack result = event.getResult();
        if (!isMace(left) && !isMace(result)) return;
        if (result == null) return;
        var before = left != null ? left.getEnchantments() : java.util.Map.of();
        if (!result.getEnchantments().equals(before)) event.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (!isMace(result) || result.getItemMeta() == null) return;
        if (result.getItemMeta().hasEnchants()) event.setResult(null);
    }
}
