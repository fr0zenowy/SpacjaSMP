package pl.zamrozenie;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MarketGuiHolder implements InventoryHolder {

    public enum Mode { BROWSE, MINE, CONFIRM }

    public static final int SIZE = 54;
    public static final int CONFIRM_SIZE = 27;
    public static final String TITLE_BROWSE = "\u00A7f\uE001\uE030";
    public static final String TITLE_MINE = "\u00A7f\uE001\uE031";
    public static final String TITLE_CONFIRM = "\u00A7f\uE001\uE032";

    /** Srodek: rzedy 0-3, kolumny 2-6 (te znaki rowna sie na screenie). */
    public static final int[] LIST_SLOTS = {
            2, 3, 4, 5, 6,
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33
    };

    public static final int[] MINE_SLOTS = {0, 1, 2};
    public static final int MINE_BACK = 49;

    public static final int CONFIRM_BUY = 11;
    public static final int CONFIRM_PREVIEW = 13;
    public static final int CONFIRM_CLOSE = 15;

    public static final int BTN_SWORD = 9;
    public static final int BTN_SCROLL = 18;
    public static final int BTN_VIAL = 27;
    public static final int BTN_BAG = 45;
    public static final int BTN_SEARCH = 46;
    public static final int BTN_PREV = 48;
    public static final int BTN_CLOSE = 49;
    public static final int BTN_NEXT = 50;
    public static final int BTN_GEAR = 53;

    public static final int PAGE_SIZE = LIST_SLOTS.length;

    private final Mode mode;
    private final int page;
    private final String query;
    private final MarketCategory category;
    private final MarketSort sort;
    private final java.util.UUID listingId;
    private Inventory inventory;

    public MarketGuiHolder(Mode mode, int page, String query, MarketCategory category) {
        this(mode, page, query, category, MarketSort.NONE, null);
    }

    public MarketGuiHolder(Mode mode, int page, String query, MarketCategory category, java.util.UUID listingId) {
        this(mode, page, query, category, MarketSort.NONE, listingId);
    }

    public MarketGuiHolder(Mode mode, int page, String query, MarketCategory category, MarketSort sort) {
        this(mode, page, query, category, sort, null);
    }

    public MarketGuiHolder(Mode mode, int page, String query, MarketCategory category, MarketSort sort, java.util.UUID listingId) {
        this.mode = mode;
        this.page = Math.max(0, page);
        this.query = query;
        this.category = category;
        this.sort = sort == null ? MarketSort.NONE : sort;
        this.listingId = listingId;
    }

    public Mode getMode() { return mode; }
    public int getPage() { return page; }
    public String getQuery() { return query; }
    public MarketCategory getCategory() { return category; }
    public MarketSort getSort() { return sort; }
    public java.util.UUID getListingId() { return listingId; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }

    public static boolean isChrome(int slot) {
        return slot == BTN_SWORD || slot == BTN_SCROLL || slot == BTN_VIAL
                || slot == BTN_BAG || slot == BTN_SEARCH
                || slot == BTN_PREV || slot == BTN_CLOSE || slot == BTN_NEXT
                || slot == BTN_GEAR;
    }
}
