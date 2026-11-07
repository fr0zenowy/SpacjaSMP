package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class MarketManager {

    public static final int MAX_OWN = 3;
    public static final String ID_CLOSE = "close";
    public static final String ID_BAG = "bag";
    public static final String ID_SEARCH = "search";
    public static final String ID_PREV = "prev";
    public static final String ID_NEXT = "next";
    public static final String ID_LISTING = "listing";
    public static final String ID_OWN = "own";
    public static final String ID_BUY = "buy";
    public static final String ID_BACK = "back";
    public static final String ID_SORT = "sort";
    public static final String ID_CAT_TOOLS = "cat_tools";
    public static final String ID_CAT_FOOD = "cat_food";
    public static final String ID_CAT_USEFUL = "cat_useful";

    private final ZamrozeniePlugin plugin;
    private final ShopEconomy economy;
    private final NamespacedKey buttonKey;
    private final NamespacedKey listingKey;
    private final File file;
    private final Map<UUID, MarketListing> listings = new LinkedHashMap<>();
    private final Set<UUID> searchPrompt = new java.util.HashSet<>();
    private final Map<UUID, MarketCategory> searchCategory = new HashMap<>();
    private final Map<UUID, MarketSort> searchSort = new HashMap<>();
    private final Map<UUID, SignEdit> signEdits = new HashMap<>();

    public MarketManager(ZamrozeniePlugin plugin, ShopEconomy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.buttonKey = new NamespacedKey(plugin, "rynek_btn");
        this.listingKey = new NamespacedKey(plugin, "rynek_id");
        this.file = new File(plugin.getDataFolder(), "rynek-listings.yml");
        load();
    }

    public ZamrozeniePlugin getPlugin() { return plugin; }

    public void openBrowse(Player player, int page, String query) {
        openBrowse(player, page, query, null, MarketSort.NONE);
    }

    public void openBrowse(Player player, int page, String query, MarketCategory category) {
        openBrowse(player, page, query, category, MarketSort.NONE);
    }

    public void openBrowse(Player player, int page, String query, MarketCategory category, MarketSort sort) {
        if (sort == null) sort = MarketSort.NONE;
        List<MarketListing> shown = filtered(query, category);
        sort.apply(shown);
        int pages = Math.max(1, (shown.size() + MarketGuiHolder.PAGE_SIZE - 1) / MarketGuiHolder.PAGE_SIZE);
        page = Math.min(page, pages - 1);
        MarketGuiHolder holder = new MarketGuiHolder(MarketGuiHolder.Mode.BROWSE, page, query, category, sort);
        Inventory inv = Bukkit.createInventory(holder, MarketGuiHolder.SIZE, MarketGuiHolder.TITLE_BROWSE);
        holder.setInventory(inv);
        paintChrome(inv, category, sort);
        int from = page * MarketGuiHolder.PAGE_SIZE;
        for (int i = 0; i < MarketGuiHolder.PAGE_SIZE; i++) {
            int idx = from + i;
            if (idx >= shown.size()) break;
            inv.setItem(MarketGuiHolder.LIST_SLOTS[i], listingItem(shown.get(idx), false));
        }
        player.openInventory(inv);
    }

    public void openMine(Player player) {
        openMine(player, 0, null, null, MarketSort.NONE);
    }

    public void openMine(Player player, int page, String query, MarketCategory category, MarketSort sort) {
        MarketGuiHolder holder = new MarketGuiHolder(MarketGuiHolder.Mode.MINE, page, query, category, sort);
        Inventory inv = Bukkit.createInventory(holder, MarketGuiHolder.SIZE, MarketGuiHolder.TITLE_MINE);
        holder.setInventory(inv);
        List<MarketListing> own = ownListings(player.getUniqueId());
        for (int i = 0; i < MarketGuiHolder.MINE_SLOTS.length && i < own.size(); i++) {
            inv.setItem(MarketGuiHolder.MINE_SLOTS[i], listingItem(own.get(i), true));
        }
        inv.setItem(MarketGuiHolder.MINE_BACK, chrome(ChatColor.RED + "Wróć", ID_BACK, null, false));
        player.openInventory(inv);
    }

    public void openConfirm(Player player, UUID listingId, int page, String query, MarketCategory category, MarketSort sort) {
        MarketListing listing = listings.get(listingId);
        if (listing == null) {
            player.sendMessage(ChatColor.RED + "Ta oferta już nie istnieje.");
            openBrowse(player, page, query, category, sort);
            return;
        }
        if (listing.seller.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "To Twoja oferta. Cofniesz ją w plecaku.");
            return;
        }
        MarketGuiHolder holder = new MarketGuiHolder(MarketGuiHolder.Mode.CONFIRM, page, query, category, sort, listingId);
        Inventory inv = Bukkit.createInventory(holder, MarketGuiHolder.CONFIRM_SIZE, MarketGuiHolder.TITLE_CONFIRM);
        holder.setInventory(inv);
        inv.setItem(MarketGuiHolder.CONFIRM_BUY, chrome(ChatColor.GREEN + "Zakup", ID_BUY, null, false));
        inv.setItem(MarketGuiHolder.CONFIRM_PREVIEW, confirmPreview(listing));
        inv.setItem(MarketGuiHolder.CONFIRM_CLOSE, chrome(ChatColor.RED + "Zamknij", ID_CLOSE, null, false));
        player.openInventory(inv);
    }

    private void paintChrome(Inventory inv, MarketCategory active, MarketSort sort) {
        inv.setItem(MarketGuiHolder.BTN_SWORD, chrome(
                "" + ChatColor.GRAY + ChatColor.BOLD + "Narzędzia",
                ID_CAT_TOOLS,
                List.of(loreLine("Kliknij, aby przejść do kategorii z narzędziami")),
                active == MarketCategory.TOOLS
        ));
        inv.setItem(MarketGuiHolder.BTN_SCROLL, chrome(
                "" + ChatColor.GOLD + ChatColor.BOLD + "Jedzenie",
                ID_CAT_FOOD,
                List.of(loreLine("Kliknij, aby przejść do kategorii z jedzeniem")),
                active == MarketCategory.FOOD
        ));
        inv.setItem(MarketGuiHolder.BTN_VIAL, chrome(
                ChatColor.RED + "Przydatne",
                ID_CAT_USEFUL,
                List.of(loreLine("Kliknij, aby przejść do kategorii z przydatnymi")),
                active == MarketCategory.USEFUL
        ));
        inv.setItem(MarketGuiHolder.BTN_BAG, chrome(ChatColor.YELLOW + "Twoje oferty", ID_BAG, List.of(
                loreLine("Maks. " + MAX_OWN + " wystawione itemy")
        ), false));
        inv.setItem(MarketGuiHolder.BTN_SEARCH, chrome(ChatColor.AQUA + "Szukaj", ID_SEARCH, List.of(
                loreLine("Wpisz nazwę na tabliczce")
        ), false));
        inv.setItem(MarketGuiHolder.BTN_PREV, chrome(ChatColor.GRAY + "Poprzednia strona", ID_PREV, null, false));
        inv.setItem(MarketGuiHolder.BTN_CLOSE, chrome(ChatColor.RED + "Zamknij", ID_CLOSE, null, false));
        inv.setItem(MarketGuiHolder.BTN_NEXT, chrome(ChatColor.GRAY + "Następna strona", ID_NEXT, null, false));
        inv.setItem(MarketGuiHolder.BTN_GEAR, chrome(
                "" + ChatColor.GOLD + "Filtrowanie " + ChatColor.GRAY + "(Kliknij)",
                ID_SORT,
                sortLore(sort == null ? MarketSort.NONE : sort),
                false
        ));
    }

    private List<String> sortLore(MarketSort active) {
        List<String> lore = new ArrayList<>();
        lore.add(loreLine("Wyświetlaj według"));
        for (MarketSort option : MarketSort.values()) {
            if (option == active) {
                lore.add("" + ChatColor.YELLOW + ChatColor.UNDERLINE + ShopManager.ARROW + " " + option.label);
            } else {
                lore.add(loreLine(option.label));
            }
        }
        return lore;
    }

    private ItemStack listingItem(MarketListing listing, boolean own) {
        ItemStack it = listing.item.clone();
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        List<String> lore = new ArrayList<>();
        lore.add(loreLine("Cena: " + ShopManager.money(listing.price)));
        lore.add(loreLine("Sprzedawca: " + ChatColor.WHITE + listing.sellerName));
        lore.add(loreLine("Ilość: " + ChatColor.WHITE + it.getAmount()));
        if (own) {
            lore.add("");
            lore.add(loreLine(ChatColor.YELLOW, "Kliknij, aby cofnąć item z rynku"));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.STRING, own ? ID_OWN : ID_LISTING);
        meta.getPersistentDataContainer().set(listingKey, PersistentDataType.STRING, listing.id.toString());
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack confirmPreview(MarketListing listing) {
        ItemStack it = listing.item.clone();
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setLore(List.of(loreLine("Cena: " + ShopManager.money(listing.price))));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private static String loreLine(String text) {
        return loreLine(ChatColor.GRAY, text);
    }

    private static String loreLine(ChatColor color, String text) {
        return color + ShopManager.ARROW + " " + text;
    }

    private ItemStack chrome(String name, String id, List<String> lore, boolean glint) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        meta.setCustomModelData(1);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (glint) meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    public String buttonId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(buttonKey, PersistentDataType.STRING);
    }

    public UUID listingId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(listingKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void wystaw(Player player, int amount, double price) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Trzymaj przedmiot w ręce.");
            return;
        }
        if (amount < 1) {
            player.sendMessage(ChatColor.RED + "Ilość musi być większa od 0.");
            return;
        }
        if (price <= 0) {
            player.sendMessage(ChatColor.RED + "Cena musi być większa od 0.");
            return;
        }
        if (ownListings(player.getUniqueId()).size() >= MAX_OWN) {
            player.sendMessage(ChatColor.RED + "Możesz wystawić maksymalnie " + MAX_OWN + " przedmioty.");
            return;
        }
        if (hand.getAmount() < amount) {
            player.sendMessage(ChatColor.RED + "Nie masz tyle tego przedmiotu. Masz " + hand.getAmount() + ".");
            return;
        }

        ItemStack stored = hand.clone();
        stored.setAmount(amount);
        if (hand.getAmount() <= amount) player.getInventory().setItemInMainHand(null);
        else hand.setAmount(hand.getAmount() - amount);

        MarketListing listing = new MarketListing(
                UUID.randomUUID(), player.getUniqueId(), player.getName(), ShopManager.roundMoney(price), stored);
        listings.put(listing.id, listing);
        save();
        player.sendMessage(ChatColor.GREEN + "Wystawiono " + ChatColor.WHITE + "x" + amount + " "
                + prettyName(stored) + ChatColor.GREEN + " za " + ShopManager.money(price));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    public void withdraw(Player player, UUID id, int page, String query, MarketCategory category, MarketSort sort) {
        MarketListing listing = listings.get(id);
        if (listing == null || !listing.seller.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Nie znaleziono tej oferty.");
            return;
        }
        listings.remove(id);
        save();
        give(player, listing.item);
        player.sendMessage(ChatColor.GREEN + "Cofnięto przedmiot z rynku.");
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.1f);
        openMine(player, page, query, category, sort);
    }

    public void buy(Player player, UUID id, int page, String query, MarketCategory category, MarketSort sort) {
        MarketListing listing = listings.get(id);
        if (listing == null) {
            player.sendMessage(ChatColor.RED + "Ta oferta już nie istnieje.");
            openBrowse(player, page, query, category, sort);
            return;
        }
        if (listing.seller.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "To Twoja oferta.");
            return;
        }
        if (!economy.take(player.getUniqueId(), listing.price)) {
            player.sendMessage(ChatColor.RED + "Nie masz tyle monet. Masz " + ShopManager.money(economy.get(player)));
            return;
        }
        economy.add(listing.seller, listing.price);
        listings.remove(id);
        save();
        give(player, listing.item);
        player.sendMessage(ChatColor.GREEN + "Kupiono " + ChatColor.WHITE + prettyName(listing.item)
                + ChatColor.GREEN + " za " + ShopManager.money(listing.price));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
        Player seller = Bukkit.getPlayer(listing.seller);
        if (seller != null && seller.isOnline()) {
            seller.sendMessage(ChatColor.GREEN + player.getName() + ChatColor.GRAY + " kupił Twój przedmiot za "
                    + ShopManager.money(listing.price));
        }
        openBrowse(player, page, query, category, sort);
    }

    public void startSignSearch(Player player, MarketCategory category, MarketSort sort) {
        player.closeInventory();
        if (!openSignEditor(player, category, sort)) {
            startChatSearch(player, category, sort);
        }
    }

    public void startChatSearch(Player player, MarketCategory category, MarketSort sort) {
        player.closeInventory();
        searchPrompt.add(player.getUniqueId());
        if (category != null) searchCategory.put(player.getUniqueId(), category);
        else searchCategory.remove(player.getUniqueId());
        if (sort != null) searchSort.put(player.getUniqueId(), sort);
        else searchSort.remove(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "Wpisz na czacie, czego szukasz na rynku.");
        player.sendMessage(ChatColor.GRAY + "Np. " + ChatColor.WHITE + "obsidian" + ChatColor.GRAY
                + " albo " + ChatColor.WHITE + "obs" + ChatColor.GRAY + " — pokaże wszystko z tym kawałkiem nazwy.");
        player.sendMessage(ChatColor.GRAY + "Wpisz " + ChatColor.RED + "anuluj" + ChatColor.GRAY + " albo "
                + ChatColor.WHITE + "*" + ChatColor.GRAY + " żeby pokazać cały rynek.");
        player.sendMessage("");
    }

    private boolean openSignEditor(Player player, MarketCategory category, MarketSort sort) {
        restoreSign(player.getUniqueId());
        Block spot = findSignSpot(player);
        if (spot == null) return false;
        BlockState previous = spot.getState();
        spot.setType(Material.OAK_SIGN, false);
        if (!(spot.getState() instanceof Sign sign)) {
            previous.update(true, false);
            return false;
        }
        sign.setWaxed(false);
        sign.update(true, false);
        signEdits.put(player.getUniqueId(), new SignEdit(previous, category, sort));
        player.openSign((Sign) spot.getState(), Side.FRONT);
        player.sendMessage(ChatColor.GRAY + "Wpisz nazwę przedmiotu na tabliczce.");
        return true;
    }

    private Block findSignSpot(Player player) {
        Block[] candidates = {
                player.getLocation().getBlock().getRelative(0, 2, 0),
                player.getLocation().getBlock().getRelative(0, 3, 0),
                player.getEyeLocation().getBlock(),
                player.getLocation().getBlock().getRelative(0, 1, 0)
        };
        for (Block block : candidates) {
            if (block.getType().isAir()) return block;
        }
        return null;
    }

    public boolean hasSignEdit(UUID id) {
        return signEdits.containsKey(id);
    }

    public void finishSignSearch(Player player, String[] lines) {
        SignEdit edit = signEdits.remove(player.getUniqueId());
        if (edit == null) return;
        edit.previous.update(true, false);
        StringBuilder raw = new StringBuilder();
        if (lines != null) {
            for (String line : lines) {
                if (line != null) raw.append(line);
            }
        }
        finishSearch(player, raw.toString().trim(), edit.category, edit.sort);
    }

    public boolean hasSearchPrompt(UUID id) {
        return searchPrompt.contains(id);
    }

    public void finishSearch(Player player, String raw) {
        MarketCategory category = searchCategory.remove(player.getUniqueId());
        MarketSort sort = searchSort.remove(player.getUniqueId());
        searchPrompt.remove(player.getUniqueId());
        finishSearch(player, raw, category, sort);
    }

    private void finishSearch(Player player, String raw, MarketCategory category, MarketSort sort) {
        searchPrompt.remove(player.getUniqueId());
        searchCategory.remove(player.getUniqueId());
        searchSort.remove(player.getUniqueId());
        if (raw.equalsIgnoreCase("anuluj") || raw.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.GRAY + "Anulowano wyszukiwanie.");
            openBrowse(player, 0, null, category, sort);
            return;
        }
        if (raw.equals("*") || raw.equalsIgnoreCase("all") || raw.isBlank()) {
            openBrowse(player, 0, null, category, sort);
            return;
        }
        String q = raw.toLowerCase(Locale.ROOT).trim();
        player.sendMessage(ChatColor.GREEN + "Szukam: " + ChatColor.WHITE + q);
        openBrowse(player, 0, q, category, sort);
    }

    public void onQuit(UUID id) {
        searchPrompt.remove(id);
        searchCategory.remove(id);
        searchSort.remove(id);
        restoreSign(id);
    }

    private void restoreSign(UUID id) {
        SignEdit edit = signEdits.remove(id);
        if (edit != null) edit.previous.update(true, false);
    }

    private List<MarketListing> filtered(String query, MarketCategory category) {
        List<MarketListing> out = new ArrayList<>();
        for (MarketListing listing : listings.values()) {
            if (category != null && !category.matches(listing.item)) continue;
            if (query != null && !query.isBlank() && !matchesQuery(listing, query)) continue;
            out.add(listing);
        }
        return out;
    }

    private boolean matchesQuery(MarketListing listing, String query) {
        String key = listing.item.getType().getKey().toString().toLowerCase(Locale.ROOT);
        String name = listing.item.getType().name().toLowerCase(Locale.ROOT);
        String pretty = ChatColor.stripColor(prettyName(listing.item)).toLowerCase(Locale.ROOT);
        return key.contains(query) || name.contains(query) || pretty.contains(query);
    }

    private List<MarketListing> ownListings(UUID seller) {
        List<MarketListing> out = new ArrayList<>();
        for (MarketListing listing : listings.values()) {
            if (listing.seller.equals(seller)) out.add(listing);
        }
        return out;
    }

    private static String prettyName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void give(Player player, ItemStack item) {
        player.getInventory().addItem(item.clone()).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void load() {
        listings.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("listings");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                UUID seller = UUID.fromString(root.getString(key + ".seller"));
                String name = root.getString(key + ".seller-name", "?");
                double price = root.getDouble(key + ".price");
                ItemStack item = root.getItemStack(key + ".item");
                long created = root.getLong(key + ".created", 0L);
                if (item != null && price > 0) {
                    listings.put(id, new MarketListing(id, seller, name, price, item, created));
                }
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (MarketListing listing : listings.values()) {
            String path = "listings." + listing.id;
            yaml.set(path + ".seller", listing.seller.toString());
            yaml.set(path + ".seller-name", listing.sellerName);
            yaml.set(path + ".price", listing.price);
            yaml.set(path + ".created", listing.createdAt);
            yaml.set(path + ".item", listing.item);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie zapisac rynek-listings.yml", e);
        }
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(signEdits.keySet())) {
            restoreSign(id);
        }
        save();
    }

    private static final class SignEdit {
        final BlockState previous;
        final MarketCategory category;
        final MarketSort sort;

        SignEdit(BlockState previous, MarketCategory category, MarketSort sort) {
            this.previous = previous;
            this.category = category;
            this.sort = sort == null ? MarketSort.NONE : sort;
        }
    }
}
