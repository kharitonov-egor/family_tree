package com.egakh.familytree.client.screen;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Wraps plain text at Unicode line-break opportunities, so Chinese, Japanese, and Korean lines break between characters
 * instead of only at spaces. Minecraft's splitter breaks at the last space, which left "Family" alone on a Japanese line.
 */
public final class LineBreaks {
    /** Phrases that never break across lines. */
    static final List<String> UNBROKEN = List.of("Family Tree");

    private LineBreaks() {}

    public static List<String> split(String text, int maxWidth, ToIntFunction<String> width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) splitParagraph(paragraph, Math.max(1, maxWidth), width, lines);
        return lines;
    }

    private static void splitParagraph(String text, int maxWidth, ToIntFunction<String> width, List<String> lines) {
        if (text.isEmpty()) { lines.add(""); return; }
        String analysed = text;
        for (String phrase : UNBROKEN) analysed = analysed.replace(phrase, phrase.replace(' ', ' '));
        BreakIterator breaks = BreakIterator.getLineInstance(Locale.ROOT);
        breaks.setText(analysed);
        int lineStart = 0, lastFit = -1;
        for (int end = breaks.next(); end != BreakIterator.DONE; end = breaks.next()) {
            if (width.applyAsInt(text.substring(lineStart, end).stripTrailing()) <= maxWidth) {
                lastFit = end;
                continue;
            }
            if (lastFit > lineStart) {
                lines.add(text.substring(lineStart, lastFit).stripTrailing());
                lineStart = lastFit;
                if (width.applyAsInt(text.substring(lineStart, end).stripTrailing()) <= maxWidth) { lastFit = end; continue; }
            }
            lineStart = hardBreak(text, lineStart, end, maxWidth, width, lines);
            lastFit = end;
        }
        if (lineStart < text.length()) lines.add(text.substring(lineStart).stripTrailing());
    }

    /** Splits one segment wider than the line by characters, returning where its unfinished last line starts. */
    private static int hardBreak(String text, int start, int end, int maxWidth, ToIntFunction<String> width, List<String> lines) {
        int lineStart = start;
        int cut = lineStart;
        while (cut < end) {
            int next = text.offsetByCodePoints(cut, 1);
            if (cut > lineStart && width.applyAsInt(text.substring(lineStart, next)) > maxWidth) {
                lines.add(text.substring(lineStart, cut));
                lineStart = cut;
            }
            cut = next;
        }
        return lineStart;
    }
}
