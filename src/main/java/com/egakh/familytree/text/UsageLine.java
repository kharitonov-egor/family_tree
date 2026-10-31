package com.egakh.familytree.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Trims the translated {@code familytree.command.usage} line to the subcommands a player may run. Every language
 * keeps the subcommand names untranslated as the first word of each choice, so the line is filtered, not rebuilt.
 */
public final class UsageLine {
    private UsageLine() {}

    public static String filter(String usage, Predicate<String> mayRun) {
        int open = usage.indexOf('<');
        int close = usage.lastIndexOf('>');
        if (open < 0 || close <= open) return usage;
        List<String> kept = new ArrayList<>();
        for (String choice : choices(usage.substring(open + 1, close))) {
            if (choice == null) return usage;
            int space = choice.indexOf(' ');
            if (mayRun.test(space < 0 ? choice : choice.substring(0, space))) kept.add(choice);
        }
        return usage.substring(0, open + 1) + String.join("|", kept) + usage.substring(close);
    }

    /** Splits on the bars outside nested brackets. A null entry means the brackets don't balance. */
    static List<String> choices(String inner) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '<' || c == '[') depth++;
            else if (c == '>' || c == ']') depth--;
            else if (c == '|' && depth == 0) {
                parts.add(inner.substring(start, i).strip());
                start = i + 1;
            }
            if (depth < 0) {
                parts.add(null);
                return parts;
            }
        }
        parts.add(depth == 0 ? inner.substring(start).strip() : null);
        return parts;
    }
}
