package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class LeaveSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    private final Map<java.util.UUID, Long> confirmations = new HashMap<>();
    private static final long CONFIRMATION_TIMEOUT = 10_000L;
    
    public LeaveSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @Override
    public String getName() {
        return "opusc";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("leave", "exit");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        
        if (clan == null) {
            player.sendMessage(MessageUtil.error("Nie należysz do żadnego klanu!"));
            return;
        }
        
        Long confirmTime = confirmations.get(player.getUniqueId());
        if (confirmTime == null || System.currentTimeMillis() - confirmTime > CONFIRMATION_TIMEOUT) {
            confirmations.put(player.getUniqueId(), System.currentTimeMillis());
            player.sendMessage(MessageUtil.warning("Czy na pewno chcesz opuścić klan " + 
                MessageUtil.formatClanTag(clan.getTag()) + "&7?"));
            player.sendMessage(MessageUtil.format("&7Wpisz &f/klan opusc &7ponownie aby potwierdzić."));
            return;
        }
        
        confirmations.remove(player.getUniqueId());
        
        String error = clanManager.leaveClan(player);
        
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        
        player.sendTitle(
            MessageUtil.format("&8[&a✔&8]"),
            MessageUtil.format("&7Opuściłeś klan"),
            10, 60, 20
        );
        
        for (Player member : Bukkit.getOnlinePlayers()) {
            if (clan.hasMember(member.getUniqueId())) {
                member.sendMessage(MessageUtil.format("&8[&c-&8] &f" + player.getName() + 
                    " &copuścił klan!"));
            }
        }
    }
}