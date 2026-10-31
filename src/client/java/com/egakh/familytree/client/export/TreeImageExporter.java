package com.egakh.familytree.client.export;

import com.egakh.familytree.client.screen.HorseStatsText;
import com.egakh.familytree.client.screen.TreeLayout;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.util.HorseStatUnits.Trend;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.imageio.ImageIO;

/** Draws a fixed-scale image, independent of the screen's zoom and dimensions. */
public final class TreeImageExporter {
    public static final int SCALE = 2;
    private static final int MARGIN = 32;
    private static final int HEADER = 80;
    private static final long MAX_PIXELS = 24_000_000;
    private static final Font FOOTER_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final int FOOTER_LINE = 16;
    /** {@code missingParent} marks a parent link whose record isn't in the history the tree was drawn from. */
    public record Card(double x, double y, String name, String detail, String status,
                       boolean deceased, BufferedImage portrait, List<HorseStatsText.Segment> stats, boolean rare,
                       boolean missingParent) {}
    /** Corner points as x, y pairs. */
    public record Line(double[] points) {}
    /** A triangle and count above a card with older generations left out, or below one with younger generations left out. */
    public record Marker(double x, double y, boolean older, int generations) {}
    public record Picture(String title, String footer, int width, int height, List<Card> cards, List<Line> lines,
                          List<Marker> markers, Legend legend) {}
    /** Legend text and the word that replaces ♥ when the export font has no heart. Markers are drawn only when cards use them. */
    public record Legend(String higher, String lower, String hearts, String rare, boolean statMarkers, boolean rareMarkers) {
        public Legend(String higher, String lower, String hearts, String rare) { this(higher, lower, hearts, rare, true, true); }
    }

    private TreeImageExporter() {}

    /** Throws before any portraits are captured or pixels allocated. */
    public static void requireExportable(TreeLayout.Result tree) {
        imageSize(tree);
    }

    private static int[] imageSize(TreeLayout.Result tree) {
        int width = (int) Math.ceil(Math.max(440, tree.maxX - tree.minX + MARGIN * 2)) * SCALE;
        int height = (int) Math.ceil(tree.maxY - tree.minY + header(tree) + MARGIN * 2) * SCALE;
        if (tree.nodes.isEmpty() || width <= 0 || height <= 0 || width > 16384 || height > 16384
                || (long) width * height > MAX_PIXELS) throw new IllegalArgumentException("Tree is too large to export");
        return new int[]{width, height};
    }

    /** Room above the top cards for the title, the legend, and markers for older generations. */
    private static int header(TreeLayout.Result tree) {
        return tree.olderHidden.isEmpty() ? HEADER : HEADER + 24;
    }

    public static Picture prepare(TreeLayout.Result tree, String title, String footer,
                                   Function<AnimalRecord, String> details,
                                   Function<AnimalRecord, String> status,
                                   Function<AnimalRecord, BufferedImage> portraits,
                                   Function<AnimalRecord, List<HorseStatsText.Segment>> stats,
                                   Predicate<AnimalRecord> rare,
                                   Legend legend) {
        return prepare(tree, title, footer, details, status, portraits, stats, rare, legend, pet -> false);
    }

