package pl.zamrozenie.anticheat;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Checki swiata: lodka/wagonik (BoatFly), kopanie (Nuker / FastBreak), interakcje poza zasiegiem (Freecam / GhostHand).
 */
public class WorldChecks implements Listener {

    /** Vanilla: zasieg do bloku 4.5 (survival), mierzony do najblizszego punktu bloku. Z pingiem i lagiem dajemy 5.6. */
    private static final double BLOCK_REACH = 5.6;
    /** Kat miedzy kierunkiem patrzenia a blokiem — vanilla klient nie moze kopac / klikac bloku, na ktory nie patrzy. */
    private static final double BLOCK_ANGLE_DEG = 70.0;
    /** Vanilla survival: po zniszczeniu bloku klient czeka 5 tickow (max 4/s przy instabreak, plus zapas). */
    private static final int MAX_BREAKS_PER_SEC = 9;

    private final AntiCheat ac;

    public WorldChecks(AntiCheat ac) {
        this.ac = ac;
    }

    // ─── LODKA / WAGONIK ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onVehicleMove(VehicleMoveEvent e) {
        Vehicle v = e.getVehicle();
        if (!(v instanceof Boat)) return;
        Player driver = null;
        for (Entity pass : v.getPassengers()) if (pass instanceof Player pl) { driver = pl; break; }
        if (driver == null || ac.isExempt(driver)) return;

        Location from = e.getFrom(), to = e.getTo();
        double dy = to.getY() - from.getY();
        PlayerData d = ac.data(driver);
        if (dy <= 0.05) { d.vehicleFlyBuffer = Math.max(0, d.vehicleFlyBuffer - 1); return; }

        // lodka moze isc w gore tylko w wodzie / bubble column
        if (vehicleInLiquid(v, to)) { d.vehicleFlyBuffer = 0; return; }
        // ...albo gdy wjezdza na blok ponizej 0.6 (step), np. z wody na brzeg — sprawdzamy podparcie
        if (dy < 0.6 && hasSupportBelow(v, to)) { d.vehicleFlyBuffer = 0; return; }

        if (++d.vehicleFlyBuffer >= 2) {
            d.vehicleFlyBuffer = 0;
            if (ac.flag(driver, Check.VEHICLE_FLY, String.format("lodka w gore dy=%.2f bez wody", dy))) {
                final Location back = from.clone();
                final Player p = driver;
                v.eject();
                v.setVelocity(new Vector(0, 0, 0));
                v.teleport(back);
                d.setbackMs = System.currentTimeMillis();
                p.teleport(back.clone().add(0, 0.5, 0));
            }
        }
    }

    private static boolean vehicleInLiquid(Vehicle v, Location to) {
        BoundingBox bb = v.getBoundingBox().shift(to.toVector().subtract(v.getLocation().toVector())).expand(0.05, 0.3, 0.05);
        for (int x = (int) Math.floor(bb.getMinX()); x <= (int) Math.floor(bb.getMaxX()); x++)
            for (int y = (int) Math.floor(bb.getMinY()); y <= (int) Math.floor(bb.getMaxY()); y++)
                for (int z = (int) Math.floor(bb.getMinZ()); z <= (int) Math.floor(bb.getMaxZ()); z++) {
                    Block b = to.getWorld().getBlockAt(x, y, z);
                    if (b.isLiquid() || b.getType() == Material.BUBBLE_COLUMN) return true;
                    if (b.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged()) return true;
                }
        return false;
    }

    private static boolean hasSupportBelow(Vehicle v, Location to) {
        BoundingBox bb = v.getBoundingBox().shift(to.toVector().subtract(v.getLocation().toVector()));
        BoundingBox probe = new BoundingBox(bb.getMinX(), bb.getMinY() - 0.7, bb.getMinZ(), bb.getMaxX(), bb.getMinY() + 0.05, bb.getMaxZ());
        for (int x = (int) Math.floor(probe.getMinX()); x <= (int) Math.floor(probe.getMaxX()); x++)
            for (int y = (int) Math.floor(probe.getMinY()); y <= (int) Math.floor(probe.getMaxY()); y++)
                for (int z = (int) Math.floor(probe.getMinZ()); z <= (int) Math.floor(probe.getMaxZ()); z++) {
                    Block b = to.getWorld().getBlockAt(x, y, z);
                    if (b.isPassable() || b.isLiquid()) continue;
                    for (BoundingBox shape : b.getCollisionShape().getBoundingBoxes())
                        if (shape.clone().shift(x, y, z).overlaps(probe)) return true;
                }
        return false;
    }

    // ─── KOPANIE ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (ac.isExempt(p) || p.getGameMode() == GameMode.CREATIVE) return;
        PlayerData d = ac.data(p);
        Block b = e.getBlock();

        String reach = blockReachProblem(p, b);
        if (reach != null) {
            if (ac.flag(p, Check.NUKER, reach)) e.setCancelled(true);
            return;
        }

        long now = System.currentTimeMillis();
        d.breaks.addLast(now);
        while (!d.breaks.isEmpty() && now - d.breaks.peekFirst() > 1000) d.breaks.removeFirst();
        if (d.breaks.size() > MAX_BREAKS_PER_SEC) {
            if (ac.flag(p, Check.NUKER, d.breaks.size() + " blokow/s")) e.setCancelled(true);
        }
    }

    // ─── INTERAKCJE / STAWIANIE ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (ac.isExempt(p) || p.getGameMode() == GameMode.CREATIVE) return;
        String problem = blockReachProblem(p, e.getClickedBlock());
        if (problem != null && ac.flag(p, Check.FREECAM, problem)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (ac.isExempt(p) || p.getGameMode() == GameMode.CREATIVE) return;
        String problem = blockReachProblem(p, e.getBlockAgainst());
        if (problem != null && ac.flag(p, Check.FREECAM, problem)) e.setCancelled(true);
    }

    /** Null = OK. Inaczej opis problemu (za daleko / nie patrzy). */
    private static String blockReachProblem(Player p, Block b) {
        Location eye = p.getEyeLocation();
        BoundingBox box = new BoundingBox(b.getX(), b.getY(), b.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1);
        double cx = clamp(eye.getX(), box.getMinX(), box.getMaxX());
        double cy = clamp(eye.getY(), box.getMinY(), box.getMaxY());
        double cz = clamp(eye.getZ(), box.getMinZ(), box.getMaxZ());
        double dist = Math.sqrt(sq(eye.getX() - cx) + sq(eye.getY() - cy) + sq(eye.getZ() - cz));
        if (dist > BLOCK_REACH) return String.format("blok %.1f kratek od oczu (max %.1f)", dist, BLOCK_REACH);
        if (dist < 0.3) return null; // stoi w bloku / tuz przy nim — kat bez sensu

        Vector look = eye.getDirection();
        Vector toCenter = new Vector(b.getX() + 0.5 - eye.getX(), b.getY() + 0.5 - eye.getY(), b.getZ() + 0.5 - eye.getZ());
        double angle = Math.toDegrees(look.angle(toCenter));
        // blisko bloku kat do srodka moze byc duzy nawet legalnie — skalujemy prog
        double limit = dist < 1.5 ? 110.0 : BLOCK_ANGLE_DEG;
        if (angle > limit) return String.format("blok %.0f\u00B0 od kierunku patrzenia", angle);
        return null;
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double sq(double v) { return v * v; }
}
