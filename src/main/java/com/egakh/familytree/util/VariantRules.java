package com.egakh.familytree.util;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Variant names and inheritance rules. A variant is the string saved in {@code variant_id}:
 * an enum's serialized name such as {@code blue}, a registry key such as {@code minecraft:warm},
 * {@code coat/markings} for horses, and {@code main/hidden} genes for pandas.
 */
public final class VariantRules {
    public enum Outcome {
        UNKNOWN, INHERITED, MIXED, HIDDEN_GENE, NEW;

        /** The value saved in {@code variant_origin}. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Unknown or future values read as UNKNOWN. */
        public static Outcome parse(String key) {
            for (Outcome outcome : values()) if (outcome.key().equals(key)) return outcome;
            return UNKNOWN;
        }
    }

    public static final String SHEEP = "minecraft:sheep";
    public static final String PANDA = "minecraft:panda";
    private static final Map<String, String> GROUPS = Map.ofEntries(
            Map.entry("minecraft:axolotl", "axolotl"), Map.entry("minecraft:rabbit", "rabbit"),
            Map.entry("minecraft:fox", "fox"), Map.entry("minecraft:mooshroom", "mooshroom"),
            Map.entry("minecraft:llama", "llama"), Map.entry("minecraft:trader_llama", "llama"),
            Map.entry("minecraft:frog", "frog"), Map.entry("minecraft:cow", "climate"),
            Map.entry("minecraft:pig", "climate"), Map.entry("minecraft:chicken", "climate"));
    private static final Set<String> RECESSIVE_PANDA_GENES = Set.of("brown", "weak");

    private VariantRules() {}

    /** Cats, wolves, horses, and parrots show their variant in the portrait, so they get no text label. */
    public static boolean labeled(String species, String variant) {
        if (variant == null || variant.isBlank()) return false;
        return GROUPS.containsKey(species) || SHEEP.equals(species) || PANDA.equals(species) || !species.startsWith("minecraft:");
    }

    /** Translation key for a labeled vanilla variant, or null for sheep, pandas, and modded animals. */
    public static String labelKey(String species, String variant) {
        String group = GROUPS.get(species);
        return group == null || variant == null ? null : "familytree.variant." + group + "." + path(variant);
    }

    public static String pandaGeneKey(String gene) {
        return "familytree.variant.panda." + gene;
    }

    public static boolean rare(String species, String variant) {
        if (variant == null) return false;
        return switch (species) {
            case "minecraft:axolotl" -> "blue".equals(variant);
            case "minecraft:mooshroom" -> "brown".equals(variant);
            case PANDA -> "brown".equals(visiblePandaGene(variant));
            default -> false;
        };
    }

    /** Dye and lightning change sheep and mooshroom colors after birth, so only an origin saved at birth explains them. */
    public static boolean changesAfterBirth(String species) {
        return SHEEP.equals(species) || "minecraft:mooshroom".equals(species);
    }

    public static Outcome outcome(String species, String child, String parentA, String parentB) {
        if (child == null) return Outcome.UNKNOWN;
        if (PANDA.equals(species)) return pandaOutcome(child, parentA, parentB);
        if (child.equals(parentA) || child.equals(parentB)) return Outcome.INHERITED;
        if (parentA == null || parentB == null) return Outcome.UNKNOWN;
        return SHEEP.equals(species) ? Outcome.MIXED : Outcome.NEW;
    }

    public static String noteKey(String species, Outcome outcome) {
        return switch (outcome) {
            case MIXED -> "familytree.variant.note.mixed";
            case HIDDEN_GENE -> "familytree.variant.note.hidden_gene";
            case NEW -> switch (species) {
                case "minecraft:axolotl" -> "familytree.variant.note.axolotl";
                case "minecraft:mooshroom" -> "familytree.variant.note.mooshroom";
                case PANDA -> "familytree.variant.note.panda";
                default -> "familytree.variant.note.new";
            };
            default -> null;
        };
    }

    /** Pandas show a recessive gene only when both genes match; otherwise a recessive main gene looks normal. */
    public static String visiblePandaGene(String variant) {
        String[] genes = pandaGenes(variant);
        if (genes == null) return null;
        if (!RECESSIVE_PANDA_GENES.contains(genes[0])) return genes[0];
        return genes[0].equals(genes[1]) ? genes[0] : "normal";
    }

    public static String[] pandaGenes(String variant) {
        if (variant == null) return null;
        String[] genes = variant.split("/", -1);
        return genes.length == 2 && !genes[0].isEmpty() && !genes[1].isEmpty() ? genes : null;
    }

    public static String humanize(String variant) {
        String text = path(variant).replace('/', ' ').replace('_', ' ').trim();
        return text.isEmpty() ? variant : Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase(Locale.ROOT);
    }

    private static Outcome pandaOutcome(String child, String parentA, String parentB) {
        String[] genes = pandaGenes(child), a = pandaGenes(parentA), b = pandaGenes(parentB);
        if (genes == null || a == null || b == null) return Outcome.UNKNOWN;
        // Each parent passes one of its two genes. Vanilla gives the main gene from one parent and the hidden gene from the other.
        boolean traced = carries(a, genes[0]) && carries(b, genes[1]) || carries(b, genes[0]) && carries(a, genes[1]);
        if (!traced) return Outcome.NEW;
        String shown = visiblePandaGene(child);
        return shown.equals(visiblePandaGene(parentA)) || shown.equals(visiblePandaGene(parentB))
                ? Outcome.INHERITED : Outcome.HIDDEN_GENE;
    }

    private static boolean carries(String[] genes, String gene) {
        return genes[0].equals(gene) || genes[1].equals(gene);
    }

    private static String path(String variant) {
        int colon = variant.indexOf(':');
        return colon >= 0 ? variant.substring(colon + 1) : variant;
    }
}