    public static Picture prepare(TreeLayout.Result tree, String title, String footer,
                                   Function<AnimalRecord, String> details,
                                   Function<AnimalRecord, String> status,
                                   Function<AnimalRecord, BufferedImage> portraits,
                                   Function<AnimalRecord, List<HorseStatsText.Segment>> stats,
                                   Predicate<AnimalRecord> rare,
                                   Legend legend,
                                   Predicate<AnimalRecord> missingParent) {
        int[] size = imageSize(tree);
        int width = size[0], header = header(tree);
        int height = size[1] + (footerLines(footer, width / SCALE - MARGIN * 2).size() - 1) * FOOTER_LINE * SCALE;
        List<Card> cards = tree.nodes.stream().map(node -> new Card(
                node.x - tree.minX + MARGIN, node.y - tree.minY + header,
                node.record.name(), details.apply(node.record), status.apply(node.record),
                node.record.deceased(), portraits.apply(node.record), List.copyOf(stats.apply(node.record)),
                rare.test(node.record), missingParent.test(node.record))).toList();
        List<Line> lines = tree.edges.stream().map(edge -> {
            double[] points = edge.route().clone();
            for (int i = 0; i < points.length; i += 2) {
                points[i] += MARGIN - tree.minX;
                points[i + 1] += header - tree.minY;
            }
            return new Line(points);
        }).toList();
        List<Marker> markers = new ArrayList<>();
        for (TreeLayout.Node node : tree.nodes) {
            double centre = node.x - tree.minX + MARGIN + TreeLayout.NODE_WIDTH / 2.0, top = node.y - tree.minY + header;
            Integer older = tree.olderHidden.get(node.id), younger = tree.youngerHidden.get(node.id);
            if (older != null) markers.add(new Marker(centre, top, true, older));
            if (younger != null) markers.add(new Marker(centre, top + TreeLayout.NODE_HEIGHT, false, younger));
        }
        boolean marked = cards.stream().flatMap(card -> card.stats().stream())
                .anyMatch(stat -> stat.trend() == Trend.BEATS_BOTH || stat.trend() == Trend.TRAILS_BOTH);
        boolean rareCards = cards.stream().anyMatch(Card::rare);
        Legend shown = legend == null ? null
                : new Legend(legend.higher(), legend.lower(), legend.hearts(), legend.rare(), marked, rareCards);
        return new Picture(title, footer, width, height, cards, lines, List.copyOf(markers), shown);
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
            if (picture.legend() != null && (picture.legend().statMarkers() || picture.legend().rareMarkers()))
                drawLegend(g, picture.legend(), picture.width() / SCALE - MARGIN * 2);
            g.setStroke(new BasicStroke(2));
            g.setColor(new Color(0x758D96));
            for (Line line : picture.lines()) {
                double[] points = line.points();
                for (int i = 2; i + 1 < points.length; i += 2)
                    g.drawLine((int) points[i - 2], (int) points[i - 1], (int) points[i], (int) points[i + 1]);
            }
            for (Card card : picture.cards()) if (card.missingParent()) drawMissingParent(g, card);
            String hearts = picture.legend() == null ? "hearts" : picture.legend().hearts();
            for (Card card : picture.cards()) drawCard(g, card, hearts);
            for (Marker marker : picture.markers()) drawMarker(g, marker);
            g.setFont(FOOTER_FONT);
            g.setColor(new Color(0xA9BAC0));
            int footerWidth = picture.width() / SCALE - MARGIN * 2;
            List<String> footer = footerLines(picture.footer(), footerWidth);
            int baseline = picture.height() / SCALE - 18 - (footer.size() - 1) * FOOTER_LINE;
            for (String line : footer) {
                g.drawString(fit(g, line, footerWidth), MARGIN, baseline);
                baseline += FOOTER_LINE;
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /** Saves as family-tree-{@code subject}-date.png, adding -2, -3, and so on when that name is taken. */
    public static Path save(Picture picture, Path directory, String subject) throws IOException {
        Files.createDirectories(directory);
        LocalDate today = LocalDate.now();
        Path file = null;
        for (int copy = 1; file == null; copy++) {
            try {
                file = Files.createFile(directory.resolve(fileName(subject, today, copy)));
            } catch (FileAlreadyExistsException taken) {
                if (copy >= 10_000) throw taken;
            }
        }
        try {
            if (!ImageIO.write(render(picture), "PNG", file.toFile())) throw new IOException("PNG writer unavailable");
            return file;
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(file);
            throw failure;
        }
    }

    /** Letters and digits of any script stay; everything else, including path separators, becomes a hyphen. */
    public static String fileName(String subject, LocalDate date, int copy) {
        StringBuilder slug = new StringBuilder();
        subject.codePoints().limit(48).forEach(point -> {
            if (Character.isLetterOrDigit(point)) slug.appendCodePoint(point);
            else if (slug.length() > 0 && slug.charAt(slug.length() - 1) != '-') slug.append('-');
        });
        while (slug.length() > 0 && slug.charAt(slug.length() - 1) == '-') slug.setLength(slug.length() - 1);
        return "family-tree-" + (slug.length() == 0 ? "" : slug + "-") + date + (copy > 1 ? "-" + copy : "") + ".png";
    }

    private static void drawMarker(Graphics2D g, Marker marker) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        String text = "+" + marker.generations();
        int width = 14 + g.getFontMetrics().stringWidth(text);
        int x = (int) Math.round(marker.x() - width / 2.0);
        int y = (int) Math.round(marker.older() ? marker.y() - 20 : marker.y() + 4);
        g.setColor(new Color(0x101820));
        g.fillRoundRect(x - 4, y, width + 8, 16, 6, 6);
        g.setColor(new Color(0xD8B671));
        g.drawRoundRect(x - 4, y, width + 8, 16, 6, 6);
        int middle = x + 4;
        g.fillPolygon(marker.older() ? new int[]{middle - 4, middle, middle + 4} : new int[]{middle - 4, middle + 4, middle},
                marker.older() ? new int[]{y + 11, y + 4, y + 11} : new int[]{y + 4, y + 4, y + 11}, 3);
        g.drawString(text, x + 12, y + 12);
    }

    /** Wraps the footer at spaces and line breaks. Each line after the first makes the picture taller. */
    private static List<String> footerLines(String footer, int width) {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        try {
            g.setFont(FOOTER_FONT);
            FontMetrics metrics = g.getFontMetrics();
            List<String> lines = new ArrayList<>();
            for (String paragraph : footer.split("\n")) {
                String line = "";
                for (String word : paragraph.split(" ")) {
                    String longer = line.isEmpty() ? word : line + " " + word;
                    if (!line.isEmpty() && metrics.stringWidth(longer) > width) {
                        lines.add(line);
                        line = word;
                    } else line = longer;
                }
                lines.add(line);
            }
            return lines;
        } finally {
            g.dispose();
        }
    }

    private static void drawMissingParent(Graphics2D g, Card card) {
        int x = (int) Math.round(card.x() + TreeLayout.NODE_WIDTH * 0.75), top = (int) Math.round(card.y());
        int length = (int) Math.round(TreeLayout.V_SPACING * 0.3);
        java.awt.Stroke solid = g.getStroke();
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{3, 3}, 0));
        g.drawLine(x, top - length, x, top);
        g.setStroke(solid);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString("?", x - g.getFontMetrics().stringWidth("?") / 2, top - length - 3);
    }

    private static void drawCard(Graphics2D g, Card card, String hearts) {
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
        String name = fit(g, card.name(), card.rare() ? available - 16 : available);
        int nameBaseline = y + (stats ? 28 : 30);
        g.drawString(name, textX, nameBaseline);
        if (card.rare()) drawStar(g, textX + g.getFontMetrics().stringWidth(name) + 9, nameBaseline - 6, 6);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(new Color(0xCBD6D9));
        g.drawString(fit(g, card.detail(), available), textX, y + (stats ? 47 : 53));
        if (stats) drawStats(g, card, textX, y + 65, available, hearts);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(new Color(card.deceased() ? 0xD8B6AC : 0xB5D4B7));
        g.drawString(fit(g, card.status(), TreeLayout.NODE_WIDTH - 32), x + 16, y + (stats ? 99 : 87));
    }

    /** Wraps whole segments onto at most two rows. Triangles avoid relying on font symbol coverage. */
    private static void drawStats(Graphics2D g, Card card, int left, int baseline, int available, String hearts) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        FontMetrics metrics = g.getFontMetrics();
        int separator = metrics.stringWidth("  ");
        int x = left, row = 0;
        for (HorseStatsText.Segment segment : card.stats()) {
            String text = displayable(g.getFont(), segment.text(), hearts);
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
        if (legend.statMarkers()) for (boolean up : new boolean[]{true, false}) {
            String text = up ? legend.higher() : legend.lower();
            g.setColor(new Color(up ? 0x6FD26F : 0xE86B6B));
            g.fillPolygon(up ? new int[]{x, x + 4, x + 8} : new int[]{x, x + 8, x + 4},
                    up ? new int[]{y - 1, y - 9, y - 1} : new int[]{y - 9, y - 9, y - 1}, 3);
            g.setColor(new Color(0xA9BAC0));
            g.drawString(fit(g, text, Math.max(1, MARGIN + available - x - 12)), x + 12, y);
            x += 12 + g.getFontMetrics().stringWidth(text) + 18;
        }
        if (legend.rareMarkers() && legend.rare() != null) {
            drawStar(g, x + 4, y - 5, 5);
            g.setColor(new Color(0xA9BAC0));
            g.drawString(fit(g, legend.rare(), Math.max(1, MARGIN + available - x - 12)), x + 12, y);
        }
    }

    /** Fonts may lack a star glyph, so the rare marker is drawn as a shape. */
    private static void drawStar(Graphics2D g, int centerX, int centerY, int radius) {
        int[] xs = new int[10], ys = new int[10];
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            double r = i % 2 == 0 ? radius : radius * 0.45;
            xs[i] = (int) Math.round(centerX + Math.cos(angle) * r);
            ys[i] = (int) Math.round(centerY + Math.sin(angle) * r);
        }
        g.setColor(new Color(0xFFC94A));
        g.fillPolygon(xs, ys, 10);
    }

    private static String displayable(Font font, String text, String hearts) {
        return font.canDisplayUpTo(text) == -1 ? text : text.replace("\u2665", hearts);
    }

    private static String fit(Graphics2D g, String text, int width) {
        if (g.getFontMetrics().stringWidth(text) <= width) return text;
        int end = text.length();
        while (end > 0 && g.getFontMetrics().stringWidth(text.substring(0, end) + "...") > width)
            end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + "...";
    }
}
