package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ZrespBossaCommand implements CommandExecutor, TabCompleter {

    private static final Pattern DURATION = Pattern.compile(
            "(?i)^(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m(?:in)?)?(?:(\\d+)s(?:ec|ek)?)?$"
    );
    private static final List<String> TIME_SUGGESTIONS = List.of("15m", "30m", "1h");

    private final BossEventManager events;

    public ZrespBossaCommand(BossEventManager events) {
        this.events = events;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zamrozenie.zrespbossa")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnień.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Użycie: /" + label + " <boss> [czas] [x y z]");
            sender.sendMessage(ChatColor.GRAY + "Bossy: " + MythicBoss.names());
            sender.sendMessage(ChatColor.GRAY + "Czas: 30m, 15 m, 1h — title + bossbar, potem spawn.");
            sender.sendMessage(ChatColor.GRAY + "Bez kordów boss respi się tam, gdzie stoisz.");
            return true;
        }

        MythicBoss boss = MythicBoss.fromInput(args[0]);
        if (boss == null) {
            sender.sendMessage(ChatColor.RED + "Nie znam bossa: " + args[0]);
            sender.sendMessage(ChatColor.GRAY + "Dostępne: " + MythicBoss.names());
            return true;
        }

        Parsed rest = parseRest(sender, Arrays.copyOfRange(args, 1, args.length));
        if (rest == null) return true;

        if (rest.delayMs > 0) {
            sender.sendMessage(events.schedule(boss, rest.loc, rest.delayMs,
                    sender instanceof Player p ? p : null));
        } else {
            sender.sendMessage(events.spawn(boss, rest.loc, sender instanceof Player p ? p : null));
        }
        return true;
    }

    private Parsed parseRest(CommandSender sender, String[] rest) {
        long delay = 0;
        List<String> coords = new ArrayList<>();
        for (int i = 0; i < rest.length; i++) {
            Long token = parseDurationToken(rest[i]);
            if (token != null) {
                delay += token;
                continue;
            }
            if (i + 1 < rest.length && isNumber(rest[i]) && isTimeUnit(rest[i + 1])) {
                Long combined = parseDurationToken(rest[i] + rest[i + 1]);
                if (combined != null) {
                    delay += combined;
                    i++;
                    continue;
                }
            }
            coords.add(rest[i]);
        }
        Location loc = resolveLocation(sender, coords.toArray(String[]::new));
        if (loc == null) return null;
        return new Parsed(delay, loc);
    }

    static Long parseDurationToken(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.replace(" ", "").toLowerCase(Locale.ROOT);
        Matcher m = DURATION.matcher(s);
        if (!m.matches()) return null;
        boolean any = false;
        long ms = 0;
        if (m.group(1) != null) { ms += Long.parseLong(m.group(1)) * 24L * 3600_000L; any = true; }
        if (m.group(2) != null) { ms += Long.parseLong(m.group(2)) * 3600_000L; any = true; }
        if (m.group(3) != null) { ms += Long.parseLong(m.group(3)) * 60_000L; any = true; }
        if (m.group(4) != null) { ms += Long.parseLong(m.group(4)) * 1000L; any = true; }
        if (!any || ms <= 0) return null;
        return Math.min(ms, 7L * 24L * 3600_000L);
    }

    private static boolean isNumber(String raw) {
        try {
            Double.parseDouble(raw.replace(',', '.'));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isTimeUnit(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        return s.equals("s") || s.equals("sec") || s.equals("sek")
                || s.equals("m") || s.equals("min")
                || s.equals("h") || s.equals("godz");
    }

    private Location resolveLocation(CommandSender sender, String[] coords) {
        Double x = coords.length >= 3 ? parseCoord(coords[0]) : null;
        Double y = coords.length >= 3 ? parseCoord(coords[1]) : null;
        Double z = coords.length >= 3 ? parseCoord(coords[2]) : null;
        boolean exact = x != null && y != null && z != null;

        if (sender instanceof Player player) {
            if (!exact) return player.getLocation();
            World world = player.getWorld();
            if (coords.length >= 4 && sender.getServer().getWorld(coords[3]) != null) {
                world = sender.getServer().getWorld(coords[3]);
            }
            return new Location(world, x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
        }

        if (!exact) {
            sender.sendMessage(ChatColor.RED + "Konsola: /zrespbossa <boss> [czas] <x> <y> <z> [świat]");
            return null;
        }
        World world = coords.length >= 4
                ? sender.getServer().getWorld(coords[3])
                : sender.getServer().getWorlds().get(0);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Nie znaleziono świata.");
            return null;
        }
        return new Location(world, x, y, z);
    }

    private static Double parseCoord(String raw) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Arrays.stream(MythicBoss.values())
                    .map(MythicBoss::getMythicId)
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String t : TIME_SUGGESTIONS) {
                if (t.startsWith(prefix)) out.add(t);
            }
            if (sender instanceof Player player) out.add(String.valueOf(player.getLocation().getBlockX()));
            return out;
        }
        if (sender instanceof Player player && args.length >= 3 && args.length <= 5) {
            Location loc = player.getLocation();
            boolean timeFirst = parseDurationToken(args[1]) != null
                    || (isNumber(args[1]) && isTimeUnit(args.length > 2 ? args[2] : ""));
            int coordIndex = timeFirst ? args.length - (parseDurationToken(args[1]) != null ? 2 : 3) : args.length - 2;
            if (coordIndex == 0) return List.of(String.valueOf(loc.getBlockX()));
            if (coordIndex == 1) return List.of(String.valueOf(loc.getBlockY()));
            if (coordIndex == 2) return List.of(String.valueOf(loc.getBlockZ()));
        }
        return List.of();
    }

    private record Parsed(long delayMs, Location loc) {}
}
