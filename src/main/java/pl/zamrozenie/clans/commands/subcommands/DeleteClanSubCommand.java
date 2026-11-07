package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class DeleteClanSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    private final Map<UUID, Long> confirmations = new HashMap<>();
    private static final long CONFIRMATION_TIMEOUT = 10_000L; // 10 sekund
    
    public DeleteClanSubCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }
    
    @Override
    public String getName() {
        return "rozwiaz";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("delete", "usun");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        
        // Sprawdź czy wymaga potwierdzenia
        Long confirmTime = confirmations.get(player.getUniqueId());
        if (confirmTime == null || System.currentTimeMillis() - confirmTime > CONFIRMATION_TIMEOUT) {
            // Pierwsza próba - wyślij prośbę o potwierdzenie
            confirmations.put(player.getUniqueId(), System.currentTimeMillis());
            
            player.sendMessage(MessageUtil.warning("Czy na pewno chcesz rozwiązać klan " + 
                MessageUtil.formatClanTag(clan.getTag()) + "&7?"));
            player.sendMessage(MessageUtil.format("&7Wszystkie dane zostaną &cusunięte&7!"));
            player.sendMessage(MessageUtil.format("&7Wpisz &f/klan rozwiaz &7ponownie aby potwierdzić."));
            return;
        }
        
        // Druga próba - usuń klan
        confirmations.remove(player.getUniqueId());
        
        String error = clanManager.deleteClan(player);
        
        if (error != null) {
            player.sendMessage(error);
        } else {
            // Powiadom wszystkich członków
            for (Player member : Bukkit.getOnlinePlayers()) {
                if (clan.hasMember(member.getUniqueId())) {
                    member.sendTitle(
                        MessageUtil.format("&8[&c✘&8]"),
                        MessageUtil.format("&cKlan został rozwiązany!"),
                        10, 60, 20
                    );
                    member.sendMessage(MessageUtil.error("Klan " + MessageUtil.formatClanTag(clan.getTag()) + 
                        " &czostał rozwiązany przez lidera!"));
                }
            }
        }
    }
}