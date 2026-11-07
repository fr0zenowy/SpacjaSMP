package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Obsluguje komendy operatorskie:
 *   /adminzrzut <HH:mm>   – ustawia codzienna godzine zrzutu (czas Polski)
 *   /adminzrzut off       – wylacza harmonogram
 *   /adminzrzuttest       – natychmiast respi zrzut (test)
 *   /adminzrzutotworz     – natychmiast otwiera aktywny zrzut (pomija 15 min)
 */
public class AdminZrzutCommand implements CommandExecutor, TabCompleter {

    private final ZrzutManager manager;

    public AdminZrzutCommand(ZrzutManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("zamrozenie.use")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnien.");
            return true;
        }

        String name = cmd.getName().toLowerCase();
        switch (name) {
            case "adminzrzuttest" -> {
                Player p = sender instanceof Player ? (Player) sender : null;
                sender.sendMessage(ChatColor.GREEN + "Respie testowy zrzut...");
                manager.triggerDrop(p);
            }
            case "adminzrzutotworz" -> {
                Player p = sender instanceof Player ? (Player) sender : null;
                manager.openDrop(p);
                if (manager.getPhase() == ZrzutManager.Phase.OPEN)
                    sender.sendMessage(ChatColor.GREEN + "Zrzut zostal otwarty.");
            }
            case "adminzrzut" -> {
                if (args.length < 1) {
                    sender.sendMessage(ChatColor.RED + "Uzycie: /adminzrzut <HH:mm | off>");
                    return true;
                }
                if (args[0].equalsIgnoreCase("off")) {
                    manager.disableSchedule();
                    sender.sendMessage(ChatColor.YELLOW + "Codzienny zrzut zostal wylaczony.");
                    return true;
                }
                int[] hm = parseTime(args[0]);
                if (hm == null) {
                    sender.sendMessage(ChatColor.RED + "Zly format godziny. Przyklad: /adminzrzut 18:00");
                    return true;
                }
                manager.setScheduledTime(hm[0], hm[1]);
                sender.sendMessage(ChatColor.GREEN + "Ustawiono codzienny zrzut na " +
                        ChatColor.YELLOW + String.format("%02d:%02d", hm[0], hm[1]) +
                        ChatColor.GREEN + " (czas Polski). Mod bedzie czekal na te godzine.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Nieznana komenda.");
        }
        return true;
    }

    /** Parsuje "HH:mm" lub "HH". Zwraca [hour, minute] lub null. */
    private int[] parseTime(String input) {
        try {
            String[] parts = input.split(":");
            int hour = Integer.parseInt(parts[0].trim());
            int minute = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
            return new int[]{hour, minute};
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("adminzrzut") && args.length == 1) {
            return List.of("18:00", "20:00", "off");
        }
        return Collections.emptyList();
    }
}
