package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Iterator;
import java.util.List;

/** W Netherze nie ma ancient debris — znika z chunków i nie da się go wykopać. */
public class NetherDebrisListener implements Listener {

    private final ZamrozeniePlugin plugin;
    private final NamespacedKey legendaryKey;

    public NetherDebrisListener(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        this.legendaryKey = new NamespacedKey(plugin, "legendary_id");
        Bukkit.getScheduler().runTask(plugin, this::stripLoaded);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("netherite.strip-nether", true);
    }

    private boolean stripChests() {
        return plugin.getConfig().getBoolean("netherite.strip-chests", true);
    }

    private static boolean nether(World world) {
        return world != null && world.getEnvironment() == World.Environment.NETHER;
    }

    private static boolean debris(Material type) {
        return type == Material.ANCIENT_DEBRIS;
    }

    private void stripLoaded() {
        if (!enabled()) return;
        for (World world : Bukkit.getWorlds()) {
            if (!nether(world)) continue;
            for (Chunk chunk : world.getLoadedChunks()) {
                strip(chunk);
            }
        }
    }

    private void strip(Chunk chunk) {
        if (chunk == null || !nether(chunk.getWorld())) return;
        int minY = chunk.getWorld().getMinHeight();
        int maxY = chunk.getWorld().getMaxHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (debris(block.getType())) {
                        block.setType(Material.NETHERRACK, false);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!enabled() || !nether(event.getWorld())) return;
        strip(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!enabled()) return;
        Block block = event.getBlock();
        if (!nether(block.getWorld()) || !debris(block.getType())) return;
        event.setCancelled(true);
        event.setDropItems(false);
        block.setType(Material.NETHERRACK, false);
        event.getPlayer().sendMessage(ChatColor.RED + "W Netherze nie da się kopać netherite.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        stripExploded(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        stripExploded(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (containsDebris(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (containsDebris(event.getBlocks())) event.setCancelled(true);
    }

    private void stripExploded(List<Block> blocks) {
        if (!enabled()) return;
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (!nether(block.getWorld()) || !debris(block.getType())) continue;
            it.remove();
            block.setType(Material.NETHERRACK, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        if (!stripChests()) return;
        event.getLoot().removeIf(this::isNetheriteLoot);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestOpen(InventoryOpenEvent event) {
        if (!stripChests()) return;
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (!isWorldChest(holder)) return;
        if (inv.getLocation() == null || !nether(inv.getLocation().getWorld())) return;
        stripInventory(inv);
    }

    private static boolean isWorldChest(InventoryHolder holder) {
        return holder instanceof Chest
                || holder instanceof DoubleChest
                || holder instanceof Barrel
                || holder instanceof StorageMinecart;
    }

    private void stripInventory(Inventory inv) {
        ItemStack[] contents = inv.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (isNetheriteLoot(contents[i])) {
                contents[i] = null;
                changed = true;
            }
        }
        if (changed) inv.setContents(contents);
    }

    private boolean isNetheriteLoot(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(legendaryKey, PersistentDataType.STRING)) {
            return false;
        }
        return switch (item.getType()) {
            case ANCIENT_DEBRIS,
                 NETHERITE_SCRAP,
                 NETHERITE_INGOT,
                 NETHERITE_BLOCK,
                 NETHERITE_SWORD,
                 NETHERITE_PICKAXE,
                 NETHERITE_AXE,
                 NETHERITE_SHOVEL,
                 NETHERITE_HOE,
                 NETHERITE_HELMET,
                 NETHERITE_CHESTPLATE,
                 NETHERITE_LEGGINGS,
                 NETHERITE_BOOTS,
                 NETHERITE_UPGRADE_SMITHING_TEMPLATE -> true;
            default -> false;
        };
    }

    private boolean containsDebris(List<Block> blocks) {
        if (!enabled()) return false;
        for (Block block : blocks) {
            if (nether(block.getWorld()) && debris(block.getType())) return true;
        }
        return false;
    }
}
