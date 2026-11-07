package pl.zamrozenie.clans.commands.subcommands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.utils.ClanColors;
import pl.zamrozenie.clans.utils.MessageUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ColorSubCommand implements SubCommand {

    private final ClanManager clanManager;

    public ColorSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    @Override
    public String getName() {
        return "kolor";
    }

    @Override
    public List<String> getAliases() {
        return List.of("color", "kolory");
    }

    @Override
    public void execute(Player player, String[] args) {
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(MessageUtil.error("Nie należysz do żadnego klanu!"));
            return;
        }

        if (args.length == 0) {
            showColors(player, clan);
            return;
        }

        ChatColor color = ClanColors.fromInput(args[0]);
        if (color == null) {
            player.sendMessage(MessageUtil.error("Nieznany kolor. Użyj &f/klan kolor &caby zobaczyć listę."));
            return;
        }

        String error = clanManager.setClanColor(player, color);
        if (error != null) {
            player.sendMessage(error);
            return;
        }

        player.sendMessage(MessageUtil.success("Ustawiono kolor tagu: "
                + color + ClanColors.displayName(color)
                + ChatColor.GREEN + ". Tag: " + MessageUtil.formatClanTag(clan)));
    }

    private void showColors(Player player, Clan clan) {
        player.sendMessage(MessageUtil.shortDivider());
        player.sendMessage(MessageUtil.format("&7Aktualny kolor: "
                + clan.getColor() + ClanColors.displayName(clan.getColor())
                + " &8- " + MessageUtil.formatClanTag(clan)));
        player.sendMessage(MessageUtil.format("&7Wybierz: &f/klan kolor <nazwa>"));
        player.sendMessage("");
        StringBuilder line = new StringBuilder();
        int i = 0;
        for (Map.Entry<ChatColor, String> e : ClanColors.displayMap().entrySet()) {
            if (i > 0 && i % 4 == 0) {
                player.sendMessage(MessageUtil.format(line.toString()));
                line = new StringBuilder();
            }
            if (line.length() > 0) line.append("  ");
            line.append(e.getKey()).append(e.getValue());
            i++;
        }
        if (line.length() > 0) player.sendMessage(MessageUtil.format(line.toString()));
        player.sendMessage(MessageUtil.divider());
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length != 1) return List.of();
        String partial = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : ClanColors.suggestions()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(partial)) out.add(name);
        }
        return out;
    }
}
