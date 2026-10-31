package com.egakh.familytree.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minecraft's translation format: {@code %s} takes the next argument, {@code %2$s} a numbered one, {@code %%} is a percent sign. */
public final class Templates {
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private Templates() {}

    /** Splits a template into strings and the arguments placed between them. A malformed template stays literal. */
    public static List<Object> split(String template, List<?> args) {
        List<Object> parts = new ArrayList<>();
        Matcher matcher = FORMAT.matcher(template);
        int next = 0;
        int last = 0;
        try {
            while (matcher.find()) {
                if (matcher.start() > last) parts.add(template.substring(last, matcher.start()));
                last = matcher.end();
                String type = matcher.group(2);
                if ("%".equals(type) && matcher.group(1) == null) {
                    parts.add("%");
                    continue;
                }
                if (!"s".equals(type)) throw new IllegalArgumentException(template);
                int index = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) - 1 : next++;
                parts.add(index >= 0 && index < args.size() ? args.get(index) : "");
            }
        } catch (IllegalArgumentException malformed) {
            return List.of(template);
        }
        if (last < template.length()) parts.add(template.substring(last));
        return parts;
    }
}
