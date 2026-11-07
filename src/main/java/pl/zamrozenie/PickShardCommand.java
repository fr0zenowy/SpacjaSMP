package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PickShardCommand implements CommandExecutor {

    private final LegendaryItems items;

    public PickShardCommand(LegendaryItems items) {
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zamrozenie.kilof3x3")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnień.");
            return true;
        }
        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Nie ma takiego gracza.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(ChatColor.RED + "Użycie: /odlamekkilofa <gracz>");
            return true;
        }
        target.getInventory().addItem(items.createPickShard());
        target.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Odłamek Kilofa"
                + ChatColor.GRAY + " jest w Twoim ekwipunku.");
        if (sender != target) {
            sender.sendMessage(ChatColor.GREEN + "Dano Odłamek Kilofa graczowi " + target.getName() + ".");
        }
        return true;
    }
}
