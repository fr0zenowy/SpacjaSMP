package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Komenda admin do generowania eventowych przedmiotów Smoka Kresu do testowania.
 * 
 * Użycie:
 *   /dragongive belly   - daje Brzuszek Smoka
 *   /dragongive claw    - daje Smoczy Szpon
 *   /dragongive all     - daje oba przedmioty
 */
public class DragonGiveCommand implements CommandExecutor, TabCompleter {

    private final DragonManager dragonManager;

    public DragonGiveCommand(DragonManager dragonManager) {
        this.dragonManager = dragonManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Ta komenda może być używana tylko przez graczy.");
            return true;
        }

        if (!player.hasPermission("zamrozenie.use")) {
            player.sendMessage(ChatColor.RED + "Brak uprawnień.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Użycie: /dragongive <belly|claw|all>");
            return true;
        }

        String type = args[0].toLowerCase();
        switch (type) {
            case "belly", "brzuszek" -> {
                ItemStack belly = dragonManager.createDragonBelly();
                player.getInventory().addItem(belly);
                player.sendMessage(ChatColor.GREEN + "✔ Otrzymałeś " + 
                    ChatColor.DARK_PURPLE + ChatColor.BOLD + "Brzuszek " + 
                    ChatColor.LIGHT_PURPLE + ChatColor.BOLD + "Smoka" + 
                    ChatColor.GREEN + "!");
            }
            case "claw", "szpon" -> {
                ItemStack claw = dragonManager.createDragonClaw();
                player.getInventory().addItem(claw);
                player.sendMessage(ChatColor.GREEN + "✔ Otrzymałeś " + 
                    ChatColor.DARK_PURPLE + ChatColor.BOLD + "Smoczy Szpon" + 
                    ChatColor.GREEN + "!");
            }
            case "all", "oba" -> {
                ItemStack belly = dragonManager.createDragonBelly();
                ItemStack claw = dragonManager.createDragonClaw();
                player.getInventory().addItem(belly, claw);
                player.sendMessage(ChatColor.GREEN + "✔ Otrzymałeś " + 
                    ChatColor.DARK_PURPLE + ChatColor.BOLD + "Brzuszek Smoka" + 
                    ChatColor.GREEN + " i " + 
                    ChatColor.DARK_PURPLE + ChatColor.BOLD + "Smoczy Szpon" + 
                    ChatColor.GREEN + "!");
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Nieznany typ przedmiotu: " + type);
                player.sendMessage(ChatColor.YELLOW + "Dostępne: belly, claw, all");
                return true;
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("belly", "claw", "all", "brzuszek", "szpon", "oba");
        }
        return Collections.emptyList();
    }
}