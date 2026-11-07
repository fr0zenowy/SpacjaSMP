package pl.zamrozenie;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder GUI skrzyni z lootem zrzutu. W przeciwienstwie do zagadek tutaj
 * klikniecia NIE sa anulowane – zwyciezca normalnie wyciaga przedmioty.
 * Pozostale itemy sa wyrzucane przy zamknieciu okna.
 */
public class ZrzutLootHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv) { this.inventory = inv; }
}
