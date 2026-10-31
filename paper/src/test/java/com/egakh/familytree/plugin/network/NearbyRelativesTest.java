package com.egakh.familytree.plugin.network;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.plugin.network.Payloads.RelativesResult;
import com.egakh.familytree.util.CloseRelation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NearbyRelativesTest {
    private static final UUID ALICE = new UUID(1, 1), BOB = new UUID(2, 2);
    private final Map<UUID, AnimalRecord> records = new HashMap<>();
    private final Map<UUID, Double> nearby = new HashMap<>();

    private AnimalRecord wolf(String name, UUID owner, UUID parentA, UUID parentB) {
        AnimalRecord record = new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", name, false, parentA, parentB, 0, 0, false,
                null, null, owner, "owner", null, null, null);
        records.put(record.id(), record);
        nearby.put(record.id(), 4.0);
        return record;
    }

    private RelativesResult ask(AnimalRecord target, UUID viewer, boolean viewAll) {
        return NearbyRelatives.answer(new UUID(9, 9), target.id(), viewer, viewAll, records::get, nearby);
    }

    private static Map<UUID, CloseRelation> relations(RelativesResult result) {
        Map<UUID, CloseRelation> relations = new HashMap<>();
        for (RelativesResult.Relative relative : result.relatives()) relations.put(relative.id(), CloseRelation.byOrdinal(relative.relation()));
        return relations;
    }

    @Test void namesCloseRelativesLikeTheMod() {
        AnimalRecord grandsire = wolf("Grandsire", ALICE, null, null);
        AnimalRecord sire = wolf("Sire", ALICE, grandsire.id(), null);
        AnimalRecord dam = wolf("Dam", ALICE, null, null);
        AnimalRecord rex = wolf("Rex", ALICE, sire.id(), dam.id());
        AnimalRecord sister = wolf("Sister", ALICE, dam.id(), sire.id());
        AnimalRecord otherDam = wolf("Other dam", ALICE, null, null);
        AnimalRecord half = wolf("Half", ALICE, sire.id(), otherDam.id());
        AnimalRecord pup = wolf("Pup", ALICE, rex.id(), null);
        AnimalRecord aunt = wolf("Aunt", ALICE, grandsire.id(), null);

        RelativesResult result = ask(rex, ALICE, false);

        assertEquals(RelativesResult.Status.FOUND.ordinal(), result.status());
        assertEquals("Rex", result.targetName());
        assertEquals(Map.of(sire.id(), CloseRelation.PARENT, dam.id(), CloseRelation.PARENT, pup.id(), CloseRelation.CHILD,
                sister.id(), CloseRelation.SIBLING, half.id(), CloseRelation.HALF_SIBLING, grandsire.id(), CloseRelation.GRANDPARENT),
                relations(result));
        assertFalse(relations(result).containsKey(aunt.id()), "aunts stay dark, as in the mod");
        assertEquals(6, result.total());
        assertEquals(CloseRelation.PARENT.ordinal(), result.relatives().get(0).relation(), "closest relation first");
    }

    /** Review m5: Alice's foals share only a parent Bob owns, which Alice can't view under OWN_ONLY. */
    @Test void aHiddenParentDoesNotLinkVisibleSiblings() {
        AnimalRecord parent = wolf("P", BOB, null, null);
        AnimalRecord x = wolf("X", ALICE, parent.id(), null);
        wolf("Y", ALICE, parent.id(), null);

        RelativesResult result = ask(x, ALICE, false);

        assertEquals(RelativesResult.Status.FOUND.ordinal(), result.status());
        assertEquals(0, result.total());
        assertEquals(parent.id(), x.parentA(), "the stored record keeps its parent");
        assertEquals(2, ask(x, ALICE, true).total(), "a viewer who may see Bob's pets sees the parent and the sibling");
    }

    @Test void aParentWithoutARecordStillLinksSiblings() {
        UUID pruned = UUID.randomUUID();
        AnimalRecord x = wolf("X", ALICE, pruned, null);
        AnimalRecord y = wolf("Y", ALICE, pruned, null);

        assertEquals(Map.of(y.id(), CloseRelation.SIBLING), relations(ask(x, ALICE, false)));
    }

    @Test void onlyAnimalsInRangeThatTheViewerMaySee() {
        AnimalRecord dam = wolf("Dam", ALICE, null, null);
        AnimalRecord rex = wolf("Rex", ALICE, null, dam.id());
        AnimalRecord far = wolf("Far", ALICE, dam.id(), null);
        AnimalRecord bobs = wolf("Bob's", BOB, dam.id(), null);
        nearby.put(far.id(), NearbyRelatives.RANGE * NearbyRelatives.RANGE + 1);

        assertEquals(Map.of(dam.id(), CloseRelation.PARENT), relations(ask(rex, ALICE, false)));
        assertFalse(relations(ask(rex, ALICE, true)).containsKey(far.id()));
        assertTrue(relations(ask(rex, ALICE, true)).containsKey(bobs.id()));
    }

    @Test void aTargetTheViewerCantSeeOrThatIsFarAwayIsNotTracked() {
        AnimalRecord bobs = wolf("Bob's", BOB, null, null);
        assertEquals(RelativesResult.Status.NOT_TRACKED.ordinal(), ask(bobs, ALICE, false).status());
        AnimalRecord far = wolf("Far", ALICE, null, null);
        nearby.remove(far.id());
        assertEquals(RelativesResult.Status.NOT_TRACKED.ordinal(), ask(far, ALICE, false).status());
        assertEquals("", ask(far, ALICE, false).targetName());
    }

    @Test void theReplyKeepsTheClosestThirtyTwoAndCountsTheRest() {
        AnimalRecord dam = wolf("Dam", ALICE, null, null);
        AnimalRecord rex = wolf("Rex", ALICE, dam.id(), null);
        for (int i = 0; i < 40; i++) nearby.put(wolf("Pup " + i, ALICE, dam.id(), null).id(), 100.0 + i);
        nearby.put(dam.id(), 900.0);

        RelativesResult result = ask(rex, ALICE, false);

        assertEquals(41, result.total());
        assertEquals(RelativesResult.MAX_RELATIVES, result.relatives().size());
        assertEquals(dam.id(), result.relatives().get(0).id(), "the parent comes before the nearer siblings");
        assertEquals(List.of("Pup 0", "Pup 1"), result.relatives().subList(1, 3).stream().map(RelativesResult.Relative::name).toList());
        assertDoesNotThrow(() -> Payloads.encodeRelativesResult(result));
    }

    @Test void longNamesAreShortenedToFitTheMessage() {
        AnimalRecord dam = wolf("D".repeat(300), ALICE, null, null);
        AnimalRecord rex = wolf("R".repeat(300), ALICE, dam.id(), null);

        RelativesResult result = ask(rex, ALICE, false);

        assertEquals(NearbyRelatives.MAX_NAME_LENGTH, result.targetName().length());
        assertEquals(NearbyRelatives.MAX_NAME_LENGTH, result.relatives().get(0).name().length());
    }
}
