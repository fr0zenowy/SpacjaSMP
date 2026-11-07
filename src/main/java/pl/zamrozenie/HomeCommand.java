package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class HomeCommand implements CommandExecutor, TabCompleter {

    private final HomeManager homeManager;
    private static HomeManager homeManagerInstance;

    public HomeCommand(HomeManager homeManager) {
        this.homeManager = homeManager;
    }

    static void setHomeManagerInstance(HomeManager hm) {
        homeManagerInstance = hm;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Tylko gracze moga uzyc tej komendy.");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "";

        switch (sub) {
            case "set" -> {
                int slot = parseSlot(args, 1);
                if (!ensureValidSlot(player, slot)) return true;
                homeManager.setHome(player, slot, player.getLocation());
                player.sendMessage(ChatColor.GREEN + "✔ Home " + slot + " ustawiony na "
                        + formatLoc(player.getLocation()) + ChatColor.GREEN + ".");
            }
            case "delete", "del", "remove" -> {
                int slot = parseSlot(args, 1);
                if (!ensureValidSlot(player, slot)) return true;
                if (!homeManager.hasHome(player, slot)) {
                    player.sendMessage(ChatColor.RED + "Nie masz ustawionego home " + slot + ".");
                    return true;
                }
                homeManager.deleteHome(player, slot);
                player.sendMessage(ChatColor.YELLOW + "Home " + slot + " został usunięty.");
            }
            case "tp" -> {
                int slot = parseSlot(args, 1);
                if (!ensureValidSlot(player, slot)) return true;
                teleportHome(player, slot);
            }
            default -> openGui(player);
        }
        return true;
    }

    private int parseSlot(String[] args, int defaultSlot) {
        if (args.length < 2) return defaultSlot;
        try {
            int slot = Integer.parseInt(args[1]);
            if (homeManager.isValidSlot(slot)) return slot;
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }

    private boolean ensureValidSlot(Player player, int slot) {
        if (slot != -1) return true;
        player.sendMessage(ChatColor.RED + "Poprawny numer home to tylko " + ChatColor.WHITE + "1" + ChatColor.RED
                + " albo " + ChatColor.WHITE + "2" + ChatColor.RED + ".");
        return false;
    }

    private void teleportHome(Player player, int slot) {
        homeManager.teleportToHome(player, slot);
    }

    public static void openGui(Player player) {
        String title = ChatColor.DARK_AQUA + "\u2726 " + ChatColor.WHITE + "Tw\u00f3j Dom" + ChatColor.DARK_AQUA + " \u2726";
        HomeInventoryHolder holder = new HomeInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);

        // Tło
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        HomeManager hm = homeManagerInstance;
        if (hm != null) {
            fillHomeSlot(inv, player, hm, 1, HomeInventoryHolder.HOME_1_SLOT,
                    HomeInventoryHolder.SET_1_SLOT, HomeInventoryHolder.DELETE_1_SLOT);
            fillHomeSlot(inv, player, hm, 2, HomeInventoryHolder.HOME_2_SLOT,
                    HomeInventoryHolder.SET_2_SLOT, HomeInventoryHolder.DELETE_2_SLOT);
        }

        player.openInventory(inv);
    }

    private static void fillHomeSlot(Inventory inv, Player player, HomeManager hm, int slot,
                                     int displaySlot, int setSlot, int deleteSlot) {
        if (hm.hasHome(player, slot)) {
            Location home = hm.getHome(player, slot);
            ItemStack bed = makeItem(Material.RED_BED,
                    ChatColor.WHITE + "" + ChatColor.BOLD + "Home " + slot,
                    ChatColor.GRAY + "Lewy klik: teleport",
                    ChatColor.GRAY + "Świat: " + ChatColor.AQUA + home.getWorld().getName(),
                    ChatColor.GRAY + "X: " + ChatColor.WHITE + Math.round(home.getX()),
                    ChatColor.GRAY + "Y: " + ChatColor.WHITE + Math.round(home.getY()),
                    ChatColor.GRAY + "Z: " + ChatColor.WHITE + Math.round(home.getZ()));
            inv.setItem(displaySlot, bed);

            ItemStack setBtn = makeItem(Material.LIME_STAINED_GLASS_PANE,
                    ChatColor.GREEN + "" + ChatColor.BOLD + "Ustaw / Nadpisz Home " + slot,
                    ChatColor.GRAY + "Kliknij aby ustawić ten home",
                    ChatColor.GRAY + "w swojej aktualnej pozycji.");
            inv.setItem(setSlot, setBtn);

            ItemStack delBtn = makeItem(Material.RED_STAINED_GLASS_PANE,
                    ChatColor.RED + "" + ChatColor.BOLD + "Usuń Home " + slot,
                    ChatColor.GRAY + "Kliknij aby usunąć ten home.");
            inv.setItem(deleteSlot, delBtn);
            return;
        }

        ItemStack noBed = makeItem(Material.BARRIER,
                ChatColor.RED + "" + ChatColor.BOLD + "Brak Home " + slot,
                ChatColor.GRAY + "Kliknij zielony przycisk poniżej,",
                ChatColor.GRAY + "aby ustawić ten home.");
        inv.setItem(displaySlot, noBed);

        ItemStack setBtn = makeItem(Material.LIME_STAINED_GLASS_PANE,
                ChatColor.GREEN + "" + ChatColor.BOLD + "Ustaw Home " + slot,
                ChatColor.GRAY + "Kliknij aby ustawić home",
                ChatColor.GRAY + "w swojej aktualnej pozycji.");
        inv.setItem(setSlot, setBtn);
    }

    private String formatLoc(Location loc) {
        return ChatColor.WHITE + "(" + Math.round(loc.getX()) + ", " + Math.round(loc.getY()) + ", " + Math.round(loc.getZ()) + ")";
    }

    public static ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("set", "delete", "tp");
        if (args.length == 2 && Arrays.asList("set", "delete", "del", "remove", "tp").contains(args[0].toLowerCase())) {
            return Arrays.asList("1", "2");
        }
        return null;
    }
}
