package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/** Words a recorded death with Minecraft's own death messages, which every client already has in its language. */
public final class DeathMessages {
    private DeathMessages() {}

    /** The death message for a deceased pet, or null when no cause was recorded. */
    public static Component of(AnimalRecord pet) {
        return of(pet, pet.name());
    }

    /** {@code shownName} replaces the pet's name in the message, as when a screen isolates it from the sentence. */
    public static Component of(AnimalRecord pet, String shownName) {
        AnimalRecord.DeathCause cause = pet.deathCause();
        if (cause == null || cause.attacker() == null && cause.msgId() == null) return null;
        // Plain string arguments, so ServerText can isolate a right-to-left name for clients with the mod.
        if (cause.msgId() == null) return Component.translatable("familytree.node.slain_by", cause.attacker());
        Language language = Language.getInstance();
        String key = DeathMessageKeys.key(cause.msgId(), cause.attacker() != null,
                id -> language.has(id) ? language.getOrDefault(id) : null);
        return cause.attacker() == null ? Component.translatable(key, shownName)
                : Component.translatable(key, shownName, cause.attacker());
    }

    /** One line such as "Rex suffocated in a wall on day 12", or null when no cause was recorded. */
    public static Component line(AnimalRecord pet) {
        return line(pet, pet.name());
    }

    public static Component line(AnimalRecord pet, String shownName) {
        Component message = of(pet, shownName);
        if (message == null || pet.deathWorldDay() == null) return message;
        return Component.translatable("familytree.node.died_on", message, pet.deathWorldDay());
    }
}
