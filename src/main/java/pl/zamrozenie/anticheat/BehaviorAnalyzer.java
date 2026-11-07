package pl.zamrozenie.anticheat;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Analiza behawioralna gracza ("AI" w sensie statystyki, nie sieci neuronowej).
 *
 * Profiluje kazdego gracza i buduje wynik podejrzenia (suspicion):
 *
 *  1. GCD rotacji — mysz w Minecrafcie obraca kamere o wielokrotnosc stalej
 *     zaleznej od czulosci (min. ~0.0096 stopnia przy czulosci 0%). Killaura
 *     generuje dowolne floaty. Szacujemy jednostke (najmniejszy niezerowy obrot
 *     w oknie) i sprawdzamy czy reszta obrotow jest jej wielokrotnoscia.
 *  2. Snap — obrot > 60 stopni w jednym ticku, a tuz po nim trafienie.
 *  3. Idealne celowanie — srednia i odchylenie kata oko->srodek celu przy hitach.
 *     Czlowiek ma rozrzut kilku stopni; aura celuje w punkt.
 *  4. Skutecznosc — trafienia / machniecia. Ludzie pudluja.
 *
 * Kazda metryka dodaje punkty; powyzej progu leci flaga KillAura i blokada hitow.
 */
public class BehaviorAnalyzer {

    private static final double MIN_LEGIT_GCD_DEG = 0.0085;   // czulosc 0% = 0.0096
    private static final int    GCD_WINDOW        = 40;
    private static final double SNAP_DEG          = 60.0;
    private static final long   SNAP_HIT_WINDOW   = 120;      // ms miedzy snapem a hitem
    private static final double SUSPICION_FLAG    = 60.0;

    private final AntiCheat ac;

    public BehaviorAnalyzer(AntiCheat ac) {
        this.ac = ac;
    }

    // ─── ROTACJE ──────────────────────────────────────────────────────────────

    public void onRotation(Player p, PlayerData d, Location from, Location to, long now) {
        float dYaw = Math.abs(wrap(to.getYaw() - from.getYaw()));
        if (dYaw == 0f || Float.isNaN(dYaw) || dYaw > 180f) return;

        if (dYaw > SNAP_DEG) d.lastSnapMs = now;

        Deque<Float> w = d.yawDeltas;
        w.addLast(dYaw);
        while (w.size() > GCD_WINDOW) w.removeFirst();
        if (w.size() < 20) return;

        // jednostka = najmniejszy niezerowy obrot w oknie (1 piksel myszy)
        float unit = Float.MAX_VALUE;
        for (float v : w) if (v > 0f && v < unit) unit = v;
        if (unit == Float.MAX_VALUE) return;

        if (unit < MIN_LEGIT_GCD_DEG) {
            // obroty mniejsze niz pozwala czulosc 0% — nie pochodza z myszy
            d.gcdFailBuffer++;
            if (d.gcdFailBuffer >= 12) {
                d.gcdFailBuffer = 4;
                addSuspicion(p, d, 45, String.format(Locale.US, "rotacje bez GCD (jednostka %.5f\u00B0)", unit), now);
            }
            return;
        }
        d.gcdFailBuffer = Math.max(0, d.gcdFailBuffer - 1);

        // test reszty: kazdy obrot powinien byc ~calkowita wielokrotnoscia jednostki
        double k = dYaw / unit;
        if (k > 2.0 && k < 100.0) {
            double frac = Math.abs(k - Math.rint(k));
            d.residualTotal++;
            if (frac > 0.22) d.residualFails++;
            if (d.residualTotal >= 40) {
                double ratio = (double) d.residualFails / d.residualTotal;
                if (ratio > 0.45) {
                    addSuspicion(p, d, 35, String.format(Locale.US, "%.0f%% obrotow poza siatka myszy", ratio * 100), now);
                }
                d.residualTotal = 0;
                d.residualFails = 0;
            }
        }
    }

    // ─── HITY ─────────────────────────────────────────────────────────────────

    public void onSwing(PlayerData d, long now) {
        d.lastSwingMs = now;
        Deque<Integer> o = d.swingOutcomes;
        o.addLast(d.pendingHitSwing ? 1 : 0);
        d.pendingHitSwing = false;
        while (o.size() > 40) o.removeFirst();
    }

