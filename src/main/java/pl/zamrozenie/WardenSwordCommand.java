package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WardenSwordCommand implements CommandExecutor {

    private final LegendaryItems items;

    public WardenSwordCommand(LegendaryItems items) {
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zamrozenie.wardensword")) {
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
            sender.sendMessage(ChatColor.RED + "Użycie: /wardensword <gracz>");
            return true;
        }
        target.getInventory().addItem(items.createWardenSword());
        target.sendMessage(ChatColor.DARK_AQUA + "Otrzymałeś Warden Sword.");
        if (sender != target) {
            sender.sendMessage(ChatColor.GREEN + "Dano Warden Sword graczu " + target.getName() + ".");
        }
        return true;
    }
}
