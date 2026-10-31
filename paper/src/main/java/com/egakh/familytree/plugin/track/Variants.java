package com.egakh.familytree.plugin.track;

import org.bukkit.Keyed;
import org.bukkit.entity.Entity;

import java.util.Locale;
import java.util.Map;

/**
 * Reads the variant strings the mod saves in {@code variant_id}: an enum's serialized name such as {@code blue},
 * a registry key such as {@code minecraft:warm}, {@code coat/markings} for horses, and {@code main/hidden} panda
 * genes. Bukkit's names differ from Minecraft's for a few values, which the maps below translate.
 */
public final class Variants {
    private static final Map<String, String> HORSE_MARKINGS = Map.of("whitefield", "white_field");
    private static final Map<String, String> RABBIT = Map.of("black_and_white", "white_splotched",
            "salt_and_pepper", "salt", "the_killer_bunny", "evil");
    private static final Map<String, String> PARROT = Map.of("red", "red_blue", "cyan", "yellow_blue");

    private Variants() {}

    public static String read(Entity entity) {
        String species = Entities.species(entity);
        return switch (species) {
            case "minecraft:horse" -> {
                String coat = name(Compat.call(entity, "getColor"));
                String markings = name(Compat.call(entity, "getStyle"));
                yield coat == null || markings == null ? null : coat + "/" + HORSE_MARKINGS.getOrDefault(markings, markings);
            }
            case "minecraft:llama", "minecraft:trader_llama", "minecraft:sheep" -> name(Compat.call(entity, "getColor"));
            case "minecraft:cat" -> key(Compat.call(entity, "getCatType"));
            case "minecraft:rabbit" -> mapped(RABBIT, name(Compat.call(entity, "getRabbitType")));
            case "minecraft:fox" -> name(Compat.call(entity, "getFoxType"));
            case "minecraft:parrot" -> mapped(PARROT, name(Compat.call(entity, "getVariant")));
            case "minecraft:panda" -> {
                String main = name(Compat.call(entity, "getMainGene"));
                String hidden = name(Compat.call(entity, "getHiddenGene"));
                yield main == null || hidden == null ? null : main + "/" + hidden;
            }
            case "minecraft:wolf", "minecraft:frog", "minecraft:cow", "minecraft:pig", "minecraft:chicken" ->
                    key(Compat.call(entity, "getVariant"));
            default -> generic(Compat.call(entity, "getVariant"));
        };
    }

    private static String mapped(Map<String, String> names, String value) {
        return value == null ? null : names.getOrDefault(value, value);
    }

    /** Registry variants are saved by key, such as {@code minecraft:tabby}. Before 1.21 some were plain enums. */
    private static String key(Object value) {
        if (value == null) return null;
        if (value instanceof Keyed keyed) {
            try {
                return keyed.getKey().toString();
            } catch (RuntimeException unregistered) {
                return null;
            }
        }
        String name = name(value);
        return name == null ? null : "minecraft:" + name;
    }

    private static String name(Object value) {
        if (value instanceof Enum<?> constant) return constant.name().toLowerCase(Locale.ROOT);
        if (value instanceof Keyed keyed) {
            try {
                return keyed.getKey().getKey();
            } catch (RuntimeException unregistered) {
                return null;
            }
        }
        Object name = Compat.call(value, "name");
        return name instanceof String text ? text.toLowerCase(Locale.ROOT) : null;
    }

    private static String generic(Object value) {
        if (value == null) return null;
        if (value instanceof Enum<?> constant) return constant.name().toLowerCase(Locale.ROOT);
        return key(value);
    }
}
