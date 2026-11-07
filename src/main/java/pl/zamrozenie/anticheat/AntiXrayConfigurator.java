package pl.zamrozenie.anticheat;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Anti-Xray = wbudowany silnik Papera (config/paper-world-defaults.yml).
 * Plugin nie moze przepisywac chunkow lepiej niz Paper, wiec zamiast tego
 * przy starcie sprawdza config i — jesli anti-xray jest wylaczony albo
 * ustawiony slabo — wlacza go w trybie 2 (jak DonutSMP: ukryte bloki sa
 * zamieniane na losowe "falszywe" rudy). Zmiana wymaga restartu serwera.
 */
public class AntiXrayConfigurator implements Listener {

    private static final List<String> HIDDEN_OVERWORLD = Arrays.asList(
            "copper_ore", "deepslate_copper_ore", "raw_copper_block",
            "gold_ore", "deepslate_gold_ore", "iron_ore", "deepslate_iron_ore", "raw_iron_block",
            "coal_ore", "deepslate_coal_ore", "lapis_ore", "deepslate_lapis_ore",
            "diamond_ore", "deepslate_diamond_ore", "redstone_ore", "deepslate_redstone_ore",
            "emerald_ore", "deepslate_emerald_ore", "ancient_debris",
            "mossy_cobblestone", "obsidian", "clay",
            "chest", "trapped_chest", "barrel", "ender_chest", "shulker_box",
            "spawner", "trial_spawner", "vault", "amethyst_block", "budding_amethyst");

    private static final List<String> REPLACEMENT_OVERWORLD = Arrays.asList(
            "stone", "deepslate", "andesite", "diorite", "granite", "tuff", "oak_planks", "chest");

    private static final List<String> HIDDEN_NETHER = Arrays.asList(
            "ancient_debris", "nether_gold_ore", "nether_quartz_ore", "bone_block", "chest");

    private static final List<String> REPLACEMENT_NETHER = Arrays.asList("netherrack", "basalt", "blackstone");

    private final JavaPlugin plugin;
    private boolean restartRequired = false;
    private boolean allowFlight = false;
    private String status = "nieznany";

