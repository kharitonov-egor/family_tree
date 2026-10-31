package com.egakh.familytree.plugin.menu;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/** A column of colored dust over a relative, sent only to the player who asked, as in the mod. */
final class RelativeMarkers {
    private static final float SIZE = 1.5F;
    private static final Particle DUST = dust();
    private static final Method FORCED = forced();

    private RelativeMarkers() {}

    static void mark(Player player, UUID entityId, int color) {
        Entity entity = Bukkit.getEntity(entityId);
        if (DUST == null || entity == null || !entity.getWorld().equals(player.getWorld())) return;
        double x = entity.getLocation().getX(), y = entity.getLocation().getY() + entity.getHeight() + 0.8, z = entity.getLocation().getZ();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(color & 0xFFFFFF), SIZE);
        if (FORCED != null) {
            try {
                FORCED.invoke(player, DUST, x, y, z, 24, 0.2, 0.6, 0.2, 0.0, dust, true);
                return;
            } catch (ReflectiveOperationException | RuntimeException unforced) {
                // Fall back to the unforced call below.
            }
        }
        player.spawnParticle(DUST, x, y, z, 24, 0.2, 0.6, 0.2, 0.0, dust);
    }

    /** Spigot renamed REDSTONE to DUST in 1.20.5. */
    private static Particle dust() {
        for (String name : new String[]{"DUST", "REDSTONE"}) {
            try {
                return (Particle) Particle.class.getField(name).get(null);
            } catch (ReflectiveOperationException | RuntimeException missing) {
                // Try the older name.
            }
        }
        return null;
    }

    /** Clients drop unforced particles more than 32 blocks away, and relatives can be 64 blocks away. Newer APIs can force them. */
    private static Method forced() {
        try {
            return Player.class.getMethod("spawnParticle", Particle.class, double.class, double.class, double.class, int.class,
                    double.class, double.class, double.class, double.class, Object.class, boolean.class);
        } catch (NoSuchMethodException | RuntimeException missing) {
            return null;
        }
    }
}
