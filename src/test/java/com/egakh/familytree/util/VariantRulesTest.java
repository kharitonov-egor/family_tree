package com.egakh.familytree.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.egakh.familytree.util.VariantRules.Outcome.*;
import static com.egakh.familytree.util.VariantRules.*;
import static org.junit.jupiter.api.Assertions.*;

class VariantRulesTest {
    @Test void blueAxolotlsBrownMooshroomsAndBrownPandasAreRare() {
        assertTrue(rare("minecraft:axolotl", "blue"));
        assertFalse(rare("minecraft:axolotl", "lucy"));
        assertTrue(rare("minecraft:mooshroom", "brown"));
        assertFalse(rare("minecraft:mooshroom", "red"));
        assertTrue(rare(PANDA, "brown/brown"));
        assertFalse(rare(PANDA, "brown/normal"), "One brown gene looks normal");
        assertFalse(rare(SHEEP, "pink"));
        assertFalse(rare("minecraft:axolotl", null));
    }

    @Test void babiesMatchingAParentInheritedTheirVariant() {
        assertEquals(INHERITED, outcome("minecraft:axolotl", "lucy", "lucy", "wild"));
        assertEquals(NEW, outcome("minecraft:axolotl", "blue", "lucy", "wild"));
        assertEquals(INHERITED, outcome("minecraft:axolotl", "blue", "blue", "wild"));
        assertEquals(NEW, outcome("minecraft:mooshroom", "brown", "red", "red"));
        assertEquals(MIXED, outcome(SHEEP, "pink", "red", "white"));
        assertEquals(INHERITED, outcome(SHEEP, "white", "red", "white"));
        assertEquals(UNKNOWN, outcome(SHEEP, "pink", null, "white"), "An unknown parent could explain the color");
        assertEquals(INHERITED, outcome("minecraft:cow", "minecraft:warm", null, "minecraft:warm"));
        assertEquals(UNKNOWN, outcome("minecraft:cow", null, "minecraft:warm", "minecraft:cold"));
        assertEquals(NEW, outcome("naturalist:bear", "grizzly", "black", "black"));
    }

    @Test void pandaGenesExplainHiddenTraitsAndMutations() {
        assertEquals("normal", visiblePandaGene("brown/normal"));
        assertEquals("brown", visiblePandaGene("brown/brown"));
        assertEquals("lazy", visiblePandaGene("lazy/brown"));
        assertEquals("normal", visiblePandaGene("weak/lazy"), "A recessive main gene needs a matching hidden gene");
        assertEquals(HIDDEN_GENE, outcome(PANDA, "brown/brown", "normal/brown", "brown/normal"));
        assertEquals(INHERITED, outcome(PANDA, "lazy/normal", "lazy/worried", "normal/normal"));
        assertEquals(INHERITED, outcome(PANDA, "normal/lazy", "lazy/worried", "normal/normal"), "Either parent can give the main gene");
        assertEquals(NEW, outcome(PANDA, "playful/normal", "lazy/worried", "normal/normal"));
        assertEquals(UNKNOWN, outcome(PANDA, "lazy/normal", "lazy", "normal/normal"));
        assertNull(pandaGenes("lazy"));
        assertNull(pandaGenes("lazy/"));
    }

    @Test void savedOriginsReadBackAndOnlyDyedOrStruckSpeciesNeedThem() {
        for (var outcome : VariantRules.Outcome.values()) assertEquals(outcome, VariantRules.Outcome.parse(outcome.key()));
        assertEquals("hidden_gene", HIDDEN_GENE.key());
        assertEquals(UNKNOWN, VariantRules.Outcome.parse("from_a_future_version"));
        assertEquals(UNKNOWN, VariantRules.Outcome.parse(null));
        assertTrue(changesAfterBirth(SHEEP));
        assertTrue(changesAfterBirth("minecraft:mooshroom"));
        assertFalse(changesAfterBirth("minecraft:axolotl"));
        assertFalse(changesAfterBirth(PANDA));
    }

    @Test void notesExplainSurprisesAndStaySilentForInheritance() {
        assertEquals("familytree.variant.note.axolotl", noteKey("minecraft:axolotl", NEW));
        assertEquals("familytree.variant.note.mooshroom", noteKey("minecraft:mooshroom", NEW));
        assertEquals("familytree.variant.note.panda", noteKey(PANDA, NEW));
        assertEquals("familytree.variant.note.hidden_gene", noteKey(PANDA, HIDDEN_GENE));
        assertEquals("familytree.variant.note.mixed", noteKey(SHEEP, MIXED));
        assertEquals("familytree.variant.note.new", noteKey("minecraft:rabbit", NEW));
        assertNull(noteKey("minecraft:axolotl", INHERITED));
        assertNull(noteKey("minecraft:axolotl", UNKNOWN));
    }

    @Test void labelsCoverTheRequestedSpeciesAndModdedAnimalsButNotPortraitPets() {
        assertEquals("familytree.variant.axolotl.blue", labelKey("minecraft:axolotl", "blue"));
        assertEquals("familytree.variant.climate.warm", labelKey("minecraft:cow", "minecraft:warm"));
        assertEquals("familytree.variant.climate.cold", labelKey("minecraft:chicken", "minecraft:cold"));
        assertEquals("familytree.variant.frog.cold", labelKey("minecraft:frog", "minecraft:cold"));
        assertEquals("familytree.variant.llama.creamy", labelKey("minecraft:trader_llama", "creamy"));
        assertNull(labelKey(SHEEP, "pink"));
        assertTrue(labeled(SHEEP, "pink"));
        assertTrue(labeled(PANDA, "lazy/normal"));
        assertTrue(labeled("naturalist:bear", "grizzly"));
        assertFalse(labeled("minecraft:cat", "minecraft:tabby"));
        assertFalse(labeled("minecraft:horse", "chestnut/white"));
        assertFalse(labeled("minecraft:fox", null));
        assertEquals("White splotched", humanize("white_splotched"));
        assertEquals("Grizzly", humanize("naturalist:grizzly"));
    }

    @Test void everyVanillaVariantHasEnglishText() throws Exception {
        JsonObject english;
        try (var stream = VariantRulesTest.class.getResourceAsStream("/assets/familytree/lang/en_us.json")) {
            english = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var variants = List.of(
                List.of("minecraft:axolotl", "lucy", "wild", "gold", "cyan", "blue"),
                List.of("minecraft:rabbit", "brown", "white", "black", "white_splotched", "gold", "salt", "evil"),
                List.of("minecraft:fox", "red", "snow"),
                List.of("minecraft:frog", "minecraft:temperate", "minecraft:warm", "minecraft:cold"),
                List.of("minecraft:mooshroom", "red", "brown"),
                List.of("minecraft:llama", "creamy", "white", "brown", "gray"),
                List.of("minecraft:cow", "minecraft:temperate", "minecraft:warm", "minecraft:cold"));
        for (var species : variants)
            for (String variant : species.subList(1, species.size()))
                assertTrue(english.has(labelKey(species.get(0), variant)), labelKey(species.get(0), variant));
        for (String gene : List.of("normal", "lazy", "worried", "playful", "brown", "weak", "aggressive"))
            assertTrue(english.has(pandaGeneKey(gene)), gene);
        for (var outcome : List.of(NEW, MIXED, HIDDEN_GENE))
            for (String species : List.of("minecraft:axolotl", "minecraft:mooshroom", PANDA, SHEEP, "minecraft:rabbit"))
                if (noteKey(species, outcome) != null) assertTrue(english.has(noteKey(species, outcome)), noteKey(species, outcome));
    }
}
