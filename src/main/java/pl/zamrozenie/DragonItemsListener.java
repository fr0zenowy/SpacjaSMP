package pl.zamrozenie;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener dla przedmiotów ze Smoka Kresu:
 * - Ostrze Kresu (Rift Blade) - aktywna zdolność: fala uderzeniowa
 * - Dimensional Shroud - pasywny efekt: teleport przy 20% HP
 */
public class DragonItemsListener implements Listener {

    private final ZamrozeniePlugin plugin;
    private final DragonManager dragonManager;

    // Cooldowny dla graczy (UUID -> timestamp końca cooldownu w ms)
    private final Map<UUID, Long> riftBladeCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dimensionalShroudCooldowns = new ConcurrentHashMap<>();

    // Cooldown w milisekundach
    private static final long RIFT_BLADE_COOLDOWN_MS = 120_000L;      // 2 minuty
    private static final long DIMENSIONAL_SHROUD_COOLDOWN_MS = 120_000L;  // 2 minuty

    // Parametry Ostrza Kresu
    private static final double RIFT_BLADE_RANGE = 10.0;  // 10 bloków zasięgu
    private static final double RIFT_BLADE_DAMAGE = 10.0;  // 5 serc (10.0 HP)
    private static final int RIFT_BLADE_SLOWNESS_DURATION = 60; // 3 sekundy (w tickach)

    // Parametry Dimensional Shroud
    private static final double SHROUD_HEALTH_THRESHOLD = 0.2; // 20% HP
    private static final double SHROUD_TELEPORT_RADIUS = 15.0; // 15 bloków
    private static final int SHROUD_INVISIBILITY_DURATION = 80; // 4 sekundy (w tickach)

    public DragonItemsListener(ZamrozeniePlugin plugin, DragonManager dragonManager) {
        this.plugin = plugin;
        this.dragonManager = dragonManager;
    }

    // ─── Ostrze Kresu - Aktywna zdolność (PPM) ─────────────────────────────────

    @EventHandler
    public void onRiftBladeUse(PlayerInteractEvent event) {
        // Tylko prawy przycisk myszy
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Tylko główna ręka
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        // Sprawdź czy to Ostrze Kresu
        if (!isRiftBlade(item)) return;

        event.setCancelled(true);

        // Sprawdź cooldown
        long now = System.currentTimeMillis();
        Long cooldownEnd = riftBladeCooldowns.get(player.getUniqueId());
        if (cooldownEnd != null && now < cooldownEnd) {
            long remainingSeconds = (cooldownEnd - now) / 1000;
            player.sendMessage(ChatColor.RED + "Ostrze Kresu jest na cooldownie! " +
                ChatColor.GRAY + "Pozostało: " + ChatColor.WHITE + remainingSeconds + "s");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return;
        }

        // Uruchom zdolność
        activateRiftBlade(player);

        // Ustaw cooldown
        riftBladeCooldowns.put(player.getUniqueId(), now + RIFT_BLADE_COOLDOWN_MS);
    }

    private void activateRiftBlade(Player player) {
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection().normalize();
        World world = player.getWorld();

        // Efekty dźwiękowe i wizualne na starcie
        player.playSound(origin, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 1.2f);
        player.playSound(origin, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);
        player.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "⚔ " +
            ChatColor.LIGHT_PURPLE + "Rozpłatanie Przestrzeni!");

