package pl.zamrozenie.clans.commands;

import org.bukkit.entity.Player;
import java.util.List;
import java.util.Collections;

/**
 * Interfejs dla wszystkich subkomend /klan
 */
public interface SubCommand {
    
    /**
     * Nazwa subkomendy (np. "utworz", "info")
     */
    String getName();
    
    /**
     * Aliasy subkomendy
     */
    default List<String> getAliases() {
        return Collections.emptyList();
    }
    
    /**
     * Wykonuje subkomendę
     * @param player Gracz wykonujący komendę
     * @param args Argumenty komendy (bez nazwy subkomendy)
     */
    void execute(Player player, String[] args);
    
    /**
     * Tab completion dla subkomendy
     * @param player Gracz
     * @param args Argumenty
     * @return Lista sugestii
     */
    default List<String> tabComplete(Player player, String[] args) {
        return Collections.emptyList();
    }
}