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
                p -> p.deceased() ? "Remembered | died on day 82" : "Alive", p -> null, p -> List.of(), p -> false, null);
        assertEquals(5, picture.cards().size());
        assertEquals(4, picture.lines().size());
        assertTrue(picture.height() > 900);
        assertTrue(picture.cards().stream().noneMatch(c -> c.detail().contains("PRIVATE OWNER")));
        assertNull(picture.legend());
        willow.setTreeName("Changed after export started");
        assertTrue(picture.cards().stream().anyMatch(c -> c.name().equals("Willow")));
        Path file = TreeImageExporter.save(picture, directory.resolve("images"), "Moss");
        var image = ImageIO.read(file.toFile());
        assertEquals(picture.width(), image.getWidth());
        assertEquals(picture.height(), image.getHeight());
        var firstCard = picture.cards().get(0);
        assertNotEquals(image.getRGB(0, 0), image.getRGB(
                (int) (firstCard.x() + 10) * TreeImageExporter.SCALE,
                (int) (firstCard.y() + 10) * TreeImageExporter.SCALE));
        Path second = TreeImageExporter.save(picture, directory.resolve("images"), "Moss");
        assertNotEquals(file, second);
        String today = java.time.LocalDate.now().toString();
        assertEquals("family-tree-Moss-" + today + ".png", file.getFileName().toString());
        assertEquals("family-tree-Moss-" + today + "-2.png", second.getFileName().toString());
    }

    /** QA B2: a card whose parent was pruned gets a mark, and the note under the tree wraps instead of being cut. */
    @Test void aRemovedParentIsMarkedAndTheFooterNoteWraps() throws Exception {
        Map<UUID, AnimalRecord> records = new LinkedHashMap<>();
        AnimalRecord clover = pet("Clover", null, null), lamb = pet("Lamb", clover, null);
        lamb.setParents(clover.id(), UUID.randomUUID());
        records.put(clover.id(), clover);
        records.put(lamb.id(), lamb);
        var tree = TreeLayout.layoutForest(new ArrayList<>(records.keySet()), records, Map.of(clover.id(), List.of(lamb.id())));
        var plain = TreeImageExporter.prepare(tree, "Lamb", "Family Tree", p -> "", p -> "Alive", p -> null,
                p -> List.of(), p -> false, null);
        String note = "A short line above a card marks a parent that was removed from the history or isn't shown. ".repeat(4);
        var marked = TreeImageExporter.prepare(tree, "Lamb", note + "\nFamily Tree",
                p -> "", p -> "Alive", p -> null, p -> List.of(), p -> false, null,
                p -> com.egakh.familytree.util.MissingParents.hasMissingParent(p, records::get));
        assertEquals(Map.of("Clover", false, "Lamb", true), marked.cards().stream()
                .collect(java.util.stream.Collectors.toMap(TreeImageExporter.Card::name, TreeImageExporter.Card::missingParent)));
        assertTrue(marked.height() >= plain.height() + 2 * 16 * TreeImageExporter.SCALE, "the note wraps above the footer");
        var image = ImageIO.read(TreeImageExporter.save(marked, directory.resolve("marked"), "Lamb").toFile());
        assertEquals(marked.height(), image.getHeight());
        var japanese = TreeImageExporter.prepare(tree, "Lamb", "カードの上の短い線は、記録から削除されたか表示されていない親を示します。".repeat(3),
                p -> "", p -> "Alive", p -> null, p -> List.of(), p -> false, null, p -> false);
        assertTrue(japanese.height() >= plain.height() + 2 * 16 * TreeImageExporter.SCALE, "text without spaces wraps too");
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
                p -> "Gen 1", p -> "Alive", p -> null, p -> p == ember ? stats : List.of(), p -> false,
                new TreeImageExporter.Legend("higher than both parents", "lower than both parents", "hearts", "rare variant"));
        assertNotNull(picture.legend());
        assertTrue(picture.legend().statMarkers());
        assertFalse(picture.legend().rareMarkers());
        var foal = picture.cards().stream().filter(card -> card.name().equals("Ember")).findFirst().orElseThrow();
        assertEquals(stats, foal.stats());
        assertTrue(picture.cards().stream().filter(card -> card != foal).allMatch(card -> card.stats().isEmpty()));
        var image = ImageIO.read(TreeImageExporter.save(picture, directory.resolve("horses"), "Comet").toFile());
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
                p -> "", p -> "", p -> null, p -> List.of(), p -> false, null));
        assertThrows(IllegalArgumentException.class, () -> TreeImageExporter.requireExportable(tree));
    }

    @Test void rareVariantsGetAGoldStarAndOnlyTheLegendEntryTheyUse() throws Exception {
        Map<UUID, AnimalRecord> records = new LinkedHashMap<>();
        AnimalRecord pink = pet("Pinky", null, null), wild = pet("Mud", null, null), blue = pet("Bluebell", pink, wild);
        for (AnimalRecord axolotl : List.of(pink, wild, blue)) records.put(axolotl.id(), axolotl);
        Map<UUID, List<UUID>> children = Map.of(pink.id(), List.of(blue.id()), wild.id(), List.of(blue.id()));
        var tree = TreeLayout.layoutForest(new ArrayList<>(records.keySet()), records, children);
        var portrait = new java.awt.image.BufferedImage(96, 96, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var picture = TreeImageExporter.prepare(tree, "Bluebell's family", "Family Tree",
                p -> p == blue ? "Gen 2 \u00B7 Blue" : "Gen 1", p -> "Alive", p -> portrait, p -> List.of(), p -> p == blue,
                new TreeImageExporter.Legend("higher than both parents", "lower than both parents", "hearts", "rare variant"));
        assertNotNull(picture.legend());
        assertFalse(picture.legend().statMarkers());
        assertTrue(picture.legend().rareMarkers());
        var rare = picture.cards().stream().filter(TreeImageExporter.Card::rare).toList();
        assertEquals(List.of("Bluebell"), rare.stream().map(TreeImageExporter.Card::name).toList());
        var image = ImageIO.read(TreeImageExporter.save(picture, directory.resolve("axolotls"), "Bluebell").toFile());
        assertTrue(contains(image, rare.get(0), 0xFFC94A), "Missing the rare star");
        var plain = picture.cards().stream().filter(card -> !card.rare()).findFirst().orElseThrow();
        assertFalse(contains(image, plain, 0xFFC94A), "A common variant has a star");
    }

    @Test void wideFarmForestsWrapIntoRowsInsteadOfOneStrip() {
        Map<UUID, AnimalRecord> records = new LinkedHashMap<>();
        for (int i = 0; i < 300; i++) {
            AnimalRecord cow = pet("Cow " + i, null, null);
            records.put(cow.id(), cow);
        }
        var tree = TreeLayout.layoutForest(new ArrayList<>(records.keySet()), records, Map.of());
        assertEquals(300, tree.nodes.size());
        double width = tree.maxX - tree.minX, height = tree.maxY - tree.minY;
        assertTrue(width < 20 * height, "Forest is " + width + " wide and " + height + " tall");
        assertEquals(300, tree.nodes.stream().map(node -> node.x + "," + node.y).distinct().count(), "Cards overlap");
    }

    @Test void fileNamesUseTheSubjectAndDateAndStayValidOnEverySystem() {
        var day = java.time.LocalDate.of(2026, 10, 31);
        assertEquals("family-tree-Comet-2026-10-31.png", TreeImageExporter.fileName("Comet", day, 1));
        assertEquals("family-tree-Comet-2026-10-31-3.png", TreeImageExporter.fileName("\"Comet\"", day, 3));
        assertEquals("family-tree-Sir-Fluff-a-lot-2026-10-31.png", TreeImageExporter.fileName("Sir Fluff/a:lot?", day, 1));
        assertEquals("family-tree-Снежок-2026-10-31.png", TreeImageExporter.fileName("Снежок", day, 1));
        assertEquals("family-tree-2026-10-31.png", TreeImageExporter.fileName("../..", day, 1));
        assertTrue(TreeImageExporter.fileName("x".repeat(500), day, 1).length() < 80);
    }

    @Test void deepTreesMarkHiddenGenerationsInTheImage() throws Exception {
        Map<UUID, AnimalRecord> records = new LinkedHashMap<>();
        Map<UUID, List<UUID>> children = new HashMap<>();
        AnimalRecord line = pet("Deep 1", null, null);
        records.put(line.id(), line);
        for (int generation = 2; generation <= 10; generation++) {
            AnimalRecord next = pet("Deep " + generation, line, null);
            records.put(next.id(), next);
            children.computeIfAbsent(line.id(), id -> new ArrayList<>()).add(next.id());
            line = next;
        }
        var tree = TreeLayout.layout(line.id(), records, children);
        var picture = TreeImageExporter.prepare(tree, "Deep 10", "Family Tree",
                p -> "", p -> "Alive", p -> null, p -> List.of(), p -> false, null);
        assertEquals(1, picture.markers().size());
        assertTrue(picture.markers().get(0).older());
        assertEquals(3, picture.markers().get(0).generations());
        var image = ImageIO.read(TreeImageExporter.save(picture, directory.resolve("deep"), "Deep 10").toFile());
        var marker = picture.markers().get(0);
        boolean gold = false;
        for (int x = (int) (marker.x() - 20) * TreeImageExporter.SCALE; x < (marker.x() + 20) * TreeImageExporter.SCALE; x++)
            for (int y = (int) (marker.y() - 20) * TreeImageExporter.SCALE; y < marker.y() * TreeImageExporter.SCALE; y++)
                if ((image.getRGB(x, y) & 0xFFFFFF) == 0xD8B671) gold = true;
        assertTrue(gold, "Missing the older-generations marker above the top card");
    }

    private static AnimalRecord pet(String name, AnimalRecord a, AnimalRecord b) {
        return new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", name, false,
                a == null ? null : a.id(), b == null ? null : b.id(), 1, 1000, false, null, null,
                UUID.randomUUID(), "PRIVATE OWNER", "minecraft:pale",
                new AnimalRecord.LastSeen(100, 64, 200, "minecraft:overworld", 5), null);
    }
}
