package com.egakh.familytree.plugin.text;

import com.egakh.familytree.breeding.BreedingPair;
import com.egakh.familytree.breeding.BreedingPlan;
import com.egakh.familytree.breeding.VariantOdds;
import com.egakh.familytree.util.HorseStatUnits.Stat;
import com.egakh.familytree.util.Kinship;
import com.egakh.familytree.util.VariantRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The plugin's versions of the mod's {@code PlanText} and {@code KinshipText}, with the same keys and layout. */
public final class PlanLines {
    private PlanLines() {}

    /** One line of plan text. Muted lines are explanations and percentages that chat shows in gray. */
    public record Line(Text text, boolean muted) {}

    /** Lines grouped by topic. The book puts each group on its own page. */
    public static List<List<Line>> sections(String language, BreedingPlan plan) {
        List<List<Line>> sections = new ArrayList<>();
        List<Line> intro = new ArrayList<>();
        intro.add(new Line(tr(language, "familytree.plan.header", plan.first.name(), plan.second.name()), false));
        if (!plan.canBreed) {
            intro.add(new Line(tr(language, "familytree.planner.cannot_breed", plan.first.name(), plan.second.name()), false));
            if (plan.relation != null) intro.add(new Line(kinship(language, plan.relation, plan.first.name(), plan.second.name()), false));
            sections.add(intro);
            return sections;
        }
        Text baby = tr(language, BreedingPair.babyKey(plan.foalSpecies));
        intro.add(new Line(tr(language, "familytree.planner.foal", baby, foal(language, plan.foalSpecies)), false));
        intro.add(new Line(kinship(language, plan.relation, plan.first.name(), plan.second.name()), false));
        sections.add(intro);
        sections.add(List.of(new Line(tr(language, "familytree.planner.inbreeding", baby, percent(plan.inbreeding)), false),
                new Line(tr(language, "familytree.planner.inbreeding.note"), true)));
        List<Line> stats = new ArrayList<>();
        for (BreedingPlan.StatForecast stat : plan.stats) {
            if (stats.size() == 4) {
                sections.add(stats);
                stats = new ArrayList<>();
            }
            stats.add(new Line(tr(language, "familytree.plan.stat." + stat.stat().name().toLowerCase(Locale.ROOT),
                    format(stat.stat(), stat.lowest()), format(stat.stat(), stat.highest()), likely(language, stat)), false));
            stats.add(new Line(tr(language, "familytree.plan.beats", percent(stat.chanceAboveBoth())), true));
        }
        for (var pet : plan.missingStats) stats.add(new Line(tr(language, "familytree.planner.stats.unknown", pet.name()), true));
        if (plan.fixedStats) stats.add(new Line(tr(language, "familytree.planner.stats.camel"), true));
        if (!stats.isEmpty()) sections.add(stats);
        for (VariantOdds.Group group : plan.variants.groups())
            sections.add(List.of(new Line(group(language, plan.foalSpecies.get(0), group), false)));
        String note = noteKey(plan.variants.note());
        if (note != null) sections.add(List.of(new Line(tr(language, note), true)));
        return sections;
    }

    /** Chat lines: the header in yellow and explanations in gray, with percentages indented under each stat. */
    public static List<Text> chat(String language, BreedingPlan plan) {
        List<Text> lines = new ArrayList<>();
        for (List<Line> section : sections(language, plan)) {
            for (Line line : section) {
                Text text = line.muted() ? Text.literal("  ").append(line.text()).color(Text.Color.GRAY) : line.text().copy();
                lines.add(lines.isEmpty() ? text.color(Text.Color.YELLOW) : text);
            }
        }
        return lines;
    }

    private static Text tr(String language, String key, Object... args) {
        return Messages.tr(language, key, args);
    }

    private static Text foal(String language, List<String> species) {
        if (species.isEmpty()) return Text.literal("?");
        if (species.size() == 1) return GameText.species(species.get(0));
        return tr(language, "familytree.planner.foal.either", GameText.species(species.get(0)), GameText.species(species.get(1)));
    }

    private static Text group(String language, String species, VariantOdds.Group group) {
        Text list = Text.empty();
        boolean first = true;
        for (VariantOdds.Chance chance : group.chances()) {
            if (!first) list.append(", ");
            list.append(chance(language, species, group.kind(), chance));
            first = false;
        }
        if (group.unpredictable() > 0) {
            if (!first) list.append(", ");
            list.append(tr(language, "familytree.planner.variant.biome_share", percent(group.unpredictable())));
        }
        return tr(language, "familytree.plan.variant",
                tr(language, "familytree.planner.variant." + group.kind().name().toLowerCase(Locale.ROOT)), list);
    }

