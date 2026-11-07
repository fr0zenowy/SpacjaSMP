package pl.zamrozenie.clans.listeners;

import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.models.Clan;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Brak friendly fire między członkami tego samego klanu. */
public class CombatListener implements Listener {
    
    private final ClanManager clanManager;
    
    public CombatListener(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (attacker.equals(victim)) return;
        
        Clan attackerClan = clanManager.getPlayerClan(attacker.getUniqueId());
        Clan victimClan = clanManager.getPlayerClan(victim.getUniqueId());
        if (attackerClan == null || victimClan == null) return;
        
        if (attackerClan.getId() == victimClan.getId()) {
            event.setCancelled(true);
        }
    }
}
