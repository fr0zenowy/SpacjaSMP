package pl.zamrozenie;

import java.util.Comparator;
import java.util.List;

public enum MarketSort {
    NONE("Brak motywu"),
    PRICE_HIGH("Najwyższej ceny"),
    PRICE_LOW("Najniższej ceny"),
    NEWEST("Najnowsze"),
    OLDEST("Najstarsze");

    public final String label;

    MarketSort(String label) {
        this.label = label;
    }

    public MarketSort next() {
        MarketSort[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public void apply(List<MarketListing> list) {
        switch (this) {
            case NONE -> { }
            case PRICE_HIGH -> list.sort((a, b) -> Double.compare(b.price, a.price));
            case PRICE_LOW -> list.sort(Comparator.comparingDouble(a -> a.price));
            case NEWEST -> list.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
            case OLDEST -> list.sort(Comparator.comparingLong(a -> a.createdAt));
        }
    }
}
