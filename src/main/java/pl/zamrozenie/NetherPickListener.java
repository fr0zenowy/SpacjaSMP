package pl.zamrozenie;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetherPickListener implements Listener {

    private static final Set<Material> UNBREAKABLE = EnumSet.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID,
            Material.JIGSAW,
            Material.END_PORTAL,
            Material.END_PORTAL_FRAME,
            Material.NETHER_PORTAL,
            Material.END_GATEWAY,
            Material.REINFORCED_DEEPSLATE,
            Material.LIGHT
    );

    private final ZamrozeniePlugin plugin;
    private final LegendaryItems items;
    private final Map<UUID, BlockFace> lastFace = new ConcurrentHashMap<>();
    private final Set<UUID> expanding = ConcurrentHashMap.newKeySet();

    public NetherPickListener(ZamrozeniePlugin plugin, LegendaryItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFace(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getBlockFace() == null) return;
        lastFace.put(event.getPlayer().getUniqueId(), event.getBlockFace());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastFace.remove(id);
        expanding.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!isNetherPick(player.getInventory().getItemInMainHand())) return;
        if (inCombat(player)) return;
        if (!expanding.add(player.getUniqueId())) return;

        try {
            ItemStack tool = player.getInventory().getItemInMainHand();
            int damageAfterOne = itemDamage(tool);
            Block center = event.getBlock();
            BlockFace face = lastFace.getOrDefault(player.getUniqueId(), inferFace(player));
            burst(center);
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    Block extra = relative(center, face, a, b);
                    if (canBreak(extra)) {
                        burst(extra);
                        player.breakBlock(extra);
                    }
                }
            }
            ItemStack after = player.getInventory().getItemInMainHand();
            if (isNetherPick(after)) restoreDamage(after, damageAfterOne);
            playMineFx(center);
        } finally {
            expanding.remove(player.getUniqueId());
        }
    }

    private static void playMineFx(Block center) {
        World world = center.getWorld();
        if (world == null) return;
        Location loc = center.getLocation().add(0.5, 0.5, 0.5);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 22, 0.95, 0.55, 0.95, 0.025);
        world.spawnParticle(Particle.FLAME, loc, 14, 0.75, 0.4, 0.75, 0.012);
        world.spawnParticle(Particle.LAVA, loc, 7, 0.65, 0.25, 0.65, 0);
        world.spawnParticle(Particle.SMOKE, loc, 12, 0.85, 0.45, 0.85, 0.02);
        world.spawnParticle(Particle.CRIMSON_SPORE, loc, 18, 1.05, 0.5, 1.05, 0);
    }

    private static void burst(Block block) {
        World world = block.getWorld();
        if (world == null) return;
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        world.spawnParticle(Particle.BLOCK, loc, 10, 0.28, 0.28, 0.28, 0.08, block.getBlockData());
        world.spawnParticle(Particle.SOUL, loc, 3, 0.2, 0.2, 0.2, 0.01);
    }

    private boolean inCombat(Player player) {
        CombatTagManager combat = plugin.getCombatTag();
        if (combat != null && combat.isTagged(player)) return true;
        LifestealManager ls = plugin.getLifestealManager();
        return ls != null && ls.isInCombat(player.getUniqueId());
    }

    private boolean isNetherPick(ItemStack item) {
        return LegendaryItems.NETHER_PICK.equals(items.getId(item));
    }

    private static int itemDamage(ItemStack item) {
        if (item == null) return 0;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return 0;
        return damageable.getDamage();
    }

    private static void restoreDamage(ItemStack item, int damage) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;
        int max = item.getType().getMaxDurability();
        damageable.setDamage(Math.max(0, Math.min(max, damage)));
        item.setItemMeta(meta);
    }

    private static Block relative(Block center, BlockFace face, int a, int b) {
        return switch (face) {
            case UP, DOWN -> center.getRelative(a, 0, b);
            case NORTH, SOUTH -> center.getRelative(a, b, 0);
            default -> center.getRelative(0, b, a);
        };
    }

    private static BlockFace inferFace(Player player) {
        float pitch = player.getLocation().getPitch();
        if (pitch <= -50) return BlockFace.DOWN;
        if (pitch >= 50) return BlockFace.UP;
        return player.getFacing();
    }

    private static boolean canBreak(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) return false;
        if (UNBREAKABLE.contains(type)) return false;
        return type.getHardness() >= 0f;
    }
}
