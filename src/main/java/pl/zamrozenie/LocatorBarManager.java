package pl.zamrozenie;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Locator bar (pasek XP pokazujacy graczy, 1.21.6+).
 *
 * Vanilla ukrywa gracza przez modyfikator atrybutu waypoint_transmit_range
 * (x0 = add_multiplied_total -1) dodawany przez: dynie / glowe na glowie,
 * skradanie i niewidzialnosc. My:
 *  1. ustawiamy bazowy zasieg nadawania na 100 kratek (widoczny tylko dla graczy w tym promieniu),
 *  2. co 5 tickow zdejmujemy z gracza wszystkie modyfikatory zerujace zasieg,
 *     dzieki czemu dynia (i reszta) nie ukrywa nikogo.
 */
public class LocatorBarManager implements Listener {

    public static final double TRANSMIT_RANGE = 100.0;

    private final Plugin plugin;
    private BukkitTask task;

    public LocatorBarManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (Player p : Bukkit.getOnlinePlayers()) apply(p);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) apply(p);
        }, 5L, 5L);
    }

    public void stop() {
        if (task != null) task.cancel();
        // Przywroc vanilla (60 mln) zeby nie zostawiac graczy z 100 po wylaczeniu pluginu.
        for (Player p : Bukkit.getOnlinePlayers()) {
            AttributeInstance a = p.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE);
            if (a != null) a.setBaseValue(a.getDefaultValue());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    private void apply(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE);
        if (attr == null) return;

        if (Math.abs(attr.getBaseValue() - TRANSMIT_RANGE) > 0.01) {
            attr.setBaseValue(TRANSMIT_RANGE);
        }

        // Zdejmij kazdy modyfikator, ktory zeruje zasieg (dynia, glowa, crouch, invis).
        List<AttributeModifier> toRemove = null;
        for (AttributeModifier mod : attr.getModifiers()) {
            boolean hides = mod.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1 && mod.getAmount() <= -1.0;
            boolean zeroes = mod.getOperation() == AttributeModifier.Operation.ADD_NUMBER && mod.getAmount() <= -TRANSMIT_RANGE;
            if (hides || zeroes) {
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(mod);
            }
        }
        if (toRemove != null) {
            for (AttributeModifier mod : toRemove) attr.removeModifier(mod);
        }
    }
}
