package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Zrodla dropu pokazywane w /drop i edytowane przez /adrop.
 */
public enum DropSource {
    WLADCA_ENDU(
            "wladca_endu",
            "Władcy Endu",
            Material.ALLAY_SPAWN_EGG,
            ChatColor.AQUA,
            2,
            "wladca endu", "wladcy endu", "endu", "end", "smok", "ender dragon",
            "ender_dragon", "dragon", "wladca_endu"
    ),
    JEDNOGLOWY_CERBER(
            "jednoglowy_cerber",
            "Jednogłowego Cerbera",
            Material.SKELETON_HORSE_SPAWN_EGG,
            ChatColor.RED,
            3,
            "jednoglowy cerber", "jednoglowego cerbera", "cerber", "jednoglowy",
            "jednoglowy_cerber", "cerberus"
    ),
    WLADCA_LOCHOW(
            "wladca_lochow",
            "Władcy lochów",
            Material.EVOKER_SPAWN_EGG,
            ChatColor.DARK_GRAY,
            5,
            "wladca lochow", "wladcy lochow", "lochy", "lochow", "dungeon",
            "wladca_lochow", "wladca lochu", "hollowknight", "hollow knight", "hollow_knight",
            "hollowking", "hollow king", "hollow_king"
    ),
    ZRZUT(
            "zrzut",
            "Zrzutu",
            Material.CHEST,
            ChatColor.GOLD,
            6,
            "zrzut", "zrzutu", "airdrop", "drop zrzut"
    );

    private final String id;
    private final String displayGenitive;
    private final Material icon;
    private final ChatColor color;
    private final int menuSlot;
    private final List<String> aliases;

    DropSource(String id, String displayGenitive, Material icon, ChatColor color, int menuSlot, String... aliases) {
        this.id = id;
        this.displayGenitive = displayGenitive;
        this.icon = icon;
        this.color = color;
        this.menuSlot = menuSlot;
        this.aliases = Arrays.asList(aliases);
    }

    public String getId() { return id; }
    public String getDisplayGenitive() { return displayGenitive; }
    public Material getIcon() { return icon; }
    public ChatColor getColor() { return color; }
    public int getMenuSlot() { return menuSlot; }
    public List<String> getAliases() { return aliases; }

    public String buttonName() {
        return color + "" + ChatColor.BOLD + "Drop ze " + displayGenitive;
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = ChatColor.stripColor(raw).toLowerCase(Locale.ROOT).trim();
        s = s.replace('ą', 'a').replace('ć', 'c').replace('ę', 'e')
                .replace('ł', 'l').replace('ń', 'n').replace('ó', 'o')
                .replace('ś', 's').replace('ź', 'z').replace('ż', 'z');
        s = s.replaceAll("[^a-z0-9 ]+", " ").replaceAll("\\s+", " ").trim();
        return s;
    }

    public boolean matches(String raw) {
        String n = normalize(raw);
        if (n.isEmpty()) return false;
        if (n.equals(id.replace('_', ' ')) || n.equals(id)) return true;
        for (String alias : aliases) {
            if (n.equals(normalize(alias))) return true;
        }
        return n.contains(normalize(displayGenitive));
    }

    public static DropSource fromInput(String raw) {
        String n = normalize(raw);
        if (n.isEmpty()) return null;
        for (DropSource src : values()) {
            if (src.matches(n)) return src;
        }
        return null;
    }

    public static DropSource fromId(String id) {
        if (id == null) return null;
        for (DropSource src : values()) {
            if (src.id.equalsIgnoreCase(id)) return src;
        }
        return null;
    }
}
