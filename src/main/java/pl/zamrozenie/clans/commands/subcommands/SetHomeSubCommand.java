package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.models.ClanMember;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class SetHomeSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    
    public SetHomeSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @Override
    public String getName() {
        return "sethome";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("sh", "ustawhome");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        
        if (clan == null) {
            player.sendMessage(MessageUtil.error("Nie należysz do żadnego klanu!"));
            return;
        }
        
        ClanMember member = clan.getMember(player.getUniqueId());
        if (!member.canManageMembers()) {
            player.sendMessage(MessageUtil.error("Nie masz uprawnień!"));
            return;
        }
        
        Location loc = player.getLocation();
        
        // Sprawdź czy gracz jest w bezpiecznym miejscu
        if (player.isFlying() && player.getAllowFlight()) {
            player.sendMessage(MessageUtil.error("Nie możesz ustawić home podczas latania!"));
            return;
        }
        
        // Sprawdź czy nie stoi w płynach
        Material blockType = loc.getBlock().getType();
        if (blockType == Material.LAVA || blockType == Material.WATER) {
            player.sendMessage(MessageUtil.error("Nie możesz ustawić home w płynach!"));
            return;
        }
        
        // Ustaw home
        clan.setHome(loc);
        
        player.sendTitle(
            MessageUtil.format("&8[&a✔&8]"),
            MessageUtil.format("&aHome klanu ustawiony!"),
            10, 60, 20
        );
        
        // Powiadom klan
        for (Player member2 : Bukkit.getOnlinePlayers()) {
            if (clan.hasMember(member2.getUniqueId())) {
                member2.sendMessage(MessageUtil.format("&8[&3⌂&8] &f" + player.getName() + 
                    " &7ustawił home klanu!"));
            }
        }
    }
}