        // Animacja fali - 10 bloków w przód
        new BukkitRunnable() {
            double distance = 0;
            final Set<UUID> hitEntities = new HashSet<>();

            @Override
            public void run() {
                if (distance >= RIFT_BLADE_RANGE) {
                    cancel();
                    return;
                }

                // Aktualna pozycja fali
                Location waveLoc = origin.clone().add(direction.clone().multiply(distance));

                // Sprawdź czy fala nie trafiła w blok
                Block block = waveLoc.getBlock();
                if (block.getType().isSolid() && !block.isPassable()) {
                    // Eksplozja przy trafieniu w ścianę
                    world.spawnParticle(Particle.EXPLOSION, waveLoc, 3, 0.3, 0.3, 0.3, 0);
                    world.playSound(waveLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                    cancel();
                    return;
                }

                // Wizualizacja fali - fioletowa, pozioma fala
                drawWaveSlice(waveLoc, direction);

                // Sprawdź kolizje z mobami i graczami
                Collection<Entity> nearbyEntities = world.getNearbyEntities(waveLoc, 1.5, 1.5, 1.5);
                for (Entity entity : nearbyEntities) {
                    if (!(entity instanceof LivingEntity)) continue;
                    if (entity.equals(player)) continue;
                    if (hitEntities.contains(entity.getUniqueId())) continue;

                    LivingEntity target = (LivingEntity) entity;

                    // Zadaj obrażenia - bezpośrednio odejmij HP (ignoruje zbroję)
                    double currentHealth = target.getHealth();
                    double newHealth = Math.max(0, currentHealth - RIFT_BLADE_DAMAGE);
                    target.setHealth(newHealth);
                    
                    // Uruchom damage event dla logów i innych pluginów
                    target.damage(0.0, player);

                    // Nałóż spowolnienie
                    target.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, RIFT_BLADE_SLOWNESS_DURATION, 1, false, true, true));

                    // Efekty wizualne trafienia
                    Location hitLoc = target.getLocation().add(0, 1, 0);
                    world.spawnParticle(Particle.DAMAGE_INDICATOR, hitLoc, 10, 0.3, 0.3, 0.3, 0.1);
                    world.spawnParticle(Particle.PORTAL, hitLoc, 30, 0.5, 0.5, 0.5, 1.0);
                    world.playSound(hitLoc, Sound.ENTITY_PLAYER_HURT, 0.7f, 0.8f);

                    hitEntities.add(entity.getUniqueId());

                    if (target instanceof Player targetPlayer) {
                        targetPlayer.sendMessage(ChatColor.DARK_PURPLE + player.getName() +
                            ChatColor.GRAY + " użył " + ChatColor.LIGHT_PURPLE + "Ostrza Kresu" +
                            ChatColor.GRAY + "!");
                    }
                }

                distance += 0.5;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawWaveSlice(Location center, Vector direction) {
        World world = center.getWorld();

        // Oblicz wektory prostopadłe do kierunku lotu fali (dla siatki 5x5)
        // Wektor poziomy (right) - prostopadły do kierunku ruchu w płaszczyźnie XZ
        Vector right = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        // Wektor pionowy (up) - zawsze w górę
        Vector up = new Vector(0, 1, 0);

        // Rozmiar siatki 5x5 - każda kratka oddalona o 0.5 bloku
        int gridSize = 5;
        double spacing = 0.5; // odstęp między cząsteczkami
        double halfSize = (gridSize - 1) * spacing / 2.0; // 1.0 blok od środka do krawędzi

        // Rysuj siatkę 5x5 cząsteczek
        for (int x = 0; x < gridSize; x++) {
            for (int y = 0; y < gridSize; y++) {
                // Oblicz przesunięcie od środka siatki
                double offsetX = (x * spacing) - halfSize;
                double offsetY = (y * spacing) - halfSize;

                // Pozycja cząsteczki w przestrzeni 3D
                Vector offset = right.clone().multiply(offsetX)
                    .add(up.clone().multiply(offsetY));

                Location particleLoc = center.clone().add(offset);

                // Fioletowe cząsteczki - głównie PORTAL i ENCHANT
                world.spawnParticle(Particle.PORTAL, particleLoc, 2, 0.02, 0.02, 0.02, 0.1);
                world.spawnParticle(Particle.ENCHANT, particleLoc, 1, 0.01, 0.01, 0.01, 0.01);
                
                // Dodatkowe cząsteczki dla efektu (tylko w rogach i środku)
                if ((x == 0 || x == gridSize - 1) && (y == 0 || y == gridSize - 1)) {
                    // Rogi - więcej SOUL_FIRE_FLAME particles
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 1, 0, 0, 0, 0);
                } else if (x == gridSize / 2 && y == gridSize / 2) {
                    // Środek - dodatkowy REVERSE_PORTAL
                    world.spawnParticle(Particle.REVERSE_PORTAL, particleLoc, 1, 0.01, 0.01, 0.01, 0.05);
                }
            }
        }

        // Dźwięk co kilka ticków
        if (Math.random() < 0.3) {
            world.playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.3f, 1.5f);
        }
    }

    // ─── Dimensional Shroud - Pasywny efekt (teleport przy 20% HP) ─────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Sprawdź czy gracz ma Dimensional Shroud w ekwipunku
        if (!hasDimensionalShroud(player)) return;

        // Oblicz HP po obrażeniach
        double currentHealth = player.getHealth();
        double damage = event.getFinalDamage();
        double healthAfter = currentHealth - damage;
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();

        // Sprawdź czy HP spadnie poniżej 20%
        if (healthAfter / maxHealth > SHROUD_HEALTH_THRESHOLD) return;

        // Sprawdź cooldown
        long now = System.currentTimeMillis();
        Long cooldownEnd = dimensionalShroudCooldowns.get(player.getUniqueId());
        if (cooldownEnd != null && now < cooldownEnd) {
            // Nie aktywuj - cooldown aktywny
            return;
        }

        // Aktywuj Dimensional Shroud
        activateDimensionalShroud(player);

        // Ustaw cooldown
        dimensionalShroudCooldowns.put(player.getUniqueId(), now + DIMENSIONAL_SHROUD_COOLDOWN_MS);
    }

    private void activateDimensionalShroud(Player player) {
        Location currentLoc = player.getLocation();
        World world = player.getWorld();

        // Znajdź bezpieczne miejsce w promieniu 15 bloków
        Location safeLoc = findSafeLocation(player, SHROUD_TELEPORT_RADIUS);

        if (safeLoc == null) {
            // Nie znaleziono bezpiecznego miejsca
            player.sendMessage(ChatColor.YELLOW + "Dimensional Shroud nie może znaleźć bezpiecznego miejsca!");
            return;
        }

        // Efekty przed teleportacją
        world.spawnParticle(Particle.PORTAL, currentLoc.clone().add(0, 1, 0), 100, 0.3, 0.5, 0.3, 2.0);
        world.spawnParticle(Particle.REVERSE_PORTAL, currentLoc.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.5);
        world.playSound(currentLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

        // Teleportacja
        player.teleport(safeLoc);

        // Efekty po teleportacji
        world.spawnParticle(Particle.PORTAL, safeLoc.clone().add(0, 1, 0), 100, 0.3, 0.5, 0.3, 2.0);
        world.spawnParticle(Particle.REVERSE_PORTAL, safeLoc.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.5);
        world.spawnParticle(Particle.ENCHANTED_HIT, safeLoc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);
        world.playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        world.playSound(safeLoc, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);

        // Dodaj niewidzialność na 4 sekundy
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.INVISIBILITY, SHROUD_INVISIBILITY_DURATION, 0, false, false, true));

        // Wiadomość
        player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✦ " +
            ChatColor.GRAY + "Dimensional Shroud uratował cię przed śmiercią!");
        player.playSound(safeLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    private Location findSafeLocation(Player player, double radius) {
        Location origin = player.getLocation();
        World world = player.getWorld();
        Random random = new Random();

        // Spróbuj 20 razy znaleźć bezpieczne miejsce
        for (int attempt = 0; attempt < 20; attempt++) {
            // Losowa pozycja w promieniu
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 5 + random.nextDouble() * (radius - 5); // Min 5 bloków od gracza

            double offsetX = Math.cos(angle) * distance;
            double offsetZ = Math.sin(angle) * distance;

            Location testLoc = origin.clone().add(offsetX, 0, offsetZ);

            // Znajdź najbliższą bezpieczną powierzchnię (w dół/w górę)
            testLoc = findGroundLevel(testLoc, world);

            if (testLoc != null && isSafeLocation(testLoc)) {
                return testLoc;
            }
        }

        return null; // Nie znaleziono bezpiecznego miejsca
    }

    private Location findGroundLevel(Location loc, World world) {
        // Szukaj gruntu w dół (max 10 bloków)
        for (int i = 0; i < 10; i++) {
            Location testLoc = loc.clone().subtract(0, i, 0);
            Block ground = testLoc.getBlock();
            Block above = testLoc.clone().add(0, 1, 0).getBlock();
            Block above2 = testLoc.clone().add(0, 2, 0).getBlock();

            if (ground.getType().isSolid() && 
                !above.getType().isSolid() && 
                !above2.getType().isSolid()) {
                return testLoc.add(0, 1, 0); // Gracz stanie na gruncie
            }
        }

        // Szukaj gruntu w górę (max 10 bloków)
        for (int i = 1; i < 10; i++) {
            Location testLoc = loc.clone().add(0, i, 0);
            Block ground = testLoc.getBlock();
            Block above = testLoc.clone().add(0, 1, 0).getBlock();
            Block above2 = testLoc.clone().add(0, 2, 0).getBlock();

            if (ground.getType().isSolid() && 
                !above.getType().isSolid() && 
                !above2.getType().isSolid()) {
                return testLoc.add(0, 1, 0);
            }
        }

        return null;
    }

    private boolean isSafeLocation(Location loc) {
        Block block = loc.getBlock();
        Block below = loc.clone().subtract(0, 1, 0).getBlock();
        Block above = loc.clone().add(0, 1, 0).getBlock();

        // Musi być grunt pod nogami
        if (!below.getType().isSolid()) return false;

        // Nie może być lawy, ognia lub wody
        Material belowType = below.getType();
        if (belowType == Material.LAVA || belowType == Material.FIRE || belowType == Material.WATER) {
            return false;
        }

        // Przestrzeń nad głową musi być wolna
        if (block.getType().isSolid() || above.getType().isSolid()) return false;

        return true;
    }

    // ─── Pomocnicze metody ──────────────────────────────────────────────────────

    private boolean isRiftBlade(ItemStack item) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD) return false;
        if (!item.hasItemMeta()) return false;

        String displayName = item.getItemMeta().getDisplayName();
        return displayName != null && 
               displayName.contains("Ostrze Kresu");
    }

    private boolean hasDimensionalShroud(Player player) {
        // Sprawdź czy gracz ma Dimensional Shroud w ekwipunku
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() != Material.ECHO_SHARD) continue;
            if (!item.hasItemMeta()) continue;

            String displayName = item.getItemMeta().getDisplayName();
            if (displayName != null && displayName.contains("Dimensional Shroud")) {
                return true;
            }
        }
        return false;
    }
}