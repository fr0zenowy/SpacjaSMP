package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class InviteSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    
    public InviteSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @Override
    public String getName() {
        return "zapros";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("invite", "inv");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MessageUtil.error("Użycie: &f/klan zapros <gracz>"));
            return;
        }
        
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null || !target.isOnline()) {
            player.sendMessage(MessageUtil.error("Gracz nie jest online!"));
            return;
        }
        
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        String error = clanManager.invitePlayer(player, target);
        
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        
        // Wyślij zaproszenie do gracza
        sendInviteMessage(target, clan, player.getName());
        
        // Powiadom zapraszającego
        player.sendMessage(MessageUtil.success("Wysłano zaproszenie do gracza &f" + target.getName() + "&a!"));
        
        // Powiadom klan
        for (Player member : Bukkit.getOnlinePlayers()) {
            if (clan.hasMember(member.getUniqueId()) && !member.equals(player)) {
                member.sendMessage(MessageUtil.clan("&f" + player.getName() + 
                    " &7zaprosił &f" + target.getName() + " &7do klanu!"));
            }
        }
    }
    
    private void sendInviteMessage(Player target, Clan clan, String inviterName) {
        target.sendMessage(MessageUtil.divider());
        target.sendMessage(MessageUtil.format(""));
        target.sendMessage(MessageUtil.format("&f" + inviterName + " &7zaprasza Cię do klanu " +
            MessageUtil.formatClanTag(clan.getTag()) + " &7" + clan.getName() + "!"));
        target.sendMessage(MessageUtil.format(""));
        target.sendMessage(MessageUtil.format("&7Wpisz &f/klan akceptuj &7aby zaakceptować"));
        target.sendMessage(MessageUtil.format("&7Wpisz &f/klan odrzuc &7aby odrzucić"));
        target.sendMessage(MessageUtil.format(""));
        target.sendMessage(MessageUtil.divider());
    }
    
    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            List<String> playerNames = new ArrayList<>();
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial) && !p.equals(player)) {
                    playerNames.add(p.getName());
                }
            }
            return playerNames;
        }
        return Collections.emptyList();
    }
}