package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WysrodkujCommand implements CommandExecutor {

    // Promień = n / 2.0, np. 2 graczy → promień 1 (2 bloki od siebie), 3 → 1.5, itd.
    private static final double RADIUS_PER_PLAYER = 0.5;
    private static final int SEARCH_RADIUS = 120;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("zamrozenie.use")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do tej komendy.");
            return true;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Brak graczy na serwerze.");
            return true;
        }

        World world = getMajorityWorld(players);
        players.removeIf(p -> !p.getWorld().equals(world));
        int n = players.size();

        double cx = players.stream().mapToDouble(p -> p.getLocation().getX()).average().orElse(0);
        double cz = players.stream().mapToDouble(p -> p.getLocation().getZ()).average().orElse(0);
        int refY  = (int) players.stream().mapToDouble(p -> p.getLocation().getY()).average().orElse(64);

        // Promień rośnie z liczbą graczy: 2 → 1.0, 3 → 1.5, 4 → 2.0 ...
        double radius = Math.max(1.0, n * RADIUS_PER_PLAYER);

        if (n == 1) {
            int groundY = findGroundY(world, (int) cx, (int) cz, refY);
            if (groundY < 0) {
                sender.sendMessage(ChatColor.RED + "Nie znaleziono bezpiecznego terenu.");
                return true;
            }
            Location loc = new Location(world, (int) cx + 0.5, groundY + 1, (int) cz + 0.5);
            players.get(0).teleport(loc);
            players.get(0).sendMessage(ChatColor.AQUA + "Zostałeś uśrodkowany.");
            sender.sendMessage(ChatColor.GREEN + "✔ Uśrodkowano gracza.");
            return true;
        }

        // Szukaj płaskiego, otwartego terenu
        Location center = findFlatOpenArea(world, cx, cz, refY, n, radius);
        if (center == null) {
            sender.sendMessage(ChatColor.RED + "Nie znaleziono płaskiego, otwartego terenu (szukano w promieniu " + SEARCH_RADIUS + " bloków).");
            return true;
        }

        int groundY = center.getBlockY() - 1;

        // Teleportuj każdego gracza na jego punkt kręgu
        for (int i = 0; i < n; i++) {
            double angle = (2 * Math.PI * i) / n;
            double px = center.getX() + radius * Math.cos(angle);
            double pz = center.getZ() + radius * Math.sin(angle);

            // Wszyscy na tej samej wysokości Y
            Location spot = new Location(world, Math.floor(px) + 0.5, groundY + 1, Math.floor(pz) + 0.5);
            spot.setYaw(yawTowards(spot, center));
            players.get(i).teleport(spot);
        }

        String shape = shapeName(n);
        String msg = ChatColor.AQUA + "Zostałeś uśrodkowany — formacja: " + ChatColor.WHITE + shape;
        players.forEach(p -> p.sendMessage(msg));
        sender.sendMessage(
            ChatColor.GREEN + "✔ Uśrodkowano " + n + " graczy w formację: " +
            ChatColor.WHITE + shape + ChatColor.GREEN +
            " (promień: " + String.format("%.1f", radius) + " bloków)."
        );
        return true;
    }

    // ─── Szukanie płaskiego, otwartego terenu ─────────────────────────────────────

    /**
     * Szuka środka kręgu gdzie WSZYSTKIE N pozycji mają:
     * - bezpieczny grunt na dokładnie tym samym Y
     * - wolne powietrze na Y+1 i Y+2
     * - linię wzroku między sąsiednimi graczami (brak bloków na wysokości oczu)
     */
    private Location findFlatOpenArea(World world, double cx, double cz, int refY, int n, double radius) {
        for (int r = 0; r <= SEARCH_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;

                    double testCx = cx + dx;
                    double testCz = cz + dz;

                    int groundY = findGroundY(world, (int) testCx, (int) testCz, refY);
                    if (groundY < 0) continue;

                    if (isValidFormation(world, testCx, testCz, groundY, n, radius)) {
                        return new Location(world, testCx + 0.5, groundY + 1, testCz + 0.5);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Sprawdza czy wszystkie N pozycji na kręgu są bezpieczne i widoczne między sobą.
     */
    private boolean isValidFormation(World world, double cx, double cz, int groundY, int n, double radius) {
        List<double[]> positions = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            double angle = (2 * Math.PI * i) / n;
            double px = cx + radius * Math.cos(angle);
            double pz = cz + radius * Math.sin(angle);
            int ix = (int) Math.floor(px);
            int iz = (int) Math.floor(pz);

            // Grunt musi być na dokładnie tym samym Y co centrum
            Block ground = world.getBlockAt(ix, groundY, iz);
            if (!isSafeGround(ground)) return false;

            // Wolne powietrze na poziomie nóg i głowy
            if (!isClear(world.getBlockAt(ix, groundY + 1, iz))) return false;
            if (!isClear(world.getBlockAt(ix, groundY + 2, iz))) return false;

            positions.add(new double[]{px, pz});
        }

        // Linia wzroku między każdą parą sąsiednich graczy (na wysokości oczu Y+1.6)
        double eyeY = groundY + 1.6;
        for (int i = 0; i < n; i++) {
            double[] from = positions.get(i);
            double[] to   = positions.get((i + 1) % n);
            if (!lineOfSightClear(world, from[0], eyeY, from[1], to[0], to[1])) return false;
        }

        return true;
    }

    /**
     * Sprawdza linię wzroku między dwoma punktami (10 kroków ray-march).
     */
    private boolean lineOfSightClear(World world, double x1, double y, double z1, double x2, double z2) {
        int steps = 12;
        for (int s = 1; s < steps; s++) {
            double t = (double) s / steps;
            int bx = (int) Math.floor(x1 + (x2 - x1) * t);
            int by = (int) y;
            int bz = (int) Math.floor(z1 + (z2 - z1) * t);
            if (!world.getBlockAt(bx, by, bz).isPassable()) return false;
        }
        return true;
    }

    /**
     * Szuka bezpiecznego gruntu przy danym X,Z startując od startY i schodząc w dół.
     */
    private int findGroundY(World world, int x, int z, int startY) {
        int minY = world.getMinHeight() + 1;
        int maxY = Math.min(startY + 20, world.getMaxHeight() - 3);

        for (int y = maxY; y >= minY; y--) {
            Block ground = world.getBlockAt(x, y, z);
            Block feet   = world.getBlockAt(x, y + 1, z);
            Block head   = world.getBlockAt(x, y + 2, z);
            if (isSafeGround(ground) && isClear(feet) && isClear(head)) {
                return y;
            }
        }
        return -1;
    }

    // ─── Bloki ────────────────────────────────────────────────────────────────────

    private boolean isSafeGround(Block b) {
        if (!b.getType().isSolid()) return false;
        Material m = b.getType();
        return m != Material.MAGMA_BLOCK
            && m != Material.CACTUS
            && m != Material.CAMPFIRE
            && m != Material.SOUL_CAMPFIRE
            && m != Material.LAVA
            && m != Material.FIRE
            && m != Material.SOUL_FIRE;
    }

    private boolean isClear(Block b) {
        if (!b.isPassable()) return false;
        Material m = b.getType();
        return m != Material.LAVA
            && m != Material.WATER
            && m != Material.FIRE
            && m != Material.SOUL_FIRE
            && m != Material.WITHER_ROSE
            && m != Material.SWEET_BERRY_BUSH;
    }

    // ─── Pomocnicze ───────────────────────────────────────────────────────────────

    private World getMajorityWorld(List<Player> players) {
        return players.stream()
            .collect(Collectors.groupingBy(Player::getWorld, Collectors.counting()))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(Bukkit.getWorlds().get(0));
    }

    private float yawTowards(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private String shapeName(int n) {
        return switch (n) {
            case 2  -> "Linia";
            case 3  -> "Trójkąt";
            case 4  -> "Kwadrat";
            case 5  -> "Pięciokąt";
            case 6  -> "Sześciokąt";
            case 7  -> "Siedmiokąt";
            case 8  -> "Ośmiokąt";
            case 9  -> "Dziewięciokąt";
            case 10 -> "Dziesięciokąt";
            default -> n + "-kąt";
        };
    }
}
