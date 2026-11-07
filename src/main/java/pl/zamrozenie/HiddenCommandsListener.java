package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

public class HiddenCommandsListener implements Listener {

    private static final Set<String> BLOCKED = Set.of(
            "plugins", "pl", "plugin", "ver", "version", "about",
            "icanhasbukkit", "?"
    );

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer().hasPermission("zamrozenie.seeplugins")) return;
        String raw = event.getMessage();
        if (raw == null || raw.isEmpty() || raw.charAt(0) != '/') return;
        String cmd = raw.substring(1).trim();
        int space = cmd.indexOf(' ');
        if (space >= 0) cmd = cmd.substring(0, space);
        cmd = cmd.toLowerCase(Locale.ROOT);
        int colon = cmd.indexOf(':');
        if (colon >= 0) cmd = cmd.substring(colon + 1);
        if (!BLOCKED.contains(cmd)) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "Nie możesz użyć tej komendy.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (event.getPlayer().hasPermission("zamrozenie.seeplugins")) return;
        Iterator<String> it = event.getCommands().iterator();
        while (it.hasNext()) {
            String name = it.next().toLowerCase(Locale.ROOT);
            int colon = name.indexOf(':');
            if (colon >= 0) name = name.substring(colon + 1);
            if (BLOCKED.contains(name)) it.remove();
        }
    }
}
