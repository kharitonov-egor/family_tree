package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** QA B2: pruning leaves the removed parent's ID on its children, and the screens mark that gap. */
class MissingParentsTest {
    @Test void aParentIdWithoutARecordIsMissing() {
        Map<UUID, AnimalRecord> records = new HashMap<>();
        AnimalRecord mother = sheep(null, null), father = sheep(null, null);
        AnimalRecord lamb = sheep(mother.id(), father.id());
        AnimalRecord grandchild = sheep(lamb.id(), null);
        for (AnimalRecord record : new AnimalRecord[]{mother, father, lamb, grandchild}) records.put(record.id(), record);

        assertFalse(MissingParents.hasMissingParent(lamb, records::get));
        assertFalse(MissingParents.hasMissingAncestor(grandchild, records::get));
        records.remove(father.id());
        assertTrue(MissingParents.hasMissingParent(lamb, records::get));
        assertFalse(MissingParents.hasMissingParent(grandchild, records::get), "Only the lamb lost a parent");
        assertTrue(MissingParents.hasMissingAncestor(grandchild, records::get));
        assertFalse(MissingParents.hasMissingParent(null, records::get));
    }

    @Test void aLoopInTheRecordsEnds() {
        Map<UUID, AnimalRecord> records = new HashMap<>();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        records.put(a, new AnimalRecord(a, "minecraft:sheep", "A", true, b, null, 0, 0, false, null, null, null, null, null, null, null));
        records.put(b, new AnimalRecord(b, "minecraft:sheep", "B", true, a, null, 0, 0, false, null, null, null, null, null, null, null));
        assertFalse(MissingParents.hasMissingAncestor(records.get(a), records::get));
    }

    private static AnimalRecord sheep(UUID parentA, UUID parentB) {
        return new AnimalRecord(UUID.randomUUID(), "minecraft:sheep", "Sheep", true, parentA, parentB,
                0, 0, true, 5L, 5L, null, null, null, null, null);
    }
}
