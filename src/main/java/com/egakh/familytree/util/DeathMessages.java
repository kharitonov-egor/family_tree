package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/** Words a recorded death with Minecraft's own death messages, which every client already has in its language. */
public final class DeathMessages {
    private DeathMessages() {}

    /** The death message for a deceased pet, or null when no cause was recorded. */
    public static Component of(AnimalRecord pet) {
        AnimalRecord.DeathCause cause = pet.deathCause();
        if (cause == null || cause.attacker() == null && cause.msgId() == null) return null;
        Component name = Component.literal(pet.name());
        if (cause.msgId() == null) return Component.translatable("familytree.node.slain_by", cause.attacker());
        String key = "death.attack." + cause.msgId();
        if (!Language.getInstance().has(key)) return Component.translatable("death.attack.generic", name);
        return cause.attacker() == null ? Component.translatable(key, name)
                : Component.translatable(key, name, Component.literal(cause.attacker()));
    }

    /** One line such as "Rex suffocated in a wall on day 12", or null when no cause was recorded. */
    public static Component line(AnimalRecord pet) {
        Component message = of(pet);
        if (message == null || pet.deathWorldDay() == null) return message;
        return Component.translatable("familytree.node.died_on", message, pet.deathWorldDay());
    }
}
