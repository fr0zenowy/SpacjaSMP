package pl.zamrozenie;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class MythicHook {

    private MythicHook() {}

    public static Entity spawn(Plugin plugin, String mythicId, Location loc) {
        if (mythicId == null || loc == null || loc.getWorld() == null) return null;
        if (plugin.getServer().getPluginManager().getPlugin("MythicMobs") == null) return null;
        try {
            Class<?> bukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = bukkit.getMethod("inst").invoke(null);

            try {
                Object helper = inst.getClass().getMethod("getAPIHelper").invoke(inst);
                Object spawned = helper.getClass()
                        .getMethod("spawnMythicMob", String.class, Location.class)
                        .invoke(helper, mythicId, loc);
                if (spawned instanceof Entity entity) return entity;
            } catch (NoSuchMethodException ignored) {
            }

            Object mobManager = inst.getClass().getMethod("getMobManager").invoke(inst);
            Object active = mobManager.getClass()
                    .getMethod("spawnMob", String.class, Location.class)
                    .invoke(mobManager, mythicId, loc);
            if (active == null) return null;
            Object mythicEntity = active.getClass().getMethod("getEntity").invoke(active);
            if (mythicEntity == null) return null;
            Object bukkitEntity = mythicEntity.getClass().getMethod("getBukkitEntity").invoke(mythicEntity);
            if (bukkitEntity instanceof Entity entity) return entity;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "MythicMobs: spawn " + mythicId + " nieudany", t);
        }
        return null;
    }

    public static String internalName(Plugin plugin, UUID uuid) {
        if (uuid == null || plugin.getServer().getPluginManager().getPlugin("MythicMobs") == null) return null;
        try {
            Class<?> bukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = bukkit.getMethod("inst").invoke(null);
            Object mobManager = inst.getClass().getMethod("getMobManager").invoke(inst);
            Object optional = mobManager.getClass().getMethod("getActiveMob", UUID.class).invoke(mobManager, uuid);
            if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) return null;
            Object active = opt.get();
            Object type = active.getClass().getMethod("getType").invoke(active);
            Object name = type.getClass().getMethod("getInternalName").invoke(type);
            return name != null ? name.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
