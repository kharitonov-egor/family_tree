package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.screen.TreeCard.Kind;
import com.egakh.familytree.client.screen.TreeCard.Source;
import com.egakh.familytree.util.HorseStatUnits.Trend;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TreeCardTest {
    /** Six pixels per character and nine-pixel lines, like Minecraft's font for plain Latin text. */
    private static final TreeCard.Measure MONO = new TreeCard.Measure() {
        @Override public int width(String text) { return 6 * text.codePointCount(0, text.length()); }
        @Override public List<String> wrap(String text, int width) {
            int perLine = Math.max(1, width / 6);
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                while (word.length() > perLine) {
                    if (!line.isEmpty()) { lines.add(line.toString()); line.setLength(0); }
                    lines.add(word.substring(0, perLine));
                    word = word.substring(perLine);
                }
                if (!line.isEmpty() && line.length() + 1 + word.length() > perLine) { lines.add(line.toString()); line.setLength(0); }
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
            if (!line.isEmpty()) lines.add(line.toString());
            return lines;
        }
        @Override public String prefix(String text, int width) { return text.substring(0, Math.min(text.length(), Math.max(0, width / 6))); }
        @Override public int lineHeight() { return 9; }
    };
    private static final int STEP = 11;

    @Test
    void deathMessageWrapsIntoFreeLinesAndKeepsItsDay() {
        String death = "D-sweet_berry_bush was poked to death by a sweet berry bush on day 12";
        TreeCard card = new TreeCard(List.of(Source.text(Kind.NAME, "D-sweet_berry_bush"),
                Source.text(Kind.OWNER, "Tamed by Alex"), Source.text(Kind.DEATH, death)), true, MONO);

        List<String> lines = texts(card.full.rows(), Kind.DEATH);
        assertEquals(3, lines.size(), lines.toString());
        assertEquals(death, String.join(" ", lines));
        assertFalse(card.full.cut());
    }

    @Test
    void deathMessageTooLongForThreeLinesEndsWithEllipsisAndReadsInFullOnHover() {
        String death = "Rex was blown up by a creeper while fighting a skeleton in the deep dark near the ancient city on day 4012";
        TreeCard card = new TreeCard(List.of(Source.text(Kind.NAME, "Rex"), Source.text(Kind.DEATH, death)), true, MONO);

        List<String> lines = texts(card.full.rows(), Kind.DEATH);
        assertEquals(TreeCard.DEATH_LINES, lines.size());
        assertTrue(lines.get(lines.size() - 1).endsWith(TreeCard.ELLIPSIS), lines.toString());
        for (String line : lines) assertTrue(MONO.width(line) <= TreeCard.textWidth(true), line);
        assertTrue(card.full.cut());
        assertTrue(card.fullLines().contains(death));
    }

    @Test
    void longestNameStopsAtTwoLinesAndTheCardGrowsToShowEveryRow() {
        String name = "W".repeat(64);
        TreeCard card = new TreeCard(List.of(Source.text(Kind.NAME, name), Source.text(Kind.GENERATION, "Gen 1"),
                new Source(Kind.STATS, null, List.of(segment("7.6 b/s"), segment("1.71 jump"), segment("26.5 ♥"),
                        segment("15 slots")), false),
                new Source(Kind.VARIANT, "Brown", List.of(), false), Source.text(Kind.OWNER, "Tamed by Alex"),
                Source.text(Kind.AGE, "Day 0 | 0d")), true, MONO);

        List<String> names = texts(card.full.rows(), Kind.NAME);
        assertEquals(TreeCard.NAME_LINES, names.size());
        assertTrue(names.get(1).endsWith(TreeCard.ELLIPSIS));
        assertTrue(card.fullLines().contains(name));
        int rows = card.full.rows().size();
        int lastBaseline = TreeCard.PAD_TOP + (rows - 1) * STEP + MONO.lineHeight();
        assertTrue(lastBaseline <= card.height(MONO) - TreeCard.PAD_BOTTOM + 2,
                "The last row must sit inside the card: " + lastBaseline + " vs " + card.height(MONO));
        assertEquals(Kind.AGE, card.full.rows().get(rows - 1).kind());
    }

    @Test
    void cardsAreSizedToTheirContentInsteadOfAFixedHeight() {
        TreeCard wolf = new TreeCard(List.of(Source.text(Kind.NAME, "Rex"), Source.text(Kind.GENERATION, "Gen 1"),
                Source.text(Kind.OWNER, "Tamed by Alex")), true, MONO);
        TreeCard horse = new TreeCard(List.of(Source.text(Kind.NAME, "Gelato"), Source.text(Kind.GENERATION, "Gen 2"),
                new Source(Kind.STATS, null, List.of(segment("8.1 b/s"), segment("5.09 jump"), segment("11.1 ♥")), false),
                new Source(Kind.VARIANT, "Chestnut, white field", List.of(), true), Source.text(Kind.OWNER, "Tamed by Alex"),
                Source.text(Kind.AGE, "Day 0 | 0d")), true, MONO);

        int wolfHeight = wolf.height(MONO);
        assertEquals(TreeCard.FACE_TOP * 2 + TreeCard.FACE_SIZE, wolfHeight, "A three-line card is as tall as its portrait");
        assertTrue(wolfHeight < TreeLayout.NODE_HEIGHT / 2 + 5, "Old cards were " + TreeLayout.NODE_HEIGHT + " tall");
        assertEquals(TreeCard.PAD_TOP + 6 * STEP + TreeCard.PAD_BOTTOM, horse.height(MONO));
        assertEquals(horse.height(MONO), TreeCard.treeHeight(List.of(wolf, horse), MONO));
    }

    @Test
    void zoomedOutCardsKeepTheirFirstRowsAndCutTheRest() {
        TreeCard card = new TreeCard(List.of(Source.text(Kind.NAME, "Sir Fluffington the Third"),
                Source.text(Kind.GENERATION, "Gen 3"), Source.text(Kind.OWNER, "Tamed by Alex"),
                Source.text(Kind.DEATH, "Sir Fluffington the Third fell from a high place on day 9")), true, MONO);

        TreeCard.Fitted one = card.compact(MONO, 60, 1);
        assertEquals(List.of(Kind.NAME), one.rows().stream().map(TreeCard.Row::kind).toList());
        assertTrue(one.rows().get(0).text().endsWith(TreeCard.ELLIPSIS));
        assertTrue(one.cut());

        TreeCard.Fitted all = card.compact(MONO, 600, 10);
        assertEquals(List.of(Kind.NAME, Kind.GENERATION, Kind.OWNER, Kind.DEATH), all.rows().stream().map(TreeCard.Row::kind).toList());
        assertFalse(all.cut());
    }

    @Test
    void statsWrapWholeSegmentsAndStopAtTwoRows() {
        List<List<HorseStatsText.Segment>> lines = TreeCard.wrapSegments(MONO,
                List.of(segment("13.0 b/s"), segment("4.90 jump"), segment("14.0 \u2665")), 70);
        assertEquals(3, lines.size());

        TreeCard oneRow = new TreeCard(List.of(Source.text(Kind.NAME, "Comet"),
                new Source(Kind.STATS, null, List.of(segment("13.0 b/s"), segment("4.90 jump"), segment("14.0 \u2665")), false)),
                true, MONO);
        assertEquals(1, oneRow.full.rows().stream().filter(row -> row.kind() == Kind.STATS).count());
        assertFalse(oneRow.full.cut());

        List<HorseStatsText.Segment> wide = List.of(segment("12.34 blocks/s"), segment("5.67 blocks up"),
                segment("15.0 hearts max"), segment("15 chest slots"));
        TreeCard capped = new TreeCard(List.of(Source.text(Kind.NAME, "Comet"), new Source(Kind.STATS, null, wide, false)), true, MONO);
        assertEquals(TreeCard.STATS_LINES, capped.full.rows().stream().filter(row -> row.kind() == Kind.STATS).count());
        assertTrue(capped.full.cut());
    }

    private static HorseStatsText.Segment segment(String text) {
        return new HorseStatsText.Segment(text, Trend.UNKNOWN);
    }

    private static List<String> texts(List<TreeCard.Row> rows, Kind kind) {
        return rows.stream().filter(row -> row.kind() == kind).map(TreeCard.Row::text).toList();
    }
}
