package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class AcceptInviteSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    
    public AcceptInviteSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @Override
    public String getName() {
        return "akceptuj";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("accept", "tak");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        Clan invitingClan = clanManager.getPendingInvite(player.getUniqueId());
        
        if (invitingClan == null) {
            player.sendMessage(MessageUtil.error("Nie masz żadnego zaproszenia do klanu!"));
            return;
        }
        
        String error = clanManager.acceptInvite(player);
        
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        
        // Powiadom gracza
        player.sendTitle(
            MessageUtil.format("&8[&a✔&8]"),
            MessageUtil.format("&aDołączyłeś do klanu " + MessageUtil.formatClanTag(invitingClan)),
            10, 60, 20
        );
        
        // Powiadom klan
        for (Player member : Bukkit.getOnlinePlayers()) {
            if (invitingClan.hasMember(member.getUniqueId())) {
                member.sendMessage(MessageUtil.format("&8[&a+&8] &f" + player.getName() +
                    " &adołączył do klanu!"));
            }
        }
        
        player.sendMessage(MessageUtil.success("Dołączyłeś do klanu " + MessageUtil.formatClanTag(invitingClan) + "&a!"));
    }
}