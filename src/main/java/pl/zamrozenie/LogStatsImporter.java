package pl.zamrozenie;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Przy pierwszym starcie zlicza zabojstwa i smierci z logow serwera,
 * od pliku 2026-09-10-3.log wzwyz.
 */
public final class LogStatsImporter {

    public static final String FROM_LOG = "2026-09-10-3.log";

    private static final int MIN_KEY = sortKey(FROM_LOG);

    private static final Pattern UUID_LINE = Pattern.compile(
            "UUID of player ([A-Za-z0-9_]{1,16}) is ([0-9a-fA-F-]{36})");
    private static final Pattern INFO_LINE = Pattern.compile(
            "^\\[(\\d{2}:\\d{2}:\\d{2})\\] \\[Server thread/INFO\\]: (.*)$");
    private static final Pattern TAG = Pattern.compile("^\\[[^\\]]+\\]\\s+");
    private static final Pattern DEATH = Pattern.compile(
            "^(was slain|was shot|was killed|was pummeled|was blown up|was fireballed|"
                    + "was squashed|was pricked|was poked|was impaled|was skewered|"
                    + "was obliterated|was stung|was squished|was struck by lightning|"
                    + "drowned|died|burned|went up in flames|tried to swim|hit the ground|"
                    + "fell\\b|starved|withered|suffocated|froze|experienced kinetic|"
                    + "discovered the floor|didn.t want to live|left the confines|"
                    + "walked into|wylogowa)");
    private static final Pattern KILLER = Pattern.compile(
            "(?:(?:was (?:slain|shot|killed|pummeled|blown up|fireballed|impaled|"
                    + "skewered|obliterated) by)|whilst fighting|to escape|walki z|"
                    + "same world as)\\s+(?:\\[[^\\]]+\\]\\s+)*([A-Za-z0-9_]{1,16})");
    private static final Pattern USERCACHE = Pattern.compile(
            "\"uuid\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SKIP = Pattern.compile(
            "issued server command|logged in|logged out|lost connection|UUID of player|"
                    + "joined the game|left the game|Named entity|Villager |"
                    + "moved too quickly|has made the advancement|has completed the challenge|"
                    + "Unknown command|Cannot send chat message");

    private LogStatsImporter() {}

    public static Result scan(ZamrozeniePlugin plugin) {
        File serverRoot = plugin.getDataFolder().getParentFile();
        if (serverRoot != null) serverRoot = serverRoot.getParentFile();
        if (serverRoot == null) return null;

        Map<String, UUID> names = new HashMap<>();
        readUsercache(new File(serverRoot, "usercache.json"), names);

        File logsDir = new File(serverRoot, "logs");
        List<File> all = new ArrayList<>();
        collectLogs(logsDir, all);
        if (all.isEmpty()) return null;

        for (File file : all) {
            forEachLine(file, line -> {
                Matcher m = UUID_LINE.matcher(line);
                if (m.find()) names.put(m.group(1), UUID.fromString(m.group(2)));
            });
        }
        if (names.isEmpty()) return null;

        List<String> sorted = new ArrayList<>(names.keySet());
        sorted.sort(Comparator.comparingInt(String::length).reversed());

        Map<UUID, Integer> kills = new HashMap<>();
        Map<UUID, Integer> deaths = new HashMap<>();
        Set<String> seen = new HashSet<>();
        int deathEvents = 0;

        for (File file : all) {
            if (sortKey(file.getName()) < MIN_KEY) continue;
            forEachLine(file, line -> {
                Matcher info = INFO_LINE.matcher(line);
                if (!info.matches()) return;
                String time = info.group(1);
                String msg = info.group(2);
                if (SKIP.matcher(msg).find()) return;

                String rest = stripTags(msg, sorted);
                String victim = null;
                String after = null;
                for (String name : sorted) {
                    if (rest.startsWith(name + " ")) {
                        victim = name;
                        after = rest.substring(name.length() + 1);
                        break;
                    }
                }
                if (victim == null || !DEATH.matcher(after).find()) return;

                String key = time + "|" + victim + "|" + after;
                if (!seen.add(key)) return;

                UUID victimId = names.get(victim);
                if (victimId == null) return;
                deaths.merge(victimId, 1, Integer::sum);

                Matcher km = KILLER.matcher(after);
                if (km.find()) {
                    String killer = km.group(1);
                    UUID killerId = names.get(killer);
                    if (killerId != null && !killerId.equals(victimId)) {
                        kills.merge(killerId, 1, Integer::sum);
                    }
                }
            });
        }
        deathEvents = seen.size();
        return new Result(FROM_LOG, names, kills, deaths, deathEvents);
    }

