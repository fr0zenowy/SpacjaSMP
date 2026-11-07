package pl.zamrozenie.clans.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import pl.zamrozenie.clans.managers.ClanManager;
import pl.zamrozenie.clans.managers.DataManager;
import pl.zamrozenie.clans.models.Clan;
import pl.zamrozenie.clans.models.ClanMember;
import pl.zamrozenie.clans.utils.MessageUtil;

/**
 * Chat klanowy oraz tag klanu (w wybranym kolorze) na czacie globalnym.
 */
public class ChatListener implements Listener {

    private final ClanManager clanManager;
    private final DataManager dataManager;

    public ChatListener(ClanManager clanManager, DataManager dataManager) {
        this.clanManager = clanManager;
        this.dataManager = dataManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());

        if (dataManager.isClanChatEnabled(player.getUniqueId())) {
            if (clan == null) {
                dataManager.setClanChatEnabled(player.getUniqueId(), false);
                return;
            }
            event.setCancelled(true);
            ClanMember member = clan.getMember(player.getUniqueId());
            String rank = member != null ? member.getRank().getDisplayName() : "CZLONEK";
            String message = MessageUtil.formatClanTag(clan) + ChatColor.DARK_GRAY + " ["
                    + ChatColor.GRAY + rank + ChatColor.DARK_GRAY + "] "
                    + ChatColor.WHITE + player.getName()
                    + ChatColor.DARK_GRAY + " » " + ChatColor.GRAY + event.getMessage();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (clan.hasMember(online.getUniqueId())) {
                    online.sendMessage(message);
                }
            }
            return;
        }

        if (clan != null) {
            String name = player.getName().replace("%", "%%");
            event.setFormat(MessageUtil.formatClanTag(clan) + " " + ChatColor.WHITE + name
                    + ChatColor.RESET + ": %2$s");
        }
    }
}
