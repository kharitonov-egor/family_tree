package com.egakh.familytree.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Tooltip line breaking and book layout for the chest menus, without Minecraft classes so the plugin shares it. */
public final class MenuText {
    /** A book page shows 14 lines of 9 pixels in a text area 114 pixels wide, on every supported version. */
    public static final int BOOK_LINES = 14;
    private static final int BOOK_WIDTH = 114;
    /** Latin, Greek, and Cyrillic letters the default font draws wider than six pixels, for advances of 7, 8, 9, and 10. */
    private static final String[] WIDER = {
            "«¶»ÐĐđħŁƀƄƅƇƈƉƑƓƝƬƳǤǥȽɃɇɈɉɊɋɌɍσϔЊЋДЦЪдщъыђњћѢѣѴѵѶѷҊҋҌҍҒҗҚҞҠҢңҤҥҨҩҲҳҵҶҷӅӆӉӊӍӎӹӺԆԇԎԏԤԥԦԧԨԩԬԭԮԯ",
            "¤©®¼½¾ďĦŉƁƊƠơƢƣƤƯưƴǉȡȵɄɎɏΆΈΉΌΎΏωώЂЉЖФШЫЮюљҴҼҽҾҿӁӜӸԄԅ",
            "ƕǶЩҖҦҧԂԃԈԉԊԋԔԕԖԗԠԡԢԣ",
            "ÆæŒœǄǅǆǇǈǊǋǌǢǣǱǲǳǼǽȸȹϓӔӕԘԙԪԫ"};

    private MenuText() {}

    /** Cuts text to at most {@code max} UTF-16 units, ending in an ellipsis, without splitting a surrogate pair. */
    public static String trim(String text, int max) {
        if (text.length() <= max) return text;
        int end = max - 1;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + "…";
    }

    /**
     * The number of lines a paragraph takes on a book page. It breaks lines the way the game does, at the last space
     * that fits, using the default font's widths. Other scripts count as wide as the widest glyph, so a page may end
     * early but never runs past its last line.
     */
    public static int bookLines(String text, boolean bold) {
        int[] points = text.codePoints().toArray();
        int lines = 0;
        int start = 0;
        do {
            lines++;
            int width = 0, lastSpace = -1, next = points.length;
            boolean drawn = false;
            for (int i = start; i < points.length; i++) {
                int point = points[i];
                if (point == '\n') {
                    next = i + 1;
                    break;
                }
                if (point == ' ') lastSpace = i;
                width += advance(point) + (bold ? 1 : 0);
                if (drawn && width > BOOK_WIDTH) {
                    next = lastSpace >= 0 ? lastSpace + 1 : i;
                    break;
                }
                drawn = true;
            }
            start = next;
        } while (start < points.length);
        return lines;
    }

    private static int advance(int point) {
        if (point < 128) {
            if ("!',.:;i|".indexOf(point) >= 0) return 2;
            if ("`l".indexOf(point) >= 0) return 3;
            if (" \"()*I[]t{}".indexOf(point) >= 0) return 4;
            if ("<>fk".indexOf(point) >= 0) return 5;
            if (point == '@' || point == '~') return 7;
            return 6;
        }
        if (wide(point) || point > 0xFFFF) return 9;
        if (point < 0x250 || point >= 0x370 && point < 0x530) {
            for (int i = WIDER.length - 1; i >= 0; i--) {
                if (WIDER[i].indexOf(point) >= 0) return 7 + i;
            }
            return 6;
        }
        return 10;
    }

    /**
     * Puts paragraphs on book pages, a blank line apart. A section moves to a new page when the rest of the current
     * page can't hold it, and splits between paragraphs only when it is longer than a page.
     */
    public static <T> List<List<T>> bookPages(List<List<T>> sections, ToIntFunction<T> lines) {
        List<List<T>> pages = new ArrayList<>();
        List<T> page = new ArrayList<>();
        int used = 0;
        for (List<T> section : sections) {
            int needed = -1;
            for (T paragraph : section) needed += 1 + lines.applyAsInt(paragraph);
            if (!page.isEmpty() && needed <= BOOK_LINES && used + 1 + needed > BOOK_LINES) {
                pages.add(page);
                page = new ArrayList<>();
                used = 0;
            }
            for (T paragraph : section) {
                int height = lines.applyAsInt(paragraph);
                if (!page.isEmpty() && used + 1 + height > BOOK_LINES) {
                    pages.add(page);
                    page = new ArrayList<>();
                    used = 0;
                }
                used += page.isEmpty() ? height : 1 + height;
                page.add(paragraph);
            }
        }
        if (!page.isEmpty()) pages.add(page);
        return pages;
    }

    /** Breaks text into tooltip lines. Wide CJK characters count as two columns. */
    public static List<String> wrap(String text, int columns) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int width = 0;
        int lastSpace = -1;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\n') {
                lines.add(line.toString().strip());
                line.setLength(0);
                width = 0;
                lastSpace = -1;
                continue;
            }
            line.appendCodePoint(codePoint);
            width += wide(codePoint) ? 2 : 1;
            if (codePoint == ' ') lastSpace = line.length();
            if (width > columns) {
                int cut = lastSpace > 0 ? lastSpace : line.length() - Character.charCount(codePoint);
                if (cut <= 0) cut = line.length();
                lines.add(line.substring(0, cut).strip());
                String rest = line.substring(cut);
                line.setLength(0);
                line.append(rest);
                width = rest.codePoints().map(c -> wide(c) ? 2 : 1).sum();
                lastSpace = -1;
            }
        }
        if (!line.isEmpty()) lines.add(line.toString().strip());
        lines.removeIf(String::isEmpty);
        return lines;
    }

    private static boolean wide(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL
                || codePoint >= 0xFF00 && codePoint <= 0xFFEF || codePoint >= 0x3000 && codePoint <= 0x303F;
    }
}
