package pl.zamrozenie.clans.utils;

import org.bukkit.ChatColor;
import pl.zamrozenie.clans.models.Clan;

public class MessageUtil {
    
    // Prefiksy
    public static final String SUCCESS = "&8[&a✔&8] ";
    public static final String ERROR = "&8[&c✘&8] ";
    public static final String WARNING = "&8[&6⚠&8] ";
    public static final String INFO = "&8[&3ℹ&8] ";
    public static final String CLAN = "&8[&3KLAN&8] ";
    public static final String ALLIANCE = "&8[&3⚔&8] ";
    public static final String HOME = "&8[&3⌂&8] ";
    
    // Kolory
    public static final String PRIMARY = "&3";
    public static final String SECONDARY = "&7";
    public static final String ACCENT = "&f";
    public static final String HIGHLIGHT = "&e";
    
    public static String format(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public static String success(String message) {
        return format(SUCCESS + "&a" + message);
    }
    
    public static String error(String message) {
        return format(ERROR + "&c" + message);
    }
    
    public static String warning(String message) {
        return format(WARNING + "&7" + message);
    }
    
    public static String info(String message) {
        return format(INFO + "&7" + message);
    }
    
    public static String clan(String message) {
        return format(CLAN + "&7" + message);
    }
    
    public static String formatClanTag(String tag) {
        return formatClanTag(tag, ChatColor.AQUA);
    }

    public static String formatClanTag(Clan clan) {
        if (clan == null) return "";
        return formatClanTag(clan.getTag(), clan.getColor());
    }

    public static String formatClanTag(String tag, ChatColor color) {
        ChatColor c = color != null ? color : ChatColor.AQUA;
        return ChatColor.WHITE + "[" + c + tag + ChatColor.WHITE + "]";
    }
    
    public static String formatPlayerRank(String playerName, String rank) {
        return format("&f" + playerName + " &8[&f" + rank + "&8]");
    }
    
    public static String divider() {
        return format("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
    
    public static String shortDivider() {
        return format("&8▬▬▬▬▬▬▬▬▬▬ &3&lKlany &8▬▬▬▬▬▬▬▬▬▬");
    }
}