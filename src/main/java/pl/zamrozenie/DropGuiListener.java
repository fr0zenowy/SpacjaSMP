package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DropGuiListener implements Listener {

    private final DropManager dropManager;
    private final Set<UUID> switching = new HashSet<>();

    public DropGuiListener(DropManager dropManager) {
        this.dropManager = dropManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DropGuiHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int raw = event.getRawSlot();
        boolean top = raw >= 0 && raw < event.getInventory().getSize();

        if (holder.getMode() == DropGuiHolder.Mode.EDIT_EVENT) {
            if (!top) {
                if (event.isShiftClick() || event.getClick() == ClickType.DOUBLE_CLICK) {
                    event.setCancelled(true);
                }
                return;
            }
            event.setCancelled(true);
            if (DropGuiHolder.isDecorSlot(raw)) {
                handleNav(player, holder, event.getCurrentItem());
                return;
            }
            if (!DropGuiHolder.isContentSlot(raw) || holder.getSource() == null) return;
            handleEventSlot(player, holder, event, raw);
            clickSound(player);
            return;
        }

        if (holder.getMode() == DropGuiHolder.Mode.EDIT) {
            if (top && DropGuiHolder.isDecorSlot(raw)) {
                event.setCancelled(true);
                handleNav(player, holder, event.getCurrentItem());
                return;
            }
            if (top && !DropGuiHolder.isContentSlot(raw)) {
                event.setCancelled(true);
            }
            clickSound(player);
            return;
        }

        event.setCancelled(true);
        if (!top) return;
        handleNav(player, holder, event.getCurrentItem());
    }

    private void handleEventSlot(Player player, DropGuiHolder holder, InventoryClickEvent event, int slot) {
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            if (dropManager.isButton(cursor)) return;
            ItemStack add = cursor.clone();
            if (dropManager.addEventItem(holder.getSource(), slot, add)) {
                event.getView().setCursor(null);
                dropManager.refreshEventSlot(holder.getInventory(), holder.getSource(), slot);
            } else {
                player.sendMessage(ChatColor.RED + "W slocie mogą być maksymalnie 2 eventówki.");
            }
            return;
        }
        ItemStack removed = dropManager.removeEventItem(holder.getSource(), slot);
        dropManager.refreshEventSlot(holder.getInventory(), holder.getSource(), slot);
        if (removed != null) event.getView().setCursor(removed);
    }

    private void handleNav(Player player, DropGuiHolder holder, ItemStack clicked) {
        String id = dropManager.buttonId(clicked);
        if (id == null) return;
        clickSound(player);

        DropSource src = DropSource.fromId(id);
        if (src != null) {
            if (holder.getSource() == src) return;
            if (holder.getMode() == DropGuiHolder.Mode.EDIT && holder.getSource() != null) {
                dropManager.saveFromInventory(holder.getSource(), holder.getInventory());
                switching.add(player.getUniqueId());
                dropManager.openEdit(player, src);
                return;
            }
            if (holder.getMode() == DropGuiHolder.Mode.EDIT_EVENT) {
                switching.add(player.getUniqueId());
                dropManager.openEditEvent(player, src);
                return;
            }
            dropManager.openView(player, src);
            return;
        }

        if ("info".equals(id) && holder.getMode() != DropGuiHolder.Mode.MAIN) {
            if (holder.getMode() == DropGuiHolder.Mode.EDIT && holder.getSource() != null) {
                dropManager.saveFromInventory(holder.getSource(), holder.getInventory());
                switching.add(player.getUniqueId());
            } else if (holder.getMode() == DropGuiHolder.Mode.EDIT_EVENT) {
                switching.add(player.getUniqueId());
            }
            dropManager.openMain(player);
        }
    }

    private void clickSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof DropGuiHolder holder)) return;
        if (holder.getMode() == DropGuiHolder.Mode.EDIT_EVENT) {
            for (int slot : event.getRawSlots()) {
                if (slot < event.getInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        if (holder.getMode() != DropGuiHolder.Mode.EDIT) {
            event.setCancelled(true);
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize()
                    && (DropGuiHolder.isDecorSlot(slot) || !DropGuiHolder.isContentSlot(slot))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof DropGuiHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (holder.getMode() == DropGuiHolder.Mode.EDIT && holder.getSource() != null) {
            dropManager.saveFromInventory(holder.getSource(), event.getInventory());
            if (switching.remove(player.getUniqueId())) return;
            player.sendMessage(ChatColor.GREEN + "Zapisano drop ze " + holder.getSource().getDisplayGenitive()
                    + ChatColor.GRAY + " (" + dropManager.regularCount(holder.getSource()) + " itemów).");
            return;
        }
        if (holder.getMode() == DropGuiHolder.Mode.EDIT_EVENT && holder.getSource() != null) {
            dropManager.save();
            if (switching.remove(player.getUniqueId())) return;
            player.sendMessage(ChatColor.GREEN + "Zapisano eventówki ze " + holder.getSource().getDisplayGenitive()
                    + ChatColor.GRAY + " (" + dropManager.eventSlotCount(holder.getSource()) + " slotów).");
        }
    }
}
