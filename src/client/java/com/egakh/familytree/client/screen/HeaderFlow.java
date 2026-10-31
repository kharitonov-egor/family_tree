package com.egakh.familytree.client.screen;

import java.util.ArrayList;
import java.util.List;

/** Places header controls in rows from left to right. A group stays on one row whenever it fits on one. */
final class HeaderFlow {
    static final int GAP = 4, GROUP_GAP = 8, ROW = 22;

    record Item(String key, int width, int group, boolean flex, boolean newRow) {
        static Item fixed(String key, int width, int group) { return new Item(key, width, group, false, false); }
        static Item flex(String key, int minWidth, int group, boolean newRow) { return new Item(key, minWidth, group, true, newRow); }
    }

    record Slot(String key, int x, int y, int width) {}

    record Result(List<Slot> slots, int rows) {
        Slot find(String key) {
            for (Slot slot : slots) if (slot.key().equals(key)) return slot;
            return null;
        }
        int bottom(int top) { return top + rows * ROW; }
    }

    private HeaderFlow() {}

    static Result place(List<Item> items, int left, int right, int top) {
        int available = Math.max(1, right - left);
        List<List<Item>> rows = new ArrayList<>();
        List<List<Integer>> gaps = new ArrayList<>();
        List<Item> row = new ArrayList<>();
        List<Integer> rowGaps = new ArrayList<>();
        int used = 0;
        for (int start = 0; start < items.size(); ) {
            int end = start;
            while (end < items.size() && items.get(end).group() == items.get(start).group()) end++;
            int groupWidth = -GAP;
            for (int i = start; i < end; i++) groupWidth += Math.min(available, items.get(i).width()) + GAP;
            boolean breakBefore = items.get(start).newRow() || used + GROUP_GAP + groupWidth > available;
            if (!row.isEmpty() && breakBefore) {
                rows.add(row); gaps.add(rowGaps);
                row = new ArrayList<>(); rowGaps = new ArrayList<>(); used = 0;
            }
            for (int i = start; i < end; i++) {
                Item item = items.get(i);
                int width = Math.min(available, item.width());
                int gap = row.isEmpty() ? 0 : i == start ? GROUP_GAP : GAP;
                if (!row.isEmpty() && used + gap + width > available) {
                    rows.add(row); gaps.add(rowGaps);
                    row = new ArrayList<>(); rowGaps = new ArrayList<>(); used = 0; gap = 0;
                }
                row.add(item); rowGaps.add(gap); used += gap + width;
            }
            start = end;
        }
        if (!row.isEmpty()) { rows.add(row); gaps.add(rowGaps); }

        List<Slot> slots = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            List<Item> current = rows.get(r);
            int width = 0;
            for (int i = 0; i < current.size(); i++) width += gaps.get(r).get(i) + Math.min(available, current.get(i).width());
            int extra = Math.max(0, available - width);
            int x = left, y = top + r * ROW;
            for (int i = 0; i < current.size(); i++) {
                Item item = current.get(i);
                int itemWidth = Math.min(available, item.width());
                if (item.flex() && extra > 0) { itemWidth += extra; extra = 0; }
                x += gaps.get(r).get(i);
                slots.add(new Slot(item.key(), x, y, itemWidth));
                x += itemWidth;
            }
        }
        return new Result(slots, rows.size());
    }
}
