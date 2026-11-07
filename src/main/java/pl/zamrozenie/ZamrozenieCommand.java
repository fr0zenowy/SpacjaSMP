package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;

public class ZamrozenieCommand implements CommandExecutor, TabCompleter {

    private final ZamrozeniePlugin plugin;
    private final ZamrozenieManager manager;

    public ZamrozenieCommand(ZamrozeniePlugin plugin, ZamrozenieManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zamrozenie.use")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnien do tej komendy.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            if (!manager.isFrozen()) {
                sender.sendMessage(ChatColor.YELLOW + "Serwer nie jest aktualnie zamrozony.");
                return true;
            }
            manager.stopFreeze(false);
            sender.sendMessage(ChatColor.GREEN + "Zamrozenie zostalo przerwane.");
            return true;
        }

        int seconds = ZamrozenieManager.parseTime(args[0]);
        if (seconds <= 0) {
            sender.sendMessage(ChatColor.RED + "Niepoprawny format czasu. Przyklady: 60s, 5m, 1h, 1h30m");
            return true;
        }

        if (seconds > 86400) {
            sender.sendMessage(ChatColor.RED + "Maksymalny czas zamrozenia to 24h (86400s).");
            return true;
        }

        manager.startFreeze(seconds);
        sender.sendMessage(ChatColor.AQUA + "Zamrozenie rozpoczete na " + ChatColor.GREEN + args[0] + ChatColor.AQUA + ".");
        plugin.getLogger().info(sender.getName() + " uruchomil zamrozenie na " + args[0]);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== Zamrozenie ===");
        sender.sendMessage(ChatColor.GRAY + "/zamrozenie <czas> " + ChatColor.WHITE + "- zamraza serwer (np. 60s, 5m, 1h)");
        sender.sendMessage(ChatColor.GRAY + "/zamrozenie stop   " + ChatColor.WHITE + "- konczy zamrozenie");
        if (manager.isFrozen()) {
            sender.sendMessage(ChatColor.YELLOW + "Status: ZAMROZONY | Pozostalo: " + manager.getRemainingSeconds() + "s");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Status: Odmrozony");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("zamrozenie.use")) return null;

        if (args.length == 1) {
            if (manager.isFrozen()) {
                return Arrays.asList("stop", "30s", "1m", "5m", "10m", "30m", "1h");
            }
            return Arrays.asList("30s", "1m", "5m", "10m", "30m", "1h");
        }
        return null;
    }
}
