package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class DemoteSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    
    public DemoteSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @Override
    public String getName() {
        return "zdegraduj";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("demote", "deg");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.error("Użycie: &f/klan zdegraduj <gracz>"));
            return;
        }
        
        String targetName = args[0];
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        
        String error = clanManager.demoteMember(player, targetName);
        
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        
        player.sendMessage(MessageUtil.success("Zdegradowano gracza &f" + targetName + " &az admina!"));
        
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.sendMessage(MessageUtil.format("&8[&c-&8] &cZostałeś zdegradowany z admina!"));
        }
        
        for (Player member : Bukkit.getOnlinePlayers()) {
            if (clan != null && clan.hasMember(member.getUniqueId()) && !member.equals(player)) {
                member.sendMessage(MessageUtil.format("&8[&c-&8] &f" + targetName + 
                    " &czostał zdegradowany z admina!"));
            }
        }
    }
    
    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            Clan clan = clanManager.getPlayerClan(player.getUniqueId());
            if (clan == null) return Collections.emptyList();
            
            List<String> names = new ArrayList<>();
            String partial = args[0].toLowerCase();
            for (var member : clan.getMembers().values()) {
                if (member.getPlayerName().toLowerCase().startsWith(partial)) {
                    names.add(member.getPlayerName());
                }
            }
            return names;
        }
        return Collections.emptyList();
    }
}