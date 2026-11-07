package pl.zamrozenie.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Autorski anticheat serwera Zamrozenie.
 *
 * Zasada dzialania: kazdy check woła {@link #flag(Player, Check, String)}.
 * Naruszenie dodaje VL (violation level), wysyla alert adminom
 * (permisja zamrozenie.ac.alerts) i — jesli check jest blokujacy —
 * sam check anuluje event / cofa gracza. Po przekroczeniu progu VL gracz
 * jest wyrzucany. VL powoli maleje w czasie, zeby pojedyncze lagi nie
 * kumulowaly sie w kick.
 */
public class AntiCheat {

    public static final String PERM_ALERTS = "zamrozenie.ac.alerts";
    public static final String PERM_BYPASS = "zamrozenie.ac.bypass";
    public static final String PREFIX = ChatColor.DARK_RED + "" + ChatColor.BOLD + "AC " + ChatColor.DARK_GRAY + "\u00BB ";

    private final JavaPlugin plugin;
    private final Map<UUID, PlayerData> data = new HashMap<>();
    private final Set<UUID> alertsDisabled = new HashSet<>();
    private final Set<Check> disabledChecks = EnumSet.noneOf(Check.class);

    private boolean enabled;
    /** Tryb "tylko alerty": zadnych setbackow, anulowania eventow, blokowania uderzen ani kickow. */
    private boolean alertsOnly;
    private boolean kickEnabled;
    private double kickVl;
    private double decayPer10s;
    private long alertCooldownMs;

    private BukkitTask decayTask;
    private AntiXrayConfigurator antiXray;
    private BehaviorAnalyzer analyzer;

    public AntiCheat(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void setAntiXray(AntiXrayConfigurator antiXray) {
        this.antiXray = antiXray;
    }

    public AntiXrayConfigurator getAntiXray() {
        return antiXray;
    }

    public void setAnalyzer(BehaviorAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public BehaviorAnalyzer getAnalyzer() {
        return analyzer;
    }

    public void blockHits(Player p, long ms) {
        if (alertsOnly) return;
        data(p).hitBlockedUntilMs = System.currentTimeMillis() + ms;
    }

    public boolean hitsBlocked(Player p) {
        if (alertsOnly) return false;
        return System.currentTimeMillis() < data(p).hitBlockedUntilMs;
    }

    public boolean isAlertsOnly() {
        return alertsOnly;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled        = c.getBoolean("anticheat.enabled", true);
        // Zawsze tylko alerty — nic nie cofa, nie anuluje i nie kickuje.
        alertsOnly     = true;
        kickEnabled    = false;
        kickVl         = c.getDouble("anticheat.kick.vl", 60.0);
        decayPer10s    = c.getDouble("anticheat.vl-decay-per-10s", 2.0);
        alertCooldownMs = c.getLong("anticheat.alert-cooldown-ms", 1500L);
        disabledChecks.clear();
        for (String s : c.getStringList("anticheat.disabled-checks")) {
            Check ch = Check.byId(s);
            if (ch != null) disabledChecks.add(ch);
        }
    }

    public void start() {
        if (decayTask != null) decayTask.cancel();
        decayTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (PlayerData d : data.values()) {
                d.totalVl = Math.max(0, d.totalVl - decayPer10s);
                d.vl.replaceAll((k, v) -> Math.max(0, v - decayPer10s / 2));
                d.suspicion = Math.max(0, d.suspicion - 4);
            }
        }, 200L, 200L);
    }

    public void stop() {
        if (decayTask != null) decayTask.cancel();
        data.clear();
    }

    // ─── DANE ─────────────────────────────────────────────────────────────────

    public PlayerData data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), k -> {
            PlayerData d = new PlayerData();
            d.joinMs = System.currentTimeMillis();
            d.resetMovement(p.getLocation(), d.joinMs);
            return d;
        });
    }

    public PlayerData dataIfPresent(UUID id) {
        return data.get(id);
    }

    public void remove(UUID id) {
        data.remove(id);
        alertsDisabled.remove(id);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCheckEnabled(Check c) {
        return enabled && !disabledChecks.contains(c);
    }

    public boolean isExempt(Player p) {
        return !enabled || p.hasPermission(PERM_BYPASS);
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    // ─── FLAGI ────────────────────────────────────────────────────────────────

    /**
     * Rejestruje naruszenie.
     * @return true jesli check jest blokujacy (caller powinien anulowac event);
     *         w trybie alerts-only zawsze false
     */
    public boolean flag(Player p, Check check, String detail) {
        if (!isCheckEnabled(check)) return false;
        PlayerData d = data(p);
        d.totalVl += check.weight;
        d.vl.merge(check, check.weight, Double::sum);

        long now = System.currentTimeMillis();
        Long last = d.lastAlert.get(check);
        if (last == null || now - last >= alertCooldownMs) {
            d.lastAlert.put(check, now);
            String msg = PREFIX + ChatColor.RED + p.getName() + ChatColor.GRAY + " \u2192 " + ChatColor.WHITE + check.id
                    + ChatColor.DARK_GRAY + " [" + ChatColor.YELLOW + "VL " + fmt(d.vlOf(check)) + ChatColor.DARK_GRAY + "/"
                    + ChatColor.GOLD + fmt(d.totalVl) + ChatColor.DARK_GRAY + "] "
                    + ChatColor.GRAY + detail + ChatColor.DARK_GRAY + " (ping " + p.getPing() + "ms)";
            alert(msg);
        }

        if (kickEnabled && d.totalVl >= kickVl) {
            d.totalVl = 0;
            d.vl.clear();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                p.kickPlayer(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Zamrozenie AntiCheat\n\n"
                        + ChatColor.GRAY + "Wykryto niedozwolone modyfikacje klienta.\n"
                        + ChatColor.DARK_GRAY + "(" + check.id + ")");
                alert(PREFIX + ChatColor.RED + p.getName() + ChatColor.GRAY + " zostal wyrzucony " + ChatColor.DARK_GRAY
                        + "(" + check.id + ", VL >= " + fmt(kickVl) + ")");
            });
        }
        return check.blocks && !alertsOnly;
    }

    public void alert(String message) {
        plugin.getLogger().warning(ChatColor.stripColor(message));
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (!admin.hasPermission(PERM_ALERTS)) continue;
            if (alertsDisabled.contains(admin.getUniqueId())) continue;
            admin.sendMessage(message);
            admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.8f);
        }
    }

    public boolean toggleAlerts(Player admin) {
        UUID id = admin.getUniqueId();
        if (alertsDisabled.remove(id)) return true;
        alertsDisabled.add(id);
        return false;
    }

    public boolean hasAlerts(Player admin) {
        return !alertsDisabled.contains(admin.getUniqueId());
    }

    public Map<UUID, PlayerData> all() {
        return Collections.unmodifiableMap(data);
    }

    static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.US, "%.1f", v);
    }
}
