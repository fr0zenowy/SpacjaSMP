package pl.zamrozenie;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder GUI informacji o Smoku Kresu (/smok).
 * Pokazuje możliwe dropy oraz informacje o evencie.
 * GUI jest read-only — kliknięcia są anulowane.
 * Slot 40 cyklicznie zmienia przedmiot co 1s (animacja w DragonGuiListener).
 */
public class DragonGuiHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inv) {
        this.inventory = inv;
    }
}