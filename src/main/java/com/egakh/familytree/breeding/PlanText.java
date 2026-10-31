package com.egakh.familytree.breeding;

import com.egakh.familytree.util.HorseStatUnits.Stat;
import com.egakh.familytree.util.KinshipText;
import com.egakh.familytree.util.VariantRules;
import com.egakh.familytree.util.VariantText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A planned pair as text lines, for chat, the chest menu book, and the planner screen's labels. */
public final class PlanText {
    private PlanText() {}

    /** One line of plan text. Muted lines are explanations and percentages that chat shows in gray. */
    public record Line(Component text, boolean muted) {}

    /** Lines grouped by topic. The chest menu book puts each group on its own page. */
    public static List<List<Line>> sections(BreedingPlan plan) {
        List<List<Line>> sections = new ArrayList<>();
        List<Line> intro = new ArrayList<>();
        intro.add(new Line(Component.translatable("familytree.plan.header", plan.first.name(), plan.second.name()), false));
        if (!plan.canBreed) {
            intro.add(new Line(Component.translatable("familytree.planner.cannot_breed", plan.first.name(), plan.second.name()), false));
            if (plan.relation != null) intro.add(new Line(KinshipText.describe(plan.relation, plan.first.name(), plan.second.name()), false));
            sections.add(intro);
            return sections;
        }
        intro.add(new Line(Component.translatable("familytree.planner.foal", baby(plan.foalSpecies), foal(plan.foalSpecies)), false));
        intro.add(new Line(KinshipText.describe(plan.relation, plan.first.name(), plan.second.name()), false));
        sections.add(intro);
        sections.add(List.of(new Line(KinshipText.inbreeding(baby(plan.foalSpecies), plan.inbreeding), false),
                new Line(Component.translatable("familytree.planner.inbreeding.note"), true)));
        List<Line> stats = new ArrayList<>();
        for (BreedingPlan.StatForecast stat : plan.stats) {
            if (stats.size() == 4) {
                sections.add(stats);
                stats = new ArrayList<>();
            }
            stats.add(new Line(Component.translatable("familytree.plan.stat." + key(stat.stat()),
                    format(stat.stat(), stat.lowest()), format(stat.stat(), stat.highest()), likely(stat)), false));
            stats.add(new Line(Component.translatable("familytree.plan.beats", KinshipText.percent(stat.chanceAboveBoth())), true));
        }
        for (var pet : plan.missingStats) stats.add(new Line(Component.translatable("familytree.planner.stats.unknown", pet.name()), true));
        if (plan.fixedStats) stats.add(new Line(Component.translatable("familytree.planner.stats.camel"), true));
        if (!stats.isEmpty()) sections.add(stats);
        for (VariantOdds.Group group : plan.variants.groups()) sections.add(List.of(new Line(group(plan.foalSpecies.get(0), group), false)));
        Component note = note(plan.variants.note());
        if (note != null) sections.add(List.of(new Line(note, true)));
        return sections;
    }

    /** Chat lines: the header in yellow and explanations in gray, with percentages indented under each stat. */
    public static List<Component> lines(BreedingPlan plan) {
        List<Component> lines = new ArrayList<>();
        for (List<Line> section : sections(plan)) {
            for (Line line : section) {
                MutableComponent text = line.muted() ? Component.literal("  ").append(line.text()).withStyle(ChatFormatting.GRAY)
                        : line.text().copy();
                lines.add(lines.isEmpty() ? text.withStyle(ChatFormatting.YELLOW) : text);
            }
        }
        return lines;
    }

    public static Component baby(List<String> species) {
        return Component.translatable(BreedingPair.babyKey(species));
    }

    public static Component foal(List<String> species) {
        if (species.isEmpty()) return Component.literal("?");
        if (species.size() == 1) return speciesName(species.get(0));
        return Component.translatable("familytree.planner.foal.either", speciesName(species.get(0)), speciesName(species.get(1)));
    }

    public static MutableComponent group(String species, VariantOdds.Group group) {
        MutableComponent list = Component.empty();
        for (MutableComponent entry : entries(species, group)) {
            if (!list.getSiblings().isEmpty()) list.append(", ");
            list.append(entry);
        }
        return Component.translatable("familytree.plan.variant", title(group.kind()), list);
    }

    /** Each outcome with its chance, then the share the biome decides. */
    public static List<MutableComponent> entries(String species, VariantOdds.Group group) {
        List<MutableComponent> entries = new ArrayList<>();
        for (VariantOdds.Chance chance : group.chances()) entries.add(chance(species, group.kind(), chance));
        if (group.unpredictable() > 0)
            entries.add(Component.translatable("familytree.planner.variant.biome_share", KinshipText.percent(group.unpredictable())));
        return entries;
    }

    public static MutableComponent chance(String species, VariantOdds.Kind kind, VariantOdds.Chance chance) {
        MutableComponent label = label(species, kind, chance.variant());
        MutableComponent text = Component.translatable("familytree.plan.odds", label, KinshipText.percent(chance.probability()));
        if (kind != VariantOdds.Kind.COAT && kind != VariantOdds.Kind.MARKINGS && VariantRules.rare(species, chance.variant()))
            text.append(Component.literal(" " + VariantText.RARE_MARKER).withStyle(style ->
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(VariantText.RARE_COLOR & 0xFFFFFF))));
        return text;
    }

    public static MutableComponent label(String species, VariantOdds.Kind kind, String variant) {
        MutableComponent label = switch (kind) {
            case WOOL -> VariantText.woolColor(variant);
            case COAT -> VariantText.horseCoat(variant);
            case MARKINGS -> VariantText.horseMarkings(variant);
            case LOOK, VARIANT -> VariantText.label(species, variant);
        };
        if (label != null) return label;
        String coat = VariantRules.coatKey(species, variant);
        return coat == null ? Component.literal(VariantRules.humanize(variant))
                : Component.translatableWithFallback(coat, VariantRules.humanize(variant));
    }

    public static Component title(VariantOdds.Kind kind) {
        return Component.translatable("familytree.planner.variant." + kind.name().toLowerCase(Locale.ROOT));
    }

    public static Component note(VariantOdds.Note note) {
        return switch (note) {
            case NONE -> null;
            case FROG_BIOME -> Component.translatable("familytree.planner.note.frog");
            case RABBIT_BIOME -> Component.translatable("familytree.planner.note.rabbit");
            case MODDED -> Component.translatable("familytree.planner.note.modded");
            case UNKNOWN_PARENT -> Component.translatable("familytree.planner.note.unknown_parent");
        };
    }

    public static String key(Stat stat) {
        return stat.name().toLowerCase(Locale.ROOT);
    }

    /** Chest slots are whole numbers; other stats use the precision of the tree cards. */
    public static String format(Stat stat, double value) {
        return stat == Stat.STRENGTH ? String.valueOf(Math.round(value)) : stat.format(value);
    }

    public static MutableComponent likely(BreedingPlan.StatForecast stat) {
        if (stat.likelyLow() == stat.likelyHigh()) return Component.literal(format(stat.stat(), stat.likelyLow()));
        return Component.translatable("familytree.planner.range", format(stat.stat(), stat.likelyLow()),
                format(stat.stat(), stat.likelyHigh()));
    }

    public static MutableComponent speciesName(String speciesId) {
        int colon = speciesId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : speciesId.substring(0, colon);
        String path = colon < 0 ? speciesId : speciesId.substring(colon + 1);
        return Component.translatableWithFallback("entity." + namespace + "." + path, path.replace('_', ' '));
    }
}
