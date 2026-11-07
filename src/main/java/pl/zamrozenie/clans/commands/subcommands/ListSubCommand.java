package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.entity.Player;

import java.util.*;

public class ListSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    private static final int CLANS_PER_PAGE = 10;
    
    public ListSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @Override
    public String getName() {
        return "lista";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("list", "l");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        Collection<Clan> allClans = clanManager.getAllClans();
        
        if (allClans.isEmpty()) {
            player.sendMessage(MessageUtil.error("Brak klanów na serwerze!"));
            return;
        }
        
        // Pobierz numer strony
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(MessageUtil.error("Nieprawidłowy numer strony!"));
                return;
            }
        }
        
        // Sortuj klany po liczbie członków (malejąco)
        List<Clan> sortedClans = new ArrayList<>(allClans);
        sortedClans.sort((c1, c2) -> Integer.compare(c2.getMemberCount(), c1.getMemberCount()));
        
        // Oblicz paginację
        int totalPages = (int) Math.ceil((double) sortedClans.size() / CLANS_PER_PAGE);
        if (page < 1 || page > totalPages) {
            player.sendMessage(MessageUtil.error("Nieprawidłowy numer strony! (1-" + totalPages + ")"));
            return;
        }
        
        int startIndex = (page - 1) * CLANS_PER_PAGE;
        int endIndex = Math.min(startIndex + CLANS_PER_PAGE, sortedClans.size());
        
        // Wyświetl listę
        player.sendMessage(MessageUtil.shortDivider());
        player.sendMessage(MessageUtil.format(""));
        
        for (int i = startIndex; i < endIndex; i++) {
            Clan clan = sortedClans.get(i);
            int rank = i + 1;
            player.sendMessage(MessageUtil.format("&8[" + rank + "] " + 
                MessageUtil.formatClanTag(clan) + " &7" + clan.getName() + 
                " &8- &f" + clan.getMemberCount() + " &7członków"));
        }
        
        player.sendMessage(MessageUtil.format(""));
        player.sendMessage(MessageUtil.format("&7Strona: &f" + page + "&7/&f" + totalPages));
        player.sendMessage(MessageUtil.format("&7Kliknij aby zobaczyć szczegóły: &f/klan info <tag>"));
        player.sendMessage(MessageUtil.format(""));
        player.sendMessage(MessageUtil.divider());
    }
    
    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            return List.of("<strona>");
        }
        return Collections.emptyList();
    }
}