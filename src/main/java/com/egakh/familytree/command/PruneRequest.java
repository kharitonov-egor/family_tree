package com.egakh.familytree.command;

import java.util.Locale;

/** {@code /familytree prune species <id> [confirm]}. The species ID is a greedy argument, so confirm arrives inside it. */
public record PruneRequest(String species, boolean confirmed) {
    private static final String CONFIRM = " confirm";

    public static PruneRequest species(String argument) {
        String text = argument.trim();
        boolean confirmed = text.toLowerCase(Locale.ROOT).endsWith(CONFIRM);
        if (confirmed) text = text.substring(0, text.length() - CONFIRM.length()).trim();
        return new PruneRequest(text, confirmed);
    }
}
