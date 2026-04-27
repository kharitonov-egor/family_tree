package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.util.TimeUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class TreeRenderer {

    private static final int BG_ALIVE = 0xFF202830;
    private static final int BG_DECEASED = 0xFF1A1A1A;
    private static final int BORDER_ALIVE = 0xFF6FB3FF;
    private static final int BORDER_FOCUS = 0xFFFFD24A;
    private static final int BORDER_DECEASED = 0xFF7A7A7A;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFB0B7C0;
    private static final int TEXT_DECEASED = 0xFF888888;
    private static final int EDGE_COLOR = 0xFF7C8DA8;

    private TreeRenderer() {}

    public static void drawNode(GuiGraphicsExtractor gfx, Font font, TreeLayout.Node node,
                                long currentWorldDay, long currentEpochMillis, boolean isFocus,
                                double panX, double panY, double zoom) {
        int x = (int) Math.round(node.x * zoom + panX);
        int y = (int) Math.round(node.y * zoom + panY);
        int w = Math.max(32, (int) Math.round(TreeLayout.NODE_WIDTH * zoom));
        int h = Math.max(24, (int) Math.round(TreeLayout.NODE_HEIGHT * zoom));
        AnimalRecord r = node.record;

        int bg = r.deceased() ? BG_DECEASED : BG_ALIVE;
        int border = isFocus ? BORDER_FOCUS : (r.deceased() ? BORDER_DECEASED : BORDER_ALIVE);

        gfx.fill(x, y, x + w, y + h, bg);
        gfx.fill(x, y, x + w, y + 1, border);
        gfx.fill(x, y + h - 1, x + w, y + h, border);
        gfx.fill(x, y, x + 1, y + h, border);
        gfx.fill(x + w - 1, y, x + w, y + h, border);

        int textColor = r.deceased() ? TEXT_DECEASED : TEXT_PRIMARY;
        int line1 = y + 5;
        int line2 = y + Math.max(14, (int) Math.round(16 * zoom));
        int line3 = y + Math.max(23, (int) Math.round(28 * zoom));
        int line4 = y + Math.max(32, (int) Math.round(39 * zoom));
        int line5 = y + Math.max(41, (int) Math.round(50 * zoom));

        gfx.text(font, Component.literal(r.name()), x + 6, line1, textColor);
        gfx.text(font, Component.literal(shortSpecies(r.speciesId())), x + 6, line2, TEXT_SECONDARY);

        long worldAge;
        long realAge;
        if (r.deceased() && r.deathWorldDay() != null && r.deathEpochMillis() != null) {
            worldAge = r.deathWorldDay() - r.birthWorldDay();
            realAge = TimeUtil.realDaysBetween(r.birthEpochMillis(), r.deathEpochMillis());
        } else {
            worldAge = currentWorldDay - r.birthWorldDay();
            realAge = TimeUtil.realDaysBetween(r.birthEpochMillis(), currentEpochMillis);
        }

        String dayLine = "Day " + r.birthWorldDay() + " | " + worldAge + "d (W)";
        gfx.text(font, Component.literal(dayLine), x + 6, line3, TEXT_SECONDARY);

        String realLine = TimeUtil.formatRealDate(r.birthEpochMillis()) + " | " + realAge + "d (R)";
        gfx.text(font, Component.literal(realLine), x + 6, line4, TEXT_SECONDARY);

        if (r.deceased()) {
            gfx.text(font, Component.translatable("familytree.node.deceased"),
                    x + 6, line5, 0xFFB94A4A);
        }
    }

    public static void drawEdge(GuiGraphicsExtractor gfx, TreeLayout.Node parent, TreeLayout.Node child,
                                double panX, double panY, double zoom) {
        int px = (int) Math.round((parent.x + TreeLayout.NODE_WIDTH / 2.0) * zoom + panX);
        int py = (int) Math.round((parent.y + TreeLayout.NODE_HEIGHT) * zoom + panY);
        int cx = (int) Math.round((child.x + TreeLayout.NODE_WIDTH / 2.0) * zoom + panX);
        int cy = (int) Math.round(child.y * zoom + panY);

        int midY = (py + cy) / 2;
        drawHLine(gfx, Math.min(px, cx), Math.max(px, cx), midY);
        drawVLine(gfx, px, py, midY);
        drawVLine(gfx, cx, midY, cy);
    }

    private static void drawHLine(GuiGraphicsExtractor gfx, int x1, int x2, int y) {
        gfx.fill(x1, y, x2 + 1, y + 1, EDGE_COLOR);
    }

    private static void drawVLine(GuiGraphicsExtractor gfx, int x, int y1, int y2) {
        int a = Math.min(y1, y2);
        int b = Math.max(y1, y2);
        gfx.fill(x, a, x + 1, b + 1, EDGE_COLOR);
    }

    private static String shortSpecies(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
