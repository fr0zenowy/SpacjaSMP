package pl.zamrozenie.anticheat;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Deque;
import java.util.Iterator;

/**
 * Checki walki:
 *  - Reach      : odleglosc oko -> hitbox celu (wieksze hitboxy / reach hacki)
 *  - WallHit    : bicie przez sciane (fullbox) — raytrace z oka do kilku punktow hitboxa
 *  - Aim        : atak bez patrzenia na cel (killaura bez rotacji)
 *  - MultiAura  : >2 rozne cele w 150 ms
 *  - MaceKiller : obrazenia z mace wieksze niz pozwala realny spadek
 *  - AutoClicker: CPS (alert)
 *  - FastPlace  : stawianie blokow (alert)
 */
public class CombatChecks implements Listener {

    private static final double REACH_BASE         = 3.05;  // vanilla 3.0 + hitbox 0.1 (czesc juz w expand)
    private static final double REACH_LAG_MIN      = 0.30;
    private static final double REACH_LAG_MAX      = 0.75;
    private static final double AIM_MAX_ANGLE_DEG  = 70.0;
    private static final int    MULTI_AURA_TARGETS = 3;
    private static final long   MULTI_AURA_WINDOW  = 150;
    private static final int    CPS_LIMIT          = 24;
    private static final int    PLACE_LIMIT        = 14;

    private final AntiCheat ac;

    public CombatChecks(AntiCheat ac) {
        this.ac = ac;
    }

    // ─── DAMAGE ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return; // sweep/thorns/projectile — pomijamy
        if (ac.isExempt(attacker)) return;
        if (ac.hitsBlocked(attacker)) {
            e.setCancelled(true);
            return;
        }

        Entity target = e.getEntity();
        if (target.getVehicle() == attacker || attacker.getVehicle() == target) return;
        if (target.getWorld() != attacker.getWorld()) return;

        PlayerData d = ac.data(attacker);
        long now = System.currentTimeMillis();
        Location eye = attacker.getEyeLocation();
        BoundingBox box = target.getBoundingBox().expand(0.1); // vanilla: pickable hitbox = bbox + 0.1

        // punkt hitboxa najblizszy oku
        Vector nearest = new Vector(
                clamp(eye.getX(), box.getMinX(), box.getMaxX()),
                clamp(eye.getY(), box.getMinY(), box.getMaxY()),
                clamp(eye.getZ(), box.getMinZ(), box.getMaxZ()));
        double dist = nearest.distance(eye.toVector());

        // 1) REACH
        double lag = REACH_LAG_MIN + Math.min(250, Math.max(0, attacker.getPing())) / 250.0 * (REACH_LAG_MAX - REACH_LAG_MIN);
        if (target instanceof Player tp && tp.getPing() > 0) lag += Math.min(150, tp.getPing()) / 1000.0; // ofiara tez laguje
        double maxReach = REACH_BASE + lag;
        if (dist > maxReach) {
            if (ac.flag(attacker, Check.REACH, String.format("%.2f > %.2f kratek", dist, maxReach))) {
                e.setCancelled(true);
                return;
            }
        }

        // 2) AIM — kat miedzy kierunkiem patrzenia i wektorem do najblizszego punktu hitboxa
        if (dist > 0.25) {
            Vector toTarget = nearest.clone().subtract(eye.toVector());
            double angle = Math.toDegrees(eye.getDirection().angle(toTarget));
            if (!Double.isNaN(angle) && angle > AIM_MAX_ANGLE_DEG) {
                if (ac.flag(attacker, Check.AIM, String.format("kat %.0f\u00B0 od celu", angle))) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        // 3) WALL HIT (fullbox)
        if (dist > 0.4 && eye.getBlock().isPassable() && !canSee(attacker.getWorld(), eye, box, target)) {
            if (ac.flag(attacker, Check.WALL_HIT, "brak linii wzroku do celu (" + target.getType().name().toLowerCase() + ")")) {
                e.setCancelled(true);
                return;
            }
        }

        // 4) MULTI AURA — rozne cele w bardzo krotkim czasie
        Deque<long[]> hits = d.recentHits;
        hits.addLast(new long[]{now, target.getEntityId()});
        while (!hits.isEmpty() && now - hits.peekFirst()[0] > MULTI_AURA_WINDOW) hits.pollFirst();
        int distinct = (int) hits.stream().mapToLong(h -> h[1]).distinct().count();
        if (distinct >= MULTI_AURA_TARGETS) {
            if (ac.flag(attacker, Check.MULTI_AURA, distinct + " celi w " + MULTI_AURA_WINDOW + "ms")) {
                e.setCancelled(true);
                hits.clear();
                return;
            }
        }

        // 5) MACE KILLER
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (hand.getType() == Material.MACE) {
            checkMace(attacker, d, hand, e);
        }

        BehaviorAnalyzer analyzer = ac.getAnalyzer();
        if (analyzer != null) analyzer.onHit(attacker, d, target, eye, now);
    }

    private void checkMace(Player attacker, PlayerData d, ItemStack mace, EntityDamageByEntityEvent e) {
        double y = attacker.getLocation().getY();
        double trackedFall = Double.isNaN(d.highestY) ? 0 : Math.max(0, d.highestY - y);
        double serverFall = attacker.getFallDistance();
        // ufamy mniejszej z wartosci + tolerancja (server fallDistance mozna spoofowac pakietami)
        double fall = Math.min(serverFall, trackedFall + 1.0);

        AttributeInstance atk = attacker.getAttribute(Attribute.ATTACK_DAMAGE);
        double base = atk != null ? atk.getValue() : 6.0;      // mace 6 + sila itd.
        int sharp = mace.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharp > 0) base += 0.5 * sharp + 0.5;
        int density = mace.getEnchantmentLevel(Enchantment.DENSITY);

        double smash = smashBonus(fall + 0.5, density);
        double allowed = (base + smash) * 1.5 + 1.0;            // crit x1.5 + tolerancja

        if (e.getDamage() > allowed) {
            String detail = String.format("dmg %.1f > %.1f (spadek serwer %.1f / realny %.1f)", e.getDamage(), allowed, serverFall, trackedFall);
            if (ac.flag(attacker, Check.MACE_KILLER, detail)) {
                e.setDamage(Math.min(e.getDamage(), Math.max(1.0, allowed)));
            }
        }
    }

