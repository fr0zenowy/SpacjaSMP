package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Admin po wstawieniu przedmiotu do /asklep podaje cene na czacie. */
public class ShopChatListener implements Listener {

    private final ShopManager shop;

    public ShopChatListener(ShopManager shop) {
        this.shop = shop;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ShopManager.PricePrompt prompt = shop.peekPrompt(player.getUniqueId());
        if (prompt == null) return;

        // wiadomosc nie trafia na publiczny czat
        event.setCancelled(true);
        String raw = ChatColor.stripColor(event.getMessage()).trim();
        shop.getPlugin().getServer().getScheduler().runTask(shop.getPlugin(),
                () -> finishPrompt(player, prompt, raw));
    }

    private void finishPrompt(Player player, ShopManager.PricePrompt prompt, String raw) {
        ShopManager.PricePrompt current = shop.peekPrompt(player.getUniqueId());
        if (current == null || current != prompt) return;
        if (!player.isOnline()) return;
        shop.takePrompt(player.getUniqueId());

        if (raw.equalsIgnoreCase("anuluj") || raw.equalsIgnoreCase("cancel")) {
            shop.giveBack(player, prompt.item);
            player.sendMessage(ChatColor.GRAY + "Anulowano ustawianie ceny.");
            shop.openCatalog(player, prompt.sell, true);
            return;
        }

        Double price = ShopManager.parsePrice(raw);
        if (price == null) {
            shop.giveBack(player, prompt.item);
            player.sendMessage(ChatColor.RED + "Niepoprawna cena. Przedmiot nie zosta\u0142 dodany.");
            shop.openCatalog(player, prompt.sell, true);
            return;
        }

        shop.setOffer(prompt.sell, prompt.slot, prompt.item, price);
        player.sendMessage(ChatColor.GREEN + "Ustawiono cen\u0119: " + ShopManager.money(price));
        shop.openCatalog(player, prompt.sell, true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ShopManager.PricePrompt prompt = shop.takePrompt(event.getPlayer().getUniqueId());
        if (prompt == null) return;
        shop.giveBack(event.getPlayer(), prompt.item);
    }
}
