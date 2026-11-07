package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TpaManager {

    private static final int EXPIRE_SECONDS = 60;

    private final ZamrozeniePlugin plugin;

    // target UUID -> requester UUID
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    // requester UUID -> expiry task
    private final Map<UUID, BukkitTask> expiryTasks = new HashMap<>();

    public TpaManager(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
    }

    public void sendRequest(Player requester, Player target) {
        // Anuluj poprzednie żądanie tego gracza jeśli istnieje
        cancelRequest(requester.getUniqueId());

        pendingRequests.put(target.getUniqueId(), requester.getUniqueId());

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequests.remove(target.getUniqueId(), requester.getUniqueId())) {
                expiryTasks.remove(requester.getUniqueId());
                requester.sendMessage(ChatColor.GRAY + "Twoje żądanie TPA do " + ChatColor.WHITE + target.getName() + ChatColor.GRAY + " wygasło.");
                target.sendMessage(ChatColor.GRAY + "Żądanie TPA od " + ChatColor.WHITE + requester.getName() + ChatColor.GRAY + " wygasło.");
            }
        }, EXPIRE_SECONDS * 20L);

        expiryTasks.put(requester.getUniqueId(), task);

        requester.sendMessage(
            ChatColor.AQUA + "✉ Wysłano żądanie TPA do " + ChatColor.WHITE + target.getName() +
            ChatColor.GRAY + ". Wygaśnie za " + EXPIRE_SECONDS + "s."
        );
        target.sendMessage(
            ChatColor.AQUA + "✉ " + ChatColor.WHITE + requester.getName() +
            ChatColor.AQUA + " chce się do Ciebie teleportować!"
        );
        target.sendMessage(
            ChatColor.GREEN + "  /tpaccept " + ChatColor.GRAY + "- akceptuj  " +
            ChatColor.RED + "  /tpadeny " + ChatColor.GRAY + "- odrzuć"
        );
    }

    public boolean acceptRequest(Player target) {
        UUID requesterUUID = pendingRequests.remove(target.getUniqueId());
        if (requesterUUID == null) return false;

        cancelExpiryTask(requesterUUID);

        Player requester = Bukkit.getPlayer(requesterUUID);
        if (requester == null || !requester.isOnline()) {
            target.sendMessage(ChatColor.RED + "Gracz który wysłał żądanie już nie jest online.");
            return true;
        }

        requester.teleport(target.getLocation());
        requester.sendMessage(ChatColor.GREEN + "✔ Zaakceptowano! Teleportowano do " + ChatColor.WHITE + target.getName() + ChatColor.GREEN + ".");
        target.sendMessage(ChatColor.GREEN + "✔ Zaakceptowałeś żądanie od " + ChatColor.WHITE + requester.getName() + ChatColor.GREEN + ".");
        return true;
    }

    public boolean denyRequest(Player target) {
        UUID requesterUUID = pendingRequests.remove(target.getUniqueId());
        if (requesterUUID == null) return false;

        cancelExpiryTask(requesterUUID);

        Player requester = Bukkit.getPlayer(requesterUUID);
        target.sendMessage(ChatColor.RED + "✖ Odrzuciłeś żądanie TPA.");
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(ChatColor.RED + "✖ " + ChatColor.WHITE + target.getName() + ChatColor.RED + " odrzucił Twoje żądanie TPA.");
        }
        return true;
    }

    public boolean hasPendingRequest(Player target) {
        return pendingRequests.containsKey(target.getUniqueId());
    }

    private void cancelRequest(UUID requesterUUID) {
        pendingRequests.values().removeIf(v -> v.equals(requesterUUID));
        cancelExpiryTask(requesterUUID);
    }

    private void cancelExpiryTask(UUID requesterUUID) {
        BukkitTask task = expiryTasks.remove(requesterUUID);
        if (task != null) task.cancel();
    }
}
