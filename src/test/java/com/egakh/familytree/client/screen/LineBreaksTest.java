package com.egakh.familytree.client.screen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.*;

class LineBreaksTest {
    /** Minecraft draws Latin letters about 6 px wide and CJK characters 9 px wide at GUI scale 1. */
    private static final ToIntFunction<String> WIDTH = text -> text.codePoints().map(c -> c < 0x2E80 ? 6 : 9).sum();

    private static final String JAPANESE = "Family Treeを導入した状態で動物を繁殖させると、子は両親と一緒に家系図に加わり、世代をまたいで血統をたどれます。";

    @Test
    void japaneseKeepsTheModNameTogetherAndFillsLines() {
        for (int width = 120; width <= 520; width += 7) {
            List<String> lines = LineBreaks.split(JAPANESE, width, WIDTH);
            assertEquals(JAPANESE, String.join("", lines), "no text lost at " + width);
            assertTrue(lines.get(0).startsWith("Family Tree"), "the mod name split at " + width + ": " + lines);
            for (String line : lines) assertTrue(WIDTH.applyAsInt(line) <= width, line + " is wider than " + width);
            assertTrue(WIDTH.applyAsInt(lines.get(0)) > width - 3 * 9, "the first line stops early at " + width + ": " + lines);
        }
    }

    @Test
    void japanesePunctuationNeverStartsALine() {
        for (int width = 60; width <= 300; width += 3)
            for (String line : LineBreaks.split(JAPANESE, width, WIDTH))
                assertFalse(line.startsWith("、") || line.startsWith("。"), "line starts with punctuation at " + width + ": " + line);
    }

    @Test
    void englishStillWrapsAtSpaces() {
        String text = "Breed animals while Family Tree is installed. Each baby joins the tree with both parents.";
        List<String> lines = LineBreaks.split(text, 120, WIDTH);
        for (String line : lines) {
            assertTrue(WIDTH.applyAsInt(line) <= 120, line);
            assertFalse(line.startsWith(" ") || line.endsWith(" "), "'" + line + "'");
        }
        assertEquals(text, String.join(" ", lines));
        assertTrue(lines.stream().noneMatch(line -> line.endsWith("Family")), "the mod name split: " + lines);
    }

    @Test
    void wordsLongerThanTheLineBreakByCharacter() {
        List<String> lines = LineBreaks.split("Supercalifragilistic", 30, WIDTH);
        assertEquals("Supercalifragilistic", String.join("", lines));
        for (String line : lines) assertTrue(WIDTH.applyAsInt(line) <= 30, line);
    }
}