    private static String stripTags(String msg, List<String> sorted) {
        String rest = msg;
        Matcher tag = TAG.matcher(rest);
        while (tag.find() && tag.start() == 0) {
            String after = rest.substring(tag.end());
            boolean hit = false;
            for (String name : sorted) {
                if (after.startsWith(name + " ") || after.equals(name)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) break;
            rest = after;
            tag = TAG.matcher(rest);
        }
        return rest;
    }

    private static void readUsercache(File file, Map<String, UUID> names) {
        if (file == null || !file.isFile()) return;
        String raw;
        try {
            raw = java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return;
        }
        Matcher m = USERCACHE.matcher(raw);
        while (m.find()) {
            try {
                names.put(m.group(2), UUID.fromString(m.group(1)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static void collectLogs(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File kid : kids) {
            if (kid.isDirectory()) collectLogs(kid, out);
            else if (isLogName(kid.getName())) out.add(kid);
        }
    }

    private static boolean isLogName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".log") || lower.endsWith(".log.gz");
    }

    static int sortKey(String name) {
        String base = name.toLowerCase(Locale.ROOT);
        if (base.endsWith(".gz")) base = base.substring(0, base.length() - 3);
        if (base.equals("latest.log")) return Integer.MAX_VALUE;
        Matcher m = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})-(\\d+)\\.log").matcher(base);
        if (!m.matches()) return -1;
        return Integer.parseInt(m.group(1) + m.group(2) + m.group(3)) * 100
                + Integer.parseInt(m.group(4));
    }

    private static void forEachLine(File file, LineConsumer consumer) {
        try (InputStream raw = open(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                consumer.accept(line);
            }
        } catch (IOException ignored) {
        }
    }

    private static InputStream open(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".gz")) {
            return new GZIPInputStream(in);
        }
        return in;
    }

    public static void writeSnapshot(File target, Result result, Map<UUID, String> extraNames) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("imported-from-logs", true);
        yaml.set("from-log", result.fromLog);
        Set<UUID> ids = new HashSet<>();
        ids.addAll(result.kills.keySet());
        ids.addAll(result.deaths.keySet());
        List<UUID> sorted = new ArrayList<>(ids);
        sorted.sort(Comparator.comparing(UUID::toString));
        Map<UUID, String> reverse = new HashMap<>();
        for (Map.Entry<String, UUID> e : result.names.entrySet()) {
            reverse.put(e.getValue(), e.getKey());
        }
        if (extraNames != null) reverse.putAll(extraNames);
        for (UUID id : sorted) {
            String key = id.toString();
            yaml.set(key + ".name", reverse.getOrDefault(id, "???"));
            yaml.set(key + ".kills", result.kills.getOrDefault(id, 0));
            yaml.set(key + ".deaths", result.deaths.getOrDefault(id, 0));
        }
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            yaml.save(target);
        } catch (IOException ignored) {
        }
    }

    @FunctionalInterface
    private interface LineConsumer {
        void accept(String line);
    }

    public static final class Result {
        public final String fromLog;
        public final Map<String, UUID> names;
        public final Map<UUID, Integer> kills;
        public final Map<UUID, Integer> deaths;
        public final int deathEvents;

        Result(String fromLog, Map<String, UUID> names,
               Map<UUID, Integer> kills, Map<UUID, Integer> deaths, int deathEvents) {
            this.fromLog = fromLog;
            this.names = names;
            this.kills = kills;
            this.deaths = deaths;
            this.deathEvents = deathEvents;
        }
    }
}
