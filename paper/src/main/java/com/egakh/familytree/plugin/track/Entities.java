package com.egakh.familytree.plugin.track;

import com.egakh.familytree.data.HorseStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.UUID;

/** What Family Tree reads from a live entity, in the same form the mod saves it. */
public final class Entities {
    private static final String[] SPEED = {"movement_speed", "generic.movement_speed"};
    private static final String[] JUMP = {"jump_strength", "generic.jump_strength", "horse.jump_strength"};
    private static final String[] HEALTH = {"max_health", "generic.max_health"};

    private Entities() {}

    public static String species(Entity entity) {
        return ((Keyed) entity.getType()).getKey().toString();
    }

    public static boolean isAnimal(Entity entity) {
        return entity instanceof Animals;
    }

    public static UUID owner(Entity entity) {
        if (!(entity instanceof Tameable tameable)) return null;
        Object id = Compat.call(tameable, "getOwnerUniqueId");
        if (id instanceof UUID uuid) return uuid;
        AnimalTamer owner = tameable.getOwner();
        return owner == null ? null : owner.getUniqueId();
    }

    /** Like the mod, names an owner only while they are online. Reading an offline player's name can touch the disk. */
    public static String ownerName(UUID owner) {
        if (owner == null) return null;
        Player online = Bukkit.getPlayer(owner);
        return online == null ? null : online.getName();
    }

    public static String customName(Entity entity) {
        String name = entity.getCustomName();
        if (name == null) return null;
        String plain = ChatColor.stripColor(name);
        return plain == null || plain.isBlank() ? null : plain;
    }

    public static HorseStats stats(Entity entity) {
        if (!(entity instanceof AbstractHorse horse)) return null;
        return new HorseStats(base(horse, SPEED), base(horse, JUMP), base(horse, HEALTH),
                horse instanceof Llama llama ? llama.getStrength() : null);
    }

    private static Double base(Attributable entity, String[] keys) {
        for (String key : keys) {
            Attribute attribute;
            try {
                attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
            } catch (RuntimeException invalid) {
                attribute = null;
            }
            if (attribute == null) continue;
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) return instance.getBaseValue();
        }
        return null;
    }
}
