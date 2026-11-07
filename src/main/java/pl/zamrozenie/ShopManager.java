package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShopManager {

    public static final String COIN = "\uE020";
    public static final String ARROW = "\u00BB";
    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d{1,2})?)");

    /** Kwota na zielono, ikona monety bez tintu (bialy = oryginalny kolor PNG). */
    public static String money(double amount) {
        return ChatColor.GREEN + formatMoney(amount) + ChatColor.WHITE + " " + COIN;
    }

    public static String formatMoney(double amount) {
        return String.format(Locale.US, "%.2f", roundMoney(amount));
    }

    public static double roundMoney(double amount) {
        return Math.round(Math.max(0.0, amount) * 100.0) / 100.0;
    }

    public static long toCents(double amount) {
        return Math.round(roundMoney(amount) * 100.0);
    }

    /** Stara cena czerwona i skreslona, aktualna zielona — w jednej linii. */
    public static String moneyChanged(double current, double base) {
        return ChatColor.RED + "" + ChatColor.STRIKETHROUGH + formatMoney(base)
                + ChatColor.RESET + " " + ChatColor.GREEN + formatMoney(current)
                + ChatColor.WHITE + " " + COIN;
    }

    public static final String ID_BUY = "buy";
    public static final String ID_SELL = "sell";
    public static final String ID_CLOSE = "close";
    public static final String ID_BANK = "bank";
    public static final String ID_INFO = "info";
    public static final String ID_CONFIRM = "confirm";
    public static final String ID_ADD = "add";
    public static final String ID_SUB = "sub";
    public static final String ID_OFFER = "offer";

    private final ZamrozeniePlugin plugin;
    private final ShopEconomy economy;
    private final ShopMarket market;
    private final ShopFloodLock flood;
    private final NamespacedKey buttonKey;
    private final NamespacedKey priceKey;
    private final File file;
    private final Map<Integer, ShopOffer> buyOffers = new HashMap<>();
    private final Map<Integer, ShopOffer> sellOffers = new HashMap<>();
    private final Map<UUID, PricePrompt> prompts = new HashMap<>();
    private BukkitTask marketTask;

    public ShopManager(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        this.economy = new ShopEconomy(plugin);
        this.market = new ShopMarket(plugin);
        this.flood = new ShopFloodLock(plugin);
        this.buttonKey = new NamespacedKey(plugin, "shop_button");
        this.priceKey = new NamespacedKey(plugin, "shop_price");
        this.file = new File(plugin.getDataFolder(), "shop-items.yml");
        load();
        startMarket();
    }

    public ShopMarket getMarket() {
        return market;
    }

    private void startMarket() {
        if (marketTask != null) marketTask.cancel();
        marketTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMarket, 20L * 60, 20L * 60);
    }

    private void tickMarket() {
        market.reload();
        freezeBuyPrices();
        if (market.recoverIdle(sellOffers.values())) save();
    }

    public ShopEconomy getEconomy() {
        return economy;
    }

    public ZamrozeniePlugin getPlugin() {
        return plugin;
    }

    // ─── OTWIERANIE ───────────────────────────────────────────────────────────

    public void openMain(Player player) {
        ShopGuiHolder holder = new ShopGuiHolder(ShopGuiHolder.Mode.MAIN);
        Inventory inv = Bukkit.createInventory(holder, ShopGuiHolder.MAIN_SIZE, ShopGuiHolder.TITLE_MAIN);
        holder.setInventory(inv);
        inv.setItem(ShopGuiHolder.MAIN_BUY, chrome(ChatColor.DARK_GREEN + "Kupno", ID_BUY, null));
        inv.setItem(ShopGuiHolder.MAIN_SELL, chrome(ChatColor.RED + "Sprzeda\u017C", ID_SELL, null));
        player.openInventory(inv);
    }

    public void openCatalog(Player player, boolean sell, boolean admin) {
        ShopGuiHolder.Mode mode = sell
                ? (admin ? ShopGuiHolder.Mode.SELL_ADMIN : ShopGuiHolder.Mode.SELL)
                : (admin ? ShopGuiHolder.Mode.BUY_ADMIN : ShopGuiHolder.Mode.BUY);
        ShopGuiHolder holder = new ShopGuiHolder(mode);
        Inventory inv = Bukkit.createInventory(holder, ShopGuiHolder.LIST_SIZE, ShopGuiHolder.TITLE_LIST);
        holder.setInventory(inv);
        paintCatalog(inv, player, sell);
        player.openInventory(inv);
    }

    public void openConfirm(Player player, ShopOffer offer) {
        ShopGuiHolder holder = new ShopGuiHolder(
                ShopGuiHolder.Mode.CONFIRM, offer.item.clone(), livePrice(offer), 1, offer.slot);
        Inventory inv = Bukkit.createInventory(holder, ShopGuiHolder.CONFIRM_SIZE, ShopGuiHolder.TITLE_CONFIRM);
        holder.setInventory(inv);
        paintConfirm(inv, holder);
        player.openInventory(inv);
    }

    public void refreshConfirm(ShopGuiHolder holder) {
        if (holder.getInventory() == null) return;
        paintConfirm(holder.getInventory(), holder);
    }

    // ─── RYSOWANIE ────────────────────────────────────────────────────────────

    private void paintCatalog(Inventory inv, Player player, boolean sell) {
        inv.setItem(ShopGuiHolder.LIST_CLOSE, chrome(ChatColor.RED + "Zamknij", ID_CLOSE, null));
        inv.setItem(ShopGuiHolder.LIST_BANK, chrome(ChatColor.GREEN + "Banknocik", ID_BANK, List.of(
                ChatColor.GRAY + ARROW + " Posiadana ilo\u015B\u0107 pieni\u0119dzy: " + money(economy.get(player))
        )));
        for (ShopOffer offer : (sell ? sellOffers : buyOffers).values()) {
            if (offer.slot < 0 || offer.slot >= ShopGuiHolder.LIST_SIZE || ShopGuiHolder.isListChrome(offer.slot)) continue;
            inv.setItem(offer.slot, displayItem(offer, sell, player));
        }
    }

    private void paintConfirm(Inventory inv, ShopGuiHolder holder) {
        int qty = holder.getConfirmQty();
        ShopOffer offer = buyOffers.get(holder.getConfirmSlot());
        double unit = offer != null ? offer.basePrice : holder.getConfirmPrice();
        double total = roundMoney(unit * qty);
        inv.setItem(ShopGuiHolder.CONFIRM_INFO, chrome(ChatColor.RED + "Wa\u017Cne", ID_INFO, List.of(
                ChatColor.GRAY + ARROW + " " + ChatColor.RED + "Zakup jest trwa\u0142y i nie da sie go "
                        + ChatColor.RED + ChatColor.BOLD + "COFN\u0104\u0106!"
        )));
        inv.setItem(ShopGuiHolder.CONFIRM_BUY, chrome(ChatColor.GREEN + "Zakup", ID_CONFIRM, List.of(
                ChatColor.GRAY + ARROW + " Kliknij aby Zakupi\u0107 " + ChatColor.GREEN + qty
                        + ChatColor.GRAY + " przedmiot\u00F3w za " + money(total)
        )));
        inv.setItem(ShopGuiHolder.CONFIRM_CLOSE, chrome(ChatColor.RED + "Zamknij", ID_CLOSE, null));
        inv.setItem(ShopGuiHolder.CONFIRM_ADD, chrome(ChatColor.GRAY + "Dodaj przedmioty", ID_ADD, List.of(
                ChatColor.GRAY + ARROW + " LPM: " + ChatColor.WHITE + "+16",
                ChatColor.GRAY + ARROW + " Shift: " + ChatColor.WHITE + "+64"
        )));
        inv.setItem(ShopGuiHolder.CONFIRM_SUB, chrome(ChatColor.GRAY + "Odejmij przedmioty", ID_SUB, List.of(
                ChatColor.GRAY + ARROW + " LPM: " + ChatColor.WHITE + "-16",
                ChatColor.GRAY + ARROW + " Shift: " + ChatColor.WHITE + "-64"
        )));

        ItemStack preview = holder.getConfirmBase().clone();
        preview.setAmount(Math.min(64, qty));
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                    ChatColor.GRAY + ARROW + " Kliknij ptaszka obok aby zakupi\u0107 " + qty + " przedmiot\u00F3w!",
                    ChatColor.GRAY + ARROW + " Cena to: " + money(total)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
            preview.setItemMeta(meta);
        }
        inv.setItem(ShopGuiHolder.CONFIRM_PREVIEW, preview);
    }

    private ItemStack displayItem(ShopOffer offer, boolean sell, Player player) {
        ItemStack it = offer.item.clone();
        it.setAmount(1);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        if (sell) {
            double shown = flood.personalSellPrice(player, offer);
            java.util.ArrayList<String> lore = new java.util.ArrayList<>();
            if (flood.isLocked(player, offer.slot)) {
                lore.add(ChatColor.GRAY + ARROW + " Cena produktu to " + moneyChanged(shown, offer.basePrice));
                lore.add(ChatColor.RED + ARROW + " Osobisty limit: " + ChatColor.GRAY + "-40% od ceny bazowej");
            } else {
                lore.add(ChatColor.GRAY + ARROW + " Cena produktu to " + moneyChanged(offer.price, offer.basePrice));
            }
            lore.add("");
            lore.add(ChatColor.GRAY + ARROW + " Kliknij " + ChatColor.RED + "PPM" + ChatColor.GRAY + " aby sprzeda\u0107 stak!");
            lore.add(ChatColor.GRAY + ARROW + " Kliknij " + ChatColor.RED + "PPM + SHIFT" + ChatColor.GRAY + " aby sprzeda\u0107 wszystkie przedmioty!");
            meta.setLore(lore);
        } else {
            meta.setLore(List.of(
                    ChatColor.GRAY + ARROW + " Kliknij  " + ChatColor.GREEN + "LPM" + ChatColor.GRAY + " aby zakupi\u0107 przedmiot!",
                    ChatColor.GRAY + ARROW + " Cena przedmiotu: " + money(offer.basePrice)
            ));
        }
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.STRING, ID_OFFER);
        meta.getPersistentDataContainer().set(priceKey, PersistentDataType.DOUBLE, offer.price);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack chrome(String name, String id, List<String> lore) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        meta.setCustomModelData(1);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    public String buttonId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(buttonKey, PersistentDataType.STRING);
    }

    public Double offerPrice(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(priceKey, PersistentDataType.DOUBLE);
    }

    public ShopOffer offerAt(boolean sell, int slot) {
        return (sell ? sellOffers : buyOffers).get(slot);
    }

    // ─── ADMIN: OFERTY ────────────────────────────────────────────────────────

    public void setOffer(boolean sell, int slot, ItemStack item, double price) {
        ItemStack stored = item.clone();
        stored.setAmount(1);
        Map<Integer, ShopOffer> map = sell ? sellOffers : buyOffers;
        map.put(slot, new ShopOffer(slot, price, stored));
        save();
    }

    public ShopOffer removeOffer(boolean sell, int slot) {
        ShopOffer old = (sell ? sellOffers : buyOffers).remove(slot);
        if (old != null) save();
        return old;
    }

    // ─── ZAKUP / SPRZEDAŻ ─────────────────────────────────────────────────────

    public void buy(Player player, ItemStack base, double fallbackPrice, int qty, int slot) {
        if (qty < 1) return;
        ShopOffer offer = buyOffers.get(slot);
        if (offer == null) offer = findMatching(buyOffers, base);
        double unit = offer != null ? offer.basePrice : fallbackPrice;
        double total = roundMoney(unit * qty);
        if (economy.get(player) < total) {
            player.sendMessage(ChatColor.RED + "Nie masz wystarczaj\u0105co pieni\u0119dzy. " + ChatColor.GRAY + "Potrzeba "
                    + money(total));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (!economy.take(player.getUniqueId(), total)) return;
        ItemStack template = (offer != null ? offer.item : base).clone();
        int stackSize = Math.max(1, template.getMaxStackSize());
        int leftQty = qty;
        while (leftQty > 0) {
            ItemStack give = template.clone();
            int part = Math.min(stackSize, leftQty);
            give.setAmount(part);
            leftQty -= part;
            player.getInventory().addItem(give).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        player.sendMessage(ChatColor.GREEN + "Kupiono " + ChatColor.WHITE + "x" + qty + ChatColor.GREEN + " za "
                + money(total));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        openCatalog(player, false, false);
    }

    public void sell(Player player, ShopOffer offer, boolean all) {
        sell(player, offer, all ? SellMode.ALL : SellMode.STACK);
    }

    public enum SellMode { ONE, STACK, ALL }

    public void sell(Player player, ShopOffer offer, SellMode mode) {
        ShopOffer live = sellOffers.get(offer.slot);
        if (live == null) live = offer;
        int have = countMatching(player, live.item);
        if (have < 1) {
            player.sendMessage(ChatColor.RED + "Nie masz tego przedmiotu.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        int stack = Math.max(1, live.item.getMaxStackSize());
        int qty = switch (mode) {
            case ONE -> 1;
            case STACK -> Math.min(have, stack);
            case ALL -> have;
        };
        removeMatching(player, live.item, qty);
        double unit = flood.personalSellPrice(player, live);
        double gain = roundMoney(unit * qty);
        economy.add(player.getUniqueId(), gain);
        market.applyTrade(live, qty, sellOffers.values(), player.getUniqueId());
        int lockHours = flood.afterSale(player, live.slot, gain);
        save();
        player.sendMessage(ChatColor.GREEN + "Sprzedano " + ChatColor.WHITE + "x" + qty + ChatColor.GREEN + " za "
                + money(gain));
        if (lockHours > 0) {
            player.sendMessage(ChatColor.RED + "Zarobi\u0142e\u015B " + money(flood.threshold()) + ChatColor.RED
                    + " lub wi\u0119cej z tego itemu. Twoja cena sprzedazy spad\u0142a o 40% na "
                    + ChatColor.WHITE + lockHours + "h" + ChatColor.RED + ".");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
        openCatalog(player, true, false);
    }

    private static double livePrice(ShopOffer offer) {
        return offer != null ? offer.price : 0.01;
    }

    private ShopOffer findMatching(Map<Integer, ShopOffer> offers, ItemStack item) {
        for (ShopOffer offer : offers.values()) {
            if (matches(offer.item, item)) return offer;
        }
        return null;
    }

    public boolean matches(ItemStack shop, ItemStack other) {
        if (shop == null || other == null || shop.getType() != other.getType() || other.getType() == Material.AIR) {
            return false;
        }
        ItemMeta sm = shop.getItemMeta();
        ItemMeta om = other.getItemMeta();
        if (sm == null || om == null) return sm == om;
        if (sm.hasDisplayName() != om.hasDisplayName()) return false;
        if (sm.hasDisplayName() && !sm.getDisplayName().equals(om.getDisplayName())) return false;
        if (sm.hasCustomModelData() != om.hasCustomModelData()) return false;
        if (sm.hasCustomModelData() && sm.getCustomModelData() != om.getCustomModelData()) return false;
        return sm.getEnchants().equals(om.getEnchants());
    }

    private int countMatching(Player player, ItemStack shop) {
        int n = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (matches(shop, it)) n += it.getAmount();
        }
        return n;
    }

    private void removeMatching(Player player, ItemStack shop, int qty) {
        int left = qty;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (!matches(shop, it)) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) contents[i] = null;
            left -= take;
        }
        player.getInventory().setContents(contents);
    }

    // ─── CENA PODAWANA NA CZACIE ──────────────────────────────────────────────

    public void startPricePrompt(Player player, boolean sell, int slot, ItemStack item) {
        player.closeInventory();
        prompts.put(player.getUniqueId(), new PricePrompt(sell, slot, item));
        String what = sell ? "sprzeda\u017Cy" : "kupna";
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "Podaj na czacie cen\u0119 " + what + " tego przedmiotu "
                + ChatColor.GRAY + "(np. " + ChatColor.WHITE + "150" + ChatColor.GRAY + " albo "
                + ChatColor.WHITE + "5.63" + ChatColor.GRAY + ").");
        player.sendMessage(ChatColor.GRAY + "Wpisz " + ChatColor.RED + "anuluj" + ChatColor.GRAY
                + " aby zrezygnowa\u0107 i odebra\u0107 przedmiot.");
        player.sendMessage("");
    }

    public PricePrompt takePrompt(UUID id) {
        return prompts.remove(id);
    }

    public PricePrompt peekPrompt(UUID id) {
        return prompts.get(id);
    }

    public void giveBack(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        player.getInventory().addItem(item.clone()).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    public static Double parsePrice(String... lines) {
        for (String line : lines) {
            if (line == null) continue;
            Matcher m = PRICE_PATTERN.matcher(ChatColor.stripColor(line).trim());
            if (!m.find()) continue;
            try {
                double v = Double.parseDouble(m.group(1).replace(',', '.'));
                if (v > 0) return roundMoney(v);
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    // ─── ZAPIS ────────────────────────────────────────────────────────────────

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        loadSection(yaml.getConfigurationSection("buy"), buyOffers);
        loadSection(yaml.getConfigurationSection("sell"), sellOffers);
        if (freezeBuyPrices()) save();
    }

    /** Kupno zawsze ma stala cene bazowa. */
    public boolean freezeBuyPrices() {
        boolean changed = false;
        for (ShopOffer offer : buyOffers.values()) {
            if (Math.abs(offer.price - offer.basePrice) < 0.0001) continue;
            offer.price = offer.basePrice;
            offer.volumeCents = 0;
            changed = true;
        }
        return changed;
    }

    /** Resetuje ceny sprzedazy do bazy z /asklep. */
    public int resetSellPrices() {
        freezeBuyPrices();
        int n = 0;
        long now = System.currentTimeMillis();
        for (ShopOffer offer : sellOffers.values()) {
            boolean dirty = Math.abs(offer.price - offer.basePrice) >= 0.0001 || offer.volumeCents != 0;
            offer.price = offer.basePrice;
            offer.volumeCents = 0;
            offer.lastTradeMs = now;
            if (dirty) n++;
        }
        save();
        return n;
    }

    private void loadSection(ConfigurationSection section, Map<Integer, ShopOffer> into) {
        into.clear();
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                double price = section.getDouble(key + ".price");
                ItemStack item = section.getItemStack(key + ".item");
                if (item != null && price > 0) {
                    double current = section.getDouble(key + ".current", price);
                    long last = section.getLong(key + ".last-trade", System.currentTimeMillis());
                    long volume = section.getLong(key + ".volume-cents", 0L);
                    into.put(slot, new ShopOffer(slot, price, current, last, volume, item));
                }
            } catch (NumberFormatException ignored) { }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        writeSection(yaml, "buy", buyOffers);
        writeSection(yaml, "sell", sellOffers);
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie zapisac shop-items.yml", e);
        }
    }

    private void writeSection(YamlConfiguration yaml, String path, Map<Integer, ShopOffer> offers) {
        for (ShopOffer o : offers.values()) {
            yaml.set(path + "." + o.slot + ".price", o.basePrice);
            yaml.set(path + "." + o.slot + ".current", o.price);
            yaml.set(path + "." + o.slot + ".last-trade", o.lastTradeMs);
            yaml.set(path + "." + o.slot + ".volume-cents", o.volumeCents);
            yaml.set(path + "." + o.slot + ".item", o.item);
        }
    }

    public void shutdown() {
        if (marketTask != null) {
            marketTask.cancel();
            marketTask = null;
        }
        // gracze w trakcie podawania ceny odzyskuja przedmiot
        for (Map.Entry<UUID, PricePrompt> e : prompts.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && p.isOnline()) giveBack(p, e.getValue().item);
        }
        prompts.clear();
        save();
        economy.save();
        flood.save();
    }

    public static final class PricePrompt {
        public final boolean sell;
        public final int slot;
        public final ItemStack item;

        PricePrompt(boolean sell, int slot, ItemStack item) {
            this.sell = sell;
            this.slot = slot;
            this.item = item;
        }
    }
}
