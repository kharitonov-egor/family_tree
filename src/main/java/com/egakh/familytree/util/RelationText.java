package com.egakh.familytree.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

public final class RelationText {
    private RelationText() {}

    /** "Parent · Clover", with the relation in its color. */
    public static MutableComponent label(CloseRelation relation, String name) {
        return Component.translatable("familytree.relatives.label",
                Component.translatable(relation.translationKey()).withStyle(style -> style.withColor(TextColor.fromRgb(relation.color()))),
                Component.literal(name));
    }
}
