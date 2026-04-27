package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.data.AnimalRecord;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class TreeRenderer {
    private static final Identifier DEFAULT_CAT_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/cat/cat_tabby.png");
    private static final int CAT_TEXTURE_WIDTH = 64;
    private static final int CAT_TEXTURE_HEIGHT = 32;
    private static final float CAT_FACE_U = 5.0f;
    private static final float CAT_FACE_V = 4.0f;
    private static final int CAT_FACE_WIDTH = 6;
    private static final int CAT_FACE_HEIGHT = 6;

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
        boolean isCat = isCat(r);
        int iconSize = isCat ? Math.max(28, (int) Math.round(36 * zoom)) : 0;
        int iconPadding = isCat ? Math.max(6, (int) Math.round(8 * zoom)) : 0;
        int left = x + Math.max(8, (int) Math.round(10 * zoom)) + (isCat ? iconSize + iconPadding : 0);
        int lineHeight = Math.max(font.lineHeight + 2, (int) Math.round((font.lineHeight + 2) * zoom));
        int line1 = y + Math.max(6, (int) Math.round(8 * zoom));
        int line2 = line1 + lineHeight;
        int line3 = line2 + lineHeight;
        int textWidth = Math.max(24, w - 20);

        if (isCat) {
            int iconX = x + Math.max(8, (int) Math.round(10 * zoom));
            int iconY = y + Math.max(8, (int) Math.round(9 * zoom));
            drawCatFace(gfx, catTexture(r), iconX, iconY, iconSize);
            textWidth = Math.max(24, w - 20 - iconSize - iconPadding);
        }

        gfx.text(font, Component.literal(trimToWidth(font, r.name(), textWidth)), left, line1, textColor);
        gfx.text(font, Component.literal(trimToWidth(font, shortSpecies(r.speciesId()), textWidth)),
                left, line2, TEXT_SECONDARY);

        int nextLine = line3;
        String tamedBy = buildTamedByLine(r);
        if (!tamedBy.isEmpty()) {
            gfx.text(font, Component.literal(trimToWidth(font, tamedBy, textWidth)), left, nextLine, TEXT_SECONDARY);
            nextLine += lineHeight;
        }

        long worldAge;
        if (r.deceased() && r.deathWorldDay() != null && r.deathEpochMillis() != null) {
            worldAge = r.deathWorldDay() - r.birthWorldDay();
        } else {
            worldAge = currentWorldDay - r.birthWorldDay();
        }

        String dayLine = buildAgeSummary(r.birthWorldDay(), worldAge);
        if (!dayLine.isEmpty()) {
            gfx.text(font, Component.literal(trimToWidth(font, dayLine, textWidth)), left, nextLine, TEXT_SECONDARY);
            nextLine += lineHeight;
        }

        if (r.deceased()) {
            gfx.text(font, Component.translatable("familytree.node.deceased"), left, nextLine, 0xFFB94A4A);
        }
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

    private static boolean isCat(AnimalRecord record) {
        return "minecraft:cat".equals(record.speciesId());
    }

    private static Identifier catTexture(AnimalRecord record) {
        if (record.textureId() != null && !record.textureId().isBlank()) {
            Identifier texture = normalizeCatTexture(record.textureId());
            if (texture != null) {
                return texture;
            }
        }
        return DEFAULT_CAT_TEXTURE;
    }

    private static Identifier normalizeCatTexture(String rawId) {
        Identifier id = Identifier.tryParse(rawId);
        if (id == null) {
            return null;
        }
        if (id.getPath().startsWith("textures/")) {
            return id;
        }
        return id.withPrefix("textures/").withSuffix(".png");
    }

    private static void drawCatFace(GuiGraphicsExtractor gfx, Identifier texture, int x, int y, int size) {
        gfx.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, CAT_FACE_U, CAT_FACE_V,
                size, size, CAT_FACE_WIDTH, CAT_FACE_HEIGHT, CAT_TEXTURE_WIDTH, CAT_TEXTURE_HEIGHT);
    }

    private static String buildTamedByLine(AnimalRecord record) {
        if (record.ownerName() == null || record.ownerName().isBlank()) {
            return "";
        }
        return Component.translatable("familytree.owner.tamed_by", record.ownerName()).getString();
    }
}
