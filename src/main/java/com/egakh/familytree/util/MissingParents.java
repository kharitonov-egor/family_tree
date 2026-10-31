package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Parent links whose record the lookup can't find. Pruning keeps a removed parent's ID on its children, so kinship
 * still sees shared parents; a viewer who can't open another player's animal sees the same gap.
 */
public final class MissingParents {
    private MissingParents() {}

    public static boolean hasMissingParent(AnimalRecord pet, Function<UUID, AnimalRecord> lookup) {
        return pet != null && (missing(pet.parentA(), lookup) || missing(pet.parentB(), lookup));
    }

    public static boolean hasMissingAncestor(AnimalRecord pet, Function<UUID, AnimalRecord> lookup) {
        Deque<AnimalRecord> pending = new ArrayDeque<>();
        Set<UUID> seen = new HashSet<>();
        if (pet != null) pending.push(pet);
        while (!pending.isEmpty()) {
            AnimalRecord next = pending.pop();
            if (!seen.add(next.id())) continue;
            for (UUID parentId : new UUID[]{next.parentA(), next.parentB()}) {
                if (parentId == null) continue;
                AnimalRecord parent = lookup.apply(parentId);
                if (parent == null) return true;
                pending.push(parent);
            }
        }
        return false;
    }

    private static boolean missing(UUID id, Function<UUID, AnimalRecord> lookup) {
        return id != null && lookup.apply(id) == null;
    }
}
