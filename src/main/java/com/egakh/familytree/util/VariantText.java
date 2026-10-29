package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Player-facing variant names shared by cards, details, exports, and {@code /familytree info}. */
public final class VariantText {
    public static final String RARE_MARKER = "★";
    public static final int RARE_COLOR = 0xFFFFC94A;

    private VariantText() {}

    public static boolean has(AnimalRecord pet) {
        return pet != null && VariantRules.labeled(pet.speciesId(), pet.variantId());
    }

    public static boolean rare(AnimalRecord pet) {
        return pet != null && VariantRules.rare(pet.speciesId(), pet.variantId());
    }

    /** The variant's name, such as "Blue" or "Pink wool", or null when cards show no variant text for this species. */
    public static MutableComponent label(String species, String variant) {
        if (!VariantRules.labeled(species, variant)) return null;
        if (VariantRules.SHEEP.equals(species))
            return Component.translatable("familytree.variant.wool",
                    Component.translatableWithFallback("color.minecraft." + variant, VariantRules.humanize(variant)));
        if (VariantRules.PANDA.equals(species)) return panda(variant);
        String key = VariantRules.labelKey(species, variant);
        return key == null ? Component.literal(VariantRules.humanize(variant))
                : Component.translatableWithFallback(key, VariantRules.humanize(variant));
    }

    public static MutableComponent label(AnimalRecord pet) {
        return pet == null ? null : label(pet.speciesId(), pet.variantId());
    }

    /** Prefers the origin saved at birth. Without one, compares current variants only for species that keep theirs for life. */
    public static VariantRules.Outcome outcome(AnimalRecord pet, Function<UUID, AnimalRecord> lookup) {
        if (pet.variantOrigin() != null) return VariantRules.Outcome.parse(pet.variantOrigin());
        if (VariantRules.changesAfterBirth(pet.speciesId())) return VariantRules.Outcome.UNKNOWN;
        AnimalRecord a = parent(pet.parentA(), lookup), b = parent(pet.parentB(), lookup);
        if (a == null && b == null) return VariantRules.Outcome.UNKNOWN;
        return VariantRules.outcome(pet.speciesId(), pet.variantId(),
                a == null ? null : a.variantId(), b == null ? null : b.variantId());
    }

    public static Component parents(AnimalRecord pet, Function<UUID, AnimalRecord> lookup) {
        AnimalRecord a = parent(pet.parentA(), lookup), b = parent(pet.parentB(), lookup);
        Component first = a == null ? null : label(a), second = b == null ? null : label(b);
        if (first == null && second == null) return null;
        return Component.translatable("familytree.variant.parents",
                first == null ? Component.literal("?") : first, second == null ? Component.literal("?") : second);
    }

    public static Component note(AnimalRecord pet, Function<UUID, AnimalRecord> lookup) {
        String key = VariantRules.noteKey(pet.speciesId(), outcome(pet, lookup));
        return key == null ? null : Component.translatable(key);
    }

    private static MutableComponent panda(String variant) {
        String[] genes = VariantRules.pandaGenes(variant);
        if (genes == null) return Component.literal(VariantRules.humanize(variant));
        String shown = VariantRules.visiblePandaGene(variant);
        Set<String> carried = new LinkedHashSet<>(java.util.List.of(genes));
        carried.remove(shown);
        if (carried.isEmpty()) return gene(shown);
        MutableComponent hidden = Component.empty();
        for (String gene : carried) {
            if (!hidden.getSiblings().isEmpty()) hidden.append(", ");
            hidden.append(gene(gene));
        }
        return Component.translatable("familytree.variant.panda.carries", gene(shown), hidden);
    }

    private static MutableComponent gene(String gene) {
        return Component.translatableWithFallback(VariantRules.pandaGeneKey(gene), VariantRules.humanize(gene));
    }

    private static AnimalRecord parent(UUID id, Function<UUID, AnimalRecord> lookup) {
        return id == null ? null : lookup.apply(id);
    }
}
