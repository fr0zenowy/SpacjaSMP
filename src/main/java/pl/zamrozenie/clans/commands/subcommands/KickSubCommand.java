package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class KickSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    
    public KickSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @Override
    public String getName() {
        return "wyrzuc";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("kick", "remove");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.error("Użycie: &f/klan wyrzuc <gracz>"));
            return;
        }
        
        String targetName = args[0];
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        
        String error = clanManager.kickPlayer(player, targetName);
        
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        
        player.sendMessage(MessageUtil.success("Wyrzucono gracza &f" + targetName + " &az klanu!"));
        
        // Powiadom wyrzucanego gracza (jeśli online)
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.sendTitle(
                MessageUtil.format("&8[&c✘&8]"),
                MessageUtil.format("&cZostałeś wyrzucony z klanu!"),
                10, 60, 20
            );
            targetPlayer.sendMessage(MessageUtil.format("&8[&c-&8] &f" + player.getName() + 
                " &cwyrzucił Cię z klanu!"));
        }
        
        // Powiadom klan
        for (Player member : Bukkit.getOnlinePlayers()) {
            if (clan != null && clan.hasMember(member.getUniqueId()) && !member.equals(player)) {
                member.sendMessage(MessageUtil.format("&8[&c-&8] &f" + targetName + 
                    " &czostał wyrzucony z klanu!"));
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