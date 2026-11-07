package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.*;
import pl.zamrozenie.clans.models.*;
import pl.zamrozenie.clans.utils.ClanColors;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.*;

public class InfoSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    
    public InfoSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @Override
    public String getName() {
        return "info";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("informacje", "i");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        Clan clan;
        
        if (args.length == 0) {
            // Pokaż info o swoim klanie
            clan = clanManager.getPlayerClan(player.getUniqueId());
            if (clan == null) {
                player.sendMessage(MessageUtil.error("Nie należysz do żadnego klanu!"));
                return;
            }
        } else {
            // Pokaż info o podanym klanie
            String tag = args[0];
            clan = clanManager.getClanByTag(tag);
            if (clan == null) {
                player.sendMessage(MessageUtil.error("Nie znaleziono klanu o tagu &f" + tag + "&c!"));
                return;
            }
        }
        
        displayClanInfo(player, clan);
    }
    
    private void displayClanInfo(Player player, Clan clan) {
        player.sendMessage(MessageUtil.divider());
        player.sendMessage(MessageUtil.format(""));
        player.sendMessage(MessageUtil.format(MessageUtil.formatClanTag(clan) + " &7" + clan.getName()));
        player.sendMessage(MessageUtil.format(""));
        
        // Lider
        ClanMember leader = clan.getLeader();
        String leaderName = leader != null ? leader.getPlayerName() : "Nieznany";
        player.sendMessage(MessageUtil.format("&8» &7Lider: &f" + leaderName));
        player.sendMessage(MessageUtil.format("&8» &7Kolor tagu: "
                + clan.getColor() + ClanColors.displayName(clan.getColor())));
        
        // Członkowie
        int onlineCount = clanManager.getOnlineMemberCount(clan);
        int totalCount = clan.getMemberCount();
        player.sendMessage(MessageUtil.format("&8» &7Członkowie: &f" + onlineCount + "&7/&f" + totalCount
                + " &8(&7max " + ClanManager.MAX_MEMBERS + "&8)"));
        
        // Data utworzenia
        String createdDate = clan.getCreatedAt().format(dateFormatter);
        player.sendMessage(MessageUtil.format("&8» &7Data utworzenia: &f" + createdDate));
        
        // Lista członków online
        player.sendMessage(MessageUtil.format(""));
        player.sendMessage(MessageUtil.format("&7Członkowie online:"));
        
        boolean hasOnlineMembers = false;
        for (ClanMember member : clan.getMembers().values()) {
            Player memberPlayer = Bukkit.getPlayer(member.getPlayerUUID());
            if (memberPlayer != null && memberPlayer.isOnline()) {
                hasOnlineMembers = true;
                player.sendMessage(MessageUtil.format("&8» &a" + member.getPlayerName() + 
                    " &8[&f" + member.getRank().getDisplayName() + "&8]"));
            }
        }
        
        if (!hasOnlineMembers) {
            player.sendMessage(MessageUtil.format("&8» &7Brak online"));
        }
        
        player.sendMessage(MessageUtil.format(""));
        player.sendMessage(MessageUtil.divider());
    }
    
    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            List<String> tags = new ArrayList<>();
            for (Clan clan : clanManager.getAllClans()) {
                if (clan.getTag().toLowerCase().startsWith(args[0].toLowerCase())) {
                    tags.add(clan.getTag());
                }
            }
            return tags;
        }
        return Collections.emptyList();
    }
}