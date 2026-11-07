package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class TpaCommand implements CommandExecutor, TabCompleter {

    private final TpaManager tpaManager;

    public TpaCommand(TpaManager tpaManager) {
        this.tpaManager = tpaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Tylko gracze moga uzyc tej komendy.");
            return true;
        }

        switch (label.toLowerCase()) {
            case "tpa" -> {
                if (args.length == 0) {
                    player.sendMessage(ChatColor.RED + "Użycie: /tpa <nick>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(ChatColor.RED + "Gracz " + ChatColor.WHITE + args[0] + ChatColor.RED + " nie jest online.");
                    return true;
                }
                if (target.equals(player)) {
                    player.sendMessage(ChatColor.RED + "Nie możesz wysłać żądania TPA do siebie.");
                    return true;
                }
                tpaManager.sendRequest(player, target);
            }
            case "tpaccept" -> {
                if (!tpaManager.hasPendingRequest(player)) {
                    player.sendMessage(ChatColor.RED + "Nie masz żadnego oczekującego żądania TPA.");
                    return true;
                }
                tpaManager.acceptRequest(player);
            }
            case "tpadeny", "tpdeny" -> {
                if (!tpaManager.hasPendingRequest(player)) {
                    player.sendMessage(ChatColor.RED + "Nie masz żadnego oczekującego żądania TPA.");
                    return true;
                }
                tpaManager.denyRequest(player);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (alias.equalsIgnoreCase("tpa") && args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
        }
        return null;
    }
}
