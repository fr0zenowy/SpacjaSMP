package pl.zamrozenie;

import org.bukkit.Material;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Farmer nie sprzedaje pumpkin pie. Istniejace trade'y sa podmieniane
 * na inny trade z puli farmera (chleb, ciastka, marchewki, melon, ciasto).
 */
public class VillagerPieListener implements Listener {

    private static final ItemStack[] FARMER_REPLACEMENTS = {
            stack(Material.BREAD, 6),
            stack(Material.COOKIE, 18),
            stack(Material.GOLDEN_CARROT, 3),
            stack(Material.GLISTERING_MELON_SLICE, 3),
            stack(Material.CAKE, 1)
    };

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAcquire(VillagerAcquireTradeEvent event) {
        if (!isPumpkinPie(event.getRecipe())) return;
        event.setRecipe(replace(event.getRecipe()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onReplenish(VillagerReplenishTradeEvent event) {
        if (!isPumpkinPie(event.getRecipe())) return;
        event.setRecipe(replace(event.getRecipe()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof AbstractVillager villager) {
            sanitize(villager);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory() instanceof MerchantInventory merch) {
            Merchant merchant = merch.getMerchant();
            if (merchant instanceof AbstractVillager villager) {
                sanitize(villager);
            } else {
                sanitize(merchant);
            }
        }
    }

    private static void sanitize(AbstractVillager villager) {
        List<MerchantRecipe> next = sanitize(villager.getRecipes());
        if (next != null) villager.setRecipes(next);
    }

    private static void sanitize(Merchant merchant) {
        List<MerchantRecipe> next = sanitize(merchant.getRecipes());
        if (next != null) merchant.setRecipes(next);
    }

    /** Zwraca nowa liste gdy bylo pumpkin pie, inaczej null. */
    private static List<MerchantRecipe> sanitize(List<MerchantRecipe> recipes) {
        if (recipes == null || recipes.isEmpty()) return null;
        boolean changed = false;
        List<MerchantRecipe> next = new ArrayList<>(recipes.size());
        for (MerchantRecipe recipe : recipes) {
            if (isPumpkinPie(recipe)) {
                next.add(replace(recipe));
                changed = true;
            } else {
                next.add(recipe);
            }
        }
        return changed ? next : null;
    }

    private static boolean isPumpkinPie(MerchantRecipe recipe) {
        if (recipe == null) return false;
        ItemStack result = recipe.getResult();
        if (result != null && result.getType() == Material.PUMPKIN_PIE) return true;
        for (ItemStack ingredient : recipe.getIngredients()) {
            if (ingredient != null && ingredient.getType() == Material.PUMPKIN_PIE) return true;
        }
        return false;
    }

    private static MerchantRecipe replace(MerchantRecipe old) {
        ItemStack result = FARMER_REPLACEMENTS[ThreadLocalRandom.current().nextInt(FARMER_REPLACEMENTS.length)].clone();
        MerchantRecipe next = new MerchantRecipe(
                result,
                old.getUses(),
                old.getMaxUses(),
                old.hasExperienceReward(),
                old.getVillagerExperience(),
                old.getPriceMultiplier()
        );
        List<ItemStack> ingredients = old.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            next.addIngredient(new ItemStack(Material.EMERALD));
        } else {
            for (ItemStack ingredient : ingredients) {
                if (ingredient == null || ingredient.getType() == Material.PUMPKIN_PIE) {
                    next.addIngredient(new ItemStack(Material.EMERALD));
                } else {
                    next.addIngredient(ingredient.clone());
                }
            }
        }
        next.setDemand(old.getDemand());
        next.setSpecialPrice(old.getSpecialPrice());
        return next;
    }

    private static ItemStack stack(Material material, int amount) {
        return new ItemStack(material, amount);
    }
}
