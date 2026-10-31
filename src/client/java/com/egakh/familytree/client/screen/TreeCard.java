package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.util.AnimalCategory;
import com.egakh.familytree.util.DeathMessages;
import com.egakh.familytree.util.PetNames;
import com.egakh.familytree.util.VariantText;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** A tree card's text, laid out at zoom 1 in card units. The screen scales the whole card, text included. */
final class TreeCard {
    enum Kind { NAME, GENERATION, STATS, VARIANT, OWNER, AGE, DEATH }

    /** One piece of content at full length. */
    record Source(Kind kind, String text, List<HorseStatsText.Segment> segments, boolean rare) {
        static Source text(Kind kind, String text) {
            return new Source(kind, text, List.of(), false);
        }

        String fullText() {
            if (kind != Kind.STATS) return rare ? text + " " + VariantText.RARE_MARKER : text;
            StringBuilder out = new StringBuilder();
            for (HorseStatsText.Segment segment : segments) {
                if (!out.isEmpty()) out.append(SEPARATOR);
                out.append(segment.text());
                String marker = HorseStatsText.marker(segment.trend());
                if (!marker.isEmpty()) out.append(' ').append(marker);
            }
            return out.toString();
        }
    }

    /** One drawn line. Stats rows carry their segments; every other row carries text. */
    record Row(Kind kind, String text, List<HorseStatsText.Segment> segments, boolean rare) {}

    /** The rows that fit, and whether any content was shortened or left out. */
    record Fitted(List<Row> rows, boolean cut) {}

    interface Measure {
        int width(String text);
        List<String> wrap(String text, int width);
        String prefix(String text, int width);
        int lineHeight();
    }

    static final int PAD_X = 10;
    static final int PAD_TOP = 8;
    static final int PAD_BOTTOM = 6;
    static final int FACE_SIZE = 36;
    static final int FACE_TOP = 9;
    static final int FACE_GAP = 8;
    static final int NAME_LINES = 2;
    static final int STATS_LINES = 2;
    static final int DEATH_LINES = 3;
    static final String ELLIPSIS = "...";
    static final String SEPARATOR = " · ";

    final List<Source> sources;
    final boolean face;
    final Fitted full;

    TreeCard(List<Source> sources, boolean face, Measure measure) {
        this.sources = List.copyOf(sources);
        this.face = face;
        this.full = fit(measure, textWidth(face), NAME_LINES, STATS_LINES, DEATH_LINES, Integer.MAX_VALUE);
    }

    static int textLeft(boolean face) {
        return PAD_X + (face ? FACE_SIZE + FACE_GAP : 0);
    }

    static int textWidth(boolean face) {
        return TreeLayout.NODE_WIDTH - textLeft(face) - PAD_X;
    }

    static int lineStep(Measure measure) {
        return measure.lineHeight() + 2;
    }

    /** The height that shows every row, and the portrait when there is one. */
    int height(Measure measure) {
        int text = PAD_TOP + full.rows().size() * lineStep(measure) + PAD_BOTTOM;
        return Math.max(text, face ? FACE_TOP * 2 + FACE_SIZE : 0);
    }

    /** Cards in one tree share a height, so rows of cards line up and links meet their edges. */
    static int treeHeight(Iterable<TreeCard> cards, Measure measure) {
        int height = PAD_TOP + 2 * lineStep(measure) + PAD_BOTTOM;
        for (TreeCard card : cards) height = Math.max(height, card.height(measure));
        return height;
    }

    /** Rows for a narrower text area, as when the screen keeps text readable on a zoomed-out card. */
    Fitted compact(Measure measure, int width, int maxRows) {
        return fit(measure, width, 1, 1, 1, maxRows);
    }

    /** The full text of every row, for the hover tooltip of a card whose content was cut. */
    List<String> fullLines() {
        List<String> lines = new ArrayList<>(sources.size());
        for (Source source : sources) lines.add(source.fullText());
        return lines;
    }

    Fitted fit(Measure measure, int width, int nameLines, int statsLines, int deathLines, int maxRows) {
        List<Row> rows = new ArrayList<>();
        boolean cut = false;
        for (Source source : sources) {
            if (rows.size() >= maxRows) return new Fitted(rows, true);
            int room = maxRows - rows.size();
            switch (source.kind()) {
                case NAME -> cut |= wrapped(rows, source, measure, width, Math.min(room, nameLines));
                case DEATH -> cut |= wrapped(rows, source, measure, width, Math.min(room, deathLines));
                case STATS -> {
                    List<List<HorseStatsText.Segment>> lines = wrapSegments(measure, source.segments(), width);
                    int shown = Math.min(lines.size(), Math.min(room, statsLines));
                    for (int i = 0; i < shown; i++) rows.add(new Row(Kind.STATS, null, lines.get(i), false));
                    cut |= shown < lines.size() || statsWidth(measure, lines.get(0)) > width;
                }
                case VARIANT -> {
                    int marker = source.rare() ? measure.width(" " + VariantText.RARE_MARKER) : 0;
                    String text = ellipsize(measure, source.text(), Math.max(1, width - marker));
                    rows.add(new Row(Kind.VARIANT, text, List.of(), source.rare()));
                    cut |= !text.equals(source.text());
                }
                default -> {
                    String text = ellipsize(measure, source.text(), width);
                    rows.add(new Row(source.kind(), text, List.of(), false));
                    cut |= !text.equals(source.text());
                }
            }
        }
        return new Fitted(rows, cut);
    }

