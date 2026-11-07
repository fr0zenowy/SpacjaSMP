package pl.zamrozenie;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public class KepicGownoCommand implements CommandExecutor {

    private static final String[] LINES = {
            "kepic zul gowno normalnie kupe zol gowno",
            "gotuje gotuje gowno normalnie w czajniku kepic normalnie w czajniku gowno gowno"
    };

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LINES[ThreadLocalRandom.current().nextInt(LINES.length)]);
            return true;
        }
        player.sendMessage(LINES[ThreadLocalRandom.current().nextInt(LINES.length)]);
        return true;
    }
}
