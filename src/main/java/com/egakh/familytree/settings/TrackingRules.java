package com.egakh.familytree.settings;

import com.egakh.familytree.util.AnimalCategory;

import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Server choices for which animals other than pets enter the history, and how many unnamed ones it keeps. */
public record TrackingRules(boolean farm, boolean wildlife, boolean modded, Set<String> trackedSpecies,
                            Set<String> ignoredSpecies, int farmRecordLimit, boolean autoPruneFarm) {
    public static final int DEFAULT_FARM_LIMIT = 5_000;
    /** Leaves room for pets under the 100,000-record snapshot limit. */
    public static final int MAX_FARM_LIMIT = 50_000;
    public static final TrackingRules DEFAULT = new TrackingRules(true, true, true, Set.of(), Set.of(), DEFAULT_FARM_LIMIT, false);

    static final String FARM = "trackFarmAnimals";
    static final String WILDLIFE = "trackWildlife";
    static final String MODDED = "trackModdedAnimals";
    static final String TRACKED = "trackedSpecies";
    static final String IGNORED = "ignoredSpecies";
    static final String LIMIT = "farmRecordLimit";
    static final String AUTO_PRUNE = "autoPruneFarmRecords";

    public TrackingRules {
        trackedSpecies = Set.copyOf(trackedSpecies);
        ignoredSpecies = Set.copyOf(ignoredSpecies);
        farmRecordLimit = Math.max(0, Math.min(MAX_FARM_LIMIT, farmRecordLimit));
    }

    /** Ignored species also stop new pets. */
    public boolean records(String speciesId, AnimalCategory category) {
        if (ignoredSpecies.contains(speciesId)) return false;
        if (category == AnimalCategory.PETS || trackedSpecies.contains(speciesId)) return true;
        return switch (category) {
            case FARM -> farm;
            case WILDLIFE -> wildlife;
            case MODDED -> modded;
            case PETS -> true;
        };
    }

    public static TrackingRules parse(Properties properties) {
        return new TrackingRules(
                flag(properties, FARM, DEFAULT.farm), flag(properties, WILDLIFE, DEFAULT.wildlife),
                flag(properties, MODDED, DEFAULT.modded),
                species(properties.getProperty(TRACKED)), species(properties.getProperty(IGNORED)),
                number(properties.getProperty(LIMIT), DEFAULT.farmRecordLimit),
                flag(properties, AUTO_PRUNE, DEFAULT.autoPruneFarm));
    }

    public void store(Properties properties) {
        properties.setProperty(FARM, Boolean.toString(farm));
        properties.setProperty(WILDLIFE, Boolean.toString(wildlife));
        properties.setProperty(MODDED, Boolean.toString(modded));
        properties.setProperty(TRACKED, String.join(",", new TreeSet<>(trackedSpecies)));
        properties.setProperty(IGNORED, String.join(",", new TreeSet<>(ignoredSpecies)));
        properties.setProperty(LIMIT, Integer.toString(farmRecordLimit));
        properties.setProperty(AUTO_PRUNE, Boolean.toString(autoPruneFarm));
    }

    private static boolean flag(Properties properties, String key, boolean fallback) {
        String raw = properties.getProperty(key);
        if (raw == null) return fallback;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.equals("true") || (!value.equals("false") && fallback);
    }

    private static int number(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    /** Species ids without a namespace are vanilla, so {@code fox} means {@code minecraft:fox}. */
    private static Set<String> species(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .map(value -> value.contains(":") ? value : "minecraft:" + value)
                .collect(Collectors.toUnmodifiableSet());
    }
}
