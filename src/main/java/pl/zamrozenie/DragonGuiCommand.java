package pl.zamrozenie;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class DragonGuiCommand implements CommandExecutor {

    private final DragonManager dragonManager;

    public DragonGuiCommand(DragonManager dragonManager) {
        this.dragonManager = dragonManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cTa komenda moze byc uzywana tylko przez graczy.");
            return true;
        }

        Player player = (Player) sender;
        
        // Otwórz GUI dla gracza
        Inventory gui = dragonManager.buildDragonGui();
        player.openInventory(gui);
        
        return true;
    }
}