package com.egakh.familytree.client.screen;

import com.egakh.familytree.text.LangTables;
import com.egakh.familytree.util.PetNames;
import com.ibm.icu.text.Bidi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BidiLinesTest {
    /** The QA report's name: Chinese first, so the whole name reads left to right, with Arabic inside. */
    private static final String NAME = "小白 🐶 كلب سعيد Ünïcødé";

    @Test
    void aNameSplitAcrossLinesKeepsEachLineLeftToRight() {
        String sentence = String.format(LangTables.template("en_us", "familytree.kin.parent"), "Jelly", PetNames.isolate(NAME));
        int split = sentence.indexOf('\uD83D');

        List<String> plainLines = List.of(sentence.substring(0, split), sentence.substring(split));
        assertEquals(1, new Bidi(plainLines.get(1), Bidi.LEVEL_DEFAULT_LTR).getParaLevel(),
                "Minecraft orders lines one at a time, so a continuation that starts in Arabic turns right to left");

        List<String> lines = BidiLines.wrap(sentence, text -> List.of(text.substring(0, split), text.substring(split)));
        for (String line : lines) {
            Bidi bidi = new Bidi(line, Bidi.LEVEL_DEFAULT_LTR);
            assertEquals(0, bidi.getParaLevel(), line);
        }
        String second = new Bidi(lines.get(1), Bidi.LEVEL_DEFAULT_LTR).writeReordered(Bidi.DO_MIRRORING | Bidi.REMOVE_BIDI_CONTROLS);
        assertTrue(second.endsWith("Ünïcødé's parent."), second);
        assertTrue(second.startsWith("🐶"), "The name keeps its left-to-right order: " + second);
    }

    @Test
    void firstStrongIsolatesResolveFromTheirWholeContent() {
        assertEquals("\u2066" + NAME + "\u2069", BidiLines.resolve("\u2068" + NAME + "\u2069"));
        assertEquals("a \u2067كلب x\u2069 b", BidiLines.resolve("a \u2068كلب x\u2069 b"));
        assertEquals("Rex", BidiLines.resolve("Rex"));
    }

    @Test
    void openIsolatesCloseAtLineEndsAndReopenOnTheNextLine() {
        assertEquals(List.of("a \u2067b\u2069", "\u2067c\u2069 d"), BidiLines.carry(List.of("a \u2067b", "c\u2069 d")));
        assertEquals(List.of("x", "y"), BidiLines.carry(List.of("x", "y")));
        assertEquals("\u2066Wolf\u2069", BidiLines.balance("\u2066Wolf"));
        assertEquals("plain", BidiLines.balance("plain"));
    }
}
