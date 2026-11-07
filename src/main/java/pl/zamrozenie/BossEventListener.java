package pl.zamrozenie;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class BossEventListener implements Listener {

    private final BossEventManager events;

    public BossEventListener(BossEventManager events) {
        this.events = events;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onScale(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!events.isBoss(living)) return;
        events.scaleIncomingDamage(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!events.isBoss(living)) return;
        Player attacker = attacker(event);
        if (attacker == null) return;
        events.registerDamage(attacker, events.toVirtualDamage(event.getFinalDamage()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!events.isBoss(event.getEntity())) return;
        event.getDrops().clear();
        events.onBossDeath(event.getEntity(), event);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        events.addJoiningPlayer(event.getPlayer());
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
