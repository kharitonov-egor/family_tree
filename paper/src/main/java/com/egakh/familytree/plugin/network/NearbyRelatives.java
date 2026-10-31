package com.egakh.familytree.plugin.network;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.permissions.PetAccess;
import com.egakh.familytree.plugin.network.Payloads.RelativesResult;
import com.egakh.familytree.util.CloseRelation;
import com.egakh.familytree.util.Kinship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * The mod's {@code NearbyRelatives}: answers a relatives request for one viewer from the records they may view and
 * the animals loaded within {@link #RANGE} blocks of them. The reply names relatives by entity UUID, never by position.
 */
public final class NearbyRelatives {
    public static final double RANGE = 64.0;
    public static final int MAX_NAME_LENGTH = 64;

    private NearbyRelatives() {}

    /** {@code nearby} maps each animal loaded near the viewer to its squared distance; {@code lookup} finds any record. */
    public static RelativesResult answer(UUID requestId, UUID targetId, UUID viewer, boolean viewAll,
                                         Function<UUID, AnimalRecord> lookup, Map<UUID, Double> nearby) {
        Function<UUID, AnimalRecord> visible = id -> {
            AnimalRecord record = id == null ? null : lookup.apply(id);
            return PetAccess.canView(record, viewer, viewAll) ? record : null;
        };
        AnimalRecord target = visible.apply(targetId);
        Double targetDistance = targetId == null ? null : nearby.get(targetId);
        if (target == null || targetDistance == null || targetDistance > RANGE * RANGE)
            return RelativesResult.empty(requestId, targetId, RelativesResult.Status.NOT_TRACKED);
        Map<UUID, AnimalRecord> candidates = new HashMap<>();
        for (Map.Entry<UUID, Double> loaded : nearby.entrySet()) {
            AnimalRecord record = loaded.getValue() <= RANGE * RANGE ? visible.apply(loaded.getKey()) : null;
            if (record != null && !record.id().equals(targetId)) candidates.put(record.id(), record);
        }
        Kinship kinship = new Kinship(ancestry(target, candidates.values(), lookup, visible));
        record Found(AnimalRecord record, CloseRelation relation, double distanceSq) {}
        List<Found> found = new ArrayList<>();
        for (AnimalRecord record : candidates.values()) {
            CloseRelation relation = CloseRelation.of(kinship.relation(record.id(), targetId));
            if (relation != null) found.add(new Found(record, relation, nearby.get(record.id())));
        }
        found.sort(Comparator.comparingInt((Found relative) -> relative.relation().ordinal())
                .thenComparingDouble(Found::distanceSq).thenComparing(relative -> relative.record().id()));
        List<RelativesResult.Relative> shown = found.stream().limit(RelativesResult.MAX_RELATIVES)
                .map(relative -> new RelativesResult.Relative(relative.record().id(), relative.relation().ordinal(),
                        shorten(relative.record().name()))).toList();
        return new RelativesResult(requestId, targetId, RelativesResult.Status.FOUND.ordinal(), shorten(target.name()),
                found.size(), shown);
    }

    /**
     * The target, the candidates, and every visible ancestor of them: all that Kinship walks to relate them. A link to a
     * record the viewer can't view is dropped, so two animals related only through someone else's hidden pet stay unlit.
     * A parent with no record at all stays linked, as Kinship expects.
     */
    private static Map<UUID, AnimalRecord> ancestry(AnimalRecord target, Collection<AnimalRecord> candidates,
                                                    Function<UUID, AnimalRecord> lookup, Function<UUID, AnimalRecord> visible) {
        Map<UUID, AnimalRecord> lineage = new HashMap<>();
        ArrayDeque<AnimalRecord> pending = new ArrayDeque<>(candidates);
        pending.add(target);
        while (!pending.isEmpty()) {
            AnimalRecord record = pending.pop();
            if (lineage.containsKey(record.id())) continue;
            UUID[] parents = {record.parentA(), record.parentB()};
            for (int i = 0; i < parents.length; i++) {
                if (parents[i] == null) continue;
                AnimalRecord parent = visible.apply(parents[i]);
                if (parent != null) {
                    if (!lineage.containsKey(parent.id())) pending.push(parent);
                } else if (lookup.apply(parents[i]) != null) {
                    parents[i] = null;
                }
            }
            lineage.put(record.id(), withParents(record, parents[0], parents[1]));
        }
        return lineage;
    }

    /** A copy with other parent links, so the stored record never changes. Kinship reads only IDs and parent links. */
    private static AnimalRecord withParents(AnimalRecord record, UUID parentA, UUID parentB) {
        if (Objects.equals(parentA, record.parentA()) && Objects.equals(parentB, record.parentB())) return record;
        return new AnimalRecord(record.id(), record.speciesId(), record.originalName(), record.nameAutogenerated(), parentA, parentB,
                record.birthWorldDay(), record.birthEpochMillis(), record.deceased(), record.deathWorldDay(),
                record.deathEpochMillis(), record.ownerId(), record.ownerName(), record.variantId(), null, null);
    }

    private static String shorten(String name) {
        if (name == null) return "";
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
