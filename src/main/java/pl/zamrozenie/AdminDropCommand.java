package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AdminDropCommand implements CommandExecutor, TabCompleter {

    private static final List<String> BOSSES = List.of(
            "wladca endu",
            "jednoglowy cerber",
            "wladca lochow",
            "zrzut"
    );

    private final DropManager dropManager;

    public AdminDropCommand(DropManager dropManager) {
        this.dropManager = dropManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zamrozenie.adrop")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnien. Potrzebne: zamrozenie.adrop");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Tylko gracz moze edytowac drop w GUI.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Uzycie: /adrop <boss>");
            sender.sendMessage(ChatColor.YELLOW + "Albo: /adrop eventowki <boss>");
            sender.sendMessage(ChatColor.GRAY + "Bossy: wladca endu, jednoglowy cerber, wladca lochow, zrzut");
            sender.sendMessage(ChatColor.DARK_GRAY + "Zrzut: tylko zwykły drop (/adrop zrzut), bez eventówek.");
            return true;
        }

        boolean event = args[0].equalsIgnoreCase("eventowki") || args[0].equalsIgnoreCase("eventówki");
        String bossRaw = event
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : String.join(" ", args);
        if (event && bossRaw.isBlank()) {
            sender.sendMessage(ChatColor.YELLOW + "Uzycie: /adrop eventowki <boss>");
            sender.sendMessage(ChatColor.GRAY + "Bossy: wladca endu, jednoglowy cerber, wladca lochow, zrzut");
            sender.sendMessage(ChatColor.DARK_GRAY + "Zrzut: tylko zwykły drop (/adrop zrzut), bez eventówek.");
            return true;
        }

        DropSource src = DropSource.fromInput(bossRaw);
        if (src == null) {
            sender.sendMessage(ChatColor.RED + "Nie znam takiego bossa: " + bossRaw);
            sender.sendMessage(ChatColor.GRAY + "Dostepne: wladca endu, jednoglowy cerber, wladca lochow, zrzut");
            return true;
        }

        if (event) {
            if (src == DropSource.ZRZUT) {
                player.sendMessage(ChatColor.RED + "Zrzut nie ma eventówek. Ustaw zwykły drop: /adrop zrzut");
                return true;
            }
            dropManager.openEditEvent(player, src);
            player.sendMessage(ChatColor.GREEN + "Edytujesz eventówki ze " + src.getDisplayGenitive()
                    + ChatColor.GRAY + " — 1 item = 100%, dwa w slocie = 50/50 (dropnie tylko jeden).");
            player.sendMessage(ChatColor.GRAY + "Włóż item z kursora, kliknij pustą ręką żeby wyjąć.");
            return true;
        }

        dropManager.openEdit(player, src);
        player.sendMessage(ChatColor.GREEN + "Edytujesz drop ze " + src.getDisplayGenitive()
                + ChatColor.GRAY + " — włóż itemy i zamknij GUI, żeby zapisać.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length >= 1 && (args[0].equalsIgnoreCase("eventowki") || args[0].equalsIgnoreCase("eventówki"))) {
            String prefix = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                    .toLowerCase(Locale.ROOT);
            return BOSSES.stream().filter(o -> o.startsWith(prefix)).collect(Collectors.toCollection(ArrayList::new));
        }
        List<String> opts = new ArrayList<>(BOSSES);
        opts.add("eventowki");
        String prefix = String.join(" ", args).toLowerCase(Locale.ROOT);
        return opts.stream().filter(o -> o.startsWith(prefix)).collect(Collectors.toCollection(ArrayList::new));
    }
}
