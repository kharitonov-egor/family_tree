package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Genealogy generation numbers, derived entirely from the parent graph.
 * A pet with no tracked parents is generation 1 (a founder); each step down
 * the lineage adds one. Nothing here is persisted, so it is safe to change.
 */
public final class Genealogy {

    private Genealogy() {}

    public static Map<UUID, Integer> computeGenerations(Map<UUID, AnimalRecord> records) {
        Map<UUID, Integer> memo = new HashMap<>();
        for (UUID id : records.keySet()) {
            resolve(id, records, memo, new HashSet<>());
        }
        return memo;
    }

    public static int generationOf(UUID id, Map<UUID, AnimalRecord> records) {
        return resolve(id, records, new HashMap<>(), new HashSet<>());
    }

    private static int resolve(UUID id, Map<UUID, AnimalRecord> records,
                               Map<UUID, Integer> memo, Set<UUID> stack) {
        if (id == null) return 0;
        Integer cached = memo.get(id);
        if (cached != null) return cached;
        AnimalRecord record = records.get(id);
        if (record == null) return 0;
        if (!stack.add(id)) {
            return 1;
        }

        int parentGeneration = Math.max(
                parentGeneration(record.parentA(), records, memo, stack),
                parentGeneration(record.parentB(), records, memo, stack));
        int generation = parentGeneration + 1;

        stack.remove(id);
        memo.put(id, generation);
        return generation;
    }

    private static int parentGeneration(UUID parentId, Map<UUID, AnimalRecord> records,
                                        Map<UUID, Integer> memo, Set<UUID> stack) {
        if (parentId == null || !records.containsKey(parentId)) return 0;
        return resolve(parentId, records, memo, stack);
    }
}
