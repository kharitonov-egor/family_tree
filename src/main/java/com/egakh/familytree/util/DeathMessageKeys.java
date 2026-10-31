package com.egakh.familytree.util;

import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/** Chooses which of Minecraft's death messages words a saved death cause. Shared with the plugin. */
public final class DeathMessageKeys {
    public static final String GENERIC = "death.attack.generic";
    private static final Pattern SECOND_ARGUMENT = Pattern.compile("%2\\$s|%s.*%s", Pattern.DOTALL);

    private DeathMessageKeys() {}

    /**
     * The message for {@code msgId}, or the plain "died" message when the language lacks it or when it names an
     * attacker nobody recorded, such as thorns damage from {@code /damage}. {@code translation} returns null for a
     * missing key.
     */
    public static String key(String msgId, boolean attackerKnown, UnaryOperator<String> translation) {
        String key = "death.attack." + msgId;
        String text = translation.apply(key);
        return text == null || !attackerKnown && namesAttacker(text) ? GENERIC : key;
    }

    static boolean namesAttacker(String text) {
        return SECOND_ARGUMENT.matcher(text).find();
    }
}
