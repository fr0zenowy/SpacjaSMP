package pl.zamrozenie;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Random;

/**
 * Fabryka legendarnych przedmiotow do zrzutu.
 *
 *  GRAPPLE      – Hak z Lina (FISHING_ROD)
 *  FURY         – Furia Berserkera (BLAZE_ROD)
 *  SNARE        – Klatka Pojedynku (GHAST_TEAR)
 *  BOSS_PUMPKIN – Dynia Bossa (CARVED_PUMPKIN, helm)
 *  RIFT_THORN   – Kolce Rozdarcia (PRISMARINE_SHARD, debuff po trafieniu)
 */
public final class LegendaryItems {

    public static final String GRAPPLE      = "grapple";
    public static final String FURY         = "fury";
    public static final String SNARE        = "snare";
    public static final String BOSS_PUMPKIN = "boss_pumpkin";
    public static final String RIFT_THORN   = "rift_thorn";
    public static final String DRAGON_BELLY = "dragon_belly";
    public static final String DRAGON_CLAW  = "dragon_claw";
    public static final String WARDEN_SWORD = "warden_sword";
    public static final String VOID_BOW     = "void_bow";
    public static final String NETHER_PICK  = "nether_pick_3x3";
    public static final String PICK_SHARD   = "nether_pick_shard";

    // CustomModelData – haczyki pod resource pack (tekstury)
    public static final int CMD_GRAPPLE      = 5001;
    public static final int CMD_FURY         = 5002;
    public static final int CMD_SNARE        = 5003;
    public static final int CMD_BOSS_PUMPKIN = 5004;
    public static final int CMD_RIFT_THORN   = 5005;
    public static final int CMD_DRAGON_BELLY = 5006;
    public static final int CMD_DRAGON_CLAW  = 5007;
    public static final int CMD_WARDEN_SWORD = 7100002;
    public static final int CMD_VOID_BOW     = 7100003;

    // Cooldowny (w tickach, 20 tickow = 1s)
    public static final int GRAPPLE_CD_TICKS  = 4  * 20;
    public static final int FURY_CD_TICKS     = 40 * 20;
    public static final int SNARE_CD_TICKS    = 90 * 20;
    public static final int RIFT_THORN_CD_TICKS = 30 * 20;

    private final ZamrozeniePlugin plugin;
    private final NamespacedKey idKey;
    private final NamespacedKey voidModeKey;

