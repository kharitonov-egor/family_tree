package com.egakh.familytree.util;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Pets are tamed or ridden. Every other recorded animal entered the history through breeding, a name tag, or the linking stick. */
public enum AnimalCategory {
    PETS, FARM, WILDLIFE, MODDED;

    private static final Set<String> VANILLA_PETS = Set.of("minecraft:wolf", "minecraft:cat", "minecraft:parrot",
            "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:llama", "minecraft:trader_llama",
            "minecraft:camel", "minecraft:camel_husk", "minecraft:skeleton_horse", "minecraft:zombie_horse",
            "minecraft:nautilus", "minecraft:zombie_nautilus");
    private static final Set<String> VANILLA_FARM = Set.of("minecraft:cow", "minecraft:mooshroom", "minecraft:sheep",
            "minecraft:pig", "minecraft:chicken", "minecraft:rabbit", "minecraft:goat");
    private static final Map<String, Boolean> MODDED_PETS = new ConcurrentHashMap<>();
    private static final AtomicInteger LEARNED = new AtomicInteger();

    public static AnimalCategory of(String speciesId) {
        if (VANILLA_PETS.contains(speciesId) || Boolean.TRUE.equals(MODDED_PETS.get(speciesId))) return PETS;
        if (!speciesId.startsWith("minecraft:")) return MODDED;
        return VANILLA_FARM.contains(speciesId) ? FARM : WILDLIFE;
    }

    /** Modded species count as pets until an entity shows otherwise, so limits and cleanup never touch them by mistake. */
    public static boolean knownNonPet(String speciesId) {
        if (VANILLA_PETS.contains(speciesId)) return false;
        return speciesId.startsWith("minecraft:") || Boolean.FALSE.equals(MODDED_PETS.get(speciesId));
    }

    public static void learn(String speciesId, boolean pet) {
        if (speciesId.startsWith("minecraft:")) return;
        Boolean previous = MODDED_PETS.put(speciesId, pet);
        if (previous == null || previous != pet) LEARNED.incrementAndGet();
    }

    /** Changes whenever a modded species is classified, so cached counts know to recount. */
    public static int learnedVersion() {
        return LEARNED.get();
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
