package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LifestealCommand implements CommandExecutor, TabCompleter {

    private final LifestealManager ls;

    public LifestealCommand(LifestealManager ls) {
        this.ls = ls;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // /lifesteal (bez argumentow) — pokaz swoje serca
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Podaj nick gracza: /lifesteal info <gracz>");
                return true;
            }
            int h = ls.getHearts(player.getUniqueId());
            sender.sendMessage(
                ChatColor.RED + "\u2764 Lifesteal" +
                ChatColor.GRAY + " | Twoje serca: " +
                ChatColor.WHITE + h + ChatColor.GRAY + "/" + LifestealManager.MAX_HEARTS +
                "\n" + ls.heartsBar(h)
            );
            return true;
        }

        switch (args[0].toLowerCase()) {

            // ─── /lifesteal info <gracz> ──────────────────────────────────────────
            case "info" -> {
                if (!sender.hasPermission("zamrozenie.use")) { noPerms(sender); return true; }
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Uzycie: /lifesteal info <gracz>"); return true; }
                OfflinePlayer target = resolvePlayer(args[1]);
                if (target == null) { sender.sendMessage(ChatColor.RED + "Nie znaleziono gracza " + args[1] + "."); return true; }
                int h = ls.getHearts(target.getUniqueId());
                sender.sendMessage(
                    ChatColor.WHITE + target.getName() +
                    ChatColor.GRAY + " | Serca: " +
                    ChatColor.WHITE + h + ChatColor.GRAY + "/" + LifestealManager.MAX_HEARTS +
                    "\n" + ls.heartsBar(h)
                );
            }

            // ─── /lifesteal set <gracz> <ilosc>  (także offline) ─────────────────
            case "set" -> {
                if (!sender.hasPermission("zamrozenie.use")) { noPerms(sender); return true; }
                if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Uzycie: /lifesteal set <gracz> <ilosc>"); return true; }
                OfflinePlayer target = resolvePlayer(args[1]);
                if (target == null) { sender.sendMessage(ChatColor.RED + "Nie znaleziono gracza " + args[1] + "."); return true; }
                try {
                    int amount = Integer.parseInt(args[2]);
                    ls.setHearts(target.getUniqueId(), amount);
                    int now = ls.getHearts(target.getUniqueId());
                    String name = target.getName() != null ? target.getName() : args[1];
                    sender.sendMessage(ChatColor.GREEN + "Ustawiono " + now + " serc dla " + name
                            + (target.isOnline() ? "." : " (offline)."));
                    if (target instanceof Player online) {
                        online.sendMessage(ChatColor.YELLOW + "Twoje serca zostaly zmienione na: " + now);
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Podaj poprawna liczbe.");
                }
            }

            case "add" -> changeHearts(sender, args, true);
            case "take", "remove" -> changeHearts(sender, args, false);

            case "reset" -> {
                if (!sender.hasPermission("zamrozenie.use")) { noPerms(sender); return true; }
                if (args.length < 2 || !args[1].equalsIgnoreCase("potwierdz")) {
                    sender.sendMessage(ChatColor.YELLOW + "Reset serc u WSZYSTKICH:");
                    sender.sendMessage(ChatColor.WHITE + "20 → 13" + ChatColor.GRAY + ", "
                            + ChatColor.WHITE + "19 → 12" + ChatColor.GRAY + ", "
                            + ChatColor.WHITE + "18 → 11" + ChatColor.GRAY + ", reszta → 10.");
                    sender.sendMessage(ChatColor.RED + "Żeby wykonać: " + ChatColor.WHITE + "/lifesteal reset potwierdz");
                    return true;
                }
                int[] r = ls.resetSeasonHearts();
                sender.sendMessage(ChatColor.GREEN + "Reset serc zrobiony.");
                sender.sendMessage(ChatColor.GRAY + "20→13: " + ChatColor.WHITE + r[0]
                        + ChatColor.GRAY + "  19→12: " + ChatColor.WHITE + r[1]
                        + ChatColor.GRAY + "  18→11: " + ChatColor.WHITE + r[2]
                        + ChatColor.GRAY + "  reszta→10: " + ChatColor.WHITE + r[3]
                        + ChatColor.GRAY + "  pominięci (0 serc): " + ChatColor.WHITE + r[4]);
                Bukkit.broadcastMessage(ChatColor.RED + "❤ " + ChatColor.YELLOW + "Reset serc na serwerze.");
            }

            // ─── /lifesteal give <gracz> heart [ilosc] ──────────────────────────
            // ─── /lifesteal give <gracz> book <banzgracz> ───────────────────────
            case "give" -> {
                if (!sender.hasPermission("zamrozenie.use")) { noPerms(sender); return true; }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Uzycie: /lifesteal give <gracz> heart|book [cel]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(ChatColor.RED + "Gracz " + args[1] + " nie jest online."); return true; }

                if (args[2].equalsIgnoreCase("heart")) {
                    int amount = args.length >= 4 ? parseInt(args[3], 1) : 1;
                    for (int i = 0; i < amount; i++) {
                        target.getInventory().addItem(ls.createHeartItem());
                    }
                    sender.sendMessage(ChatColor.GREEN + "Dano " + amount + "x serce graczowi " + target.getName() + ".");
                    target.sendMessage(ChatColor.GREEN + "Otrzymales " + amount + "x serce od " + sender.getName() + ".");

                } else if (args[2].equalsIgnoreCase("book")) {
                    target.getInventory().addItem(ls.createResurrectionBook());
                    sender.sendMessage(ChatColor.GREEN + "Dano Ksiege Odrodzenia graczowi " + target.getName() + ".");
                    target.sendMessage(ChatColor.GOLD + "Otrzymales Ksiege Odrodzenia.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Nieznany typ: uzyj 'heart' lub 'book'.");
                }
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    private void changeHearts(CommandSender sender, String[] args, boolean add) {
        if (!sender.hasPermission("zamrozenie.use")) { noPerms(sender); return; }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uzycie: /lifesteal " + (add ? "add" : "take") + " <gracz> [ilosc]");
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED + "Nie znaleziono gracza " + args[1] + "."); return; }
        int delta = args.length >= 3 ? parseInt(args[2], 1) : 1;
        int next = ls.getHearts(target.getUniqueId()) + (add ? delta : -delta);
        ls.setHearts(target.getUniqueId(), next);
        int now = ls.getHearts(target.getUniqueId());
        String name = target.getName() != null ? target.getName() : args[1];
        sender.sendMessage(ChatColor.GREEN + (add ? "Dodano " : "Zabrano ") + delta + " serc "
                + name + ChatColor.GRAY + " → " + ChatColor.WHITE + now
                + (target.isOnline() ? "" : " (offline)"));
        if (target instanceof Player online) {
            online.sendMessage(ChatColor.YELLOW + "Twoje serca zostaly zmienione na: " + now);
        }
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return op;
        }
        return null;
    }

    private void noPerms(CommandSender s)  { s.sendMessage(ChatColor.RED + "Brak uprawnien."); }
    private int  parseInt(String s, int d) { try { return Math.max(1, Integer.parseInt(s)); } catch (NumberFormatException e) { return d; } }

    private void sendHelp(CommandSender s) {
        s.sendMessage(ChatColor.RED + "\u2764 Lifesteal " + ChatColor.GRAY + "— komendy:");
        s.sendMessage(ChatColor.WHITE + "/lifesteal" + ChatColor.GRAY + "                       — twoje serca");
        s.sendMessage(ChatColor.WHITE + "/lifesteal info <gracz>" + ChatColor.GRAY + "          — serca gracza [op]");
        s.sendMessage(ChatColor.WHITE + "/lifesteal set <gracz> <n>" + ChatColor.GRAY + "       — ustaw serca (także offline) [op]");
        s.sendMessage(ChatColor.WHITE + "/lifesteal add <gracz> [n]" + ChatColor.GRAY + "       — dodaj serca [op]");
        s.sendMessage(ChatColor.WHITE + "/lifesteal take <gracz> [n]" + ChatColor.GRAY + "      — zabierz serca [op]");
        s.sendMessage(ChatColor.WHITE + "/lifesteal reset" + ChatColor.GRAY + "                 — reset sezonowy serc [op]");
        s.sendMessage(ChatColor.WHITE + "/lifesteal give <gracz> heart" + ChatColor.GRAY + "    — daj serce [op]");
        s.sendMessage(ChatColor.WHITE + "/lifesteal give <gracz> book" + ChatColor.GRAY + "        — daj Ksiege [op]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return filterPrefix(Arrays.asList("info", "set", "add", "take", "reset", "give"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) return filterPrefix(List.of("potwierdz"), args[1]);
        if (args.length == 2) return knownPlayers(args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return filterPrefix(Arrays.asList("heart", "book"), args[2]);
        if (args.length == 4 && args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("book")) {
            // Podpowiedz zbanowanych graczy
            return Bukkit.getBannedPlayers().stream()
                .map(OfflinePlayer::getName)
                .filter(n -> n != null && n.toLowerCase().startsWith(args[3].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private List<String> knownPlayers(String prefix) {
        String p = prefix.toLowerCase();
        return java.util.Arrays.stream(Bukkit.getOfflinePlayers())
            .map(OfflinePlayer::getName)
            .filter(n -> n != null && n.toLowerCase().startsWith(p))
            .distinct()
            .collect(Collectors.toList());
    }

    private List<String> filterPrefix(List<String> list, String prefix) {
        return list.stream().filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }
}
