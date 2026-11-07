package pl.zamrozenie;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum MarketCategory {
    TOOLS,
    FOOD,
    USEFUL;

    public boolean matches(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return switch (this) {
            case TOOLS -> isTools(item.getType());
            case FOOD -> isFood(item.getType());
            case USEFUL -> !isTools(item.getType()) && !isFood(item.getType());
        };
    }

    public static boolean isFood(Material material) {
        if (material.isEdible()) return true;
        return switch (material) {
            case CAKE, MILK_BUCKET, SUGAR, WHEAT, WHEAT_SEEDS, EGG, BROWN_EGG, BLUE_EGG,
                    COCOA_BEANS, PUMPKIN, MELON, MELON_SLICE, GLISTERING_MELON_SLICE,
                    MELON_SEEDS, PUMPKIN_SEEDS, BROWN_MUSHROOM, RED_MUSHROOM, BOWL,
                    HONEYCOMB, SUGAR_CANE, NETHER_WART, BEETROOT_SEEDS -> true;
            default -> false;
        };
    }

    public static boolean isTools(Material material) {
        String name = material.name();
        if (name.endsWith("_SWORD")
                || name.endsWith("_PICKAXE")
                || (name.endsWith("_AXE") && !name.endsWith("_PICKAXE"))
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || name.endsWith("_SPEAR")
                || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || name.endsWith("_HORSE_ARMOR")) {
            return true;
        }
        return switch (material) {
            case BOW, CROSSBOW, TRIDENT, MACE, SHIELD, ELYTRA, SHEARS, BRUSH,
                    FLINT_AND_STEEL, FISHING_ROD, CARROT_ON_A_STICK,
                    WARPED_FUNGUS_ON_A_STICK, WOLF_ARMOR, TURTLE_HELMET -> true;
            default -> false;
        };
    }
}
