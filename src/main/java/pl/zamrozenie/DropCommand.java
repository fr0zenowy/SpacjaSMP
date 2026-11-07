package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DropCommand implements CommandExecutor {

    private final DropManager dropManager;

    public DropCommand(DropManager dropManager) {
        this.dropManager = dropManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Tylko gracz moze otworzyc /drop.");
            return true;
        }
        dropManager.openMain(player);
        return true;
    }
}
