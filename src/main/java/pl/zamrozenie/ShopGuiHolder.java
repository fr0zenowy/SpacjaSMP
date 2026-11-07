package pl.zamrozenie;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class ShopGuiHolder implements InventoryHolder {

    public enum Mode { MAIN, BUY, SELL, BUY_ADMIN, SELL_ADMIN, CONFIRM }

    // rozmiary okien dopasowane do tekstur GUI (shop.png = 5 rzedow, shop_list.png = 6, shop_confirm.png = 3)
    public static final int MAIN_SIZE = 45;
    public static final int LIST_SIZE = 54;
    public static final int CONFIRM_SIZE = 27;

    public static final int MAIN_BUY = 20;
    public static final int MAIN_SELL = 24;

    public static final int LIST_CLOSE = 49;
    public static final int LIST_BANK = 50;

    public static final int CONFIRM_INFO = 4;
    public static final int CONFIRM_BUY = 11;
    public static final int CONFIRM_PREVIEW = 13;
    public static final int CONFIRM_CLOSE = 15;
    public static final int CONFIRM_ADD = 21;
    public static final int CONFIRM_SUB = 23;

    public static final String TITLE_MAIN = "\u00A7f\uE001\uE010";
    public static final String TITLE_LIST = "\u00A7f\uE001\uE011";
    public static final String TITLE_CONFIRM = "\u00A7f\uE001\uE012";

    private final Mode mode;
    private final ItemStack confirmBase;
    private final double confirmPrice;
    private final int confirmSlot;
    private int confirmQty;
    private Inventory inventory;

    public ShopGuiHolder(Mode mode) {
        this(mode, null, 0, 1, -1);
    }

    public ShopGuiHolder(Mode mode, ItemStack confirmBase, double confirmPrice, int confirmQty) {
        this(mode, confirmBase, confirmPrice, confirmQty, -1);
    }

    public ShopGuiHolder(Mode mode, ItemStack confirmBase, double confirmPrice, int confirmQty, int confirmSlot) {
        this.mode = mode;
        this.confirmBase = confirmBase;
        this.confirmPrice = confirmPrice;
        this.confirmQty = confirmQty;
        this.confirmSlot = confirmSlot;
    }

    public Mode getMode() { return mode; }
    public ItemStack getConfirmBase() { return confirmBase; }
    public double getConfirmPrice() { return confirmPrice; }
    public int getConfirmSlot() { return confirmSlot; }
    public int getConfirmQty() { return confirmQty; }
    public void setConfirmQty(int qty) { this.confirmQty = qty; }

    public boolean isAdmin() {
        return mode == Mode.BUY_ADMIN || mode == Mode.SELL_ADMIN;
    }

    public boolean isCatalog() {
        return mode == Mode.BUY || mode == Mode.SELL || isAdmin();
    }

    public boolean isSellCatalog() {
        return mode == Mode.SELL || mode == Mode.SELL_ADMIN;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static boolean isListChrome(int slot) {
        return slot == LIST_CLOSE || slot == LIST_BANK;
    }

    public static boolean isConfirmChrome(int slot) {
        return slot == CONFIRM_INFO || slot == CONFIRM_BUY || slot == CONFIRM_PREVIEW
                || slot == CONFIRM_CLOSE || slot == CONFIRM_ADD || slot == CONFIRM_SUB;
    }
}