    /** Vanilla: smash tylko gdy spadek > 1.5; 4 dmg/kratke przez 3 kratki, 2 przez kolejne 5, potem 1. Density +0.5/lvl/kratke. */
    private static double smashBonus(double fall, int density) {
        if (fall <= 1.5) return 0;
        double bonus;
        if (fall <= 3) bonus = 4 * fall;
        else if (fall <= 8) bonus = 12 + 2 * (fall - 3);
        else bonus = 22 + (fall - 8);
        return bonus + density * 0.5 * fall;
    }

    // ─── LINIA WZROKU ─────────────────────────────────────────────────────────

    private static boolean canSee(World world, Location eye, BoundingBox box, Entity target) {
        Vector center = box.getCenter();
        Vector[] points = new Vector[]{
                new Vector(clamp(eye.getX(), box.getMinX(), box.getMaxX()), clamp(eye.getY(), box.getMinY(), box.getMaxY()), clamp(eye.getZ(), box.getMinZ(), box.getMaxZ())),
                center,
                new Vector(center.getX(), box.getMaxY() - 0.1, center.getZ()),
                new Vector(center.getX(), box.getMinY() + 0.1, center.getZ()),
                new Vector(center.getX(), clamp(eye.getY(), box.getMinY() + 0.1, box.getMaxY() - 0.1), center.getZ())
        };
        Vector from = eye.toVector();
        for (Vector pt : points) {
            // lekko do srodka, zeby nie trafiac w blok na ktorym stoi cel
            Vector inner = pt.clone().add(center.clone().subtract(pt).multiply(0.05));
            Vector dir = inner.clone().subtract(from);
            double len = dir.length();
            if (len < 0.05) return true;
            RayTraceResult r = world.rayTraceBlocks(eye, dir.normalize(), len, FluidCollisionMode.NEVER, true);
            if (r == null || r.getHitBlock() == null) return true;
            Block hit = r.getHitBlock();
            // cel czesciowo w bloku (np. glowa w plycie) — traktuj jako widoczny
            if (box.overlaps(hit.getBoundingBox())) return true;
        }
        return false;
    }

    // ─── CPS ───────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        if (ac.isExempt(p)) return;
        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();
        BehaviorAnalyzer analyzer = ac.getAnalyzer();
        if (analyzer != null) analyzer.onSwing(d, now);
        d.swings.addLast(now);
        prune(d.swings, now, 1000);
        if (d.swings.size() > CPS_LIMIT) {
            d.cpsStrikes++;
            if (d.cpsStrikes >= 20) { // ~sekunda ciaglego przekraczania
                d.cpsStrikes = 0;
                ac.flag(p, Check.AUTO_CLICKER, d.swings.size() + " CPS");
            }
        } else if (d.cpsStrikes > 0) {
            d.cpsStrikes--;
        }
    }

    // ─── FAST PLACE ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (ac.isExempt(p)) return;
        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();
        d.placements.addLast(now);
        prune(d.placements, now, 1000);
        if (d.placements.size() > PLACE_LIMIT) {
            ac.flag(p, Check.FAST_PLACE, d.placements.size() + " blokow/s");
            d.placements.clear();
        }
    }

    private static void prune(Deque<Long> q, long now, long window) {
        Iterator<Long> it = q.iterator();
        while (it.hasNext()) {
            if (now - it.next() > window) it.remove(); else break;
        }
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
