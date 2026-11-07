package pl.zamrozenie.clans.commands;

import pl.zamrozenie.clans.commands.subcommands.*;
import pl.zamrozenie.clans.managers.*;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Główna komenda /klan z systemem subkomend
 */
public class ClanCommand implements CommandExecutor, TabCompleter {
    
    private final ClanManager clanManager;
    private final DataManager dataManager;
    private final Map<String, SubCommand> subCommands;
    
    public ClanCommand(ClanManager clanManager, DataManager dataManager) {
        this.clanManager = clanManager;
        this.dataManager = dataManager;
        this.subCommands = new HashMap<>();
        
        registerSubCommands();
    }
    
    private void registerSubCommands() {
        // Podstawowe komendy
        registerSubCommand(new CreateClanSubCommand(clanManager));
        registerSubCommand(new DeleteClanSubCommand(clanManager));
        registerSubCommand(new InfoSubCommand(clanManager));
        registerSubCommand(new ListSubCommand(clanManager));
        registerSubCommand(new ChatSubCommand(clanManager, dataManager));
        registerSubCommand(new ColorSubCommand(clanManager));
        
        // Zarządzanie członkami
        registerSubCommand(new InviteSubCommand(clanManager));
        registerSubCommand(new AcceptInviteSubCommand(clanManager));
        registerSubCommand(new DenyInviteSubCommand(clanManager));
        registerSubCommand(new KickSubCommand(clanManager));
        registerSubCommand(new LeaveSubCommand(clanManager));
        registerSubCommand(new PromoteSubCommand(clanManager));
        registerSubCommand(new DemoteSubCommand(clanManager));
        
        // Home
        registerSubCommand(new SetHomeSubCommand(clanManager));
        registerSubCommand(new HomeSubCommand(clanManager));
    }
    
    private void registerSubCommand(SubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(), subCommand);
        // Dodaj aliasy
        for (String alias : subCommand.getAliases()) {
            subCommands.put(alias.toLowerCase(), subCommand);
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Ta komenda jest dostępna tylko dla graczy!"));
            return true;
        }
        
        // Bez argumentów - pokazuje pomoc lub otwiera GUI
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        // Znajdź i wykonaj subkomendę
        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);
        
        if (subCommand == null) {
            player.sendMessage(MessageUtil.error("Nieznana komenda! Użyj &f/klan &caby zobaczyć listę komend."));
            return true;
        }
        
        // Pobierz argumenty bez pierwszego (nazwa subkomendy)
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        
        // Wykonaj subkomendę
        subCommand.execute(player, subArgs);
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        
        List<String> completions = new ArrayList<>();
        
        // Pierwsz argument - sugestie subkomend
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String subCmdName : subCommands.keySet()) {
                if (subCmdName.startsWith(partial)) {
                    completions.add(subCmdName);
                }
            }
        }
        // Dalsze argumenty - deleguj do subkomendy
        else if (args.length > 1) {
            SubCommand subCommand = subCommands.get(args[0].toLowerCase());
            if (subCommand != null) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCommand.tabComplete((Player) sender, subArgs);
            }
        }
        
        Collections.sort(completions);
        return completions;
    }
    
    private void showHelp(Player player) {
        player.sendMessage(MessageUtil.divider());
        player.sendMessage(MessageUtil.format(""));
        player.sendMessage(MessageUtil.format("&3&lSystem Klanów"));
        player.sendMessage(MessageUtil.format(""));
        player.sendMessage(MessageUtil.format("&8» &7/klan &futworz <tag> <nazwa> &8- &7Tworzy nowy klan"));
        player.sendMessage(MessageUtil.format("&8» &7/klan &frozwiaz &8- &7Rozwiązuje klan"));
        player.sendMessage(MessageUtil.format("&8» &7/klan &finfo [tag] &8- &7Informacje o klanie"));
        player.sendMessage(MessageUtil.format("&8» &7/klan &flista &8- &7Lista wszystkich klanów"));
        player.sendMessage(MessageUtil.format("&8» &7/klan &fzapros <gracz> &8- &7Zaprasza gracza"));
        player.sendMessage(MessageUtil.format("&8» &7/klan &fwyrzuc <gracz> &8- &7Wyrzuca gracza"));
        player.sendMessage(MessageUtil.format("&8» &7/klan &fopusc &8- &7Opuszcza klan"));
        player.sendMessage(MessageUtil.format("&8» &7/klan &fawansuj <gracz> &8- &7Awansuje na admina"));
        player.sendMessage(MessageUtil.format("&8» &7/klan &fzdegraduj <gracz> &8- &7Degraduje admina"));
        player.sendMessage(MessageUtil.format("&8» &7/klan &fchat &8- &7Przełącza chat klanowy"));
        player.sendMessage(MessageUtil.format("&8» &7/klan &fkolor [nazwa] &8- &7Kolor tagu na czacie, tabie i przy nicku"));
        player.sendMessage(MessageUtil.format("&8» &7/klan &fsethome &8- &7Ustawia home klanu"));
        player.sendMessage(MessageUtil.format("&8» &7/klan &fhome &8- &7Teleportuje do home"));
        player.sendMessage(MessageUtil.format("&8» &7Limit klanu: &f4 osoby"));
        player.sendMessage(MessageUtil.format(""));
        player.sendMessage(MessageUtil.divider());
    }
}