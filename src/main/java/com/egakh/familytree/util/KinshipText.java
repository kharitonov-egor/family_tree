package com.egakh.familytree.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/** Plain-language relationship sentences shared by the tree, the planner, chat, and the chest menu book. */
public final class KinshipText {
    private KinshipText() {}

    public static MutableComponent describe(Kinship.Relation relation, String first, String second) {
        return switch (relation.kind()) {
            case SAME -> Component.translatable("familytree.kin.same", first, second);
            case UNRELATED -> Component.translatable("familytree.kin.unrelated", first, second);
            case ANCESTOR -> lineal("familytree.kin.", relation.down(), "parent", "grandparent", "great_grandparent", "ancestor", first, second);
            case DESCENDANT -> lineal("familytree.kin.", relation.up(), "child", "grandchild", "great_grandchild", "descendant", first, second);
            case SIBLING -> Component.translatable(switch (relation.half()) {
                case FULL -> "familytree.kin.siblings.full";
                case HALF -> "familytree.kin.siblings.half";
                case UNKNOWN -> "familytree.kin.siblings";
            }, first, second);
            case AUNT_OR_UNCLE -> collateral(relation.down(), relation.half(), "aunt", first, second);
            case NIECE_OR_NEPHEW -> collateral(relation.up(), relation.half(), "niece", first, second);
            case COUSIN -> Component.translatable("familytree.kin.cousins", first, second, cousins(relation));
        };
    }

    /** {@code steps} counts generations between the two animals: 1 for a parent, 2 for a grandparent. */
    private static MutableComponent lineal(String prefix, int steps, String one, String two, String three, String far,
                                           String first, String second) {
        return switch (steps) {
            case 1 -> Component.translatable(prefix + one, first, second);
            case 2 -> Component.translatable(prefix + two, first, second);
            case 3 -> Component.translatable(prefix + three, first, second);
            default -> Component.translatable(prefix + far, first, second, steps);
        };
    }

    /** {@code steps} is the far animal's distance to the shared ancestor: 2 for an aunt, 3 for a great-aunt. */
    private static MutableComponent collateral(int steps, Kinship.Half half, String role, String first, String second) {
        if (steps == 2) return Component.translatable("familytree.kin." + role + (half == Kinship.Half.HALF ? ".half" : ""), first, second);
        if (steps == 3) return Component.translatable("familytree.kin.great_" + role, first, second);
        return Component.translatable("familytree.kin.distant_" + role, first, second, steps - 1);
    }

    private static MutableComponent cousins(Kinship.Relation relation) {
        int degree = relation.cousinDegree();
        MutableComponent kind = degree <= 3 ? Component.translatable("familytree.kin.cousin." + degree)
                : Component.translatable("familytree.kin.cousin.n", degree);
        if (relation.half() == Kinship.Half.HALF) kind = Component.translatable("familytree.kin.half", kind);
        int removed = relation.removed();
        if (removed == 0) return kind;
        return removed <= 2 ? Component.translatable("familytree.kin.removed." + removed, kind)
                : Component.translatable("familytree.kin.removed.n", kind, removed);
    }

    public static MutableComponent inbreeding(Component baby, double coefficient) {
        return Component.translatable("familytree.planner.inbreeding", baby, percent(coefficient));
    }

    /** Percentages keep three significant digits, so 1/8 reads 12.5% and 1/1,200 reads 0.0833%. */
    public static String percent(double probability) {
        double value = probability * 100;
        if (value <= 0) return "0%";
        if (value >= 99.95 && value < 100) return "99.9%";
        String text;
        if (value >= 10) text = String.format(Locale.ROOT, "%.1f", value);
        else if (value >= 1) text = String.format(Locale.ROOT, "%.2f", value);
        else if (value >= 0.001) text = String.format(Locale.ROOT, "%.3g", value);
        else return "<0.001%";
        if (text.contains(".")) text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return text + "%";
    }
}
