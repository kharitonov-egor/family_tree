package com.egakh.familytree.plugin.text;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.util.DeathMessageKeys;
import com.egakh.familytree.util.VariantRules;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The plugin's versions of the mod's {@code VariantText} and {@code DeathMessages}, and species names. Game nouns
 * use Minecraft's own keys, so clients show them in their language; Family Tree's words come from {@link Messages}.
 */
public final class GameText {
    public static final String RARE_MARKER = "★";

    private GameText() {}

    public static Text species(String speciesId) {
        int colon = speciesId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : speciesId.substring(0, colon);
        String path = colon < 0 ? speciesId : speciesId.substring(colon + 1);
        if (namespace.equals("minecraft")) return Text.vanilla("entity.minecraft." + path.replace('/', '.'));
        return Text.literal(path.replace('_', ' '));
    }

    public static boolean rare(AnimalRecord pet) {
        return pet != null && VariantRules.rare(pet.speciesId(), pet.variantId());
    }

    /** The variant's name, such as "Blue" or "Pink wool", or null when the species shows no variant text. */
    public static Text variant(String language, String species, String variant) {
        if (!VariantRules.labeled(species, variant)) return null;
        if (VariantRules.SHEEP.equals(species))
            return Messages.tr(language, "familytree.variant.wool", Text.vanilla("color.minecraft." + variant));
        if (VariantRules.PANDA.equals(species)) return panda(language, variant);
        String key = VariantRules.labelKey(species, variant);
        return key != null && Messages.has(language, key) ? Messages.tr(language, key) : Text.literal(VariantRules.humanize(variant));
    }

    public static Text variant(String language, AnimalRecord pet) {
        return pet == null ? null : variant(language, pet.speciesId(), pet.variantId());
    }

    /** Prefers the origin saved at birth. Without one, compares current variants only for species that keep theirs for life. */
    public static VariantRules.Outcome outcome(AnimalRecord pet, Function<UUID, AnimalRecord> lookup) {
        if (pet.variantOrigin() != null) return VariantRules.Outcome.parse(pet.variantOrigin());
        if (VariantRules.changesAfterBirth(pet.speciesId())) return VariantRules.Outcome.UNKNOWN;
        AnimalRecord a = parent(pet.parentA(), lookup), b = parent(pet.parentB(), lookup);
        if (a == null && b == null) return VariantRules.Outcome.UNKNOWN;
        return VariantRules.outcome(pet.speciesId(), pet.variantId(), a == null ? null : a.variantId(), b == null ? null : b.variantId());
    }

    public static Text variantParents(String language, AnimalRecord pet, Function<UUID, AnimalRecord> lookup) {
        AnimalRecord a = parent(pet.parentA(), lookup), b = parent(pet.parentB(), lookup);
        Text first = a == null ? null : variant(language, a), second = b == null ? null : variant(language, b);
        if (first == null && second == null) return null;
        return Messages.tr(language, "familytree.variant.parents",
                first == null ? Text.literal("?") : first, second == null ? Text.literal("?") : second);
    }

    public static Text variantNote(String language, AnimalRecord pet, Function<UUID, AnimalRecord> lookup) {
        String key = VariantRules.noteKey(pet.speciesId(), outcome(pet, lookup));
        return key == null ? null : Messages.tr(language, key);
    }

    /** The death message for a deceased animal, or null when no cause was recorded. */
    public static Text death(String language, AnimalRecord pet) {
        AnimalRecord.DeathCause cause = pet.deathCause();
        if (cause == null || cause.attacker() == null && cause.msgId() == null) return null;
        Text name = Text.literal(pet.name());
        if (cause.msgId() == null) return Messages.tr(language, "familytree.node.slain_by", cause.attacker());
        String key = DeathMessageKeys.key(cause.msgId(), cause.attacker() != null,
                id -> VanillaLang.has(id) ? VanillaLang.english(id) : null);
        return cause.attacker() == null ? Text.vanilla(key, name) : Text.vanilla(key, name, Text.literal(cause.attacker()));
    }

    /** One line such as "Rex suffocated in a wall on day 12", or null when no cause was recorded. */
    public static Text deathLine(String language, AnimalRecord pet) {
        Text message = death(language, pet);
        if (message == null || pet.deathWorldDay() == null) return message;
        return Messages.tr(language, "familytree.node.died_on", message, pet.deathWorldDay());
    }

    private static Text panda(String language, String variant) {
        String[] genes = VariantRules.pandaGenes(variant);
        if (genes == null) return Text.literal(VariantRules.humanize(variant));
        String shown = VariantRules.visiblePandaGene(variant);
        Set<String> carried = new LinkedHashSet<>(List.of(genes));
        carried.remove(shown);
        if (carried.isEmpty()) return gene(language, shown);
        Text hidden = Text.empty();
        boolean first = true;
        for (String gene : carried) {
            if (!first) hidden.append(", ");
            hidden.append(gene(language, gene));
            first = false;
        }
        return Messages.tr(language, "familytree.variant.panda.carries", gene(language, shown), hidden);
    }

    private static Text gene(String language, String gene) {
        String key = VariantRules.pandaGeneKey(gene);
        return Messages.has(language, key) ? Messages.tr(language, key) : Text.literal(VariantRules.humanize(gene));
    }

    private static AnimalRecord parent(UUID id, Function<UUID, AnimalRecord> lookup) {
        return id == null ? null : lookup.apply(id);
    }
}
