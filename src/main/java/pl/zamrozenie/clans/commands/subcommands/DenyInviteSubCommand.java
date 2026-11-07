package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class DenyInviteSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    
    public DenyInviteSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @Override
    public String getName() {
        return "odrzuc";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("deny", "nie");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        Clan invitingClan = clanManager.getPendingInvite(player.getUniqueId());
        
        String error = clanManager.denyInvite(player);
        
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        
        player.sendMessage(MessageUtil.success("Odrzuciłeś zaproszenie do klanu."));
        
        // Powiadom klan o odrzuceniu (tylko admini i lider)
        if (invitingClan != null) {
            for (Player member : Bukkit.getOnlinePlayers()) {
                if (invitingClan.hasMember(member.getUniqueId())) {
                    var clanMember = invitingClan.getMember(member.getUniqueId());
                    if (clanMember != null && clanMember.canManageMembers()) {
                        member.sendMessage(MessageUtil.format("&8[&c✘&8] &f" + player.getName() + 
                            " &codrzucił zaproszenie!"));
                    }
                }
            }
        }
    }
}