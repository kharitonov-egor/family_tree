package com.egakh.familytree.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Wraps text that holds bidi isolates around pet names. Minecraft orders each wrapped line on its own, so a name
 * split across two lines would lose its isolate and turn the second line right to left. Each line here reopens
 * the isolates the previous line left open, with the direction the whole name had.
 */
public final class BidiLines {
    static final char LRI = '\u2066', RLI = '\u2067', FSI = '\u2068', PDI = '\u2069';

    private BidiLines() {}

    /** Inline styles give way to the text's own style when the text holds isolates, which only pet names add. */
    public static List<FormattedCharSequence> split(Font font, Component text, int width) {
        String plain = text.getString();
        if (!hasIsolates(plain)) return font.split(text, width);
        List<FormattedCharSequence> ordered = new ArrayList<>();
        for (String line : wrap(plain, value -> lines(font, value, width)))
            ordered.add(Language.getInstance().getVisualOrder(FormattedText.of(line, text.getStyle())));
        return ordered;
    }

    static List<String> lines(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        font.getSplitter().splitLines(text, Math.max(1, width), Style.EMPTY, false,
                (style, start, end) -> lines.add(text.substring(start, end)));
        return lines;
    }

    /** Plain-text lines for {@code text} wrapped by {@code wrap}, each with balanced isolates. */
    static List<String> wrap(String text, java.util.function.Function<String, List<String>> wrap) {
        if (!hasIsolates(text)) return wrap.apply(text);
        return carry(wrap.apply(resolve(text)));
    }

    static boolean hasIsolates(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= LRI && c <= PDI) return true;
        }
        return false;
    }

    /** Replaces each first-strong isolate with the explicit direction its whole content has. */
    static String resolve(String text) {
        StringBuilder out = new StringBuilder(text);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == FSI) out.setCharAt(i, firstStrongIsRightToLeft(text, i + 1) ? RLI : LRI);
        }
        return out.toString();
    }

    private static boolean firstStrongIsRightToLeft(String text, int start) {
        int depth = 0;
        for (int i = start; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == LRI || codePoint == RLI || codePoint == FSI) depth++;
            else if (codePoint == PDI) {
                if (depth-- == 0) return false;
            } else if (depth == 0) {
                byte direction = Character.getDirectionality(codePoint);
                if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false;
                if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                        || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true;
            }
        }
        return false;
    }

    /** Closes each line's open isolates at its end and reopens them at the start of the next line. */
    static List<String> carry(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        Deque<Character> open = new ArrayDeque<>();
        for (String line : lines) {
            String reopened = opening(open);
            close(line, open);
            out.add(reopened + line + closing(open));
        }
        return out;
    }

    /** Appends a pop for every isolate the text opens and never closes, as when an ellipsis cuts a name short. */
    static String balance(String text) {
        Deque<Character> open = new ArrayDeque<>();
        close(text, open);
        return text + closing(open);
    }

    private static String opening(Deque<Character> open) {
        StringBuilder out = new StringBuilder();
        open.descendingIterator().forEachRemaining(out::append);
        return out.toString();
    }

    private static String closing(Deque<Character> open) {
        return String.valueOf(PDI).repeat(open.size());
    }

    private static void close(String line, Deque<Character> open) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == LRI || c == RLI || c == FSI) open.push(c);
            else if (c == PDI && !open.isEmpty()) open.pop();
        }
    }
}
