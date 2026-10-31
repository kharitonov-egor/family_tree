package com.egakh.familytree.settings;

import com.egakh.familytree.util.AnimalCategory;

import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

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

    public boolean records(String speciesId, AnimalCategory category) {
        return records(speciesId, category, false, false);
    }

    /**
     * The settings file wins over the {@code familytree:tracked} and {@code familytree:ignored} entity type tags,
     * and both win over the category switches. At each level, ignored wins over tracked. Ignored species also stop
     * new pets.
     */
    public boolean records(String speciesId, AnimalCategory category, boolean taggedTracked, boolean taggedIgnored) {
        if (ignoredSpecies.contains(speciesId)) return false;
        if (trackedSpecies.contains(speciesId)) return true;
        if (taggedIgnored) return false;
        if (taggedTracked || category == AnimalCategory.PETS) return true;
        return switch (category) {
            case FARM -> farm;
            case WILDLIFE -> wildlife;
            case MODDED -> modded;
            case PETS -> true;
        };
    }

    public static TrackingRules parse(Properties properties) {
        return read(new SettingsReader(properties));
    }

    static TrackingRules read(SettingsReader reader) {
        return new TrackingRules(
                reader.flag(FARM, DEFAULT.farm), reader.flag(WILDLIFE, DEFAULT.wildlife),
                reader.flag(MODDED, DEFAULT.modded),
                reader.species(TRACKED), reader.species(IGNORED),
                reader.number(LIMIT, DEFAULT.farmRecordLimit, 0, MAX_FARM_LIMIT),
                reader.flag(AUTO_PRUNE, DEFAULT.autoPruneFarm));
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
}
