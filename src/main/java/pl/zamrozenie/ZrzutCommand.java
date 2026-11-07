package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ZrzutCommand implements CommandExecutor {

    private final ZrzutManager manager;

    public ZrzutCommand(ZrzutManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Tylko gracz moze sprawdzic status zrzutu.");
            return true;
        }
        manager.sendStatus(player);
        return true;
    }
}
