package pl.zamrozenie;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class HomeInventoryHolder implements InventoryHolder {

    public static final int HOME_1_SLOT = 11;
    public static final int HOME_2_SLOT = 15;
    public static final int SET_1_SLOT = 20;
    public static final int SET_2_SLOT = 24;
    public static final int DELETE_1_SLOT = 19;
    public static final int DELETE_2_SLOT = 25;

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inv) {
        this.inventory = inv;
    }
}
