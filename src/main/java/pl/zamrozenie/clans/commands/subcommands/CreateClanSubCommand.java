package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class CreateClanSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    
    public CreateClanSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @Override
    public String getName() {
        return "utworz";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("create", "stworz");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.error("Użycie: &f/klan utworz <tag> <nazwa>"));
            return;
        }
        
        String tag = args[0];
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        
        String error = clanManager.createClan(player, tag, name);
        
        if (error != null) {
            player.sendMessage(error);
        } else {
            player.sendTitle(
                MessageUtil.format("&8[&a✔&8]"),
                MessageUtil.format("&aUtworzono klan &f[&3" + tag + "&f] &a" + name),
                10, 60, 20
            );
            player.sendMessage(MessageUtil.success("Utworzono klan " + MessageUtil.formatClanTag(tag) + " &a" + name + "&a!"));
            player.sendMessage(MessageUtil.info("Kolor tagu zmienisz przez &f/klan kolor"));
        }
    }
    
    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            return List.of("<tag>");
        } else if (args.length == 2) {
            return List.of("<nazwa>");
        }
        return Collections.emptyList();
    }
}