    public void onHit(Player attacker, PlayerData d, Entity target, Location eye, long now) {
        d.lastHitMs = now;
        d.pendingHitSwing = true;

        // snap + hit
        if (now - d.lastSnapMs <= SNAP_HIT_WINDOW) {
            d.snapHits.addLast(now);
            while (!d.snapHits.isEmpty() && now - d.snapHits.peekFirst() > 10_000) d.snapHits.pollFirst();
            if (d.snapHits.size() >= 3) {
                d.snapHits.clear();
                addSuspicion(attacker, d, 30, "3 snapy >" + (int) SNAP_DEG + "\u00B0 z natychmiastowym trafieniem w 10 s", now);
            }
        }

        // kat oko -> srodek celu
        Vector toCenter = target.getBoundingBox().getCenter().subtract(eye.toVector());
        if (toCenter.lengthSquared() > 0.01) {
            double angle = Math.toDegrees(eye.getDirection().angle(toCenter));
            if (!Double.isNaN(angle)) {
                d.hitAngles.addLast(angle);
                while (d.hitAngles.size() > 15) d.hitAngles.removeFirst();
                if (d.hitAngles.size() >= 12) {
                    double mean = 0;
                    for (double a : d.hitAngles) mean += a;
                    mean /= d.hitAngles.size();
                    double var = 0;
                    for (double a : d.hitAngles) var += (a - mean) * (a - mean);
                    double std = Math.sqrt(var / d.hitAngles.size());
                    if (mean < 1.2 && std < 0.6) {
                        d.hitAngles.clear();
                        addSuspicion(attacker, d, 35, String.format(Locale.US, "idealne celowanie (sr. %.2f\u00B0, odch. %.2f\u00B0)", mean, std), now);
                    }
                }
            }
        }

        // skutecznosc
        if (d.swingOutcomes.size() >= 30) {
            int hits = 0;
            for (int v : d.swingOutcomes) hits += v;
            double acc = (double) hits / d.swingOutcomes.size();
            if (acc > 0.92) {
                d.swingOutcomes.clear();
                addSuspicion(attacker, d, 20, String.format(Locale.US, "skutecznosc %.0f%% na %d machniec", acc * 100, 30), now);
            }
        }
    }

    // ─── WYNIK ────────────────────────────────────────────────────────────────

    private void addSuspicion(Player p, PlayerData d, double points, String reason, long now) {
        d.suspicion += points;
        d.lastAuraReason = reason;
        if (d.suspicion >= SUSPICION_FLAG && now - d.lastAuraFlagMs > 3000) {
            d.lastAuraFlagMs = now;
            d.suspicion = SUSPICION_FLAG * 0.5;
            if (ac.flag(p, Check.AURA, reason)) {
                ac.blockHits(p, 4000);
            }
        } else if (points >= 30) {
            // pojedyncza mocna poszlaka — sam alert informacyjny, bez blokady
            ac.alert(AntiCheat.PREFIX + ChatColor.GOLD + p.getName() + ChatColor.GRAY + " \u2192 analiza: " + ChatColor.WHITE + reason
                    + ChatColor.DARK_GRAY + " (podejrzenie " + AntiCheat.fmt(d.suspicion) + "/" + (int) SUSPICION_FLAG + ")");
        }
    }

    /** Profil do /ac ai <gracz>. */
    public List<String> profile(Player p, PlayerData d) {
        List<String> out = new ArrayList<>();
        float unit = Float.MAX_VALUE;
        for (float v : d.yawDeltas) if (v > 0f && v < unit) unit = v;
        double mean = 0, std = 0;
        if (!d.hitAngles.isEmpty()) {
            for (double a : d.hitAngles) mean += a;
            mean /= d.hitAngles.size();
            for (double a : d.hitAngles) std += (a - mean) * (a - mean);
            std = Math.sqrt(std / d.hitAngles.size());
        }
        int hits = 0;
        for (int v : d.swingOutcomes) hits += v;
        String g = ChatColor.DARK_GRAY + "  \u00BB " + ChatColor.GRAY;
        out.add(AntiCheat.PREFIX + ChatColor.WHITE + p.getName() + ChatColor.GRAY + " — podejrzenie " + ChatColor.GOLD + AntiCheat.fmt(d.suspicion)
                + ChatColor.DARK_GRAY + "/" + (int) SUSPICION_FLAG + ChatColor.GRAY + ", ping " + p.getPing() + "ms");
        out.add(g + "jednostka myszy: " + ChatColor.WHITE + (unit == Float.MAX_VALUE ? "brak danych" : String.format(Locale.US, "%.4f\u00B0", unit))
                + ChatColor.GRAY + " (legit >= 0.0096), probek " + d.yawDeltas.size());
        out.add(g + "obroty poza siatka: " + ChatColor.WHITE + d.residualFails + "/" + d.residualTotal + ChatColor.GRAY + " (biezace okno)");
        out.add(g + "snap+hit (10 s): " + ChatColor.WHITE + d.snapHits.size());
        out.add(g + "celowanie przy hitach: " + ChatColor.WHITE + (d.hitAngles.isEmpty() ? "brak" : String.format(Locale.US, "sr. %.2f\u00B0, odch. %.2f\u00B0 (%d hitow)", mean, std, d.hitAngles.size()))
                + ChatColor.GRAY + " (czlowiek: sr. 2-8\u00B0)");
        out.add(g + "skutecznosc: " + ChatColor.WHITE + (d.swingOutcomes.isEmpty() ? "brak" : String.format(Locale.US, "%.0f%% (%d/%d machniec)", 100.0 * hits / d.swingOutcomes.size(), hits, d.swingOutcomes.size())));
        out.add(g + "ostatnia poszlaka: " + ChatColor.WHITE + (d.lastAuraReason.isEmpty() ? "-" : d.lastAuraReason));
        long left = d.hitBlockedUntilMs - System.currentTimeMillis();
        if (left > 0) out.add(g + ChatColor.RED + "hity zablokowane jeszcze " + (left / 1000 + 1) + " s");
        return out;
    }

    private static float wrap(float deg) {
        deg %= 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }
}
