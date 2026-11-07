package pl.zamrozenie;

import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Pojedynek w osobnym borderze 10x10 ustawianym w miejscu walki.
 * Po smierci / wyjsciu / limicie czasu border znika.
 */
public class DuelCageManager {

    public static final int CAGE_SIZE = 10;
    public static final int MAX_DURATION_SEC = 20;

    private final ZamrozeniePlugin plugin;

    /** arenaId -> sesja */
    private final Map<Integer, DuelSession> sessions = new HashMap<>();
    /** gracz -> arenaId */
    private final Map<UUID, Integer> playerArena = new HashMap<>();
    private int nextArenaId = 0;
    private BukkitTask tickTask;

    public DuelCageManager(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickDuels, 20L, 10L);
    }

    public boolean isInDuel(UUID uuid) {
        return playerArena.containsKey(uuid);
    }

    public boolean isInDuel(Player p) {
        return isInDuel(p.getUniqueId());
    }

    public boolean canPlayersFight(Player first, Player second) {
        Integer firstArena = playerArena.get(first.getUniqueId());
        Integer secondArena = playerArena.get(second.getUniqueId());
        return firstArena != null && firstArena.equals(secondArena);
    }

    /** Rozpoczyna pojedynek miedzy atakujacym a celem. */
    public void startDuel(Player attacker, Player victim) {
        if (attacker.equals(victim)) return;
        if (!attacker.getWorld().equals(victim.getWorld())) {
            attacker.sendMessage(ChatColor.RED + "Ten gracz jest w innym swiecie.");
            return;
        }
        if (isInDuel(attacker) || isInDuel(victim)) {
            attacker.sendMessage(ChatColor.RED + "Ty lub ten gracz jestescie juz w klatce!");
            return;
        }
        Location center = attacker.getLocation().clone().add(victim.getLocation()).multiply(0.5);
        center.setWorld(attacker.getWorld());

        int id = nextArenaId++;
        DuelSession session = new DuelSession(id, attacker, victim, center);
        sessions.put(id, session);
        playerArena.put(attacker.getUniqueId(), id);
        playerArena.put(victim.getUniqueId(), id);

        session.previousBorders.put(attacker.getUniqueId(), attacker.getWorldBorder());
        session.previousBorders.put(victim.getUniqueId(), victim.getWorldBorder());
        applyBorder(attacker, center);
        applyBorder(victim, center);
        createBossBar(session, attacker, victim);
        refreshVisibility();

        attacker.sendTitle(
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Border!",
                ChatColor.GRAY + "1v1 z " + ChatColor.WHITE + victim.getName(),
                5, 40, 10
        );
        victim.sendTitle(
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Border!",
                ChatColor.GRAY + "1v1 z " + ChatColor.WHITE + attacker.getName(),
                5, 40, 10
        );
        attacker.playSound(attacker.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1f, 1.2f);
        victim.playSound(victim.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1f, 1.2f);
    }

    private void applyBorder(Player player, Location center) {
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(center.getX(), center.getZ());
        border.setSize(CAGE_SIZE);
        border.setWarningDistance(0);
        border.setDamageBuffer(0.0);
        border.setDamageAmount(1.0);
        player.setWorldBorder(border);
    }

    private void createBossBar(DuelSession session, Player attacker, Player victim) {
        BossBar bossBar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID);
        session.bossBar = bossBar;
        bossBar.addPlayer(attacker);
        bossBar.addPlayer(victim);
        updateBossBar(session);
        bossBar.setVisible(true);
    }

    private void updateBossBar(DuelSession session) {
        if (session.bossBar == null) return;
        long elapsed = System.currentTimeMillis() - session.startTime;
        double progress = 1.0 - (double) elapsed / (MAX_DURATION_SEC * 1000.0);
        progress = Math.max(0.0, Math.min(1.0, progress));
        int remainingSeconds = (int) Math.ceil((MAX_DURATION_SEC * 1000L - elapsed) / 1000.0);
        remainingSeconds = Math.max(0, remainingSeconds);

        session.bossBar.setTitle(
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "ZNAJDUJESZ SIE W KLATCE! "
                        + ChatColor.WHITE + "Pozostaly czas: "
                        + ChatColor.YELLOW + remainingSeconds + "s"
        );
        session.bossBar.setProgress(progress);
    }

    public void handleJoin(Player joiner) {
        Bukkit.getScheduler().runTask(plugin, this::refreshVisibility);
    }

    private void tickDuels() {
        long now = System.currentTimeMillis();
        List<Integer> toEnd = new ArrayList<>();

        for (DuelSession s : sessions.values()) {
            Player p1 = Bukkit.getPlayer(s.player1);
            Player p2 = Bukkit.getPlayer(s.player2);

            if (p1 == null || !p1.isOnline() || p2 == null || !p2.isOnline()) {
                toEnd.add(s.id);
                continue;
            }

            if (now - s.startTime > MAX_DURATION_SEC * 1000L) {
                p1.sendMessage(ChatColor.YELLOW + "Czas klatki minal — wracacie na swoje miejsca.");
                p2.sendMessage(ChatColor.YELLOW + "Czas klatki minal — wracacie na swoje miejsca.");
                toEnd.add(s.id);
                continue;
            }
            updateBossBar(s);
        }

        for (int id : toEnd) endDuel(id, null);
    }

    /** Konczy pojedynek (smierc, quit, timeout). winner moze byc null. */
    public void endDuel(int arenaId, UUID winner) {
        DuelSession s = sessions.remove(arenaId);
        if (s == null) return;

        playerArena.remove(s.player1);
        playerArena.remove(s.player2);

        Player p1 = Bukkit.getPlayer(s.player1);
        Player p2 = Bukkit.getPlayer(s.player2);

        restoreBorder(p1, s.previousBorders.get(s.player1));
        restoreBorder(p2, s.previousBorders.get(s.player2));
        removeBossBar(s);
        refreshVisibility();

        if (winner != null) {
            Player w = Bukkit.getPlayer(winner);
            String name = w != null ? w.getName() : "?";
            if (p1 != null && p1.isOnline()) p1.sendMessage(ChatColor.GOLD + "Border zakonczony. Zwyciezca: " + name);
            if (p2 != null && p2.isOnline()) p2.sendMessage(ChatColor.GOLD + "Border zakonczony. Zwyciezca: " + name);
        }
    }

    public void endDuelForPlayer(UUID uuid, UUID killer) {
        Integer id = playerArena.get(uuid);
        if (id != null) endDuel(id, killer);
    }

    private void restoreBorder(Player player, WorldBorder previousBorder) {
        if (player != null && player.isOnline()) {
            player.setWorldBorder(previousBorder);
        }
    }

    private void removeBossBar(DuelSession session) {
        if (session.bossBar != null) {
            session.bossBar.removeAll();
            session.bossBar.setVisible(false);
            session.bossBar = null;
        }
    }

    private void refreshVisibility() {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player viewer : onlinePlayers) {
            for (Player target : onlinePlayers) {
                if (viewer.equals(target)) continue;
                if (shouldPlayersSeeEachOther(viewer, target)) {
                    viewer.showPlayer(plugin, target);
                } else {
                    viewer.hidePlayer(plugin, target);
                }
            }
        }
    }

    private boolean shouldPlayersSeeEachOther(Player first, Player second) {
        Integer firstArena = playerArena.get(first.getUniqueId());
        Integer secondArena = playerArena.get(second.getUniqueId());
        if (firstArena == null && secondArena == null) return true;
        return firstArena != null && firstArena.equals(secondArena);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        new ArrayList<>(sessions.keySet()).forEach(id -> endDuel(id, null));
    }

    // ─── Sesja ───────────────────────────────────────────────────────────────────────

    private static class DuelSession {
        final int id;
        final UUID player1;
        final UUID player2;
        final Location center;
        final long startTime = System.currentTimeMillis();
        final Map<UUID, WorldBorder> previousBorders = new HashMap<>();
        BossBar bossBar;

        DuelSession(int id, Player p1, Player p2, Location center) {
            this.id = id;
            this.player1 = p1 != null ? p1.getUniqueId() : null;
            this.player2 = p2 != null ? p2.getUniqueId() : null;
            this.center = center;
        }
    }
}
