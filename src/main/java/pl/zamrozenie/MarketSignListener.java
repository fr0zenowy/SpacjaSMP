package pl.zamrozenie;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

public class MarketSignListener implements Listener {

    private final MarketManager market;

    public MarketSignListener(MarketManager market) {
        this.market = market;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSign(SignChangeEvent event) {
        if (!market.hasSignEdit(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        market.finishSignSearch(event.getPlayer(), event.getLines());
    }
}