    /** Adds up to {@code limit} wrapped lines and reports whether the text had to be shortened. */
    private static boolean wrapped(List<Row> rows, Source source, Measure measure, int width, int limit) {
        List<String> lines = measure.wrap(source.text(), width);
        if (lines.isEmpty()) lines = List.of("");
        int shown = Math.min(lines.size(), limit);
        boolean cut = shown < lines.size();
        for (int i = 0; i < shown; i++) {
            String line = lines.get(i);
            String fitted = i == shown - 1 && cut ? withEllipsis(measure, line, width) : ellipsize(measure, line, width);
            cut |= !fitted.equals(line);
            rows.add(new Row(source.kind(), fitted, List.of(), false));
        }
        return cut;
    }

    static String ellipsize(Measure measure, String text, int width) {
        return measure.width(text) <= width ? text : withEllipsis(measure, text, width);
    }

    private static String withEllipsis(Measure measure, String text, int width) {
        String body = text.stripTrailing();
        if (measure.width(body + ELLIPSIS) <= width) return body + ELLIPSIS;
        return BidiLines.balance(measure.prefix(body, Math.max(0, width - measure.width(ELLIPSIS))).stripTrailing()) + ELLIPSIS;
    }

    /** Whole segments per line, so a value never splits from its unit. */
    static List<List<HorseStatsText.Segment>> wrapSegments(Measure measure, List<HorseStatsText.Segment> segments, int width) {
        List<List<HorseStatsText.Segment>> lines = new ArrayList<>();
        List<HorseStatsText.Segment> line = new ArrayList<>();
        int used = 0;
        for (HorseStatsText.Segment segment : segments) {
            int segmentWidth = segmentWidth(measure, segment);
            if (!line.isEmpty() && used + measure.width(SEPARATOR) + segmentWidth > width) {
                lines.add(line);
                line = new ArrayList<>();
                used = 0;
            }
            used += (line.isEmpty() ? 0 : measure.width(SEPARATOR)) + segmentWidth;
            line.add(segment);
        }
        if (!line.isEmpty()) lines.add(line);
        if (lines.isEmpty()) lines.add(List.of());
        return lines;
    }

    static int statsWidth(Measure measure, List<HorseStatsText.Segment> segments) {
        int width = 0;
        for (HorseStatsText.Segment segment : segments)
            width += (width == 0 ? 0 : measure.width(SEPARATOR)) + segmentWidth(measure, segment);
        return width;
    }

    private static int segmentWidth(Measure measure, HorseStatsText.Segment segment) {
        String marker = HorseStatsText.marker(segment.trend());
        return measure.width(segment.text()) + (marker.isEmpty() ? 0 : measure.width(" " + marker));
    }

    static Measure measure(Font font) {
        return new Measure() {
            @Override public int width(String text) { return font.width(text); }
            @Override public List<String> wrap(String text, int width) {
                return BidiLines.wrap(text, plain -> BidiLines.lines(font, plain, width));
            }
            @Override public String prefix(String text, int width) { return font.plainSubstrByWidth(text, width); }
            @Override public int lineHeight() { return font.lineHeight; }
        };
    }

    /** Everything the card shows for one animal, in the order it is drawn. */
    static List<Source> sources(AnimalRecord record, int generation, long currentWorldDay,
                                Function<UUID, AnimalRecord> lookup) {
        List<Source> sources = new ArrayList<>();
        sources.add(Source.text(Kind.NAME, record.name()));
        if (FamilyTreeClientSettings.showGeneration() && generation > 0)
            sources.add(Source.text(Kind.GENERATION, Component.translatable("familytree.node.generation", generation).getString()));
        if (FamilyTreeClientSettings.showHorseStats() && HorseStatsText.has(record)) {
            List<HorseStatsText.Segment> stats = HorseStatsText.card(record, lookup);
            if (!stats.isEmpty()) sources.add(new Source(Kind.STATS, null, List.copyOf(stats), false));
        }
        Component variant = VariantText.label(record);
        if (variant != null) sources.add(new Source(Kind.VARIANT, variant.getString(), List.of(), VariantText.rare(record)));
        String owner = ownerLine(record);
        if (!owner.isEmpty()) sources.add(Source.text(Kind.OWNER, owner));
        long age = record.deceased() && record.deathWorldDay() != null
                ? record.deathWorldDay() - record.birthWorldDay()
                : currentWorldDay - record.birthWorldDay();
        String ageLine = ageLine(record.birthWorldDay(), age);
        if (!ageLine.isEmpty()) sources.add(Source.text(Kind.AGE, ageLine));
        if (record.deceased()) sources.add(Source.text(Kind.DEATH, deathLine(record)));
        return sources;
    }

    static String ownerLine(AnimalRecord record) {
        if (record.ownerName() == null || record.ownerName().isBlank()) return "";
        boolean pet = PetFaceRenderer.category(record.speciesId()) == AnimalCategory.PETS;
        return Component.translatable(pet ? "familytree.owner.tamed_by" : "familytree.owner.owner",
                PetNames.isolate(record.ownerName())).getString();
    }

    private static String deathLine(AnimalRecord record) {
        Component line = record.deathWorldDay() == null ? null : DeathMessages.line(record, PetNames.isolate(record.name()));
        return line == null ? Component.translatable("familytree.node.deceased").getString() : line.getString();
    }

    private static String ageLine(long birthDay, long worldAge) {
        boolean showBirthDay = FamilyTreeClientSettings.showBirthDay();
        boolean showAge = FamilyTreeClientSettings.showAge();
        if (showBirthDay && showAge) return Component.translatable("familytree.screen.age_summary", birthDay, worldAge).getString();
        if (showBirthDay) return Component.translatable("familytree.node.born_world", birthDay).getString();
        if (showAge) return Component.translatable("familytree.screen.age_days", worldAge).getString();
        return "";
    }
}
