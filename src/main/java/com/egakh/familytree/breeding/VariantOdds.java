package com.egakh.familytree.breeding;

import com.egakh.familytree.util.VariantRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Variant odds for a planned foal, from each species' {@code getBreedOffspring}. The rules are identical on every
 * supported target from 1.20.1 to 26.3. Variants use the strings saved in {@code variant_id}.
 */
public final class VariantOdds {
    public enum Kind { WOOL, COAT, MARKINGS, LOOK, VARIANT }

    public record Chance(String variant, double probability) {}

    /** {@code unpredictable} is the share decided by something Family Tree can't know, such as the biome. */
    public record Group(Kind kind, List<Chance> chances, double unpredictable) {}

    public enum Note { NONE, EITHER_PARENT, FROG_BIOME, RABBIT_BIOME, MODDED, UNKNOWN_PARENT }

    public record Result(List<Group> groups, Note note) {
        static final Result NOTHING = new Result(List.of(), Note.NONE);

        static Result of(Group group, Note note) {
            return new Result(List.of(group), note);
        }
    }

    /** The two-dye shapeless recipes in every supported version's data. Other pairs pick a parent's color. */
    private static final Map<Set<String>, String> DYE_MIXES = Map.of(
            Set.of("black", "white"), "gray",
            Set.of("blue", "green"), "cyan",
            Set.of("blue", "red"), "purple",
            Set.of("blue", "white"), "light_blue",
            Set.of("gray", "white"), "light_gray",
            Set.of("green", "white"), "lime",
            Set.of("pink", "purple"), "magenta",
            Set.of("red", "white"), "pink",
            Set.of("red", "yellow"), "orange");
    public static final List<String> HORSE_COATS = List.of("white", "creamy", "chestnut", "brown", "black", "gray", "dark_brown");
    public static final List<String> HORSE_MARKINGS = List.of("none", "white", "white_field", "white_dots", "black_dots");
    public static final List<String> PANDA_GENES = List.of("normal", "lazy", "worried", "playful", "brown", "weak", "aggressive");
    /** {@code Panda.Gene.getRandom} draws {@code nextInt(16)}; these are the counts of draws that give each gene. */
    private static final Map<String, Integer> PANDA_RANDOM_WEIGHTS = Map.of(
            "lazy", 1, "worried", 1, "playful", 1, "aggressive", 1, "weak", 5, "brown", 2, "normal", 5);
    private static final double PANDA_MUTATION = 1.0 / 32;
    public static final double AXOLOTL_BLUE = 1.0 / 1200;
    public static final double MOOSHROOM_FLIP = 1.0 / 1024;
    public static final double RABBIT_BIOME = 1.0 / 20;
    private static final Set<String> EITHER_PARENT = Set.of("minecraft:fox", "minecraft:llama", "minecraft:trader_llama",
            "minecraft:cow", "minecraft:pig", "minecraft:chicken");

    private VariantOdds() {}

    /** Parents of different species only breed as horse and donkey, or as llama and trader llama. */
    public static Result forPair(String speciesA, String speciesB, String variantA, String variantB) {
        if (speciesA == null || speciesB == null) return Result.NOTHING;
        if (!speciesA.equals(speciesB))
            return BreedingPair.llamaLike(speciesA) && BreedingPair.llamaLike(speciesB)
                    ? eitherParent(Kind.VARIANT, variantA, variantB) : Result.NOTHING;
        String species = speciesA;
        switch (species) {
            case "minecraft:frog":
                return new Result(List.of(), Note.FROG_BIOME);
            case "minecraft:cat":
            case "minecraft:wolf":
                return variantA == null && variantB == null ? Result.NOTHING : new Result(List.of(), Note.EITHER_PARENT);
            default:
                break;
        }
        if (!species.startsWith("minecraft:")) return new Result(List.of(), Note.MODDED);
        boolean hasRule = VariantRules.SHEEP.equals(species) || VariantRules.PANDA.equals(species)
                || "minecraft:horse".equals(species) || "minecraft:axolotl".equals(species)
                || "minecraft:mooshroom".equals(species) || "minecraft:rabbit".equals(species) || EITHER_PARENT.contains(species);
        if (!hasRule) return Result.NOTHING;
        // Cows, pigs, and chickens have no variants before 1.21.5, so neither parent has one there.
        if (variantA == null && variantB == null && EITHER_PARENT.contains(species)) return Result.NOTHING;
        if (variantA == null || variantB == null) return new Result(List.of(), Note.UNKNOWN_PARENT);
        return switch (species) {
            case VariantRules.SHEEP -> Result.of(new Group(Kind.WOOL, wool(variantA, variantB), 0), Note.NONE);
            case VariantRules.PANDA -> panda(variantA, variantB);
            case "minecraft:horse" -> horse(variantA, variantB);
            case "minecraft:axolotl" -> Result.of(new Group(Kind.VARIANT, sorted(merge(
                    new Chance(variantA, (1 - AXOLOTL_BLUE) / 2), new Chance(variantB, (1 - AXOLOTL_BLUE) / 2),
                    new Chance("blue", AXOLOTL_BLUE))), 0), Note.NONE);
            case "minecraft:mooshroom" -> Result.of(new Group(Kind.VARIANT, mooshroom(variantA, variantB), 0), Note.NONE);
            case "minecraft:rabbit" -> Result.of(new Group(Kind.VARIANT, sorted(merge(
                    new Chance(variantA, (1 - RABBIT_BIOME) / 2), new Chance(variantB, (1 - RABBIT_BIOME) / 2))),
                    RABBIT_BIOME), Note.RABBIT_BIOME);
            default -> eitherParent(Kind.VARIANT, variantA, variantB);
        };
    }

    public static String mixedWool(String a, String b) {
        return DYE_MIXES.get(Set.of(a, b));
    }

