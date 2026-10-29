package com.egakh.familytree.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FarmRecordsTest {
    @Test void onlyUnnamedAnimalsOtherThanPetsCount() {
        AnimalRecord cow = animal("minecraft:cow", null, null), puppy = animal("minecraft:wolf", null, null);
        AnimalRecord named = animal("minecraft:sheep", null, null), treeNamed = animal("minecraft:pig", null, null);
        named.rename("Dolly", false);
        treeNamed.setTreeName("Wilbur");
        AnimalRecord modded = animal("unseenmod:yak", null, null);
        assertTrue(FarmRecords.unnamedFarm(cow));
        assertFalse(FarmRecords.unnamedFarm(puppy));
        assertFalse(FarmRecords.unnamedFarm(named));
        assertFalse(FarmRecords.unnamedFarm(treeNamed));
        assertFalse(FarmRecords.unnamedFarm(modded), "Unclassified modded species are protected");
        assertEquals(1, FarmRecords.count(List.of(cow, puppy, named, treeNamed, modded)));
    }

    @Test void cleanupRemovesDeceasedLeavesBeforeParentsAndOldestDeathsFirst() {
        AnimalRecord mother = animal("minecraft:cow", null, null), calf = animal("minecraft:cow", mother, null);
        AnimalRecord oldLeaf = animal("minecraft:cow", null, null), newLeaf = animal("minecraft:cow", null, null);
        AnimalRecord living = animal("minecraft:cow", null, null), deadPuppy = animal("minecraft:wolf", null, null);
        AnimalRecord namedDead = animal("minecraft:cow", null, null);
        namedDead.rename("Bessie", false);
        mother.markDeceased(5, 5, null);
        oldLeaf.markDeceased(10, 10, null);
        newLeaf.markDeceased(20, 20, null);
        deadPuppy.markDeceased(1, 1, null);
        namedDead.markDeceased(1, 1, null);
        var order = FarmRecords.removable(List.of(mother, calf, oldLeaf, newLeaf, living, deadPuppy, namedDead));
        assertEquals(List.of(oldLeaf, newLeaf, mother), order);
    }

    @Test void cleanupKeepsThreeQuartersOfTheLimit() {
        assertEquals(3_750, FarmRecords.cleanupTarget(5_000));
        assertEquals(0, FarmRecords.cleanupTarget(0));
    }

    private static AnimalRecord animal(String species, AnimalRecord mother, AnimalRecord father) {
        return new AnimalRecord(UUID.randomUUID(), species, "Cow 1A2B", true,
                mother == null ? null : mother.id(), father == null ? null : father.id(), 1, 1, false, null, null,
                UUID.randomUUID(), "Alex", null, null, null);
    }
}
