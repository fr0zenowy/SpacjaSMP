package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ItemLimitListener implements Listener {

    private static final Material TOTEM = Material.TOTEM_OF_UNDYING;
    private static final Material PEARL = Material.ENDER_PEARL;

    private final ZamrozeniePlugin plugin;
    private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();

    public ItemLimitListener(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
    }

    private int totemMax() {
        return Math.max(0, plugin.getConfig().getInt("item-limits.totem-max", 3));
    }

    private int pearlMax() {
        return Math.max(0, plugin.getConfig().getInt("item-limits.pearl-max", 8));
    }

    private int maxOf(Material type) {
        if (type == TOTEM) return totemMax();
        if (type == PEARL) return pearlMax();
        return Integer.MAX_VALUE;
    }

    // ─── Podnoszenie z ziemi ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (ignore(player)) return;
        ItemStack stack = event.getItem().getItemStack();
        if (stack == null) return;

        for (Material type : List.of(TOTEM, PEARL)) {
            int incoming = countDeep(stack, type);
            if (incoming <= 0) continue;
            int room = maxOf(type) - countPlayer(player, type);
            if (room <= 0) {
                event.setCancelled(true);
                event.getItem().setPickupDelay(40);
                notify(player, type);
                return;
            }
            if (stack.getType() != type) {
                if (incoming > room) {
                    event.setCancelled(true);
                    event.getItem().setPickupDelay(40);
                    notify(player, type);
                    return;
                }
                continue;
            }
            if (stack.getAmount() <= room) continue;

            int leftoverAmt = stack.getAmount() - room;
            ItemStack leftover = stack.clone();
            leftover.setAmount(leftoverAmt);
            stack.setAmount(room);
            event.getItem().setItemStack(stack);
            Location loc = event.getItem().getLocation();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (loc.getWorld() == null) return;
                Item drop = loc.getWorld().dropItem(loc, leftover);
                drop.setPickupDelay(40);
            });
            notify(player, type);
        }
        scheduleEnforce(player);
    }

    // ─── Skrzynki i dowolne GUI ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (ignore(player)) return;

        Inventory clicked = event.getClickedInventory();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean own = isOwnInventory(clicked, player);

        boolean containerOpen = !isOwnInventory(event.getView().getTopInventory(), player);

        if (event.getClick() == ClickType.DOUBLE_CLICK && containerOpen
                && (firstLimited(cursor) != null || firstLimited(current) != null)) {
            event.setCancelled(true);
            Material type = firstLimited(cursor);
            if (type == null) type = firstLimited(current);
            notify(player, type);
            scheduleEnforce(player);
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY && !own && current != null) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            if (wouldOverflow(player, current, hotbar)) {
                event.setCancelled(true);
                Material type = firstLimited(current);
                if (type != null) notify(player, type);
            }
            scheduleEnforce(player);
            return;
        }

        if (event.getClick() == ClickType.SWAP_OFFHAND && !own && current != null) {
            ItemStack off = player.getInventory().getItemInOffHand();
            if (wouldOverflow(player, current, off)) {
                event.setCancelled(true);
                Material type = firstLimited(current);
                if (type != null) notify(player, type);
            }
            scheduleEnforce(player);
            return;
        }

        if (event.isShiftClick() && !own && current != null && current.getType() != Material.AIR) {
            handleIncoming(event, player, current, true);
            scheduleEnforce(player);
            return;
        }

        if (!own && current != null && current.getType() != Material.AIR && !event.isShiftClick()) {
            if (cursor == null || cursor.getType() == Material.AIR) {
                handleIncoming(event, player, current, false);
            } else if (wouldOverflow(player, current, cursor)) {
                event.setCancelled(true);
                Material type = firstLimited(current);
                if (type != null) notify(player, type);
            }
        }

        scheduleEnforce(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) scheduleEnforce(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) scheduleEnforce(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleEnforce(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemFrame(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) return;
        Player player = event.getPlayer();
        if (ignore(player)) return;
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand.getType() != Material.AIR) return;
        ItemStack item = frame.getItem();
        Material type = firstLimited(item);
        if (type == null) return;
        if (countPlayer(player, type) + countDeep(item, type) > maxOf(type)) {
            event.setCancelled(true);
            notify(player, type);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        if (ignore(player)) return;
        ItemStack taking = event.getArmorStandItem();
        ItemStack putting = event.getPlayerItem();
        if (putting != null && putting.getType() != Material.AIR) return;
        Material type = firstLimited(taking);
        if (type == null) return;
        if (countPlayer(player, type) + countDeep(taking, type) > maxOf(type)) {
            event.setCancelled(true);
            notify(player, type);
        }
    }

    /**
     * @return false gdy event zostal anulowany / obsluzony recznie
     */
    private boolean handleIncoming(InventoryClickEvent event, Player player, ItemStack incoming, boolean shift) {
        for (Material type : List.of(TOTEM, PEARL)) {
            int pieces = countDeep(incoming, type);
            if (pieces <= 0) continue;
            int room = maxOf(type) - countPlayer(player, type);
            if (room <= 0) {
                event.setCancelled(true);
                notify(player, type);
                return false;
            }
            if (incoming.getType() != type) {
                if (pieces > room) {
                    event.setCancelled(true);
                    notify(player, type);
                    return false;
                }
                continue;
            }
            if (incoming.getAmount() <= room) continue;

            event.setCancelled(true);
            int take = room;
            if (shift) {
                ItemStack give = incoming.clone();
                give.setAmount(take);
                Map<Integer, ItemStack> left = player.getInventory().addItem(give);
                int leftover = 0;
                for (ItemStack l : left.values()) leftover += l.getAmount();
                int moved = take - leftover;
                if (moved <= 0) return false;
                incoming.setAmount(incoming.getAmount() - moved);
            } else {
                ItemStack hold = incoming.clone();
                hold.setAmount(take);
                event.getView().setCursor(hold);
                incoming.setAmount(incoming.getAmount() - take);
            }
            if (incoming.getAmount() <= 0) event.getClickedInventory().setItem(event.getSlot(), null);
            else event.getClickedInventory().setItem(event.getSlot(), incoming);
            player.updateInventory();
            notify(player, type);
            return false;
        }
        return true;
    }

    private boolean wouldOverflow(Player player, ItemStack incoming, ItemStack swappingOut) {
        for (Material type : List.of(TOTEM, PEARL)) {
            int add = countDeep(incoming, type);
            if (add <= 0) continue;
            int have = countPlayer(player, type);
            int sub = swappingOut != null && swappingOut.getType() != Material.AIR
                    ? countDeep(swappingOut, type) : 0;
            if (have - sub + add > maxOf(type)) return true;
        }
        return false;
    }

    private void scheduleEnforce(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> enforce(player));
    }

    private void enforce(Player player) {
        if (player == null || !player.isOnline() || ignore(player)) return;
        dumpExtra(player, TOTEM);
        dumpExtra(player, PEARL);
    }

    private void dumpExtra(Player player, Material type) {
        int extra = countPlayer(player, type) - maxOf(type);
        if (extra <= 0) return;
        List<ItemStack> dumped = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length && extra > 0; i++) {
            extra = strip(storage[i], type, extra, dumped);
            if (storage[i] != null && storage[i].getAmount() <= 0) storage[i] = null;
        }
        inv.setStorageContents(storage);

        if (extra > 0) {
            ItemStack off = inv.getItemInOffHand();
            extra = strip(off, type, extra, dumped);
            if (off != null && off.getAmount() <= 0) inv.setItemInOffHand(null);
            else inv.setItemInOffHand(off);
        }
        if (extra > 0) {
            ItemStack cursor = player.getOpenInventory().getCursor();
            extra = strip(cursor, type, extra, dumped);
            if (cursor == null || cursor.getType() == Material.AIR || cursor.getAmount() <= 0) {
                player.getOpenInventory().setCursor(null);
            } else {
                player.getOpenInventory().setCursor(cursor);
            }
        }

        Location loc = player.getLocation();
        for (ItemStack drop : dumped) {
            if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) continue;
            player.getWorld().dropItemNaturally(loc, drop);
        }
        if (!dumped.isEmpty()) notify(player, type);
    }

    private int strip(ItemStack stack, Material type, int extra, List<ItemStack> dumped) {
        if (stack == null || stack.getType() == Material.AIR || extra <= 0) return extra;
        if (stack.getType() == type) {
            int take = Math.min(extra, stack.getAmount());
            ItemStack drop = stack.clone();
            drop.setAmount(take);
            dumped.add(drop);
            stack.setAmount(stack.getAmount() - take);
            return extra - take;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
            ItemStack[] contents = box.getInventory().getContents();
            for (int i = 0; i < contents.length && extra > 0; i++) {
                extra = strip(contents[i], type, extra, dumped);
                if (contents[i] != null && contents[i].getAmount() <= 0) contents[i] = null;
            }
            box.getInventory().setContents(contents);
            bsm.setBlockState(box);
            stack.setItemMeta(bsm);
            return extra;
        }
        if (meta instanceof BundleMeta bundle) {
            List<ItemStack> items = new ArrayList<>(bundle.getItems());
            List<ItemStack> kept = new ArrayList<>();
            for (ItemStack in : items) {
                extra = strip(in, type, extra, dumped);
                if (in != null && in.getType() != Material.AIR && in.getAmount() > 0) kept.add(in);
            }
            bundle.setItems(kept);
            stack.setItemMeta(bundle);
        }
        return extra;
    }

    private int countPlayer(Player player, Material type) {
        int n = 0;
        for (ItemStack it : player.getInventory().getStorageContents()) n += countDeep(it, type);
        n += countDeep(player.getInventory().getItemInOffHand(), type);
        n += countDeep(player.getOpenInventory().getCursor(), type);
        return n;
    }

    private int countDeep(ItemStack stack, Material type) {
        if (stack == null || stack.getType() == Material.AIR) return 0;
        int n = 0;
        if (stack.getType() == type) n += stack.getAmount();
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack in : box.getInventory().getContents()) n += countDeep(in, type);
        }
        if (meta instanceof BundleMeta bundle) {
            for (ItemStack in : bundle.getItems()) n += countDeep(in, type);
        }
        return n;
    }

    private Material firstLimited(ItemStack stack) {
        if (stack == null) return null;
        if (countDeep(stack, TOTEM) > 0) return TOTEM;
        if (countDeep(stack, PEARL) > 0) return PEARL;
        return null;
    }

    private boolean isOwnInventory(Inventory inv, Player player) {
        if (inv == null) return false;
        if (inv.equals(player.getInventory())) return true;
        InventoryType type = inv.getType();
        return type == InventoryType.CRAFTING || type == InventoryType.PLAYER || type == InventoryType.CREATIVE;
    }

    private boolean ignore(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    private void notify(Player player, Material type) {
        if (player == null || type == null) return;
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUniqueId());
        if (last != null && now - last < 1500L) return;
        lastNotice.put(player.getUniqueId(), now);
        if (type == TOTEM) {
            player.sendMessage(ChatColor.RED + "Możesz mieć maksymalnie " + ChatColor.WHITE + totemMax()
                    + ChatColor.RED + " totemy.");
        } else if (type == PEARL) {
            player.sendMessage(ChatColor.RED + "Możesz mieć maksymalnie " + ChatColor.WHITE + pearlMax()
                    + ChatColor.RED + " ender pereł.");
        }
    }
}
