package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GenealogyTest {
    private final Map<UUID, AnimalRecord> records = new HashMap<>();

    @Test
    void rejectsSelfParentAndDescendantsThroughEitherParent() {
        AnimalRecord founder = pet(null, null);
        AnimalRecord child = pet(null, founder.id());
        AnimalRecord grandchild = pet(child.id(), null);
        AnimalRecord unrelated = pet(null, null);

        assertTrue(wouldCycle(founder, founder, unrelated));
        assertTrue(wouldCycle(founder, child, unrelated));
        assertTrue(wouldCycle(founder, unrelated, grandchild));
        assertFalse(wouldCycle(grandchild, founder, unrelated));
        assertNull(founder.parentA());
        assertNull(founder.parentB());
    }

    @Test
    void allowsSharedAncestorsAndReplacingExistingParents() {
        AnimalRecord founder = pet(null, null);
        AnimalRecord first = pet(founder.id(), null);
        AnimalRecord second = pet(founder.id(), null);
        AnimalRecord child = pet(first.id(), second.id());

        assertFalse(wouldCycle(child, first, second));
        assertFalse(wouldCycle(child, founder, first));
        assertFalse(Genealogy.wouldCreateCycle(child.id(), null, null, records::get));
    }

    @Test
    void toleratesMissingRecordsAndExistingUnrelatedCycles() {
        AnimalRecord first = pet(null, null);
        AnimalRecord second = pet(first.id(), null);
        first.setParents(second.id(), UUID.randomUUID());
        AnimalRecord child = pet(null, null);

        assertFalse(wouldCycle(child, first, second));
        assertFalse(Genealogy.wouldCreateCycle(child.id(), UUID.randomUUID(), null, records::get));
        assertTrue(wouldCycle(first, child, second));
    }

    @Test
    void seesChangesMadeAfterAnEarlierValidation() {
        AnimalRecord first = pet(null, null);
        AnimalRecord second = pet(null, null);
        AnimalRecord child = pet(null, null);

        assertFalse(wouldCycle(child, first, second));
        first.setParents(child.id(), null);
        assertTrue(wouldCycle(child, first, second));
    }

    @Test
    void checksDeepAncestryWithoutRecursiveStackOverflow() {
        AnimalRecord founder = pet(null, null);
        AnimalRecord descendant = founder;
        for (int i = 0; i < 10_000; i++) descendant = pet(descendant.id(), null);

        assertTrue(Genealogy.wouldCreateCycle(founder.id(), null, descendant.id(), records::get));
    }

    private boolean wouldCycle(AnimalRecord child, AnimalRecord first, AnimalRecord second) {
        return Genealogy.wouldCreateCycle(child.id(), first.id(), second.id(), records::get);
    }

    private AnimalRecord pet(UUID first, UUID second) {
        AnimalRecord record = new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", "Wolf", false,
                first, second, 0, 0, false, null, null, null, null, null, null, null);
        records.put(record.id(), record);
        return record;
    }
}
