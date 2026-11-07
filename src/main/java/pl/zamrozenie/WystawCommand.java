package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WystawCommand implements CommandExecutor {

    private final MarketManager market;

    public WystawCommand(MarketManager market) {
        this.market = market;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Tylko gracz moze wystawiac itemy.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Uzycie: /wystaw <ilosc> <za ile>");
            player.sendMessage(ChatColor.GRAY + "Przyklad: /wystaw 1 100");
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Niepoprawna ilosc.");
            return true;
        }
        Double price = ShopManager.parsePrice(args[1]);
        if (price == null) {
            player.sendMessage(ChatColor.RED + "Niepoprawna cena.");
            return true;
        }
        market.wystaw(player, amount, price);
        return true;
    }
}
