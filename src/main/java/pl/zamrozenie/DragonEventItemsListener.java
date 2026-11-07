package pl.zamrozenie;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener dla eventowych przedmiotów Smoka Kresu:
 * - Brzuszek Smoka (SHULKER_SHELL) - zbiera itemy zabitych graczy
 * - Smoczy Szpon (NETHERITE_SWORD) - wzrost damage +5% per hit (max 150%)
 */
public class DragonEventItemsListener implements Listener {

    private final ZamrozeniePlugin plugin;
    private final LegendaryItems legendaryItems;

    // Mapa damage bonusów dla Smoczego Szponu (UUID gracza -> procent bonusu)
    private final Map<UUID, Integer> dragonClawDamageBonus = new ConcurrentHashMap<>();
    
    // Mapa slotów hotbara dla wyświetlania action bara
    private final Map<UUID, Integer> lastHeldSlot = new ConcurrentHashMap<>();
    
    // Mapa przechowująca itemy w Brzuszkach Smoka (UUID brzuszka -> lista itemów)
    private final Map<UUID, List<ItemStack>> bellyStorageMap = new ConcurrentHashMap<>();
    
    // Mapa śledzenia otwartych GUI (UUID brzuszka -> UUID gracza który ma otwarte)
    private final Map<UUID, UUID> openBellyGuis = new ConcurrentHashMap<>();

    public DragonEventItemsListener(ZamrozeniePlugin plugin, LegendaryItems legendaryItems) {
        this.plugin = plugin;
        this.legendaryItems = legendaryItems;
        
        // Task wyświetlający % damage nad hotbarem co 5 ticków
        startActionBarTask();
    }

    // ─── Brzuszek Smoka - PPM otwiera GUI ──────────────────────────────────────

    @EventHandler
    public void onDragonBellyUse(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        String itemId = legendaryItems.getId(item);
        if (!LegendaryItems.DRAGON_BELLY.equals(itemId)) return;

        event.setCancelled(true);
        openDragonBellyGui(player, item);
    }

