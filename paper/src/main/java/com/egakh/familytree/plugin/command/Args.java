package com.egakh.familytree.plugin.command;

/**
 * Reads command arguments the way Brigadier's {@code StringArgumentType} does: a word, or a quoted string with
 * {@code \"} and {@code \\} escapes. Unlike Brigadier, an unquoted word may contain any character but a space.
 */
final class Args {
    private final String input;
    private int cursor;

    Args(String[] args) {
        this.input = String.join(" ", args);
    }

    boolean done() {
        skipSpaces();
        return cursor >= input.length();
    }

    /** The next word or quoted string, or null when there is none. */
    String next() {
        skipSpaces();
        if (cursor >= input.length()) return null;
        char first = input.charAt(cursor);
        if (first == '"' || first == '\'') {
            StringBuilder out = new StringBuilder();
            cursor++;
            while (cursor < input.length()) {
                char c = input.charAt(cursor++);
                if (c == '\\' && cursor < input.length()) out.append(input.charAt(cursor++));
                else if (c == first) return out.toString();
                else out.append(c);
            }
            return out.toString();
        }
        int start = cursor;
        while (cursor < input.length() && input.charAt(cursor) != ' ') cursor++;
        return input.substring(start, cursor);
    }

    /** Everything left, for greedy arguments such as names with spaces. */
    String rest() {
        skipSpaces();
        String rest = input.substring(Math.min(cursor, input.length())).trim();
        cursor = input.length();
        return rest;
    }

    private void skipSpaces() {
        while (cursor < input.length() && input.charAt(cursor) == ' ') cursor++;
    }

    static String quoteIfNeeded(String name) {
        if (!name.isEmpty() && name.indexOf(' ') < 0 && name.indexOf('"') < 0 && name.indexOf('\'') < 0) return name;
        return '"' + name.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
