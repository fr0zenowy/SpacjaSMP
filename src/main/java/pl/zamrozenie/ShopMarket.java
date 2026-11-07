package pl.zamrozenie;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Za kazde 250$ sprzedazy danego itemu cena spada o 0.01$.
 * Kupno nie rusza rynku. Po bezczynnosci wraca do ceny bazowej.
 */
public class ShopMarket {

    private final ZamrozeniePlugin plugin;
    /** slot -> (gracz -> ostatnia sprzedaz ms) */
    private final Map<Integer, Map<UUID, Long>> recentSellers = new HashMap<>();

    private boolean enabled;
    private long dropEveryCents;
    private double dropAmount;
    private double minPrice;
    private double busyMinPrice;
    private int busyUniqueSellers;
    private long busyWindowMs;
    private double maxPercent;
    private long idleMs;
    private double recoverPerMinute;

    public ShopMarket(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("shop-market.enabled", true);
        dropEveryCents = ShopManager.toCents(Math.max(0.01, c.getDouble("shop-market.drop-per-money", 250.0)));
        dropAmount = Math.max(0.01, c.getDouble("shop-market.drop-amount", 0.01));
        minPrice = Math.max(0.01, c.getDouble("shop-market.min-price", 0.01));
        busyMinPrice = Math.max(minPrice, c.getDouble("shop-market.busy-min-price", 0.10));
        busyUniqueSellers = Math.max(1, c.getInt("shop-market.busy-unique-sellers", 5));
        busyWindowMs = Math.max(1L, c.getLong("shop-market.busy-window-minutes", 60L)) * 60_000L;
        maxPercent = c.getDouble("shop-market.max-percent", 130.0);
        idleMs = Math.max(1L, c.getLong("shop-market.idle-minutes", 45L)) * 60_000L;
        recoverPerMinute = c.getDouble("shop-market.recover-percent-per-minute", 3.0) / 100.0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean applyTrade(ShopOffer traded, int qty, Collection<ShopOffer> catalog) {
        return applyTrade(traded, qty, catalog, null);
    }

    public boolean applyTrade(ShopOffer traded, int qty, Collection<ShopOffer> catalog, UUID seller) {
        if (!enabled || traded == null || qty < 1) return false;
        long now = System.currentTimeMillis();
        traded.lastTradeMs = now;
        if (seller != null) recordSeller(traded.slot, seller, now);

        long cents = ShopManager.toCents(ShopManager.roundMoney(traded.price * qty));
        if (cents < 1) return false;
        traded.volumeCents += cents;
        long steps = dropEveryCents < 1 ? 0 : traded.volumeCents / dropEveryCents;
        if (steps < 1) return false;
        traded.volumeCents -= steps * dropEveryCents;
        return setPrice(traded, traded.price - steps * dropAmount);
    }

    public boolean recoverIdle(Collection<ShopOffer> catalog) {
        if (!enabled || catalog == null || catalog.isEmpty()) return false;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (ShopOffer offer : catalog) {
            if (now - offer.lastTradeMs < idleMs) continue;
            if (Math.abs(offer.price - offer.basePrice) < 0.0001) continue;
            double next = offer.price + (offer.basePrice - offer.price) * recoverPerMinute;
            if (Math.abs(next - offer.basePrice) < 0.005) next = offer.basePrice;
            if (setPrice(offer, next)) changed = true;
        }
        return changed;
    }

    private void recordSeller(int slot, UUID seller, long now) {
        recentSellers.computeIfAbsent(slot, k -> new HashMap<>()).put(seller, now);
        pruneSellers(slot, now);
    }

    private void pruneSellers(int slot, long now) {
        Map<UUID, Long> map = recentSellers.get(slot);
        if (map == null) return;
        Iterator<Map.Entry<UUID, Long>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > busyWindowMs) it.remove();
        }
        if (map.isEmpty()) recentSellers.remove(slot);
    }

    private double floorFor(ShopOffer offer) {
        long now = System.currentTimeMillis();
        pruneSellers(offer.slot, now);
        Map<UUID, Long> map = recentSellers.get(offer.slot);
        int unique = map == null ? 0 : map.size();
        if (unique < busyUniqueSellers) return minPrice;
        return Math.max(minPrice, Math.min(busyMinPrice, offer.basePrice));
    }

    private boolean setPrice(ShopOffer offer, double raw) {
        double min = floorFor(offer);
        double max = Math.max(min, clampBound(offer.basePrice, maxPercent));
        double next = ShopManager.roundMoney(Math.max(min, Math.min(max, raw)));
        if (Math.abs(next - offer.price) < 0.0000001) return false;
        offer.price = next;
        return true;
    }

    private static double clampBound(double base, double percent) {
        return Math.max(0.01, base * (percent / 100.0));
    }
}