    private static Text chance(String language, String species, VariantOdds.Kind kind, VariantOdds.Chance chance) {
        Text text = tr(language, "familytree.plan.odds", label(language, species, kind, chance.variant()), percent(chance.probability()));
        if (kind != VariantOdds.Kind.COAT && kind != VariantOdds.Kind.MARKINGS && VariantRules.rare(species, chance.variant()))
            text.append(Text.literal(" " + GameText.RARE_MARKER).color(Text.Color.GOLD));
        return text;
    }

    private static Text label(String language, String species, VariantOdds.Kind kind, String variant) {
        Text label = switch (kind) {
            case WOOL -> Text.vanilla("color.minecraft." + variant);
            case COAT -> ownOrHumanized(language, "familytree.variant.horse." + variant, variant);
            case MARKINGS -> ownOrHumanized(language, "familytree.variant.markings." + variant, variant);
            case LOOK, VARIANT -> GameText.variant(language, species, variant);
        };
        if (label != null) return label;
        String coat = VariantRules.coatKey(species, variant);
        return coat == null ? Text.literal(VariantRules.humanize(variant)) : ownOrHumanized(language, coat, variant);
    }

    private static Text ownOrHumanized(String language, String key, String variant) {
        return Messages.has(language, key) ? tr(language, key) : Text.literal(VariantRules.humanize(variant));
    }

    private static String noteKey(VariantOdds.Note note) {
        return switch (note) {
            case NONE -> null;
            case FROG_BIOME -> "familytree.planner.note.frog";
            case RABBIT_BIOME -> "familytree.planner.note.rabbit";
            case MODDED -> "familytree.planner.note.modded";
            case UNKNOWN_PARENT -> "familytree.planner.note.unknown_parent";
        };
    }

    /** Chest slots are whole numbers; other stats use the precision of the tree cards. */
    private static String format(Stat stat, double value) {
        return stat == Stat.STRENGTH ? String.valueOf(Math.round(value)) : stat.format(value);
    }

    private static Text likely(String language, BreedingPlan.StatForecast stat) {
        if (stat.likelyLow() == stat.likelyHigh()) return Text.literal(format(stat.stat(), stat.likelyLow()));
        return tr(language, "familytree.planner.range", format(stat.stat(), stat.likelyLow()), format(stat.stat(), stat.likelyHigh()));
    }

    public static Text kinship(String language, Kinship.Relation relation, String first, String second) {
        return switch (relation.kind()) {
            case SAME -> tr(language, "familytree.kin.same", first, second);
            case UNRELATED -> tr(language, "familytree.kin.unrelated", first, second);
            case ANCESTOR -> lineal(language, relation.down(), "parent", "grandparent", "great_grandparent", "ancestor", first, second);
            case DESCENDANT -> lineal(language, relation.up(), "child", "grandchild", "great_grandchild", "descendant", first, second);
            case SIBLING -> tr(language, switch (relation.half()) {
                case FULL -> "familytree.kin.siblings.full";
                case HALF -> "familytree.kin.siblings.half";
                case UNKNOWN -> "familytree.kin.siblings";
            }, first, second);
            case AUNT_OR_UNCLE -> collateral(language, relation.down(), relation.half(), "aunt", first, second);
            case NIECE_OR_NEPHEW -> collateral(language, relation.up(), relation.half(), "niece", first, second);
            case COUSIN -> tr(language, "familytree.kin.cousins", first, second, cousins(language, relation));
        };
    }

    private static Text lineal(String language, int steps, String one, String two, String three, String far, String first, String second) {
        return switch (steps) {
            case 1 -> tr(language, "familytree.kin." + one, first, second);
            case 2 -> tr(language, "familytree.kin." + two, first, second);
            case 3 -> tr(language, "familytree.kin." + three, first, second);
            default -> tr(language, "familytree.kin." + far, first, second, steps);
        };
    }

    private static Text collateral(String language, int steps, Kinship.Half half, String role, String first, String second) {
        if (steps == 2) return tr(language, "familytree.kin." + role + (half == Kinship.Half.HALF ? ".half" : ""), first, second);
        if (steps == 3) return tr(language, "familytree.kin.great_" + role, first, second);
        return tr(language, "familytree.kin.distant_" + role, first, second, steps - 1);
    }

    private static Text cousins(String language, Kinship.Relation relation) {
        int degree = relation.cousinDegree();
        Text kind = degree <= 3 ? tr(language, "familytree.kin.cousin." + degree) : tr(language, "familytree.kin.cousin.n", degree);
        if (relation.half() == Kinship.Half.HALF) kind = tr(language, "familytree.kin.half", kind);
        int removed = relation.removed();
        if (removed == 0) return kind;
        return removed <= 2 ? tr(language, "familytree.kin.removed." + removed, kind) : tr(language, "familytree.kin.removed.n", kind, removed);
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
