package pl.zamrozenie;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoidBowListener implements Listener {

    private static final double TRUE_DAMAGE = 7.0;
    private static final double HOLE_RADIUS = 5.5;
    private static final double FREEZE_RADIUS = 1.4;
    private static final int HOLE_TICKS = 8 * 20;
    private static final int FREEZE_TICKS = 5 * 20;
    private static final long SHARED_CD_MS = 150_000L;
    private static final Particle.DustOptions DUST_CORE = new Particle.DustOptions(Color.fromRGB(0, 0, 0), 2.6f);
    private static final Particle.DustOptions DUST_DISK = new Particle.DustOptions(Color.fromRGB(12, 12, 14), 1.55f);
    private static final Particle.DustOptions DUST_DISK_OUTER = new Particle.DustOptions(Color.fromRGB(28, 28, 32), 1.2f);
    private static final Particle.DustOptions DUST_PHOTON = new Particle.DustOptions(Color.fromRGB(255, 132, 28), 1.15f);
    private static final Particle.DustOptions DUST_PHOTON_HOT = new Particle.DustOptions(Color.fromRGB(255, 214, 120), 0.95f);

    private final ZamrozeniePlugin plugin;
    private final LegendaryItems items;
    private final NamespacedKey bulletKey;
    private final NamespacedKey arrowKey;
    private final Map<UUID, Long> sharedCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> frozenUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> toggleAt = new ConcurrentHashMap<>();
    private final List<Hole> holes = new ArrayList<>();
    private final List<Flight> flights = new ArrayList<>();

    public VoidBowListener(ZamrozeniePlugin plugin, LegendaryItems items) {
        this.plugin = plugin;
        this.items = items;
        this.bulletKey = new NamespacedKey(plugin, "void_bullet");
        this.arrowKey = new NamespacedKey(plugin, "void_arrow");
        startTicker();
    }

    public void shutdown() {
        frozenUntil.clear();
        for (Hole hole : holes) hole.removePieces();
        holes.clear();
        for (Flight flight : flights) {
            if (flight.bullet.isValid()) flight.bullet.remove();
        }
        flights.clear();
    }

    private boolean isBow(ItemStack item) {
        return LegendaryItems.VOID_BOW.equals(items.getId(item));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggle(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        ItemStack bow = player.getInventory().getItemInMainHand();
        if (!isBow(bow)) return;

        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = toggleAt.get(player.getUniqueId());
        if (last != null && now - last < 250L) return;
        toggleAt.put(player.getUniqueId(), now);

        int next = items.getVoidMode(bow) == 0 ? 1 : 0;
        items.setVoidMode(bow, next);
        String name = next == 0
                ? ChatColor.DARK_PURPLE + "Czarna dziura"
                : ChatColor.RED + "3.5 serca";
        String text = ChatColor.GRAY + "Moc: " + name;
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
        player.sendMessage(ChatColor.GRAY + ShopManager.ARROW + " Zmieniono moc na " + name);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, next == 0 ? 0.7f : 1.3f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (!isBow(bow)) return;

        int mode = items.getVoidMode(bow);
        Entity projectile = event.getProjectile();
        Vector velocity = projectile.getVelocity().clone();
        Location from = projectile.getLocation();

        if (blockedByCooldown(player, event)) return;

        if (mode == 0) {
            projectile.remove();
            spawnBullet(player, from, velocity);
            startCooldown(player);
            player.getWorld().playSound(from, Sound.ENTITY_SHULKER_SHOOT, 1f, 0.55f);
            return;
        }

        startCooldown(player);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.getPersistentDataContainer().set(arrowKey, PersistentDataType.BYTE, (byte) 1);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBulletHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof ShulkerBullet bullet)) return;
        if (!bullet.getPersistentDataContainer().has(bulletKey, PersistentDataType.BYTE)) return;

        Location at;
        if (event.getHitEntity() != null) {
            at = event.getHitEntity().getLocation();
        } else if (event.getHitBlock() != null) {
            at = event.getHitBlock().getLocation().add(0.5, 1.05, 0.5);
        } else {
            at = bullet.getLocation();
        }
        UUID owner = bullet.getShooter() instanceof Player p ? p.getUniqueId() : null;
        bullet.remove();
        openHole(at, owner);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBulletDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof ShulkerBullet bullet)) return;
        if (!bullet.getPersistentDataContainer().has(bulletKey, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow)) return;
        if (!arrow.getPersistentDataContainer().has(arrowKey, PersistentDataType.BYTE)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player shooter = arrow.getShooter() instanceof Player p ? p : null;
        event.setCancelled(true);
        arrow.remove();
        if (shooter == null || shooter.getUniqueId().equals(victim.getUniqueId())) return;

        victim.setNoDamageTicks(0);
        DamageSource source = DamageSource.builder(DamageType.SONIC_BOOM)
                .withCausingEntity(shooter)
                .withDirectEntity(shooter)
                .withDamageLocation(victim.getLocation())
                .build();
        victim.damage(TRUE_DAMAGE, source);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.5f);
        victim.getWorld().spawnParticle(Particle.SQUID_INK, victim.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.02);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isFrozen(event.getPlayer())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
        Location stay = from.clone();
        stay.setYaw(to.getYaw());
        stay.setPitch(to.getPitch());
        event.setTo(stay);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!isFrozen(event.getPlayer())) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
                || cause == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.isGliding() && isFrozen(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        frozenUntil.remove(event.getPlayer().getUniqueId());
        sharedCooldown.remove(event.getPlayer().getUniqueId());
    }

    private boolean blockedByCooldown(Player player, EntityShootBowEvent event) {
        Long ready = sharedCooldown.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (ready == null || ready <= now) return false;
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "Kataklizm Pustki odnowi się za " + ChatColor.WHITE + formatCd(ready - now) + ChatColor.RED + ".");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
        return true;
    }

    private void startCooldown(Player player) {
        sharedCooldown.put(player.getUniqueId(), System.currentTimeMillis() + SHARED_CD_MS);
    }

    private static String formatCd(long ms) {
        long sec = Math.max(1L, (ms + 999L) / 1000L);
        long m = sec / 60L;
        long s = sec % 60L;
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private boolean isFrozen(Player player) {
        Long until = frozenUntil.get(player.getUniqueId());
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            frozenUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void spawnBullet(Player shooter, Location from, Vector velocity) {
        World world = from.getWorld();
        if (world == null) return;
        ShulkerBullet bullet = world.spawn(from, ShulkerBullet.class, spawned -> {
            spawned.setShooter(shooter);
            spawned.setTarget(null);
            spawned.setGravity(false);
            spawned.setVisibleByDefault(false);
            spawned.setVelocity(velocity);
            spawned.getPersistentDataContainer().set(bulletKey, PersistentDataType.BYTE, (byte) 1);
        });
        flights.add(new Flight(bullet, velocity.clone()));
    }

    private void openHole(Location at, UUID owner) {
        World world = at.getWorld();
        if (world == null) return;
        Location center = at.clone();
        world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 0.85f, 0.38f);
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.9f, 0.45f);
        world.spawnParticle(Particle.SQUID_INK, center, 28, 0.12, 0.12, 0.12, 0.01, null, true);
        world.spawnParticle(Particle.SMOKE, center, 18, 0.18, 0.18, 0.18, 0.01, null, true);
        world.spawnParticle(Particle.DUST, center, 20, 0.25, 0.25, 0.25, 0, DUST_CORE, true);
        holes.add(new Hole(center, owner, spawnHoleVisuals(center)));
    }

    private List<HolePiece> spawnHoleVisuals(Location center) {
        List<HolePiece> out = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) return out;
        out.add(piece(world, center, Material.BLACK_CONCRETE, 0f, 0f, 1.7f, 1.7f, 1.7f, 0f, 0.7f));
        out.add(piece(world, center, Material.BLACK_STAINED_GLASS, 0f, 0f, 2.2f, 2.2f, 2.2f, 0f, -0.45f));
        int n = 16;
        for (int i = 0; i < n; i++) {
            float a = (float) (i * Math.PI * 2.0 / n);
            out.add(piece(world, center, Material.BLACK_CONCRETE, 2.15f, 0f, 0.52f, 0.09f, 0.52f, a, 0.055f));
            out.add(piece(world, center, Material.GRAY_CONCRETE, 3.1f, 0f, 0.46f, 0.07f, 0.46f, a + 0.1f, 0.038f));
        }
        return out;
    }

    private HolePiece piece(World world, Location center, Material mat,
                            float radius, float yOff, float sx, float sy, float sz,
                            float startAngle, float spin) {
        BlockDisplay display = world.spawn(center, BlockDisplay.class, d -> {
            d.setBlock(mat.createBlockData());
            d.setBrightness(new Display.Brightness(0, 0));
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setPersistent(false);
            d.setInterpolationDuration(2);
            d.setTeleportDuration(2);
        });
        HolePiece piece = new HolePiece(display, radius, yOff, sx, sy, sz, startAngle, spin);
        applyPiece(piece, 0);
        return piece;
    }

    private static void applyPiece(HolePiece piece, int ticks) {
        float ang = piece.startAngle + ticks * piece.spin;
        float x = (float) (Math.cos(ang) * piece.radius);
        float z = (float) (Math.sin(ang) * piece.radius);
        piece.display.setTransformation(new Transformation(
                new Vector3f(x - piece.sx / 2f, piece.yOff - piece.sy / 2f, z - piece.sz / 2f),
                new AxisAngle4f(ang, 0f, 1f, 0f),
                new Vector3f(piece.sx, piece.sy, piece.sz),
                new AxisAngle4f()
        ));
    }

    private void freeze(Player player) {
        if (isFrozen(player)) return;
        frozenUntil.put(player.getUniqueId(), System.currentTimeMillis() + FREEZE_TICKS * 50L);
        player.setVelocity(new Vector(0, 0, 0));
        player.setGliding(false);
        if (player.getVehicle() != null) player.leaveVehicle();
        player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.7f);
        player.sendMessage(ChatColor.DARK_PURPLE + "Czarna dziura Cię zamroziła na 5s!");
    }

    private void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tickFlights();
                tickHoles();
                long now = System.currentTimeMillis();
                frozenUntil.entrySet().removeIf(e -> e.getValue() <= now);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void tickFlights() {
        Iterator<Flight> it = flights.iterator();
        while (it.hasNext()) {
            Flight flight = it.next();
            ShulkerBullet bullet = flight.bullet;
            if (!bullet.isValid() || bullet.isDead() || flight.ticks++ > 100) {
                if (bullet.isValid()) bullet.remove();
                it.remove();
                continue;
            }
            flight.velocity.setY(flight.velocity.getY() - 0.05);
            bullet.setTarget(null);
            bullet.setVelocity(flight.velocity.clone());
            Location trail = bullet.getLocation();
            World tw = trail.getWorld();
            if (tw != null) {
                tw.spawnParticle(Particle.DUST, trail, 2, 0.03, 0.03, 0.03, 0, DUST_CORE, true);
                tw.spawnParticle(Particle.SMOKE, trail, 1, 0.02, 0.02, 0.02, 0.0, null, true);
            }
        }
    }

    private void tickHoles() {
        Iterator<Hole> it = holes.iterator();
        while (it.hasNext()) {
            Hole hole = it.next();
            hole.ticks++;
            if (hole.ticks > HOLE_TICKS || hole.center.getWorld() == null) {
                hole.removePieces();
                it.remove();
                continue;
            }
            paintHole(hole);
            pullPlayers(hole);
        }
    }

    private void paintHole(Hole hole) {
        Location c = hole.center;
        World world = c.getWorld();
        if (world == null) return;
        double t = hole.ticks * 0.12;

        if (hole.ticks % 2 == 0) {
            for (HolePiece piece : hole.pieces) {
                if (!piece.display.isValid()) continue;
                piece.display.setInterpolationDuration(2);
                applyPiece(piece, hole.ticks);
            }
        }

        world.spawnParticle(Particle.DUST, c, 8, 0.2, 0.2, 0.2, 0, DUST_CORE, true);
        if (hole.ticks % 2 == 0) {
            world.spawnParticle(Particle.SQUID_INK, c, 3, 0.1, 0.1, 0.1, 0.002, null, true);
            world.spawnParticle(Particle.SMOKE, c, 2, 0.12, 0.12, 0.12, 0.0, null, true);
        }

        ringDust(world, c, 1.26, 0.0, 26, t * 1.7, DUST_PHOTON);
        ringDust(world, c, 1.42, 0.0, 18, t * 1.45, DUST_PHOTON_HOT);
        if (hole.ticks % 2 == 0) {
            ringDust(world, c, 1.34, 0.7, 12, t * 1.25, DUST_PHOTON);
            ringDust(world, c, 1.34, -0.7, 12, t * 1.25, DUST_PHOTON);
        }

        ringDust(world, c, 2.15, 0.0, 14, t * 0.95, DUST_DISK);
        ringDust(world, c, 3.1, 0.0, 16, t * 0.7, DUST_DISK_OUTER);
        ringDust(world, c, 4.05, 0.0, 12, t * 0.5, DUST_DISK_OUTER);

        if (hole.ticks % 2 == 0) {
            for (int i = 0; i < 5; i++) {
                double ang = t + i * 1.256;
                double r = 3.7;
                Location p = c.clone().add(Math.cos(ang) * r, 0.02, Math.sin(ang) * r);
                Vector inward = c.toVector().subtract(p.toVector());
                if (inward.lengthSquared() < 0.0001) continue;
                inward.normalize().multiply(0.13);
                world.spawnParticle(Particle.SQUID_INK, p, 0, inward.getX(), 0.0, inward.getZ(), 0.28);
            }
        }

        if (hole.ticks % 16 == 0) {
            world.playSound(c, Sound.BLOCK_PORTAL_AMBIENT, 0.16f, 0.32f);
        }
    }

    private static void ringDust(World world, Location center, double radius, double y,
                                 int points, double phase, Particle.DustOptions dust) {
        for (int i = 0; i < points; i++) {
            double ang = phase + i * Math.PI * 2.0 / points;
            Location p = center.clone().add(Math.cos(ang) * radius, y, Math.sin(ang) * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust, true);
        }
    }

    private void pullPlayers(Hole hole) {
        World world = hole.center.getWorld();
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            if (!player.isOnline() || player.isDead()) continue;
            if (hole.owner != null && hole.owner.equals(player.getUniqueId())) continue;
            if (isFrozen(player)) {
                player.setVelocity(new Vector(0, 0, 0));
                continue;
            }
            Location loc = player.getLocation();
            if (loc.getWorld() != world) continue;
            double dist = loc.distance(hole.center);
            if (dist > HOLE_RADIUS) continue;
            if (dist <= FREEZE_RADIUS) {
                freeze(player);
                continue;
            }
            Vector pull = hole.center.toVector().subtract(loc.toVector());
            if (pull.lengthSquared() < 0.0001) continue;
            double strength = 0.18 + 0.32 * (1.0 - dist / HOLE_RADIUS);
            player.setVelocity(player.getVelocity().multiply(0.65).add(pull.normalize().multiply(strength)));
        }
    }

    private static final class Flight {
        private final ShulkerBullet bullet;
        private final Vector velocity;
        private int ticks;

        private Flight(ShulkerBullet bullet, Vector velocity) {
            this.bullet = bullet;
            this.velocity = velocity;
        }
    }

    private static final class Hole {
        private final Location center;
        private final UUID owner;
        private final List<HolePiece> pieces;
        private int ticks;

        private Hole(Location center, UUID owner, List<HolePiece> pieces) {
            this.center = center;
            this.owner = owner;
            this.pieces = pieces;
        }

        private void removePieces() {
            for (HolePiece piece : pieces) {
                if (piece.display.isValid()) piece.display.remove();
            }
            pieces.clear();
        }
    }

    private static final class HolePiece {
        private final BlockDisplay display;
        private final float radius;
        private final float yOff;
        private final float sx;
        private final float sy;
        private final float sz;
        private final float startAngle;
        private final float spin;

        private HolePiece(BlockDisplay display, float radius, float yOff,
                          float sx, float sy, float sz, float startAngle, float spin) {
            this.display = display;
            this.radius = radius;
            this.yOff = yOff;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.startAngle = startAngle;
            this.spin = spin;
        }
    }
}
