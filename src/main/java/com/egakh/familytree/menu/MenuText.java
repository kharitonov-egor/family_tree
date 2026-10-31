package com.egakh.familytree.menu;

import java.util.ArrayList;
import java.util.List;

/** Tooltip line breaking for the chest menus, without Minecraft classes so the plugin shares it. */
public final class MenuText {
    private MenuText() {}

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
