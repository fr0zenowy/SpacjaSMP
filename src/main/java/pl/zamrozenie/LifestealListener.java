package pl.zamrozenie;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LifestealListener implements Listener {

    private final LifestealManager ls;
    private final ZamrozeniePlugin plugin;

    public LifestealListener(ZamrozeniePlugin plugin, LifestealManager ls) {
        this.plugin = plugin;
        this.ls = ls;
    }

    // ─── Smierc gracza ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (ls.combatLogProcessed.contains(victim.getUniqueId())) {
            return;
        }

        LifestealManager.DeathHeartResult result = ls.applyDeathHearts(victim, killer);
        if (result == LifestealManager.DeathHeartResult.PROTECTED) {
            int sec = ls.remainingDeathProtectionSeconds(victim.getUniqueId());
            victim.sendMessage(ChatColor.YELLOW + "Ochrona po śmierci: " + ChatColor.WHITE + "nie tracisz serca"
                    + ChatColor.YELLOW + " (" + ChatColor.WHITE + sec + "s" + ChatColor.YELLOW + " ochrony).");
            return;
        }
        if (result != LifestealManager.DeathHeartResult.APPLIED) return;
        int newHearts = ls.getHearts(victim.getUniqueId());
        victim.sendMessage(ChatColor.RED + "Straci\u0142e\u015b serce! Masz teraz: " +
            ChatColor.WHITE + newHearts + ChatColor.RED + "/" + LifestealManager.MAX_HEARTS + " serc.");
    }

    // ─── Rejestracja trafien miedzy graczami (combat) ────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHitPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;

        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj
                   && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null || attacker.equals(victim)) return;

        boolean freshCombat = !ls.isInCombat(victim.getUniqueId());

        ls.registerCombatHit(attacker.getUniqueId(), victim.getUniqueId());

        // Powiadom obu o wejsciu w walke (tylko raz, nie co trafienie)
        if (freshCombat) {
            long secs = LifestealManager.COMBAT_DURATION_MS / 1000;
            String msg = ChatColor.RED + "\u2694 " + ChatColor.BOLD + "WALKA!" +
                ChatColor.RED + " Nie mo\u017cesz bezpiecznie si\u0119 wylogowa\u0107 przez " +
                ChatColor.WHITE + secs + ChatColor.RED + " sekund.";
            victim.sendMessage(msg);
            attacker.sendMessage(msg);
        }
    }

    // ─── AntiLogout: gracz wylogowuje sie podczas walki ──────────────────────────

    // ─── Powrot do zycia ─────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            ls.applyHearts(player);
            int protect = ls.remainingDeathProtectionSeconds(player.getUniqueId());
            if (protect > 0) {
                player.sendMessage(ChatColor.YELLOW + "Ochrona po śmierci: " + ChatColor.WHITE
                        + protect + "s" + ChatColor.YELLOW + " — nie stracisz serca i nikt go z Ciebie nie weźmie.");
            }
        }, 2L);
    }

    // ─── Wejscie na serwer ───────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (player.getAddress() != null) {
            ls.updateIP(player.getUniqueId(), player.getAddress().getAddress().getHostAddress());
        }

        if (!ls.hearts.containsKey(player.getUniqueId())) {
            ls.setHearts(player.getUniqueId(), LifestealManager.DEFAULT_HEARTS);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            ls.applyHearts(player);

            if (ls.pendingSpawn.remove(player.getUniqueId())) {
                Location spawn = player.getWorld().getSpawnLocation();
                player.teleport(spawn);
                player.sendMessage(ChatColor.GREEN + "\u2764 Witaj z powrotem! Zosta\u0142e\u015b odrodzony z " +
                    LifestealManager.DEFAULT_HEARTS + " sercami.");
            }
        }, 20L);
    }

    // ─── Blokowanie rzucania serc na ziemie ──────────────────────────────────────

    @EventHandler
    public void onDropHeart(PlayerDropItemEvent event) {
        if (ls.isHeartItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                ChatColor.RED + "Nie mo\u017cesz wyrzuci\u0107 serca! U\u017cyj go prawym przyciskiem.");
        }
    }

    // ─── Prawe klikniecie: serce lub ksiega ──────────────────────────────────────

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Tylko glowna reka — unikamy podwojnego odpalenia dla offhandu
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        if (ls.isHeartItem(item)) {
            event.setCancelled(true);

            // Serce z EQ mozna wplacic tylko do 10 serc (DEFAULT_HEARTS).
            // Serca powyzej 10 mozna zdobyc wylacznie przez zabijanie graczy.
            if (ls.getHearts(player.getUniqueId()) >= LifestealManager.DEFAULT_HEARTS) {
                player.sendMessage(ChatColor.YELLOW + "Nie mo\u017cesz wplaci\u0107 serca! "
                    + ChatColor.GRAY + "Wplacanie serc dzia\u0142a tylko gdy masz mniej ni\u017c "
                    + ChatColor.WHITE + LifestealManager.DEFAULT_HEARTS
                    + ChatColor.GRAY + " serc.");
                return;
            }

            ls.addHeart(player.getUniqueId());
            ls.applyHearts(player);

            if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
            else player.getInventory().setItemInMainHand(null);

            int newH = ls.getHearts(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "\u2764 U\u017cy\u0142e\u015b serca! Masz teraz: " +
                ChatColor.WHITE + newH + ChatColor.GREEN + "/" + LifestealManager.MAX_HEARTS +
                "  " + ls.heartsBar(newH));
            playHeartAnimation(player);
            return;
        }

        if (ls.isResurrectionBook(item)) {
            event.setCancelled(true);
            openResurrectionGui(player);
        }
    }

    // ─── GUI ksiegi odrodzenia — klikniecia ──────────────────────────────────────

    @EventHandler
    public void onResurrectionGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof ResurrectionGuiHolder holder)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        String targetName = holder.getPlayerAtSlot(event.getSlot());
        if (targetName == null) return;

        if (!Bukkit.getBanList(BanList.Type.NAME).isBanned(targetName)) {
            player.sendMessage(ChatColor.YELLOW + targetName + " nie jest ju\u017c zbanowany.");
            player.closeInventory();
            return;
        }

        player.closeInventory();

        ItemStack book = player.getInventory().getItemInMainHand();
        if (ls.isResurrectionBook(book)) {
            if (book.getAmount() > 1) book.setAmount(book.getAmount() - 1);
            else player.getInventory().setItemInMainHand(null);
        }

        ls.revivePlayer(targetName, player);
        playRevivalAnimation(player.getLocation(), targetName, player.getName());
    }

    // ─── Otwarcie GUI ────────────────────────────────────────────────────────────

    private void openResurrectionGui(Player player) {
        // Tylko gracze zbanowani przez system Lifesteal (nie serwerowy ban admina)
        var serverBanList = Bukkit.getBanList(BanList.Type.NAME);
        List<String> bannedNames = new ArrayList<>();
        for (String name : ls.getLifestealBannedNames()) {
            if (serverBanList.isBanned(name)) { // upewnij sie ze nadal zbanowany
                bannedNames.add(name);
            }
        }
        bannedNames.sort(String::compareToIgnoreCase);

        if (bannedNames.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Brak graczy zbanowanych przez Lifesteal.");
            return;
        }

        int displayCount = Math.min(bannedNames.size(), 45);
        int rows = (int) Math.ceil(displayCount / 9.0) + 1;
        int size = Math.min(54, rows * 9);

        ResurrectionGuiHolder holder = new ResurrectionGuiHolder(bannedNames);
        Inventory inv = Bukkit.createInventory(holder, size,
            ChatColor.GOLD + "" + ChatColor.BOLD + "Ksiega Odrodzenia");
        holder.setInventory(inv);

        ItemStack glass = HomeCommand.makeItem(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + " ");
        for (int i = 0; i < displayCount; i++) {
            String name = bannedNames.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
            meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + name);
            meta.setLore(List.of(
                ChatColor.RED + "Status: Zbanowany",
                "",
                ChatColor.YELLOW + "Kliknij aby odbanowac i zresetowac gracza."
            ));
            head.setItemMeta(meta);
            inv.setItem(i, head);
        }

        int lastRowStart = size - 9;
        for (int i = lastRowStart; i < size; i++) {
            inv.setItem(i, glass);
        }

        if (bannedNames.size() > 45) {
            inv.setItem(lastRowStart + 4, HomeCommand.makeItem(Material.PAPER,
                ChatColor.YELLOW + "Zbyt wielu graczy",
                ChatColor.GRAY + "Pokazano 45 z " + bannedNames.size() + " zbanowanych."));
        }

        player.openInventory(inv);
    }

    // ─── Animacja odrodzenia ─────────────────────────────────────────────────────

    private void playRevivalAnimation(Location origin, String revivedName, String reviverName) {
        World world = origin.getWorld();
        Location center = origin.clone().add(0, 0.5, 0);

        // ── Faza 1 (T=0): kolumna czasteczek w gore + dzwiek ─────────────────────
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.25f, 1.8f);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE,     0.8f,  0.5f);

        for (int t = 0; t < 20; t++) {
            final int tick = t;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double y = 0.3 + tick * 0.2;
                for (int i = 0; i < 8; i++) {
                    double angle = (2 * Math.PI * i / 8.0) + (tick * 0.45);
                    world.spawnParticle(Particle.ENCHANTED_HIT,
                        center.clone().add(Math.cos(angle) * 0.55, y, Math.sin(angle) * 0.55), 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.END_ROD,
                        center.clone().add(Math.cos(angle) * 0.28, y + 0.05, Math.sin(angle) * 0.28), 1, 0.04, 0, 0.04, 0.01);
                }
            }, (long) tick * 2);
        }

        // ── Faza 2 (T=22): pierscien soul fire ───────────────────────────────────
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < 36; i++) {
                double angle = 2 * Math.PI * i / 36.0;
                world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                    center.clone().add(Math.cos(angle) * 2.8, 0.05, Math.sin(angle) * 2.8), 1, 0, 0.04, 0, 0.015);
            }
            world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.7f);
        }, 22L);

        // ── Faza 3 (T=32): PIORUN + eksplozja + glowka + tridenty spadaja ────────
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location strikePos = center.clone().add(0, 1.5, 0);
            world.strikeLightningEffect(strikePos);
            world.spawnParticle(Particle.FLASH,     strikePos, 3);
            world.spawnParticle(Particle.EXPLOSION, strikePos, 6, 0.4, 0.4, 0.4, 0.0);
            world.spawnParticle(Particle.CRIT,      center,   40, 1.0, 1.0, 1.0, 0.4);
            world.spawnParticle(Particle.END_ROD,   center,   30, 0.5, 1.0, 0.5, 0.3);
            world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.7f);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE,        0.5f, 1.6f);

            // Glowka gracza wyswietlona na ArmorStandzie — nie da sie podniesc
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta headMeta = (SkullMeta) head.getItemMeta();
            headMeta.setOwningPlayer(Bukkit.getOfflinePlayer(revivedName));
            headMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + revivedName);
            head.setItemMeta(headMeta);

            ArmorStand stand = world.spawn(center.clone().add(0, 0.5, 0), ArmorStand.class);
            stand.setHelmet(head);
            stand.setVisible(false);
            stand.setGravity(true);
            stand.setSmall(false);
            stand.setInvulnerable(true);
            stand.setGlowing(true);
            stand.setVelocity(new Vector(0, 0.42, 0));
            Bukkit.getScheduler().runTaskLater(plugin, stand::remove, 80L);

            // 8 tridentow spada z nieba w krag (promien ~3 bloki, wysokosc +14)
            int tridentCount = 8;
            for (int i = 0; i < tridentCount; i++) {
                double angle = 2 * Math.PI * i / (double) tridentCount;
                double radius = 3.0;
                Location spawnLoc = center.clone().add(
                    Math.cos(angle) * radius,
                    14.0,
                    Math.sin(angle) * radius
                );
                Trident trident = world.spawn(spawnLoc, Trident.class);
                trident.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                trident.setGlowing(true);
                // Lekki rozrzut do srodka
                trident.setVelocity(new Vector(
                    -Math.cos(angle) * 0.08,
                    -0.6,
                    -Math.sin(angle) * 0.08
                ));
                // Usun po 4 sekundach (na wypadek gdyby gdzies utknely)
                Bukkit.getScheduler().runTaskLater(plugin, trident::remove, 80L);
            }

            // Shockwave
            for (int ring = 1; ring <= 5; ring++) {
                final double r = ring * 0.65;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (int i = 0; i < 28; i++) {
                        double angle = 2 * Math.PI * i / 28.0;
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                            center.clone().add(Math.cos(angle) * r, 0.05, Math.sin(angle) * r), 1, 0, 0, 0, 0.01);
                        world.spawnParticle(Particle.END_ROD,
                            center.clone().add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r), 1, 0, 0, 0, 0.025);
                    }
                }, (long) (ring * 3));
            }
        }, 32L);

        // ── Faza 4 (T=55): deszcz serc + fajerwerki ──────────────────────────────
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.spawnParticle(Particle.HEART,         center.clone().add(0, 2.5, 0), 30, 1.2, 0.8, 1.2, 0.08);
            world.spawnParticle(Particle.FIREWORK,      center.clone().add(0, 1.5, 0), 60, 0.6, 0.6, 0.6, 0.25);
            world.spawnParticle(Particle.ENCHANTED_HIT, center, 20, 0.8, 0.8, 0.8, 0.1);
            world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP,       1.0f, 1.3f);
            world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.5f);
        }, 55L);

        // ── Faza 5 (T=70): broadcast + tytul ─────────────────────────────────────
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String sep  = ChatColor.DARK_PURPLE + "" + ChatColor.STRIKETHROUGH + "============================";
            String gold = ChatColor.GOLD + "" + ChatColor.BOLD;
            String gray = ChatColor.GRAY + "";
            String wht  = ChatColor.WHITE + "" + ChatColor.BOLD;
            String aqua = ChatColor.AQUA + "" + ChatColor.BOLD;

            Bukkit.broadcastMessage(sep);
            Bukkit.broadcastMessage("  " + gold + "\u2736 ODRODZENIE! \u2736");
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("  " + gray + "Gracz " + wht + revivedName + gray + " zosta\u0142 odbanowany");
            Bukkit.broadcastMessage("  " + gray + "przez " + aqua + reviverName + gray + " za pomoc\u0105 " + gold + "Ksi\u0119gi Odrodzenia" + gray + ".");
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("  " + ChatColor.GREEN + revivedName + ChatColor.GRAY
                + " mo\u017ce powr\u00f3ci\u0107 z "
                + ChatColor.RED + LifestealManager.DEFAULT_HEARTS + " \u2764"
                + ChatColor.GRAY + " sercami!");
            Bukkit.broadcastMessage(sep);

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(
                    ChatColor.GOLD + "" + ChatColor.BOLD + "\u2736 ODRODZENIE \u2736",
                    ChatColor.WHITE + revivedName + ChatColor.GRAY + " zosta\u0142 przywr\u00f3cony!",
                    10, 70, 25);
            }
            world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
            world.spawnParticle(Particle.HEART, center.clone().add(0, 3.0, 0), 15, 0.5, 0.3, 0.5, 0.05);
        }, 70L);
    }

    // ─── Animacja serca ──────────────────────────────────────────────────────────

    private void playHeartAnimation(Player player) {
        Location center = player.getLocation().add(0, 1.1, 0);
        World world = player.getWorld();

        for (int i = 0; i < 12; i++) {
            double angle = (2 * Math.PI * i) / 12;
            world.spawnParticle(Particle.HEART,
                center.clone().add(Math.cos(angle), 0.1, Math.sin(angle)), 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 18; i++) {
            double angle = (2 * Math.PI * i) / 9;
            world.spawnParticle(Particle.END_ROD,
                center.clone().add(Math.cos(angle) * 0.6, i * 0.12, Math.sin(angle) * 0.6), 1, 0, 0, 0, 0);
        }
        world.spawnParticle(Particle.FIREWORK, center, 25, 0.25, 0.4, 0.25, 0.08);
        world.spawnParticle(Particle.HEART, center.clone().add(0, 0.5, 0), 4, 0.3, 0.2, 0.3, 0.05);

        player.playSound(center, Sound.ENTITY_PLAYER_LEVELUP,        0.6f, 1.8f);
        player.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME,   0.5f, 2.0f);
        player.playSound(center, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.5f);
    }
}