    private void openDragonBellyGui(Player player, ItemStack bellyItem) {
        // Pobierz UUID brzuszka
        UUID bellyId = getBellyId(bellyItem);
        
        // Sprawdź czy ktoś inny ma otwarte to GUI
        UUID currentOpener = openBellyGuis.get(bellyId);
        if (currentOpener != null && !currentOpener.equals(player.getUniqueId())) {
            Player otherPlayer = Bukkit.getPlayer(currentOpener);
            if (otherPlayer != null && otherPlayer.isOnline()) {
                player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Ten " +
                    ChatColor.DARK_PURPLE + "Brzuszek Smoka" + ChatColor.GRAY +
                    " jest obecnie otwarty przez innego gracza!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                return;
            } else {
                // Gracz się wylogował, usuń blokadę
                openBellyGuis.remove(bellyId);
            }
        }
        
        // Pobierz zapisane itemy z mapy
        List<ItemStack> storedItems = bellyStorageMap.getOrDefault(bellyId, new ArrayList<>());

        // Stwórz GUI 6-rzędowe (54 sloty) jak shulker box
        Inventory inv = Bukkit.createInventory(new DragonBellyGuiHolder(bellyId, player.getUniqueId()), 54,
            ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Brzuszek " +
            ChatColor.LIGHT_PURPLE + ChatColor.BOLD + "Smoka");

        // Wypełnij GUI zapisanymi itemami
        for (int i = 0; i < storedItems.size() && i < 54; i++) {
            inv.setItem(i, storedItems.get(i));
        }

        // Zaznacz że GUI jest otwarte przez tego gracza
        openBellyGuis.put(bellyId, player.getUniqueId());
        
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 0.8f);
    }

    @EventHandler
    public void onDragonBellyGuiClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof DragonBellyGuiHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID bellyId = holder.getBellyId();
        
        // Zapisz aktualne itemy z GUI do mapy
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }

        bellyStorageMap.put(bellyId, items);
        
        // Zwolnij blokadę GUI
        openBellyGuis.remove(bellyId);
        
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.7f, 0.8f);
    }
    
    @EventHandler
    public void onDragonBellyGuiClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DragonBellyGuiHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        UUID bellyId = holder.getBellyId();
        UUID ownerId = holder.getOwnerId();
        
        // Weryfikuj czy gracz wciąż ma ten brzuszek w ekwipunku
        ItemStack currentBelly = findDragonBellyById(player, bellyId);
        if (currentBelly == null) {
            // Gracz nie ma już tego brzuszka w ekwipunku - zablokuj wyciąganie
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY +
                "Musisz mieć " + ChatColor.DARK_PURPLE + "Brzuszek Smoka" +
                ChatColor.GRAY + " w ekwipunku aby wyciągnąć przedmioty!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return;
        }
        
        // Zablokuj wkładanie itemów do GUI (tylko wyciąganie)
        if (event.getClickedInventory() != null &&
            event.getClickedInventory().getHolder() instanceof DragonBellyGuiHolder) {
            // Pozwól tylko na wyciąganie itemów (nie na wkładanie)
            if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) {
                event.setCancelled(true);
            }
        } else if (event.getClick().isShiftClick()) {
            // Zablokuj shift-click z ekwipunku gracza do GUI
            event.setCancelled(true);
        }
    }

    // ─── Brzuszek Smoka - zbieranie itemów po zabiciu gracza ───────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerKillWithBelly(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.equals(victim)) return;

        // Sprawdź czy killer ma Brzuszek Smoka w ekwipunku
        ItemStack bellyItem = findDragonBelly(killer);
        if (bellyItem == null) return;

        // Pobierz UUID brzuszka
        UUID bellyId = getBellyId(bellyItem);
        
        // Zbierz WSZYSTKIE itemy z ekwipunku zabitego gracza
        List<ItemStack> allItems = new ArrayList<>();
        
        // Dodaj itemy z głównego ekwipunku
        for (ItemStack item : victim.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                allItems.add(item.clone());
            }
        }
        
        // Dodaj zbroję
        for (ItemStack item : victim.getInventory().getArmorContents()) {
            if (item != null && item.getType() != Material.AIR) {
                allItems.add(item.clone());
            }
        }
        
        if (allItems.isEmpty()) return;

        // Wyczyść dropy - itemy trafiają do brzuszka
        event.getDrops().clear();

        // Dodaj itemy do brzuszka w mapie
        List<ItemStack> stored = bellyStorageMap.getOrDefault(bellyId, new ArrayList<>());
        stored.addAll(allItems);
        bellyStorageMap.put(bellyId, stored);

        // Efekty wizualne i dźwiękowe
        Location loc = victim.getLocation().add(0, 1, 0);
        World world = killer.getWorld();
        
        world.spawnParticle(Particle.PORTAL, loc, 100, 0.5, 0.5, 0.5, 1.0);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 30, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticle(Particle.REVERSE_PORTAL, loc, 50, 0.3, 0.3, 0.3, 0.3);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
        world.playSound(loc, Sound.BLOCK_ENDER_CHEST_CLOSE, 1.0f, 0.7f);

        killer.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "☠ " +
            ChatColor.GRAY + "Przedmioty " + ChatColor.WHITE + victim.getName() +
            ChatColor.GRAY + " (" + allItems.size() + " szt.) zostały pochłonięte przez " +
            ChatColor.DARK_PURPLE + "Brzuszek " + ChatColor.LIGHT_PURPLE + "Smoka" +
            ChatColor.GRAY + "!");
    }

    // ─── Smoczy Szpon - wzrost damage +5% per hit ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDragonClawHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player)) return; // Tylko PvP

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        String itemId = legendaryItems.getId(weapon);
        
        if (!LegendaryItems.DRAGON_CLAW.equals(itemId)) return;

        // Pobierz aktualny bonus
        int currentBonus = dragonClawDamageBonus.getOrDefault(attacker.getUniqueId(), 100);
        
        // Zastosuj bonus do damage
        double baseDamage = event.getDamage();
        double multiplier = currentBonus / 100.0;
        double newDamage = baseDamage * multiplier;
        event.setDamage(newDamage);

        // Zwiększ bonus o 5% (max 150%)
        int newBonus = Math.min(150, currentBonus + 5);
        dragonClawDamageBonus.put(attacker.getUniqueId(), newBonus);

        // Efekty wizualne - intensywność zależna od bonusu
        if (newBonus > 100) {
            Location hitLoc = event.getEntity().getLocation().add(0, 1, 0);
            World world = attacker.getWorld();
            
            int particleCount = (newBonus - 100) / 5; // 0-10 cząsteczek
            world.spawnParticle(Particle.ENCHANT, hitLoc, particleCount, 0.3, 0.3, 0.3, 0.1);
            world.spawnParticle(Particle.ENCHANTED_HIT, hitLoc, particleCount / 2, 0.2, 0.2, 0.2, 0.05);
            
            if (newBonus >= 150) {
                world.spawnParticle(Particle.DRAGON_BREATH, hitLoc, 15, 0.5, 0.5, 0.5, 0.02);
                world.playSound(hitLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.8f);
            }
        }
    }

    // ─── Smoczy Szpon - reset bonusu przy otrzymaniu damage ────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonClawUserDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        UUID victimId = victim.getUniqueId();
        Integer currentBonus = dragonClawDamageBonus.get(victimId);
        
        if (currentBonus != null && currentBonus > 100) {
            // Reset bonusu do 100%
            dragonClawDamageBonus.put(victimId, 100);
            
            // Efekty wizualne resetu
            Location loc = victim.getLocation().add(0, 1, 0);
            World world = victim.getWorld();
            
            world.spawnParticle(Particle.SMOKE, loc, 20, 0.3, 0.5, 0.3, 0.05);
            world.spawnParticle(Particle.ASH, loc, 15, 0.4, 0.4, 0.4, 0.02);
            world.playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.4f, 1.5f);
            
            victim.sendMessage(ChatColor.RED + "⚠ " + ChatColor.GRAY + "Bonus " +
                ChatColor.DARK_PURPLE + "Smoczego Szponu" + ChatColor.GRAY + " został zresetowany!");
        }
    }

    // ─── Action Bar - wyświetlanie % damage ────────────────────────────────────

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        lastHeldSlot.put(player.getUniqueId(), event.getNewSlot());
    }

    private void startActionBarTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack held = player.getInventory().getItemInMainHand();
                    String itemId = legendaryItems.getId(held);
                    
                    if (LegendaryItems.DRAGON_CLAW.equals(itemId)) {
                        int bonus = dragonClawDamageBonus.getOrDefault(player.getUniqueId(), 100);
                        displayDamageBonus(player, bonus);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // Co 5 ticków (0.25s)
    }

    private void displayDamageBonus(Player player, int bonus) {
        String color;
        if (bonus >= 150) {
            color = ChatColor.DARK_RED + "" + ChatColor.BOLD;
        } else if (bonus >= 130) {
            color = ChatColor.RED + "" + ChatColor.BOLD;
        } else if (bonus >= 110) {
            color = ChatColor.GOLD + "";
        } else {
            color = ChatColor.GRAY + "";
        }

        String bar = createProgressBar(bonus, 100, 150, 20);
        
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                color + "⚔ Obrażenia: " + bonus + "% " + ChatColor.DARK_GRAY + bar
            ));
    }

    private String createProgressBar(int current, int min, int max, int length) {
        int progress = (int) (((double) (current - min) / (max - min)) * length);
        progress = Math.max(0, Math.min(length, progress));
        
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < length; i++) {
            if (i < progress) {
                bar.append(ChatColor.RED).append("|");
            } else {
                bar.append(ChatColor.DARK_GRAY).append("|");
            }
        }
        bar.append(ChatColor.DARK_GRAY).append("]");
        return bar.toString();
    }

    // ─── Pomocnicze metody - Brzuszek Smoka ────────────────────────────────────

    private ItemStack findDragonBelly(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String itemId = legendaryItems.getId(item);
            if (LegendaryItems.DRAGON_BELLY.equals(itemId)) {
                return item;
            }
        }
        return null;
    }
    
    private ItemStack findDragonBellyById(Player player, UUID bellyId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String itemId = legendaryItems.getId(item);
            if (LegendaryItems.DRAGON_BELLY.equals(itemId)) {
                UUID itemBellyId = getBellyId(item);
                if (itemBellyId.equals(bellyId)) {
                    return item;
                }
            }
        }
        return null;
    }

    private UUID getBellyId(ItemStack bellyItem) {
        if (bellyItem == null || !bellyItem.hasItemMeta()) {
            return UUID.randomUUID();
        }
        
        ItemMeta meta = bellyItem.getItemMeta();
        String idStr = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "belly_id"),
            PersistentDataType.STRING
        );
        
        if (idStr == null) {
            // Utwórz nowy UUID dla tego brzuszka
            UUID newId = UUID.randomUUID();
            meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "belly_id"),
                PersistentDataType.STRING,
                newId.toString()
            );
            bellyItem.setItemMeta(meta);
            return newId;
        }
        
        return UUID.fromString(idStr);
    }

    /**
     * Holder dla GUI Brzuszka Smoka
     */
    public static class DragonBellyGuiHolder implements org.bukkit.inventory.InventoryHolder {
        private final UUID bellyId;
        private final UUID ownerId;

        public DragonBellyGuiHolder(UUID bellyId, UUID ownerId) {
            this.bellyId = bellyId;
            this.ownerId = ownerId;
        }

        public UUID getBellyId() {
            return bellyId;
        }
        
        public UUID getOwnerId() {
            return ownerId;
        }

        @Override
        public Inventory getInventory() {
            return null; // Nie używane
        }
    }
}