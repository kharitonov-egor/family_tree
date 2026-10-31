package com.egakh.familytree.client.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntryWrapTest {
    private static int[] gaps(int count, int first, int rest) {
        int[] gaps = new int[count];
        for (int i = 1; i < count; i++) gaps[i] = i == 1 ? first : rest;
        return gaps;
    }

    @Test
    void breaksOnlyBetweenEntries() {
        assertEquals(List.of(0, 2), EntryWrap.lineStarts(new int[]{40, 40, 40}, gaps(3, 4, 4), 90));
        assertEquals(List.of(0), EntryWrap.lineStarts(new int[]{40, 40}, gaps(2, 4, 4), 84));
        assertEquals(List.of(0, 1), EntryWrap.lineStarts(new int[]{40, 40}, gaps(2, 4, 4), 83));
    }

    @Test
    void anEntryWiderThanTheLineStandsAlone() {
        assertEquals(List.of(0, 1, 2), EntryWrap.lineStarts(new int[]{10, 200, 10}, gaps(3, 4, 4), 100));
        assertEquals(List.of(0), EntryWrap.lineStarts(new int[]{200}, new int[1], 100));
        assertEquals(List.of(), EntryWrap.lineStarts(new int[0], new int[0], 100));
    }

    /** The Russian panda case from the review: a 265-pixel entry fits a 296-pixel line, but not after its title. */
    @Test
    void aLongFirstEntryLeavesTheTitleOnALineOfItsOwn() {
        assertEquals(List.of(0, 1, 2), EntryWrap.lineStarts(new int[]{62, 265, 150}, gaps(3, 0, 4), 296));
        assertEquals(List.of(0, 2), EntryWrap.lineStarts(new int[]{30, 200, 150}, gaps(3, 0, 4), 296));
    }
}