    private static List<Chance> wool(String a, String b) {
        String mixed = a.equals(b) ? null : mixedWool(a, b);
        if (mixed != null) return List.of(new Chance(mixed, 1));
        return sorted(merge(new Chance(a, 0.5), new Chance(b, 0.5)));
    }

    private static List<Chance> mooshroom(String a, String b) {
        if (!a.equals(b)) return sorted(merge(new Chance(a, 0.5), new Chance(b, 0.5)));
        String other = "brown".equals(a) ? "red" : "brown";
        return sorted(List.of(new Chance(a, 1 - MOOSHROOM_FLIP), new Chance(other, MOOSHROOM_FLIP)));
    }

    private static Result eitherParent(Kind kind, String a, String b) {
        if (a == null || b == null) return new Result(List.of(), Note.UNKNOWN_PARENT);
        return Result.of(new Group(kind, sorted(merge(new Chance(a, 0.5), new Chance(b, 0.5))), 0), Note.NONE);
    }

    /** {@code nextInt(9)} keeps a parent's coat on 0-3 or 4-7 and picks any coat on 8; markings use {@code nextInt(5)}. */
    private static Result horse(String a, String b) {
        String[] first = split(a), second = split(b);
        if (first == null || second == null) return new Result(List.of(), Note.UNKNOWN_PARENT);
        List<Chance> coats = new ArrayList<>(), markings = new ArrayList<>();
        for (String coat : HORSE_COATS) coats.add(new Chance(coat, (coat.equals(first[0]) ? 4.0 / 9 : 0)
                + (coat.equals(second[0]) ? 4.0 / 9 : 0) + 1.0 / 9 / HORSE_COATS.size()));
        for (String marking : HORSE_MARKINGS) markings.add(new Chance(marking, (marking.equals(first[1]) ? 2.0 / 5 : 0)
                + (marking.equals(second[1]) ? 2.0 / 5 : 0) + 1.0 / 5 / HORSE_MARKINGS.size()));
        return new Result(List.of(new Group(Kind.COAT, sorted(coats), 0), new Group(Kind.MARKINGS, sorted(markings), 0)), Note.NONE);
    }

    /**
     * {@code setGeneFromParents}: the main gene comes from one parent and the hidden gene from the other, each a
     * random one of that parent's two genes. Then each gene is replaced by {@code Gene.getRandom} 1 time in 32.
     * Outcomes are grouped by look, so "normal/brown" and "brown/normal" count together.
     */
    private static Result panda(String a, String b) {
        String[] first = VariantRules.pandaGenes(a), second = VariantRules.pandaGenes(b);
        if (first == null || second == null) return new Result(List.of(), Note.UNKNOWN_PARENT);
        Map<String, Double> fromFirst = genePick(first), fromSecond = genePick(second);
        Map<String, Double> genotypes = new LinkedHashMap<>();
        for (int order = 0; order < 2; order++) {
            Map<String, Double> main = mutate(order == 0 ? fromFirst : fromSecond);
            Map<String, Double> hidden = mutate(order == 0 ? fromSecond : fromFirst);
            for (var m : main.entrySet())
                for (var h : hidden.entrySet())
                    genotypes.merge(m.getKey() + "/" + h.getKey(), 0.5 * m.getValue() * h.getValue(), Double::sum);
        }
        Map<String, Chance> looks = new LinkedHashMap<>();
        for (var genotype : genotypes.entrySet()) {
            String key = pandaLook(genotype.getKey());
            Chance existing = looks.get(key);
            looks.put(key, new Chance(existing == null ? genotype.getKey() : existing.variant(),
                    (existing == null ? 0 : existing.probability()) + genotype.getValue()));
        }
        return Result.of(new Group(Kind.LOOK, sorted(new ArrayList<>(looks.values())), 0), Note.NONE);
    }

    /** Visible gene plus the other gene it carries, the parts the variant label shows. */
    static String pandaLook(String genotype) {
        String[] genes = VariantRules.pandaGenes(genotype);
        String shown = VariantRules.visiblePandaGene(genotype);
        java.util.TreeSet<String> carried = new java.util.TreeSet<>(List.of(genes));
        carried.remove(shown);
        return shown + "|" + String.join(",", carried);
    }

    private static Map<String, Double> genePick(String[] genes) {
        Map<String, Double> pick = new LinkedHashMap<>();
        pick.merge(genes[0], 0.5, Double::sum);
        pick.merge(genes[1], 0.5, Double::sum);
        return pick;
    }

    private static Map<String, Double> mutate(Map<String, Double> inherited) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (var gene : inherited.entrySet()) result.merge(gene.getKey(), gene.getValue() * (1 - PANDA_MUTATION), Double::sum);
        for (var gene : PANDA_RANDOM_WEIGHTS.entrySet()) result.merge(gene.getKey(), PANDA_MUTATION * gene.getValue() / 16.0, Double::sum);
        return result;
    }

    private static String[] split(String horseVariant) {
        String[] parts = horseVariant.split("/", -1);
        return parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty() ? parts : null;
    }

    private static List<Chance> merge(Chance... chances) {
        Map<String, Double> merged = new LinkedHashMap<>();
        for (Chance chance : chances) merged.merge(chance.variant(), chance.probability(), Double::sum);
        List<Chance> list = new ArrayList<>();
        merged.forEach((variant, probability) -> list.add(new Chance(variant, probability)));
        return list;
    }

    private static List<Chance> sorted(List<Chance> chances) {
        List<Chance> list = new ArrayList<>(chances);
        list.removeIf(chance -> chance.probability() <= 0);
        list.sort(Comparator.comparingDouble(Chance::probability).reversed().thenComparing(Chance::variant));
        return list;
    }
}
