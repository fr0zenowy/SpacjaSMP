package pl.zamrozenie;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class StatsListener implements Listener {

    private final StatsManager stats;

    public StatsListener(StatsManager stats) {
        this.stats = stats;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        stats.rememberName(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        stats.recordDeath(victim, killer);
    }
}
