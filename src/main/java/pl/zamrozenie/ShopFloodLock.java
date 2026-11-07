package pl.zamrozenie;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Osobisty lock sprzedazy: jak gracz zarobi z jednego slotu itemu &gt;= prog,
 * tylko jemu cena tego itemu spada o X% na 2–4 godziny.
 */
public class ShopFloodLock {

    public static final class Lock {
        public final long untilMs;
        public final double earned;

        Lock(long untilMs, double earned) {
            this.untilMs = untilMs;
            this.earned = earned;
        }

        boolean active() {
            return untilMs > System.currentTimeMillis();
        }
    }

    private final ZamrozeniePlugin plugin;
    private final File file;
    private final Random random = new Random();
    /** uuid -> (slot -> lock/earned) */
    private final Map<UUID, Map<Integer, Lock>> data = new HashMap<>();

    private boolean enabled;
    private double threshold;
    private double discount;
    private int minHours;
    private int maxHours;

    public ShopFloodLock(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop-player-locks.yml");
        reload();
        load();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("shop-flood.enabled", true);
        threshold = c.getDouble("shop-flood.threshold", 1000.0);
        discount = c.getDouble("shop-flood.discount-percent", 40.0) / 100.0;
        minHours = Math.max(1, c.getInt("shop-flood.min-hours", 2));
        maxHours = Math.max(minHours, c.getInt("shop-flood.max-hours", 4));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double threshold() {
        return threshold;
    }

    public boolean isLocked(Player player, int slot) {
        if (!enabled || player == null) return false;
        Lock lock = get(player.getUniqueId(), slot);
        return lock != null && lock.active();
    }

    public double personalSellPrice(Player player, ShopOffer offer) {
        if (offer == null) return 0.01;
        if (!isLocked(player, offer.slot)) return Math.max(0.01, offer.price);
        // -40% od ceny bazowej (tej z /asklep), nie od juz obnizonego rynku
        double unit = ShopManager.roundMoney(offer.basePrice * (1.0 - discount));
        return Math.max(0.01, unit);
    }

    /**
     * Po sprzedazy: dolicza zarobek. Gdy osiagnie prog, zaklada lock.
     * @return godziny locka, albo 0 jesli lock nie skoczyl
     */
    public int afterSale(Player player, int slot, double gain) {
        if (!enabled || player == null || gain <= 0) return 0;
        reload();
        UUID id = player.getUniqueId();
        Lock prev = get(id, slot);
        if (prev != null && prev.active()) {
            put(id, slot, new Lock(prev.untilMs, prev.earned + gain));
            save();
            return 0;
        }
        double earned = (prev == null ? 0 : prev.earned) + gain;
        if (earned < threshold) {
            put(id, slot, new Lock(0, earned));
            save();
            return 0;
        }
        int hours = minHours + random.nextInt(maxHours - minHours + 1);
        long until = System.currentTimeMillis() + hours * 3_600_000L;
        put(id, slot, new Lock(until, 0));
        save();
        return hours;
    }

    private Lock get(UUID id, int slot) {
        Map<Integer, Lock> map = data.get(id);
        return map == null ? null : map.get(slot);
    }

    private void put(UUID id, int slot, Lock lock) {
        data.computeIfAbsent(id, k -> new HashMap<>()).put(slot, lock);
    }

    private void load() {
        data.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) return;
        for (String uid : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(uid);
                ConfigurationSection slots = root.getConfigurationSection(uid);
                if (slots == null) continue;
                for (String key : slots.getKeys(false)) {
                    int slot = Integer.parseInt(key);
                    long until = slots.getLong(key + ".until");
                    double earned = slots.getDouble(key + ".earned");
                    put(id, slot, new Lock(until, earned));
                }
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<Integer, Lock>> e : data.entrySet()) {
            for (Map.Entry<Integer, Lock> s : e.getValue().entrySet()) {
                Lock lock = s.getValue();
                if (!lock.active() && lock.earned <= 0) continue;
                if (!lock.active() && lock.untilMs > 0 && lock.untilMs < now && lock.earned <= 0) continue;
                String path = "players." + e.getKey() + "." + s.getKey();
                yaml.set(path + ".until", lock.untilMs);
                yaml.set(path + ".earned", lock.earned);
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie zapisac shop-player-locks.yml", ex);
        }
    }
}
