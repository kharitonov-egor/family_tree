package com.egakh.familytree.settings;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static com.egakh.familytree.util.AnimalCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class TrackingRulesTest {
    @Test void defaultsRecordEveryCategoryWithAFiveThousandLimitAndNoCleanup() {
        TrackingRules rules = TrackingRules.parse(new Properties());
        assertEquals(TrackingRules.DEFAULT, rules);
        assertTrue(rules.records("minecraft:cow", FARM));
        assertTrue(rules.records("minecraft:axolotl", WILDLIFE));
        assertTrue(rules.records("naturalist:bear", MODDED));
        assertTrue(rules.records("minecraft:wolf", PETS));
        assertEquals(5_000, rules.farmRecordLimit());
        assertFalse(rules.autoPruneFarm());
    }

    @Test void ignoredSpeciesWinAndTrackedSpeciesOverrideADisabledCategory() {
        Properties properties = new Properties();
        properties.setProperty("trackFarmAnimals", "false");
        properties.setProperty("trackWildlife", "FALSE");
        properties.setProperty("trackedSpecies", " sheep, minecraft:axolotl ,,");
        properties.setProperty("ignoredSpecies", "minecraft:parrot,naturalist:snail");
        TrackingRules rules = TrackingRules.parse(properties);
        assertEquals(Set.of("minecraft:sheep", "minecraft:axolotl"), rules.trackedSpecies());
        assertFalse(rules.records("minecraft:cow", FARM));
        assertTrue(rules.records("minecraft:sheep", FARM));
        assertFalse(rules.records("minecraft:fox", WILDLIFE));
        assertTrue(rules.records("minecraft:axolotl", WILDLIFE));
        assertFalse(rules.records("minecraft:parrot", PETS), "Ignored species also stop new pets");
        assertTrue(rules.records("minecraft:wolf", PETS));
        assertFalse(rules.records("naturalist:snail", MODDED));
        assertTrue(rules.records("naturalist:bear", MODDED));
    }

    @Test void limitsAreClampedAndInvalidValuesFallBackToDefaults() {
        Properties properties = new Properties();
        properties.setProperty("farmRecordLimit", "250000");
        properties.setProperty("autoPruneFarmRecords", "yes");
        properties.setProperty("trackModdedAnimals", "maybe");
        TrackingRules rules = TrackingRules.parse(properties);
        assertEquals(TrackingRules.MAX_FARM_LIMIT, rules.farmRecordLimit());
        assertFalse(rules.autoPruneFarm());
        assertTrue(rules.modded());
        properties.setProperty("farmRecordLimit", "-4");
        assertEquals(0, TrackingRules.parse(properties).farmRecordLimit());
        properties.setProperty("farmRecordLimit", "lots");
        assertEquals(TrackingRules.DEFAULT_FARM_LIMIT, TrackingRules.parse(properties).farmRecordLimit());
    }

    @Test void storedSettingsParseBackUnchanged() {
        TrackingRules rules = new TrackingRules(false, true, false, Set.of("minecraft:fox"), Set.of("minecraft:bee"), 1234, true);
        Properties properties = new Properties();
        rules.store(properties);
        assertEquals(rules, TrackingRules.parse(properties));
    }

    /** taggedTracked and taggedIgnored stand for the familytree:tracked and familytree:ignored entity type tags. */
    @Test void theSettingsFileWinsOverTagsAndTagsWinOverCategories() {
        TrackingRules rules = new TrackingRules(false, true, true, Set.of("minecraft:bee"), Set.of("minecraft:fox"), 5_000, false);
        assertTrue(rules.records("minecraft:sheep", FARM, true, false), "A tracked tag overrides a disabled category");
        assertFalse(rules.records("minecraft:axolotl", WILDLIFE, false, true), "An ignored tag overrides an enabled category");
        assertTrue(rules.records("minecraft:bee", WILDLIFE, false, true), "trackedSpecies wins over the ignored tag");
        assertFalse(rules.records("minecraft:fox", WILDLIFE, true, false), "ignoredSpecies wins over the tracked tag");
        assertFalse(rules.records("minecraft:cow", FARM, true, true), "A species in both tags is ignored");
        assertFalse(rules.records("minecraft:wolf", PETS, false, true), "The ignored tag stops new pets");
        assertTrue(rules.records("minecraft:wolf", PETS, false, false));
        assertEquals(rules.records("minecraft:cow", FARM), rules.records("minecraft:cow", FARM, false, false));
    }
}
