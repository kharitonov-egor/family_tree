package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.data.AnimalRecord;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

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
        int w = Math.max(110, (int) Math.round(TreeLayout.NODE_WIDTH * zoom));
        int h = Math.max(64, (int) Math.round(TreeLayout.NODE_HEIGHT * zoom));
        AnimalRecord r = node.record;

        int bg = r.deceased() ? BG_DECEASED : BG_ALIVE;
        int border = isFocus ? BORDER_FOCUS : (r.deceased() ? BORDER_DECEASED : BORDER_ALIVE);

        gfx.fill(x, y, x + w, y + h, bg);
        gfx.fill(x, y, x + w, y + 1, border);
        gfx.fill(x, y + h - 1, x + w, y + h, border);
        gfx.fill(x, y, x + 1, y + h, border);
        gfx.fill(x + w - 1, y, x + w, y + h, border);

        int textColor = r.deceased() ? TEXT_DECEASED : TEXT_PRIMARY;
        boolean hasFace = PetFaceRenderer.hasFace(r);
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
        gfx.text(font, Component.literal(trimToWidth(font, shortSpecies(r.speciesId()), textWidth)),
                left, cursorY, TEXT_SECONDARY);
        cursorY += lineHeight;

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
            gfx.text(font, Component.translatable("familytree.node.deceased"), left, cursorY, 0xFFB94A4A);
        }
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

    public static void drawEdge(GuiGraphicsExtractor gfx, TreeLayout.Node parent, TreeLayout.Node child,
                                double panX, double panY, double zoom) {
        int scaledNodeWidth = Math.max(110, (int) Math.round(TreeLayout.NODE_WIDTH * zoom));
        int scaledNodeHeight = Math.max(50, (int) Math.round(TreeLayout.NODE_HEIGHT * zoom));
        int px = (int) Math.round(parent.x * zoom + panX + scaledNodeWidth / 2.0);
        int py = (int) Math.round(parent.y * zoom + panY + scaledNodeHeight);
        int cx = (int) Math.round(child.x * zoom + panX + scaledNodeWidth / 2.0);
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

    private static String trimToWidth(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static String buildAgeSummary(long birthDay, long worldAge) {
        boolean showBirthDay = FamilyTreeClientSettings.showBirthDay();
        boolean showAge = FamilyTreeClientSettings.showAge();
        if (showBirthDay && showAge) {
            return "Day " + birthDay + " | " + worldAge + "d";
        }
        if (showBirthDay) {
            return "Day " + birthDay;
        }
        if (showAge) {
            return worldAge + "d";
        }
        return "";
    }

    private static String buildTamedByLine(AnimalRecord record) {
        if (record.ownerName() == null || record.ownerName().isBlank()) {
            return "";
        }
        return Component.translatable("familytree.owner.tamed_by", record.ownerName()).getString();
    }
}
