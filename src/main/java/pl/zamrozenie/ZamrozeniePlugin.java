package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import pl.zamrozenie.clans.commands.ClanCommand;
import pl.zamrozenie.clans.listeners.*;
import pl.zamrozenie.clans.managers.*;

public class ZamrozeniePlugin extends JavaPlugin {

    private ZamrozenieManager manager;
    private HomeManager homeManager;
    private TpaManager tpaManager;
    private LifestealManager lifestealManager;
    private LegendaryItems legendaryItems;
    private ZrzutManager zrzutManager;
    private DuelCageManager duelCageManager;
    private LegendaryArmorListener legendaryArmorListener;
    private DragonManager dragonManager;
    private DragonItemsListener dragonItemsListener;
    private DragonEventItemsListener dragonEventItemsListener;
    private VoidBowListener voidBowListener;
    private DropManager dropManager;
    private ShopManager shopManager;
    private StatsManager statsManager;
    private MarketManager marketManager;
    private WeaponCooldownListener weaponCooldowns;
    private LocatorBarManager locatorBar;
    private CombatTagManager combatTag;
    private BossEventManager bossEvents;
    private pl.zamrozenie.anticheat.AntiCheat antiCheat;
    
    // Clan system
    private pl.zamrozenie.clans.managers.DataManager clanDataManager;
    private ClanManager clanManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager          = new ZamrozenieManager(this);
        homeManager      = new HomeManager(this);
        tpaManager       = new TpaManager(this);
        lifestealManager = new LifestealManager(this);
        legendaryItems   = new LegendaryItems(this);
        zrzutManager     = new ZrzutManager(this, lifestealManager, legendaryItems);
        duelCageManager  = new DuelCageManager(this);
        legendaryArmorListener = new LegendaryArmorListener(this, legendaryItems);
        dragonManager    = new DragonManager(this);
        dragonItemsListener = new DragonItemsListener(this, dragonManager);
        dragonEventItemsListener = new DragonEventItemsListener(this, legendaryItems);
        dropManager = new DropManager(this);
        combatTag = new CombatTagManager(this);
        bossEvents = new BossEventManager(this);
        shopManager = new ShopManager(this);
        statsManager = new StatsManager(this);
        marketManager = new MarketManager(this, shopManager.getEconomy());
        registerPlaceholders();
        
        // Initialize clan system
        clanDataManager  = new pl.zamrozenie.clans.managers.DataManager(this);
        clanManager      = new ClanManager(this, clanDataManager);

        HomeCommand.setHomeManagerInstance(homeManager);

        TpaCommand      tpaCmd      = new TpaCommand(tpaManager);
        LifestealCommand lsCmd      = new LifestealCommand(lifestealManager);

        getCommand("zamrozenie").setExecutor(new ZamrozenieCommand(this, manager));
        getCommand("home").setExecutor(new HomeCommand(homeManager));
        getCommand("home").setTabCompleter(new HomeCommand(homeManager));
        getCommand("tpa").setExecutor(tpaCmd);
        getCommand("tpa").setTabCompleter(tpaCmd);
        getCommand("tpaccept").setExecutor(tpaCmd);
        getCommand("tpadeny").setExecutor(tpaCmd);
        getCommand("wysrodkuj").setExecutor(new WysrodkujCommand());
        getCommand("lifesteal").setExecutor(lsCmd);
        getCommand("lifesteal").setTabCompleter(lsCmd);
        getCommand("wyplacserce").setExecutor(new WyplacSerceCommand(lifestealManager));

        AdminZrzutCommand adminZrzutCmd = new AdminZrzutCommand(zrzutManager);
        getCommand("zrzut").setExecutor(new ZrzutCommand(zrzutManager));
        getCommand("adminzrzut").setExecutor(adminZrzutCmd);
        getCommand("adminzrzut").setTabCompleter(adminZrzutCmd);
        getCommand("adminzrzuttest").setExecutor(adminZrzutCmd);
        getCommand("adminzrzutotworz").setExecutor(adminZrzutCmd);
        getCommand("aktywujsmoka").setExecutor(new AktywujSmokaCommand(dragonManager));
        getCommand("smok").setExecutor(new DragonGuiCommand(dragonManager));
        getCommand("dragongive").setExecutor(new DragonGiveCommand(dragonManager));
        getCommand("dragongive").setTabCompleter(new DragonGiveCommand(dragonManager));
        getCommand("wardensword").setExecutor(new WardenSwordCommand(legendaryItems));
        getCommand("kataklizm").setExecutor(new VoidBowCommand(legendaryItems));
        getCommand("kilof3x3").setExecutor(new NetherPickCommand(legendaryItems));
        getCommand("odlamekkilofa").setExecutor(new PickShardCommand(legendaryItems));
        getServer().getPluginManager().registerEvents(new NetherPickListener(this, legendaryItems), this);
        getServer().getPluginManager().registerEvents(
                new NetherPickCraftListener(this, legendaryItems, lifestealManager), this);
        voidBowListener = new VoidBowListener(this, legendaryItems);
        getServer().getPluginManager().registerEvents(voidBowListener, this);
        
