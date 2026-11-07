package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HomeManager {

    public static final int MIN_SLOT = 1;
    public static final int MAX_SLOT = 2;

    private final ZamrozeniePlugin plugin;
    private File dataFile;
    private FileConfiguration data;
    private final Map<UUID, BukkitTask> pendingTeleport = new HashMap<>();
    private final Map<UUID, Location> warmupStart = new HashMap<>();

    public HomeManager(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "homes.yml");
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        migrateAllLegacyHomesOnLoad();
    }

    public void setHome(Player player, int slot, Location loc) {
        String path = homePath(player.getUniqueId(), slot);
        data.set(path + ".world", loc.getWorld().getName());
        data.set(path + ".x", loc.getX());
        data.set(path + ".y", loc.getY());
        data.set(path + ".z", loc.getZ());
        data.set(path + ".yaw", (double) loc.getYaw());
        data.set(path + ".pitch", (double) loc.getPitch());
        save();
    }

    public void setHome(Player player, Location loc) {
        setHome(player, 1, loc);
    }

    public Location getHome(Player player, int slot) {
        return getHomeByUUID(player.getUniqueId(), slot);
    }

    public Location getHome(Player player) {
        return getHome(player, 1);
    }

    public Location getHomeByUUID(UUID uuid, int slot) {
        String path = homePath(uuid, slot);
        if (!data.contains(path + ".world")) return null;

        String worldName = data.getString(path + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = data.getDouble(path + ".x");
        double y = data.getDouble(path + ".y");
        double z = data.getDouble(path + ".z");
        float yaw = (float) data.getDouble(path + ".yaw");
        float pitch = (float) data.getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean hasHome(Player player, int slot) {
        return data.contains(homePath(player.getUniqueId(), slot) + ".world");
    }

    public boolean hasHome(Player player) {
        return hasHome(player, 1);
    }

    public void deleteHome(Player player, int slot) {
        data.set(homePath(player.getUniqueId(), slot), null);
        save();
    }

    public void deleteHome(Player player) {
        deleteHome(player, 1);
    }

    public boolean isValidSlot(int slot) {
        return slot >= MIN_SLOT && slot <= MAX_SLOT;
    }

    /** Start odliczania, potem teleport. Komenda i GUI. */
    public boolean teleportToHome(Player player, int slot) {
        if (!hasHome(player, slot)) {
            player.sendMessage(ChatColor.RED + "Nie masz ustawionego home " + slot + ". Użyj "
                    + ChatColor.WHITE + "/home set " + slot);
            return false;
        }
        Location home = getHome(player, slot);
        if (home == null) {
            player.sendMessage(ChatColor.RED + "Nie można znaleźć świata tego home.");
            return false;
        }

        int delay = Math.max(0, plugin.getConfig().getInt("home-cooldown-seconds", 5));
        if (delay == 0) {
            player.teleport(home);
            player.sendMessage(ChatColor.GREEN + "✔ Przeteleportowano do home " + slot + ".");
            return true;
        }

        UUID id = player.getUniqueId();
        cancelWarmup(id);
        warmupStart.put(id, player.getLocation().clone());
        player.sendMessage(ChatColor.GRAY + "Zostaniesz przeteleportowany za " + ChatColor.WHITE
                + delay + "s" + ChatColor.GRAY + ".");

        BukkitTask task = new BukkitRunnable() {
            int left = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelWarmup(id);
                    return;
                }
                Location origin = warmupStart.get(id);
                if (origin != null && (origin.getWorld() == null
                        || !origin.getWorld().equals(player.getWorld())
                        || player.getLocation().distance(origin) > 0.5)) {
                    player.sendMessage(ChatColor.RED + "Teleportacja anulowana. Ruszyłeś się.");
                    cancelWarmup(id);
                    return;
                }
                if (left <= 0) {
                    Location dest = getHome(player, slot);
                    if (dest != null) {
                        player.teleport(dest);
                        player.sendMessage(ChatColor.GREEN + "✔ Przeteleportowano do home " + slot + ".");
                    }
                    cancelWarmup(id);
                    return;
                }
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        pendingTeleport.put(id, task);
        return true;
    }

    public void cancelWarmup(UUID id) {
        BukkitTask task = pendingTeleport.remove(id);
        if (task != null) task.cancel();
        warmupStart.remove(id);
    }

    private String homePath(UUID uuid, int slot) {
        migrateLegacyHomeIfNeeded(uuid);
        return "homes." + uuid + "." + slot;
    }

    /** Jednorazowa migracja starych home (homes.uuid) -> slot 1; slot 2 zostaje pusty. */
    private void migrateAllLegacyHomesOnLoad() {
        if (!data.isConfigurationSection("homes")) return;
        boolean changed = false;
        for (String key : data.getConfigurationSection("homes").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                if (migrateLegacyHomeIfNeeded(uuid)) changed = true;
            } catch (IllegalArgumentException ignored) {
                // pomijamy klucze, ktore nie sa UUID
            }
        }
        if (changed) save();
    }

    /** @return true jesli wykonano migracje */
    private boolean migrateLegacyHomeIfNeeded(UUID uuid) {
        String legacyPath = "homes." + uuid;
        String slotOnePath = legacyPath + ".1";
        if (!data.contains(legacyPath + ".world") || data.contains(slotOnePath + ".world")) return false;

        data.set(slotOnePath + ".world", data.getString(legacyPath + ".world"));
        data.set(slotOnePath + ".x", data.getDouble(legacyPath + ".x"));
        data.set(slotOnePath + ".y", data.getDouble(legacyPath + ".y"));
        data.set(slotOnePath + ".z", data.getDouble(legacyPath + ".z"));
        data.set(slotOnePath + ".yaw", data.getDouble(legacyPath + ".yaw"));
        data.set(slotOnePath + ".pitch", data.getDouble(legacyPath + ".pitch"));

        data.set(legacyPath + ".world", null);
        data.set(legacyPath + ".x", null);
        data.set(legacyPath + ".y", null);
        data.set(legacyPath + ".z", null);
        data.set(legacyPath + ".yaw", null);
        data.set(legacyPath + ".pitch", null);
        return true;
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie mozna zapisac homes.yml: " + e.getMessage());
        }
    }
}
