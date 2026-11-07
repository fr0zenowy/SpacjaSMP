package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Perły nie istnieją — kasowane z EQ, ziemi, skrzyń i rzutów. */
public class PearlRulesListener implements Listener {

    private static final Material PEARL = Material.ENDER_PEARL;

    private final ZamrozeniePlugin plugin;
    private final ConcurrentHashMap<UUID, Long> lastNotice = new ConcurrentHashMap<>();

    public PearlRulesListener(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 1L, 10L);
    }

    private void sweep() {
        for (World world : Bukkit.getWorlds()) {
            for (EnderPearl pearl : world.getEntitiesByClass(EnderPearl.class)) {
                pearl.remove();
            }
            for (Item item : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = item.getItemStack();
                if (stack != null && stack.getType() == PEARL) item.remove();
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            stripInventory(player.getInventory());
            stripInventory(player.getEnderChest());
        }
    }

    private static boolean stripInventory(Inventory inv) {
        if (inv == null) return false;
        boolean changed = false;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getType() == PEARL) {
                inv.setItem(i, null);
                changed = true;
            }
        }
        if (inv instanceof PlayerInventory pInv) {
            ItemStack extra = pInv.getItemInOffHand();
            if (extra != null && extra.getType() == PEARL) {
                pInv.setItemInOffHand(null);
                changed = true;
            }
            ItemStack cursor = pInv.getHolder() instanceof Player player ? player.getItemOnCursor() : null;
            if (cursor != null && cursor.getType() == PEARL) {
                ((Player) pInv.getHolder()).setItemOnCursor(null);
                changed = true;
            }
        }
        return changed;
    }

    private void notify(Player player) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUniqueId());
        if (last != null && now - last < 1500L) return;
        lastNotice.put(player.getUniqueId(), now);
        player.sendMessage(ChatColor.RED + "Perły są wyłączone na serwerze.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        event.setCancelled(true);
        pearl.remove();
        if (pearl.getShooter() instanceof Player player) notify(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != PEARL) return;
        event.setCancelled(true);
        event.getPlayer().getInventory().remove(PEARL);
        event.getPlayer().setItemOnCursor(null);
        notify(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (stack == null || stack.getType() != PEARL) return;
        event.setCancelled(true);
        event.getItem().remove();
        if (event.getEntity() instanceof Player player) notify(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (stack == null || stack.getType() != PEARL) return;
        event.setCancelled(true);
        event.getItemDrop().remove();
        event.getPlayer().getInventory().remove(PEARL);
        notify(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack stack = event.getEntity().getItemStack();
        if (stack != null && stack.getType() == PEARL) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        stripInventory(event.getPlayer().getInventory());
        stripInventory(event.getPlayer().getEnderChest());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOpen(InventoryOpenEvent event) {
        stripInventory(event.getInventory());
        if (event.getInventory().getHolder() instanceof Container container) {
            try {
                BlockState state = container.getBlock().getState();
                if (state instanceof Container c) {
                    stripInventory(c.getInventory());
                    c.update();
                }
            } catch (Exception ignored) {
            }
        }
        if (event.getPlayer() instanceof Player player) {
            stripInventory(player.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == PEARL) {
            event.setCancelled(true);
            event.setCurrentItem(null);
            if (event.getWhoClicked() instanceof Player player) notify(player);
        }
        if (event.getCursor() != null && event.getCursor().getType() == PEARL) {
            event.setCancelled(true);
            event.setCursor(null);
            if (event.getWhoClicked() instanceof Player player) notify(player);
        }
        stripInventory(event.getView().getTopInventory());
        stripInventory(event.getView().getBottomInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getOldCursor() != null && event.getOldCursor().getType() == PEARL) {
            event.setCancelled(true);
            event.setCursor(null);
            if (event.getWhoClicked() instanceof Player player) notify(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLoot(LootGenerateEvent event) {
        event.getLoot().removeIf(stack -> stack != null && stack.getType() == PEARL);
    }
}
