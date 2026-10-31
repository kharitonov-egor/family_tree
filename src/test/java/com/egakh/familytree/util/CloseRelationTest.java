package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The world highlight's relation names, read from the breeding planner's {@link Kinship}. */
class CloseRelationTest {
    private final Map<UUID, AnimalRecord> records = new HashMap<>();
    private long next = 1;

    private AnimalRecord animal(String species, AnimalRecord a, AnimalRecord b) {
        AnimalRecord record = new AnimalRecord(new UUID(7, next++), species, "Animal " + next, false,
                a == null ? null : a.id(), b == null ? null : b.id(), 1, 1, false, null, null,
                new UUID(1, 1), "Owner", null, null, null);
        records.put(record.id(), record);
        return record;
    }

    private AnimalRecord horse(AnimalRecord a, AnimalRecord b) {
        return animal("minecraft:horse", a, b);
    }

    /** What {@code relative} is to {@code focus}. */
    private CloseRelation of(AnimalRecord relative, AnimalRecord focus) {
        return CloseRelation.of(new Kinship(records).relation(relative.id(), focus.id()));
    }

    @Test void namesThreeGenerationsAroundOneAnimal() {
        AnimalRecord grandsire = horse(null, null);
        AnimalRecord granddam = horse(null, null);
        AnimalRecord comet = horse(grandsire, granddam);
        AnimalRecord dusk = horse(null, null);
        AnimalRecord ember = horse(comet, dusk);
        AnimalRecord fullSibling = horse(dusk, comet);
        AnimalRecord aunt = horse(grandsire, granddam);
        AnimalRecord stranger = horse(null, null);
        AnimalRecord mate = horse(null, null);
        AnimalRecord foal = horse(ember, mate);
        AnimalRecord grandfoal = horse(foal, stranger);
        AnimalRecord greatGrandfoal = horse(grandfoal, null);

        assertEquals(CloseRelation.PARENT, of(comet, ember));
        assertEquals(CloseRelation.PARENT, of(dusk, ember));
        assertEquals(CloseRelation.GRANDPARENT, of(grandsire, ember));
        assertEquals(CloseRelation.GRANDPARENT, of(granddam, ember));
        assertEquals(CloseRelation.SIBLING, of(fullSibling, ember), "parent order does not matter");
        assertEquals(CloseRelation.CHILD, of(foal, ember));
        assertEquals(CloseRelation.GRANDCHILD, of(grandfoal, ember));
        assertNull(of(ember, ember), "an animal is not its own relative");
        assertNull(of(stranger, ember));
        assertNull(of(mate, ember), "the other parent of a foal is not a blood relative");
        assertNull(of(aunt, ember), "aunts and cousins stay dark");
        assertNull(of(greatGrandfoal, ember), "the highlight stops at grandchildren");
    }

    @Test void halfSiblingsShareExactlyOneParent() {
        AnimalRecord sire = horse(null, null);
        AnimalRecord damA = horse(null, null);
        AnimalRecord damB = horse(null, null);
        AnimalRecord ember = horse(sire, damA);
        AnimalRecord brother = horse(sire, damA);
        AnimalRecord halfThroughSire = horse(damB, sire);
        AnimalRecord halfThroughDam = horse(damA, horse(null, null));
        AnimalRecord oneParentKnown = horse(sire, null);
        AnimalRecord unrelated = horse(damB, null);

        assertEquals(CloseRelation.SIBLING, of(brother, ember));
        assertEquals(CloseRelation.HALF_SIBLING, of(halfThroughSire, ember));
        assertEquals(CloseRelation.HALF_SIBLING, of(halfThroughDam, ember));
        assertEquals(CloseRelation.SIBLING, of(oneParentKnown, ember), "a missing parent might be the shared one");
        assertNull(of(unrelated, ember));
        assertNull(of(damB, ember));
    }

    @Test void muleHasAHorseAndADonkeyParent() {
        AnimalRecord horse = animal("minecraft:horse", null, null);
        AnimalRecord donkey = animal("minecraft:donkey", null, null);
        AnimalRecord jack = animal("minecraft:donkey", null, null);
        AnimalRecord mule = animal("minecraft:mule", horse, donkey);
        AnimalRecord foal = animal("minecraft:horse", horse, animal("minecraft:horse", null, null));
        AnimalRecord donkeyFoal = animal("minecraft:donkey", jack, donkey);

        assertEquals(CloseRelation.PARENT, of(horse, mule));
        assertEquals(CloseRelation.PARENT, of(donkey, mule));
        assertEquals(CloseRelation.HALF_SIBLING, of(foal, mule));
        assertEquals(CloseRelation.HALF_SIBLING, of(donkeyFoal, mule));
        assertEquals(CloseRelation.CHILD, of(mule, horse));
        assertEquals(CloseRelation.CHILD, of(mule, donkey));
    }

    @Test void closestRelationWinsInInbredLines() {
        AnimalRecord founder = horse(null, null);
        AnimalRecord daughter = horse(founder, horse(null, null));
        AnimalRecord inbred = horse(founder, daughter);

        assertEquals(CloseRelation.PARENT, of(founder, inbred), "also a grandparent through the daughter");
        assertEquals(CloseRelation.PARENT, of(daughter, inbred), "also a half sibling through the founder");
    }

    @Test void hiddenParentHidesTheGrandparentsBehindIt() {
        AnimalRecord grandparent = horse(null, null);
        AnimalRecord hiddenParent = horse(grandparent, null);
        AnimalRecord dusk = horse(null, null);
        AnimalRecord ember = horse(hiddenParent, dusk);
        AnimalRecord halfSibling = horse(hiddenParent, horse(null, null));
        records.remove(hiddenParent.id());

        assertNull(of(grandparent, ember));
        assertEquals(CloseRelation.PARENT, of(dusk, ember));
        assertEquals(CloseRelation.HALF_SIBLING, of(halfSibling, ember), "the shared parent ID is in both visible records");
    }

    @Test void selfParentLinksAreIgnored() {
        AnimalRecord odd = new AnimalRecord(new UUID(8, 8), "minecraft:wolf", "Odd", false, new UUID(8, 8), null,
                1, 1, false, null, null, null, null, null, null, null);
        records.put(odd.id(), odd);
        AnimalRecord pup = horse(odd, null);
        assertEquals(CloseRelation.CHILD, of(pup, odd));
        assertEquals(CloseRelation.PARENT, of(odd, pup));
    }

    @Test void ordinalsAreStableOnTheWire() {
        assertEquals(List.of(CloseRelation.PARENT, CloseRelation.CHILD, CloseRelation.SIBLING, CloseRelation.HALF_SIBLING,
                CloseRelation.GRANDPARENT, CloseRelation.GRANDCHILD), List.of(CloseRelation.values()));
        assertNull(CloseRelation.byOrdinal(CloseRelation.values().length));
        assertNull(CloseRelation.byOrdinal(-1));
        assertEquals("familytree.relation.half_sibling", CloseRelation.HALF_SIBLING.translationKey());
    }
}
