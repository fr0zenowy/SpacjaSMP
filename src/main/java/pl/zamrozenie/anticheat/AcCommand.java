package pl.zamrozenie.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /ac alerts | vl [gracz] | reset <gracz> | checks | reload */
public class AcCommand implements CommandExecutor, TabCompleter {

    private final AntiCheat ac;

    public AcCommand(AntiCheat ac) {
        this.ac = ac;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "alerts" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(AntiCheat.PREFIX + ChatColor.RED + "Tylko dla graczy."); return true; }
                boolean on = ac.toggleAlerts(p);
                p.sendMessage(AntiCheat.PREFIX + ChatColor.GRAY + "Alerty: " + (on ? ChatColor.GREEN + "WLACZONE" : ChatColor.RED + "WYLACZONE"));
            }
            case "vl" -> {
                if (args.length >= 2) {
                    Player t = Bukkit.getPlayerExact(args[1]);
                    if (t == null) { sender.sendMessage(AntiCheat.PREFIX + ChatColor.RED + "Gracz offline."); return true; }
                    PlayerData d = ac.dataIfPresent(t.getUniqueId());
                    if (d == null || d.totalVl <= 0) { sender.sendMessage(AntiCheat.PREFIX + ChatColor.GRAY + t.getName() + " nie ma naruszen."); return true; }
                    sender.sendMessage(AntiCheat.PREFIX + ChatColor.WHITE + t.getName() + ChatColor.GRAY + " — VL " + ChatColor.GOLD + AntiCheat.fmt(d.totalVl)
                            + ChatColor.DARK_GRAY + " (ping " + t.getPing() + "ms)");
                    for (Map.Entry<Check, Double> en : d.vl.entrySet()) {
                        if (en.getValue() <= 0) continue;
                        sender.sendMessage(ChatColor.DARK_GRAY + "  \u00BB " + ChatColor.WHITE + en.getKey().id + ChatColor.GRAY + ": " + ChatColor.YELLOW + AntiCheat.fmt(en.getValue()));
                    }
                } else {
                    boolean any = false;
                    for (Map.Entry<UUID, PlayerData> en : ac.all().entrySet()) {
                        if (en.getValue().totalVl <= 0) continue;
                        Player t = Bukkit.getPlayer(en.getKey());
                        if (t == null) continue;
                        any = true;
                        sender.sendMessage(AntiCheat.PREFIX + ChatColor.WHITE + t.getName() + ChatColor.GRAY + " — VL " + ChatColor.GOLD + AntiCheat.fmt(en.getValue().totalVl));
                    }
                    if (!any) sender.sendMessage(AntiCheat.PREFIX + ChatColor.GRAY + "Nikt nie ma aktywnych naruszen.");
                }
            }
            case "reset" -> {
                if (args.length < 2) { sender.sendMessage(AntiCheat.PREFIX + ChatColor.RED + "Uzycie: /ac reset <gracz>"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage(AntiCheat.PREFIX + ChatColor.RED + "Gracz offline."); return true; }
                PlayerData d = ac.dataIfPresent(t.getUniqueId());
                if (d != null) { d.totalVl = 0; d.vl.clear(); d.suspicion = 0; }
                sender.sendMessage(AntiCheat.PREFIX + ChatColor.GRAY + "Zresetowano VL gracza " + ChatColor.WHITE + t.getName());
            }
            case "ai", "profil" -> {
                if (args.length < 2) { sender.sendMessage(AntiCheat.PREFIX + ChatColor.RED + "Uzycie: /ac ai <gracz>"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage(AntiCheat.PREFIX + ChatColor.RED + "Gracz offline."); return true; }
                BehaviorAnalyzer ba = ac.getAnalyzer();
                if (ba == null) { sender.sendMessage(AntiCheat.PREFIX + ChatColor.RED + "Analizator nieaktywny."); return true; }
                for (String line : ba.profile(t, ac.data(t))) sender.sendMessage(line);
            }
            case "checks" -> {
                sender.sendMessage(AntiCheat.PREFIX + ChatColor.GRAY + "Checki:");
                for (Check c : Check.values()) {
                    String state = ac.isCheckEnabled(c) ? ChatColor.GREEN + "ON " : ChatColor.RED + "OFF";
                    String mode = c.blocks ? ChatColor.RED + "blokuje" : ChatColor.YELLOW + "alert";
                    sender.sendMessage(ChatColor.DARK_GRAY + "  " + state + " " + ChatColor.WHITE + c.id + ChatColor.DARK_GRAY + " [" + mode + ChatColor.DARK_GRAY + "] "
                            + ChatColor.GRAY + c.description);
                }
            }
            case "antixray", "xray" -> {
                AntiXrayConfigurator ax = ac.getAntiXray();
                sender.sendMessage(AntiCheat.PREFIX + ChatColor.GRAY + "Anti-Xray (Paper): " + (ax == null ? ChatColor.RED + "brak" : ax.getStatus()));
                if (ax != null && ax.isRestartRequired()) sender.sendMessage(ChatColor.DARK_GRAY + "  \u00BB " + ChatColor.RED + "Wymagany restart serwera!");
                if (ax != null && ax.isAllowFlight()) sender.sendMessage(ChatColor.DARK_GRAY + "  \u00BB " + ChatColor.YELLOW + "server.properties: allow-flight=true — ustaw false");
            }
            case "reload" -> {
                ac.getPlugin().reloadConfig();
                ac.reload();
                sender.sendMessage(AntiCheat.PREFIX + ChatColor.GREEN + "Przeladowano konfiguracje anticheata.");
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(AntiCheat.PREFIX + ChatColor.GRAY + "Zamrozenie AntiCheat " + (ac.isEnabled() ? ChatColor.GREEN + "(aktywny)" : ChatColor.RED + "(wylaczony)"));
        s.sendMessage(ChatColor.DARK_GRAY + "  \u00BB " + ChatColor.WHITE + "/ac alerts " + ChatColor.GRAY + "- wlacz/wylacz alerty");
        s.sendMessage(ChatColor.DARK_GRAY + "  \u00BB " + ChatColor.WHITE + "/ac vl [gracz] " + ChatColor.GRAY + "- poziom naruszen");
        s.sendMessage(ChatColor.DARK_GRAY + "  \u00BB " + ChatColor.WHITE + "/ac reset <gracz> " + ChatColor.GRAY + "- wyzeruj VL");
        s.sendMessage(ChatColor.DARK_GRAY + "  \u00BB " + ChatColor.WHITE + "/ac ai <gracz> " + ChatColor.GRAY + "- profil killaury");
        s.sendMessage(ChatColor.DARK_GRAY + "  \u00BB " + ChatColor.WHITE + "/ac checks " + ChatColor.GRAY + "- lista checkow");
        s.sendMessage(ChatColor.DARK_GRAY + "  \u00BB " + ChatColor.WHITE + "/ac antixray " + ChatColor.GRAY + "- status anti-xray Papera");
        s.sendMessage(ChatColor.DARK_GRAY + "  \u00BB " + ChatColor.WHITE + "/ac reload " + ChatColor.GRAY + "- przeladuj config");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[]{"alerts", "vl", "reset", "ai", "checks", "antixray", "reload"})
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("vl") || args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("ai") || args[0].equalsIgnoreCase("profil"))) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
        }
        return out;
    }
}
