package com.egakh.familytree.client.screen;

import java.util.ArrayList;
import java.util.List;

/** Fills lines with entries of known widths and breaks only between entries, so "Gray 1.59%" stays on one line. */
final class EntryWrap {
    private EntryWrap() {}

    /**
     * The index of the first entry on each line. {@code gaps[i]} is the space before entry {@code i} when it shares a line
     * with the one before. An entry wider than the line gets a line to itself.
     */
    static List<Integer> lineStarts(int[] widths, int[] gaps, int maxWidth) {
        List<Integer> starts = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < widths.length; i++) {
            if (i == 0 || used + gaps[i] + widths[i] > maxWidth) {
                starts.add(i);
                used = widths[i];
            } else {
                used += gaps[i] + widths[i];
            }
        }
        return starts;
    }
}
