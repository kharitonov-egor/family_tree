package com.egakh.familytree.client.export;

import com.egakh.familytree.client.screen.HorseStatsText;
import com.egakh.familytree.client.screen.TreeLayout;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.util.HorseStatUnits.Trend;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import javax.imageio.ImageIO;

/** Draws a fixed-scale image, independent of the screen's zoom and dimensions. */
public final class TreeImageExporter {
    public static final int SCALE = 2;
    private static final int MARGIN = 32;
    private static final int HEADER = 80;
    private static final long MAX_PIXELS = 24_000_000;
    public record Card(double x, double y, String name, String detail, String status,
                       boolean deceased, BufferedImage portrait, List<HorseStatsText.Segment> stats) {}
    public record Line(double x1, double y1, double x2, double y2) {}
    public record Picture(String title, String footer, int width, int height, List<Card> cards, List<Line> lines,
                          Legend legend) {}
    /** Explains the stat markers in shared images. Drawn only when a card has a marker. */
    public record Legend(String higher, String lower) {}

    private TreeImageExporter() {}

    public static Picture prepare(TreeLayout.Result tree, String title, String footer,
                                   Function<AnimalRecord, String> details,
                                   Function<AnimalRecord, String> status,
                                   Function<AnimalRecord, BufferedImage> portraits,
                                   Function<AnimalRecord, List<HorseStatsText.Segment>> stats,
                                   Legend legend) {
        int width = (int) Math.ceil(Math.max(440, tree.maxX - tree.minX + MARGIN * 2)) * SCALE;
        int height = (int) Math.ceil(tree.maxY - tree.minY + HEADER + MARGIN * 2) * SCALE;
        if (tree.nodes.isEmpty() || width <= 0 || height <= 0 || width > 16384 || height > 16384
                || (long) width * height > MAX_PIXELS) throw new IllegalArgumentException("Tree is too large to export");
        List<Card> cards = tree.nodes.stream().map(node -> new Card(
                node.x - tree.minX + MARGIN, node.y - tree.minY + HEADER,
                node.record.name(), details.apply(node.record), status.apply(node.record),
                node.record.deceased(), portraits.apply(node.record), List.copyOf(stats.apply(node.record)))).toList();
        List<Line> lines = tree.edges.stream().map(edge -> new Line(
                edge.from.x - tree.minX + MARGIN + TreeLayout.NODE_WIDTH / 2.0,
                edge.from.y - tree.minY + HEADER + TreeLayout.NODE_HEIGHT,
                edge.to.x - tree.minX + MARGIN + TreeLayout.NODE_WIDTH / 2.0,
                edge.to.y - tree.minY + HEADER)).toList();
        boolean marked = cards.stream().flatMap(card -> card.stats().stream())
                .anyMatch(stat -> stat.trend() == Trend.BEATS_BOTH || stat.trend() == Trend.TRAILS_BOTH);
        return new Picture(title, footer, width, height, cards, lines, marked ? legend : null);
    }

