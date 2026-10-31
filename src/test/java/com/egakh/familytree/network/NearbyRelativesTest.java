package com.egakh.familytree.network;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.RelativesResult;
import com.egakh.familytree.util.CloseRelation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NearbyRelativesTest {
    private static final UUID ALEX = new UUID(1, 1);
    private static final UUID SAM = new UUID(2, 2);
    private static final UUID REQUEST = new UUID(3, 3);
    private final Map<UUID, AnimalRecord> records = new LinkedHashMap<>();
    private final Map<UUID, Double> loaded = new HashMap<>();
    private long next = 1;

    private AnimalRecord pet(String name, UUID owner, AnimalRecord a, AnimalRecord b, double distance) {
        AnimalRecord record = new AnimalRecord(new UUID(9, next++), "minecraft:wolf", name, false,
                a == null ? null : a.id(), b == null ? null : b.id(), 1, 1, false, null, null, owner, "Owner", null,
                new AnimalRecord.LastSeen(10, 64, 10, "minecraft:overworld", 1), null);
        records.put(record.id(), record);
        if (!Double.isNaN(distance)) loaded.put(record.id(), distance * distance);
        return record;
    }

    private RelativesResult ask(AnimalRecord target, UUID viewer, boolean viewAll) {
        return NearbyRelatives.answer(REQUEST, target.id(), viewer, viewAll, records::get, loaded);
    }

    private static Map<String, CloseRelation> byName(RelativesResult result) {
        Map<String, CloseRelation> names = new LinkedHashMap<>();
        for (RelativesResult.Relative relative : result.relatives())
            names.put(relative.name(), CloseRelation.byOrdinal(relative.relation()));
        return names;
    }

    @Test void repliesWithLoadedRelativesClosestFirst() {
        AnimalRecord granddam = pet("Granny", ALEX, null, null, 20);
        AnimalRecord moss = pet("Moss", ALEX, granddam, null, 12);
        AnimalRecord fern = pet("Fern", ALEX, null, null, 3);
        AnimalRecord clover = pet("Clover", ALEX, moss, fern, 2);
        pet("Ivy", ALEX, moss, fern, 30);
        pet("Bud", ALEX, clover, null, 5);
        pet("Far away", ALEX, clover, null, 65);
        pet("Unloaded", ALEX, clover, null, Double.NaN);

        RelativesResult result = ask(clover, ALEX, false);

        assertEquals(RelativesResult.Status.FOUND, result.statusValue());
        assertEquals(REQUEST, result.requestId());
        assertEquals(clover.id(), result.target());
        assertEquals("Clover", result.targetName());
        assertEquals(5, result.total());
        assertEquals(List.of("Fern", "Moss", "Bud", "Ivy", "Granny"), List.copyOf(byName(result).keySet()),
                "parents nearest first, then children, siblings, and grandparents");
        assertEquals(CloseRelation.GRANDPARENT, byName(result).get("Granny"));
    }

    @Test void hidesOtherPlayersPetsWhenViewPolicyIsOwnerOnly() {
        AnimalRecord samsDam = pet("Sam's dam", SAM, null, null, 4);
        AnimalRecord alexsSire = pet("Alex's sire", ALEX, null, null, 4);
        AnimalRecord pup = pet("Pup", ALEX, alexsSire, samsDam, 2);
        pet("Sam's pup", SAM, alexsSire, samsDam, 3);

        RelativesResult ownerOnly = ask(pup, ALEX, false);
        assertEquals(Map.of("Alex's sire", CloseRelation.PARENT), byName(ownerOnly));
        assertEquals(1, ownerOnly.total());

        RelativesResult everyone = ask(pup, ALEX, true);
        assertEquals(Map.of("Alex's sire", CloseRelation.PARENT, "Sam's dam", CloseRelation.PARENT, "Sam's pup", CloseRelation.SIBLING),
                byName(everyone));
    }

    @Test void aHiddenParentLinksNobodyButAnUnlistedOneStillDoes() {
        AnimalRecord samsSire = pet("Sam's sire", SAM, null, null, 4);
        AnimalRecord samsOtherSire = pet("Sam's other sire", SAM, null, null, 4);
        AnimalRecord alexsDam = pet("Alex's dam", ALEX, null, null, 4);
        AnimalRecord pup = pet("Pup", ALEX, samsSire, null, 2);
        pet("Same hidden sire", ALEX, samsSire, null, 3);
        pet("Same dam, other hidden sire", ALEX, samsOtherSire, alexsDam, 3);
        AnimalRecord halfPup = pet("Half pup", ALEX, samsSire, alexsDam, 2);

        assertEquals(0, ask(pup, ALEX, false).total(), "related only through Sam's sire");
        assertEquals(Map.of("Same dam, other hidden sire", CloseRelation.SIBLING, "Alex's dam", CloseRelation.PARENT),
                byName(ask(halfPup, ALEX, false)), "with the sires hidden, Family Tree can't tell half from full");
        assertEquals(Map.of("Same hidden sire", CloseRelation.SIBLING, "Sam's sire", CloseRelation.PARENT,
                        "Half pup", CloseRelation.SIBLING), byName(ask(pup, ALEX, true)));
        assertEquals(CloseRelation.HALF_SIBLING, byName(ask(halfPup, ALEX, true)).get("Same dam, other hidden sire"));

        UUID pruned = new UUID(7, 7);
        AnimalRecord orphan = pet("Orphan", ALEX, null, null, 2);
        orphan.setParents(pruned, null);
        pet("Orphan's sibling", ALEX, null, null, 3).setParents(pruned, null);
        assertEquals(Map.of("Orphan's sibling", CloseRelation.SIBLING), byName(ask(orphan, ALEX, false)),
                "a parent ID without a record still links siblings, as in the screens");
    }

    @Test void refusesATargetTheViewerCannotSee() {
        AnimalRecord samsWolf = pet("Rex", SAM, null, null, 2);
        pet("Rex junior", SAM, samsWolf, null, 3);
        RelativesResult hidden = ask(samsWolf, ALEX, false);
        assertEquals(RelativesResult.Status.NOT_TRACKED, hidden.statusValue());
        assertTrue(hidden.relatives().isEmpty());
        assertEquals("", hidden.targetName());
    }

    @Test void refusesAnUntrackedOrDistantTarget() {
        UUID untracked = new UUID(5, 5);
        loaded.put(untracked, 4.0);
        assertEquals(RelativesResult.Status.NOT_TRACKED,
                NearbyRelatives.answer(REQUEST, untracked, ALEX, true, records::get, loaded).statusValue());
        AnimalRecord distant = pet("Distant", ALEX, null, null, 80);
        assertEquals(RelativesResult.Status.NOT_TRACKED, ask(distant, ALEX, false).statusValue());
    }

    @Test void capsTheReplyAndReportsTheTotal() {
        AnimalRecord sire = pet("Sire", ALEX, null, null, 1);
        for (int i = 0; i < RelativesResult.MAX_RELATIVES + 8; i++) pet("Pup " + i, ALEX, sire, null, 2 + i);
        RelativesResult result = ask(sire, ALEX, false);
        assertEquals(RelativesResult.MAX_RELATIVES, result.relatives().size());
        assertEquals(RelativesResult.MAX_RELATIVES + 8, result.total());
        assertEquals("Pup 0", result.relatives().get(0).name(), "nearest first within one relation");
    }

    @Test void readsOnlyTheLineageOfAnimalsInRange() {
        AnimalRecord grandsire = pet("Grandsire", ALEX, null, null, Double.NaN);
        AnimalRecord sire = pet("Sire", ALEX, grandsire, null, 3);
        AnimalRecord pup = pet("Pup", ALEX, sire, null, 2);
        for (int i = 0; i < 10_000; i++) pet("Stranger " + i, ALEX, null, null, Double.NaN);
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        RelativesResult result = NearbyRelatives.answer(REQUEST, pup.id(), ALEX, false,
                id -> { reads.incrementAndGet(); return records.get(id); }, loaded);
        assertEquals(Map.of("Sire", CloseRelation.PARENT), byName(result));
        assertTrue(reads.get() < 20, "read " + reads.get() + " records");
    }

    @Test void shortensLongNames() {
        AnimalRecord sire = pet("S".repeat(200), ALEX, null, null, 1);
        AnimalRecord pup = pet("P".repeat(200), ALEX, sire, null, 2);
        RelativesResult result = ask(pup, ALEX, false);
        assertEquals(NearbyRelatives.MAX_NAME_LENGTH, result.targetName().length());
        assertEquals(NearbyRelatives.MAX_NAME_LENGTH, result.relatives().get(0).name().length());
    }
}
