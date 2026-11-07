package pl.zamrozenie;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Zarzadza calym systemem zrzutow:
 *  - harmonogram codziennego zrzutu o wyznaczonej godzinie (czas Polski),
 *  - animacja spadania skrzyni (tryb spectator, kazdy gracz oglada),
 *  - 15-minutowa faza blokady z bossbarem (kordy + odliczanie),
 *  - 3 zagadki w GUI (kto pierwszy rozwiaze, ten zgarnia loot),
 *  - 3 zagadki w GUI (kto pierwszy rozwiaze, ten zgarnia loot z /drop),
 */
public class ZrzutManager {

    public enum Phase { IDLE, ANIMATION, LOCKED, OPEN }

    private static final ZoneId PL_ZONE = ZoneId.of("Europe/Warsaw");
    private static final long LOCK_DURATION_MS = 15L * 60L * 1000L; // 15 minut
    /** Zrzut moze wyladowac tylko w tym kwadracie (X i Z). */
    private static final int DROP_BOUNDS_MIN = -2000;
    private static final int DROP_BOUNDS_MAX = 2000;

    private final ZamrozeniePlugin plugin;
    private final LifestealManager lifesteal;
    private final LegendaryItems legendary;

    private File dataFile;
    private org.bukkit.configuration.file.FileConfiguration data;

    // ─── Harmonogram ───────────────────────────────────────────────────────────────
    private boolean scheduleEnabled = false;
    private int scheduledHour = -1;
    private int scheduledMinute = -1;
    private LocalDate lastTriggerDate = null;
    private BukkitTask schedulerTask;

    // ─── Aktywny zrzut ───────────────────────────────────────────────────────────────
    private Phase phase = Phase.IDLE;
    private Location crateLoc;
    private long openAtMillis;
    private final AtomicBoolean claimed = new AtomicBoolean(false);
    private UUID winner;
    private BossBar bossBar;
    private BukkitTask lockTask;
    private ArmorStand crateEntity;
    private BukkitTask animationTask;
    private final List<org.bukkit.entity.Entity> animationEntities = new ArrayList<>();

    /** Zapisany stan graczy na czas animacji (do przywrocenia). */
    private final Map<UUID, PlayerState> savedStates = new HashMap<>();
    /** Postep w zagadkach: UUID -> indeks aktualnej zagadki (0..3). 3 = ukonczono. */
    private final Map<UUID, Integer> puzzleProgress = new HashMap<>();

    private record PlayerState(Location loc, GameMode mode, boolean allowFlight, boolean flying) {}

    public ZrzutManager(ZamrozeniePlugin plugin, LifestealManager lifesteal, LegendaryItems legendary) {
        this.plugin = plugin;
        this.lifesteal = lifesteal;
        this.legendary = legendary;

        dataFile = new File(plugin.getDataFolder(), "zrzut.yml");
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        data = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);
        load();

