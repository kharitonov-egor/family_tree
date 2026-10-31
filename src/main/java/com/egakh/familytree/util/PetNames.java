package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class PetNames {
    private PetNames() {}

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
