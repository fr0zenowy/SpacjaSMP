package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.managers.DataManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class ChatSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    private final DataManager dataManager;
    
    public ChatSubCommand(ClanManager clanManager, DataManager dataManager) {
        this.clanManager = clanManager;
        this.dataManager = dataManager;
    }
    
    @Override
    public String getName() {
        return "chat";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("cc", "c");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        
        if (clan == null) {
            player.sendMessage(MessageUtil.error("Nie należysz do żadnego klanu!"));
            return;
        }
        
        // Toggle clan chat
        boolean currentState = dataManager.isClanChatEnabled(player.getUniqueId());
        dataManager.setClanChatEnabled(player.getUniqueId(), !currentState);
        
        if (!currentState) {
            player.sendMessage(MessageUtil.success("Czat klanowy &fwłączony&a!"));
            player.sendMessage(MessageUtil.format("&7Wszystkie twoje wiadomości będą widoczne tylko dla klanu."));
        } else {
            player.sendMessage(MessageUtil.success("Czat klanowy &fwyłączony&a!"));
            player.sendMessage(MessageUtil.format("&7Twoje wiadomości są teraz widoczne dla wszystkich."));
        }
    }
}