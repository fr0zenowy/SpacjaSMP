package pl.zamrozenie;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public class ResurrectionGuiHolder implements InventoryHolder {

    private Inventory inventory;
    private final List<String> bannedNames; // nazwy w kolejnosci slotow

    public ResurrectionGuiHolder(List<String> bannedNames) {
        this.bannedNames = bannedNames;
    }

    /** Zwraca nick gracza przypisany do danego slotu, lub null jesli brak. */
    public String getPlayerAtSlot(int slot) {
        if (slot < 0 || slot >= bannedNames.size()) return null;
        return bannedNames.get(slot);
    }

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv) { this.inventory = inv; }
}
