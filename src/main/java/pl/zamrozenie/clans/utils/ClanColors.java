package pl.zamrozenie.clans.utils;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ClanColors {

    private static final Map<String, ChatColor> BY_NAME = new LinkedHashMap<>();
    private static final Map<ChatColor, String> DISPLAY = new LinkedHashMap<>();

    static {
        add("czerwony", "red", ChatColor.RED);
        add("ciemnoczerwony", "bordowy", "dark_red", ChatColor.DARK_RED);
        add("niebieski", "blue", ChatColor.BLUE);
        add("ciemnoniebieski", "dark_blue", ChatColor.DARK_BLUE);
        add("zielony", "green", ChatColor.GREEN);
        add("ciemnozielony", "dark_green", ChatColor.DARK_GREEN);
        add("turkus", "aqua", ChatColor.AQUA);
        add("ciemnyturkus", "dark_aqua", ChatColor.DARK_AQUA);
        add("żółty", "zolty", "yellow", ChatColor.YELLOW);
        add("złoty", "zloty", "pomaranczowy", "gold", ChatColor.GOLD);
        add("różowy", "rozowy", "pink", "light_purple", ChatColor.LIGHT_PURPLE);
        add("fioletowy", "purple", "dark_purple", ChatColor.DARK_PURPLE);
        add("biały", "bialy", "white", ChatColor.WHITE);
        add("szary", "gray", "grey", ChatColor.GRAY);
        add("ciemnoszary", "dark_gray", ChatColor.DARK_GRAY);
        add("czarny", "black", ChatColor.BLACK);
    }

    private ClanColors() {}

    private static void add(String primary, ChatColor color) {
        BY_NAME.put(normalize(primary), color);
        DISPLAY.putIfAbsent(color, primary);
    }

    private static void add(String primary, String alias, ChatColor color) {
        add(primary, color);
        BY_NAME.put(normalize(alias), color);
    }

    private static void add(String primary, String a, String b, ChatColor color) {
        add(primary, a, color);
        BY_NAME.put(normalize(b), color);
    }

    private static void add(String primary, String a, String b, String c, ChatColor color) {
        add(primary, a, b, color);
        BY_NAME.put(normalize(c), color);
    }

    public static ChatColor fromInput(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return BY_NAME.get(normalize(raw));
    }

    public static ChatColor fromStored(String raw) {
        if (raw == null || raw.isBlank()) return ChatColor.AQUA;
        try {
            ChatColor color = ChatColor.valueOf(raw.toUpperCase(Locale.ROOT));
            if (color.isFormat()) return ChatColor.AQUA;
            return color;
        } catch (IllegalArgumentException ignored) {
            ChatColor byName = fromInput(raw);
            return byName != null ? byName : ChatColor.AQUA;
        }
    }

    public static String displayName(ChatColor color) {
        ChatColor c = color != null ? color : ChatColor.AQUA;
        return DISPLAY.getOrDefault(c, c.name().toLowerCase(Locale.ROOT));
    }

    public static List<String> suggestions() {
        return new ArrayList<>(DISPLAY.values());
    }

    public static Map<ChatColor, String> displayMap() {
        return DISPLAY;
    }

    private static String normalize(String raw) {
        return raw.toLowerCase(Locale.ROOT)
                .replace("ą", "a").replace("ć", "c").replace("ę", "e")
                .replace("ł", "l").replace("ń", "n").replace("ó", "o")
                .replace("ś", "s").replace("ź", "z").replace("ż", "z")
                .replace("-", "").replace(" ", "_");
    }
}
