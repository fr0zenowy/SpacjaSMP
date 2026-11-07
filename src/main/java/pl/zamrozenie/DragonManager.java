package pl.zamrozenie;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Zarządza eventem Smoka Kresu (/aktywujsmoka).
 *
 * Flow:
 *  1. Komenda → startAnimation() → wszyscy w trybie spectator (kamera w Endzie)
 *  2. Animacja 12 s: kryształy się ładują, błyski, dźwięki, smok materializuje się
 *  3. Gracze odzyskują kontrolę → walka
 *  4. Plugin śledzi damage (sumę do DRAGON_TRACKED_HP). Prawdziwe MC-HP smoka jest
 *     bardzo wysokie, żeby nie padł przypadkowo — zabijamy go ręcznie gdy
 *     suma damage osiągnie próg.
 *  5. Śmierć → ranking + nagrody
 */
public class DragonManager {

    /** Łączne obrażenia do zadania smokowi (1500 HP). */
    private static final double DRAGON_TRACKED_HP = 1_500.0;
    /** Długości faz animacji w tickach (20t = 1s). */
    private static final int ANIM_CHARGE_TICKS  = 100;   // 5 s — narastający portal
    private static final int ANIM_APPEAR_TICKS  = 80;    // 4 s — materializacja smoka
    private static final int ANIM_REVEAL_TICKS  = 60;    // 3 s — wybuch + ujawnienie

    private final ZamrozeniePlugin plugin;
    private final LegendaryItems legendaryItems;

    private EnderDragon dragon;
    /** Suma damage zadanego przez graczy (plugin-side). */
    private final Map<UUID, Double> damageMap = new ConcurrentHashMap<>();
    private final AtomicBoolean fightActive = new AtomicBoolean(false);

    private BukkitTask animTask;
    private BukkitTask scoreboardTask;
    private Scoreboard scoreboard;
    private Objective objective;

    /** Stany graczy zapisane przed animacją. */
    private final Map<UUID, PlayerState> savedStates = new HashMap<>();
    private record PlayerState(Location loc, GameMode gm, boolean allowFlight, boolean flying) {}

    private World endWorld;
    private final List<Entity> tempEntities = new ArrayList<>();

    // ─── Konstruktor ────────────────────────────────────────────────────────────────

    public DragonManager(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        this.legendaryItems = new LegendaryItems(plugin);
    }

    public boolean isActive() { return fightActive.get() || animTask != null; }
    public EnderDragon getDragon() { return dragon; }

    // ─── Wyszukiwanie Endu ──────────────────────────────────────────────────────────

    private World findEnd() {
        World w = Bukkit.getWorld("world_the_end");
        if (w != null) return w;
        for (World cand : Bukkit.getWorlds()) {
            if (cand.getEnvironment() == World.Environment.THE_END) return cand;
        }
        return null;
    }

    // ─── Wejście publiczne: start animacji ─────────────────────────────────────────

