package com.egakh.familytree.breeding;

import com.egakh.familytree.text.LangTables;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BabyWordsTest {
    private static String english(List<String> foalSpecies) {
        return LangTables.table(LangTables.DEFAULT).get(BreedingPair.babyKey(foalSpecies));
    }

    @Test
    void eachSpeciesGetsItsOwnWordForABaby() {
        assertEquals("Foal", english(List.of(BreedingPair.HORSE)));
        assertEquals("Foal", english(BreedingPair.foalSpecies(BreedingPair.HORSE, BreedingPair.DONKEY)));
        assertEquals("Lamb", english(List.of("minecraft:sheep")));
        assertEquals("Calf", english(List.of("minecraft:mooshroom")));
        assertEquals("Calf", english(List.of(BreedingPair.CAMEL)));
        assertEquals("Pup", english(List.of("minecraft:wolf")));
        assertEquals("Kitten", english(List.of("minecraft:cat")));
        assertEquals("Cria", english(BreedingPair.foalSpecies(BreedingPair.TRADER_LLAMA, BreedingPair.LLAMA)));
    }

    @Test
    void speciesWithoutAWordAndModdedAnimalsGetANeutralOne() {
        assertEquals("Baby", english(List.of("minecraft:bee")));
        assertEquals("Baby", english(List.of("minecraft:axolotl")));
        assertEquals("Baby", english(List.of("examplemod:minecraft_sheep")));
        assertEquals("Baby", english(List.of("sheep")));
        assertEquals("Baby", english(List.of()));
    }

    @Test
    void everyBreedingVanillaSpeciesHasAWordInEveryLanguage() {
        List<String> species = List.of("horse", "donkey", "llama", "trader_llama", "camel", "cow", "mooshroom", "sheep", "pig",
                "chicken", "rabbit", "goat", "wolf", "cat", "ocelot", "fox", "panda", "armadillo", "turtle", "frog", "sniffer",
                "hoglin", "bee", "axolotl", "strider", "nautilus", "happy_ghast");
        for (String language : LangTables.SHIPPED) {
            Map<String, String> table = LangTables.table(language);
            for (String path : species) {
                String word = table.get(BreedingPair.babyKey(List.of("minecraft:" + path)));
                assertNotNull(word, language + " " + path);
                assertFalse(word.isBlank(), language + " " + path);
            }
        }
    }
}
