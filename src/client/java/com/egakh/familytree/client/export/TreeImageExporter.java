package com.egakh.familytree.client.export;

import com.egakh.familytree.client.screen.TreeLayout;
import com.egakh.familytree.data.AnimalRecord;
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
                       boolean deceased, BufferedImage portrait) {}
    public record Line(double x1, double y1, double x2, double y2) {}
    public record Picture(String title, String footer, int width, int height, List<Card> cards, List<Line> lines) {}

    private TreeImageExporter() {}

    public static Picture prepare(TreeLayout.Result tree, String title, String footer,
                                   Function<AnimalRecord, String> details,
                                   Function<AnimalRecord, String> status,
                                   Function<AnimalRecord, BufferedImage> portraits) {
        int width = (int) Math.ceil(Math.max(440, tree.maxX - tree.minX + MARGIN * 2)) * SCALE;
        int height = (int) Math.ceil(tree.maxY - tree.minY + HEADER + MARGIN * 2) * SCALE;
        if (tree.nodes.isEmpty() || width <= 0 || height <= 0 || width > 16384 || height > 16384
                || (long) width * height > MAX_PIXELS) throw new IllegalArgumentException("Tree is too large to export");
        List<Card> cards = tree.nodes.stream().map(node -> new Card(
                node.x - tree.minX + MARGIN, node.y - tree.minY + HEADER,
                node.record.name(), details.apply(node.record), status.apply(node.record),
                node.record.deceased(), portraits.apply(node.record))).toList();
        List<Line> lines = tree.edges.stream().map(edge -> new Line(
                edge.from.x - tree.minX + MARGIN + TreeLayout.NODE_WIDTH / 2.0,
                edge.from.y - tree.minY + HEADER + TreeLayout.NODE_HEIGHT,
                edge.to.x - tree.minX + MARGIN + TreeLayout.NODE_WIDTH / 2.0,
                edge.to.y - tree.minY + HEADER)).toList();
        return new Picture(title, footer, width, height, cards, lines);
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
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.setColor(new Color(0xF3E9D8));
        g.drawString(fit(g, card.name(), available), textX, y + 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(new Color(0xCBD6D9));
        g.drawString(fit(g, card.detail(), available), textX, y + 53);
        g.setColor(new Color(card.deceased() ? 0xD8B6AC : 0xB5D4B7));
        g.drawString(fit(g, card.status(), TreeLayout.NODE_WIDTH - 32), x + 16, y + 87);
    }

    private static String fit(Graphics2D g, String text, int width) {
        if (g.getFontMetrics().stringWidth(text) <= width) return text;
        int end = text.length();
        while (end > 0 && g.getFontMetrics().stringWidth(text.substring(0, end) + "...") > width)
            end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + "...";
    }
}
