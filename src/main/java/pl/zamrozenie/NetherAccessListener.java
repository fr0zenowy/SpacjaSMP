package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;

/** Nether otwarty tylko dla operatorow (permission zamrozenie.nether). */
public class NetherAccessListener implements Listener {

    public static final String PERM = "zamrozenie.nether";

    private final ZamrozeniePlugin plugin;

    public NetherAccessListener(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean locked() {
        return plugin.getConfig().getBoolean("nether-access.lock-players", false);
    }

    private static boolean nether(Location loc) {
        return loc != null && loc.getWorld() != null
                && loc.getWorld().getEnvironment() == World.Environment.NETHER;
    }

    private static boolean canEnter(Player player) {
        return player != null && (player.isOp() || player.hasPermission(PERM));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!locked() || canEnter(event.getPlayer())) return;
        if (!nether(event.getTo())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "Nether jest wyłączony.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!locked() || canEnter(event.getPlayer())) return;
        if (nether(event.getFrom()) || !nether(event.getTo())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "Nether jest wyłączony.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreate(PortalCreateEvent event) {
        if (!locked()) return;
        if (event.getReason() != PortalCreateEvent.CreateReason.FIRE) return;
        Entity entity = event.getEntity();
        if (entity instanceof Player player && canEnter(player)) return;
        event.setCancelled(true);
        if (entity instanceof Player player) {
            player.sendMessage(ChatColor.RED + "Nie możesz otworzyć portalu do Netheru.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!locked()) return;
        Player player = event.getPlayer();
        if (canEnter(player) || !nether(player.getLocation())) return;
        Location spawn = overworldSpawn();
        if (spawn == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.teleport(spawn);
        });
        player.sendMessage(ChatColor.RED + "Nether jest wyłączony. Wyrzucono Cię na spawn.");
    }

    private static Location overworldSpawn() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return world.getSpawnLocation();
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
    }
}
