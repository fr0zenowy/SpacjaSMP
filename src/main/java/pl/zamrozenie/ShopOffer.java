package pl.zamrozenie;

import org.bukkit.inventory.ItemStack;

public class ShopOffer {

    public final int slot;
    /** Cena ustawiona przez admina — do niej wraca rynek. */
    public final double basePrice;
    /** Aktualna cena (gospodarka). */
    public double price;
    public long lastTradeMs;
    /** Reszta obrotu w groszach do kolejnego spadku o 0.01. */
    public long volumeCents;
    public final ItemStack item;

    public ShopOffer(int slot, double price, ItemStack item) {
        this(slot, price, price, System.currentTimeMillis(), 0L, item);
    }

    public ShopOffer(int slot, double basePrice, double price, long lastTradeMs, ItemStack item) {
        this(slot, basePrice, price, lastTradeMs, 0L, item);
    }

    public ShopOffer(int slot, double basePrice, double price, long lastTradeMs, long volumeCents, ItemStack item) {
        this.slot = slot;
        this.basePrice = Math.max(0.01, basePrice);
        this.price = Math.max(0.01, price);
        this.lastTradeMs = lastTradeMs;
        this.volumeCents = Math.max(0L, volumeCents);
        this.item = item;
    }
}
