package com.egakh.familytree.network;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.RelativesResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Proof-of-bug test from the 1.4.0 code review. It fails on d689590. */
class ReviewRelativesProofTest {
    private static AnimalRecord wolf(String name, UUID owner, UUID parentA) {
        return new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", name, false, parentA, null, 0, 0, false,
                null, null, owner, "owner", null, null, null);
    }

    @Test
    void hiddenParentDoesNotLinkVisibleSiblings() {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        AnimalRecord parent = wolf("P", bob, null);
        AnimalRecord x = wolf("X", alice, parent.id());
        AnimalRecord y = wolf("Y", alice, parent.id());
        Map<UUID, AnimalRecord> records = new HashMap<>();
        for (AnimalRecord record : new AnimalRecord[]{parent, x, y}) records.put(record.id(), record);
        Map<UUID, Double> nearby = Map.of(x.id(), 4.0, y.id(), 9.0, parent.id(), 16.0);

        RelativesResult result = NearbyRelatives.answer(UUID.randomUUID(), x.id(), alice, false, records::get, nearby);

        assertEquals(RelativesResult.Status.FOUND, result.statusValue());
        assertEquals(0, result.total(), "Y is related to X only through Bob's parent, which Alice can't view");
    }
}
