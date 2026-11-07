package pl.zamrozenie;

import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class DropDeathListener implements Listener {

    private final DropManager dropManager;

    public DropDeathListener(DropManager dropManager) {
        this.dropManager = dropManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof EnderDragon) return;

        DropSource src = dropManager.matchEntity(entity);
        if (src == null || src == DropSource.ZRZUT || src == DropSource.WLADCA_ENDU) return;
        if (src == DropSource.WLADCA_LOCHOW) return;
        dropManager.addToDeath(src, event);
    }
}
