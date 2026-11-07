package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MarketCommand implements CommandExecutor {

    private final MarketManager market;

    public MarketCommand(MarketManager market) {
        this.market = market;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Tylko gracz moze otworzyc /rynek.");
            return true;
        }
        market.openBrowse(player, 0, null);
        return true;
    }
}
