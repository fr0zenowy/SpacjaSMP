package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WyplacSerceCommand implements CommandExecutor {

    private static final int MIN_HEARTS_TO_WITHDRAW = 11;

    private final LifestealManager ls;

    public WyplacSerceCommand(LifestealManager ls) {
        this.ls = ls;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Tylko gracz moze uzyc tej komendy.");
            return true;
        }

        int hearts = ls.getHearts(player.getUniqueId());

        if (hearts < MIN_HEARTS_TO_WITHDRAW) {
            player.sendMessage(
                ChatColor.RED + "" + ChatColor.BOLD + "\u2715 " + ChatColor.RED +
                "Nie mozesz wyplacic serca! Potrzebujesz co najmniej " +
                ChatColor.WHITE + MIN_HEARTS_TO_WITHDRAW + ChatColor.RED +
                " serc, a masz tylko " + ChatColor.WHITE + hearts + ChatColor.RED + "."
            );
            return true;
        }

        // Jesli jest juz oczekujace potwierdzenie — wykonaj wyplate
        if (ls.hasPendingWithdraw(player.getUniqueId())) {
            ls.clearPendingWithdraw(player.getUniqueId());

            // Odejmij serce i dodaj do EQ
            ls.removeHeart(player.getUniqueId());
            ls.applyHearts(player);

            ItemStack heartItem = ls.createHeartItem();
            player.getInventory().addItem(heartItem);

            int newHearts = ls.getHearts(player.getUniqueId());
            player.sendMessage(
                ChatColor.GREEN + "" + ChatColor.BOLD + "\u2764 " +
                ChatColor.GREEN + "Serce zostalo wyplacone! Masz teraz: " +
                ChatColor.WHITE + newHearts + ChatColor.GREEN + "/" + LifestealManager.MAX_HEARTS +
                " serc.  " + ls.heartsBar(newHearts)
            );
            return true;
        }

        // Pierwsze uzycie — pokaz ostrzezenie
        ls.setPendingWithdraw(player.getUniqueId());

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "  \u26a0  OSTRZEZENIE!  \u26a0");
        player.sendMessage(ChatColor.YELLOW + "Pamietaj, ze wyplacone serce mozna uzyc tylko raz.");
        if (hearts > 10) {
            player.sendMessage(ChatColor.YELLOW + "Jezeli masz wiecej niz 10 serc, nie bedziesz mogl");
            player.sendMessage(ChatColor.YELLOW + "wlozyc go z powrotem do serc (nie mozesz uzywac");
            player.sendMessage(ChatColor.YELLOW + "serduszek powyzej 10 serc z EQ).");
        }
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "Aktualna liczba serc: " +
            ChatColor.RED + hearts + ChatColor.WHITE + "/" + LifestealManager.MAX_HEARTS);
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "Aby potwierdzic wyplate, wpisz " +
            ChatColor.WHITE + "/wyplacserce" +
            ChatColor.AQUA + " ponownie w ciagu " +
            ChatColor.WHITE + "30 sekund" + ChatColor.AQUA + ".");
        player.sendMessage("");

        return true;
    }
}
