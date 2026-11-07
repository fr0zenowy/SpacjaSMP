package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MarketChatListener implements Listener {

    private final MarketManager market;

    public MarketChatListener(MarketManager market) {
        this.market = market;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!market.hasSearchPrompt(player.getUniqueId())) return;
        event.setCancelled(true);
        String raw = ChatColor.stripColor(event.getMessage()).trim();
        market.getPlugin().getServer().getScheduler().runTask(market.getPlugin(),
                () -> market.finishSearch(player, raw));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        market.onQuit(event.getPlayer().getUniqueId());
    }
}
