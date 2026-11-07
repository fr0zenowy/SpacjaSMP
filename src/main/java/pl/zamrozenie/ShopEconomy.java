package pl.zamrozenie;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Saldo w groszach (1.00 = 100), zeby ceny typu 5.63 nie gubily reszty. */
public class ShopEconomy {

    private final ZamrozeniePlugin plugin;
    private final File file;
    private final Map<UUID, Long> cents = new HashMap<>();

    public ShopEconomy(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop-balances.yml");
        load();
    }

    public double get(UUID id) {
        return cents.getOrDefault(id, 0L) / 100.0;
    }

    public double get(Player player) {
        return get(player.getUniqueId());
    }

    public long getCents(UUID id) {
        return cents.getOrDefault(id, 0L);
    }

    public void set(UUID id, double amount) {
        setCents(id, ShopManager.toCents(amount));
    }

    public void setCents(UUID id, long amount) {
        cents.put(id, Math.max(0L, amount));
        save();
    }

    public void add(UUID id, double amount) {
        setCents(id, getCents(id) + ShopManager.toCents(amount));
    }

    public boolean take(UUID id, double amount) {
        long need = ShopManager.toCents(amount);
        long have = getCents(id);
        if (have < need) return false;
        setCents(id, have - need);
        return true;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                cents.put(UUID.fromString(key), ShopManager.toCents(yaml.getDouble(key)));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public Map<UUID, Long> snapshotCents() {
        return new HashMap<>(cents);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : cents.entrySet()) {
            yaml.set(e.getKey().toString(), e.getValue() / 100.0);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie zapisac shop-balances.yml", ex);
        }
    }
}