    public LegendaryItems(ZamrozeniePlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "legendary_id");
        this.voidModeKey = new NamespacedKey(plugin, "void_bow_mode");
    }

    public NamespacedKey getIdKey() { return idKey; }
    public NamespacedKey getVoidModeKey() { return voidModeKey; }

    /** Zwraca id legendarnego przedmiotu lub null jesli to zwykly item. */
    public String getId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(idKey, PersistentDataType.STRING);
    }

    public boolean isLegendary(ItemStack item) {
        return getId(item) != null;
    }

    /** Wszystkie legendy (fabryka / ewentualne inne uzycia). */
    public ItemStack[] allLegendaries() {
        return new ItemStack[] {
                createGrapple(),
                createFury(),
                createSnare(),
                createBossPumpkin(),
                createRiftThorn()
        };
    }

    /** Legendy w zrzucie: klatka, dynia, kolce. */
    public ItemStack[] zrzutLegendaries() {
        return new ItemStack[] {
                createSnare(),
                createBossPumpkin(),
                createRiftThorn()
        };
    }

    /** Losowa legenda ze zrzutu (ruletka + loot). */
    public ItemStack randomZrzutLegendary() {
        ItemStack[] pool = zrzutLegendaries();
        return pool[new Random().nextInt(pool.length)].clone();
    }

    private static String rarity() {
        return ChatColor.GOLD + "✦ " + ChatColor.YELLOW + "" + ChatColor.BOLD + "LEGENDARNY" + ChatColor.GOLD + " ✦";
    }

    // ─── Hak z Lina ────────────────────────────────────────────────────────────────
    public ItemStack createGrapple() {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "✸ Hak Pustkowi ✸");
        meta.setLore(Arrays.asList(
                rarity(),
                "",
                ChatColor.GRAY + "Wykuty przez zaginionych lowcow,",
                ChatColor.GRAY + "lina nigdy nie chybia celu.",
                "",
                ChatColor.WHITE + "» " + ChatColor.GRAY + "Zarzuc hak i zwin, a zostaniesz",
                ChatColor.GRAY + "   blyskawicznie przyciagniety w jego strone.",
                ChatColor.WHITE + "» " + ChatColor.GREEN + "Brak obrazen z upadku po uzyciu.",
                "",
                ChatColor.DARK_GRAY + "Cooldown: 4s"
        ));
        meta.addEnchant(Enchantment.LURE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.setCustomModelData(CMD_GRAPPLE);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, GRAPPLE);
        item.setItemMeta(meta);
        return item;
    }

    // ─── Furia Berserkera ────────────────────────────────────────────────────────────
    public ItemStack createFury() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✸ Berlo Furii ✸");
        meta.setLore(Arrays.asList(
                rarity(),
                "",
                ChatColor.GRAY + "Plonie gniewem dawnych wojownikow.",
                ChatColor.GRAY + "Kto je dzierzy, nie zna strachu.",
                "",
                ChatColor.WHITE + "» " + ChatColor.GRAY + "PPM: wpadasz w bojowy szal na " + ChatColor.RED + "5s",
                ChatColor.RED + "   ▪ Sila III",
                ChatColor.AQUA + "   ▪ Szybkosc III",
                "",
                ChatColor.DARK_GRAY + "Cooldown: 40s"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setCustomModelData(CMD_FURY);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, FURY);
        item.setItemMeta(meta);
        return item;
    }

    // ─── Sidla Lowcy ─────────────────────────────────────────────────────────────────
    public ItemStack createSnare() {
        ItemStack item = new ItemStack(Material.GHAST_TEAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✸ Klatka Pojedynku ✸");
        meta.setLore(Arrays.asList(
                rarity(),
                "",
                ChatColor.GRAY + "Nici pustki splataja wymiar pojedynku.",
                ChatColor.GRAY + "Reszta swiata was nie widzi.",
                "",
                ChatColor.WHITE + "» " + ChatColor.GRAY + "Traf gracza tym przedmiotem.",
                ChatColor.WHITE + "» " + ChatColor.GRAY + "Wokół was tworzy sie border " + ChatColor.LIGHT_PURPLE + "10x10",
                ChatColor.GRAY + "   na maks. " + ChatColor.YELLOW + "20s" + ChatColor.GRAY + ".",
                ChatColor.GRAY + "   Bez rzucania i bez teleportu.",
                "",
                ChatColor.DARK_GRAY + "Cooldown: 1.5 min"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setCustomModelData(CMD_SNARE);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, SNARE);
        item.setItemMeta(meta);
        return item;
    }

    // ─── Dynia Bossa ───────────────────────────────────────────────────────────────
    public ItemStack createBossPumpkin() {
        ItemStack item = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "✸ Dynia Bossa ✸");
        meta.setLore(Arrays.asList(
                rarity(),
                "",
                ChatColor.GRAY + "Wypalona pieczecia upadlego wladcy dyni.",
                ChatColor.GRAY + "Nosi ja ten, kto przetrwal najgorsze.",
                "",
                ChatColor.WHITE + "» " + ChatColor.GRAY + "Zaloz na glowe:",
                ChatColor.BLUE + "   ▪ Odpornosc I",
                ChatColor.DARK_GRAY + "   ▪ Ukryta ochrona VII",
                "",
                ChatColor.DARK_GRAY + "Pasywnie, bez cooldownu"
        ));
        meta.addEnchant(Enchantment.PROTECTION, 7, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE);
        meta.setUnbreakable(true);
        meta.setCustomModelData(CMD_BOSS_PUMPKIN);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, BOSS_PUMPKIN);
        item.setItemMeta(meta);
        return item;
    }

    // ─── Kolce Rozdarcia ───────────────────────────────────────────────────────────
    public ItemStack createRiftThorn() {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "✸ Kolce Rozdarcia ✸");
        meta.setLore(Arrays.asList(
                rarity(),
                "",
                ChatColor.GRAY + "Odłamek kryształu z rozpękniętej areny.",
                ChatColor.GRAY + "Kazde trafienie wysysa sile przeciwnika.",
                "",
                ChatColor.WHITE + "» " + ChatColor.GRAY + "Traf gracza w walce wręcz:",
                ChatColor.GRAY + "   ▪ " + ChatColor.RED + "Slabosc I" + ChatColor.GRAY + " na " + ChatColor.YELLOW + "10s",
                ChatColor.GRAY + "   ▪ " + ChatColor.AQUA + "Spowolnienie II" + ChatColor.GRAY + " na " + ChatColor.YELLOW + "3s",
                "",
                ChatColor.DARK_GRAY + "Cooldown: 30s"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setCustomModelData(CMD_RIFT_THORN);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, RIFT_THORN);
        item.setItemMeta(meta);
        return item;
    }

    // ─── Brzuszek Smoka ──────────────────────────────────────────────────────────────
    public ItemStack createDragonBelly() {
        ItemStack item = new ItemStack(Material.SHULKER_SHELL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Brzuszek " +
                          ChatColor.LIGHT_PURPLE + ChatColor.BOLD + "Smoka");
        meta.setLore(Arrays.asList(
                rarity(),
                "",
                ChatColor.GRAY + "Brzuszek pradawnego smoka kresu,",
                ChatColor.GRAY + "pożera dusze pokonanych wrogów.",
                "",
                ChatColor.WHITE + "» " + ChatColor.GRAY + "Posiadając w ekwipunku po " + ChatColor.RED + "zabiciu",
                ChatColor.GRAY + "   " + ChatColor.WHITE + "gracza" + ChatColor.GRAY + " jego przedmioty trafiają",
                ChatColor.GRAY + "   do " + ChatColor.DARK_PURPLE + "Brzuszka " + ChatColor.LIGHT_PURPLE + "Smoka",
                "",
                ChatColor.GOLD + "» PPM: otwórz brzuszek!",
                "",
                ChatColor.DARK_GRAY + "Pasywnie, bez cooldownu"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.setCustomModelData(CMD_DRAGON_BELLY);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, DRAGON_BELLY);
        item.setItemMeta(meta);
        return item;
    }

    // ─── Smoczy Szpon ────────────────────────────────────────────────────────────────
    public ItemStack createDragonClaw() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Smoczy Szpon");
        meta.setLore(Arrays.asList(
                rarity(),
                "",
                ChatColor.GRAY + "Szpon wykuty z pazura smoka kresu,",
                ChatColor.GRAY + "każde uderzenie wzmacnia jego moc.",
                "",
                ChatColor.WHITE + "» " + ChatColor.GRAY + "Każde " + ChatColor.RED + "uderzenie" + ChatColor.GRAY + " zwiększa",
                ChatColor.GRAY + "   obrażenia o " + ChatColor.WHITE + "5%" + ChatColor.GRAY + " (max " + ChatColor.WHITE + "150%" + ChatColor.GRAY + ")",
                ChatColor.WHITE + "» " + ChatColor.GRAY + "Otrzymanie obrażeń " + ChatColor.RED + "resetuje" + ChatColor.GRAY + " bonus",
                "",
                ChatColor.DARK_GRAY + "Pasywnie, bez cooldownu"
        ));
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.setCustomModelData(CMD_DRAGON_CLAW);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, DRAGON_CLAW);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createWardenSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_AQUA + "Warden Sword");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "» Każde trafienie ładuje miecz o " + ChatColor.AQUA + "5%",
                ChatColor.GRAY + "» Przy " + ChatColor.AQUA + "100%" + ChatColor.GRAY + " odblokowuje " + ChatColor.AQUA + "Rage",
                "",
                ChatColor.WHITE + "» " + ChatColor.GRAY + "Shift + PPM: " + ChatColor.AQUA + "Sonic Boom",
                ChatColor.GRAY + "   ▪ " + ChatColor.RED + "5 serc" + ChatColor.GRAY + " true damage",
                ChatColor.GRAY + "   ▪ " + ChatColor.AQUA + "Szybkość II" + ChatColor.GRAY + " na " + ChatColor.YELLOW + "10s",
                ChatColor.GRAY + "   ▪ Ładunek wraca do " + ChatColor.WHITE + "0%"
        ));
        meta.setCustomModelData(CMD_WARDEN_SWORD);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, WARDEN_SWORD);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createNetherPick() {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Kilof Otchłani");
        meta.setLore(Arrays.asList(
                rarity(),
                "",
                ChatColor.GRAY + "Wykuty z netheritu i zaklęty ogniem piekieł.",
                ChatColor.GRAY + "Skała pęka wokół niego jak szkło.",
                "",
                ChatColor.WHITE + "» " + ChatColor.GRAY + "Niszczy obszar " + ChatColor.RED + "3×3",
                ChatColor.WHITE + "» " + ChatColor.RED + "W walce" + ChatColor.GRAY + " działa jak zwykły kilof",
                ChatColor.WHITE + "» " + ChatColor.GOLD + "Efficiency V" + ChatColor.DARK_GRAY + "  |  "
                        + ChatColor.AQUA + "Unbreaking III" + ChatColor.DARK_GRAY + "  |  "
                        + ChatColor.GREEN + "Mending",
                "",
                ChatColor.DARK_GRAY + "Pasywnie, bez cooldownu"
        ));
        meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, NETHER_PICK);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createPickShard() {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Odłamek Kilofa");
        meta.setLore(Arrays.asList(
                ChatColor.RED + "✦ " + ChatColor.GRAY + "Odłamek z piekielnego rdzenia.",
                "",
                ChatColor.WHITE + "» " + ChatColor.GRAY + "Składnik craftingu "
                        + ChatColor.DARK_RED + ChatColor.BOLD + "Kilofa Otchłani",
                ChatColor.DARK_GRAY + "Zwykły Echo Shard nie zadziała."
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, PICK_SHARD);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPickShard(ItemStack item) {
        return PICK_SHARD.equals(getId(item));
    }

    public ItemStack createVoidBow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Kataklizm Pustki");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + ShopManager.ARROW + " Jest to przedmiot z:",
                ChatColor.GRAY + ShopManager.ARROW + " Bossa władcy lochów (Lochy)",
                "",
                ChatColor.GRAY + ShopManager.ARROW + " Łuk posiada 2 Customowe moce",
                ChatColor.GRAY + ShopManager.ARROW + " 1. Wystrzel strzałę aby stworzyć czarną dziurę",
                ChatColor.GRAY + ShopManager.ARROW + " Shift + PPM aby zmienić " + ChatColor.RED + "moc",
                ChatColor.GRAY + ShopManager.ARROW + " 2. Traf strzałą w gracza aby zadać mu 3.5 serca!",
                ""
        ));
        meta.setUnbreakable(true);
        meta.setCustomModelData(CMD_VOID_BOW);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, VOID_BOW);
        meta.getPersistentDataContainer().set(voidModeKey, PersistentDataType.INTEGER, 0);
        item.setItemMeta(meta);
        return item;
    }

    public int getVoidMode(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer mode = item.getItemMeta().getPersistentDataContainer()
                .get(voidModeKey, PersistentDataType.INTEGER);
        return mode == null ? 0 : mode;
    }

    public void setVoidMode(ItemStack item, int mode) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(voidModeKey, PersistentDataType.INTEGER, mode == 1 ? 1 : 0);
        item.setItemMeta(meta);
    }
}
