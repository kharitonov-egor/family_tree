package com.egakh.familytree.breeding;

import java.util.List;
import java.util.Set;

/** Which recorded species can breed together, following each animal's {@code canMate} in the game. */
public final class BreedingPair {
    public static final String HORSE = "minecraft:horse";
    public static final String DONKEY = "minecraft:donkey";
    public static final String MULE = "minecraft:mule";
    public static final String LLAMA = "minecraft:llama";
    public static final String TRADER_LLAMA = "minecraft:trader_llama";
    public static final String CAMEL = "minecraft:camel";
    private static final Set<String> NEVER_BREED = Set.of(MULE, "minecraft:skeleton_horse", "minecraft:zombie_horse",
            "minecraft:parrot", "minecraft:camel_husk", "minecraft:zombie_nautilus");
    /** {@code setOffspringAttributes} runs for these foals; camels get the fixed camel attributes instead. */
    private static final Set<String> INHERITS_STATS = Set.of(HORSE, DONKEY, MULE, LLAMA, TRADER_LLAMA);

    private BreedingPair() {}

    public static boolean breeds(String species) {
        return species != null && !NEVER_BREED.contains(species);
    }

    public static boolean canBreed(String a, String b) {
        if (!breeds(a) || !breeds(b)) return false;
        return a.equals(b) || pair(a, b, HORSE, DONKEY) || pair(a, b, LLAMA, TRADER_LLAMA);
    }

    /**
     * The possible foal species. A llama and a trader llama have either, because the foal's type comes from
     * whichever parent's breeding code runs.
     */
    public static List<String> foalSpecies(String a, String b) {
        if (!canBreed(a, b)) return List.of();
        if (pair(a, b, HORSE, DONKEY)) return List.of(MULE);
        if (pair(a, b, LLAMA, TRADER_LLAMA)) return List.of(LLAMA, TRADER_LLAMA);
        return List.of(a);
    }

    public static boolean inheritsStats(String foalSpecies) {
        return INHERITS_STATS.contains(foalSpecies);
    }

    public static boolean llamaLike(String species) {
        return LLAMA.equals(species) || TRADER_LLAMA.equals(species);
    }

    private static boolean pair(String a, String b, String first, String second) {
        return a.equals(first) && b.equals(second) || a.equals(second) && b.equals(first);
    }
}
