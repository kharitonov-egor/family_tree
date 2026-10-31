package com.egakh.familytree.util;

import java.util.Locale;

/**
 * The relations the in-world highlight names. It reads {@link Kinship} so the world labels and the breeding planner
 * agree on who is related. Aunts, uncles, and cousins stay dark, so a pen of one big family doesn't light up whole.
 * Colors come from the Okabe-Ito palette, which stays distinguishable with the common kinds of color blindness, and
 * every label names the relation too, so color is never the only cue. {@link RelationText} builds the labels, so this
 * class stays free of Minecraft classes and the plugin compiles it too.
 */
public enum CloseRelation {
    /** Closest first. The network sends the ordinal, so add new relations at the end. */
    PARENT, CHILD, SIBLING, HALF_SIBLING, GRANDPARENT, GRANDCHILD;

    /** What {@code relation}'s first animal is to its second, or null when the highlight leaves it out. */
    public static CloseRelation of(Kinship.Relation relation) {
        return switch (relation.kind()) {
            case ANCESTOR -> relation.down() == 1 ? PARENT : relation.down() == 2 ? GRANDPARENT : null;
            case DESCENDANT -> relation.up() == 1 ? CHILD : relation.up() == 2 ? GRANDCHILD : null;
            case SIBLING -> relation.half() == Kinship.Half.HALF ? HALF_SIBLING : SIBLING;
            default -> null;
        };
    }

    public int color() {
        return switch (this) {
            case PARENT -> 0xE69F00;
            case CHILD -> 0x56B4E9;
            case SIBLING, HALF_SIBLING -> 0x009E73;
            case GRANDPARENT -> 0xF0E442;
            case GRANDCHILD -> 0xCC79A7;
        };
    }

    public String translationKey() {
        return "familytree.relation." + name().toLowerCase(Locale.ROOT);
    }

    public static CloseRelation byOrdinal(int ordinal) {
        CloseRelation[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
