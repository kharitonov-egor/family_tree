package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Proof-of-bug tests from the 1.4.0 code review. They fail on d689590. */
class ReviewKinshipProofTest {
    private final Map<UUID, AnimalRecord> records = new HashMap<>();

    private AnimalRecord horse(String name, AnimalRecord a, AnimalRecord b) {
        AnimalRecord record = new AnimalRecord(UUID.randomUUID(), "minecraft:horse", name, false,
                a == null ? null : a.id(), b == null ? null : b.id(), 0, 0, false, null, null, null, null, null, null, null);
        records.put(record.id(), record);
        return record;
    }

    @Test
    void coancestryOfGrandparentAndInbredGrandchild() {
        AnimalRecord a = horse("A", null, null);
        AnimalRecord b = horse("B", a, null);
        AnimalRecord c = horse("C", a, b);
        assertEquals(0.375, new Kinship(records).coancestry(a.id(), c.id()), 1e-9);
    }

    @Test
    void earlierQueryDoesNotPoisonTheCache() {
        AnimalRecord a = horse("A", null, null);
        AnimalRecord b = horse("B", a, null);
        AnimalRecord c = horse("C", a, b);
        Kinship kinship = new Kinship(records);
        kinship.coancestry(a.id(), c.id());
        assertEquals(0.25, kinship.coancestry(a.id(), b.id()), 1e-9);
    }

    @Test
    void parentAndChildWithBothParentsKnownAlsoPoisoned() {
        AnimalRecord a = horse("A", null, null);
        AnimalRecord d = horse("D", null, null);
        AnimalRecord b = horse("B", a, d);
        AnimalRecord c = horse("C", a, b);
        Kinship kinship = new Kinship(records);
        assertEquals(0.375, kinship.coancestry(a.id(), c.id()), 1e-9);
        assertEquals(0.25, kinship.coancestry(a.id(), b.id()), 1e-9);
    }
}