        // Register clan command
        ClanCommand clanCommand = new ClanCommand(clanManager, clanDataManager);
        getCommand("klan").setExecutor(clanCommand);
        getCommand("klan").setTabCompleter(clanCommand);

        getServer().getPluginManager().registerEvents(new ZamrozenieListener(manager), this);
        getServer().getPluginManager().registerEvents(new HomeListener(homeManager), this);
        getServer().getPluginManager().registerEvents(new LifestealListener(this, lifestealManager), this);
        getServer().getPluginManager().registerEvents(new ZrzutListener(zrzutManager, legendaryItems, duelCageManager), this);
        getServer().getPluginManager().registerEvents(legendaryArmorListener, this);
        getServer().getPluginManager().registerEvents(new DuelCageListener(duelCageManager), this);
        getServer().getPluginManager().registerEvents(new DragonListener(dragonManager), this);
        getServer().getPluginManager().registerEvents(new DragonGuiListener(this, dragonManager), this);
        getServer().getPluginManager().registerEvents(dragonItemsListener, this);
        getServer().getPluginManager().registerEvents(dragonEventItemsListener, this);
        getServer().getPluginManager().registerEvents(new WardenSwordListener(this, legendaryItems), this);
        
        // Register clan listeners
        getServer().getPluginManager().registerEvents(new CombatListener(clanManager), this);
        PlayerListener playerListener = new PlayerListener(this, clanManager, clanDataManager);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(new ChatListener(clanManager, clanDataManager), this);
        
        // Ustaw PlayerListener w ClanManager dla natychmiastowej aktualizacji tagow
        clanManager.setPlayerListener(playerListener);

        getServer().getPluginManager().registerEvents(new MaceEnchantBlockListener(), this);

        AdminDropCommand adropCmd = new AdminDropCommand(dropManager);
        getCommand("drop").setExecutor(new DropCommand(dropManager));
        TutorialCommand tutorialCmd = new TutorialCommand();
        getCommand("tutorial").setExecutor(tutorialCmd);
        getServer().getPluginManager().registerEvents(tutorialCmd, this);
        getServer().getPluginManager().registerEvents(new HiddenCommandsListener(), this);
        getCommand("adrop").setExecutor(adropCmd);
        getCommand("adrop").setTabCompleter(adropCmd);
        getServer().getPluginManager().registerEvents(new DropGuiListener(dropManager), this);
        getServer().getPluginManager().registerEvents(new DropDeathListener(dropManager), this);
        getCommand("shop").setExecutor(new ShopCommand(shopManager));
        TopkiCommand topkiCmd = new TopkiCommand(statsManager, shopManager.getEconomy());
        getCommand("topki").setExecutor(topkiCmd);
        getServer().getPluginManager().registerEvents(topkiCmd, this);
        getServer().getPluginManager().registerEvents(new StatsListener(statsManager), this);
        getServer().getPluginManager().registerEvents(new VillagerPieListener(), this);
        getCommand("kepicgowno").setExecutor(new KepicGownoCommand());
        AdminShopCommand asklep = new AdminShopCommand(shopManager);
        getCommand("asklep").setExecutor(asklep);
        getCommand("asklep").setTabCompleter(asklep);
        getServer().getPluginManager().registerEvents(new ShopGuiListener(shopManager), this);
        getServer().getPluginManager().registerEvents(new ShopChatListener(shopManager), this);
        getCommand("rynek").setExecutor(new MarketCommand(marketManager));
        getCommand("wystaw").setExecutor(new WystawCommand(marketManager));
        getServer().getPluginManager().registerEvents(new MarketGuiListener(marketManager), this);
        getServer().getPluginManager().registerEvents(new MarketChatListener(marketManager), this);
        getServer().getPluginManager().registerEvents(new MarketSignListener(marketManager), this);

        getServer().getPluginManager().registerEvents(new CombatTagListener(combatTag), this);
        combatTag.start();
        getServer().getPluginManager().registerEvents(new ItemLimitListener(this), this);
        getServer().getPluginManager().registerEvents(new PearlRulesListener(this), this);
        getServer().getPluginManager().registerEvents(new NetherDebrisListener(this), this);
        getServer().getPluginManager().registerEvents(new NetherAccessListener(this), this);
        getServer().getPluginManager().registerEvents(new NetherBedListener(), this);
        getServer().getPluginManager().registerEvents(new CrystalRulesListener(this), this);

        getServer().getPluginManager().registerEvents(new BossEventListener(bossEvents), this);
        ZrespBossaCommand zrespCmd = new ZrespBossaCommand(bossEvents);
        getCommand("zrespbossa").setExecutor(zrespCmd);
        getCommand("zrespbossa").setTabCompleter(zrespCmd);

