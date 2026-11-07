package pl.zamrozenie;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder GUI losowania legendarnego przedmiotu (animacja "ruletki").
 * Klikniecia sa anulowane – to czysto wizualne losowanie.
 */
public class ZrzutSpinHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv) { this.inventory = inv; }
}
