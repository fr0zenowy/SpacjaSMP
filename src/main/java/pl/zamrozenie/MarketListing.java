package pl.zamrozenie;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class MarketListing {

    public final UUID id;
    public final UUID seller;
    public final String sellerName;
    public final double price;
    public final ItemStack item;
    public final long createdAt;

    public MarketListing(UUID id, UUID seller, String sellerName, double price, ItemStack item) {
        this(id, seller, sellerName, price, item, System.currentTimeMillis());
    }

    public MarketListing(UUID id, UUID seller, String sellerName, double price, ItemStack item, long createdAt) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.price = price;
        this.item = item;
        this.createdAt = createdAt;
    }
}
