package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Start bossa: czerwone szkło w kratach u góry.
 * Śmierć bossa: szkło wraca, iron bary znikają i wracają po 10 minutach.
 */
public class BossArenaBlocks {

    private static final class Saved {
        final String world;
        final int x, y, z;
        final String data;

        Saved(String world, int x, int y, int z, String data) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }
    }

    private final ZamrozeniePlugin plugin;
    private final File file;
    private final List<Saved> glassSaved = new ArrayList<>();
    private final List<Saved> barsSaved = new ArrayList<>();
    private BukkitTask barsRestoreTask;
    private MythicBoss lastType;

    public BossArenaBlocks(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boss-arena.yml");
        plugin.getServer().getScheduler().runTask(plugin, this::loadPending);
    }

    public void apply(MythicBoss type, World fallbackWorld) {
        if (type == null || !type.isEventEnabled()) return;
        restoreAll();
        lastType = type;

        World world = resolveWorld(type, fallbackWorld);
        if (world == null) {
            plugin.getLogger().warning("Arena bossa: nie ma świata do edycji bloków.");
            return;
        }

        int[] glassFrom = cuboid(type, "glass.from", 1512, -50, -946);
        int[] glassTo = cuboid(type, "glass.to", 1512, -45, -942);
        placeGlass(world, glassFrom, glassTo);
        persist();
    }

    public void onBossKilled(MythicBoss type, World fallbackWorld) {
        restoreGlass();
        MythicBoss boss = type != null ? type : lastType;
        if (boss == null || !boss.isEventEnabled()) return;

        World world = resolveWorld(boss, fallbackWorld);
        if (world == null) return;

        int[] barsFrom = cuboid(boss, "bars.from", 1518, -48, -927);
        int[] barsTo = cuboid(boss, "bars.to", 1516, -50, -926);
        removeBars(world, barsFrom, barsTo);
        persist();
        scheduleBarsRestore(restoreAt(boss));
    }

    public void restoreGlass() {
        applySaved(glassSaved);
        persist();
    }

    public void restoreAll() {
        cancelBarsTask();
        applySaved(glassSaved);
        applySaved(barsSaved);
        persist();
    }

    public void shutdown() {
        restoreAll();
    }

    private void restoreBars() {
        cancelBarsTask();
        applySaved(barsSaved);
        persist();
    }

    private void applySaved(List<Saved> list) {
        if (list.isEmpty()) return;
        for (Saved s : list) {
            World world = Bukkit.getWorld(s.world);
            if (world == null) continue;
            world.getChunkAt(s.x >> 4, s.z >> 4).load();
            try {
                BlockData data = Bukkit.createBlockData(s.data);
                world.getBlockAt(s.x, s.y, s.z).setBlockData(data, false);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "Arena bossa: nie da się przywrócić " + s.data, ex);
            }
        }
        list.clear();
    }

    private void placeGlass(World world, int[] from, int[] to) {
        Material glass = Material.RED_STAINED_GLASS;
        forEach(world, from, to, block -> {
            if (block.getType() == glass) return;
            glassSaved.add(remember(block));
            block.setType(glass, false);
        });
    }

    private void removeBars(World world, int[] from, int[] to) {
        forEach(world, from, to, block -> {
            if (block.getType() != Material.IRON_BARS) return;
            barsSaved.add(remember(block));
            block.setType(Material.AIR, false);
        });
    }

    private static Saved remember(Block block) {
        return new Saved(
                block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(),
                block.getBlockData().getAsString()
        );
    }

    private static void forEach(World world, int[] a, int[] b, java.util.function.Consumer<Block> consumer) {
        int minX = Math.min(a[0], b[0]), maxX = Math.max(a[0], b[0]);
        int minY = Math.min(a[1], b[1]), maxY = Math.max(a[1], b[1]);
        int minZ = Math.min(a[2], b[2]), maxZ = Math.max(a[2], b[2]);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.getChunkAt(x >> 4, z >> 4).load();
                for (int y = minY; y <= maxY; y++) {
                    consumer.accept(world.getBlockAt(x, y, z));
                }
            }
        }
    }

    private World resolveWorld(MythicBoss type, World fallback) {
        String name = plugin.getConfig().getString(type.configPath() + ".arena.world", "");
        if (name != null && !name.isBlank()) {
            World named = Bukkit.getWorld(name);
            if (named != null) return named;
        }
        return fallback;
    }

    private int[] cuboid(MythicBoss type, String key, int dx, int dy, int dz) {
        List<Integer> list = plugin.getConfig().getIntegerList(type.configPath() + ".arena." + key);
        if (list.size() >= 3) return new int[]{list.get(0), list.get(1), list.get(2)};
        return new int[]{dx, dy, dz};
    }

    private long restoreAt(MythicBoss type) {
        int seconds = Math.max(1, plugin.getConfig().getInt(type.configPath() + ".arena.restore-seconds", 600));
        return System.currentTimeMillis() + seconds * 1000L;
    }

    private void scheduleBarsRestore(long at) {
        cancelBarsTask();
        long delayTicks = Math.max(1L, (at - System.currentTimeMillis() + 49L) / 50L);
        barsRestoreTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::restoreBars, delayTicks);
        persist(at);
    }

    private void cancelBarsTask() {
        if (barsRestoreTask != null) {
            barsRestoreTask.cancel();
            barsRestoreTask = null;
        }
    }

    private void persist() {
        persist(0L);
    }

    private void persist(long barsRestoreAt) {
        if (glassSaved.isEmpty() && barsSaved.isEmpty()) {
            if (file.exists() && !file.delete()) {
                try {
                    new YamlConfiguration().save(file);
                } catch (IOException ignored) {
                }
            }
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        if (barsRestoreAt > 0) yaml.set("bars-restore-at", barsRestoreAt);
        writeList(yaml, "glass", glassSaved);
        writeList(yaml, "bars", barsSaved);
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Nie udało się zapisać boss-arena.yml", e);
        }
    }

    private static void writeList(YamlConfiguration yaml, String root, List<Saved> list) {
        int i = 0;
        for (Saved s : list) {
            String path = root + "." + i;
            yaml.set(path + ".world", s.world);
            yaml.set(path + ".x", s.x);
            yaml.set(path + ".y", s.y);
            yaml.set(path + ".z", s.z);
            yaml.set(path + ".data", s.data);
            i++;
        }
    }

    private void loadPending() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        glassSaved.clear();
        barsSaved.clear();
        readList(yaml.getConfigurationSection("glass"), glassSaved);
        readList(yaml.getConfigurationSection("bars"), barsSaved);
        // stary format (jedna lista "blocks") — przywróć od razu
        readList(yaml.getConfigurationSection("blocks"), glassSaved);

        if (!glassSaved.isEmpty()) restoreGlass();

        if (barsSaved.isEmpty()) {
            persist();
            return;
        }
        long at = yaml.getLong("bars-restore-at", yaml.getLong("restore-at", 0L));
        long delayMs = at - System.currentTimeMillis();
        if (delayMs <= 0) {
            restoreBars();
            return;
        }
        barsRestoreTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::restoreBars,
                Math.max(1L, delayMs / 50L));
    }

    private static void readList(ConfigurationSection root, List<Saved> into) {
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;
            String world = sec.getString("world");
            String data = sec.getString("data");
            if (world == null || data == null) continue;
            into.add(new Saved(world, sec.getInt("x"), sec.getInt("y"), sec.getInt("z"), data));
        }
    }
}
