package com.egakh.familytree.client.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntryWrapTest {
    @Test
    void breaksOnlyBetweenEntries() {
        assertEquals(List.of(0, 2), EntryWrap.lineStarts(new int[]{40, 40, 40}, 4, 90));
        assertEquals(List.of(0), EntryWrap.lineStarts(new int[]{40, 40}, 4, 84));
        assertEquals(List.of(0, 1), EntryWrap.lineStarts(new int[]{40, 40}, 4, 83));
    }

    @Test
    void anEntryWiderThanTheLineStandsAlone() {
        assertEquals(List.of(0, 1, 2), EntryWrap.lineStarts(new int[]{10, 200, 10}, 4, 100));
        assertEquals(List.of(0), EntryWrap.lineStarts(new int[]{200}, 4, 100));
        assertEquals(List.of(), EntryWrap.lineStarts(new int[0], 4, 100));
    }
}
