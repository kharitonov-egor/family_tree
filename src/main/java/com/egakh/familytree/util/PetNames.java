package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class PetNames {
    public static final char FIRST_STRONG_ISOLATE = '\u2068';
    public static final char POP_DIRECTIONAL_ISOLATE = '\u2069';

    private PetNames() {}

    /**
     * Wraps a name that holds right-to-left text or bidi controls in a first-strong isolate, so a sentence keeps
     * its own direction around it. Other names come back unchanged. Only clients with the mod draw the isolate
     * marks as nothing; vanilla fonts show them as boxes.
     */
    public static String isolate(String name) {
        if (name == null || !needsIsolate(name)) return name;
        return FIRST_STRONG_ISOLATE + balanced(name) + POP_DIRECTIONAL_ISOLATE;
    }

    /**
     * Drops pops the name never opened and closes the isolates it leaves open, so the name can't end the wrapper
     * early. The closing pop also ends any embedding or override still open inside it.
     */
    static String balanced(String name) {
        StringBuilder out = new StringBuilder(name.length() + 2);
        int open = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\u2066' || c == '\u2067' || c == FIRST_STRONG_ISOLATE) open++;
            else if (c == POP_DIRECTIONAL_ISOLATE) {
                if (open == 0) continue;
                open--;
            }
            out.append(c);
        }
        return out.append(String.valueOf(POP_DIRECTIONAL_ISOLATE).repeat(open)).toString();
    }

    public static boolean needsIsolate(String text) {
        return text.codePoints().anyMatch(codePoint -> switch (Character.getDirectionality(codePoint)) {
            case Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                 Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING, Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                 Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING, Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
                 Character.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT, Character.DIRECTIONALITY_LEFT_TO_RIGHT_ISOLATE,
                 Character.DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE, Character.DIRECTIONALITY_FIRST_STRONG_ISOLATE,
                 Character.DIRECTIONALITY_POP_DIRECTIONAL_ISOLATE -> true;
            default -> false;
        });
    }

    public static List<AnimalRecord> matches(Collection<AnimalRecord> records, String name,
                                              Predicate<AnimalRecord> allowed) {
        UUID id;
        try { id = UUID.fromString(name); }
        catch (IllegalArgumentException invalid) { id = null; }
        UUID exact = id;
        return records.stream().filter(allowed)
                .filter(pet -> exact == null ? pet.matchesName(name) : pet.id().equals(exact))
                .sorted(Comparator.comparing(AnimalRecord::id)).toList();
    }

    public enum Outcome { FOUND, AMBIGUOUS, NOT_ALLOWED, UNKNOWN }

    /** {@code matches} holds every pet the player may act on, so it has one entry when the outcome is FOUND. */
    public record Lookup(Outcome outcome, List<AnimalRecord> matches) {
        public AnimalRecord pet() {
            return outcome == Outcome.FOUND ? matches.get(0) : null;
        }
    }

    /**
     * Resolves a typed name for an action. A pet the player can see but not act on answers NOT_ALLOWED instead of
     * UNKNOWN, while a pet the player can't see stays unknown. The name may be wrapped in one pair of quotes.
     */
    public static Lookup find(Collection<AnimalRecord> records, String typed,
                              Predicate<AnimalRecord> visible, Predicate<AnimalRecord> usable) {
        String unquoted = unquote(typed);
        Lookup lookup = findExact(records, typed, visible, usable);
        if (lookup.outcome() != Outcome.UNKNOWN || unquoted.equals(typed)) return lookup;
        return findExact(records, unquoted, visible, usable);
    }

    private static Lookup findExact(Collection<AnimalRecord> records, String name,
                                    Predicate<AnimalRecord> visible, Predicate<AnimalRecord> usable) {
        List<AnimalRecord> found = matches(records, name, usable);
        if (found.size() == 1) return new Lookup(Outcome.FOUND, found);
        if (found.size() > 1) return new Lookup(Outcome.AMBIGUOUS, found);
        return new Lookup(matches(records, name, visible).isEmpty() ? Outcome.UNKNOWN : Outcome.NOT_ALLOWED, List.of());
    }

    /** Strips one pair of matching quotes, with Brigadier's {@code \"} and {@code \\} escapes, from a greedy argument. */
    public static String unquote(String typed) {
        String text = typed.strip();
        if (text.length() < 2) return text;
        char quote = text.charAt(0);
        if ((quote != '"' && quote != '\'') || text.charAt(text.length() - 1) != quote) return text;
        StringBuilder out = new StringBuilder(text.length() - 2);
        for (int i = 1; i < text.length() - 1; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() - 1) {
                char next = text.charAt(i + 1);
                if (next == '\\' || next == quote) {
                    out.append(next);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
