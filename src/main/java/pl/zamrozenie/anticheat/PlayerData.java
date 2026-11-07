package pl.zamrozenie.anticheat;

import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/** Stan anticheata per gracz. */
public class PlayerData {

    // ── violation levels ─────────────────────────────────────────────────────
    double totalVl = 0;
    final Map<Check, Double> vl = new EnumMap<>(Check.class);
    final Map<Check, Long> lastAlert = new EnumMap<>(Check.class);

    // ── ruch ─────────────────────────────────────────────────────────────────
    Location lastGround;
    Location lastLoc;
    double lastDy = 0;
    int airTicks = 0;
    double riseSinceGround = 0;
    double highestY = Double.NaN;
    double flyBuffer = 0;
    double speedBuffer = 0;
    int stillAirPackets = 0;   // pakiety bez zmiany pozycji w powietrzu (hover w miejscu)
    long setbackMs;            // kiedy MY cofnelismy gracza (ten teleport nie daje wyjatku)
    long lastFlyFlagMs;        // po fladze Fly przez kilka sekund tryb "ostry"

    // znaczniki czasowe (ms) zdarzen, ktore legalnie psuja fizyke
    long joinMs;
    long lastVelocityMs;
    long lastBigVelocityMs;
    long lastTeleportMs;
    long lastSlimeMs;
    long lastSpecialBlockMs;   // honey, bubble column, powder snow, cobweb, scaffolding itp.
    long lastVehicleMs;
    long lastGlideMs;
    long lastFlightMs;         // allowFlight / creative / spectator

    // licznik pakietow ruchu na sekunde
    int movesThisSecond = 0;
    long moveSecondStart = 0;
    int timerStrikes = 0;

    // ── walka ────────────────────────────────────────────────────────────────
    final Deque<Long> swings = new ArrayDeque<>();
    int cpsStrikes = 0;
    final Deque<long[]> recentHits = new ArrayDeque<>(); // [timeMs, targetEntityIdHash]

    // ── stawianie / kopanie ──────────────────────────────────────────────────
    final Deque<Long> placements = new ArrayDeque<>();
    final Deque<Long> breaks = new ArrayDeque<>();
    int vehicleFlyBuffer = 0;

    // ── analiza behawioralna (KillAura) ──────────────────────────────────────
    final Deque<Float> yawDeltas = new ArrayDeque<>();
    int gcdFailBuffer = 0;
    int residualTotal = 0;
    int residualFails = 0;
    long lastSnapMs;
    long lastSwingMs;
    long lastHitMs;
    boolean pendingHitSwing;
    final Deque<Integer> swingOutcomes = new ArrayDeque<>();
    final Deque<Long> snapHits = new ArrayDeque<>();
    final Deque<Double> hitAngles = new ArrayDeque<>();
    double suspicion = 0;
    String lastAuraReason = "";
    long lastAuraFlagMs;
    long hitBlockedUntilMs;

    void resetMovement(Location loc, long now) {
        lastGround = loc == null ? null : loc.clone();
        lastLoc = loc == null ? null : loc.clone();
        lastDy = 0;
        airTicks = 0;
        riseSinceGround = 0;
        highestY = loc == null ? Double.NaN : loc.getY();
        flyBuffer = 0;
        speedBuffer = 0;
        lastTeleportMs = now;
    }

    double vlOf(Check c) {
        return vl.getOrDefault(c, 0.0);
    }
}