    public AntiXrayConfigurator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void run() {
        File serverRoot = plugin.getDataFolder().getAbsoluteFile().getParentFile().getParentFile();
        File defaults = new File(serverRoot, "config/paper-world-defaults.yml");
        boolean auto = plugin.getConfig().getBoolean("antixray.auto-configure-paper", true);

        if (!defaults.exists()) {
            status = ChatColor.RED + "brak config/paper-world-defaults.yml (to nie Paper?)";
            plugin.getLogger().warning("[AntiXray] Nie znaleziono " + defaults.getPath() + " — anti-xray wymaga Paper/Purpur.");
            return;
        }

        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(defaults);
            boolean enabled = y.getBoolean("anticheat.anti-xray.enabled", false);
            int mode = y.getInt("anticheat.anti-xray.engine-mode", 1);
            List<String> hidden = y.getStringList("anticheat.anti-xray.hidden-blocks");
            boolean ok = enabled && mode >= 2 && hidden.contains("diamond_ore") && hidden.contains("deepslate_diamond_ore");

            if (ok) {
                status = ChatColor.GREEN + "aktywny (engine-mode " + mode + ", " + hidden.size() + " ukrytych blokow)";
            } else if (auto) {
                y.set("anticheat.anti-xray.enabled", true);
                y.set("anticheat.anti-xray.engine-mode", 2);
                y.set("anticheat.anti-xray.max-block-height", 64);
                y.set("anticheat.anti-xray.update-radius", 2);
                y.set("anticheat.anti-xray.lava-obscures", false);
                y.set("anticheat.anti-xray.use-permission", false);
                y.set("anticheat.anti-xray.hidden-blocks", HIDDEN_OVERWORLD);
                y.set("anticheat.anti-xray.replacement-blocks", REPLACEMENT_OVERWORLD);
                y.save(defaults);
                restartRequired = true;
                status = ChatColor.GOLD + "skonfigurowany (engine-mode 2) — WYMAGANY RESTART SERWERA";
                plugin.getLogger().warning("[AntiXray] Wlaczylem anti-xray Papera w " + defaults.getPath() + " — ZRESTARTUJ SERWER, zeby zaczal dzialac.");
            } else {
                status = ChatColor.RED + "wylaczony w paper-world-defaults.yml (auto-configure off)";
                plugin.getLogger().warning("[AntiXray] Anti-xray Papera jest WYLACZONY. Ustaw anticheat.anti-xray.enabled: true w " + defaults.getPath());
            }

            // per-world override'y: config/paper-world/<swiat>/paper-world.yml
            File perWorld = new File(serverRoot, "config/paper-world");
            File[] worlds = perWorld.isDirectory() ? perWorld.listFiles(File::isDirectory) : null;
            if (worlds != null) {
                for (File w : worlds) {
                    File f = new File(w, "paper-world.yml");
                    if (!f.exists()) continue;
                    YamlConfiguration wy = YamlConfiguration.loadConfiguration(f);
                    boolean changed = false;
                    if (wy.contains("anticheat.anti-xray.enabled") && !wy.getBoolean("anticheat.anti-xray.enabled")) {
                        if (!auto) {
                            plugin.getLogger().warning("[AntiXray] Swiat " + w.getName() + " ma anti-xray wylaczony w " + f.getPath());
                            continue;
                        }
                        wy.set("anticheat.anti-xray.enabled", true);
                        changed = true;
                    }
                    if (auto && w.getName().toLowerCase().contains("nether") && !wy.contains("anticheat.anti-xray.hidden-blocks")) {
                        wy.set("anticheat.anti-xray.enabled", true);
                        wy.set("anticheat.anti-xray.engine-mode", 1);
                        wy.set("anticheat.anti-xray.max-block-height", 128);
                        wy.set("anticheat.anti-xray.hidden-blocks", HIDDEN_NETHER);
                        wy.set("anticheat.anti-xray.replacement-blocks", REPLACEMENT_NETHER);
                        changed = true;
                    }
                    if (changed) {
                        wy.save(f);
                        restartRequired = true;
                        plugin.getLogger().warning("[AntiXray] Poprawilem config swiata " + w.getName() + " — wymagany restart.");
                    }
                }
            }
        } catch (IOException ex) {
            status = ChatColor.RED + "blad zapisu: " + ex.getMessage();
            plugin.getLogger().severe("[AntiXray] Nie moge zapisac configu Papera: " + ex.getMessage());
        }

        // server.properties: allow-flight=true wylacza wbudowany w vanilla kick za latanie
        File props = new File(serverRoot, "server.properties");
        if (props.exists()) {
            try (FileInputStream in = new FileInputStream(props)) {
                Properties p = new Properties();
                p.load(in);
                allowFlight = Boolean.parseBoolean(p.getProperty("allow-flight", "false"));
                if (allowFlight) {
                    plugin.getLogger().warning("[AntiCheat] server.properties ma allow-flight=true — vanilla nie wyrzuca za latanie. Ustaw allow-flight=false.");
                }
            } catch (IOException ignored) { }
        }
    }

    public String getStatus() {
        return status;
    }

    public boolean isRestartRequired() {
        return restartRequired;
    }

    public boolean isAllowFlight() {
        return allowFlight;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission(AntiCheat.PERM_ALERTS)) return;
        if (restartRequired) {
            p.sendMessage(AntiCheat.PREFIX + ChatColor.GOLD + "Anti-Xray Papera zostal wlaczony w configu. " + ChatColor.RED + ChatColor.BOLD + "ZRESTARTUJ SERWER"
                    + ChatColor.GOLD + ", zeby zaczal dzialac.");
        }
        if (allowFlight) {
            p.sendMessage(AntiCheat.PREFIX + ChatColor.YELLOW + "server.properties: " + ChatColor.WHITE + "allow-flight=true" + ChatColor.YELLOW
                    + " — ustaw na false (vanilla sam wyrzuca za lot).");
        }
    }
}
