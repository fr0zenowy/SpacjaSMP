package pl.zamrozenie.clans.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.managers.DataManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.models.ClanMember;
import pl.zamrozenie.clans.utils.MessageUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Tag klanu na tabie (playerListName), przy nicku i nad głową (scoreboard team).
 * Odświeża też custom scoreboardy (boss/smok), żeby tag nie znikał.
 */
public class PlayerListener implements Listener {

    private final Plugin plugin;
    private final ClanManager clanManager;
    private final DataManager dataManager;

    public PlayerListener(Plugin plugin, ClanManager clanManager, DataManager dataManager) {
        this.plugin = plugin;
        this.clanManager = clanManager;
        this.dataManager = dataManager;
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAllBoards, 40L, 40L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        if (clan != null) {
            ClanMember member = clan.getMember(player.getUniqueId());
            if (member != null) {
                member.setPlayerName(player.getName());
            }
        }
        applyNames(player);
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshAllBoards, 2L);
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshAllBoards, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        dataManager.setClanChatEnabled(player.getUniqueId(), false);
        removeClanTeams(player);
        player.setDisplayName(player.getName());
        player.setPlayerListName(null);
    }

    public void updatePlayerNametag(Player player, Clan clan) {
        applyNames(player);
        refreshAllBoards();
    }

    public void removePlayerTag(Player player) {
        removeClanTeams(player);
        player.setDisplayName(player.getName());
        player.setPlayerListName(null);
        refreshAllBoards();
    }

    public void refreshClan(Clan clan) {
        if (clan == null) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (clan.hasMember(online.getUniqueId())) {
                applyNames(online);
            }
        }
        refreshAllBoards();
    }

    public void refreshAllBoards() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            applyNames(online);
        }
        Set<Scoreboard> boards = new HashSet<>();
        boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player online : Bukkit.getOnlinePlayers()) {
            boards.add(online.getScoreboard());
        }
        for (Scoreboard board : boards) {
            applyToBoard(board);
        }
    }

    private void applyNames(Player player) {
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.setDisplayName(player.getName());
            player.setPlayerListName(null);
            return;
        }
        String tagged = MessageUtil.formatClanTag(clan) + " " + ChatColor.WHITE + player.getName();
        player.setDisplayName(tagged);
        player.setPlayerListName(tagged);
    }

    private void applyToBoard(Scoreboard board) {
        if (board == null) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            Clan clan = clanManager.getPlayerClan(online.getUniqueId());
            if (clan == null) {
                removeFromClanTeams(board, online);
                continue;
            }
            Team team = clanTeam(board, clan);
            team.setPrefix(MessageUtil.formatClanTag(clan) + " ");
            team.setColor(ChatColor.WHITE);
            if (!team.hasPlayer(online)) {
                removeFromClanTeams(board, online);
                team.addPlayer(online);
            }
        }
    }

    private static Team clanTeam(Scoreboard board, Clan clan) {
        String name = teamName(clan);
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        return team;
    }

    private static String teamName(Clan clan) {
        String name = "clan_" + clan.getId();
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    private void removeClanTeams(Player player) {
        Set<Scoreboard> boards = new HashSet<>();
        boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
        boards.add(player.getScoreboard());
        for (Scoreboard board : boards) {
            removeFromClanTeams(board, player);
        }
    }

    private static void removeFromClanTeams(Scoreboard board, Player player) {
        if (board == null) return;
        for (Team team : board.getTeams()) {
            if (team.getName().startsWith("clan_") && team.hasPlayer(player)) {
                team.removePlayer(player);
            }
        }
    }
}
