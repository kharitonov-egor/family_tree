package com.egakh.familytree.integration;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.data.HorseStats;
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
 * The family details a tooltip mod may show for one tracked animal. It is built for one viewer and holds only
 * records that viewer can open in Family Tree's own screens, so it never carries coordinates or hidden relatives.
 */
public record PetInsight(String name, String parentA, String parentB, int generation,
                         HorseStats stats, HorseStats parentAStats, HorseStats parentBStats) {
    public static final String TAG = "familytree";

    private static final Codec<PetInsight> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(PetInsight::name),
            Codec.STRING.optionalFieldOf("parent_a").forGetter(insight -> Optional.ofNullable(insight.parentA)),
            Codec.STRING.optionalFieldOf("parent_b").forGetter(insight -> Optional.ofNullable(insight.parentB)),
            Codec.INT.optionalFieldOf("generation", 0).forGetter(PetInsight::generation),
            HorseStats.CODEC.optionalFieldOf("stats").forGetter(insight -> Optional.ofNullable(insight.stats)),
            HorseStats.CODEC.optionalFieldOf("parent_a_stats").forGetter(insight -> Optional.ofNullable(insight.parentAStats)),
            HorseStats.CODEC.optionalFieldOf("parent_b_stats").forGetter(insight -> Optional.ofNullable(insight.parentBStats))
    ).apply(instance, (name, a, b, generation, stats, aStats, bStats) -> new PetInsight(name, a.orElse(null), b.orElse(null),
            generation, stats.orElse(null), aStats.orElse(null), bStats.orElse(null))));

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
        return new PetInsight(pet.name(), a == null ? null : a.name(), b == null ? null : b.name(),
                Genealogy.generationOf(pet.id(), lineage(pet, visible)), pet.horseStats(),
                a == null ? null : a.horseStats(), b == null ? null : b.horseStats());
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
