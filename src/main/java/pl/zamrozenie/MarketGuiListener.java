package pl.zamrozenie;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;

public class MarketGuiListener implements Listener {

    private final MarketManager market;

    public MarketGuiListener(MarketManager market) {
        this.market = market;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MarketGuiHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);

        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getInventory().getSize()) return;

        String id = market.buttonId(event.getCurrentItem());
        if (id == null) return;
        click(player);

        switch (id) {
            case MarketManager.ID_CLOSE -> {
                if (holder.getMode() == MarketGuiHolder.Mode.CONFIRM) {
                    reopenBrowse(player, holder);
                } else {
                    player.closeInventory();
                }
            }
            case MarketManager.ID_BACK -> reopenBrowse(player, holder);
            case MarketManager.ID_BAG -> {
                if (holder.getMode() == MarketGuiHolder.Mode.BROWSE) {
                    market.openMine(player, holder.getPage(), holder.getQuery(), holder.getCategory(), holder.getSort());
                }
            }
            case MarketManager.ID_SEARCH -> {
                if (holder.getMode() == MarketGuiHolder.Mode.BROWSE) {
                    market.startSignSearch(player, holder.getCategory(), holder.getSort());
                }
            }
            case MarketManager.ID_SORT -> {
                if (holder.getMode() == MarketGuiHolder.Mode.BROWSE) {
                    market.openBrowse(player, 0, holder.getQuery(), holder.getCategory(), holder.getSort().next());
                }
            }
            case MarketManager.ID_CAT_TOOLS -> toggleCategory(player, holder, MarketCategory.TOOLS);
            case MarketManager.ID_CAT_FOOD -> toggleCategory(player, holder, MarketCategory.FOOD);
            case MarketManager.ID_CAT_USEFUL -> toggleCategory(player, holder, MarketCategory.USEFUL);
            case MarketManager.ID_PREV -> {
                if (holder.getMode() == MarketGuiHolder.Mode.BROWSE && holder.getPage() > 0) {
                    market.openBrowse(player, holder.getPage() - 1, holder.getQuery(), holder.getCategory(), holder.getSort());
                }
            }
            case MarketManager.ID_NEXT -> {
                if (holder.getMode() == MarketGuiHolder.Mode.BROWSE) {
                    market.openBrowse(player, holder.getPage() + 1, holder.getQuery(), holder.getCategory(), holder.getSort());
                }
            }
            case MarketManager.ID_LISTING -> {
                UUID listing = market.listingId(event.getCurrentItem());
                if (listing != null) {
                    market.openConfirm(player, listing, holder.getPage(), holder.getQuery(), holder.getCategory(), holder.getSort());
                }
            }
            case MarketManager.ID_BUY -> {
                UUID listing = holder.getListingId();
                if (listing != null) {
                    market.buy(player, listing, holder.getPage(), holder.getQuery(), holder.getCategory(), holder.getSort());
                }
            }
            case MarketManager.ID_OWN -> {
                UUID listing = market.listingId(event.getCurrentItem());
                if (listing != null) {
                    market.withdraw(player, listing, holder.getPage(), holder.getQuery(), holder.getCategory(), holder.getSort());
                }
            }
            default -> { }
        }
    }

    private void reopenBrowse(Player player, MarketGuiHolder holder) {
        market.openBrowse(player, holder.getPage(), holder.getQuery(), holder.getCategory(), holder.getSort());
    }

    private void toggleCategory(Player player, MarketGuiHolder holder, MarketCategory category) {
        if (holder.getMode() != MarketGuiHolder.Mode.BROWSE) return;
        MarketCategory next = holder.getCategory() == category ? null : category;
        market.openBrowse(player, 0, holder.getQuery(), next, holder.getSort());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MarketGuiHolder) event.setCancelled(true);
    }

    private void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }
}
