package com.egakh.familytree.plugin.text;

import com.egakh.familytree.breeding.BreedingPlan;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.util.Kinship;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlanLinesTest {
    private static AnimalRecord horse(long id, String name, UUID a, UUID b, HorseStats stats) {
        AnimalRecord horse = new AnimalRecord(new UUID(0, id), "minecraft:horse", name, false, a, b, 1, 2, false, null, null,
                null, null, "chestnut/white", null, null);
        horse.setHorseStats(stats);
        return horse;
    }

    @Test void siblingsGetTheirRelationStatsAndOddsInChat() {
        AnimalRecord mother = horse(1, "Mare", null, null, new HorseStats(0.2, 0.6, 24.0, null));
        AnimalRecord father = horse(2, "Stallion", null, null, new HorseStats(0.3, 0.8, 28.0, null));
        AnimalRecord quill = horse(3, "Quill", mother.id(), father.id(), new HorseStats(0.25, 0.7, 26.0, null));
        AnimalRecord sorrel = horse(4, "Sorrel", mother.id(), father.id(), new HorseStats(0.28, 0.75, 27.0, null));
        Map<UUID, AnimalRecord> all = Map.of(mother.id(), mother, father.id(), father, quill.id(), quill, sorrel.id(), sorrel);
        List<Text> lines = PlanLines.chat("en_us", new BreedingPlan(quill, sorrel, new Kinship(all)));
        List<String> plain = lines.stream().map(line -> line.plain(VanillaLang::english)).toList();
        assertTrue(plain.get(0).contains("Quill") && plain.get(0).contains("Sorrel"), plain.get(0));
        assertEquals(Text.Color.YELLOW, lines.get(0).color());
        assertTrue(plain.stream().anyMatch(line -> line.contains("siblings")), plain.toString());
        assertTrue(plain.stream().anyMatch(line -> line.contains("25%")), "full siblings' foal is 25% inbred: " + plain);
        assertTrue(plain.stream().filter(line -> line.startsWith("  ")).count() >= 3, "muted lines are indented");
    }

    @Test void callsTheBabyALambForSheep() {
        UUID ewe = new UUID(0, 5), ram = new UUID(0, 6);
        Map<UUID, AnimalRecord> all = Map.of(
                ewe, new AnimalRecord(ewe, "minecraft:sheep", "Ewe", false, null, null, 1, 2, false, null, null, null, null, "white", null, null),
                ram, new AnimalRecord(ram, "minecraft:sheep", "Ram", false, null, null, 1, 2, false, null, null, null, null, "black", null, null));
        List<String> plain = PlanLines.chat("en_us", new BreedingPlan(all.get(ewe), all.get(ram), new Kinship(all))).stream()
                .map(line -> line.plain(VanillaLang::english)).toList();
        assertTrue(plain.contains("Lamb: Sheep"), plain.toString());
        assertTrue(plain.contains("Lamb's inbreeding coefficient: 0%"), plain.toString());
        assertTrue(plain.stream().noneMatch(line -> line.toLowerCase(java.util.Locale.ROOT).contains("foal")), plain.toString());
    }

    @Test void percentagesKeepThreeSignificantDigits() {
        assertEquals("12.5%", PlanLines.percent(0.125));
        assertEquals("0.0833%", PlanLines.percent(1 / 1200.0));
        assertEquals("0%", PlanLines.percent(0));
        assertEquals("99.9%", PlanLines.percent(0.9996));
        assertEquals("100%", PlanLines.percent(1));
    }
}