    public static BufferedImage render(Picture picture) {
        BufferedImage image = new BufferedImage(picture.width(), picture.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(SCALE, SCALE);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x101820));
            g.fillRect(0, 0, picture.width(), picture.height());
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            g.setColor(new Color(0xF3E9D8));
            g.drawString(fit(g, picture.title(), picture.width() / SCALE - MARGIN * 2), MARGIN, 42);
            if (picture.legend() != null) drawLegend(g, picture.legend(), picture.width() / SCALE - MARGIN * 2);
            g.setStroke(new BasicStroke(2));
            g.setColor(new Color(0x758D96));
            for (Line line : picture.lines()) {
                int middle = (int) ((line.y1() + line.y2()) / 2);
                g.drawLine((int) line.x1(), (int) line.y1(), (int) line.x1(), middle);
                g.drawLine((int) line.x1(), middle, (int) line.x2(), middle);
                g.drawLine((int) line.x2(), middle, (int) line.x2(), (int) line.y2());
            }
            for (Card card : picture.cards()) drawCard(g, card);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.setColor(new Color(0xA9BAC0));
            g.drawString(fit(g, picture.footer(), picture.width() / SCALE - MARGIN * 2),
                    MARGIN, picture.height() / SCALE - 18);
        } finally {
            g.dispose();
        }
        return image;
    }

    public static Path save(Picture picture, Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, "family-tree-", ".png");
        try {
            if (!ImageIO.write(render(picture), "PNG", file.toFile())) throw new IOException("PNG writer unavailable");
            return file;
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(file);
            throw failure;
        }
    }

    private static void drawCard(Graphics2D g, Card card) {
        int x = (int) Math.round(card.x()), y = (int) Math.round(card.y());
        g.setColor(new Color(card.deceased() ? 0x252A30 : 0x233640));
        g.fillRoundRect(x, y, TreeLayout.NODE_WIDTH, TreeLayout.NODE_HEIGHT, 12, 12);
        g.setColor(new Color(card.deceased() ? 0x9F8F87 : 0xD8B671));
        g.drawRoundRect(x, y, TreeLayout.NODE_WIDTH, TreeLayout.NODE_HEIGHT, 12, 12);
        int textX = x + 16;
        if (card.portrait() != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(card.portrait(), x + 14, y + 18, 48, 48, null);
            textX = x + 74;
        }
        int available = x + TreeLayout.NODE_WIDTH - 14 - textX;
        boolean stats = !card.stats().isEmpty();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.setColor(new Color(0xF3E9D8));
        g.drawString(fit(g, card.name(), available), textX, y + (stats ? 28 : 30));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(new Color(0xCBD6D9));
        g.drawString(fit(g, card.detail(), available), textX, y + (stats ? 47 : 53));
        if (stats) drawStats(g, card, textX, y + 65, available);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(new Color(card.deceased() ? 0xD8B6AC : 0xB5D4B7));
        g.drawString(fit(g, card.status(), TreeLayout.NODE_WIDTH - 32), x + 16, y + (stats ? 99 : 87));
    }

    /** Wraps whole segments onto at most two rows. Triangles avoid relying on font symbol coverage. */
    private static void drawStats(Graphics2D g, Card card, int left, int baseline, int available) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        FontMetrics metrics = g.getFontMetrics();
        int separator = metrics.stringWidth("  ");
        int x = left, row = 0;
        for (HorseStatsText.Segment segment : card.stats()) {
            String text = displayable(g.getFont(), segment.text());
            boolean marked = segment.trend() == Trend.BEATS_BOTH || segment.trend() == Trend.TRAILS_BOTH;
            int width = metrics.stringWidth(text) + (marked ? 11 : 0);
            if (x > left && x + separator + width > left + available) {
                if (++row == 2) return;
                x = left;
            }
            if (x > left) x += separator;
            int y = baseline + row * 15;
            g.setColor(new Color(card.deceased() ? 0xB9B2AA : 0xE6EEF2));
            g.drawString(fit(g, text, left + available - x), x, y);
            x += metrics.stringWidth(text);
            if (marked) {
                boolean up = segment.trend() == Trend.BEATS_BOTH;
                g.setColor(new Color(up ? 0x6FD26F : 0xE86B6B));
                int top = y - 9, bottom = y - 1, middle = x + 7;
                g.fillPolygon(up ? new int[]{x + 3, middle, x + 11} : new int[]{x + 3, x + 11, middle},
                        up ? new int[]{bottom, top, bottom} : new int[]{top, top, bottom}, 3);
                x += 11;
            }
        }
    }

    private static void drawLegend(Graphics2D g, Legend legend, int available) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int x = MARGIN, y = 64;
        for (boolean up : new boolean[]{true, false}) {
            String text = up ? legend.higher() : legend.lower();
            g.setColor(new Color(up ? 0x6FD26F : 0xE86B6B));
            g.fillPolygon(up ? new int[]{x, x + 4, x + 8} : new int[]{x, x + 8, x + 4},
                    up ? new int[]{y - 1, y - 9, y - 1} : new int[]{y - 9, y - 9, y - 1}, 3);
            g.setColor(new Color(0xA9BAC0));
            g.drawString(fit(g, text, Math.max(1, MARGIN + available - x - 12)), x + 12, y);
            x += 12 + g.getFontMetrics().stringWidth(text) + 18;
        }
    }

    private static String displayable(Font font, String text) {
        return font.canDisplayUpTo(text) == -1 ? text : text.replace("\u2665", "hearts");
    }

    private static String fit(Graphics2D g, String text, int width) {
        if (g.getFontMetrics().stringWidth(text) <= width) return text;
        int end = text.length();
        while (end > 0 && g.getFontMetrics().stringWidth(text.substring(0, end) + "...") > width)
            end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + "...";
    }
}
