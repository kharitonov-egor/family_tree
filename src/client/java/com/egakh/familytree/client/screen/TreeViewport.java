package com.egakh.familytree.client.screen;

/** Camera coordinates in GUI pixels. Cards, edges and hit testing use the same transform. */
final class TreeViewport {
    double panX;
    double panY;
    double zoom = 1.0;
    private double minZoom = 0.2;

    void fit(TreeLayout.Result tree, int width, int top, int bottom) {
        if (tree.nodes.isEmpty()) return;
        double availableWidth = Math.max(1, width - 32);
        double availableHeight = Math.max(1, bottom - top - 32);
        zoom = Math.min(1.25, Math.min(availableWidth / Math.max(1, tree.maxX - tree.minX),
                availableHeight / Math.max(1, tree.maxY - tree.minY)));
        minZoom = Math.min(0.2, zoom);
        center((tree.minX + tree.maxX) / 2, (tree.minY + tree.maxY) / 2, width, top, bottom);
    }

    void center(double worldX, double worldY, int width, int top, int bottom) {
        panX = width / 2.0 - worldX * zoom;
        panY = (top + bottom) / 2.0 - worldY * zoom;
    }

    void zoomAt(double x, double y, double amount) {
        double previousZoom = zoom;
        zoom = Math.max(minZoom, Math.min(2.75, zoom * Math.pow(1.1, amount)));
        double scale = zoom / previousZoom;
        panX = x - (x - panX) * scale;
        panY = y - (y - panY) * scale;
    }

    static int nodeWidth(double zoom) {
        return Math.max(1, (int) Math.round(TreeLayout.NODE_WIDTH * zoom));
    }

    static int nodeHeight(double zoom) {
        return Math.max(1, (int) Math.round(TreeLayout.NODE_HEIGHT * zoom));
    }
}
