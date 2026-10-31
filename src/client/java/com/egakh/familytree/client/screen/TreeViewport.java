package com.egakh.familytree.client.screen;

/** Camera coordinates in GUI pixels. Cards, edges and hit testing use the same transform. */
final class TreeViewport {
    /** Fit stops here, where cards still show a name, and centers on one card instead of shrinking a large family to a line. */
    static final double MIN_FIT_ZOOM = 0.25;
    /** Scrolling out stops where cards are still distinct boxes. */
    static final double MIN_ZOOM = 0.1;
    static final double MAX_ZOOM = 2.75;
    double panX;
    double panY;
    double zoom = 1.0;

    /**
     * Fits the tree inside the canvas. When it is too large to fit at {@link #MIN_FIT_ZOOM}, centers on {@code anchor}, or
     * puts the first card of the top row in the top left corner without one, and returns false.
     */
    boolean fit(TreeLayout.Result tree, TreeLayout.Node anchor, int left, int right, int top, int bottom) {
        if (tree.nodes.isEmpty()) return true;
        int padding = Math.max(4, Math.min(16, Math.min(right - left, bottom - top) / 12));
        double availableWidth = Math.max(1, right - left - padding * 2);
        double availableHeight = Math.max(1, bottom - top - padding * 2);
        double whole = Math.min(availableWidth / Math.max(1, tree.maxX - tree.minX),
                availableHeight / Math.max(1, tree.maxY - tree.minY));
        zoom = Math.min(1.25, Math.max(MIN_FIT_ZOOM, whole));
        if (whole >= MIN_FIT_ZOOM) {
            center((tree.minX + tree.maxX) / 2, (tree.minY + tree.maxY) / 2, left, right, top, bottom);
            return true;
        }
        if (anchor != null) {
            center(anchor.x + TreeLayout.NODE_WIDTH / 2.0, anchor.y + TreeLayout.NODE_HEIGHT / 2.0, left, right, top, bottom);
            return false;
        }
        TreeLayout.Node first = tree.nodes.get(0);
        for (TreeLayout.Node node : tree.nodes) {
            if (node.y < first.y || (node.y == first.y && node.x < first.x)) first = node;
        }
        panX = left + padding - first.x * zoom;
        panY = top + padding - first.y * zoom;
        return false;
    }

    void center(double worldX, double worldY, int left, int right, int top, int bottom) {
        panX = (left + right) / 2.0 - worldX * zoom;
        panY = (top + bottom) / 2.0 - worldY * zoom;
    }

    void zoomAt(double x, double y, double amount) {
        double previousZoom = zoom;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * Math.pow(1.1, amount)));
        double scale = zoom / previousZoom;
        panX = x - (x - panX) * scale;
        panY = y - (y - panY) * scale;
    }

    /** Moves the view so a card that moved in a new layout stays where it was on screen. */
    void follow(TreeLayout.Node before, TreeLayout.Node after) {
        panX += (before.x - after.x) * zoom;
        panY += (before.y - after.y) * zoom;
    }

    static int nodeWidth(double zoom) {
        return Math.max(1, (int) Math.round(TreeLayout.NODE_WIDTH * zoom));
    }

    static int nodeHeight(double zoom) {
        return Math.max(1, (int) Math.round(TreeLayout.NODE_HEIGHT * zoom));
    }
}
