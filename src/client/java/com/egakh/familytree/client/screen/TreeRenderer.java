package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.util.VariantText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class TreeRenderer {
    private static final int BG_ALIVE = 0xFF202830;
    private static final int BG_DECEASED = 0xFF1A1A1A;
    private static final int BORDER_ALIVE = 0xFF6FB3FF;
    private static final int BORDER_FOCUS = 0xFFFFD24A;
    private static final int BORDER_DECEASED = 0xFF7A7A7A;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFB0B7C0;
    private static final int TEXT_DECEASED = 0xFF888888;
    private static final int TEXT_GENERATION = 0xFFD9B25A;
    private static final int TEXT_GENERATION_DECEASED = 0xFF8A7A4A;
    private static final int TEXT_STATS = 0xFFDCE6F0;
    private static final int TEXT_DEATH = 0xFFB94A4A;
    private static final String STATS_SEPARATOR = " \u00B7 ";
    private static final int EDGE_COLOR = 0xFF7C8DA8;
    /** Cards and links on the path between two compared animals. */
    public static final int RELATION_COLOR = 0xFF7CE0B4;

    private TreeRenderer() {}

    /**
     * Draws a card at the viewport's zoom, scaling its text with it. When the zoom would draw a font pixel smaller
     * than a screen pixel, the text stays at {@code minTextScale} and the card shows only the rows that still fit.
     * Returns whether any of the card's text was shortened or left out.
     */
    public static boolean drawNode(GuiGraphicsExtractor gfx, Font font, TreeLayout.Node node, TreeCard card, int cardHeight,
                                boolean isFocus, boolean related, boolean collapsed,
                                double panX, double panY, double zoom, double minTextScale) {
        int x = (int) Math.round(node.x * zoom + panX);
        int y = (int) Math.round(node.y * zoom + panY);
        int w = scaled(TreeLayout.NODE_WIDTH, zoom);
        int h = scaled(cardHeight, zoom);
        AnimalRecord r = node.record;

        int bg = r.deceased() ? BG_DECEASED : BG_ALIVE;
        int border = isFocus ? BORDER_FOCUS : related ? RELATION_COLOR : (r.deceased() ? BORDER_DECEASED : BORDER_ALIVE);

        // The background is opaque, so two fills draw the bordered card that four borders would.
        gfx.fill(x, y, x + w, y + h, border);
        int inset = related && w > 6 && h > 6 ? 2 : 1;
        if (w > 2 && h > 2) gfx.fill(x + inset, y + inset, x + w - inset, y + h - inset, bg);
        if (w < 8 || h < 6) return true;

        TreeCard.Measure measure = TreeCard.measure(font);
        boolean cut;
        gfx.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        if (zoom >= minTextScale) {
            cut = card.full.cut();
            if (card.face) {
                PetFaceRenderer.drawFace(gfx, font, r, x + scaled(TreeCard.PAD_X, zoom), y + scaled(TreeCard.FACE_TOP, zoom),
                        scaled(TreeCard.FACE_SIZE, zoom));
            }
            pushScale(gfx, x, y, (float) zoom);
            drawRows(gfx, font, measure, card.full.rows(), TreeCard.textLeft(card.face), TreeCard.PAD_TOP,
                    TreeCard.textWidth(card.face), r.deceased());
            if (collapsed) gfx.text(font, "+", TreeLayout.NODE_WIDTH - 10, 3, TEXT_GENERATION);
            popScale(gfx);
        } else {
            cut = drawCompact(gfx, font, measure, card, r, x, y, w, h, collapsed, minTextScale);
        }
        gfx.disableScissor();
        return cut;
    }

    /** At overview zooms the text keeps a readable size, so it shows the first rows that fit, from the name down. */
    private static boolean drawCompact(GuiGraphicsExtractor gfx, Font font, TreeCard.Measure measure, TreeCard card,
                                       AnimalRecord record, int x, int y, int w, int h, boolean collapsed, double scale) {
        double step = TreeCard.lineStep(measure) * scale;
        int maxRows = (int) Math.floor((h - 4 + 2 * scale) / step);
        if (maxRows < 1 || w < 24) return true;
        int left = x + 4;
        if (card.face && w >= 64) {
            int size = Math.min(h - 8, Math.min(32, w / 3));
            if (size >= 8) {
                PetFaceRenderer.drawFace(gfx, font, record, left, y + (h - size) / 2, size);
                left += size + 4;
            }
        }
        int right = x + w - (collapsed ? 14 : 4);
        int width = (int) Math.floor((right - left) / scale);
        if (width < 8) return true;
        TreeCard.Fitted fitted = card.compact(measure, width, maxRows);
        int rows = fitted.rows().size();
        double blockHeight = rows * step - 2 * scale;
        pushScale(gfx, left, (float) (y + (h - blockHeight) / 2.0), (float) scale);
        drawRows(gfx, font, measure, fitted.rows(), 0, 0, width, record.deceased());
        popScale(gfx);
        if (collapsed) gfx.text(font, "+", x + w - 10, y + (h - font.lineHeight) / 2, TEXT_GENERATION);
        return fitted.cut();
    }

    private static void drawRows(GuiGraphicsExtractor gfx, Font font, TreeCard.Measure measure, List<TreeCard.Row> rows,
                                 int left, int top, int width, boolean deceased) {
        int step = TreeCard.lineStep(measure);
        int y = top;
        for (TreeCard.Row row : rows) {
            switch (row.kind()) {
                case STATS -> drawStats(gfx, font, row.segments(), left, y, width, step, deceased ? TEXT_DECEASED : TEXT_STATS);
                case VARIANT -> drawVariant(gfx, font, row.text(), row.rare(), left, y, deceased ? TEXT_DECEASED : TEXT_STATS);
                default -> gfx.text(font, ordered(row.text()), left, y, color(row.kind(), deceased));
            }
            y += step;
        }
    }

    private static int color(TreeCard.Kind kind, boolean deceased) {
        return switch (kind) {
            case NAME -> deceased ? TEXT_DECEASED : TEXT_PRIMARY;
            case GENERATION -> deceased ? TEXT_GENERATION_DECEASED : TEXT_GENERATION;
            case DEATH -> TEXT_DEATH;
            default -> TEXT_SECONDARY;
        };
    }

    static int scaled(int size, double zoom) {
        return Math.max(1, (int) Math.round(size * zoom));
    }

    static void pushScale(GuiGraphicsExtractor gfx, float x, float y, float scale) {
        //? if >=1.21.6 {
        gfx.pose().pushMatrix();
        gfx.pose().translate(x, y);
        gfx.pose().scale(scale, scale);
        //?} else {
        /*gfx.pose().pushPose();
        gfx.pose().translate(x, y, 0);
        gfx.pose().scale(scale, scale, 1);
        *///?}
    }

    static void popScale(GuiGraphicsExtractor gfx) {
        //? if >=1.21.6 {
        gfx.pose().popMatrix();
        //?} else {
        /*gfx.pose().popPose();
        *///?}
    }

    /** Before 1.21.6, a string drawn directly skips bidi ordering unless the game's language reads right to left. */
    static FormattedCharSequence ordered(String text) {
        return Language.getInstance().getVisualOrder(FormattedText.of(text));
    }

    private static void drawVariant(GuiGraphicsExtractor gfx, Font font, String text, boolean rare, int left, int top, int color) {
        gfx.text(font, ordered(text), left, top, color);
        if (rare) gfx.text(font, " " + VariantText.RARE_MARKER, left + font.width(text), top, VariantText.RARE_COLOR);
    }

    static int statsWidth(Font font, List<HorseStatsText.Segment> segments) {
        int width = 0;
        for (HorseStatsText.Segment segment : segments) {
            String marker = HorseStatsText.marker(segment.trend());
            width += (width == 0 ? 0 : font.width(STATS_SEPARATOR)) + font.width(segment.text())
                    + (marker.isEmpty() ? 0 : font.width(" " + marker));
        }
        return width;
    }

    /** Lays out stat segments left to right and wraps whole segments to the next line. */
    static int drawStats(GuiGraphicsExtractor gfx, Font font, List<HorseStatsText.Segment> segments,
                         int left, int top, int maxWidth, int lineHeight, int color) {
        String separator = STATS_SEPARATOR;
        int x = left;
        int y = top;
        boolean lineStarted = false;
        for (HorseStatsText.Segment segment : segments) {
            String marker = HorseStatsText.marker(segment.trend());
            int width = font.width(segment.text()) + (marker.isEmpty() ? 0 : font.width(" " + marker));
            if (lineStarted && x + font.width(separator) + width > left + maxWidth) {
                x = left;
                y += lineHeight;
                lineStarted = false;
            }
            if (lineStarted) {
                gfx.text(font, separator, x, y, color);
                x += font.width(separator);
            }
            gfx.text(font, trimToWidth(font, segment.text(), Math.max(1, left + maxWidth - x)), x, y, color);
            x += font.width(segment.text());
            if (!marker.isEmpty()) {
                x += font.width(" ");
                gfx.text(font, marker, x, y, HorseStatsText.markerColor(segment.trend()));
                x += font.width(marker);
            }
            lineStarted = true;
        }
        return lineStarted ? y + lineHeight : y;
    }

    public static void drawEdge(GuiGraphicsExtractor gfx, TreeLayout.Edge edge, double panX, double panY, double zoom) {
        drawEdge(gfx, edge, panX, panY, zoom, EDGE_COLOR, 1);
    }

    public static void drawEdge(GuiGraphicsExtractor gfx, TreeLayout.Edge edge,
                                double panX, double panY, double zoom, int color, int thickness) {
        double[] route = edge.route();
        int count = route.length / 2;
        int[] xs = new int[count], ys = new int[count];
        for (int i = 0; i < count; i++) {
            xs[i] = (int) Math.round(route[i * 2] * zoom + panX);
            ys[i] = (int) Math.round(route[i * 2 + 1] * zoom + panY);
        }
        // Both ends reach a pixel into the cards, which are drawn on top, so rounding never leaves a gap.
        if (count > 1) {
            ys[0] += ys[1] >= ys[0] ? -1 : 1;
            ys[count - 1] += ys[count - 2] <= ys[count - 1] ? 1 : -1;
        }
        for (int i = 1; i < count; i++) {
            if (ys[i] == ys[i - 1]) drawHLine(gfx, Math.min(xs[i], xs[i - 1]), Math.max(xs[i], xs[i - 1]), ys[i], color, thickness);
            else drawVLine(gfx, xs[i], ys[i - 1], ys[i], color, thickness);
        }
    }

    /** A dashed stub and a question mark above the card, where a parent that isn't in the history would connect. */
    public static void drawMissingParent(GuiGraphicsExtractor gfx, Font font, TreeLayout.Node child,
                                         double panX, double panY, double zoom) {
        int x = (int) Math.round(child.x * zoom + panX + TreeViewport.nodeWidth(zoom) * 0.75);
        int top = (int) Math.round(child.y * zoom + panY);
        int length = Math.max(6, (int) Math.round(TreeLayout.V_SPACING * 0.3 * zoom));
        int thickness = zoom >= 0.65 ? 2 : 1;
        for (int y = top - length; y < top; y += 4) gfx.fill(x, y, x + thickness, Math.min(top, y + 2), EDGE_COLOR);
        if (zoom >= 0.65) gfx.text(font, "?", x - font.width("?") / 2 + 1, top - length - font.lineHeight - 1, EDGE_COLOR);
    }

    private static void drawHLine(GuiGraphicsExtractor gfx, int x1, int x2, int y, int color, int thickness) {
        gfx.fill(x1, y, x2 + thickness, y + thickness, color);
    }

    private static void drawVLine(GuiGraphicsExtractor gfx, int x, int y1, int y2, int color, int thickness) {
        int a = Math.min(y1, y2);
        int b = Math.max(y1, y2);
        gfx.fill(x, a, x + thickness, b + 1, color);
    }

    private static String trimToWidth(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    static String buildTamedByLine(AnimalRecord record) {
        return TreeCard.ownerLine(record);
    }
}
