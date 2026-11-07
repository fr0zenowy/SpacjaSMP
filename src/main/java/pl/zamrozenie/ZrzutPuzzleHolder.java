package pl.zamrozenie;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holder GUI z zagadkami zrzutu. Przechowuje typ zagadki oraz jej rozwiazanie,
 * dzieki czemu listener moze zweryfikowac klikniecia bez polegania na tytule okna.
 *
 *  typ 1 – posortuj liczby rosnaco (sekwencja klikniec)
 *  typ 2 – kliknij blok rozniacy sie od pozostalych (jedno klikniecie)
 *  typ 3 – dopasuj kolor welny do wzoru (jedno klikniecie)
 */
public class ZrzutPuzzleHolder implements InventoryHolder {

    private Inventory inventory;
    private final int puzzleType;

    // typ 1 – sortowanie liczb
    private List<Integer> p1Expected;            // oczekiwana kolejnosc wartosci (rosnaco)
    private Map<Integer, Integer> p1SlotValue = new HashMap<>(); // slot -> wartosc
    private int p1Progress = 0;                  // ile poprawnie klikniec do tej pory

    // typ 2 / typ 3 – pojedynczy poprawny slot
    private int correctSlot = -1;

    public ZrzutPuzzleHolder(int puzzleType) {
        this.puzzleType = puzzleType;
    }

    public int getPuzzleType() { return puzzleType; }

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv) { this.inventory = inv; }

    // ─── typ 1 ───────────────────────────────────────────────────────────────────
    public void setP1Expected(List<Integer> expected) { this.p1Expected = expected; }
    public List<Integer> getP1Expected() { return p1Expected; }
    public Map<Integer, Integer> getP1SlotValue() { return p1SlotValue; }
    public int getP1Progress() { return p1Progress; }
    public void setP1Progress(int progress) { this.p1Progress = progress; }

    // ─── typ 2 / 3 ─────────────────────────────────────────────────────────────────
    public int getCorrectSlot() { return correctSlot; }
    public void setCorrectSlot(int slot) { this.correctSlot = slot; }
}
