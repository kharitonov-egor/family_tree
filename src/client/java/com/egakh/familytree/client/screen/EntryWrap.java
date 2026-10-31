package com.egakh.familytree.client.screen;

import java.util.ArrayList;
import java.util.List;

/** Fills lines with entries of known widths and breaks only between entries, so "Gray 1.59%" stays on one line. */
final class EntryWrap {
    private EntryWrap() {}

    /** The index of the first entry on each line. An entry wider than the line gets a line to itself. */
    static List<Integer> lineStarts(int[] widths, int gap, int maxWidth) {
        List<Integer> starts = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < widths.length; i++) {
            if (i == 0 || used + gap + widths[i] > maxWidth) {
                starts.add(i);
                used = widths[i];
            } else {
                used += gap + widths[i];
            }
        }
        return starts;
    }
}
