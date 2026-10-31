package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.util.AnimalCategory;
import com.egakh.familytree.util.DeathMessages;
import com.egakh.familytree.util.VariantText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

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
    private static final String STATS_SEPARATOR = " \u00B7 ";
    private static final int EDGE_COLOR = 0xFF7C8DA8;
    /** Cards and links on the path between two compared animals. */
    public static final int RELATION_COLOR = 0xFF7CE0B4;

    private TreeRenderer() {}

    public static void drawNode(GuiGraphicsExtractor gfx, Font font, TreeLayout.Node node,
                                long currentWorldDay, long currentEpochMillis, boolean isFocus, boolean collapsed,
                                int generation, Function<UUID, AnimalRecord> lookup,
                                double panX, double panY, double zoom) {
        drawNode(gfx, font, node, currentWorldDay, currentEpochMillis, isFocus, false, collapsed, generation, lookup, panX, panY, zoom);
    }

    public static void drawNode(GuiGraphicsExtractor gfx, Font font, TreeLayout.Node node,
                                long currentWorldDay, long currentEpochMillis, boolean isFocus, boolean related, boolean collapsed,
                                int generation, Function<UUID, AnimalRecord> lookup,
                                double panX, double panY, double zoom) {
        int x = (int) Math.round(node.x * zoom + panX);
        int y = (int) Math.round(node.y * zoom + panY);
        int w = TreeViewport.nodeWidth(zoom);
        int h = TreeViewport.nodeHeight(zoom);
        AnimalRecord r = node.record;

        int bg = r.deceased() ? BG_DECEASED : BG_ALIVE;
        int border = isFocus ? BORDER_FOCUS : related ? RELATION_COLOR : (r.deceased() ? BORDER_DECEASED : BORDER_ALIVE);

        // The background is opaque, so two fills draw the bordered card that four borders would.
        gfx.fill(x, y, x + w, y + h, border);
        int inset = related && w > 6 && h > 6 ? 2 : 1;
        if (w > 2 && h > 2) gfx.fill(x + inset, y + inset, x + w - inset, y + h - inset, bg);

        int textColor = r.deceased() ? TEXT_DECEASED : TEXT_PRIMARY;
        boolean hasFace = PetFaceRenderer.hasFace(r);
        if (zoom < 0.65) {
            drawCompactNode(gfx, font, r, x, y, w, h, hasFace, collapsed, textColor);
            return;
        }
        gfx.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        if (collapsed) {
            gfx.text(font, "+", x + w - 10, y + 3, TEXT_GENERATION);
        }
        int basePad = Math.max(8, (int) Math.round(10 * zoom));
        int iconSize = hasFace ? Math.max(28, (int) Math.round(36 * zoom)) : 0;
        int iconPadding = hasFace ? Math.max(6, (int) Math.round(8 * zoom)) : 0;
        int left = x + basePad + (hasFace ? iconSize + iconPadding : 0);
        int lineHeight = Math.max(font.lineHeight + 2, (int) Math.round((font.lineHeight + 2) * zoom));
        int textWidth = Math.max(24, w - basePad * 2 - (hasFace ? iconSize + iconPadding : 0));

        if (hasFace) {
            int iconX = x + basePad;
            int iconY = y + Math.max(8, (int) Math.round(9 * zoom));
            PetFaceRenderer.drawFace(gfx, font, r, iconX, iconY, iconSize);
        }

        int cursorY = y + Math.max(6, (int) Math.round(8 * zoom));
        cursorY = drawWrapped(gfx, font, r.name(), left, cursorY, textWidth, lineHeight, textColor);

        if (FamilyTreeClientSettings.showGeneration() && generation > 0) {
            String genLine = Component.translatable("familytree.node.generation", generation).getString();
            gfx.text(font, Component.literal(trimToWidth(font, genLine, textWidth)),
                    left, cursorY, r.deceased() ? TEXT_GENERATION_DECEASED : TEXT_GENERATION);
            cursorY += lineHeight;
        }

        if (FamilyTreeClientSettings.showHorseStats() && HorseStatsText.has(r)) {
            cursorY = drawStats(gfx, font, HorseStatsText.card(r, lookup), left, cursorY, textWidth, lineHeight,
                    r.deceased() ? TEXT_DECEASED : TEXT_STATS);
        }

        Component variant = VariantText.label(r);
        if (variant != null) {
            drawVariant(gfx, font, variant, VariantText.rare(r), left, cursorY, textWidth,
                    r.deceased() ? TEXT_DECEASED : TEXT_STATS);
            cursorY += lineHeight;
        }

        String tamedBy = buildTamedByLine(r);
        if (!tamedBy.isEmpty()) {
            cursorY = drawWrapped(gfx, font, tamedBy, left, cursorY, textWidth, lineHeight, TEXT_SECONDARY);
        }

        long worldAge;
        if (r.deceased() && r.deathWorldDay() != null && r.deathEpochMillis() != null) {
            worldAge = r.deathWorldDay() - r.birthWorldDay();
        } else {
            worldAge = currentWorldDay - r.birthWorldDay();
        }

        String dayLine = buildAgeSummary(r.birthWorldDay(), worldAge);
        if (!dayLine.isEmpty()) {
            gfx.text(font, Component.literal(trimToWidth(font, dayLine, textWidth)), left, cursorY, TEXT_SECONDARY);
            cursorY += lineHeight;
        }

        if (r.deceased()) {
            gfx.text(font, Component.literal(trimToWidth(font, buildDeathLine(r), textWidth)),
                    left, cursorY, 0xFFB94A4A);
        }
        gfx.disableScissor();
    }

    private static void drawCompactNode(GuiGraphicsExtractor gfx, Font font, AnimalRecord record,
                                        int x, int y, int w, int h, boolean hasFace,
                                        boolean collapsed, int textColor) {
        // At overview scales the cards remain visible, but text would be smaller than a pixel.
        if (w < 24 || h < font.lineHeight + 4) return;
        gfx.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        int left = x + 4;
        if (hasFace && w >= 64) {
            int size = Math.min(h - 8, Math.min(32, w / 3));
            PetFaceRenderer.drawFace(gfx, font, record, left, y + (h - size) / 2, size);
            left += size + 4;
        }
        int rightPadding = collapsed ? 14 : 4;
        gfx.text(font, trimToWidth(font, record.name(), Math.max(1, x + w - rightPadding - left)),
                left, y + (h - font.lineHeight) / 2, textColor);
        if (collapsed) gfx.text(font, "+", x + w - 10, y + (h - font.lineHeight) / 2, TEXT_GENERATION);
        gfx.disableScissor();
    }

    static void drawVariant(GuiGraphicsExtractor gfx, Font font, Component variant, boolean rare,
                            int left, int top, int maxWidth, int color) {
        String marker = rare ? " " + VariantText.RARE_MARKER : "";
        String text = trimToWidth(font, variant.getString(), Math.max(1, maxWidth - font.width(marker)));
        gfx.text(font, text, left, top, color);
        if (rare) gfx.text(font, marker, left + font.width(text), top, VariantText.RARE_COLOR);
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

    private static String buildDeathLine(AnimalRecord r) {
        Component line = r.deathWorldDay() == null ? null : DeathMessages.line(r);
        return line == null ? Component.translatable("familytree.node.deceased").getString() : line.getString();
    }

    private static int drawWrapped(GuiGraphicsExtractor gfx, Font font, String text,
                                   int left, int top, int maxWidth, int lineHeight, int color) {
        int y = top;
        for (FormattedCharSequence line : font.split(Component.literal(text), maxWidth)) {
            gfx.text(font, line, left, y, color);
            y += lineHeight;
        }
        return y;
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

    private static String buildAgeSummary(long birthDay, long worldAge) {
        boolean showBirthDay = FamilyTreeClientSettings.showBirthDay();
        boolean showAge = FamilyTreeClientSettings.showAge();
        if (showBirthDay && showAge) {
            return Component.translatable("familytree.screen.age_summary", birthDay, worldAge).getString();
        }
        if (showBirthDay) {
            return Component.translatable("familytree.node.born_world", birthDay).getString();
        }
        if (showAge) {
            return Component.translatable("familytree.screen.age_days", worldAge).getString();
        }
        return "";
    }

    static String buildTamedByLine(AnimalRecord record) {
        if (record.ownerName() == null || record.ownerName().isBlank()) {
            return "";
        }
        boolean pet = PetFaceRenderer.category(record.speciesId()) == AnimalCategory.PETS;
        return Component.translatable(pet ? "familytree.owner.tamed_by" : "familytree.owner.owner", record.ownerName()).getString();
    }
}
