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
import java.util.UUID;

public class TopkiCommand implements CommandExecutor, Listener {

    private static final int SLOT_MONEY = 10;
    private static final int SLOT_KILLS = 13;
    private static final int SLOT_DEATHS = 16;
    private static final int TOP_SIZE = 10;

    private final StatsManager stats;
    private final ShopEconomy economy;

    public TopkiCommand(StatsManager stats, ShopEconomy economy) {
        this.stats = stats;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Tylko gracz moze otworzyc /topki.");
            return true;
        }
        open(player);
        return true;
    }

    public void open(Player player) {
        TopkiGuiHolder holder = new TopkiGuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Topki serwera");
        holder.setInventory(inv);

        ItemStack pane = pane();
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        UUID id = player.getUniqueId();
        inv.setItem(SLOT_MONEY, icon(Material.EMERALD,
                ChatColor.GREEN + "" + ChatColor.BOLD + "Topka siana",
                moneyLore(id)));
        inv.setItem(SLOT_KILLS, icon(Material.IRON_SWORD,
                ChatColor.RED + "" + ChatColor.BOLD + "Topka zabójstw",
                killsLore(id)));
        inv.setItem(SLOT_DEATHS, icon(Material.SKELETON_SKULL,
                ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Topka śmierci",
                deathsLore(id)));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.35f, 1.4f);
    }

    private List<String> moneyLore(UUID id) {
        List<String> lore = header(
                ChatColor.GRAY + "Najbogatsi gracze na serwerze.");
        List<StatsManager.Entry> top = stats.topMoney(TOP_SIZE);
        if (top.isEmpty()) {
            lore.add(line(ChatColor.DARK_GRAY + "Na razie pusto..."));
        } else {
            int rank = 1;
            for (StatsManager.Entry e : top) {
                lore.add(ranked(rank, e.name(), ShopManager.money(e.value() / 100.0), e.id().equals(id)));
                rank++;
            }
        }
        lore.add("");
        lore.add(arrow() + ChatColor.GRAY + "Twoje miejsce: " + ChatColor.YELLOW + "#" + stats.moneyPlace(id));
        lore.add(arrow() + ChatColor.GRAY + "Saldo: " + ShopManager.money(economy.get(id)));
        return lore;
    }

    private List<String> killsLore(UUID id) {
        List<String> lore = header(
                ChatColor.GRAY + "Kto najczęściej wygrywa walki.");
        List<StatsManager.Entry> top = stats.topKills(TOP_SIZE);
        if (top.isEmpty()) {
            lore.add(line(ChatColor.DARK_GRAY + "Na razie pusto..."));
        } else {
            int rank = 1;
            for (StatsManager.Entry e : top) {
                lore.add(ranked(rank, e.name(), ChatColor.RED + "" + e.value() + ChatColor.GRAY + " zabójstw", e.id().equals(id)));
                rank++;
            }
        }
        lore.add("");
        lore.add(arrow() + ChatColor.GRAY + "Twoje miejsce: " + ChatColor.YELLOW + "#" + stats.killsPlace(id));
        lore.add(arrow() + ChatColor.GRAY + "Twoje zabójstwa: " + ChatColor.WHITE + stats.getKills(id));
        return lore;
    }

    private List<String> deathsLore(UUID id) {
        List<String> lore = header(
                ChatColor.GRAY + "Kto najczęściej odpada z walki.");
        List<StatsManager.Entry> top = stats.topDeaths(TOP_SIZE);
        if (top.isEmpty()) {
            lore.add(line(ChatColor.DARK_GRAY + "Na razie pusto..."));
        } else {
            int rank = 1;
            for (StatsManager.Entry e : top) {
                lore.add(ranked(rank, e.name(), ChatColor.DARK_RED + "" + e.value() + ChatColor.GRAY + " śmierci", e.id().equals(id)));
                rank++;
            }
        }
        lore.add("");
        lore.add(arrow() + ChatColor.GRAY + "Twoje miejsce: " + ChatColor.YELLOW + "#" + stats.deathsPlace(id));
        lore.add(arrow() + ChatColor.GRAY + "Twoje śmierci: " + ChatColor.WHITE + stats.getDeaths(id));
        return lore;
    }

    private static List<String> header(String subtitle) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "§m                          ");
        lore.add(subtitle);
        lore.add("");
        return lore;
    }

    private static String ranked(int rank, String name, String value, boolean you) {
        ChatColor place = switch (rank) {
            case 1 -> ChatColor.GOLD;
            case 2 -> ChatColor.GRAY;
            case 3 -> ChatColor.RED;
            default -> ChatColor.DARK_GRAY;
        };
        String marker = you ? ChatColor.AQUA + " « TY" : "";
        return arrow() + place + "#" + rank + " " + ChatColor.WHITE + name
                + ChatColor.DARK_GRAY + " — " + value + marker;
    }

    private static String line(String text) {
        return arrow() + text;
    }

    private static String arrow() {
        return ChatColor.GRAY + ShopManager.ARROW + " ";
    }

    private static ItemStack pane() {
        ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(" ");
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        m.setLore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TopkiGuiHolder)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof TopkiGuiHolder)) return;
        event.setCancelled(true);
    }
}
