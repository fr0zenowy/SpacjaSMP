package pl.zamrozenie;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class ZamrozeniePlaceholders extends PlaceholderExpansion {

    private final ZamrozeniePlugin plugin;

    public ZamrozeniePlaceholders(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "zamrozenie";
    }

    @Override
    public String getAuthor() {
        return "fr0zenowy";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || plugin.getShopManager() == null) return "";
        double money = plugin.getShopManager().getEconomy().get(player);
        return switch (params.toLowerCase()) {
            case "money", "hajs", "balance", "saldo" -> ShopManager.formatMoney(money);
            case "money_int", "hajs_int" -> String.valueOf((long) Math.floor(money));
            default -> null;
        };
    }
}
