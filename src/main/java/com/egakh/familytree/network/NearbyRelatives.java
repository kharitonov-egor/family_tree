package com.egakh.familytree.network;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.RelativesResult;
import com.egakh.familytree.permissions.PetAccess;
import com.egakh.familytree.util.CloseRelation;
import com.egakh.familytree.util.Kinship;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Answers a relatives request for one viewer. The viewer sees the same records as in the Family Tree screens, and
 * only animals loaded within {@link #RANGE} blocks of them. The reply names relatives by entity UUID and never by position.
 */
public final class NearbyRelatives {
    public static final double RANGE = 64.0;
    public static final int MAX_NAME_LENGTH = 64;

    private NearbyRelatives() {}

    /**
     * {@code nearby} maps each animal loaded within {@link #RANGE} blocks of the viewer to its squared distance.
     * {@code lookup} finds any record by ID. Only the visible ancestors of the target and the nearby animals are read,
     * so the cost follows the number of animals in range rather than the size of the history.
     */
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
        Kinship kinship = new Kinship(ancestry(target, candidates.values(), visible));
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

    /** The target, the candidates, and every visible ancestor of them: all that Kinship walks to relate them. */
    private static Map<UUID, AnimalRecord> ancestry(AnimalRecord target, Collection<AnimalRecord> candidates,
                                                    Function<UUID, AnimalRecord> visible) {
        Map<UUID, AnimalRecord> lineage = new HashMap<>();
        ArrayDeque<AnimalRecord> pending = new ArrayDeque<>(candidates);
        pending.add(target);
        while (!pending.isEmpty()) {
            AnimalRecord record = pending.pop();
            if (lineage.putIfAbsent(record.id(), record) != null) continue;
            for (UUID parent : new UUID[]{record.parentA(), record.parentB()}) {
                AnimalRecord found = parent == null || lineage.containsKey(parent) ? null : visible.apply(parent);
                if (found != null) pending.push(found);
            }
        }
        return lineage;
    }

    /** The action bar line after a reply: how many relatives are nearby, or that none are. */
    public static Component summary(RelativesResult result) {
        if (result.total() == 0) return Component.translatable("familytree.relatives.none", result.targetName());
        if (result.total() > result.relatives().size())
            return Component.translatable("familytree.relatives.count_capped", result.targetName(), result.total(), result.relatives().size());
        return Component.translatable("familytree.relatives.count", result.targetName(), result.total());
    }

    private static String shorten(String name) {
        if (name == null) return "";
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