    public boolean startEvent() {
        if (isActive()) return false;
        endWorld = findEnd();
        if (endWorld == null) return false;

        damageMap.clear();
        savedStates.clear();
        tempEntities.clear();

        // Kamera spektatora — trochę z boku od platformy
        Location cam = new Location(endWorld, 22, 72, 22);
        lookAt(cam, new Location(endWorld, 0, 64, 0));

        // Zapisz stan graczy i przestaw w spectator
        for (Player p : Bukkit.getOnlinePlayers()) {
            savedStates.put(p.getUniqueId(),
                    new PlayerState(p.getLocation().clone(), p.getGameMode(),
                            p.getAllowFlight(), p.isFlying()));
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(cam);
        }

        broadcastAll(ChatColor.DARK_RED + "" + ChatColor.BOLD +
                "⚔ SMOK KRESU ZOSTAJE PRZYWOLANY! ⚔");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚔ Smok Kresu ⚔",
                    ChatColor.GRAY + "Przygotuj sie do walki...", 10, 80, 20);
        }

        runAnimation(cam);
        return true;
    }

    // ─── Animacja ───────────────────────────────────────────────────────────────────

    private void runAnimation(Location cam) {
        Location center = new Location(endWorld, 0, 64, 0);
        Location dragonSpawn = new Location(endWorld, 0, 80, 0);

        final int[] tick = {0};
        final int totalTicks = ANIM_CHARGE_TICKS + ANIM_APPEAR_TICKS + ANIM_REVEAL_TICKS;

        animTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int t = tick[0]++;

            // ── Faza 1: Narastający portal (0 – 99) ────────────────────────────
            if (t < ANIM_CHARGE_TICKS) {
                double frac = (double) t / ANIM_CHARGE_TICKS;

                // Rosnący wir portalu wokół centrum
                double radius = 2 + frac * 8;
                int steps = (int) (10 + frac * 20);
                for (int i = 0; i < steps; i++) {
                    double angle = 2 * Math.PI * i / steps + t * 0.18;
                    double px = Math.cos(angle) * radius;
                    double pz = Math.sin(angle) * radius;
                    double py = 64 + frac * 20 + Math.sin(t * 0.25 + i) * 2.0;
                    endWorld.spawnParticle(Particle.PORTAL,
                            new Location(endWorld, px, py, pz), 1, 0, 0, 0, 0);
                }

                // Kolumna dymu z centrum
                endWorld.spawnParticle(Particle.LARGE_SMOKE,
                        center.clone().add(0, frac * 18, 0),
                        (int)(3 + frac * 8), 0.5, 0.3, 0.5, 0.01);

                // END_ROD strzelające w górę z centrum
                if (t % 4 == 0) {
                    endWorld.spawnParticle(Particle.END_ROD,
                            center.clone().add(rng(-1, 1), 0, rng(-1, 1)),
                            3, 0.1, 0, 0.1, 0.4 + frac * 0.6);
                }

                // Pioruny narastające w rytmie
                if (t % 15 == 0) {
                    int bolts = (int)(1 + frac * 3);
                    for (int i = 0; i < bolts; i++) {
                        endWorld.strikeLightningEffect(
                                center.clone().add(rng(-10, 10), 0, rng(-10, 10)));
                    }
                    playSoundAll(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f + (float) frac * 0.5f, 0.9f);
                }

                if (t == 0) playSoundAll(Sound.BLOCK_PORTAL_AMBIENT, 1.5f, 0.5f);
                if (t % 30 == 0) playSoundAll(Sound.BLOCK_PORTAL_TRIGGER, 1f, 0.6f + (float) frac * 0.4f);

                if (t == 40) {
                    titleAll(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "☠ Smok sie przebudza... ☠",
                            ChatColor.GRAY + "Przygotuj sie na walke", 5, 50, 10);
                }

                lockCameras(cam, center.clone().add(0, 20 + frac * 15, 0));

            // ── Faza 2: Materializacja (100 – 179) ────────────────────────────
            } else if (t < ANIM_CHARGE_TICKS + ANIM_APPEAR_TICKS) {
                int local = t - ANIM_CHARGE_TICKS;
                double frac = (double) local / ANIM_APPEAR_TICKS;

                Location smokCenter = dragonSpawn.clone().add(0, -frac * 8, 0);

                // Czarne chmury materializacji
                endWorld.spawnParticle(Particle.SMOKE, smokCenter, 40, 4, 1.5, 4, 0.04);
                endWorld.spawnParticle(Particle.SQUID_INK, smokCenter, 10, 3, 1, 3, 0.02);
                endWorld.spawnParticle(Particle.SOUL_FIRE_FLAME, smokCenter, 12, 3, 1, 3, 0.03);

                // Zwężający się vortex portalu
                double r = 8 - frac * 7;
                for (int i = 0; i < 24; i++) {
                    double angle = 2 * Math.PI * i / 24 + t * 0.3;
                    double px = Math.cos(angle) * r;
                    double pz = Math.sin(angle) * r;
                    endWorld.spawnParticle(Particle.PORTAL,
                            new Location(endWorld, px, smokCenter.getY() + rng(-1.5, 1.5), pz),
                            1, 0, 0, 0, 0.4);
                }

                if (local == 0) {
                    playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5f, 0.5f);
                    playSoundAll(Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
                    for (int i = 0; i < 10; i++) {
                        endWorld.strikeLightningEffect(
                                center.clone().add(rng(-8, 8), 0, rng(-8, 8)));
                    }
                    endWorld.spawnParticle(Particle.EXPLOSION_EMITTER,
                            center.clone().add(0, 10, 0), 4, 2, 1, 2, 0);
                    titleAll(ChatColor.DARK_RED + "" + ChatColor.BOLD + "☠ SMOK KRESU ☠",
                            ChatColor.RED + "Wyrywa sie z mroku...", 5, 60, 10);
                }

                if (local % 18 == 0) playSoundAll(Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1.8f, 0.7f);

                lockCameras(cam, smokCenter);

            // ── Faza 3: MEGA WYBUCH + spawn smoka (180 – 239) ────────────────
            } else if (t < totalTicks) {
                int local = t - ANIM_CHARGE_TICKS - ANIM_APPEAR_TICKS;

                if (local == 0) {
                    spawnDragonEntity();

                    // ── UDERZENIE PIERWSZE: centralny rdzeń wybuchu ──
                    endWorld.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 15f, 0.4f);
                    endWorld.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 15f, 0.6f);
                    endWorld.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 15f, 0.8f);
                    endWorld.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 12f, 0.5f);
                    playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 5f, 0.5f);

                    // Centralny super-wybuch
                    endWorld.spawnParticle(Particle.EXPLOSION_EMITTER,
                            center.clone().add(0, 10, 0), 30, 0, 0, 0, 0);
                    endWorld.spawnParticle(Particle.FLASH,
                            center.clone().add(0, 10, 0), 15, 0, 0, 0, 0, Color.WHITE);

                    // Ogromna kula ognia i dymu w centrum
                    endWorld.spawnParticle(Particle.FLAME,
                            center.clone().add(0, 10, 0), 800, 12, 6, 12, 0.6);
                    endWorld.spawnParticle(Particle.LARGE_SMOKE,
                            center.clone().add(0, 10, 0), 600, 10, 5, 10, 0.4);
                    endWorld.spawnParticle(Particle.CLOUD,
                            center.clone().add(0, 15, 0), 400, 15, 8, 15, 0.5);
                    endWorld.spawnParticle(Particle.SQUID_INK,
                            center.clone().add(0, 10, 0), 300, 8, 4, 8, 0.3);

                    // Fala uderzeniowa — 3 koncentryczne pierścienie eksplozji
                    for (int ring = 1; ring <= 3; ring++) {
                        double rr = ring * 7;
                        int points = ring * 12;
                        for (int i = 0; i < points; i++) {
                            double angle = 2 * Math.PI * i / points;
                            double bx = Math.cos(angle) * rr;
                            double bz = Math.sin(angle) * rr;
                            endWorld.spawnParticle(Particle.EXPLOSION_EMITTER,
                                    center.clone().add(bx, 8 + rng(-3, 6), bz), 3, 0, 0, 0, 0);
                        }
                    }

                    // 25 piorunów naraz
                    for (int i = 0; i < 25; i++) {
                        endWorld.strikeLightningEffect(
                                center.clone().add(rng(-25, 25), 0, rng(-25, 25)));
                    }
                }

                // ── Fale rozchodzące się co tick (od 1 do 20) ──
                if (local >= 1 && local <= 20) {
                    double waveRadius = local * 6.5;   // 6.5 bloków / tick ≈ szybka fala
                    double waveY      = 64 + local * 1.2;
                    int wavePoints    = 20 + local * 3;

                    for (int i = 0; i < wavePoints; i++) {
                        double angle = 2 * Math.PI * i / wavePoints;
                        double wx = Math.cos(angle) * waveRadius;
                        double wz = Math.sin(angle) * waveRadius;

                        endWorld.spawnParticle(Particle.EXPLOSION_EMITTER,
                                new Location(endWorld, wx, waveY + rng(-4, 4), wz),
                                2, 0, 0, 0, 0);
                        endWorld.spawnParticle(Particle.FLAME,
                                new Location(endWorld, wx, waveY, wz),
                                8, 1, 2, 1, 0.3);
                        endWorld.spawnParticle(Particle.LARGE_SMOKE,
                                new Location(endWorld, wx, waveY + 2, wz),
                                6, 1, 1, 1, 0.1);
                    }

                    // Słupy ognia strzelające w górę z fali
                    if (local % 3 == 0) {
                        for (int i = 0; i < 8; i++) {
                            double angle = 2 * Math.PI * i / 8 + local * 0.4;
                            double cx = Math.cos(angle) * waveRadius * 0.85;
                            double cz = Math.sin(angle) * waveRadius * 0.85;
                            endWorld.spawnParticle(Particle.FLAME,
                                    new Location(endWorld, cx, waveY, cz),
                                    25, 0.5, 4, 0.5, 0.1);
                            endWorld.spawnParticle(Particle.END_ROD,
                                    new Location(endWorld, cx, waveY, cz),
                                    10, 0.3, 3, 0.3, 0.15);
                        }
                    }

                    // Dźwięki fali (oddalające się eksplozje)
                    if (local % 3 == 0) {
                        endWorld.playSound(
                                new Location(endWorld, Math.cos(local) * waveRadius, waveY,
                                        Math.sin(local) * waveRadius),
                                Sound.ENTITY_GENERIC_EXPLODE, 8f, 0.6f + local * 0.02f);
                    }
                }

                // Ciągły dym i ogień w centrum przez całą fazę
                if (local < 55) {
                    double decay = 1.0 - local / 55.0;
                    endWorld.spawnParticle(Particle.FLAME,
                            center.clone().add(0, 10 + local * 0.5, 0),
                            (int)(60 * decay), 6 * decay, 3, 6 * decay, 0.2);
                    endWorld.spawnParticle(Particle.LARGE_SMOKE,
                            center.clone().add(0, 12 + local * 0.6, 0),
                            (int)(40 * decay), 5 * decay, 2, 5 * decay, 0.1);
                }

                // Dragon breath gdy smok jest widoczny
                if (dragon != null && dragon.isValid()) {
                    endWorld.spawnParticle(Particle.DRAGON_BREATH,
                            dragon.getLocation().add(0, 2, 0), 30, 5, 2, 5, 0.06);
                }

                // Odliczanie na ekranie
                int secondsLeft = (int) Math.ceil((ANIM_REVEAL_TICKS - local) / 20.0);
                if (secondsLeft > 0) {
                    titleAll(ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚔ Smok Kresu ⚔",
                            ChatColor.YELLOW + "Walka zaczyna sie za: " + ChatColor.RED + secondsLeft + "s",
                            0, 25, 5);
                }

                if (local == 5) playSoundAll(Sound.ENTITY_ENDER_DRAGON_AMBIENT, 3f, 0.8f);

                lockCameras(cam, dragon != null && dragon.isValid()
                        ? dragon.getLocation() : center.clone().add(0, 15, 0));

            // ── Koniec animacji ──────────────────────────────────────────────
            } else {
                animTask.cancel();
                animTask = null;
                cleanupTempEntities();
                finishAnimation();
            }

        }, 1L, 1L);
    }

    private void spawnDragonEntity() {
        for (EnderDragon d : endWorld.getEntitiesByClass(EnderDragon.class)) {
            d.remove();
        }
        Location spawnLoc = new Location(endWorld, 0, 80, 0);
        dragon = (EnderDragon) endWorld.spawnEntity(spawnLoc, EntityType.ENDER_DRAGON);
        // Nie zmieniamy MAX_HEALTH — smok ma domyślne 200 HP i pełne AI.
        // DragonListener zeruje każdy incoming damage, więc smok faktycznie nie traci HP.
        // Zabijamy go ręcznie gdy plugin-side damage osiągnie DRAGON_TRACKED_HP.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (dragon != null && dragon.isValid()) {
                dragon.setPhase(EnderDragon.Phase.CIRCLING);
            }
        }, 5L);
    }

    private void finishAnimation() {
        // Przywróć graczy
        for (Map.Entry<UUID, PlayerState> entry : savedStates.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) continue;
            PlayerState st = entry.getValue();
            p.setGameMode(st.gm());
            p.teleport(st.loc());
            p.setAllowFlight(st.allowFlight());
            p.setFlying(st.flying());
        }
        savedStates.clear();

        fightActive.set(true);
        setupScoreboard();
        startScoreboardTask();

        broadcastAll(ChatColor.DARK_RED + "" + ChatColor.BOLD +
                "[SMOK] " + ChatColor.RED + "Walka rozpoczeta! " +
                ChatColor.GRAY + "Zadaj jak najwiecej obrazen!");
        titleAll(ChatColor.RED + "" + ChatColor.BOLD + "⚔ WALKA! ⚔",
                ChatColor.GRAY + "Zadaj jak najwiecej obrazen Smokowi!",
                10, 60, 20);
        playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 1f);
    }

    // ─── Rejestracja obrażeń ────────────────────────────────────────────────────────

    public void registerDamage(Player player, double damage) {
        if (!fightActive.get() || dragon == null || !dragon.isValid()) return;

        double totalSoFar = damageMap.merge(player.getUniqueId(), damage, Double::sum);
        double globalTotal = damageMap.values().stream().mapToDouble(Double::doubleValue).sum();

        // Zaktualizuj HP smoka w scoreboardzie (nie jego prawdziwe MC HP)
        updateScoreboard();

        // Gdy suma damage osiągnie próg → smok ginie
        if (globalTotal >= DRAGON_TRACKED_HP) {
            if (dragon.isValid()) {
                endWorld.spawnParticle(Particle.EXPLOSION_EMITTER,
                        dragon.getLocation(), 5, 2, 1, 2, 0);
                dragon.setHealth(0); // wymuszamy śmierć MC → wywoła EntityDeathEvent
            }
        }
    }

    public double getTotalDamageDealt() {
        return damageMap.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double getTrackedMaxHp() { return DRAGON_TRACKED_HP; }

    // ─── Śmierć smoka ──────────────────────────────────────────────────────────────

    public void onDragonDeath() {
        if (!fightActive.get()) return;
        fightActive.set(false);

        if (scoreboardTask != null) { scoreboardTask.cancel(); scoreboardTask = null; }

        UUID winnerUUID = damageMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        Player winner = winnerUUID != null ? Bukkit.getPlayer(winnerUUID) : null;
        double topDmg = winnerUUID != null ? damageMap.getOrDefault(winnerUUID, 0.0) : 0;

        broadcastAll(ChatColor.DARK_RED + "" + ChatColor.BOLD +
                "[SMOK] " + ChatColor.GOLD + "Smok Kresu zostal pokonany!");
        sendTopSummary();

        if (winner != null) {
            broadcastAll(ChatColor.GOLD + "" + ChatColor.BOLD +
                    "Zwyciezca: " + ChatColor.YELLOW + winner.getName() +
                    ChatColor.GRAY + " (" + String.format("%.0f", topDmg) + " dmg)");
            titleAll(
                    ChatColor.GOLD + "" + ChatColor.BOLD + "⚔ " + winner.getName() + " wygral! ⚔",
                    ChatColor.GRAY + "Zadal " + String.format("%.0f", topDmg) + " obrazen Smokowi",
                    10, 80, 20);
            playSoundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.9f);
            giveWinnerRewards(winner);
        }

        Bukkit.getScheduler().runTaskLater(plugin, this::restoreScoreboards, 100L);
        damageMap.clear();
        dragon = null;
        endWorld = null;
    }

    private void sendTopSummary() {
        List<Map.Entry<UUID, Double>> top = getTopEntries(10);
        broadcastAll(ChatColor.DARK_PURPLE + "--- Ranking damage ---");
        int rank = 1;
        for (Map.Entry<UUID, Double> e : top) {
            broadcastAll(ChatColor.GRAY + "" + rank + ". " +
                    ChatColor.YELLOW + playerName(e.getKey()) +
                    ChatColor.GRAY + " \u2014 " + ChatColor.RED +
                    String.format("%.0f", e.getValue()) + " dmg");
            rank++;
        }
    }

    private void giveWinnerRewards(Player winner) {
        Random random = new Random();
        List<ItemStack> rewards = new ArrayList<>();

        // Pula dostępnych dropów (bez legendaryck)
        List<ItemStack> availableDrops = new ArrayList<>();
        availableDrops.add(createDiamondHelmet());
        availableDrops.add(createDiamondChestplate());
        availableDrops.add(createNetheriteSpear());
        availableDrops.add(createDiamondLeggings());
        availableDrops.add(createDiamondBoots());
        availableDrops.add(new ItemStack(Material.END_CRYSTAL, 16));
        availableDrops.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4 + random.nextInt(5))); // 4-8
        availableDrops.add(new ItemStack(Material.MACE, 1));
        availableDrops.add(new ItemStack(Material.GOLDEN_APPLE, 16 + random.nextInt(17))); // 16-32
        availableDrops.add(new ItemStack(Material.RESPAWN_ANCHOR, 16));

        // Wylosuj 4 różne losowe itemy
        Collections.shuffle(availableDrops);
        for (int i = 0; i < Math.min(4, availableDrops.size()); i++) {
            rewards.add(availableDrops.get(i));
        }

        // Dodaj 100% jeden z dwóch eventowych przedmiotów (50/50 szansa)
        ItemStack legendary;
        if (random.nextBoolean()) {
            legendary = createDragonClaw(); // Smoczy Szpon
        } else {
            legendary = createDragonBelly(); // Brzuszek Smoka
        }
        rewards.add(legendary);

        // Dodaj wszystkie nagrody do ekwipunku gracza
        for (ItemStack reward : rewards) {
            // Spróbuj dodać do inventory
            HashMap<Integer, ItemStack> leftover = winner.getInventory().addItem(reward);
            
            // Jeśli inventory jest pełny, drop na ziemię
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    winner.getWorld().dropItemNaturally(winner.getLocation(), item);
                }
            }
        }

        winner.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD +
                "⚔ Gratulacje! ⚔");
        winner.sendMessage(ChatColor.GREEN + "Otrzymales " + ChatColor.WHITE + rewards.size() +
                ChatColor.GREEN + " nagrod za pokonanie Smoka Kresu!");
        winner.sendMessage(ChatColor.GRAY + "(" + ChatColor.YELLOW + "4 losowe itemy" +
                ChatColor.GRAY + " + " + ChatColor.LIGHT_PURPLE + "1 legendarka" + ChatColor.GRAY + ")");
    }

    // ─── Scoreboard ─────────────────────────────────────────────────────────────────

    private void setupScoreboard() {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = scoreboard.registerNewObjective("smok", Criteria.DUMMY,
                ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚔ Smok Kresu ⚔");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        updateScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) p.setScoreboard(scoreboard);
    }

    private void updateScoreboard() {
        if (scoreboard == null || objective == null) return;
        for (String entry : new HashSet<>(scoreboard.getEntries())) scoreboard.resetScores(entry);

        double dealt = Math.min(getTotalDamageDealt(), DRAGON_TRACKED_HP);
        double remaining = Math.max(0, DRAGON_TRACKED_HP - dealt);
        double pct = (1.0 - dealt / DRAGON_TRACKED_HP) * 100;

        String hpLine = ChatColor.RED + "HP: " + ChatColor.GOLD +
                String.format("%.0f", remaining) + ChatColor.GRAY + "/" +
                (int) DRAGON_TRACKED_HP + " " + ChatColor.DARK_GRAY +
                "(" + String.format("%.1f", pct) + "%)";
        objective.getScore(hpLine).setScore(10);
        objective.getScore(ChatColor.DARK_GRAY + "").setScore(9);

        List<Map.Entry<UUID, Double>> top = getTopEntries(3);
        String[] medals = {ChatColor.GOLD + "1.", ChatColor.GRAY + "2.", ChatColor.DARK_RED + "3."};
        if (top.isEmpty()) {
            objective.getScore(ChatColor.GRAY + "Brak obrazen").setScore(8);
        } else {
            for (int i = 0; i < top.size(); i++) {
                String line = medals[i] + " " + ChatColor.YELLOW + playerName(top.get(i).getKey()) +
                        ChatColor.GRAY + " " + String.format("%.0f", top.get(i).getValue());
                objective.getScore(line).setScore(8 - i);
            }
        }
    }

    private void startScoreboardTask() {
        if (scoreboardTask != null) scoreboardTask.cancel();
        scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!fightActive.get()) return;
            updateScoreboard();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getScoreboard() != scoreboard) p.setScoreboard(scoreboard);
            }
        }, 4L, 4L);
    }

    private void restoreScoreboards() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getScoreboard() == scoreboard) p.setScoreboard(main);
        }
    }

    // ─── Shutdown ───────────────────────────────────────────────────────────────────

    public void shutdown() {
        fightActive.set(false);
        if (animTask != null) { animTask.cancel(); animTask = null; }
        if (scoreboardTask != null) { scoreboardTask.cancel(); scoreboardTask = null; }
        restoreScoreboards();
        // Przywróć graczy jeśli w trakcie animacji
        for (Map.Entry<UUID, PlayerState> entry : savedStates.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                PlayerState st = entry.getValue();
                p.setGameMode(st.gm());
                p.teleport(st.loc());
                p.setAllowFlight(st.allowFlight());
                p.setFlying(st.flying());
            }
        }
        savedStates.clear();
        cleanupTempEntities();
    }

    // ─── Pomocnicze ─────────────────────────────────────────────────────────────────

    private void broadcastAll(String msg) { Bukkit.broadcastMessage(msg); }

    private void titleAll(String title, String sub, int in, int stay, int out) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendTitle(title, sub, in, stay, out);
    }

    private void playSoundAll(Sound sound, float vol, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers())
            p.playSound(p.getLocation(), sound, vol, pitch);
    }

    private void lockCameras(Location cam, Location target) {
        lookAt(cam, target);
        for (UUID id : savedStates.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && p.getGameMode() == GameMode.SPECTATOR)
                p.teleport(cam);
        }
    }

    private void lookAt(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        if (dir.lengthSquared() > 0) from.setDirection(dir.normalize());
    }

    private void drawParticleLine(Location a, Location b, Particle particle, int points) {
        Vector step = b.toVector().subtract(a.toVector()).multiply(1.0 / points);
        Location cur = a.clone();
        for (int i = 0; i < points; i++) {
            cur.add(step);
            endWorld.spawnParticle(particle, cur, 1, 0, 0, 0, 0);
        }
    }

    private void cleanupTempEntities() {
        for (Entity e : tempEntities) if (e.isValid()) e.remove();
        tempEntities.clear();
    }

    private List<Map.Entry<UUID, Double>> getTopEntries(int limit) {
        return damageMap.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String playerName(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    private double rng(double min, double max) {
        return min + Math.random() * (max - min);
    }

    // ─── GUI Smoka ──────────────────────────────────────────────────────────────────

    /**
     * Tworzy GUI informacji o Smoku Kresu.
     * 6 linijek (54 sloty), read-only, z rotating itemem w slocie 40.
     */
    public Inventory buildDragonGui() {
        DragonGuiHolder holder = new DragonGuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, "§5§lInformacje o smoku");
        holder.setInventory(inv);

        // Black Stained Glass Pane jako ramka
        ItemStack bsgp = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());

        // Linia 1: BSGP + Informacje w środku (slot 4)
        for (int i = 0; i <= 3; i++) inv.setItem(i, bsgp);
        inv.setItem(4, createDragonInfoItem());
        for (int i = 5; i <= 8; i++) inv.setItem(i, bsgp);

        // Linia 2: BSGP na bokach (sloty 9-17)
        inv.setItem(9, bsgp);
        inv.setItem(17, bsgp);

        // Linia 3: BSGP na bokach + zbroja i spear (sloty 18-26)
        inv.setItem(18, bsgp);
        inv.setItem(20, createDiamondHelmet());
        inv.setItem(21, createDiamondChestplate());
        inv.setItem(22, createNetheriteSpear());
        inv.setItem(23, createDiamondLeggings());
        inv.setItem(24, createDiamondBoots());
        inv.setItem(26, bsgp);

        // Linia 4: BSGP + przedmioty (sloty 27-35)
        inv.setItem(27, bsgp);
        // Slot 28 - pusty (usunięto BSGP)
        inv.setItem(29, createItem(Material.END_CRYSTAL, "§fEnd Crystal", Arrays.asList("§7Ilość: §e16"), 16));
        inv.setItem(30, createEnchantedGoldenApple());
        inv.setItem(31, createItem(Material.MACE, "§fMace", Collections.emptyList()));
        inv.setItem(32, createGoldenApple());
        inv.setItem(33, createItem(Material.RESPAWN_ANCHOR, "§fRespawn Anchor", Arrays.asList("§7Ilość: §e16"), 16));
        // Slot 34 - pusty (usunięto BSGP)
        inv.setItem(35, bsgp);

        // Linia 5: BSGP + rotating slot (40) + puste (sloty 36-44)
        inv.setItem(36, bsgp);
        // Slot 40 - rotating item (będzie zmieniany przez listener)
        inv.setItem(40, createDragonBelly()); // Domyślnie pierwszy item (Brzuszek Smoka)
        inv.setItem(44, bsgp);

        // Linia 6: BSGP (sloty 45-53)
        for (int i = 45; i <= 53; i++) inv.setItem(i, bsgp);

        return inv;
    }

    // ─── Tworzenie itemów dla GUI ──────────────────────────────────────────────────

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        return createItem(mat, name, lore, 1);
    }

    private ItemStack createItem(Material mat, String name, List<String> lore, int amount) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Slot 4: Enderman Spawn Egg - Informacje o smoku
     */
    private ItemStack createDragonInfoItem() {
        ItemStack item = new ItemStack(Material.ENDERMAN_SPAWN_EGG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Informacje o smoku");

            List<String> lore = new ArrayList<>();
            lore.add(""); // Pusta linia
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.DARK_PURPLE + "Smok " + ChatColor.GRAY + "przylatuje dnia:");
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.GREEN + "20.06.2026 " + ChatColor.GRAY + "o godzinie " + ChatColor.GREEN + "18:00");
            lore.add(""); // Pusta linia
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "Najcenniejsze dropy:" + ChatColor.DARK_PURPLE + ChatColor.BOLD + " Smoczy Szpon");
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "Oraz " + ChatColor.DARK_PURPLE + ChatColor.BOLD + "Brzuszek " + ChatColor.LIGHT_PURPLE + ChatColor.BOLD + "Smoka");
            lore.add(""); // Pusta linia
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "Na oba przedmioty szansa wynosi: " + ChatColor.RED + "50%");
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "Wszystko zależy od " + ChatColor.GREEN + "Szczęścia!");
            lore.add(""); // Pusta linia

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Slot 20: Diamond Helmet z enchantami
     */
    public ItemStack createDiamondHelmet() {
        ItemStack item = new ItemStack(Material.DIAMOND_HELMET);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.PROTECTION, 4, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.RESPIRATION, 3, true);
            meta.addEnchant(Enchantment.AQUA_AFFINITY, 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Slot 21: Diamond Chestplate z enchantami
     */
    public ItemStack createDiamondChestplate() {
        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.PROTECTION, 4, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Slot 22: Netherite Spear (custom item representation)
     */
    public ItemStack createNetheriteSpear() {
        ItemStack item = new ItemStack(Material.NETHERITE_SPEAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5§lNetherite Spear");
            meta.addEnchant(Enchantment.SHARPNESS, 5, true);
            meta.addEnchant(Enchantment.LOOTING, 3, true);
            meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            // Lunge enchant (Minecraft 1.21+) - może nie być dostępny w starszych wersjach
            try {
                Enchantment lunge = Enchantment.getByKey(NamespacedKey.minecraft("lunge"));
                if (lunge != null) meta.addEnchant(lunge, 3, true);
            } catch (Exception ignored) {}
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Slot 23: Diamond Leggings z enchantami
     */
    public ItemStack createDiamondLeggings() {
        ItemStack item = new ItemStack(Material.DIAMOND_LEGGINGS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.PROTECTION, 4, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Slot 24: Diamond Boots z enchantami
     */
    public ItemStack createDiamondBoots() {
        ItemStack item = new ItemStack(Material.DIAMOND_BOOTS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.PROTECTION, 4, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
            meta.addEnchant(Enchantment.DEPTH_STRIDER, 3, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Slot 30: Enchanted Golden Apple (4-8 sztuk)
     */
    private ItemStack createEnchantedGoldenApple() {
        ItemStack item = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 6); // Średnia z 4-8
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList("§7Szansa na drop: §e4-8 sztuk"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Slot 32: Golden Apple (16-32 sztuk)
     */
    private ItemStack createGoldenApple() {
        ItemStack item = new ItemStack(Material.GOLDEN_APPLE, 24); // Średnia z 16-32
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList("§7Szansa na drop: §e16-32 sztuk"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Slot 40 (rotating): Shulker Shell - Brzuszek Smoka
     */
    public ItemStack createDragonBelly() {
        ItemStack item = new ItemStack(Material.SHULKER_SHELL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Brzuszek " +
                              ChatColor.LIGHT_PURPLE + ChatColor.BOLD + "Smoka");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "Posiadając ten przedmiot w");
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "ekwipunku po " + ChatColor.RED + "zabiciu" + ChatColor.GRAY + " dowolnego");
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.WHITE + "gracza " + ChatColor.GRAY + "jego przedmioty trafiają");
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "do " + ChatColor.DARK_PURPLE + "Brzuszka " + ChatColor.LIGHT_PURPLE + "Smoka");
            lore.add(""); // Pusta linia
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.GOLD + "Kliknij PPM, aby otworzyć brzuszek!");

            meta.setCustomModelData(5006); // CMD_DRAGON_BELLY
            meta.setLore(lore);
            
            // Dodaj persistent data ID
            meta.getPersistentDataContainer().set(
                legendaryItems.getIdKey(),
                PersistentDataType.STRING,
                LegendaryItems.DRAGON_BELLY
            );
            
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Slot 40 (rotating): Netherite Sword - Smoczy Szpon
     */
    public ItemStack createDragonClaw() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Smoczy Szpon");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "Jest to miecz zdobyty z");
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.DARK_PURPLE + "Smoka kresu " + ChatColor.WHITE + "(20.06.2026)");
            lore.add(""); // Pusta linia
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "Każde uderzenie zwiększa");
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "Obrażenia o " + ChatColor.WHITE + "5% " + ChatColor.GRAY + "(aż do " + ChatColor.WHITE + "50%" + ChatColor.GRAY + ")");
            lore.add(ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "Otrzymanie obrażeń resetuje " + ChatColor.WHITE + "%");
            lore.add(""); // Pusta linia

            meta.setCustomModelData(5007); // CMD_DRAGON_CLAW
            meta.setLore(lore);
            
            // Dodaj persistent data ID
            meta.getPersistentDataContainer().set(
                legendaryItems.getIdKey(),
                PersistentDataType.STRING,
                LegendaryItems.DRAGON_CLAW
            );
            
            item.setItemMeta(meta);
        }
        return item;
    }

    public ZamrozeniePlugin getPlugin() {
        return plugin;
    }
}
