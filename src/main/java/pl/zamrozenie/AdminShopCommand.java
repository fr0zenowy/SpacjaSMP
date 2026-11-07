package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AdminShopCommand implements CommandExecutor, TabCompleter {

    private final ShopManager shop;

    public AdminShopCommand(ShopManager shop) {
        this.shop = shop;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zamrozenie.asklep")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnien. Potrzebne: zamrozenie.asklep");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Uzycie: /asklep <kupno|sprzedaz|daj|ustaw|reset>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reset") || sub.equals("resetuj") || sub.equals("resetcen")) {
            int n = shop.resetSellPrices();
            sender.sendMessage(ChatColor.GREEN + "Zresetowano ceny sprzedazy do bazowych"
                    + ChatColor.GRAY + " (" + n + " itemow). "
                    + ChatColor.GREEN + "Kupno i tak ma stala cene.");
            return true;
        }
        if (sub.equals("ustaw") || sub.equals("set")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.YELLOW + "Uzycie: /asklep ustaw <nick> <ilosc>");
                return true;
            }
            OfflinePlayer target = findPlayer(args[1]);
            if (target == null || target.getUniqueId() == null
                    || (target.getName() == null && !target.hasPlayedBefore())) {
                sender.sendMessage(ChatColor.RED + "Nie znaleziono gracza.");
                return true;
            }
            Double amount = parseSetAmount(args[2]);
            if (amount == null) {
                sender.sendMessage(ChatColor.RED + "Niepoprawna ilosc.");
                return true;
            }
            UUID id = target.getUniqueId();
            shop.getEconomy().set(id, amount);
            String name = target.getName() != null ? target.getName() : args[1];
            sender.sendMessage(ChatColor.GREEN + "Ustawiono " + ShopManager.money(amount)
                    + ChatColor.GREEN + " dla " + ChatColor.WHITE + name);
            Player online = target.getPlayer();
            if (online != null) {
                online.sendMessage(ChatColor.GREEN + "Twoje saldo CN ustawiono na " + ShopManager.money(amount));
            }
            return true;
        }
        if (sub.equals("daj") || sub.equals("give")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.YELLOW + "Uzycie: /asklep daj <gracz> <kwota>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Gracz offline.");
                return true;
            }
            Double amount = ShopManager.parsePrice(args[2]);
            if (amount == null) {
                sender.sendMessage(ChatColor.RED + "Niepoprawna kwota.");
                return true;
            }
            shop.getEconomy().add(target.getUniqueId(), amount);
            sender.sendMessage(ChatColor.GREEN + "Dodano " + ShopManager.money(amount)
                    + ChatColor.GREEN + " dla " + ChatColor.WHITE + target.getName());
            target.sendMessage(ChatColor.GREEN + "Otrzymano " + ShopManager.money(amount));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Tylko gracz moze edytowac sklep w GUI.");
            return true;
        }
        if (sub.equals("kupno") || sub.equals("buy") || sub.equals("kup")) {
            shop.openCatalog(player, false, true);
            player.sendMessage(ChatColor.GREEN + "Edytujesz kupno" + ChatColor.GRAY + " — wloz item i wpisz cene na czacie.");
            return true;
        }
        if (sub.equals("sprzedaz") || sub.equals("sprzeda\u017C") || sub.equals("sell") || sub.startsWith("sprzed")) {
            shop.openCatalog(player, true, true);
            player.sendMessage(ChatColor.GREEN + "Edytujesz sprzedaz" + ChatColor.GRAY + " — wloz item i wpisz cene na czacie.");
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Uzycie: /asklep <kupno|sprzedaz|daj|ustaw|reset>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[]{"kupno", "sprzedaz", "daj", "ustaw", "reset"}) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2
                && (args[0].equalsIgnoreCase("daj") || args[0].equalsIgnoreCase("ustaw"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }

    private static OfflinePlayer findPlayer(String nick) {
        Player online = Bukkit.getPlayerExact(nick);
        if (online != null) return online;
        for (OfflinePlayer off : Bukkit.getOfflinePlayers()) {
            if (off.getName() != null && off.getName().equalsIgnoreCase(nick)) return off;
        }
        return null;
    }

    private static Double parseSetAmount(String raw) {
        try {
            double v = Double.parseDouble(raw.trim().replace(',', '.'));
            if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return ShopManager.roundMoney(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
