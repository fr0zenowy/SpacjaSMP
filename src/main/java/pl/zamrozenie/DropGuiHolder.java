package pl.zamrozenie;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class DropGuiHolder implements InventoryHolder {

    public enum Mode { MAIN, VIEW, EDIT, EDIT_EVENT }

    public boolean isEdit() {
        return mode == Mode.EDIT || mode == Mode.EDIT_EVENT;
    }

    public static final int INFO_SLOT = 4;
    public static final int PREV_SLOT = 48;
    public static final int NEXT_SLOT = 50;
    public static final int CONTENT_START = 9;
    public static final int CONTENT_END = 44;

    /** Tytul 1:1 z resource packiem DropGUI-glif (ujemna spacja + glif). */
    public static final String TITLE = "\u00A7f\uE001\uE000";

    private final Mode mode;
    private final DropSource source;
    private Inventory inventory;

    public DropGuiHolder(Mode mode, DropSource source) {
        this.mode = mode;
        this.source = source;
    }

    public Mode getMode() { return mode; }
    public DropSource getSource() { return source; }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static boolean isContentSlot(int slot) {
        return slot >= CONTENT_START && slot <= CONTENT_END;
    }

    public static boolean isDecorSlot(int slot) {
        if (slot == INFO_SLOT || slot == PREV_SLOT || slot == NEXT_SLOT) return true;
        for (DropSource src : DropSource.values()) {
            if (src.getMenuSlot() == slot) return true;
        }
        return false;
    }
}
