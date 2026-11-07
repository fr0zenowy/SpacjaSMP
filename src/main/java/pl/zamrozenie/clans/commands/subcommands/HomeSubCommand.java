package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class HomeSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    private final Map<UUID, BukkitRunnable> teleportTasks = new HashMap<>();
    private final Map<UUID, Location> originalLocations = new HashMap<>();
    
    private static final int TELEPORT_DELAY_SECONDS = 5;
    
    public HomeSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @Override
    public String getName() {
        return "home";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("h", "dom");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        
        if (clan == null) {
            player.sendMessage(MessageUtil.error("Nie należysz do żadnego klanu!"));
            return;
        }
        
        if (!clan.hasHome()) {
            player.sendMessage(MessageUtil.error("Home klanu nie został ustawiony!"));
            return;
        }
        
        // Anuluj poprzednią teleportację jeśli istnieje
        if (teleportTasks.containsKey(player.getUniqueId())) {
            teleportTasks.get(player.getUniqueId()).cancel();
            teleportTasks.remove(player.getUniqueId());
            originalLocations.remove(player.getUniqueId());
        }
        
        // Zapisz aktualną lokalizację
        originalLocations.put(player.getUniqueId(), player.getLocation().clone());
        
        player.sendMessage(MessageUtil.format("&8[&3⌂&8] &7Teleportacja za &f" + TELEPORT_DELAY_SECONDS + 
            " &7sekund... &7Nie ruszaj się!"));
        
        BukkitRunnable task = new BukkitRunnable() {
            int countdown = TELEPORT_DELAY_SECONDS;
            
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    teleportTasks.remove(player.getUniqueId());
                    originalLocations.remove(player.getUniqueId());
                    return;
                }
                
                // Sprawdź czy gracz się ruszył
                Location originalLoc = originalLocations.get(player.getUniqueId());
                if (originalLoc != null && player.getLocation().distance(originalLoc) > 0.5) {
                    player.sendMessage(MessageUtil.error("Teleportacja anulowana! Ruszyłeś się."));
                    cancel();
                    teleportTasks.remove(player.getUniqueId());
                    originalLocations.remove(player.getUniqueId());
                    return;
                }
                
                if (countdown <= 0) {
                    // Teleportuj
                    Location homeLoc = new Location(
                        Bukkit.getWorld(clan.getHomeWorld()),
                        clan.getHomeX(),
                        clan.getHomeY(),
                        clan.getHomeZ(),
                        clan.getHomeYaw(),
                        clan.getHomePitch()
                    );
                    
                    player.teleport(homeLoc);
                    player.sendTitle(
                        MessageUtil.format("&8[&a✔&8]"),
                        MessageUtil.format("&aPrzeteleportowano do home klanu!"),
                        10, 60, 20
                    );
                    
                    cancel();
                    teleportTasks.remove(player.getUniqueId());
                    originalLocations.remove(player.getUniqueId());
                } else {
                    countdown--;
                }
            }
        };
        
        teleportTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(Bukkit.getPluginManager().getPlugin("Zamrozenie"), 0L, 20L);
    }
}