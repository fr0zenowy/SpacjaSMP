package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class PromoteSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    
    public PromoteSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @Override
    public String getName() {
        return "awansuj";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("promote", "promote");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.error("Użycie: &f/klan awansuj <gracz>"));
            return;
        }
        
        String targetName = args[0];
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        
        String error = clanManager.promoteMember(player, targetName);
        
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        
        player.sendMessage(MessageUtil.success("Awansowano gracza &f" + targetName + " &ana admina!"));
        
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.sendMessage(MessageUtil.success("Zostałeś awansowany na admina klanu!"));
        }
        
        for (Player member : Bukkit.getOnlinePlayers()) {
            if (clan != null && clan.hasMember(member.getUniqueId()) && !member.equals(player)) {
                member.sendMessage(MessageUtil.format("&8[&a+&8] &f" + targetName + 
                    " &azostał awansowany na admina!"));
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