        startScheduler();
    }

    public Phase getPhase()            { return phase; }
    public Location getCrateLoc()      { return crateLoc; }
    public boolean isClaimed()         { return claimed.get(); }
    public LegendaryItems getLegendary(){ return legendary; }
    public boolean isScheduleEnabled() { return scheduleEnabled; }
    public int getScheduledHour()      { return scheduledHour; }
    public int getScheduledMinute()    { return scheduledMinute; }

    // ─── Harmonogram ───────────────────────────────────────────────────────────────

    public void setScheduledTime(int hour, int minute) {
        this.scheduledHour = hour;
        this.scheduledMinute = minute;
        this.scheduleEnabled = true;
        this.lastTriggerDate = null; // pozwol wystrzelic dzis jesli godzina jeszcze nie minela
        save();
    }

    public void disableSchedule() {
        this.scheduleEnabled = false;
        save();
    }

    private void startScheduler() {
        if (schedulerTask != null) schedulerTask.cancel();
        schedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkSchedule, 100L, 20L);
    }

    private void checkSchedule() {
        if (!scheduleEnabled || scheduledHour < 0) return;
        if (phase != Phase.IDLE) return; // nie nakladaj zrzutow

        ZonedDateTime now = ZonedDateTime.now(PL_ZONE);
        LocalDate today = now.toLocalDate();
        if (today.equals(lastTriggerDate)) return; // dzis juz bylo

        LocalTime target = LocalTime.of(scheduledHour, scheduledMinute);
        // Wystrzel gdy aktualna godzina dorownala/przekroczyla zaplanowana (z marginesem 1 min)
        if (!now.toLocalTime().isBefore(target) && now.toLocalTime().isBefore(target.plusMinutes(1))) {
            lastTriggerDate = today;
            triggerDrop(null);
        }
    }

    // ─── Wystrzelenie zrzutu ─────────────────────────────────────────────────────────

    /** Rozpoczyna zrzut. Jesli juz aktywny – czysci poprzedni. */
    public void triggerDrop(Player initiator) {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            if (initiator != null) initiator.sendMessage(ChatColor.RED + "Brak graczy online – zrzut anulowany.");
            return;
        }
        cleanup();

        Location landing = pickLandingLocation();
        if (landing == null) {
            if (initiator != null) initiator.sendMessage(ChatColor.RED + "Nie znaleziono miejsca na zrzut.");
            return;
        }
        this.crateLoc = landing;
        startAnimation(landing);
    }

    private Location pickLandingLocation() {
        World world = Bukkit.getWorlds().get(0);
        if (world == null) return null;

        Random rng = new Random();

        // Preferuj odlegle kordy (daleko od 0,0) — nawet niezaladowane chunki
        for (int attempt = 0; attempt < 50; attempt++) {
            int x = randomDropCoord(rng);
            int z = randomDropCoord(rng);
            Location loc = landingAt(world, x, z);
            if (loc != null) return loc;
        }

        // Ostateczna proba: losuj tylko maksymalnie daleko
        for (int attempt = 0; attempt < 20; attempt++) {
            int x = randomDropCoordEdge(rng);
            int z = randomDropCoordEdge(rng);
            Location loc = landingAt(world, x, z);
            if (loc != null) return loc;
        }

        return landingAt(world, 0, 0);
    }

    /**
     * Losuje wspolrzedna X/Z z przewaga na odlegle wartosci.
     * ~85%: |coord| >= 700 (daleko od spawnu), reszta: caly zakres.
     */
    private int randomDropCoord(Random rng) {
        if (rng.nextDouble() < 0.85) {
            return randomDropCoordFar(rng, 700);
        }
        return DROP_BOUNDS_MIN + rng.nextInt(DROP_BOUNDS_MAX - DROP_BOUNDS_MIN + 1);
    }

    /** Odlegla wspolrzedna — im dalej od srodka, tym wieksza szansa (w strone +/-2000). */
    private int randomDropCoordFar(Random rng, int minAbs) {
        minAbs = Math.min(minAbs, DROP_BOUNDS_MAX);
        int sign = rng.nextBoolean() ? 1 : -1;
        double u = rng.nextDouble();
        // Potega < 1 = wiecej wartosci blisko maksimum (krawedz mapy)
        u = Math.pow(u, 0.4);
        int abs = minAbs + (int) (u * (DROP_BOUNDS_MAX - minAbs));
        abs = Math.min(abs, DROP_BOUNDS_MAX);
        return sign * abs;
    }

    /** Skraj mapy: |coord| zwykle 1500–2000. */
    private int randomDropCoordEdge(Random rng) {
        int sign = rng.nextBoolean() ? 1 : -1;
        int abs = 1500 + rng.nextInt(DROP_BOUNDS_MAX - 1500 + 1);
        return sign * abs;
    }

    private boolean isInDropZone(int x, int z) {
        return x >= DROP_BOUNDS_MIN && x <= DROP_BOUNDS_MAX
                && z >= DROP_BOUNDS_MIN && z <= DROP_BOUNDS_MAX;
    }

    private Location landingAt(World world, int x, int z) {
        if (!isInDropZone(x, z)) return null;
        world.getChunkAt(x >> 4, z >> 4).load(true);
        int y = world.getHighestBlockYAt(x, z);
        Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
        Material below = loc.getBlock().getType();
        if (below.isAir() || !below.isSolid()) return loc;
        return null;
    }

    // ─── Animacja spadania ───────────────────────────────────────────────────────────

    private static final int FALL_TICKS   = 55; // ~2.75s spadania meteoru
    private static final int REVEAL_TICKS  = 60; // 3s pokazywania skrzyni po uderzeniu

    private void startAnimation(Location landing) {
        phase = Phase.ANIMATION;
        World world = landing.getWorld();

        // Stala pozycja kamery, kierunek bedzie sledzil spadajaca skrzynie
        final Location cam = landing.clone().add(9.0, 7.0, 9.0);

        // Zapisz stan i przenies graczy do trybu widza
        for (Player p : Bukkit.getOnlinePlayers()) {
            savedStates.put(p.getUniqueId(),
                    new PlayerState(p.getLocation().clone(), p.getGameMode(), p.getAllowFlight(), p.isFlying()));
            p.setGameMode(GameMode.SPECTATOR);
            lookAt(cam, landing.clone().add(0, 14, 0));
            p.teleport(cam);
        }

        // Skrzynia jako maly armor stand z chestem na glowie (rdzen meteoru)
        Location start = landing.clone().add(0, 30, 0);
        crateEntity = world.spawn(start, ArmorStand.class, as -> {
            as.setSmall(true);
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setInvulnerable(true);
            as.getEquipment().setHelmet(new ItemStack(Material.CHEST));
        });

        final double startY = start.getY();
        final double endY = landing.getY();
        final Location crateCur = landing.clone();
        crateCur.setY(startY);
        final int[] tick = {0};

        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int t = tick[0]++;

            if (t < FALL_TICKS) {
                // ── faza spadania ──
                double progress = (double) t / FALL_TICKS;
                crateCur.setY(startY - (startY - endY) * progress);
                if (crateEntity != null && crateEntity.isValid()) crateEntity.teleport(crateCur);

                // ogon meteoru
                world.spawnParticle(Particle.FLAME, crateCur, 15, 0.3, 0.3, 0.3, 0.02);
                world.spawnParticle(Particle.LARGE_SMOKE, crateCur, 10, 0.3, 0.5, 0.3, 0.01);
                world.spawnParticle(Particle.LAVA, crateCur, 2, 0.2, 0.2, 0.2, 0);

                // pioruny walace w okolice meteoru
                if (t % 11 == 0) {
                    Location bolt = crateCur.clone().add(rand(-3, 3), 0, rand(-3, 3));
                    world.strikeLightningEffect(bolt);
                }
                // tridenty lecace w skrzynie
                if (t % 13 == 0) spawnTridentAt(world, crateCur);

                if (t % 6 == 0) world.playSound(landing, Sound.ENTITY_PHANTOM_FLAP, 1.4f, 0.6f);
                lockCameras(cam, crateCur);

            } else if (t == FALL_TICKS) {
                // ── uderzenie meteoru: skrzynia + bialy blysk ──
                if (crateEntity != null && crateEntity.isValid()) { crateEntity.remove(); crateEntity = null; }
                clearAnimationEntities();
                placeCrateBlock(landing);
                meteorImpactFlash(world, landing, cam);
                lockCameras(cam, landing.clone().add(0.5, 0.5, 0.5));

            } else if (t < FALL_TICKS + REVEAL_TICKS) {
                // ── pokazywanie skrzyni ──
                if (t % 10 == 0)
                    world.spawnParticle(Particle.END_ROD, landing.clone().add(0.5, 1, 0.5), 6, 0.2, 0.4, 0.2, 0.01);
                lockCameras(cam, landing.clone().add(0.5, 0.5, 0.5));

            } else {
                if (animationTask != null) { animationTask.cancel(); animationTask = null; }
                finishAnimation();
            }
        }, 1L, 1L);
    }

    /** Trzyma kamere kazdego widza w jednym miejscu i obraca ja na cel (blokada ruchu kamery). */
    private void lockCameras(Location cam, Location target) {
        lookAt(cam, target);
        for (UUID id : savedStates.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && p.getGameMode() == GameMode.SPECTATOR) {
                p.teleport(cam);
            }
        }
    }

    /** Ustawia kierunek lokacji 'from' tak, by patrzyla na 'to'. */
    private void lookAt(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        from.setDirection(dir);
    }

    private void spawnTridentAt(World world, Location target) {
        Location spawn = target.clone().add(rand(-9, 9), 9 + rand(0, 4), rand(-9, 9));
        org.bukkit.entity.Trident tr = world.spawn(spawn, org.bukkit.entity.Trident.class);
        tr.setVelocity(target.toVector().subtract(spawn.toVector()).normalize().multiply(2.6));
        tr.setDamage(0);
        tr.setGlowing(true);
        tr.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
        animationEntities.add(tr);
    }

    private void meteorImpactFlash(World world, Location landing, Location cam) {
        // pioruny dajace silny rozblysk ekranu
        for (int i = 0; i < 5; i++) {
            world.strikeLightningEffect(landing.clone().add(rand(-2, 2), 0, rand(-2, 2)));
        }
        // bialy blysk (Paper 1.21+ wymaga Color przy FLASH)
        world.spawnParticle(Particle.FLASH, landing, 8, 0, 0, 0, 0, Color.WHITE);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, landing, 2);
        world.spawnParticle(Particle.CLOUD, landing, 80, 2, 0.5, 2, 0.1);
        world.playSound(landing, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 5f, 1f);
        world.playSound(landing, Sound.ENTITY_GENERIC_EXPLODE, 5f, 0.7f);

        // bialy blysk bezposrednio na ekranie kazdego widza
        for (UUID id : savedStates.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                Location flashLoc = cam.clone().add(cam.getDirection().multiply(2));
                p.spawnParticle(Particle.FLASH, flashLoc, 6, 0, 0, 0, 0, Color.WHITE);
            }
        }
    }

    private void clearAnimationEntities() {
        for (org.bukkit.entity.Entity e : animationEntities) {
            if (e != null && e.isValid()) e.remove();
        }
        animationEntities.clear();
    }

    private double rand(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private void finishAnimation() {
        restoreAllPlayers();

        String coords = formatCoords(crateLoc);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(
                    ChatColor.GOLD + "" + ChatColor.BOLD + "Zrzut Spadl!",
                    ChatColor.GRAY + "Kordynaty: " + ChatColor.GREEN + coords,
                    10, 70, 20
            );
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[ZRZUT] " +
                ChatColor.YELLOW + "Zrzut spadl na kordach " + ChatColor.GREEN + coords +
                ChatColor.YELLOW + "! Masz 15 minut, by do niego dobiec.");

        startLockPhase();
    }

    private void restoreAllPlayers() {
        for (Map.Entry<UUID, PlayerState> e : savedStates.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && p.isOnline()) {
                PlayerState st = e.getValue();
                p.setGameMode(st.mode());
                p.teleport(st.loc());
                p.setAllowFlight(st.allowFlight());
                p.setFlying(st.flying());
            }
        }
        savedStates.clear();
    }

    private void placeCrateBlock(Location loc) {
        Block b = loc.getBlock();
        b.setType(Material.CHEST);
    }

    // ─── Faza blokady (bossbar) ──────────────────────────────────────────────────────

    private void startLockPhase() {
        phase = Phase.LOCKED;
        claimed.set(false);
        winner = null;
        openAtMillis = System.currentTimeMillis() + LOCK_DURATION_MS;

        bossBar = Bukkit.createBossBar(ChatColor.GOLD + "Zrzut", BarColor.RED, BarStyle.SEGMENTED_10);
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);
        bossBar.setVisible(true);

        lockTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long remaining = openAtMillis - System.currentTimeMillis();
            if (remaining <= 0) {
                openDrop(null);
                return;
            }
            double progress = Math.max(0, Math.min(1, (double) remaining / LOCK_DURATION_MS));
            bossBar.setProgress(progress);
            bossBar.setTitle(ChatColor.GOLD + "Zrzut " + ChatColor.GRAY + "| Kordy: " +
                    ChatColor.GREEN + formatCoords(crateLoc) +
                    ChatColor.GRAY + " | Otwarcie za: " + ChatColor.YELLOW + formatMMSS(remaining));
        }, 20L, 20L);
    }

    /** Otwiera zrzut (po odliczeniu lub recznie przez admina). */
    public void openDrop(Player admin) {
        if (phase != Phase.LOCKED) {
            if (admin != null) admin.sendMessage(ChatColor.RED + "Nie ma zablokowanego zrzutu do otwarcia.");
            return;
        }
        phase = Phase.OPEN;
        if (lockTask != null) { lockTask.cancel(); lockTask = null; }
        if (bossBar != null) {
            bossBar.setColor(BarColor.GREEN);
            bossBar.setProgress(1.0);
            bossBar.setTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "Zrzut otwarty! " +
                    ChatColor.GRAY + "Kordy: " + ChatColor.GREEN + formatCoords(crateLoc) +
                    ChatColor.GRAY + " — rozwiaz zagadki!");
        }
        Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "[ZRZUT] " +
                ChatColor.YELLOW + "Zrzut zostal otwarty! Kliknij skrzynie PPM i rozwiaz zagadki, kto pierwszy ten lepszy!");
        for (Player p : Bukkit.getOnlinePlayers())
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
    }

    public long getRemainingLockMs() {
        return Math.max(0, openAtMillis - System.currentTimeMillis());
    }

    // ─── Interakcja ze skrzynia ──────────────────────────────────────────────────────

    public boolean isCrateBlock(Block block) {
        return crateLoc != null && block != null
                && block.getWorld().equals(crateLoc.getWorld())
                && block.getX() == crateLoc.getBlockX()
                && block.getY() == crateLoc.getBlockY()
                && block.getZ() == crateLoc.getBlockZ();
    }

    /** Obsluguje PPM na skrzyni zrzutu. */
    public void handleCrateClick(Player player) {
        if (phase == Phase.LOCKED) {
            player.sendMessage(ChatColor.RED + "Zrzut jest jeszcze zablokowany! Otwarcie za " +
                    ChatColor.YELLOW + formatMMSS(getRemainingLockMs()) + ChatColor.RED + ".");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
            return;
        }
        if (phase != Phase.OPEN) return;

        if (claimed.get()) {
            notifyZrzutAlreadyTaken(player);
            return;
        }
        int idx = puzzleProgress.getOrDefault(player.getUniqueId(), 0);
        openPuzzle(player, idx);
    }

    // ─── Zagadki (GUI) ───────────────────────────────────────────────────────────────

    public void openPuzzle(Player player, int index) {
        if (claimed.get()) {
            notifyZrzutAlreadyTaken(player);
            return;
        }
        puzzleProgress.put(player.getUniqueId(), index);
        Inventory inv;
        switch (index) {
            case 0 -> inv = buildPuzzle1();
            case 1 -> inv = buildPuzzle2();
            case 2 -> inv = buildPuzzle3();
            default -> { tryClaim(player); return; }
        }
        player.openInventory(inv);
    }

    /** Obsluguje klikniecie w GUI zagadki. Zwraca true gdy klik byl istotny. */
    public void handlePuzzleClick(Player player, ZrzutPuzzleHolder holder, int slot, ItemStack clicked) {
        if (claimed.get()) {
            notifyZrzutAlreadyTaken(player);
            return;
        }
        if (clicked == null || clicked.getType() == Material.AIR) return;
        Material mat = clicked.getType();
        if (mat == Material.GRAY_STAINED_GLASS_PANE || mat == Material.KNOWLEDGE_BOOK) return;

        Inventory inv = holder.getInventory();

        switch (holder.getPuzzleType()) {
            case 1 -> {
                if (mat == Material.LIME_STAINED_GLASS_PANE) return; // juz zaliczona liczba
                Integer val = holder.getP1SlotValue().get(slot);
                if (val == null) return;
                int expected = holder.getP1Expected().get(holder.getP1Progress());
                if (val == expected) {
                    inv.setItem(slot, solvedMarker());
                    holder.setP1Progress(holder.getP1Progress() + 1);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
                    if (holder.getP1Progress() >= holder.getP1Expected().size()) {
                        puzzleSolved(player);
                    }
                } else {
                    // reset – odtworz wszystkie liczby
                    for (Map.Entry<Integer, Integer> e : holder.getP1SlotValue().entrySet()) {
                        inv.setItem(e.getKey(), numberItem(e.getValue()));
                    }
                    holder.setP1Progress(0);
                    player.sendMessage(ChatColor.RED + "Zla kolejnosc! Liczymy od nowa.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            }
            case 2, 3 -> {
                if (slot == holder.getCorrectSlot()) {
                    puzzleSolved(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Niepoprawnie! Sprobuj jeszcze raz.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            }
        }
    }

    private ItemStack solvedMarker() {
        ItemStack it = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "OK");
        it.setItemMeta(m);
        return it;
    }

    /** Wywolywane po poprawnym rozwiazaniu aktualnej zagadki. */
    public void puzzleSolved(Player player) {
        if (claimed.get()) {
            notifyZrzutAlreadyTaken(player);
            return;
        }
        int idx = puzzleProgress.getOrDefault(player.getUniqueId(), 0) + 1;
        puzzleProgress.put(player.getUniqueId(), idx);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        if (idx >= 3) {
            tryClaim(player);
        } else {
            player.sendMessage(ChatColor.GREEN + "Zagadka " + idx + "/3 rozwiazana!");
            Bukkit.getScheduler().runTask(plugin, () -> openPuzzle(player, idx));
        }
    }

    private void notifyZrzutAlreadyTaken(Player player) {
        Player w = winner != null ? Bukkit.getPlayer(winner) : null;
        player.sendMessage(ChatColor.RED + "Zrzut juz zostal otwarty" +
                (w != null ? " przez " + ChatColor.YELLOW + w.getName() : "") +
                ChatColor.RED + ". Nie mozesz otworzyc skrzyni.");
        player.closeInventory();
    }

    /** Zamyka GUI zagadek u wszystkich oprócz zwyciezcy. */
    private void closeOtherPuzzleViews(Player winnerPlayer) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(winnerPlayer)) continue;
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof ZrzutPuzzleHolder) {
                notifyZrzutAlreadyTaken(p);
            }
        }
    }

    private void tryClaim(Player player) {
        if (!claimed.compareAndSet(false, true)) {
            notifyZrzutAlreadyTaken(player);
            return;
        }
        winner = player.getUniqueId();
        player.closeInventory();
        closeOtherPuzzleViews(player);

        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[ZRZUT] " +
                ChatColor.YELLOW + player.getName() + ChatColor.GREEN +
                " rozwiazal wszystkie zagadki i zgarnal zrzut!");
        for (Player p : Bukkit.getOnlinePlayers())
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);

        endDrop();
        Bukkit.getScheduler().runTask(plugin, () -> openLootChest(player));
    }

    // ─── Skrzynia z lootem ───────────────────────────────────────────────────────────

    /** Otwiera GUI skrzyni z lootem z /drop — zwyciezca sam wyciaga przedmioty. */
    private void openLootChest(Player player) {
        if (!player.isOnline()) return;
        if (winner == null || !player.getUniqueId().equals(winner)) {
            notifyZrzutAlreadyTaken(player);
            return;
        }
        ZrzutLootHolder holder = new ZrzutLootHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                ChatColor.GOLD + "" + ChatColor.BOLD + "✦ Skrzynia Zrzutu ✦");
        holder.setInventory(inv);

        List<ItemStack> loot = generateLoot();
        List<Integer> freeSlots = new ArrayList<>();
        for (int i = 0; i < 27; i++) freeSlots.add(i);
        Collections.shuffle(freeSlots);
        int idx = 0;
        for (ItemStack it : loot) {
            if (idx < freeSlots.size()) inv.setItem(freeSlots.get(idx++), it);
            else inv.addItem(it);
        }

        player.openInventory(inv);
        player.sendMessage(ChatColor.GOLD + "Otworzyles skrzynie zrzutu! Wyciagnij loot — " +
                ChatColor.YELLOW + "po zamknieciu reszta trafi do ekwipunku.");
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    private List<ItemStack> generateLoot() {
        DropManager drops = plugin.getDropManager();
        if (drops == null) return new ArrayList<>();
        List<ItemStack> loot = drops.rollRegularDrops(DropSource.ZRZUT);
        Collections.shuffle(loot);
        if (loot.size() > 27) loot = new ArrayList<>(loot.subList(0, 27));
        return loot;
    }

    // ─── /zrzut – status na czacie (drop jest w /drop) ───────────────────────────────

    public void sendStatus(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Zrzut");
        if (scheduleEnabled && scheduledHour >= 0) {
            player.sendMessage(ChatColor.GRAY + ShopManager.ARROW + " Codziennie o "
                    + ChatColor.YELLOW + String.format("%02d:%02d", scheduledHour, scheduledMinute)
                    + ChatColor.GRAY + " (PL)");
        } else {
            player.sendMessage(ChatColor.GRAY + ShopManager.ARROW + " Godzina zrzutu: "
                    + ChatColor.RED + "nieustawiona");
        }
        switch (phase) {
            case ANIMATION -> player.sendMessage(ChatColor.GRAY + ShopManager.ARROW + " "
                    + ChatColor.AQUA + "Zrzut właśnie spada!");
            case LOCKED -> player.sendMessage(ChatColor.GRAY + ShopManager.ARROW + " Aktywny! Otwarcie za: "
                    + ChatColor.YELLOW + formatMMSS(getRemainingLockMs()));
            case OPEN -> player.sendMessage(ChatColor.GRAY + ShopManager.ARROW + " "
                    + ChatColor.GREEN + "Otwarty — rozwiąż zagadki!"
                    + (claimed.get() ? ChatColor.RED + " (zgarnięty)" : ""));
            default -> player.sendMessage(ChatColor.GRAY + ShopManager.ARROW + " Brak aktywnego zrzutu.");
        }
        player.sendMessage(ChatColor.GRAY + ShopManager.ARROW + " Drop zobaczysz w "
                + ChatColor.WHITE + "/drop" + ChatColor.GRAY + " → Zrzut");
        player.sendMessage("");
    }

    private void endDrop() {
        if (crateLoc != null) {
            crateLoc.getBlock().setType(Material.AIR);
            crateLoc.getWorld().spawnParticle(Particle.CLOUD, crateLoc, 30, 0.5, 0.5, 0.5, 0.05);
        }
        if (bossBar != null) { bossBar.removeAll(); bossBar = null; }
        if (lockTask != null) { lockTask.cancel(); lockTask = null; }
        puzzleProgress.clear();
        phase = Phase.IDLE;
        crateLoc = null;
        // claimed + winner zostaja do nastepnego zrzutu (kto pierwszy, ten losuje)
    }

    // ─── Budowanie GUI zagadek ───────────────────────────────────────────────────────

    private ItemStack infoBook(String title, List<String> lore) {
        ItemStack book = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + title);
        meta.setLore(lore);
        book.setItemMeta(meta);
        return book;
    }

    private ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(" ");
        it.setItemMeta(m);
        return it;
    }

    /** Zagadka 1 – posortuj 5 liczb rosnaco. */
    private Inventory buildPuzzle1() {
        ZrzutPuzzleHolder holder = new ZrzutPuzzleHolder(1);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_GRAY + "Zrzut — Zagadka");
        holder.setInventory(inv);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler());

        inv.setItem(4, infoBook("Zagadka 1/3", Arrays.asList(
                ChatColor.GRAY + "Klikaj liczby w kolejnosci",
                ChatColor.GRAY + "od " + ChatColor.GREEN + "najmniejszej" + ChatColor.GRAY + " do " +
                        ChatColor.RED + "najwiekszej" + ChatColor.GRAY + ".",
                "",
                ChatColor.YELLOW + "Pomylka = liczenie od nowa."
        )));

        // 5 unikalnych liczb 1..9
        List<Integer> values = new ArrayList<>();
        Random rng = new Random();
        while (values.size() < 5) {
            int v = 1 + rng.nextInt(9);
            if (!values.contains(v)) values.add(v);
        }
        List<Integer> expected = new ArrayList<>(values);
        Collections.sort(expected);
        holder.setP1Expected(expected);

        int[] slots = {10, 11, 12, 13, 14};
        List<Integer> shuffled = new ArrayList<>(values);
        Collections.shuffle(shuffled);
        for (int i = 0; i < slots.length; i++) {
            int slot = slots[i];
            int val = shuffled.get(i);
            holder.getP1SlotValue().put(slot, val);
            inv.setItem(slot, numberItem(val));
        }
        return inv;
    }

    private ItemStack numberItem(int value) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, value));
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Liczba: " + value);
        it.setItemMeta(m);
        return it;
    }

    /** Zagadka 2 – kliknij blok rozniacy sie od pozostalych. */
    private Inventory buildPuzzle2() {
        ZrzutPuzzleHolder holder = new ZrzutPuzzleHolder(2);
        Inventory inv = Bukkit.createInventory(holder, 45, ChatColor.DARK_GRAY + "Zrzut — Zagadka");
        holder.setInventory(inv);
        for (int i = 0; i < 45; i++) inv.setItem(i, filler());

        inv.setItem(4, infoBook("Zagadka 2/3", Arrays.asList(
                ChatColor.GRAY + "Jeden blok rozni sie od reszty.",
                ChatColor.GRAY + "Kliknij " + ChatColor.GREEN + "ten inny" + ChatColor.GRAY + " blok.",
                "",
                ChatColor.YELLOW + "Pomylka = brak postepu."
        )));

        int[] grid = {18,19,20,21,22,23,24,25,26, 27,28,29,30,31,32,33,34,35};
        Random rng = new Random();
        int oddSlot = grid[rng.nextInt(grid.length)];
        holder.setCorrectSlot(oddSlot);

        ItemStack common = namedBlock(Material.COBBLESTONE, ChatColor.GRAY + "Kamien Bruk");
        ItemStack odd    = namedBlock(Material.STONE, ChatColor.GRAY + "Kamien");
        for (int slot : grid) inv.setItem(slot, slot == oddSlot ? odd : common.clone());
        return inv;
    }

    /** Zagadka 3 – dopasuj kolor welny do wzoru. */
    private Inventory buildPuzzle3() {
        ZrzutPuzzleHolder holder = new ZrzutPuzzleHolder(3);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_GRAY + "Zrzut — Zagadka");
        holder.setInventory(inv);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler());

        Material[] wools = {
                Material.RED_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL, Material.YELLOW_WOOL,
                Material.PURPLE_WOOL, Material.ORANGE_WOOL, Material.PINK_WOOL
        };
        String[] names = { "Czerwona", "Niebieska", "Zielona", "Zolta", "Fioletowa", "Pomaranczowa", "Rozowa" };

        Random rng = new Random();
        int targetIdx = rng.nextInt(wools.length);

        inv.setItem(4, infoBook("Zagadka 3/3", Arrays.asList(
                ChatColor.GRAY + "Kliknij welne w kolorze:",
                ChatColor.GREEN + "" + ChatColor.BOLD + names[targetIdx],
                "",
                ChatColor.YELLOW + "Pomylka = brak postepu."
        )));

        // ulozenie 7 welien w slotach 10..16
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < wools.length; i++) order.add(i);
        Collections.shuffle(order);
        for (int i = 0; i < slots.length; i++) {
            int woolIdx = order.get(i);
            inv.setItem(slots[i], namedBlock(wools[woolIdx], ChatColor.GRAY + names[woolIdx] + " welna"));
            if (woolIdx == targetIdx) holder.setCorrectSlot(slots[i]);
        }
        return inv;
    }

    private ItemStack namedBlock(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        it.setItemMeta(m);
        return it;
    }

    // ─── Sprzatanie ──────────────────────────────────────────────────────────────────

    /** Pelne zamkniecie – anuluje takze stale taski (przy wylaczeniu pluginu). */
    public void shutdown() {
        if (schedulerTask != null) { schedulerTask.cancel(); schedulerTask = null; }
        cleanup();
    }

    public void cleanup() {
        if (animationTask != null) { animationTask.cancel(); animationTask = null; }
        if (lockTask != null) { lockTask.cancel(); lockTask = null; }
        if (crateEntity != null && crateEntity.isValid()) { crateEntity.remove(); crateEntity = null; }
        clearAnimationEntities();
        if (!savedStates.isEmpty()) restoreAllPlayers();
        if (bossBar != null) { bossBar.removeAll(); bossBar = null; }
        if (crateLoc != null) {
            // usun resztke skrzyni jesli zostala
            if (crateLoc.getBlock().getType() == Material.CHEST) crateLoc.getBlock().setType(Material.AIR);
        }
        puzzleProgress.clear();
        phase = Phase.IDLE;
        crateLoc = null;
        claimed.set(false);
        winner = null;
    }

    /** Awaryjne przywrocenie gracza, ktory wszedl na serwer w trakcie animacji. */
    public void restoreOnJoin(Player player) {
        PlayerState st = savedStates.remove(player.getUniqueId());
        if (st != null) {
            player.setGameMode(st.mode());
            player.teleport(st.loc());
            player.setAllowFlight(st.allowFlight());
            player.setFlying(st.flying());
        }
    }

    public void addPlayerToBossBar(Player player) {
        if (bossBar != null) bossBar.addPlayer(player);
    }

    // ─── Pomocnicze ──────────────────────────────────────────────────────────────────

    private String formatCoords(Location loc) {
        if (loc == null) return "?";
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private String formatMMSS(long millis) {
        long totalSec = millis / 1000;
        long m = totalSec / 60;
        long s = totalSec % 60;
        return String.format("%02d:%02d", m, s);
    }

    // ─── Zapis / Odczyt ──────────────────────────────────────────────────────────────

    private void load() {
        scheduleEnabled = data.getBoolean("schedule.enabled", false);
        scheduledHour   = data.getInt("schedule.hour", -1);
        scheduledMinute = data.getInt("schedule.minute", -1);
        String last = data.getString("schedule.lastTriggerDate", null);
        if (last != null) {
            try { lastTriggerDate = LocalDate.parse(last); } catch (Exception ignored) {}
        }
    }

    public void save() {
        data.set("schedule.enabled", scheduleEnabled);
        data.set("schedule.hour", scheduledHour);
        data.set("schedule.minute", scheduledMinute);
        data.set("schedule.lastTriggerDate", lastTriggerDate != null ? lastTriggerDate.toString() : null);
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().severe("Nie mozna zapisac zrzut.yml!"); }
    }
}
