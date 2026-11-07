package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BossEventManager {

    private final ZamrozeniePlugin plugin;
    private final Map<UUID, Double> damage = new HashMap<>();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private MythicBoss activeBoss;
    private UUID bossId;
    private LivingEntity boss;
    private BukkitTask task;
    private boolean active;
    private double configuredMaxHp;

    private BukkitTask countdownTask;
    private BossBar countdownBar;
    private BossBar fightBar;
    private MythicBoss pendingType;
    private Location pendingLoc;
    private long countdownEnd;
    private long countdownTotal;
    private final BossArenaBlocks arena;

    public BossEventManager(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        this.arena = new BossArenaBlocks(plugin);
    }

    public boolean isActive() {
        return active && boss != null && boss.isValid();
    }

    public boolean isCountingDown() {
        return countdownTask != null && pendingType != null;
    }

    public boolean isBoss(Entity entity) {
        return entity != null && bossId != null && bossId.equals(entity.getUniqueId());
    }

    public MythicBoss getActiveBoss() {
        return activeBoss;
    }

    public void addJoiningPlayer(Player player) {
        if (player == null) return;
        if (countdownBar != null) countdownBar.addPlayer(player);
        if (fightBar != null) fightBar.addPlayer(player);
    }

    public String schedule(MythicBoss type, Location loc, long delayMs, Player actor) {
        if (delayMs <= 0) return spawn(type, loc, actor);
        if (type == null || loc == null || loc.getWorld() == null) {
            return ChatColor.RED + "Niepoprawna lokalizacja.";
        }
        if (type.isEventEnabled() && isActive()) {
            return ChatColor.RED + "Ten boss jest już zrespiony.";
        }
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            return ChatColor.RED + "Na serwerze nie ma MythicMobs.";
        }

        clearCountdown();
        pendingType = type;
        pendingLoc = loc.clone();
        countdownTotal = Math.max(1000L, delayMs);
        countdownEnd = System.currentTimeMillis() + countdownTotal;

        sendWakeTitle(type);
        createCountdownBar(type, loc);
        updateCountdownBar();

        countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (System.currentTimeMillis() >= countdownEnd) {
                finishCountdown();
                return;
            }
            updateCountdownBar();
        }, 20L, 20L);

        return ChatColor.GREEN + "Odliczanie: " + ChatColor.WHITE + formatTime(countdownTotal)
                + ChatColor.GRAY + " — " + ChatColor.WHITE + type.getDisplayName()
                + ChatColor.GRAY + " na " + ChatColor.WHITE + coords(loc) + ChatColor.GRAY + ".";
    }

    public String spawn(MythicBoss type, Location loc, Player actor) {
        if (type == null || loc == null || loc.getWorld() == null) {
            return ChatColor.RED + "Niepoprawna lokalizacja.";
        }
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            return ChatColor.RED + "Na serwerze nie ma MythicMobs.";
        }
        if (type.isEventEnabled() && isActive()) {
            return ChatColor.RED + "Ten boss jest już zrespiony.";
        }

        clearCountdown();

        String mythic = mythicId(type);
        Entity spawned = MythicHook.spawn(plugin, mythic, loc);
        if (!(spawned instanceof LivingEntity living)) {
            return ChatColor.RED + "Nie udało się zrespić " + type.getMythicId()
                    + ChatColor.GRAY + " (sprawdź nazwę w MythicMobs).";
        }

        applyConfiguredHealth(type, living);
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyConfiguredHealth(type, living), 1L);

        if (type.isEventEnabled()) {
            startEvent(type, living);
        }

        String where = coords(loc);
        return ChatColor.GREEN + "Zrespiono " + ChatColor.WHITE + type.getDisplayName()
                + ChatColor.GRAY + " (" + type.getMythicId() + ") na " + ChatColor.WHITE + where
                + (actor != null ? ChatColor.GRAY + " w " + loc.getWorld().getName() : "")
                + (type.isEventEnabled() ? ChatColor.GRAY + "." : ChatColor.DARK_GRAY + " — pełny event później.");
    }

    public void registerDamage(Player player, double amount) {
        if (!isActive() || player == null || amount <= 0) return;
        damage.merge(player.getUniqueId(), amount, Double::sum);
        updateFightBar();
        refreshBoards();
    }

    public void onBossDeath(LivingEntity entity, EntityDeathEvent event) {
        if (!isBoss(entity) || !active) return;
        event.getDrops().clear();
        rewardTop(entity);
        active = false;
        arena.onBossKilled(activeBoss, entity.getWorld());
        updateFightBar();
        refreshBoards();
        Bukkit.getScheduler().runTaskLater(plugin, this::endEvent, 100L);
    }

    public void shutdown() {
        clearCountdown();
        arena.shutdown();
        endEvent();
    }

    private void rewardTop(LivingEntity entity) {
        DropSource src = activeBoss != null ? activeBoss.getDropSource() : null;
        if (src == null) return;
        Player top = topDamagerOnline();
        if (top == null && entity.getKiller() != null) top = entity.getKiller();
        if (top == null) return;
        plugin.getDropManager().giveOrDrop(top, src);
        top.sendMessage(ChatColor.GREEN + "Otrzymałeś drop ze " + src.getDisplayGenitive()
                + ChatColor.GRAY + " (TOP 1 obrażeń).");
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + displayName()
                + ChatColor.GRAY + " pokonany. Drop trafił do "
                + ChatColor.WHITE + top.getName() + ChatColor.GRAY + ".");
    }

    private Player topDamagerOnline() {
        for (Map.Entry<UUID, Double> e : top(32)) {
            Player player = Bukkit.getPlayer(e.getKey());
            if (player != null && player.isOnline()) return player;
        }
        return null;
    }

    private void sendWakeTitle(MythicBoss type) {
        String title = ChatColor.DARK_GRAY + "" + ChatColor.BOLD
                + plugin.getConfig().getString(type.configPath() + ".wake-title", "WŁADCA LOCHÓW");
        String sub = ChatColor.GRAY + "" + ChatColor.BOLD
                + plugin.getConfig().getString(type.configPath() + ".wake-subtitle", "BUDZI SIĘ ZE SNU");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(title, sub, 10, 80, 20);
        }
    }

    private void createCountdownBar(MythicBoss type, Location loc) {
        countdownBar = Bukkit.createBossBar(barText(countdownTotal, loc), BarColor.GREEN, BarStyle.SEGMENTED_20);
        countdownBar.setProgress(1.0);
        countdownBar.setVisible(true);
        for (Player player : Bukkit.getOnlinePlayers()) countdownBar.addPlayer(player);
    }

    private void updateCountdownBar() {
        if (countdownBar == null || pendingLoc == null) return;
        long left = Math.max(0L, countdownEnd - System.currentTimeMillis());
        countdownBar.setTitle(barText(left, pendingLoc));
        countdownBar.setProgress(Math.max(0.0, Math.min(1.0, left / (double) countdownTotal)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!countdownBar.getPlayers().contains(player)) countdownBar.addPlayer(player);
        }
    }

    private String barText(long leftMs, Location loc) {
        String name = pendingType == null ? "WŁADCA LOCHÓW"
                : plugin.getConfig().getString(pendingType.configPath() + ".wake-title", "WŁADCA LOCHÓW");
        String entry = pendingType == null ? "wejście do lochów"
                : plugin.getConfig().getString(pendingType.configPath() + ".bossbar-entry", "wejście do lochów");
        return ChatColor.DARK_GRAY + "" + ChatColor.BOLD + name
                + ChatColor.DARK_GRAY + "  »  "
                + ChatColor.WHITE + formatTime(leftMs)
                + ChatColor.GRAY + "  " + entry + "  "
                + ChatColor.WHITE + coords(loc);
    }

    private void finishCountdown() {
        MythicBoss type = pendingType;
        Location loc = pendingLoc;
        clearCountdown();
        if (type == null || loc == null) return;
        spawn(type, loc, null);
    }

    private void clearCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (countdownBar != null) {
            countdownBar.removeAll();
            countdownBar.setVisible(false);
            countdownBar = null;
        }
        pendingType = null;
        pendingLoc = null;
        countdownEnd = 0;
        countdownTotal = 0;
    }

    private void startEvent(MythicBoss type, LivingEntity living) {
        endEvent();
        activeBoss = type;
        boss = living;
        bossId = living.getUniqueId();
        damage.clear();
        active = true;
        configuredMaxHp = plugin.getConfig().getDouble(type.configPath() + ".health", 0);
        createFightBar();
        refreshBoards();
        arena.apply(type, living.getWorld());
        if (task != null) task.cancel();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!active) return;
            if (boss == null || !boss.isValid()) {
                active = false;
                refreshBoards();
                Bukkit.getScheduler().runTaskLater(plugin, this::endEvent, 100L);
                return;
            }
            updateFightBar();
            refreshBoards();
        }, 10L, 10L);
    }

    private void endEvent() {
        active = false;
        arena.restoreGlass();
        if (task != null) {
            task.cancel();
            task = null;
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = boards.get(player.getUniqueId());
            if (board != null && player.getScoreboard() == board) {
                player.setScoreboard(main);
            }
        }
        boards.clear();
        damage.clear();
        clearFightBar();
        boss = null;
        bossId = null;
        activeBoss = null;
        configuredMaxHp = 0;
    }

    private void refreshBoards() {
        if (activeBoss == null) return;
        List<Map.Entry<UUID, Double>> top = top(3);
        int hp = remainingHp();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> newBoard());
            Objective obj = board.getObjective("boss");
            if (obj == null) continue;
            paint(board, obj, top, hp, damage.getOrDefault(player.getUniqueId(), 0.0));
            if (player.getScoreboard() != board) player.setScoreboard(board);
        }
    }

    private Scoreboard newBoard() {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("boss", Criteria.DUMMY,
                ChatColor.DARK_GRAY + "" + ChatColor.BOLD + displayName(), RenderType.INTEGER);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        return board;
    }

    private void paint(Scoreboard board, Objective obj, List<Map.Entry<UUID, Double>> top, int hp, double mine) {
        for (String entry : new HashSet<>(board.getEntries())) board.resetScores(entry);

        setLine(board, obj, 9, ChatColor.DARK_GRAY + displayName() + ChatColor.GRAY + " " + appearedText());
        setLine(board, obj, 8, ChatColor.GRAY + "Pozostałe zdrowie władcy: " + ChatColor.WHITE + hp + "HP");
        setLine(board, obj, 7, blank(7));
        setLine(board, obj, 6, ChatColor.YELLOW + "TOP 3 OBRAŻEŃ");
        setLine(board, obj, 5, rankLine(1, top));
        setLine(board, obj, 4, rankLine(2, top));
        setLine(board, obj, 3, rankLine(3, top));
        setLine(board, obj, 2, blank(2));
        setLine(board, obj, 1, ChatColor.GRAY + "Twoje obrażenia: " + ChatColor.RED + formatDmg(mine));
    }

    private void setLine(Scoreboard board, Objective obj, int score, String text) {
        String key = "bl" + score;
        Team team = board.getTeam(key);
        if (team == null) team = board.registerNewTeam(key);
        String entry = dummy(score);
        if (!team.hasEntry(entry)) {
            for (String old : new HashSet<>(team.getEntries())) team.removeEntry(old);
            team.addEntry(entry);
        }
        if (text.length() <= 64) {
            team.setPrefix(text);
            team.setSuffix("");
        } else {
            team.setPrefix(text.substring(0, 64));
            team.setSuffix(text.substring(64, Math.min(128, text.length())));
        }
        obj.getScore(entry).setScore(score);
    }

    private static String dummy(int score) {
        ChatColor[] colors = ChatColor.values();
        return colors[Math.floorMod(score, 16)].toString()
                + ChatColor.RESET
                + colors[Math.floorMod(score + 5, 16)];
    }

    private static String blank(int n) {
        return ChatColor.RESET.toString() + ChatColor.values()[Math.floorMod(n, 16)];
    }

    private String rankLine(int place, List<Map.Entry<UUID, Double>> top) {
        String prefix = ChatColor.WHITE + "#" + place + ". ";
        if (place > top.size()) {
            return prefix + ChatColor.DARK_GRAY + "-";
        }
        Map.Entry<UUID, Double> e = top.get(place - 1);
        return prefix + ChatColor.WHITE + playerName(e.getKey())
                + ChatColor.GRAY + " " + ShopManager.ARROW + " "
                + ChatColor.RED + formatDmg(e.getValue());
    }

    private List<Map.Entry<UUID, Double>> top(int n) {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(damage.entrySet());
        list.sort(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()));
        if (list.size() > n) return new ArrayList<>(list.subList(0, n));
        return list;
    }

    private String playerName(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) return player.getName();
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : "???";
    }

    private static String formatDmg(double dmg) {
        return String.format("%.0f", Math.max(0, dmg));
    }

    static String formatTime(long ms) {
        long total = Math.max(0L, (ms + 999L) / 1000L);
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private static String coords(Location loc) {
        return loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }

    private int remainingHp() {
        if (boss == null || !boss.isValid()) return 0;
        double vanMax = vanillaMaxHp();
        if (vanMax <= 0) return 0;
        return (int) Math.ceil(Math.max(0, boss.getHealth() / vanMax * displayMaxHp()));
    }

    private double vanillaMaxHp() {
        if (boss == null || !boss.isValid()) return 1;
        AttributeInstance attr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null && attr.getValue() > 0) return attr.getValue();
        return Math.max(1.0, boss.getHealth());
    }

    private double displayMaxHp() {
        if (configuredMaxHp > 0) return configuredMaxHp;
        return vanillaMaxHp();
    }

    /** Minecraft cap to 1024 HP — skalujemy dmg, żeby 3000 na pasku było prawdziwe. */
    public void scaleIncomingDamage(EntityDamageEvent event) {
        double scale = healthScale();
        if (scale >= 0.999) return;
        event.setDamage(Math.max(0.0, event.getDamage() * scale));
    }

    public double toVirtualDamage(double vanillaDamage) {
        double scale = healthScale();
        if (scale <= 0) return vanillaDamage;
        return vanillaDamage / scale;
    }

    private double healthScale() {
        double cfg = displayMaxHp();
        double van = vanillaMaxHp();
        if (cfg <= van || cfg <= 0 || van <= 0) return 1.0;
        return van / cfg;
    }

    private void applyConfiguredHealth(MythicBoss type, LivingEntity living) {
        if (type == null || living == null || !living.isValid()) return;
        double hp = plugin.getConfig().getDouble(type.configPath() + ".health", 0);
        if (hp <= 0) return;
        AttributeInstance attr = living.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        try {
            attr.setBaseValue(hp);
        } catch (IllegalArgumentException ignored) {
            attr.setBaseValue(Math.min(hp, 1024));
        }
        living.setHealth(attr.getValue());
    }

    private void createFightBar() {
        clearFightBar();
        fightBar = Bukkit.createBossBar(fightBarTitle(), BarColor.GREEN, BarStyle.SEGMENTED_20);
        fightBar.addFlag(BarFlag.DARKEN_SKY);
        fightBar.setProgress(1.0);
        fightBar.setVisible(true);
        for (Player player : Bukkit.getOnlinePlayers()) fightBar.addPlayer(player);
        updateFightBar();
    }

    private void updateFightBar() {
        if (fightBar == null) return;
        double pct = Math.max(0.0, Math.min(1.0, remainingHp() / displayMaxHp()));
        fightBar.setProgress(pct);
        fightBar.setTitle(fightBarTitle());
        if (pct > 0.5) fightBar.setColor(BarColor.GREEN);
        else if (pct > 0.25) fightBar.setColor(BarColor.YELLOW);
        else fightBar.setColor(BarColor.RED);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!fightBar.getPlayers().contains(player)) fightBar.addPlayer(player);
        }
    }

    private String fightBarTitle() {
        String name = activeBoss == null ? "WŁADCA LOCHÓW"
                : plugin.getConfig().getString(activeBoss.configPath() + ".wake-title", "WŁADCA LOCHÓW");
        return ChatColor.DARK_GRAY + "" + ChatColor.BOLD + name
                + ChatColor.DARK_GRAY + "  »  "
                + ChatColor.WHITE + remainingHp()
                + ChatColor.GRAY + " HP";
    }

    private void clearFightBar() {
        if (fightBar == null) return;
        fightBar.removeAll();
        fightBar.setVisible(false);
        fightBar = null;
    }

    private String displayName() {
        return plugin.getConfig().getString(activeBoss.configPath() + ".display-name", activeBoss.getDisplayName());
    }

    private String appearedText() {
        return plugin.getConfig().getString(activeBoss.configPath() + ".appeared-text", "pojawił się w lochach");
    }

    private String mythicId(MythicBoss type) {
        String id = plugin.getConfig().getString(type.configPath() + ".mythic-id", type.getMythicId());
        MythicBoss matched = MythicBoss.fromInput(id);
        if (matched != null) return matched.getMythicId();
        if (id == null || id.isBlank()) return type.getMythicId();
        return id;
    }
}
