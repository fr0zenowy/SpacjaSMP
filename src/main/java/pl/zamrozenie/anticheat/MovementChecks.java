package pl.zamrozenie.anticheat;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Checki ruchu: Fly / Speed / Blink / BadPacket / Timer.
 *
 * Wszystko liczone z PlayerMoveEvent (jeden event = jeden pakiet pozycji
 * klienta, czyli jeden tick klienta). Nie ufamy fladze onGround z pakietu —
 * sami sprawdzamy kolizje pod bounding boxem gracza.
 */
public class MovementChecks implements Listener {

    private static final double MAX_JUMP_RISE      = 1.35;   // vanilla skok = 1.2523
    private static final double JUMP_BOOST_PER_LVL = 0.7;
    private static final double HOVER_EPS          = 0.02;
    private static final double MAX_H_PER_TICK     = 1.0;    // twardy limit (setback)
    private static final double SOFT_H_PER_TICK    = 0.66;   // miekki limit (sprint-jump + speed I ~0.6)
    private static final double BLINK_DISTANCE     = 10.0;
    private static final int    MAX_MOVES_PER_SEC  = 32;     // vanilla = 20

    private final AntiCheat ac;

    public MovementChecks(AntiCheat ac) {
        this.ac = ac;
    }

    // ─── EVENTY POMOCNICZE ────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        ac.data(e.getPlayer()).resetMovement(e.getPlayer().getLocation(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        ac.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.isCancelled() || e.getTo() == null) return;
        PlayerData d = ac.data(e.getPlayer());
        long now = System.currentTimeMillis();
        boolean ourSetback = now - d.setbackMs < 200;
        d.resetMovement(e.getTo(), now);
        // Nasz wlasny setback NIE daje okresu ochronnego — inaczej cheater lata 1.5 s, cofka, 1.5 s, cofka...
        if (ourSetback) {
            d.lastTeleportMs = 0;
            d.flyBuffer = 1.0;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        ac.data(e.getPlayer()).resetMovement(e.getRespawnLocation(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        ac.data(e.getPlayer()).resetMovement(e.getPlayer().getLocation(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVelocity(PlayerVelocityEvent e) {
        if (e.isCancelled()) return;
        PlayerData d = ac.data(e.getPlayer());
        long now = System.currentTimeMillis();
        if (now - d.setbackMs < 200) return; // velocity wywolane naszym setbackiem — bez wyjatku
        Vector v = e.getVelocity();
        if (v.lengthSquared() < 0.0001) return; // zerowe velocity nic nie zmienia w fizyce
        d.lastVelocityMs = now;
        if (v.getY() > 0.5 || v.lengthSquared() > 1.5) d.lastBigVelocityMs = now;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        ac.data(e.getPlayer()).lastFlightMs = System.currentTimeMillis();
    }

    // ─── GLOWNY CHECK ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (ac.isExempt(p)) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();

        // 1) BadPacket: NaN / nieskonczonosc / pitch poza [-90, 90]
        if (!isFinite(to) || Math.abs(to.getPitch()) > 90.01f) {
            if (ac.flag(p, Check.BAD_PACKET, "pozycja=" + coords(to) + " pitch=" + to.getPitch())) {
                e.setCancelled(true);
            }
            return;
        }

        // 2) Timer / packet spam (liczymy wszystkie pakiety ruchu, tez same obroty)
        if (now - d.moveSecondStart >= 1000) {
            if (d.movesThisSecond > MAX_MOVES_PER_SEC) {
                d.timerStrikes++;
                if (d.timerStrikes >= 2) {
                    ac.flag(p, Check.TIMER, d.movesThisSecond + " pakietow ruchu/s");
                    d.timerStrikes = 0;
                }
            } else {
                d.timerStrikes = 0;
            }
            d.movesThisSecond = 0;
            d.moveSecondStart = now;
        }
        d.movesThisSecond++;

        if (from.getWorld() != to.getWorld()) {
            d.resetMovement(to, now);
            return;
        }
        BehaviorAnalyzer analyzer = ac.getAnalyzer();
        if (analyzer != null) analyzer.onRotation(p, d, from, to, now);
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();

        if (dx == 0 && dy == 0 && dz == 0) {
            // Sam obrot glowy / pakiet "idle". Legalnie w powietrzu NIE MOZNA stac w miejscu —
            // grawitacja zawsze zmienia Y. Kilka takich pakietow z rzedu w powietrzu = hover.
            if (onGround(p, to) || isExemptState(p, to, d, now)) {
                d.stillAirPackets = 0;
                return;
            }
            d.stillAirPackets++;
            if (d.stillAirPackets >= 5) {
                d.stillAirPackets = 0;
                d.lastFlyFlagMs = now;
                if (ac.flag(p, Check.FLY, "hover w miejscu (" + coords(to) + ")")) {
                    setback(e, d, from);
                }
            }
            return;
        }
        d.stillAirPackets = 0;

        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dist = Math.sqrt(horizontal * horizontal + dy * dy);

        // 3) Blink: skok o >10 kratek bez teleportu
        if (dist > BLINK_DISTANCE && now - d.lastTeleportMs > 2000 && !p.isInsideVehicle()) {
            if (ac.flag(p, Check.BLINK, String.format("%.1f kratek w 1 pakiecie", dist))) {
                setback(e, d, from);
            }
            return;
        }

        boolean ground = onGround(p, to);
        boolean exempt = isExemptState(p, to, d, now);

        if (ground) {
            d.airTicks = 0;
            d.riseSinceGround = 0;
            d.flyBuffer = Math.max(0, d.flyBuffer - 0.5);
            d.lastGround = to.clone();
            d.highestY = to.getY();
        } else if (exempt) {
            d.airTicks = 0;
            d.riseSinceGround = 0;
            d.flyBuffer = Math.max(0, d.flyBuffer - 0.25);
            d.highestY = Double.isNaN(d.highestY) ? to.getY() : Math.max(d.highestY, to.getY());
        } else {
            d.airTicks++;
            if (dy > 0) d.riseSinceGround += dy;
            d.highestY = Double.isNaN(d.highestY) ? to.getY() : Math.max(d.highestY, to.getY());

            String reason = flyReason(p, d, dy);
            if (reason != null) {
                d.flyBuffer += 1.0;
                // po niedawnej fladze tryb ostry: cofamy od razu, bez bufora
                boolean strict = now - d.lastFlyFlagMs < 5000;
                if (strict || d.flyBuffer >= 2.0) {
                    d.flyBuffer = 1.0;
                    d.lastFlyFlagMs = now;
                    d.highestY = to.getY(); // nie pozwalamy "zarobic" fall distance na locie
                    if (ac.flag(p, Check.FLY, reason)) {
                        setback(e, d, from);
                        return;
                    }
                }
            } else {
                d.flyBuffer = Math.max(0, d.flyBuffer - 0.1);
            }
        }

        // 4) Speed
        if (!exempt && !p.isInsideVehicle()) {
            double allowed = speedAllowance(p, ground, d, now);
            double hardLimit = MAX_H_PER_TICK * (allowed / SOFT_H_PER_TICK); // twardy limit skalowany efektami
            if (horizontal > hardLimit) {
                if (ac.flag(p, Check.SPEED, String.format("%.2f kratki/tick (limit %.2f)", horizontal, hardLimit))) {
                    setback(e, d, from);
                    return;
                }
            } else if (horizontal > allowed) {
                d.speedBuffer += horizontal - allowed;
                if (d.speedBuffer > 1.5) { // ~10+ tickow lekko za szybko
                    d.speedBuffer = 0;
                    if (ac.flag(p, Check.SPEED, String.format("srednio %.2f kratki/tick (limit %.2f)", horizontal, allowed))) {
                        setback(e, d, from);
                        return;
                    }
                }
            } else {
                d.speedBuffer = Math.max(0, d.speedBuffer - 0.05);
            }
        } else {
            d.speedBuffer = 0;
        }

        d.lastDy = dy;
        d.lastLoc = to.clone();
    }

    // ─── FLY ──────────────────────────────────────────────────────────────────

    private String flyReason(Player p, PlayerData d, double dy) {
        // (e) brak grawitacji: w powietrzu kazdy kolejny tick ruchu w gore MUSI byc wolniejszy
        //     (vanilla: 0.42 -> 0.33 -> 0.25 ...). Rowny albo rosnacy dy > 0 = silnik lotu.
        if (d.airTicks >= 2 && dy > 0 && dy > d.lastDy - 0.03) {
            return String.format("brak grawitacji dy %.3f -> %.3f", d.lastDy, dy);
        }
        if (d.airTicks < 3) return null;

        int jumpBoost = 0;
        var eff = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (eff != null) jumpBoost = eff.getAmplifier() + 1;
        double maxRise = MAX_JUMP_RISE + jumpBoost * JUMP_BOOST_PER_LVL;

        // (a) wzniosl sie wyzej niz pozwala skok
        if (d.riseSinceGround > maxRise) {
            return String.format("wzniesienie %.2f > %.2f bez podparcia", d.riseSinceGround, maxRise);
        }
        // (b) hover — dwa kolejne pakiety bez ruchu w pionie w powietrzu
        if (d.airTicks >= 10 && Math.abs(dy) < HOVER_EPS && Math.abs(d.lastDy) < HOVER_EPS) {
            return String.format("hover (dy=%.3f) przez %d tickow", dy, d.airTicks);
        }
        // (c) przyspieszenie w gore w powietrzu (grawitacja zawsze zmniejsza dy)
        if (dy > 0 && dy > d.lastDy + 0.01 && d.airTicks > 3) {
            return String.format("przyspieszenie w gore dy %.3f -> %.3f", d.lastDy, dy);
        }
        // (d) spadanie wolniej niz grawitacja przez dluzszy czas (glide/slow fall hack)
        if (d.airTicks >= 25 && dy < 0 && dy > -0.12 && d.lastDy < 0 && d.lastDy > -0.12) {
            return String.format("za wolne spadanie dy=%.3f po %d tickach", dy, d.airTicks);
        }
        return null;
    }

    // ─── SPEED ────────────────────────────────────────────────────────────────

    private double speedAllowance(Player p, boolean ground, PlayerData d, long now) {
        double allowed = SOFT_H_PER_TICK;
        var speed = p.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) allowed *= 1.0 + 0.2 * (speed.getAmplifier() + 1);
        allowed *= Math.max(0.2f, p.getWalkSpeed()) / 0.2;
        if (now - d.lastVelocityMs < 2000) allowed += 1.5;          // knockback / wind charge
        if (now - d.lastSpecialBlockMs < 1500) allowed += 0.4;      // lod, slime itp.
        if (p.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE) || p.isSwimming()) allowed += 0.5;
        if (p.isGliding() || p.isRiptiding()) allowed += 3.0;
        return allowed;
    }

    // ─── STANY WYJATKOWE ──────────────────────────────────────────────────────

    private boolean isExemptState(Player p, Location to, PlayerData d, long now) {
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) { d.lastFlightMs = now; return true; }
        if (p.getAllowFlight() || p.isFlying()) { d.lastFlightMs = now; return true; }
        if (p.isGliding()) { d.lastGlideMs = now; return true; }
        if (p.isRiptiding()) return true;
        if (p.isInsideVehicle()) { d.lastVehicleMs = now; return true; }
        if (p.isDead() || p.isSleeping()) return true;
        if (p.isClimbing()) return true;
        if (p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return true;
        if (p.isInWater() || p.isSwimming() || inLiquid(p, to)) return true;

        if (now - d.joinMs < 3000) return true;
        if (now - d.lastTeleportMs < 1500) return true;
        if (now - d.lastVelocityMs < 1500) return true;
        if (now - d.lastBigVelocityMs < 3500) return true;
        if (now - d.lastFlightMs < 1500) return true;
        if (now - d.lastGlideMs < 2500) return true;
        if (now - d.lastVehicleMs < 1500) return true;

        if (touchesSpecialBlock(p, to)) { d.lastSpecialBlockMs = now; return true; }
        if (now - d.lastSpecialBlockMs < 1200) return true;
        if (now - d.lastSlimeMs < 3000) return true;
        return false;
    }

    private static boolean inLiquid(Player p, Location to) {
        BoundingBox bb = playerBox(p, to);
        World w = to.getWorld();
        for (int x = (int) Math.floor(bb.getMinX()); x <= (int) Math.floor(bb.getMaxX()); x++)
            for (int y = (int) Math.floor(bb.getMinY()); y <= (int) Math.floor(bb.getMaxY()); y++)
                for (int z = (int) Math.floor(bb.getMinZ()); z <= (int) Math.floor(bb.getMaxZ()); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.isLiquid() || b.getType() == Material.BUBBLE_COLUMN) return true;
                    if (b.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged()) return true;
                }
        return false;
    }

    /** Bloki, ktore legalnie psuja fizyke: pajeczyna, miod, slime, snieg sypki, rusztowanie, drabiny, lawa... */
    private boolean touchesSpecialBlock(Player p, Location to) {
        BoundingBox bb = playerBox(p, to).expand(0.35, 0.05, 0.35);
        // dodatkowo blok pod stopami (slime bounce, honey)
        bb = bb.union(new Vector(bb.getMinX(), bb.getMinY() - 1.0, bb.getMinZ()));
        World w = to.getWorld();
        PlayerData d = ac.data(p);
        boolean special = false;
        for (int x = (int) Math.floor(bb.getMinX()); x <= (int) Math.floor(bb.getMaxX()); x++)
            for (int y = (int) Math.floor(bb.getMinY()); y <= (int) Math.floor(bb.getMaxY()); y++)
                for (int z = (int) Math.floor(bb.getMinZ()); z <= (int) Math.floor(bb.getMaxZ()); z++) {
                    Material m = w.getBlockAt(x, y, z).getType();
                    switch (m) {
                        case SLIME_BLOCK -> { d.lastSlimeMs = System.currentTimeMillis(); special = true; }
                        case COBWEB, HONEY_BLOCK, POWDER_SNOW, SCAFFOLDING, LADDER, VINE, WEEPING_VINES, TWISTING_VINES,
                             WEEPING_VINES_PLANT, TWISTING_VINES_PLANT, CAVE_VINES, CAVE_VINES_PLANT, SWEET_BERRY_BUSH,
                             BUBBLE_COLUMN, WATER, LAVA, SOUL_SAND, PISTON_HEAD, MOVING_PISTON, ICE, PACKED_ICE, BLUE_ICE,
                             FROSTED_ICE, END_PORTAL, NETHER_PORTAL, END_GATEWAY, RED_BED, WHITE_BED, BLACK_BED, BLUE_BED,
                             BROWN_BED, CYAN_BED, GRAY_BED, GREEN_BED, LIGHT_BLUE_BED, LIGHT_GRAY_BED, LIME_BED, MAGENTA_BED,
                             ORANGE_BED, PINK_BED, PURPLE_BED, YELLOW_BED, BIG_DRIPLEAF, SMALL_DRIPLEAF, POINTED_DRIPSTONE,
                             DRAGON_EGG -> special = true;
                        default -> { }
                    }
                }
        return special;
    }

    // ─── GROUND ───────────────────────────────────────────────────────────────

    /** Wlasne sprawdzenie "na ziemi": czy pod bounding boxem gracza jest jakas kolizja. */
    static boolean onGround(Player p, Location to) {
        BoundingBox bb = playerBox(p, to);
        // cienki "czujnik" tuz pod stopami + odrobina wewnatrz (schody, plyty, krawedzie)
        BoundingBox probe = new BoundingBox(bb.getMinX() - 0.001, bb.getMinY() - 0.08, bb.getMinZ() - 0.001,
                                            bb.getMaxX() + 0.001, bb.getMinY() + 0.02, bb.getMaxZ() + 0.001);
        World w = to.getWorld();
        int minX = (int) Math.floor(probe.getMinX()), maxX = (int) Math.floor(probe.getMaxX());
        int minY = (int) Math.floor(probe.getMinY()), maxY = (int) Math.floor(probe.getMaxY());
        int minZ = (int) Math.floor(probe.getMinZ()), maxZ = (int) Math.floor(probe.getMaxZ());
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.isPassable() && !b.isLiquid()) continue;
                    if (b.isLiquid()) continue;
                    for (BoundingBox shape : b.getCollisionShape().getBoundingBoxes()) {
                        BoundingBox abs = shape.clone().shift(x, y, z);
                        if (abs.overlaps(probe)) return true;
                    }
                }
        // stoi na lodce / shulkerze / innym bycie z kolizja
        BoundingBox under = new BoundingBox(bb.getMinX() - 0.3, bb.getMinY() - 0.6, bb.getMinZ() - 0.3,
                                            bb.getMaxX() + 0.3, bb.getMinY() + 0.2, bb.getMaxZ() + 0.3);
        if (!w.getNearbyEntities(under, en -> en instanceof Boat || en instanceof Shulker || en instanceof Minecart).isEmpty()) {
            return true;
        }
        // gracz "w bloku" (piasek spadl na glowe, glitch) — traktuj jak ziemie, zeby nie flagowac
        return insideSolid(w, bb);
    }

    private static boolean insideSolid(World w, BoundingBox bb) {
        BoundingBox inner = bb.clone().expand(-0.05, 0, -0.05);
        for (int x = (int) Math.floor(inner.getMinX()); x <= (int) Math.floor(inner.getMaxX()); x++)
            for (int y = (int) Math.floor(inner.getMinY()); y <= (int) Math.floor(inner.getMaxY()); y++)
                for (int z = (int) Math.floor(inner.getMinZ()); z <= (int) Math.floor(inner.getMaxZ()); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.isPassable()) continue;
                    for (BoundingBox shape : b.getCollisionShape().getBoundingBoxes()) {
                        if (shape.clone().shift(x, y, z).overlaps(inner)) return true;
                    }
                }
        return false;
    }

    static BoundingBox playerBox(Player p, Location at) {
        double half = p.getWidth() / 2.0;
        return new BoundingBox(at.getX() - half, at.getY(), at.getZ() - half,
                               at.getX() + half, at.getY() + p.getHeight(), at.getZ() + half);
    }

    // ─── SETBACK ──────────────────────────────────────────────────────────────

    private void setback(PlayerMoveEvent e, PlayerData d, Location from) {
        Location target = from.clone();
        if (d.lastGround != null && d.lastGround.getWorld() == from.getWorld()
                && d.lastGround.distanceSquared(from) < 64 * 64) {
            target = d.lastGround.clone();
        }
        target.setYaw(e.getTo().getYaw());
        target.setPitch(e.getTo().getPitch());
        d.setbackMs = System.currentTimeMillis();
        e.setTo(target);
        Player p = e.getPlayer();
        p.setFallDistance(0);
        d.airTicks = 0;
        d.riseSinceGround = 0;
        d.stillAirPackets = 0;
        d.highestY = target.getY();
    }

    private static boolean isFinite(Location l) {
        return Double.isFinite(l.getX()) && Double.isFinite(l.getY()) && Double.isFinite(l.getZ())
                && Float.isFinite(l.getYaw()) && Float.isFinite(l.getPitch());
    }

    private static String coords(Location l) {
        return String.format("%.1f/%.1f/%.1f", l.getX(), l.getY(), l.getZ());
    }
}
