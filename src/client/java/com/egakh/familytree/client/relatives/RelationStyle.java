package com.egakh.familytree.client.relatives;

import net.minecraft.network.chat.Component;

/** The animal the player looks at gets a white outline and a marker; relatives use {@link com.egakh.familytree.util.CloseRelation}'s colors. */
public final class RelationStyle {
    public static final int FOCUS = 0xFFFFFF;

    private RelationStyle() {}

    public static Component focusLabel(String name) {
        return Component.translatable("familytree.relatives.focus", Component.literal(name));
    }
}
