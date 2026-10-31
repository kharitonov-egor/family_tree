package com.egakh.familytree.client.screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Places the tree screen's tools in rows under the title, or in a column at the right when rows would take more than a
 * quarter of a short window, such as 854x480 at GUI scale 2.
 */
final class TreeToolbar {
    static final int TOP = 30;
    static final int ROW_HEIGHT = 18;
    static final int GAP = 4;
    static final int MARGIN = 8;

    record Slot(int x, int y, int width) {}

    record Layout(List<Slot> slots, boolean side, int canvasRight, int canvasTop, int canvasBottom) {
        /** Short windows keep one footer line for hints instead of two. */
        boolean compactFooter() { return side; }
    }

    private TreeToolbar() {}

    static Layout arrange(int[] widths, int screenWidth, int screenHeight) {
        List<Slot> rows = flow(widths, MARGIN, Math.max(1, screenWidth - MARGIN * 2), false);
        int rowsBottom = bottom(rows);
        int widest = 0;
        for (int width : widths) widest = Math.max(widest, width);
        int columnWidth = Math.max(widest, Math.min(150, screenWidth / 3));
        int columnLeft = screenWidth - MARGIN - columnWidth;
        List<Slot> column = flow(widths, columnLeft, columnWidth, true);
        int shortFooter = 16;
        boolean side = rowsBottom + 6 > screenHeight / 4
                && bottom(column) <= screenHeight - shortFooter
                && columnLeft - MARGIN >= 200;
        if (side) return new Layout(column, true, columnLeft - MARGIN, TOP, screenHeight - shortFooter);
        return new Layout(rows, false, screenWidth, rowsBottom + 6, screenHeight - 32);
    }

    private static int bottom(List<Slot> slots) {
        int bottom = TOP;
        for (Slot slot : slots) bottom = Math.max(bottom, slot.y() + ROW_HEIGHT);
        return bottom;
    }

    /** Greedy rows, centered, or stretched to the full width when {@code fill} is set. Each slot keeps its index. */
    private static List<Slot> flow(int[] widths, int left, int available, boolean fill) {
        Slot[] slots = new Slot[widths.length];
        int y = TOP, start = 0;
        while (start < widths.length) {
            int end = start, used = 0;
            while (end < widths.length) {
                int next = used + (end > start ? GAP : 0) + Math.min(widths[end], available);
                if (end > start && next > available) break;
                used = next;
                end++;
            }
            int extra = fill ? available - used : 0, x = left + (fill ? 0 : (available - used) / 2);
            for (int i = start; i < end; i++) {
                int share = fill ? extra / (end - start) + (i == end - 1 ? extra % (end - start) : 0) : 0;
                int width = Math.min(widths[i], available) + share;
                slots[i] = new Slot(x, y, width);
                x += width + GAP;
            }
            y += ROW_HEIGHT + GAP;
            start = end;
        }
        return new ArrayList<>(List.of(slots));
    }
}
