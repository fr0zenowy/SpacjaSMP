package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class StatsManager {

    public record Entry(UUID id, String name, long value) {}

    private final ZamrozeniePlugin plugin;
    private final File file;
    private final File historicalFile;
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private String fromLog;

    public StatsManager(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-stats.yml");
        this.historicalFile = new File(plugin.getDataFolder(), "historical-stats.yml");
        load();
        importLogsIfNeeded();
    }

    /** Przy pierwszej instalacji zlicza fragi i smierci z logow serwera. */
    private void importLogsIfNeeded() {
        if (fromLog != null && !fromLog.isEmpty()) return;
        LogStatsImporter.Result result = LogStatsImporter.scan(plugin);
        fromLog = LogStatsImporter.FROM_LOG;
        if (result == null) {
            save();
            plugin.getLogger().info("Brak logow do importu topki. Liczenie od teraz.");
            return;
        }
        for (Map.Entry<String, UUID> e : result.names.entrySet()) {
            names.putIfAbsent(e.getValue(), e.getKey());
        }
        for (Map.Entry<UUID, Integer> e : result.kills.entrySet()) {
            kills.put(e.getKey(), Math.max(kills.getOrDefault(e.getKey(), 0), e.getValue()));
        }
        for (Map.Entry<UUID, Integer> e : result.deaths.entrySet()) {
            deaths.put(e.getKey(), Math.max(deaths.getOrDefault(e.getKey(), 0), e.getValue()));
        }
        save();
        LogStatsImporter.writeSnapshot(historicalFile, result, names);
        plugin.getLogger().info("Zaimportowano topki z logow od " + fromLog
                + ": " + result.deathEvents + " smierci, "
                + result.kills.values().stream().mapToInt(Integer::intValue).sum() + " zabojstw.");
    }

    public void rememberName(Player player) {
        if (player == null) return;
        names.put(player.getUniqueId(), player.getName());
    }

    public void recordDeath(Player victim, Player killer) {
        if (victim == null) return;
        rememberName(victim);
        deaths.merge(victim.getUniqueId(), 1, Integer::sum);
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            rememberName(killer);
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
        }
        save();
    }

    public int getKills(UUID id) {
        return kills.getOrDefault(id, 0);
    }

    public int getDeaths(UUID id) {
        return deaths.getOrDefault(id, 0);
    }

    public List<Entry> topKills(int limit) {
        return top(kills, limit);
    }

    public List<Entry> topDeaths(int limit) {
        return top(deaths, limit);
    }

    public List<Entry> topMoney(int limit) {
        ShopEconomy economy = plugin.getShopManager() != null ? plugin.getShopManager().getEconomy() : null;
        if (economy == null) return List.of();
        List<Entry> list = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : economy.snapshotCents().entrySet()) {
            if (e.getValue() <= 0) continue;
            list.add(new Entry(e.getKey(), nameOf(e.getKey()), e.getValue()));
        }
        list.sort(Comparator.comparingLong(Entry::value).reversed());
        if (list.size() > limit) return list.subList(0, limit);
        return list;
    }

    public int moneyPlace(UUID id) {
        ShopEconomy economy = plugin.getShopManager() != null ? plugin.getShopManager().getEconomy() : null;
        if (economy == null) return 0;
        long mine = economy.getCents(id);
        int place = 1;
        for (long other : economy.snapshotCents().values()) {
            if (other > mine) place++;
        }
        return place;
    }

    public int killsPlace(UUID id) {
        return placeOf(kills, id);
    }

    public int deathsPlace(UUID id) {
        return placeOf(deaths, id);
    }

    private List<Entry> top(Map<UUID, Integer> source, int limit) {
        List<Entry> list = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : source.entrySet()) {
            if (e.getValue() <= 0) continue;
            list.add(new Entry(e.getKey(), nameOf(e.getKey()), e.getValue()));
        }
        list.sort(Comparator.comparingLong(Entry::value).reversed());
        if (list.size() > limit) return list.subList(0, limit);
        return list;
    }

    private int placeOf(Map<UUID, Integer> source, UUID id) {
        int mine = source.getOrDefault(id, 0);
        int place = 1;
        for (int other : source.values()) {
            if (other > mine) place++;
        }
        return place;
    }

    public String nameOf(UUID id) {
        String stored = names.get(id);
        if (stored != null && !stored.isEmpty()) return stored;
        String offline = Bukkit.getOfflinePlayer(id).getName();
        return offline != null ? offline : "???";
    }

    private void load() {
        YamlConfiguration hist = historicalFile.exists()
                ? YamlConfiguration.loadConfiguration(historicalFile) : null;
        YamlConfiguration live = file.exists()
                ? YamlConfiguration.loadConfiguration(file) : null;
        String histFrom = hist != null ? hist.getString("from-log") : null;
        String liveFrom = live != null ? live.getString("from-log") : null;
        fromLog = histFrom != null ? histFrom : liveFrom;

        if (histFrom != null && (liveFrom == null || !histFrom.equals(liveFrom))) {
            applyYaml(hist, false);
        } else {
            applyYaml(hist, false);
            applyYaml(live, true);
        }
    }

    private void applyYaml(YamlConfiguration yaml, boolean takeMax) {
        if (yaml == null) return;
        for (String key : yaml.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                int k = yaml.getInt(key + ".kills", 0);
                int d = yaml.getInt(key + ".deaths", 0);
                kills.put(id, takeMax ? Math.max(kills.getOrDefault(id, 0), k) : k);
                deaths.put(id, takeMax ? Math.max(deaths.getOrDefault(id, 0), d) : d);
                String name = yaml.getString(key + ".name");
                if (name != null && !name.isEmpty()) names.put(id, name);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (fromLog != null) yaml.set("from-log", fromLog);
        yaml.set("imported-from-logs", true);
        for (UUID id : unionKeys()) {
            String key = id.toString();
            yaml.set(key + ".kills", kills.getOrDefault(id, 0));
            yaml.set(key + ".deaths", deaths.getOrDefault(id, 0));
            String name = names.get(id);
            if (name != null) yaml.set(key + ".name", name);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie zapisac player-stats.yml", ex);
        }
    }

    private List<UUID> unionKeys() {
        List<UUID> ids = new ArrayList<>();
        ids.addAll(kills.keySet());
        for (UUID id : deaths.keySet()) {
            if (!ids.contains(id)) ids.add(id);
        }
        return ids;
    }
}
