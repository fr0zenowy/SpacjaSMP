package pl.zamrozenie;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LifestealManager {

    public static final int DEFAULT_HEARTS = 10;
    public static final int MAX_HEARTS     = 20;
    public static final int REVIVE_HEARTS  = 5;   // serca po odrodzeniu

    private final ZamrozeniePlugin plugin;

    final NamespacedKey heartKey;
    final NamespacedKey bookKey;
    final NamespacedKey bookTargetKey;

    private File dataFile;
    private FileConfiguration data;

    final Map<UUID, Integer>           hearts      = new HashMap<>();
    /** killer UUID → (victim UUID → timestamp ostatniego zabicia) */
    private final Map<UUID, Map<UUID, Long>> killHistory   = new HashMap<>();
    /** killerIP → (victimIP → timestamp) — anty-multiclient po IP */
    private final Map<String, Map<String, Long>> ipKillHistory = new HashMap<>();
    private final Map<UUID, String>       playerIPs   = new HashMap<>();

    /** Cooldown anty-multiclient: 30 minut */
    public static final long KILL_COOLDOWN_MS = 30L * 60L * 1000L;
    final Set<UUID>                       pendingSpawn    = new java.util.HashSet<>();
    /** Gracze zbanowani przez system Lifesteal (nie ban serwerowy). */
    private final Set<String>             lifestealBanned = new java.util.HashSet<>();
    /**
     * Gracze juz przetworzeni przez combat-log (onPlayerQuit).
     * Jezeli PvPManager lub inny plugin zabije gracza po wylogowaniu,
     * onPlayerDeath nie bedzie ich podwojnie kara sercami.
     */
    final Set<UUID>                       combatLogProcessed = new java.util.HashSet<>();
    /** Po śmierci — przez ten czas nie traci serc i nikt z niego nie dostaje. */
    private final Map<UUID, Long>         deathProtectionUntil = new HashMap<>();
    private final Map<UUID, Long>         pendingWithdraw  = new HashMap<>();
    private final Map<UUID, UUID>         combatAttacker   = new HashMap<>(); // victim -> attacker
    private final Map<UUID, Long>         combatTimestamp  = new HashMap<>(); // player -> last hit time

    private static final long WITHDRAW_CONFIRM_MILLIS = 30_000L;
    public  static final long COMBAT_DURATION_MS      = 30_000L;

    // ─── Combat tracking ─────────────────────────────────────────────────────────

    /** Rejestruje trafienie gracza przez gracza; obaj wchodza w stan walki. */
    public void registerCombatHit(UUID attacker, UUID victim) {
        long now = System.currentTimeMillis();
        combatAttacker.put(victim, attacker);
        combatTimestamp.put(victim, now);
        combatTimestamp.put(attacker, now);
        // napastnik: jego "przeciwnik walki" to victim (potrzebne dla symetrii logoutu)
        combatAttacker.merge(attacker, victim, (old, n) -> n);
    }

    public boolean isInCombat(UUID uuid) {
        Long t = combatTimestamp.get(uuid);
        return t != null && System.currentTimeMillis() - t < COMBAT_DURATION_MS;
    }

    /** Zwraca UUID napastnika ktory ostatnio uderzy graczy (null jesli brak/wygasly). */
    public UUID getCombatAttacker(UUID victim) {
        if (!isInCombat(victim)) return null;
        return combatAttacker.get(victim);
    }

    public void clearCombat(UUID uuid) {
        combatAttacker.remove(uuid);
        combatTimestamp.remove(uuid);
    }

    /**
     * Kara za wylog / smierc w walce: -1 serce, zabojca +1 (jak zwykla smierc).
     * Druga proba (np. PlayerDeathEvent po quit) jest ignorowana.
     */
    public enum DeathHeartResult { APPLIED, PROTECTED, DUPLICATE }

    public DeathHeartResult applyDeathHearts(Player victim, Player killer) {
        if (victim == null) return DeathHeartResult.DUPLICATE;
        if (!combatLogProcessed.add(victim.getUniqueId())) return DeathHeartResult.DUPLICATE;
        Bukkit.getScheduler().runTaskLater(plugin, () -> combatLogProcessed.remove(victim.getUniqueId()), 200L);

        if (hasDeathProtection(victim.getUniqueId())) {
            startDeathProtection(victim.getUniqueId());
            clearCombat(victim.getUniqueId());
            if (killer != null && killer.isOnline() && !killer.getUniqueId().equals(victim.getUniqueId())) {
                killer.sendMessage(ChatColor.YELLOW + "[Lifesteal] " + ChatColor.GRAY
                        + victim.getName() + " ma ochronę po śmierci — nikt nie traci i nie dostaje serca.");
            }
            return DeathHeartResult.PROTECTED;
        }

        int currentHearts = getHearts(victim.getUniqueId());
        removeHeart(victim.getUniqueId());
        clearCombat(victim.getUniqueId());

        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            if (canGainHeart(killer.getUniqueId(), victim.getUniqueId())) {
                addHeart(killer.getUniqueId());
                if (killer.isOnline()) {
                    applyHearts(killer);
                    int killerHearts = getHearts(killer.getUniqueId());
                    killer.sendMessage(ChatColor.GREEN + "\u2764 Zabi\u0142e\u015b " + ChatColor.WHITE + victim.getName()
                            + ChatColor.GREEN + "! Serce zdobyte! Masz teraz: "
                            + ChatColor.WHITE + killerHearts + ChatColor.GREEN + "/" + MAX_HEARTS
                            + "  " + heartsBar(killerHearts));
                }
            } else if (killer.isOnline()) {
                killer.sendMessage(ChatColor.YELLOW + "[Lifesteal] " + ChatColor.GRAY
                        + "Nie otrzyma\u0142e\u015b serca \u2014 ju\u017c zabi\u0142e\u015b tego gracza lub kogo\u015b z tego samego IP.");
            }
            recordKill(killer.getUniqueId(), victim.getUniqueId());
            clearCombat(killer.getUniqueId());
        }

        if (currentHearts <= 1) {
            banPlayer(victim, killer);
        } else {
            startDeathProtection(victim.getUniqueId());
        }
        return DeathHeartResult.APPLIED;
    }

    public void startDeathProtection(UUID uuid) {
        int sec = Math.max(0, plugin.getConfig().getInt("lifesteal.spawn-protection-seconds", 180));
        if (sec <= 0) {
            deathProtectionUntil.remove(uuid);
            return;
        }
        deathProtectionUntil.put(uuid, System.currentTimeMillis() + sec * 1000L);
        save();
    }

    public boolean hasDeathProtection(UUID uuid) {
        Long until = deathProtectionUntil.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            deathProtectionUntil.remove(uuid);
            return false;
        }
        return true;
    }

    public int remainingDeathProtectionSeconds(UUID uuid) {
        Long until = deathProtectionUntil.get(uuid);
        if (until == null) return 0;
        long remain = until - System.currentTimeMillis();
        if (remain <= 0) {
            deathProtectionUntil.remove(uuid);
            return 0;
        }
        return (int) Math.ceil(remain / 1000.0);
    }

    public boolean hasPendingWithdraw(UUID uuid) {
        Long ts = pendingWithdraw.get(uuid);
        if (ts == null) return false;
        if (System.currentTimeMillis() - ts > WITHDRAW_CONFIRM_MILLIS) {
            pendingWithdraw.remove(uuid);
            return false;
        }
        return true;
    }

    public void setPendingWithdraw(UUID uuid) {
        pendingWithdraw.put(uuid, System.currentTimeMillis());
    }

    public void clearPendingWithdraw(UUID uuid) {
        pendingWithdraw.remove(uuid);
    }

    public LifestealManager(ZamrozeniePlugin plugin) {
        this.plugin       = plugin;
        this.heartKey      = new NamespacedKey(plugin, "ls_heart");
        this.bookKey       = new NamespacedKey(plugin, "ls_book");
        this.bookTargetKey = new NamespacedKey(plugin, "ls_book_target");

        dataFile = new File(plugin.getDataFolder(), "lifesteal.yml");
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
    }

    // ─── Serca ───────────────────────────────────────────────────────────────────

    public int getHearts(UUID uuid) {
        return hearts.getOrDefault(uuid, DEFAULT_HEARTS);
    }

    public void addHeart(UUID uuid) {
        setHearts(uuid, getHearts(uuid) + 1);
    }

    public void removeHeart(UUID uuid) {
        setHearts(uuid, getHearts(uuid) - 1);
    }

    public void setHearts(UUID uuid, int amount) {
        int clamped = Math.max(0, Math.min(MAX_HEARTS, amount));
        hearts.put(uuid, clamped);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) applyHearts(p);
        save();
    }

    /**
     * Reset sezonowy: 20→13, 19→12, 18→11, reszta → 10.
     * Gracze z 0 serc (eliminacja) zostają na 0.
     */
    public int[] resetSeasonHearts() {
        Set<UUID> ids = new HashSet<>(hearts.keySet());
        for (Player online : Bukkit.getOnlinePlayers()) ids.add(online.getUniqueId());

        int from20 = 0, from19 = 0, from18 = 0, toDefault = 0, skipped = 0;
        for (UUID id : ids) {
            int old = getHearts(id);
            if (old <= 0) {
                skipped++;
                continue;
            }
            int next = switch (old) {
                case 20 -> 13;
                case 19 -> 12;
                case 18 -> 11;
                default -> DEFAULT_HEARTS;
            };
            if (old == next) continue;
            hearts.put(id, next);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                applyHearts(p);
                p.sendMessage(ChatColor.YELLOW + "Reset serc: " + ChatColor.WHITE + old
                        + ChatColor.GRAY + " → " + ChatColor.WHITE + next);
            }
            if (old == 20) from20++;
            else if (old == 19) from19++;
            else if (old == 18) from18++;
            else toDefault++;
        }
        save();
        return new int[] { from20, from19, from18, toDefault, skipped };
    }

    public void applyHearts(Player player) {
        int h = getHearts(player.getUniqueId());
        double maxHp = Math.max(2.0, h * 2.0);
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(maxHp);
        if (player.getHealth() > maxHp) player.setHealth(maxHp);
    }

    public String heartsBar(int h) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_HEARTS; i++) {
            if (i < h) sb.append(ChatColor.RED).append("❤");
            else        sb.append(ChatColor.DARK_GRAY).append("❤");
        }
        return sb.toString();
    }

    // ─── Anti-multiclient ────────────────────────────────────────────────────────

    /**
     * Zwraca true jeśli zabójca może zdobyć serce za tego konkretnego gracza.
     * false = już zabił tego samego nicka LUB kogoś z tego samego IP.
     */
    // ─── Anty-multiclient z cooldownem 30 min ────────────────────────────────────

    /**
     * Sprawdza czy killer moze zdobyc serce z zabicia victim.
     * Blokuje jesli:
     *  - to samo UUID zostalo zabite w ciagu ostatnich 30 min
     *  - to samo IP (inne konto na tej samej maszynie) zostalo zabite w ciagu 30 min
     */
    public boolean canGainHeart(UUID killer, UUID victim) {
        long now = System.currentTimeMillis();

        // Sprawdz po UUID
        Map<UUID, Long> killerHistory = killHistory.get(killer);
        if (killerHistory != null) {
            Long lastKill = killerHistory.get(victim);
            if (lastKill != null && now - lastKill < KILL_COOLDOWN_MS) return false;
        }

        // Sprawdz po IP
        String killerIP = playerIPs.get(killer);
        String victimIP = playerIPs.get(victim);

        if (killerIP != null && victimIP != null) {
            // Ten sam IP = ta sama maszyna
            if (killerIP.equals(victimIP)) return false;

            // Sprawdz czy killer juz zabil kogos z tego samego IP ofiary (w cooldownie)
            Map<String, Long> ipHistory = ipKillHistory.get(killerIP);
            if (ipHistory != null) {
                Long lastIPKill = ipHistory.get(victimIP);
                if (lastIPKill != null && now - lastIPKill < KILL_COOLDOWN_MS) return false;
            }
        }

        return true;
    }

    public void recordKill(UUID killer, UUID victim) {
        long now = System.currentTimeMillis();

        // Zapisz timestamp po UUID
        killHistory.computeIfAbsent(killer, k -> new HashMap<>()).put(victim, now);

        // Zapisz timestamp po IP
        String killerIP = playerIPs.get(killer);
        String victimIP = playerIPs.get(victim);
        if (killerIP != null && victimIP != null && !killerIP.equals(victimIP)) {
            ipKillHistory.computeIfAbsent(killerIP, k -> new HashMap<>()).put(victimIP, now);
        }

        save();
    }

    public void updateIP(UUID uuid, String ip) {
        playerIPs.put(uuid, ip);
        save();
    }

    // ─── Tworzenie itemów ────────────────────────────────────────────────────────

    public ItemStack createHeartItem() {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Serce");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Prawym przyciskiem aby u\u017cy\u0107.",
            ChatColor.DARK_RED + "+" + ChatColor.RED + "1 serce do maksymalnego HP."
        ));
        meta.setCustomModelData(1001);
        meta.getPersistentDataContainer().set(heartKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createResurrectionBook() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Ksiega Odrodzenia");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Otwiera liste zbanowanych graczy.",
            "",
            ChatColor.YELLOW + "Prawe klikniecie aby wybrac gracza.",
            ChatColor.DARK_RED + "Uzyj madrzej..."
        ));
        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isHeartItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(heartKey, PersistentDataType.BYTE);
    }

    public boolean isResurrectionBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(bookKey, PersistentDataType.BYTE);
    }

    // ─── Ban / Unban ─────────────────────────────────────────────────────────────

    public void banPlayer(Player player, Player killer) {
        String reason = ChatColor.RED + "Lifesteal: 0 serc. Jedyna szansa to Ksiega Odrodzenia.";
        Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), reason, null, "Lifesteal");
        lifestealBanned.add(player.getName()); // oznacz jako ban lifestealowy
        save();

        Bukkit.broadcastMessage(
            ChatColor.DARK_RED + "" + ChatColor.BOLD + "ELIMINACJA! " +
            ChatColor.RED + player.getName() +
            ChatColor.GRAY + " zostal wyeliminowany przez " +
            ChatColor.WHITE + (killer != null ? killer.getName() : "swiata") +
            ChatColor.GRAY + " i opuszcza serwer!"
        );

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.kickPlayer(
                    ChatColor.RED + "" + ChatColor.BOLD + "Masz 0 serc!\n\n" +
                    ChatColor.GRAY + "Zostales wyeliminowany.\n" +
                    ChatColor.YELLOW + "Mozesz wrocic tylko dzieki Ksiedze Odrodzenia."
                );
            }
        }, 40L);
    }

    /**
     * Odbanowuje gracza, resetuje wszystko i ustawia flage teleportu na spawn.
     */
    public void revivePlayer(String playerName, Player who) {
        Bukkit.getBanList(BanList.Type.NAME).pardon(playerName);

        // Znajdz UUID gracza
        UUID revivedUUID = null;
        for (org.bukkit.OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(playerName)) {
                revivedUUID = op.getUniqueId();
                break;
            }
        }

        if (revivedUUID != null) {
            // Resetuj serca do domyslnych
            setHearts(revivedUUID, DEFAULT_HEARTS);

            // Wyczysc jego historie kill (jako killer)
            killHistory.remove(revivedUUID);

            // Usun go z historii innych graczy (jako victim) — mozna znow zabic i dostac serce
            for (Map<UUID, Long> victims : killHistory.values()) {
                victims.remove(revivedUUID);
            }

            // Flaga: teleportuj na spawn po wejsciu
            pendingSpawn.add(revivedUUID);
            lifestealBanned.remove(playerName);
        }

        save();
    }

    public ZamrozeniePlugin getPlugin() { return plugin; }

    /** Zwraca true jesli gracz zostal zbanowany przez system Lifesteal. */
    public boolean isLifestealBanned(String playerName) {
        return lifestealBanned.contains(playerName);
    }

    /** Lista nazw graczy zbanowanych przez Lifesteal (do GUI ksiegi). */
    public Set<String> getLifestealBannedNames() {
        return java.util.Collections.unmodifiableSet(lifestealBanned);
    }

    /** Recznie oznacza gracza jako zbanowanego przez Lifesteal (np. combat-log). */
    public void markLifestealBan(String playerName) {
        lifestealBanned.add(playerName);
        save();
    }

    // ─── Zapis / Odczyt ──────────────────────────────────────────────────────────

    private void loadData() {
        if (data.contains("hearts")) {
            for (String key : data.getConfigurationSection("hearts").getKeys(false)) {
                try { hearts.put(UUID.fromString(key), data.getInt("hearts." + key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        if (data.contains("kills")) {
            for (String killerKey : data.getConfigurationSection("kills").getKeys(false)) {
                try {
                    UUID killer = UUID.fromString(killerKey);
                    Map<UUID, Long> victims = new HashMap<>();
                    var section = data.getConfigurationSection("kills." + killerKey);
                    if (section != null) {
                        for (String victimKey : section.getKeys(false)) {
                            try {
                                UUID vic = UUID.fromString(victimKey);
                                long ts = data.getLong("kills." + killerKey + "." + victimKey, 0L);
                                // Tylko wczytuj jesli cooldown nie minal
                                if (System.currentTimeMillis() - ts < KILL_COOLDOWN_MS) {
                                    victims.put(vic, ts);
                                }
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                    if (!victims.isEmpty()) killHistory.put(killer, victims);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (data.contains("ip_kills")) {
            for (String killerIP : data.getConfigurationSection("ip_kills").getKeys(false)) {
                Map<String, Long> victims = new HashMap<>();
                var section = data.getConfigurationSection("ip_kills." + killerIP);
                if (section != null) {
                    for (String victimIP : section.getKeys(false)) {
                        long ts = data.getLong("ip_kills." + killerIP + "." + victimIP, 0L);
                        if (System.currentTimeMillis() - ts < KILL_COOLDOWN_MS) {
                            victims.put(victimIP, ts);
                        }
                    }
                }
                if (!victims.isEmpty()) ipKillHistory.put(killerIP, victims);
            }
        }
        if (data.contains("ips")) {
            for (String key : data.getConfigurationSection("ips").getKeys(false)) {
                try { playerIPs.put(UUID.fromString(key), data.getString("ips." + key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        // Wczytaj liste graczy zbanowanych przez Lifesteal
        lifestealBanned.addAll(data.getStringList("lifesteal_banned"));
        if (data.contains("death_protection")) {
            long now = System.currentTimeMillis();
            for (String key : data.getConfigurationSection("death_protection").getKeys(false)) {
                try {
                    long until = data.getLong("death_protection." + key, 0L);
                    if (until > now) deathProtectionUntil.put(UUID.fromString(key), until);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void save() {
        hearts.forEach((uuid, h) -> data.set("hearts." + uuid, h));

        // Zapisz tylko wpisy w ramach cooldownu
        long now = System.currentTimeMillis();
        data.set("kills", null); // wyczys poprzednie
        killHistory.forEach((killer, victims) ->
            victims.forEach((victim, ts) -> {
                if (now - ts < KILL_COOLDOWN_MS)
                    data.set("kills." + killer + "." + victim, ts);
            })
        );
        data.set("ip_kills", null);
        ipKillHistory.forEach((killerIP, victims) ->
            victims.forEach((victimIP, ts) -> {
                if (now - ts < KILL_COOLDOWN_MS) {
                    // YAML nie lubi kropek w kluczach — zastep '.' na '_'
                    String kKey = killerIP.replace('.', '_').replace(':', '_');
                    String vKey = victimIP.replace('.', '_').replace(':', '_');
                    data.set("ip_kills." + kKey + "." + vKey, ts);
                }
            })
        );
        playerIPs.forEach((uuid, ip) -> data.set("ips." + uuid, ip));
        data.set("lifesteal_banned", new ArrayList<>(lifestealBanned));
        data.set("death_protection", null);
        deathProtectionUntil.forEach((uuid, until) -> {
            if (until > now) data.set("death_protection." + uuid, until);
        });
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().severe("Nie mozna zapisac lifesteal.yml!"); }
    }
}
