package com.egakh.familytree.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.World;

import java.util.List;
import java.util.Locale;

/**
 * World days and dimension names as the mod records them. Every dimension shares the overworld's game time in
 * vanilla, so days always come from the server's first world.
 */
public final class WorldClock {
    private WorldClock() {}

    public static long currentWorldDay() {
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? 0 : worlds.get(0).getGameTime() / 24000L;
    }

    public static long currentEpochMillis() {
        return System.currentTimeMillis();
    }

    public static String dimension(World world) {
        try {
            return ((Keyed) world).getKey().toString();
        } catch (RuntimeException | LinkageError unavailable) {
            return switch (world.getEnvironment()) {
                case NETHER -> "minecraft:the_nether";
                case THE_END -> "minecraft:the_end";
                default -> world.equals(Bukkit.getWorlds().get(0)) ? "minecraft:overworld"
                        : "minecraft:" + world.getName().toLowerCase(Locale.ROOT);
            };
        }
    }

    public static World world(String dimension) {
        for (World world : Bukkit.getWorlds()) if (dimension(world).equals(dimension)) return world;
        return null;
    }
}
