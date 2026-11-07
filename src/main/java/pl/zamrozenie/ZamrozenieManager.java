package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class ZamrozenieManager {

    private final ZamrozeniePlugin plugin;
    private boolean frozen = false;
    private int remainingSeconds = 0;
    private BukkitTask countdownTask;

    private static final String TITLE_TEXT = ChatColor.AQUA + "" + ChatColor.BOLD + "Zamrożenie!";

    public ZamrozenieManager(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
    }

    public void startFreeze(int seconds) {
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        frozen = true;
        remainingSeconds = seconds;

        freezeAllMobs();
        sendTitleToAll();

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            remainingSeconds--;
            if (remainingSeconds <= 0) {
                stopFreeze(true);
            } else {
                sendTitleToAll();
            }
        }, 20L, 20L);
    }

    public void stopFreeze(boolean natural) {
        frozen = false;
        remainingSeconds = 0;

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        unfreezeAllMobs();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (natural) {
                player.sendTitle(
                    ChatColor.AQUA + "" + ChatColor.BOLD + "Odmrożono!",
                    ChatColor.GRAY + "Każdy został odmrożony!",
                    10, 60, 20
                );
                player.sendMessage(ChatColor.GREEN + "Zamrożenie zostało zakończone.");
            } else {
                player.resetTitle();
                player.sendMessage(ChatColor.YELLOW + "Zamrożenie zostało przerwane przez operatora.");
            }
        }
    }

    public void freezeAllMobs() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Mob mob) {
                    mob.setAI(false);
                }
            }
        }
    }

    private void unfreezeAllMobs() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Mob mob) {
                    mob.setAI(true);
                }
            }
        }
    }

    public void sendTitleToPlayer(Player player) {
        if (!frozen) return;
        String subtitle = ChatColor.GRAY + "Odmrożenie za: " + ChatColor.GREEN + formatTime(remainingSeconds);
        player.sendTitle(TITLE_TEXT, subtitle, 0, 25, 5);
    }

    private void sendTitleToAll() {
        String subtitle = ChatColor.GRAY + "Odmrożenie za: " + ChatColor.GREEN + formatTime(remainingSeconds);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(TITLE_TEXT, subtitle, 0, 25, 5);
        }
    }

    private String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int secs = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(secs).append("s");
        return sb.toString().trim();
    }

    public boolean isFrozen() {
        return frozen;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    /**
     * Parsuje czas z formatu: 60s, 10m, 2h lub kombinacje np. 1h30m
     * Zwraca -1 jesli format niepoprawny.
     */
    public static int parseTime(String input) {
        if (input == null || input.isEmpty()) return -1;

        input = input.toLowerCase().trim();
        int total = 0;
        StringBuilder num = new StringBuilder();
        boolean hasAny = false;

        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
            } else if (c == 's') {
                if (num.length() == 0) return -1;
                total += Integer.parseInt(num.toString());
                num.setLength(0);
                hasAny = true;
            } else if (c == 'm') {
                if (num.length() == 0) return -1;
                total += Integer.parseInt(num.toString()) * 60;
                num.setLength(0);
                hasAny = true;
            } else if (c == 'h') {
                if (num.length() == 0) return -1;
                total += Integer.parseInt(num.toString()) * 3600;
                num.setLength(0);
                hasAny = true;
            } else {
                return -1;
            }
        }

        // Jesli zostaly cyfry bez jednostki - traktuj jako sekundy
        if (num.length() > 0) {
            total += Integer.parseInt(num.toString());
            hasAny = true;
        }

        return hasAny ? total : -1;
    }
}
