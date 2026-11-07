package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/** W Netherze nie da sie stawiac lozek ani ich wysadzac. */
public class NetherBedListener implements Listener {

    private static boolean nether(World world) {
        return world != null && world.getEnvironment() == World.Environment.NETHER;
    }

    private static boolean bed(Material type) {
        return type != null && Tag.BEDS.isTagged(type);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!nether(event.getBlock().getWorld()) || !bed(event.getBlock().getType())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "W Netherze nie można stawiać łóżek.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !nether(block.getWorld()) || !bed(block.getType())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "W Netherze nie można używać łóżek.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnter(PlayerBedEnterEvent event) {
        if (!nether(event.getBed().getWorld())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Block block = event.getBlock();
        if (!nether(block.getWorld()) || !bed(block.getType())) return;
        event.setCancelled(true);
        event.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!nether(event.getLocation().getWorld())) return;
        event.blockList().removeIf(b -> bed(b.getType()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (!nether(event.getBlock().getWorld())) return;
        ItemStack item = event.getItem();
        if (item == null || !bed(item.getType())) return;
        event.setCancelled(true);
    }
}
