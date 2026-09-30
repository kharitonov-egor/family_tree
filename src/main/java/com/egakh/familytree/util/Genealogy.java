package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Genealogy generation numbers, derived entirely from the parent graph.
 * A pet with no tracked parents is generation 1 (a founder); each step down
 * the lineage adds one. Nothing here is persisted, so it is safe to change.
 */
public final class Genealogy {

    private Genealogy() {}

    /** Checks both proposed parents against the current graph without changing any records. */
    public static boolean wouldCreateCycle(UUID childId, UUID parentA, UUID parentB,
                                           Function<UUID, AnimalRecord> lookup) {
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        if (parentA != null) pending.add(parentA);
        if (parentB != null) pending.add(parentB);
        Set<UUID> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            UUID id = pending.removeLast();
            if (id.equals(childId)) return true;
            if (!visited.add(id)) continue;
            AnimalRecord record = lookup.apply(id);
            if (record == null) continue;
            if (record.parentA() != null) pending.add(record.parentA());
            if (record.parentB() != null) pending.add(record.parentB());
        }
        return false;
    }

    public static Map<UUID, Integer> computeGenerations(Map<UUID, AnimalRecord> records) {
        Map<UUID, Integer> memo = new HashMap<>();
        for (UUID id : records.keySet()) {
            resolve(id, records, memo);
        }
        return memo;
    }

    public static int generationOf(UUID id, Map<UUID, AnimalRecord> records) {
        return resolve(id, records, new HashMap<>());
    }

    private static int resolve(UUID id, Map<UUID, AnimalRecord> records, Map<UUID, Integer> memo) {
        if (id == null || !records.containsKey(id)) return 0;
        record Visit(UUID id, boolean finish) {}
        ArrayDeque<Visit> pending = new ArrayDeque<>();
        Set<UUID> visiting = new HashSet<>();
        pending.push(new Visit(id, false));
        while (!pending.isEmpty()) {
            Visit visit = pending.pop();
            if (memo.containsKey(visit.id())) continue;
            AnimalRecord record = records.get(visit.id());
            if (record == null) continue;
            if (visit.finish()) {
                int a = record.parentA() == null ? 0 : memo.getOrDefault(record.parentA(), 0);
                int b = record.parentB() == null ? 0 : memo.getOrDefault(record.parentB(), 0);
                memo.put(visit.id(), Math.max(a, b) + 1);
                visiting.remove(visit.id());
            } else if (visiting.add(visit.id())) {
                pending.push(new Visit(visit.id(), true));
                if (record.parentB() != null && !visiting.contains(record.parentB()))
                    pending.push(new Visit(record.parentB(), false));
                if (record.parentA() != null && !visiting.contains(record.parentA()))
                    pending.push(new Visit(record.parentA(), false));
            }
        }
        return memo.getOrDefault(id, 0);
    }
}
