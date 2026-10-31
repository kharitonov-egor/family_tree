package com.egakh.familytree.client.screen;

import java.util.List;

/** Picks the canvas corner for the details panel that covers the least of the cards it describes. */
final class PanelPlacement {
    record Box(int left, int top, int right, int bottom) {
        int overlap(Box other) {
            int width = Math.min(right, other.right) - Math.max(left, other.left);
            int height = Math.min(bottom, other.bottom) - Math.max(top, other.top);
            return width <= 0 || height <= 0 ? 0 : width * height;
        }
    }

    private PanelPlacement() {}

    /** Bottom left is preferred, then bottom right, top left, and top right. */
    static Box place(Box canvas, int width, int height, int margin, List<Box> keepVisible) {
        int left = canvas.left() + margin;
        int right = Math.max(left, canvas.right() - margin - width);
        int bottom = canvas.bottom() - margin;
        int top = canvas.top() + margin;
        Box best = null;
        int bestOverlap = Integer.MAX_VALUE;
        for (Box candidate : List.of(
                new Box(left, Math.max(top, bottom - height), left + width, bottom),
                new Box(right, Math.max(top, bottom - height), right + width, bottom),
                new Box(left, top, left + width, Math.min(bottom, top + height)),
                new Box(right, top, right + width, Math.min(bottom, top + height)))) {
            int overlap = 0;
            for (Box card : keepVisible) overlap += candidate.overlap(card);
            if (overlap < bestOverlap) {
                best = candidate;
                bestOverlap = overlap;
            }
            if (overlap == 0) break;
        }
        return best;
    }
}
