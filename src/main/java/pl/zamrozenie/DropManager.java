package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class DropManager {

    public static final String PDC_BUTTON = "drop_button";
    public static final int CONTENT_SIZE = DropGuiHolder.CONTENT_END - DropGuiHolder.CONTENT_START + 1;

    private final ZamrozeniePlugin plugin;
    private final NamespacedKey buttonKey;
    private final File file;
    private final Map<DropSource, ItemStack[]> regular = new EnumMap<>(DropSource.class);
    private final Map<DropSource, Map<Integer, List<ItemStack>>> eventowki = new EnumMap<>(DropSource.class);
    private BukkitTask animTask;
    private boolean animFlip;

    public DropManager(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        this.buttonKey = new NamespacedKey(plugin, PDC_BUTTON);
        this.file = new File(plugin.getDataFolder(), "boss-drops.yml");
        for (DropSource src : DropSource.values()) {
            regular.put(src, new ItemStack[CONTENT_SIZE]);
            eventowki.put(src, new HashMap<>());
        }
        load();
        animTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickViewAnim, 20L, 20L);
    }

    public NamespacedKey getButtonKey() {
        return buttonKey;
    }

    public List<ItemStack> getDrops(DropSource source) {
        return rollDrops(source);
    }

    public int regularCount(DropSource source) {
        int n = 0;
        for (ItemStack it : regular.get(source)) {
            if (it != null && it.getType() != Material.AIR) n++;
        }
        return n;
    }

    public int eventSlotCount(DropSource source) {
        return eventowki.get(source).size();
    }

    public List<ItemStack> rollDrops(DropSource source) {
        List<ItemStack> out = new ArrayList<>(rollRegularDrops(source));
        if (source == DropSource.ZRZUT) return out;
        for (List<ItemStack> pair : eventowki.get(source).values()) {
            if (pair == null || pair.isEmpty()) continue;
            out.add(pair.get(ThreadLocalRandom.current().nextInt(pair.size())).clone());
        }
        return out;
    }

    public List<ItemStack> rollRegularDrops(DropSource source) {
        List<ItemStack> bag = new ArrayList<>();
        for (ItemStack it : regular.get(source)) {
            if (it != null && it.getType() != Material.AIR) bag.add(it.clone());
        }
        int pool = dropPoolSize(source);
        if (pool >= 0 && bag.size() > pool) {
            Collections.shuffle(bag, ThreadLocalRandom.current());
            bag = new ArrayList<>(bag.subList(0, pool));
        }
        return bag;
    }

    public List<ItemStack> previewRegular(DropSource source) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack it : regular.get(source)) {
            if (it != null && it.getType() != Material.AIR) out.add(it.clone());
        }
        return out;
    }

    private int dropPoolSize(DropSource source) {
        if (source != DropSource.WLADCA_LOCHOW) return -1;
        return Math.max(0, plugin.getConfig().getInt("bosses.hollowknight.drop-pool-size", 5));
    }

    public void saveFromInventory(DropSource source, Inventory inv) {
        ItemStack[] slots = new ItemStack[CONTENT_SIZE];
        for (int slot = DropGuiHolder.CONTENT_START; slot <= DropGuiHolder.CONTENT_END; slot++) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType() == Material.AIR || isButton(it)) continue;
            slots[slot - DropGuiHolder.CONTENT_START] = it.clone();
        }
        regular.put(source, slots);
        save();
    }

    public boolean addEventItem(DropSource source, int slot, ItemStack item) {
        if (!DropGuiHolder.isContentSlot(slot) || item == null || item.getType() == Material.AIR) return false;
        List<ItemStack> pair = eventowki.get(source).computeIfAbsent(slot, s -> new ArrayList<>());
        if (pair.size() >= 2) return false;
        pair.add(item.clone());
        save();
        return true;
    }

    public ItemStack removeEventItem(DropSource source, int slot) {
        List<ItemStack> pair = eventowki.get(source).get(slot);
        if (pair == null || pair.isEmpty()) return null;
        ItemStack removed = pair.remove(pair.size() - 1);
        if (pair.isEmpty()) eventowki.get(source).remove(slot);
        save();
        return removed;
    }

    public void refreshEventSlot(Inventory inv, DropSource source, int slot) {
        List<ItemStack> pair = eventowki.get(source).get(slot);
        if (pair == null || pair.isEmpty()) {
            inv.setItem(slot, null);
            return;
        }
        inv.setItem(slot, eventEditorItem(pair));
    }

    public void dropAt(DropSource source, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        for (ItemStack it : rollDrops(source)) {
            loc.getWorld().dropItemNaturally(loc, it);
        }
    }

    public void addToDeath(DropSource source, EntityDeathEvent event) {
        for (ItemStack it : rollDrops(source)) {
            event.getDrops().add(it);
        }
    }

    public void giveOrDrop(Player player, DropSource source) {
        if (player == null) return;
        for (ItemStack it : rollDrops(source)) {
            player.getInventory().addItem(it).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    public DropSource matchEntity(LivingEntity entity) {
        if (entity == null) return null;

        String mythic = MythicHook.internalName(plugin, entity.getUniqueId());
        if (mythic != null) {
            DropSource byMythic = DropSource.fromInput(mythic);
            if (byMythic != null) return byMythic;
        }

        String name = entity.getCustomName();
        if (name == null && entity.getScoreboardTags() != null) {
            for (String tag : entity.getScoreboardTags()) {
                DropSource byTag = DropSource.fromInput(tag);
                if (byTag != null) return byTag;
            }
        }
        if (name != null) {
            DropSource byName = DropSource.fromInput(name);
            if (byName != null) return byName;
        }
        return null;
    }

    public Inventory openMain(Player player) {
        DropGuiHolder holder = new DropGuiHolder(DropGuiHolder.Mode.MAIN, null);
        Inventory inv = Bukkit.createInventory(holder, 54, DropGuiHolder.TITLE);
        holder.setInventory(inv);
        paintChrome(inv, null);
        player.openInventory(inv);
        return inv;
    }

    public Inventory openView(Player player, DropSource source) {
        DropGuiHolder holder = new DropGuiHolder(DropGuiHolder.Mode.VIEW, source);
        Inventory inv = Bukkit.createInventory(holder, 54, DropGuiHolder.TITLE);
        holder.setInventory(inv);
        paintChrome(inv, source);
        paintViewContent(inv, source, animFlip);
        player.openInventory(inv);
        return inv;
    }

    public Inventory openEdit(Player player, DropSource source) {
        DropGuiHolder holder = new DropGuiHolder(DropGuiHolder.Mode.EDIT, source);
        Inventory inv = Bukkit.createInventory(holder, 54, DropGuiHolder.TITLE);
        holder.setInventory(inv);
        paintChrome(inv, source);
        loadRegular(inv, source);
        player.openInventory(inv);
        return inv;
    }

    public Inventory openEditEvent(Player player, DropSource source) {
        DropGuiHolder holder = new DropGuiHolder(DropGuiHolder.Mode.EDIT_EVENT, source);
        Inventory inv = Bukkit.createInventory(holder, 54, DropGuiHolder.TITLE);
        holder.setInventory(inv);
        paintChrome(inv, source);
        loadEventEditor(inv, source);
        player.openInventory(inv);
        return inv;
    }

    public static List<String> bossLore() {
        return List.of(
                ChatColor.GRAY + "\u00BB Znajdziesz tutaj wszystkie " + ChatColor.WHITE + "DROPY" + ChatColor.GRAY + ", kt\u00F3re",
                ChatColor.GRAY + "\u00BB mo\u017Cesz zdoby\u0107!",
                "",
                ChatColor.GRAY + "\u00BB " + ChatColor.YELLOW + "System nagradzania " + ChatColor.GRAY
                        + "dzia\u0142a tylko dla osoby " + ChatColor.GOLD + ChatColor.BOLD + "TOP 1!"
        );
    }

    private void paintChrome(Inventory inv, DropSource selected) {
        inv.setItem(DropGuiHolder.INFO_SLOT, chrome(Material.PAPER, ChatColor.RED + "" + ChatColor.BOLD + "DROP",
                bossLore(), "info"));
        inv.setItem(DropGuiHolder.PREV_SLOT, chrome(Material.PAPER, ChatColor.GRAY + "\u00AB Poprzednia strona",
                List.of(ChatColor.DARK_GRAY + "Wkrotce."), "prev"));
        inv.setItem(DropGuiHolder.NEXT_SLOT, chrome(Material.PAPER, ChatColor.GRAY + "Nastepna strona \u00BB",
                List.of(ChatColor.DARK_GRAY + "Wkrotce."), "next"));
        for (DropSource src : DropSource.values()) {
            inv.setItem(src.getMenuSlot(), bossButton(src, src == selected));
        }
    }

    private void loadRegular(Inventory inv, DropSource source) {
        ItemStack[] slots = regular.get(source);
        for (int i = 0; i < CONTENT_SIZE; i++) {
            if (slots[i] != null) inv.setItem(DropGuiHolder.CONTENT_START + i, slots[i].clone());
        }
    }

    private void loadEventEditor(Inventory inv, DropSource source) {
        for (Map.Entry<Integer, List<ItemStack>> e : eventowki.get(source).entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            inv.setItem(e.getKey(), eventEditorItem(e.getValue()));
        }
    }

    private void paintViewContent(Inventory inv, DropSource source, boolean flip) {
        for (int slot = DropGuiHolder.CONTENT_START; slot <= DropGuiHolder.CONTENT_END; slot++) {
            inv.setItem(slot, null);
        }
        ItemStack[] slots = regular.get(source);
        for (int i = 0; i < CONTENT_SIZE; i++) {
            if (slots[i] != null && slots[i].getType() != Material.AIR) {
                inv.setItem(DropGuiHolder.CONTENT_START + i, slots[i].clone());
            }
        }
        for (Map.Entry<Integer, List<ItemStack>> e : eventowki.get(source).entrySet()) {
            if (source == DropSource.ZRZUT) continue;
            List<ItemStack> pair = e.getValue();
            if (pair == null || pair.isEmpty()) continue;
            int idx = (flip && pair.size() > 1) ? 1 : 0;
            inv.setItem(e.getKey(), eventPreviewItem(pair.get(idx), pair.size()));
        }
    }

    private void tickViewAnim() {
        animFlip = !animFlip;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof DropGuiHolder holder)) continue;
            if (holder.getMode() != DropGuiHolder.Mode.VIEW || holder.getSource() == null) continue;
            paintViewContent(top, holder.getSource(), animFlip);
        }
    }

    private ItemStack eventPreviewItem(ItemStack base, int variants) {
        ItemStack show = base.clone();
        ItemMeta meta = show.getItemMeta();
        if (meta == null) return show;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        if (variants >= 2) {
            lore.add(ChatColor.GRAY + "\u00BB Szansa: " + ChatColor.YELLOW + "50%");
            lore.add(ChatColor.GRAY + "\u00BB Dropnie tylko jeden z dwóch");
        } else {
            lore.add(ChatColor.GRAY + "\u00BB Szansa: " + ChatColor.GREEN + "100%");
        }
        meta.setLore(lore);
        show.setItemMeta(meta);
        return show;
    }

    private ItemStack eventEditorItem(List<ItemStack> pair) {
        ItemStack show = pair.get(0).clone();
        ItemMeta meta = show.getItemMeta();
        if (meta == null) return show;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        if (pair.size() >= 2) {
            lore.add(ChatColor.GRAY + "\u00BB Szansa: " + ChatColor.YELLOW + "50% / 50%");
            lore.add(ChatColor.GRAY + "\u00BB " + ChatColor.WHITE + prettyName(pair.get(0))
                    + ChatColor.GRAY + " albo " + ChatColor.WHITE + prettyName(pair.get(1)));
            lore.add(ChatColor.GRAY + "\u00BB Dropnie tylko jedna, nigdy obie");
            lore.add(ChatColor.GRAY + "\u00BB W /drop przełączają się co 1s");
        } else {
            lore.add(ChatColor.GRAY + "\u00BB Szansa: " + ChatColor.GREEN + "100%");
            lore.add(ChatColor.GRAY + "\u00BB Kliknij z itemem na kursorze, aby dodać drugi (50/50)");
        }
        lore.add(ChatColor.YELLOW + "\u00BB Kliknij pustą ręką, żeby wyjąć item");
        meta.setLore(lore);
        show.setItemMeta(meta);
        return show;
    }

    private static String prettyName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private ItemStack bossButton(DropSource src, boolean selected) {
        ItemStack it = new ItemStack(src.getIcon());
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(src.buttonName());
        meta.setLore(bossLore());
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (selected) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        }
        meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.STRING, src.getId());
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack chrome(Material mat, String name, List<String> lore, String id) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.setCustomModelData(1);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isButton(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(buttonKey, PersistentDataType.STRING);
    }

    public String buttonId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(buttonKey, PersistentDataType.STRING);
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (DropSource src : DropSource.values()) {
            ItemStack[] slots = new ItemStack[CONTENT_SIZE];
            if (yaml.isConfigurationSection(src.getId())) {
                ConfigurationSection sec = yaml.getConfigurationSection(src.getId());
                if (sec != null) {
                    for (String key : sec.getKeys(false)) {
                        try {
                            int slot = Integer.parseInt(key);
                            ItemStack it = sec.getItemStack(key);
                            if (it != null && it.getType() != Material.AIR && DropGuiHolder.isContentSlot(slot)) {
                                slots[slot - DropGuiHolder.CONTENT_START] = it.clone();
                            }
                        } catch (NumberFormatException ignored) { }
                    }
                }
            } else {
                List<?> raw = yaml.getList(src.getId());
                if (raw != null) {
                    int slot = DropGuiHolder.CONTENT_START;
                    for (Object o : raw) {
                        if (slot > DropGuiHolder.CONTENT_END) break;
                        if (o instanceof ItemStack stack && stack.getType() != Material.AIR) {
                            slots[slot - DropGuiHolder.CONTENT_START] = stack.clone();
                            slot++;
                        }
                    }
                }
            }
            regular.put(src, slots);
        }

        ConfigurationSection evRoot = yaml.getConfigurationSection("eventowki");
        if (evRoot == null) return;
        for (DropSource src : DropSource.values()) {
            Map<Integer, List<ItemStack>> map = new HashMap<>();
            ConfigurationSection sec = evRoot.getConfigurationSection(src.getId());
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(key);
                        if (!DropGuiHolder.isContentSlot(slot)) continue;
                        List<ItemStack> pair = new ArrayList<>();
                        List<?> raw = sec.getList(key);
                        if (raw != null) {
                            for (Object o : raw) {
                                if (o instanceof ItemStack stack && stack.getType() != Material.AIR && pair.size() < 2) {
                                    pair.add(stack.clone());
                                }
                            }
                        }
                        if (!pair.isEmpty()) map.put(slot, pair);
                    } catch (NumberFormatException ignored) { }
                }
            }
            eventowki.put(src, map);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (DropSource src : DropSource.values()) {
            ItemStack[] slots = regular.get(src);
            for (int i = 0; i < CONTENT_SIZE; i++) {
                if (slots[i] == null || slots[i].getType() == Material.AIR) continue;
                yaml.set(src.getId() + "." + (DropGuiHolder.CONTENT_START + i), slots[i]);
            }
        }
        for (DropSource src : DropSource.values()) {
            for (Map.Entry<Integer, List<ItemStack>> e : eventowki.get(src).entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) continue;
                yaml.set("eventowki." + src.getId() + "." + e.getKey(), e.getValue());
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie zapisac boss-drops.yml", e);
        }
    }

    public void shutdown() {
        if (animTask != null) {
            animTask.cancel();
            animTask = null;
        }
        save();
    }
}
