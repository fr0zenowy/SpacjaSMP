package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Stół 3x3: sloty 1-9. Cancel + ponowne zdjęcie w następnym ticku,
 * bo Paper oddaje 1 sztukę składnika po custom crafcie.
 */
public class NetherPickCraftListener implements Listener {

    private final ZamrozeniePlugin plugin;
    private final LegendaryItems items;
    private final LifestealManager lifesteal;

    public NetherPickCraftListener(ZamrozeniePlugin plugin, LegendaryItems items, LifestealManager lifesteal) {
        this.plugin = plugin;
        this.items = items;
        this.lifesteal = lifesteal;
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!matches(event.getInventory().getMatrix())) {
            ItemStack result = event.getInventory().getResult();
            if (result != null && LegendaryItems.NETHER_PICK.equals(items.getId(result))) {
                event.getInventory().setResult(null);
            }
            return;
        }
        event.getInventory().setResult(items.createNetherPick());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        CraftingInventory inv = event.getInventory();
        if (!matches(inv.getMatrix())) return;

        ClickType click = event.getClick();
        if (event.isShiftClick() || click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND
                || click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            event.setCancelled(true);
            return;
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        player.setItemOnCursor(items.createNetherPick());
        consumeRequired(inv);
        Bukkit.getScheduler().runTask(plugin, () -> consumeRequired(inv));
        Bukkit.getScheduler().runTaskLater(plugin, () -> consumeRequired(inv), 2L);
    }

    /** Zdejmuje wymagane ilości. Puste sloty = nic. */
    private static void consumeRequired(CraftingInventory inv) {
        if (inv == null) return;
        takeRaw(inv, 1, 1);
        takeRaw(inv, 2, 1);
        takeRaw(inv, 3, 1);
        takeRaw(inv, 4, 5);
        takeRaw(inv, 5, 64);
        takeRaw(inv, 6, 1);
        takeRaw(inv, 8, 1);
        inv.setResult(null);
    }

    private static void takeRaw(CraftingInventory inv, int slot, int amount) {
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getType().isAir() || amount < 1) return;
        int left = item.getAmount() - amount;
        if (left <= 0) {
            inv.setItem(slot, null);
        } else {
            ItemStack copy = item.clone();
            copy.setAmount(left);
            inv.setItem(slot, copy);
        }
    }

    private boolean matches(ItemStack[] matrix) {
        if (matrix == null || matrix.length < 9) return false;
        return lifesteal.isHeartItem(matrix[0]) && amount(matrix[0]) >= 1
                && isType(matrix[1], Material.HEAVY_CORE, 1)
                && lifesteal.isHeartItem(matrix[2]) && amount(matrix[2]) >= 1
                && isType(matrix[3], Material.GOLDEN_APPLE, 5)
                && isType(matrix[4], Material.DIAMOND, 64)
                && isType(matrix[5], Material.ENCHANTED_GOLDEN_APPLE, 1)
                && isEmpty(matrix[6])
                && items.isPickShard(matrix[7]) && amount(matrix[7]) >= 1
                && isEmpty(matrix[8]);
    }

    private static boolean isType(ItemStack item, Material type, int min) {
        return item != null && item.getType() == type && item.getAmount() >= min;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static int amount(ItemStack item) {
        return item == null ? 0 : item.getAmount();
    }
}
