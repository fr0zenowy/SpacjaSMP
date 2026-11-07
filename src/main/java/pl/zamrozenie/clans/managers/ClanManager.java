package pl.zamrozenie.clans.managers;

import pl.zamrozenie.clans.models.*;
import pl.zamrozenie.clans.listeners.PlayerListener;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * ClanManager zarządza logiką biznesową klanów
 */
public class ClanManager {
    
    private final Plugin plugin;
    private final DataManager dataManager;
    private PlayerListener playerListener; // Opcjonalne - dla aktualizacji tagów
    
    // Zaproszenia do klanów (UUID gracza -> Clan który zaprosił)
    private final Map<UUID, Clan> pendingInvites = new HashMap<>();
    
    // Timeout dla zaproszeń (60 sekund)
    private static final long INVITE_TIMEOUT_MS = 60_000L;
    private final Map<UUID, Long> inviteExpirations = new HashMap<>();
    
    public ClanManager(Plugin plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        
        // Czyszczenie wygasłych zaproszeń co 30 sekund
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredInvites, 600L, 600L);
    }
    
    /**
     * Ustawia PlayerListener do aktualizacji tagów
     */
    public void setPlayerListener(PlayerListener playerListener) {
        this.playerListener = playerListener;
    }
    
    // ─── CLAN OPERATIONS ────────────────────────────────────────────────────────
    
    public String createClan(Player player, String tag, String name) {
        // Walidacja długości tagu
        if (tag.length() < 3 || tag.length() > 6) {
            return MessageUtil.error("Tag musi mieć od 3 do 6 znaków!");
        }
        
        // Walidacja znaków w tagu (tylko litery i cyfry)
        if (!tag.matches("[a-zA-Z0-9]+")) {
            return MessageUtil.error("Tag może zawierać tylko litery i cyfry!");
        }
        
        // Walidacja długości nazwy
        if (name.length() < 3 || name.length() > 32) {
            return MessageUtil.error("Nazwa musi mieć od 3 do 32 znaków!");
        }
        
        // Sprawdź czy gracz już jest w klanie
        if (dataManager.getPlayerClan(player.getUniqueId()) != null) {
            return MessageUtil.error("Już należysz do klanu!");
        }
        
        // Sprawdź czy tag jest zajęty
        if (dataManager.getClanByTag(tag) != null) {
            return MessageUtil.error("Klan o takim tagu już istnieje!");
        }
        
        // Sprawdź czy nazwa jest zajęta
        if (dataManager.getClanByName(name) != null) {
            return MessageUtil.error("Klan o takiej nazwie już istnieje!");
        }
        
        // Stwórz klan
        Clan clan = dataManager.createClan(tag, name, player.getUniqueId());
        
        // Zaktualizuj nazwę lidera
        ClanMember leader = clan.getMember(player.getUniqueId());
        if (leader != null) {
            leader.setPlayerName(player.getName());
        }
        
        // Zapisz zmiany
        dataManager.saveData();
        
        // Zaktualizuj nametag natychmiast
        if (playerListener != null) {
            playerListener.updatePlayerNametag(player, clan);
        }
        
        return null; // Sukces
    }
    
    public String deleteClan(Player player) {
        Clan clan = dataManager.getPlayerClan(player.getUniqueId());
        
        if (clan == null) {
            return MessageUtil.error("Nie należysz do żadnego klanu!");
        }
        
        ClanMember member = clan.getMember(player.getUniqueId());
        if (!member.isLeader()) {
            return MessageUtil.error("Nie masz uprawnień! &7(Wymagana ranga: &cLIDER&7)");
        }
        
        // Usuń tagi ze wszystkich członków klanu
        if (playerListener != null) {
            for (UUID memberUUID : clan.getMembers().keySet()) {
                Player memberPlayer = Bukkit.getPlayer(memberUUID);
                if (memberPlayer != null && memberPlayer.isOnline()) {
                    playerListener.removePlayerTag(memberPlayer);
                }
            }
        }
        
        dataManager.deleteClan(clan.getId());
        
        // Zapisz zmiany
        dataManager.saveData();
        
        return null; // Sukces
    }
    
    public Clan getPlayerClan(UUID playerUUID) {
        return dataManager.getPlayerClan(playerUUID);
    }
    
    public Clan getClanByTag(String tag) {
        return dataManager.getClanByTag(tag);
    }
    
    public Collection<Clan> getAllClans() {
        return dataManager.getAllClans();
    }

    public String setClanColor(Player player, ChatColor color) {
        Clan clan = dataManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            return MessageUtil.error("Nie należysz do żadnego klanu!");
        }
        ClanMember member = clan.getMember(player.getUniqueId());
        if (member == null || !member.canManageMembers()) {
            return MessageUtil.error("Nie masz uprawnień! &7(Wymagana ranga: &cADMIN&7)");
        }
        clan.setColor(color);
        dataManager.saveData();
        if (playerListener != null) {
            playerListener.refreshClan(clan);
        }
        return null;
    }
    
    // ─── MEMBER OPERATIONS ──────────────────────────────────────────────────────
    
    public static final int MAX_MEMBERS = 4;

    public String invitePlayer(Player inviter, Player target) {
        Clan clan = dataManager.getPlayerClan(inviter.getUniqueId());
        
        if (clan == null) {
            return MessageUtil.error("Nie należysz do żadnego klanu!");
        }
        
        ClanMember inviterMember = clan.getMember(inviter.getUniqueId());
        if (!inviterMember.canManageMembers()) {
            return MessageUtil.error("Nie masz uprawnień! &7(Wymagana ranga: &cADMIN&7)");
        }
        
        // Sprawdź czy cel już jest w klanie
        if (dataManager.getPlayerClan(target.getUniqueId()) != null) {
            return MessageUtil.error("Ten gracz już należy do klanu!");
        }

        if (clan.getMemberCount() >= MAX_MEMBERS) {
            return MessageUtil.error("Klan jest pełny! Maksimum " + MAX_MEMBERS + " osoby.");
        }
        
        // Zapisz zaproszenie
        pendingInvites.put(target.getUniqueId(), clan);
        inviteExpirations.put(target.getUniqueId(), System.currentTimeMillis() + INVITE_TIMEOUT_MS);
        
        return null; // Sukces
    }
    
    public String acceptInvite(Player player) {
        Clan clan = pendingInvites.get(player.getUniqueId());
        
        if (clan == null) {
            return MessageUtil.error("Nie masz żadnego zaproszenia do klanu!");
        }
        
        // Sprawdź czy zaproszenie nie wygasło
        Long expiration = inviteExpirations.get(player.getUniqueId());
        if (expiration == null || System.currentTimeMillis() > expiration) {
            pendingInvites.remove(player.getUniqueId());
            inviteExpirations.remove(player.getUniqueId());
            return MessageUtil.error("Zaproszenie wygasło!");
        }

        if (clan.getMemberCount() >= MAX_MEMBERS) {
            pendingInvites.remove(player.getUniqueId());
            inviteExpirations.remove(player.getUniqueId());
            return MessageUtil.error("Klan jest pełny! Maksimum " + MAX_MEMBERS + " osoby.");
        }
        
        // Dodaj gracza do klanu
        boolean success = dataManager.addMemberToClan(player.getUniqueId(), player.getName(), clan.getId());
        
        if (!success) {
            return MessageUtil.error("Nie można dołączyć do klanu!");
        }
        
        // Usuń zaproszenie
        pendingInvites.remove(player.getUniqueId());
        inviteExpirations.remove(player.getUniqueId());
        
        // Zapisz zmiany
        dataManager.saveData();
        
        // Zaktualizuj nametag natychmiast
        if (playerListener != null) {
            playerListener.updatePlayerNametag(player, clan);
        }
        
        return null; // Sukces
    }
    
    public String denyInvite(Player player) {
        if (!pendingInvites.containsKey(player.getUniqueId())) {
            return MessageUtil.error("Nie masz żadnego zaproszenia do klanu!");
        }
        
        pendingInvites.remove(player.getUniqueId());
        inviteExpirations.remove(player.getUniqueId());
        
        return null; // Sukces
    }
    
    public Clan getPendingInvite(UUID playerUUID) {
        return pendingInvites.get(playerUUID);
    }
    
    public String kickPlayer(Player kicker, String targetName) {
        Clan clan = dataManager.getPlayerClan(kicker.getUniqueId());
        
        if (clan == null) {
            return MessageUtil.error("Nie należysz do żadnego klanu!");
        }
        
        ClanMember kickerMember = clan.getMember(kicker.getUniqueId());
        if (!kickerMember.canManageMembers()) {
            return MessageUtil.error("Nie masz uprawnień!");
        }
        
        // Znajdź gracza po nazwie
        ClanMember targetMember = null;
        for (ClanMember member : clan.getMembers().values()) {
            if (member.getPlayerName().equalsIgnoreCase(targetName)) {
                targetMember = member;
                break;
            }
        }
        
        if (targetMember == null) {
            return MessageUtil.error("Ten gracz nie należy do klanu!");
        }
        
        // Sprawdź czy nie próbuje wyrzucić samego siebie
        if (targetMember.getPlayerUUID().equals(kicker.getUniqueId())) {
            return MessageUtil.error("Użyj &f/klan opusc &caby opuścić klan!");
        }
        
        // Admin nie może wyrzucić lidera ani innego admina
        if (kickerMember.getRank() == ClanRank.ADMIN) {
            if (targetMember.isLeader() || targetMember.isAdmin()) {
                return MessageUtil.error("Nie możesz wyrzucić tego gracza!");
            }
        }
        
        // Wyrzuć gracza
        dataManager.removeMemberFromClan(targetMember.getPlayerUUID(), clan.getId());
        
        // Usuń tag z wyrzuconego gracza
        if (playerListener != null) {
            Player targetPlayer = Bukkit.getPlayer(targetMember.getPlayerUUID());
            if (targetPlayer != null && targetPlayer.isOnline()) {
                playerListener.removePlayerTag(targetPlayer);
            }
        }
        
        // Zapisz zmiany
        dataManager.saveData();
        
        return null; // Sukces
    }
    
    public String leaveClan(Player player) {
        Clan clan = dataManager.getPlayerClan(player.getUniqueId());
        
        if (clan == null) {
            return MessageUtil.error("Nie należysz do żadnego klanu!");
        }
        
        ClanMember member = clan.getMember(player.getUniqueId());
        if (member.isLeader()) {
            return MessageUtil.error("Jako lider musisz rozwiązać klan lub przekazać przywództwo!");
        }
        
        dataManager.removeMemberFromClan(player.getUniqueId(), clan.getId());
        
        // Usuń tag z gracza
        if (playerListener != null) {
            playerListener.removePlayerTag(player);
        }
        
        // Zapisz zmiany
        dataManager.saveData();
        
        return null; // Sukces
    }
    
    public String promoteMember(Player promoter, String targetName) {
        Clan clan = dataManager.getPlayerClan(promoter.getUniqueId());
        
        if (clan == null) {
            return MessageUtil.error("Nie należysz do żadnego klanu!");
        }
        
        ClanMember promoterMember = clan.getMember(promoter.getUniqueId());
        if (!promoterMember.isLeader()) {
            return MessageUtil.error("Nie masz uprawnień!");
        }
        
        // Znajdź gracza po nazwie
        ClanMember targetMember = null;
        for (ClanMember member : clan.getMembers().values()) {
            if (member.getPlayerName().equalsIgnoreCase(targetName)) {
                targetMember = member;
                break;
            }
        }
        
        if (targetMember == null) {
            return MessageUtil.error("Ten gracz nie należy do klanu!");
        }
        
        if (targetMember.getRank() == ClanRank.MEMBER) {
            dataManager.updateMemberRank(targetMember.getPlayerUUID(), clan.getId(), ClanRank.ADMIN);
            
            // Zapisz zmiany
            dataManager.saveData();
        } else {
            return MessageUtil.error("Ten gracz już jest adminem!");
        }
        
        return null; // Sukces
    }
    
    public String demoteMember(Player demoter, String targetName) {
        Clan clan = dataManager.getPlayerClan(demoter.getUniqueId());
        
        if (clan == null) {
            return MessageUtil.error("Nie należysz do żadnego klanu!");
        }
        
        ClanMember demoterMember = clan.getMember(demoter.getUniqueId());
        if (!demoterMember.isLeader()) {
            return MessageUtil.error("Nie masz uprawnień!");
        }
        
        // Znajdź gracza po nazwie
        ClanMember targetMember = null;
        for (ClanMember member : clan.getMembers().values()) {
            if (member.getPlayerName().equalsIgnoreCase(targetName)) {
                targetMember = member;
                break;
            }
        }
        
        if (targetMember == null) {
            return MessageUtil.error("Ten gracz nie należy do klanu!");
        }
        
        if (targetMember.getRank() == ClanRank.ADMIN) {
            dataManager.updateMemberRank(targetMember.getPlayerUUID(), clan.getId(), ClanRank.MEMBER);
            
            // Zapisz zmiany
            dataManager.saveData();
        } else {
            return MessageUtil.error("Ten gracz nie jest adminem!");
        }
        
        return null; // Sukces
    }
    
    // ─── UTILITY ────────────────────────────────────────────────────────────────
    
    private void cleanupExpiredInvites() {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();
        
        for (Map.Entry<UUID, Long> entry : inviteExpirations.entrySet()) {
            if (now > entry.getValue()) {
                expired.add(entry.getKey());
            }
        }
        
        for (UUID uuid : expired) {
            pendingInvites.remove(uuid);
            inviteExpirations.remove(uuid);
        }
    }
    
    public int getOnlineMemberCount(Clan clan) {
        int count = 0;
        for (ClanMember member : clan.getMembers().values()) {
            Player player = Bukkit.getPlayer(member.getPlayerUUID());
            if (player != null && player.isOnline()) {
                count++;
            }
        }
        return count;
    }
}