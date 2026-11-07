package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AktywujSmokaCommand implements CommandExecutor {

    private final DragonManager dragonManager;

    public AktywujSmokaCommand(DragonManager dragonManager) {
        this.dragonManager = dragonManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnien do tej komendy.");
            return true;
        }

        if (dragonManager.isActive()) {
            sender.sendMessage(ChatColor.RED + "Smok Kresu jest juz aktywny!");
            return true;
        }

        boolean started = dragonManager.startEvent();
        if (!started) {
            sender.sendMessage(ChatColor.RED + "Nie udalo sie przywolac Smoka. " +
                    "Upewnij sie ze swiat Endu (world_the_end) istnieje na serwerze.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Animacja przywolania Smoka Kresu zostala uruchomiona!");
        }
        return true;
    }
}
