package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.export.TreeImageExporter;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.util.HorseStatUnits.Trend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TreeImageExporterTest {
    @TempDir Path directory;

    @Test void savesAllVisibleGenerationsAtFixedResolutionWithoutPrivateData() throws Exception {
        Map<UUID, AnimalRecord> records = new LinkedHashMap<>();
        AnimalRecord moss = pet("Moss", null, null);
        AnimalRecord fern = pet("Fern", null, null);
        AnimalRecord willow = pet("Willow", moss, fern);
        AnimalRecord cedar = pet("Cedar", null, null);
        AnimalRecord clover = pet("Clover", willow, cedar);
        moss.markDeceased(82, 5000, null);
        for (AnimalRecord pet : List.of(moss, fern, willow, cedar, clover)) records.put(pet.id(), pet);
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (AnimalRecord pet : records.values()) {
            if (pet.parentA() != null) children.computeIfAbsent(pet.parentA(), id -> new ArrayList<>()).add(pet.id());
            if (pet.parentB() != null) children.computeIfAbsent(pet.parentB(), id -> new ArrayList<>()).add(pet.id());
        }
        var tree = TreeLayout.layoutForest(new ArrayList<>(records.keySet()), records, children);
        var picture = TreeImageExporter.prepare(tree, "Moss's family", "Family Tree",
                p -> "Generation " + (p == clover ? 3 : p == willow ? 2 : 1),
                p -> p.deceased() ? "Remembered | died on day 82" : "Alive", p -> null, p -> List.of(), null);
        assertEquals(5, picture.cards().size());
        assertEquals(4, picture.lines().size());
        assertTrue(picture.height() > 900);
        assertTrue(picture.cards().stream().noneMatch(c -> c.detail().contains("PRIVATE OWNER")));
        assertNull(picture.legend());
        willow.setTreeName("Changed after export started");
        assertTrue(picture.cards().stream().anyMatch(c -> c.name().equals("Willow")));
        Path file = TreeImageExporter.save(picture, directory.resolve("images"));
        var image = ImageIO.read(file.toFile());
        assertEquals(picture.width(), image.getWidth());
        assertEquals(picture.height(), image.getHeight());
        var firstCard = picture.cards().get(0);
        assertNotEquals(image.getRGB(0, 0), image.getRGB(
                (int) (firstCard.x() + 10) * TreeImageExporter.SCALE,
                (int) (firstCard.y() + 10) * TreeImageExporter.SCALE));
        Path second = TreeImageExporter.save(picture, directory.resolve("images"));
        assertNotEquals(file, second);
    }

    @Test void horseCardsCarryStatsAndDrawMarkersForFoalsThatBeatOrTrailBothParents() throws Exception {
        Map<UUID, AnimalRecord> records = new LinkedHashMap<>();
        AnimalRecord comet = pet("Comet", null, null), dusk = pet("Dusk", null, null), ember = pet("Ember", comet, dusk);
        for (AnimalRecord horse : List.of(comet, dusk, ember)) records.put(horse.id(), horse);
        Map<UUID, List<UUID>> children = Map.of(comet.id(), List.of(ember.id()), dusk.id(), List.of(ember.id()));
        var tree = TreeLayout.layoutForest(new ArrayList<>(records.keySet()), records, children);
        var stats = List.of(new HorseStatsText.Segment("14.6 b/s", Trend.BEATS_BOTH),
                new HorseStatsText.Segment("3.12 jump", Trend.TRAILS_BOTH),
                new HorseStatsText.Segment("11.0 \u2665", Trend.BETWEEN));
        var picture = TreeImageExporter.prepare(tree, "Comet's family", "Family Tree",
                p -> "Gen 1", p -> "Alive", p -> null, p -> p == ember ? stats : List.of(),
                new TreeImageExporter.Legend("higher than both parents", "lower than both parents", "hearts"));
        assertNotNull(picture.legend());
        var foal = picture.cards().stream().filter(card -> card.name().equals("Ember")).findFirst().orElseThrow();
        assertEquals(stats, foal.stats());
        assertTrue(picture.cards().stream().filter(card -> card != foal).allMatch(card -> card.stats().isEmpty()));
        var image = ImageIO.read(TreeImageExporter.save(picture, directory.resolve("horses")).toFile());
        assertTrue(contains(image, foal, 0x6FD26F), "Missing the higher-than-both-parents marker");
        assertTrue(contains(image, foal, 0xE86B6B), "Missing the lower-than-both-parents marker");
    }

    private static boolean contains(java.awt.image.BufferedImage image, TreeImageExporter.Card card, int rgb) {
        int scale = TreeImageExporter.SCALE;
        for (int x = (int) card.x() * scale; x < (card.x() + TreeLayout.NODE_WIDTH) * scale; x++)
            for (int y = (int) card.y() * scale; y < (card.y() + TreeLayout.NODE_HEIGHT) * scale; y++)
                if ((image.getRGB(x, y) & 0xFFFFFF) == rgb) return true;
        return false;
    }

    @Test void refusesOversizedTreesBeforeAllocatingTheImage() {
        var tree = new TreeLayout.Result();
        AnimalRecord pet = pet("Very large tree", null, null);
        tree.nodes.add(new TreeLayout.Node(pet.id(), pet, 1));
        tree.maxX = 100_000;
        tree.maxY = 100_000;
        assertThrows(IllegalArgumentException.class, () -> TreeImageExporter.prepare(tree, "Tree", "",
                p -> "", p -> "", p -> null, p -> List.of(), null));
    }

    private static AnimalRecord pet(String name, AnimalRecord a, AnimalRecord b) {
        return new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", name, false,
                a == null ? null : a.id(), b == null ? null : b.id(), 1, 1000, false, null, null,
                UUID.randomUUID(), "PRIVATE OWNER", "minecraft:pale",
                new AnimalRecord.LastSeen(100, 64, 200, "minecraft:overworld", 5), null);
    }
}
