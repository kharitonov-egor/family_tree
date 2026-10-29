package com.egakh.familytree.menu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MenuWrapTest {
    @Test
    void wrapsAtSpaces() {
        assertEquals(List.of("Click a pet to", "see its family."), Menus.wrap("Click a pet to see its family.", 15));
    }

    @Test
    void wrapsWideTextWithoutSpaces() {
        List<String> lines = Menus.wrap("ペットをクリックすると家族が表示されます", 10);
        assertTrue(lines.size() > 1);
        for (String line : lines) assertTrue(line.codePointCount(0, line.length()) <= 5, line);
        assertEquals("ペットをクリックすると家族が表示されます", String.join("", lines));
    }

    @Test
    void keepsLongWordsAndExplicitBreaks() {
        assertEquals(List.of("Supercal", "ifragili", "stic", "word"), Menus.wrap("Supercalifragilistic word", 8));
        assertEquals(List.of("one", "two"), Menus.wrap("one\ntwo", 40));
    }
}
