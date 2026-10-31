package com.egakh.familytree.client.screen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BrowserLayoutTest {
    /** GUI sizes from the QA report: 1920x1080 at Auto (4), 1600x1000 at 4, 1366x768 at Auto (3), 854x480 at 2, and wider scales. */
    private static final int[][] SIZES = {{480, 270}, {400, 250}, {455, 256}, {427, 240}, {320, 240}, {533, 333}, {640, 360}, {960, 540}, {1920, 1080}};

    private static final String[] ENGLISH = {"Find existing pets", "All", "Alive", "Deceased", "Sort: Health", "Showing: Mine",
            "Settings", "Getting started", "Refresh", "Send feedback", "More..."};
    private static final String[] ENGLISH_TABS = {"Pets", "Farm", "Wildlife", "All animals"};
    private static final String[] ENGLISH_SPECIES = {"Wolf", "Cat", "Parrot", "Camel", "Donkey", "Horse", "Llama", "Mule", "Skeleton Horse"};
    private static final String[] GERMAN = {"Haustiere finden", "Alle", "Lebend", "Verstorben", "Sortieren: Gesundheit", "Anzeige: Eigene",
            "Einstellungen", "Erste Schritte", "Aktualisieren", "Feedback senden", "Mehr …"};
    private static final String[] GERMAN_TABS = {"Haustiere", "Bauernhof", "Wildtiere", "Alle Tiere"};
    private static final String[] GERMAN_SPECIES = {"Wolf", "Katze", "Papagei", "Dromedar", "Esel", "Pferd", "Lama", "Maultier", "Skelettpferd"};

    @Test
    void listKeepsTwoFullRowsAtEveryQaSize() {
        for (int[] size : SIZES) {
            for (BrowserLayout.Widths widths : List.of(widths(ENGLISH, ENGLISH_TABS, ENGLISH_SPECIES), widths(GERMAN, GERMAN_TABS, GERMAN_SPECIES),
                    widths(ENGLISH, ENGLISH_TABS, manySpecies()))) {
                BrowserLayout.Plan plan = BrowserLayout.plan(widths, size[0], size[1]);
                int list = size[1] - BrowserLayout.BOTTOM_MARGIN - plan.listTop();
                assertTrue(list >= BrowserLayout.MIN_LIST, size[0] + "x" + size[1] + " left " + list + " px for the list");
                assertControlsFit(plan, size[0]);
            }
        }
    }

    /** Every shipped language with all five tabs, measured about as wide as Minecraft's font draws them, or wider. */
    @Test
    void everyLanguageKeepsTwoRowsWithFiveTabs() {
        for (String language : com.egakh.familytree.text.LangTables.SHIPPED) {
            java.util.Map<String, String> lang = com.egakh.familytree.text.LangTables.table(language);
            java.util.function.ToIntFunction<String> key = name -> width(lang.get(name));
            int sort = 0;
            for (String stat : List.of("name", "speed", "jump", "health"))
                sort = Math.max(sort, width(lang.get("familytree.screen.sort").replace("%s", lang.get("familytree.screen.sort." + stat))));
            int[] tabs = List.of("pets", "farm", "wildlife", "modded", "all").stream()
                    .mapToInt(tab -> key.applyAsInt("familytree.screen.category." + tab)).toArray();
            BrowserLayout.Widths widths = new BrowserLayout.Widths(key.applyAsInt("familytree.discover.button"),
                    new int[]{key.applyAsInt("familytree.screen.filter.all"), key.applyAsInt("familytree.screen.filter.alive"),
                            key.applyAsInt("familytree.screen.filter.deceased")}, sort,
                    Math.max(key.applyAsInt("familytree.screen.scope.all"), key.applyAsInt("familytree.screen.scope.mine")),
                    key.applyAsInt("familytree.screen.settings"), key.applyAsInt("familytree.help.button"), key.applyAsInt("familytree.refresh"),
                    key.applyAsInt("familytree.feedback.button"), key.applyAsInt("familytree.screen.more"), tabs,
                    Arrays.stream(tabs).max().orElse(0) + 24, speciesWidths(GERMAN_SPECIES));
            for (int[] size : SIZES) {
                BrowserLayout.Plan plan = BrowserLayout.plan(widths, size[0], size[1]);
                int list = size[1] - BrowserLayout.BOTTOM_MARGIN - plan.listTop();
                assertTrue(list >= BrowserLayout.MIN_LIST, language + " at " + size[0] + "x" + size[1] + " left " + list + " px for the list");
                assertControlsFit(plan, size[0]);
            }
        }
    }

    private static int width(String text) {
        return Math.max(40, text.codePoints().map(c -> c < 0x2E80 ? 6 : 9).sum() + 16);
    }

    @Test
    void wideScreensKeepEveryButtonAndGiveSpeciesTheirOwnRow() {
        BrowserLayout.Plan plan = BrowserLayout.plan(widths(ENGLISH, ENGLISH_TABS, ENGLISH_SPECIES), 960, 540);
        assertFalse(plan.collapsed());
        for (String key : List.of("settings", "help", "refresh", "feedback")) assertNotNull(plan.slot(key), key);
        HeaderFlow.Slot strip = plan.slot("species");
        for (HeaderFlow.Slot slot : plan.flow().slots())
            if (!slot.key().equals("species")) assertNotEquals(strip.y(), slot.y(), slot.key() + " shares the species row");
        assertEquals(ENGLISH_SPECIES.length, BrowserLayout.visibleSpecies(speciesWidths(ENGLISH_SPECIES), 0, strip.width())[1]);
    }

    @Test
    void smallestScreenFoldsRareButtonsIntoMore() {
        BrowserLayout.Plan plan = BrowserLayout.plan(widths(GERMAN, GERMAN_TABS, GERMAN_SPECIES), 320, 240);
        assertTrue(plan.collapsed());
        assertNotNull(plan.slot("more"));
        assertNull(plan.slot("settings"));
    }

    @Test
    void speciesStripPagesThroughEverySpecies() {
        int[] species = speciesWidths(manySpecies());
        int width = 200, offset = 0;
        List<Integer> seen = new ArrayList<>();
        while (offset < species.length) {
            int[] shown = BrowserLayout.visibleSpecies(species, offset, width);
            assertTrue(shown[1] > shown[0]);
            int used = 2 * (BrowserLayout.ARROW + HeaderFlow.GAP) - HeaderFlow.GAP;
            for (int i = shown[0]; i < shown[1]; i++) { seen.add(i); used += species[i] + HeaderFlow.GAP; }
            assertTrue(used <= width + HeaderFlow.GAP || shown[1] - shown[0] == 1, "page overflows the strip");
            if (shown[1] < species.length)
                assertTrue(BrowserLayout.previousPage(species, shown[1], width) <= offset, "the < arrow skips species");
            offset = shown[1];
        }
        assertEquals(species.length, seen.size());
        assertEquals(0, BrowserLayout.previousPage(species, 1, width));
    }

    private static void assertControlsFit(BrowserLayout.Plan plan, int width) {
        List<HeaderFlow.Slot> slots = plan.flow().slots();
        for (HeaderFlow.Slot slot : slots) {
            assertTrue(slot.x() >= BrowserLayout.MARGIN && slot.x() + slot.width() <= width - BrowserLayout.MARGIN, slot + " leaves the screen");
            assertTrue(slot.y() + 18 <= plan.listTop() - 2, slot + " runs into the list");
            for (HeaderFlow.Slot other : slots) {
                if (other == slot || other.y() != slot.y()) continue;
                assertTrue(slot.x() + slot.width() <= other.x() || other.x() + other.width() <= slot.x(), slot + " overlaps " + other);
            }
        }
    }

    private static String[] manySpecies() {
        String[] species = new String[30];
        for (int i = 0; i < species.length; i++) species[i] = "Modded animal " + i;
        return species;
    }

    private static int label(String text, int minimum) {
        return Math.max(minimum, text.length() * 6 + 16);
    }

    private static int[] speciesWidths(String[] species) {
        return Arrays.stream(species).mapToInt(name -> label(name, 40)).toArray();
    }

    private static BrowserLayout.Widths widths(String[] labels, String[] tabs, String[] species) {
        return new BrowserLayout.Widths(label(labels[0], 60), new int[]{label(labels[1], 36), label(labels[2], 36), label(labels[3], 36)},
                label(labels[4], 60), label(labels[5], 60), label(labels[6], 40), label(labels[7], 40), label(labels[8], 40),
                label(labels[9], 40), label(labels[10], 40), Arrays.stream(tabs).mapToInt(tab -> label(tab, 40)).toArray(),
                Arrays.stream(tabs).mapToInt(tab -> label("< " + tab + " >", 40)).max().orElse(0), speciesWidths(species));
    }
}
