package com.egakh.familytree.text;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class LangParityTest {
    private static final Pattern SPECIFIER = Pattern.compile("%(?:(\\d+)\\$)?(.)");

    @Test
    void everyLanguageHasExactlyTheEnglishKeys() {
        Map<String, String> english = LangTables.table(LangTables.DEFAULT);
        assertFalse(english.isEmpty(), "en_us.json is missing");
        List<String> problems = new ArrayList<>();
        for (String language : LangTables.SHIPPED) {
            Map<String, String> table = LangTables.table(language);
            if (table.isEmpty()) {
                problems.add(language + ": file is missing or empty");
                continue;
            }
            Set<String> missing = new TreeSet<>(english.keySet());
            missing.removeAll(table.keySet());
            Set<String> extra = new TreeSet<>(table.keySet());
            extra.removeAll(english.keySet());
            if (!missing.isEmpty()) problems.add(language + " lacks " + missing);
            if (!extra.isEmpty()) problems.add(language + " has unknown keys " + extra);
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyTranslationUsesTheEnglishFormatArguments() {
        Map<String, String> english = LangTables.table(LangTables.DEFAULT);
        List<String> problems = new ArrayList<>();
        for (String language : LangTables.SHIPPED) {
            for (Map.Entry<String, String> entry : LangTables.table(language).entrySet()) {
                String source = english.get(entry.getKey());
                if (source == null) continue;
                try {
                    Set<Integer> expected = arguments(source);
                    Set<Integer> actual = arguments(entry.getValue());
                    if (!expected.equals(actual))
                        problems.add(language + " " + entry.getKey() + ": uses " + actual + ", English uses " + expected);
                } catch (IllegalArgumentException invalid) {
                    problems.add(language + " " + entry.getKey() + ": " + invalid.getMessage());
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void serverFormattingAcceptsEveryTranslation() {
        for (String language : LangTables.SHIPPED) {
            for (Map.Entry<String, String> entry : LangTables.table(language).entrySet()) {
                Object[] args = new Object[arguments(entry.getValue()).stream().mapToInt(Integer::intValue).max().orElse(0)];
                java.util.Arrays.fill(args, "x");
                assertDoesNotThrow(() -> String.format(java.util.Locale.ROOT, entry.getValue(), args),
                        language + " " + entry.getKey());
            }
        }
    }

    /** Minecraft formats only %s, %n$s, and %%. Returns the 1-based argument numbers a string uses. */
    static Set<Integer> arguments(String text) {
        Set<Integer> used = new TreeSet<>();
        Matcher matcher = SPECIFIER.matcher(text);
        int next = 1;
        boolean implicit = false, explicit = false;
        while (matcher.find()) {
            String conversion = matcher.group(2);
            if (conversion.equals("%")) continue;
            if (!conversion.equals("s")) throw new IllegalArgumentException("unsupported specifier %" + conversion);
            if (matcher.group(1) != null) {
                explicit = true;
                used.add(Integer.parseInt(matcher.group(1)));
            } else {
                implicit = true;
                used.add(next++);
            }
        }
        if (implicit && explicit) throw new IllegalArgumentException("mixes %s and %n$s");
        return used;
    }
}
