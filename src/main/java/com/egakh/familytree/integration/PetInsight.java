package com.egakh.familytree.integration;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.permissions.PetAccess;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import com.egakh.familytree.util.Genealogy;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The family details a tooltip mod may show for one tracked animal: the animal and its parents as the viewer's
 * Family Tree screens would show them. Hidden parents are absent, and no record carries a last known position.
 */
public record PetInsight(AnimalRecord pet, AnimalRecord parentA, AnimalRecord parentB, int generation) {
    public static final String TAG = "familytree";

    private static final Codec<PetInsight> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AnimalRecord.CODEC.fieldOf("pet").forGetter(PetInsight::pet),
            AnimalRecord.CODEC.optionalFieldOf("parent_a").forGetter(insight -> Optional.ofNullable(insight.parentA)),
            AnimalRecord.CODEC.optionalFieldOf("parent_b").forGetter(insight -> Optional.ofNullable(insight.parentB)),
            Codec.INT.optionalFieldOf("generation", 0).forGetter(PetInsight::generation)
    ).apply(instance, (pet, a, b, generation) -> new PetInsight(pet, a.orElse(null), b.orElse(null), generation)));

    /** Looks up a pet for this player with the same view rules as the Family Tree browser. */
    public static PetInsight forViewer(ServerPlayer viewer, UUID petId) {
        FamilyTreeState state = FamilyTreeState.get(viewer.level().getServer());
        if (state.readOnly()) return null;
        boolean viewAll = FamilyTreeServerSettings.mayViewAll(viewer);
        return of(petId, id -> {
            AnimalRecord record = state.get(id);
            return PetAccess.canView(record, viewer.getUUID(), viewAll) ? record : null;
        });
    }

    /** {@code visible} returns only the records the viewer may see. Hidden parents count as unknown. */
    public static PetInsight of(UUID petId, Function<UUID, AnimalRecord> visible) {
        AnimalRecord pet = petId == null ? null : visible.apply(petId);
        if (pet == null) return null;
        AnimalRecord a = pet.parentA() == null ? null : visible.apply(pet.parentA());
        AnimalRecord b = pet.parentB() == null ? null : visible.apply(pet.parentB());
        return new PetInsight(withoutPosition(pet), withoutPosition(a), withoutPosition(b),
                Genealogy.generationOf(pet.id(), lineage(pet, visible)));
    }

    /** Resolves the pet's parents for the shared stat and variant text. */
    public AnimalRecord parent(UUID id) {
        if (id == null) return null;
        if (parentA != null && id.equals(parentA.id())) return parentA;
        if (parentB != null && id.equals(parentB.id())) return parentB;
        return null;
    }

    private static AnimalRecord withoutPosition(AnimalRecord record) {
        return record == null ? null : PetAccess.forViewer(record, null, false);
    }

    private static Map<UUID, AnimalRecord> lineage(AnimalRecord pet, Function<UUID, AnimalRecord> visible) {
        Map<UUID, AnimalRecord> ancestors = new HashMap<>();
        ArrayDeque<AnimalRecord> pending = new ArrayDeque<>();
        pending.add(pet);
        while (!pending.isEmpty()) {
            AnimalRecord record = pending.removeLast();
            if (ancestors.putIfAbsent(record.id(), record) != null) continue;
            for (UUID parent : new UUID[]{record.parentA(), record.parentB()}) {
                AnimalRecord found = parent == null || ancestors.containsKey(parent) ? null : visible.apply(parent);
                if (found != null) pending.add(found);
            }
        }
        return ancestors;
    }

    public static void write(CompoundTag data, PetInsight insight) {
        if (insight == null) return;
        CODEC.encodeStart(NbtOps.INSTANCE, insight).result().ifPresent(tag -> data.put(TAG, tag));
    }

    public static PetInsight read(CompoundTag data) {
        Tag tag = data == null ? null : data.get(TAG);
        return tag == null ? null : CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
    }
}
