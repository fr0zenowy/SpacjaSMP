package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class TutorialCommand implements CommandExecutor, Listener {

    private static String arrow() {
        return ChatColor.GRAY + ShopManager.ARROW + " ";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Tylko gracz moze otworzyc /tutorial.");
            return true;
        }
        open(player);
        return true;
    }

    public void open(Player player) {
        TutorialGuiHolder holder = new TutorialGuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Poradnik serwera");
        holder.setInventory(inv);

        ItemStack pane = pane();
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        inv.setItem(4, icon(Material.KNOWLEDGE_BOOK, ChatColor.WHITE + "" + ChatColor.BOLD + "Witaj na serwerze",
                arrow() + "To SMP z PvP, lifestealem i eventami.",
                arrow() + "Cel: zbieraj serca, walcz, farm bossów i zrzuty.",
                "",
                arrow() + ChatColor.WHITE + "/tutorial" + ChatColor.GRAY + " — ten poradnik",
                arrow() + ChatColor.WHITE + "/drop" + ChatColor.GRAY + " — co spada z bossów i zrzutu",
                arrow() + ChatColor.WHITE + "/zrzut" + ChatColor.GRAY + " — godzina i status zrzutu"));

        inv.setItem(20, icon(Material.GOLDEN_APPLE, ChatColor.RED + "" + ChatColor.BOLD + "Lifesteal",
                arrow() + "Startujesz z " + ChatColor.WHITE + "10 sercami" + ChatColor.GRAY + ", max " + ChatColor.WHITE + "20.",
                arrow() + "Śmierć zabiera " + ChatColor.WHITE + "1 serce" + ChatColor.GRAY + " (także nie-PvP).",
                arrow() + "Zabójca dostaje serce. Ten sam gracz/IP ma cooldown.",
                arrow() + "Przy " + ChatColor.WHITE + "0 sercach" + ChatColor.GRAY + " dostajesz bana lifestealowego.",
                arrow() + ChatColor.WHITE + "/lifesteal" + ChatColor.GRAY + " i " + ChatColor.WHITE + "/wyplacserce"));

        inv.setItem(21, icon(Material.IRON_SWORD, ChatColor.DARK_RED + "" + ChatColor.BOLD + "Walka i antilogout",
                arrow() + "PvP daje " + ChatColor.WHITE + "30s" + ChatColor.GRAY + " tagu walki.",
                arrow() + "Perła nabija tag dopiero gdy " + ChatColor.WHITE + "wylądujesz" + ChatColor.GRAY + ", nie przy rzucie.",
                arrow() + "Cooldown perły: " + ChatColor.WHITE + "10s" + ChatColor.GRAY + ". W walce Riptide: "
                        + ChatColor.WHITE + "20s" + ChatColor.GRAY + ".",
                arrow() + "W tagu " + ChatColor.WHITE + "nie działa" + ChatColor.GRAY + " większość komend,",
                arrow() + "ender chest i shulkery są zablokowane. Wylog = śmierć (itemy + serce)."));

        inv.setItem(22, icon(Material.RED_BED, ChatColor.GOLD + "" + ChatColor.BOLD + "Domy, TPA, klany",
                arrow() + ChatColor.WHITE + "/home" + ChatColor.GRAY + " — teleport do ustawionego domu.",
                arrow() + ChatColor.WHITE + "/tpa" + ChatColor.GRAY + " / " + ChatColor.WHITE + "/tpaccept" + ChatColor.GRAY + " / " + ChatColor.WHITE + "/tpadeny",
                arrow() + ChatColor.WHITE + "/klan" + ChatColor.GRAY + " — tworzenie klanu, zaproszenia (max 4 osoby).",
                arrow() + ChatColor.WHITE + "/klan kolor" + ChatColor.GRAY + " — kolor tagu na czacie, tabie i przy nicku.",
                arrow() + "Tag klanu widać na czacie, nad głową i na tabie."));

        inv.setItem(23, icon(Material.EMERALD, ChatColor.GREEN + "" + ChatColor.BOLD + "Sklep i rynek",
                arrow() + ChatColor.WHITE + "/shop" + ChatColor.GRAY + " — skup i sprzedaż serwerowa.",
                arrow() + "Saldo: " + ChatColor.WHITE + "%zamrozenie_money%",
                arrow() + ChatColor.WHITE + "/rynek" + ChatColor.GRAY + " — oferty graczy.",
                arrow() + ChatColor.WHITE + "/wystaw" + ChatColor.GRAY + " — wystaw item z ręki.",
                arrow() + ChatColor.WHITE + "/topki" + ChatColor.GRAY + " — siało, zabójstwa, śmierci.",
                arrow() + "Hajs nie ginie przy śmierci."));

        inv.setItem(24, icon(Material.DIAMOND, ChatColor.AQUA + "" + ChatColor.BOLD + "Drop z bossów",
                arrow() + ChatColor.WHITE + "/drop" + ChatColor.GRAY + " pokazuje pule ze " + ChatColor.WHITE + "Władcy Endu,",
                arrow() + ChatColor.WHITE + "Jednogłowego Cerbera" + ChatColor.GRAY + " i " + ChatColor.WHITE + "Władcy Lochów.",
                arrow() + "Zwykły drop to losowe itemy z puli.",
                arrow() + "Nagrodę z eventu dostaje tylko " + ChatColor.GOLD + "TOP 1" + ChatColor.GRAY + " obrażeń.",
                arrow() + "Władca Lochów: 5 itemów z puli + eventówka dla TOP 1."));

        inv.setItem(29, icon(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Eventówki",
                arrow() + "Jeden wariant to " + ChatColor.WHITE + "100%" + ChatColor.GRAY + ". Dwa w slocie = "
                        + ChatColor.WHITE + "50/50" + ChatColor.GRAY + " — dropnie tylko jeden.",
                arrow() + "W " + ChatColor.WHITE + "/drop" + ChatColor.GRAY + " para miga co sekundę.",
                arrow() + "Przykłady: Kataklizm Pustki, Warden Sword, itemy smoka.",
                arrow() + "Zrzut " + ChatColor.WHITE + "nie daje" + ChatColor.GRAY + " eventówek."));

        inv.setItem(30, icon(Material.CHEST, ChatColor.YELLOW + "" + ChatColor.BOLD + "Zrzut",
                arrow() + ChatColor.WHITE + "/zrzut" + ChatColor.GRAY + " — godzina i status.",
                arrow() + "Skrzynia spada na mapę, 15 min blokady, potem zagadki.",
                arrow() + "Kto pierwszy rozwiąże 3 zagadki, otwiera skrzynię.",
                arrow() + "Loot = dokładnie to, co stoi w " + ChatColor.WHITE + "/drop" + ChatColor.GRAY + " → Zrzut.",
                arrow() + "Bez ruletki i bez eventówek."));

        inv.setItem(31, icon(Material.BOW, ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Eventowe bronie",
                arrow() + ChatColor.WHITE + "Kataklizm Pustki" + ChatColor.GRAY + " — łuk, Shift+PPM zmienia moc.",
                arrow() + "Moc 1: czarna dziura (ssanie + freeze 5s). CD 2.5 min.",
                arrow() + "Moc 2: strzała true dmg " + ChatColor.WHITE + "3.5 serca" + ChatColor.GRAY + " (ten sam CD).",
                arrow() + ChatColor.WHITE + "Warden Sword" + ChatColor.GRAY + " — skill na action barze.",
                arrow() + "Itemy smoka mają własne efekty (brzuch, pazur)."));

        inv.setItem(32, icon(Material.WITHER_SKELETON_SKULL, ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Władca Lochów i bossy",
                arrow() + "Władca Lochów to duży event z scoreboardem i bossbarem.",
                arrow() + "TOP 3 obrażeń widać na tablicy w trakcie walki.",
                arrow() + "Smok Kresu: " + ChatColor.WHITE + "/smok" + ChatColor.GRAY + " i event na Endzie.",
                arrow() + "Cerber i inne bossy też mają drop w " + ChatColor.WHITE + "/drop" + ChatColor.GRAY + "."));

        inv.setItem(33, icon(Material.END_CRYSTAL, ChatColor.GRAY + "" + ChatColor.BOLD + "Zasady PvP",
                arrow() + "Kryształy endu: " + ChatColor.WHITE + "nie da się" + ChatColor.GRAY + " craftować ani stawiać.",
                arrow() + "TNT w minecarcie robi " + ChatColor.WHITE + "20% mniej" + ChatColor.GRAY + " obrażeń.",
                arrow() + "Max " + ChatColor.WHITE + "3 totemy" + ChatColor.GRAY + " i " + ChatColor.WHITE + "8 pereł"
                        + ChatColor.GRAY + " — reszty nie podniesiesz.",
                arrow() + "Nie wylogowuj się w walce — tracisz wszystko jak przy śmierci."));

        inv.setItem(49, icon(Material.OAK_SIGN, ChatColor.WHITE + "" + ChatColor.BOLD + "Przydatne komendy",
                arrow() + ChatColor.WHITE + "/tutorial" + ChatColor.GRAY + " — poradnik",
                arrow() + ChatColor.WHITE + "/drop" + ChatColor.GRAY + " — drop bossów i zrzutu",
                arrow() + ChatColor.WHITE + "/zrzut" + ChatColor.GRAY + " — godzina i status zrzutu",
                arrow() + ChatColor.WHITE + "/shop /rynek /wystaw /topki",
                arrow() + ChatColor.WHITE + "/home /tpa /klan /lifesteal /smok"));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.1f);
    }

    private static ItemStack pane() {
        ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(" ");
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack icon(Material material, String name, String... loreLines) {
        ItemStack it = new ItemStack(material);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        for (String line : loreLines) lore.add(line);
        m.setLore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof TutorialGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TutorialGuiHolder) {
            event.setCancelled(true);
        }
    }
}
