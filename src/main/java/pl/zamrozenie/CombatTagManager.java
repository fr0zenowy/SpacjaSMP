package pl.zamrozenie;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CombatTagManager {

    private final ZamrozeniePlugin plugin;
    private final Map<UUID, Long> until = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastOpponent = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRespawn = ConcurrentHashMap.newKeySet();
    private final Set<UUID> combatLogKill = ConcurrentHashMap.newKeySet();
    private final File respawnFile;
    private BukkitTask task;

    public CombatTagManager(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        this.respawnFile = new File(plugin.getDataFolder(), "combat-log-respawn.yml");
        loadPending();
    }

    public void start() {
        stopTicker();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        stopTicker();
        until.clear();
        lastOpponent.clear();
        savePending();
    }

    private void stopTicker() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public int durationSeconds() {
        return Math.max(1, plugin.getConfig().getInt("combat-tag.duration-seconds", 30));
    }

    public int riptideCooldownTicks() {
        return Math.max(0, plugin.getConfig().getInt("combat-tag.riptide-cooldown-seconds", 20)) * 20;
    }

    public boolean isTagged(Player player) {
        return player != null && isTagged(player.getUniqueId());
    }

    public boolean isTagged(UUID id) {
        Long expire = until.get(id);
        return expire != null && expire > System.currentTimeMillis();
    }

    public void tag(Player player) {
        if (player == null || !player.isOnline()) return;
        until.put(player.getUniqueId(), System.currentTimeMillis() + durationSeconds() * 1000L);
        sendBar(player);
    }

    public void tagPvP(Player a, Player b) {
        tag(a);
        tag(b);
        if (a == null || b == null) return;
        lastOpponent.put(a.getUniqueId(), b.getUniqueId());
        lastOpponent.put(b.getUniqueId(), a.getUniqueId());
    }

    public void untag(UUID id) {
        until.remove(id);
        lastOpponent.remove(id);
    }

    public boolean isCombatLogKill(UUID id) {
        return combatLogKill.remove(id);
    }

    public boolean shouldForceRespawn(UUID id) {
        return pendingRespawn.contains(id);
    }

    public ZamrozeniePlugin getPlugin() {
        return plugin;
    }

    public void consumeForcedRespawn(UUID id) {
        if (id != null && pendingRespawn.remove(id)) savePending();
    }

    public Location respawnLocation(Player player) {
        Location loc = player.getRespawnLocation();
        if (loc != null && loc.getWorld() != null) return loc;
        World world = Bukkit.getWorlds().isEmpty() ? player.getWorld() : Bukkit.getWorlds().get(0);
        return world.getSpawnLocation();
    }

    /**
     * Wylogowanie w walce = zwykla smierc: drop itemow, XP i utrata serca.
     * Nie polegamy na PlayerDeathEvent przy quit — na Paper czesto nie spada EQ.
     */
    public void punishLogout(Player player) {
        if (player == null) return;
        LifestealManager ls = plugin.getLifestealManager();
        boolean tagged = isTagged(player);
        boolean lsCombat = ls != null && ls.isInCombat(player.getUniqueId());
        if (!tagged && !lsCombat) return;

        UUID id = player.getUniqueId();
        UUID opponentId = lastOpponent.remove(id);
        if (opponentId == null && ls != null) opponentId = ls.getCombatAttacker(id);
        until.remove(id);
        pendingRespawn.add(id);
        combatLogKill.add(id);
        savePending();

        dropLikeDeath(player);

        Player killer = opponentId != null ? Bukkit.getPlayer(opponentId) : null;
        if (ls != null) {
            ls.applyDeathHearts(player, killer != null && killer.isOnline() ? killer : null);
        }

        player.setNoDamageTicks(0);
        player.setInvulnerable(false);
        try {
            if (killer != null && killer.isOnline() && !killer.getUniqueId().equals(id)) {
                player.damage(10_000.0, killer);
            } else {
                player.damage(10_000.0);
            }
        } catch (Exception ignored) {
        }
        if (!player.isDead() && player.getHealth() > 0.0) {
            try {
                player.setHealth(0.0);
            } catch (Exception ignored) {
            }
        }
        if (!player.isDead()) {
            String killerName = killer != null ? killer.getName() : null;
            if (killerName != null) {
                Bukkit.broadcastMessage(ChatColor.RED + player.getName() + ChatColor.GRAY
                        + " wylogował się podczas walki z " + ChatColor.WHITE + killerName + ChatColor.GRAY + ".");
            } else {
                Bukkit.broadcastMessage(ChatColor.RED + player.getName() + ChatColor.GRAY
                        + " wylogował się podczas walki.");
            }
        }
    }

    private void dropLikeDeath(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;
        PlayerInventory inv = player.getInventory();
        dropContents(world, loc, inv.getContents());
        dropContents(world, loc, inv.getArmorContents());
        dropContents(world, loc, inv.getExtraContents());
        dropOne(world, loc, inv.getItemInOffHand());
        dropOne(world, loc, player.getItemOnCursor());
        inv.clear();
        inv.setArmorContents(null);
        inv.setItemInOffHand(null);
        player.setItemOnCursor(null);

        int orbs = Math.min(100, player.getLevel() * 7);
        if (orbs > 0) {
            world.spawn(loc, ExperienceOrb.class).setExperience(orbs);
        }
        player.setLevel(0);
        player.setExp(0f);
        player.setTotalExperience(0);
    }

    private static void dropContents(World world, Location loc, ItemStack[] items) {
        if (items == null) return;
        for (ItemStack item : items) dropOne(world, loc, item);
    }

    private static void dropOne(World world, Location loc, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() < 1) return;
        Enchantment vanish = Enchantment.getByKey(NamespacedKey.minecraft("vanishing_curse"));
        if (vanish != null && item.containsEnchantment(vanish)) return;
        world.dropItemNaturally(loc, item.clone());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Long expire = until.get(player.getUniqueId());
            if (expire == null) continue;
            if (expire <= now) {
                untag(player.getUniqueId());
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
                player.sendMessage(ChatColor.GRAY + "Nie jesteś już podczas walki.");
                continue;
            }
            sendBar(player);
        }
        until.entrySet().removeIf(e -> e.getValue() <= now);
    }

    private void sendBar(Player player) {
        Long expire = until.get(player.getUniqueId());
        if (expire == null) return;
        int sec = (int) Math.max(1, Math.ceil((expire - System.currentTimeMillis()) / 1000.0));
        String text = ChatColor.GRAY + "Jesteś podczas walki jeszcze przez: "
                + ChatColor.RED + sec + "s";
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
    }

    private void loadPending() {
        if (!respawnFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(respawnFile);
        List<String> raw = yaml.getStringList("pending");
        for (String s : raw) {
            try {
                pendingRespawn.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void savePending() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> raw = new ArrayList<>();
        for (UUID id : pendingRespawn) raw.add(id.toString());
        yaml.set("pending", raw);
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(respawnFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie zapisac combat-log-respawn.yml", e);
        }
    }
}
