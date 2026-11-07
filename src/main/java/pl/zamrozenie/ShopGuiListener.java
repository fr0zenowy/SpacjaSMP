package pl.zamrozenie;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public class ShopGuiListener implements Listener {

    private final ShopManager shop;

    public ShopGuiListener(ShopManager shop) {
        this.shop = shop;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopGuiHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int raw = event.getRawSlot();
        boolean top = raw >= 0 && raw < event.getInventory().getSize();

        if (holder.getMode() == ShopGuiHolder.Mode.MAIN) {
            event.setCancelled(true);
            if (!top) return;
            String id = shop.buttonId(event.getCurrentItem());
            if (id == null) return;
            click(player);
            if (ShopManager.ID_BUY.equals(id)) shop.openCatalog(player, false, false);
            else if (ShopManager.ID_SELL.equals(id)) shop.openCatalog(player, true, false);
            return;
        }

        if (holder.getMode() == ShopGuiHolder.Mode.CONFIRM) {
            event.setCancelled(true);
            if (!top) return;
            handleConfirm(player, holder, shop.buttonId(event.getCurrentItem()), raw, event.isShiftClick());
            return;
        }

        if (holder.isAdmin()) {
            handleAdmin(event, player, holder, raw, top);
            return;
        }

        event.setCancelled(true);
        if (!top) return;
        handlePlayerCatalog(player, holder, event, raw);
    }

    private void handlePlayerCatalog(Player player, ShopGuiHolder holder, InventoryClickEvent event, int slot) {
        String id = shop.buttonId(event.getCurrentItem());
        if (ShopManager.ID_CLOSE.equals(id)) {
            click(player);
            player.closeInventory();
            return;
        }
        if (ShopManager.ID_BANK.equals(id)) {
            click(player);
            return;
        }
        if (!ShopManager.ID_OFFER.equals(id)) return;

        ShopOffer offer = shop.offerAt(holder.isSellCatalog(), slot);
        if (offer == null) return;
        click(player);

        if (holder.isSellCatalog()) {
            if (event.getClick() == ClickType.RIGHT) {
                shop.sell(player, offer, ShopManager.SellMode.STACK);
            } else if (event.getClick() == ClickType.SHIFT_RIGHT) {
                shop.sell(player, offer, ShopManager.SellMode.ALL);
            }
            return;
        }
        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT) {
            shop.openConfirm(player, offer);
        }
    }

    private void handleConfirm(Player player, ShopGuiHolder holder, String id, int slot, boolean shift) {
        if (id == null) {
            if (slot == ShopGuiHolder.CONFIRM_PREVIEW) click(player);
            return;
        }
        click(player);
        int step = shift ? 64 : 16;
        switch (id) {
            case ShopManager.ID_CLOSE -> shop.openCatalog(player, false, false);
            case ShopManager.ID_ADD -> {
                holder.setConfirmQty(Math.min(2304, holder.getConfirmQty() + step));
                shop.refreshConfirm(holder);
            }
            case ShopManager.ID_SUB -> {
                holder.setConfirmQty(Math.max(1, holder.getConfirmQty() - step));
                shop.refreshConfirm(holder);
            }
            case ShopManager.ID_CONFIRM -> shop.buy(player, holder.getConfirmBase(), holder.getConfirmPrice(),
                    holder.getConfirmQty(), holder.getConfirmSlot());
            default -> { }
        }
    }

    private void handleAdmin(InventoryClickEvent event, Player player, ShopGuiHolder holder, int raw, boolean top) {
        if (!top) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        String id = shop.buttonId(event.getCurrentItem());
        if (ShopManager.ID_CLOSE.equals(id)) {
            click(player);
            player.closeInventory();
            return;
        }
        if (ShopManager.ID_BANK.equals(id)) {
            click(player);
            return;
        }
        if (ShopGuiHolder.isListChrome(raw)) return;

        ItemStack cursor = event.getCursor();
        boolean hasCursor = cursor != null && cursor.getType() != Material.AIR;
        ShopOffer existing = shop.offerAt(holder.isSellCatalog(), raw);

        if (hasCursor) {
            ItemStack one = cursor.clone();
            one.setAmount(1);
            if (cursor.getAmount() <= 1) event.getView().setCursor(null);
            else {
                cursor.setAmount(cursor.getAmount() - 1);
                event.getView().setCursor(cursor);
            }
            if (existing != null) give(player, existing.item);
            click(player);
            shop.startPricePrompt(player, holder.isSellCatalog(), raw, one);
            return;
        }

        if (existing != null) {
            shop.removeOffer(holder.isSellCatalog(), raw);
            event.getView().setCursor(existing.item.clone());
            event.getInventory().setItem(raw, null);
            click(player);
        }
    }

    private void give(Player player, ItemStack item) {
        player.getInventory().addItem(item.clone()).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopGuiHolder holder)) return;
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
        if (holder.isAdmin()) {
            // dno ekwipunku gracza — OK
        }
    }
}
