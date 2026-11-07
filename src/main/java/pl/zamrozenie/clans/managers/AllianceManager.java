package pl.zamrozenie.clans.managers;

import pl.zamrozenie.clans.models.*;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * AllianceManager zarządza sojuszami między klanami
 */
public class AllianceManager {
    
    private final Plugin plugin;
    private final DataManager dataManager;
    
    public AllianceManager(Plugin plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }
    
    // ─── ALLIANCE OPERATIONS ────────────────────────────────────────────────────
    
    public String sendAllianceRequest(Player sender, String targetTag) {
        Clan senderClan = dataManager.getPlayerClan(sender.getUniqueId());
        
        if (senderClan == null) {
            return MessageUtil.error("Nie należysz do żadnego klanu!");
        }
        
        ClanMember senderMember = senderClan.getMember(sender.getUniqueId());
        if (!senderMember.canManageMembers()) {
            return MessageUtil.error("Nie masz uprawnień!");
        }
        
        Clan targetClan = dataManager.getClanByTag(targetTag);
        if (targetClan == null) {
            return MessageUtil.error("Nie znaleziono klanu o tagu &f" + targetTag + "&c!");
        }
        
        if (senderClan.getId() == targetClan.getId()) {
            return MessageUtil.error("Nie możesz wysłać sojuszu do własnego klanu!");
        }
        
        // Sprawdź czy sojusz już istnieje
        Alliance existing = dataManager.getAllianceBetweenClans(senderClan.getId(), targetClan.getId());
        if (existing != null) {
            if (existing.getStatus() == Alliance.AllianceStatus.ACCEPTED) {
                return MessageUtil.error("Już macie sojusz z tym klanem!");
            } else {
                return MessageUtil.error("Zaproszenie do sojuszu zostało już wysłane!");
            }
        }
        
        // Tutaj można dodać sprawdzenie limitu sojuszy z configu
        
        // Stwórz sojusz w statusie PENDING
        Alliance alliance = dataManager.createAlliance(senderClan.getId(), targetClan.getId(), senderClan.getId());
        
        if (alliance == null) {
            return MessageUtil.error("Nie można utworzyć sojuszu!");
        }
        
        return null; // Sukces
    }
    
    public String acceptAlliance(Player accepter, String senderTag) {
        Clan accepterClan = dataManager.getPlayerClan(accepter.getUniqueId());
        
        if (accepterClan == null) {
            return MessageUtil.error("Nie należysz do żadnego klanu!");
        }
        
        ClanMember accepterMember = accepterClan.getMember(accepter.getUniqueId());
        if (!accepterMember.canManageMembers()) {
            return MessageUtil.error("Nie masz uprawnień!");
        }
        
        Clan senderClan = dataManager.getClanByTag(senderTag);
        if (senderClan == null) {
            return MessageUtil.error("Nie znaleziono klanu o tagu &f" + senderTag + "&c!");
        }
        
        Alliance alliance = dataManager.getAllianceBetweenClans(accepterClan.getId(), senderClan.getId());
        
        if (alliance == null) {
            return MessageUtil.error("Nie masz zaproszenia do sojuszu od tego klanu!");
        }
        
        if (alliance.getStatus() == Alliance.AllianceStatus.ACCEPTED) {
            return MessageUtil.error("Już macie sojusz z tym klanem!");
        }
        
        // Zaakceptuj sojusz
        alliance.setStatus(Alliance.AllianceStatus.ACCEPTED);
        
        return null; // Sukces
    }
    
    public String breakAlliance(Player breaker, String targetTag) {
        Clan breakerClan = dataManager.getPlayerClan(breaker.getUniqueId());
        
        if (breakerClan == null) {
            return MessageUtil.error("Nie należysz do żadnego klanu!");
        }
        
        ClanMember breakerMember = breakerClan.getMember(breaker.getUniqueId());
        if (!breakerMember.isLeader()) {
            return MessageUtil.error("Nie masz uprawnień! &7(Wymagana ranga: &cLIDER&7)");
        }
        
        Clan targetClan = dataManager.getClanByTag(targetTag);
        if (targetClan == null) {
            return MessageUtil.error("Nie znaleziono klanu o tagu &f" + targetTag + "&c!");
        }
        
        Alliance alliance = dataManager.getAllianceBetweenClans(breakerClan.getId(), targetClan.getId());
        
        if (alliance == null) {
            return MessageUtil.error("Nie macie sojuszu z tym klanem!");
        }
        
        dataManager.deleteAlliance(alliance);
        
        return null; // Sukces
    }
    
    public List<Clan> getAlliedClans(Clan clan) {
        List<Clan> allies = new ArrayList<>();
        List<Alliance> alliances = dataManager.getClanAlliances(clan.getId());
        
        for (Alliance alliance : alliances) {
            if (alliance.getStatus() == Alliance.AllianceStatus.ACCEPTED) {
                int allyClanId = alliance.getOtherClanId(clan.getId());
                Clan allyClan = dataManager.getClanById(allyClanId);
                if (allyClan != null) {
                    allies.add(allyClan);
                }
            }
        }
        
        return allies;
    }
    
    public List<Alliance> getPendingAllianceRequests(Clan clan) {
        List<Alliance> pending = new ArrayList<>();
        List<Alliance> alliances = dataManager.getClanAlliances(clan.getId());
        
        for (Alliance alliance : alliances) {
            if (alliance.getStatus() == Alliance.AllianceStatus.PENDING 
                && alliance.getRequestedBy() != clan.getId()) {
                pending.add(alliance);
            }
        }
        
        return pending;
    }
    
    public boolean areAllied(Clan clan1, Clan clan2) {
        if (clan1 == null || clan2 == null) return false;
        if (clan1.getId() == clan2.getId()) return true; // Same clan
        
        Alliance alliance = dataManager.getAllianceBetweenClans(clan1.getId(), clan2.getId());
        return alliance != null && alliance.getStatus() == Alliance.AllianceStatus.ACCEPTED;
    }
}