        // Cooldowny Mace / Spear
        weaponCooldowns = new WeaponCooldownListener(this);
        getServer().getPluginManager().registerEvents(weaponCooldowns, this);
        weaponCooldowns.start();

        // Locator bar: widocznosc w 100 kratkach niezaleznie od dyni
        locatorBar = new LocatorBarManager(this);
        getServer().getPluginManager().registerEvents(locatorBar, this);
        locatorBar.start();

        // AntiCheat
        antiCheat = new pl.zamrozenie.anticheat.AntiCheat(this);
        antiCheat.start();
        antiCheat.setAnalyzer(new pl.zamrozenie.anticheat.BehaviorAnalyzer(antiCheat));
        getServer().getPluginManager().registerEvents(new pl.zamrozenie.anticheat.MovementChecks(antiCheat), this);
        getServer().getPluginManager().registerEvents(new pl.zamrozenie.anticheat.CombatChecks(antiCheat), this);
        getServer().getPluginManager().registerEvents(new pl.zamrozenie.anticheat.WorldChecks(antiCheat), this);
        pl.zamrozenie.anticheat.AntiXrayConfigurator antiXray = new pl.zamrozenie.anticheat.AntiXrayConfigurator(this);
        antiXray.run();
        antiCheat.setAntiXray(antiXray);
        getServer().getPluginManager().registerEvents(antiXray, this);
        pl.zamrozenie.anticheat.AcCommand acCmd = new pl.zamrozenie.anticheat.AcCommand(antiCheat);
        getCommand("ac").setExecutor(acCmd);
        getCommand("ac").setTabCompleter(acCmd);

        legendaryArmorListener.start();

        registerHeartRecipe();

        getLogger().info("Plugin Zamrozenie wlaczony!");
    }

    @Override
    public void onDisable() {
        if (manager != null && manager.isFrozen()) {
            manager.stopFreeze(false);
        }
        if (lifestealManager != null) lifestealManager.save();
        if (zrzutManager != null) { zrzutManager.shutdown(); zrzutManager.save(); }
        if (duelCageManager != null) duelCageManager.shutdown();
        if (legendaryArmorListener != null) legendaryArmorListener.stop();
        if (dragonManager != null) dragonManager.shutdown();
        if (dropManager != null) dropManager.shutdown();
        if (statsManager != null) statsManager.save();
        if (shopManager != null) shopManager.shutdown();
        if (marketManager != null) marketManager.shutdown();
        if (weaponCooldowns != null) weaponCooldowns.stop();
        if (locatorBar != null) locatorBar.stop();
        if (combatTag != null) combatTag.stop();
        if (bossEvents != null) bossEvents.shutdown();
        if (voidBowListener != null) voidBowListener.shutdown();
        if (antiCheat != null) antiCheat.stop();
        
        // Zapisz dane klanow
        if (clanDataManager != null) clanDataManager.saveData();
        
        getLogger().info("Plugin Zamrozenie wylaczony.");
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("Brak PlaceholderAPI — %zamrozenie_money% nie bedzie dzialac.");
            return;
        }
        new ZamrozeniePlaceholders(this).register();
        getLogger().info("PlaceholderAPI: %zamrozenie_money%");
    }

    /**
     * Rejestruje crafting serca:
     *   G G G      (G = Blok Zlota)
     *   D N D      (D = Blok Diamentu,  N = Nether Star)
     *   D D D
     */
    private void registerHeartRecipe() {
        NamespacedKey key = new NamespacedKey(this, "lifesteal_heart");
        Bukkit.removeRecipe(key);

        ShapedRecipe recipe = new ShapedRecipe(key, lifestealManager.createHeartItem());
        recipe.shape("GGG", "DND", "DDD");
        recipe.setIngredient('G', Material.GOLD_BLOCK);
        recipe.setIngredient('D', Material.DIAMOND_BLOCK);
        recipe.setIngredient('N', Material.NETHER_STAR);
        Bukkit.addRecipe(recipe);
    }

    public ZamrozenieManager getManager()          { return manager; }
    public HomeManager getHomeManager()            { return homeManager; }
    public TpaManager getTpaManager()              { return tpaManager; }
    public LifestealManager getLifestealManager()  { return lifestealManager; }
    public DropManager getDropManager()            { return dropManager; }
    public ShopManager getShopManager()            { return shopManager; }
    public StatsManager getStatsManager()          { return statsManager; }
    public CombatTagManager getCombatTag()         { return combatTag; }
    public BossEventManager getBossEvents()        { return bossEvents; }
    public ClanManager getClanManager()            { return clanManager; }
    public pl.zamrozenie.anticheat.AntiCheat getAntiCheat() { return antiCheat; }
}
