package com.egakh.familytree.menu;

import com.egakh.familytree.data.AnimalRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FamilyIndexTest {
    private static final UUID KAI = new UUID(9, 1), ALEX = new UUID(9, 2);

    private static AnimalRecord cow(long id, String name, UUID owner, AnimalRecord a, AnimalRecord b) {
        return new AnimalRecord(new UUID(0, id), "minecraft:cow", name, false, a == null ? null : a.id(), b == null ? null : b.id(),
                1, 2, false, null, null, owner, owner == KAI ? "Kai" : "Alex", null, null, null);
    }

    @Test
    void countsChildrenThePlayerCannotView() {
        AnimalRecord daisy = cow(1, "Daisy", KAI, null, null);
        AnimalRecord bruno = cow(2, "Bruno", ALEX, null, null);
        AnimalRecord alexCalf = cow(3, "Clover", ALEX, daisy, bruno);
        AnimalRecord kaiCalf = cow(4, "Moss", KAI, daisy, bruno);
        List<AnimalRecord> records = List.of(daisy, bruno, alexCalf, kaiCalf);

        FamilyIndex own = FamilyIndex.build(records, KAI, false);
        assertEquals(List.of(kaiCalf.id()), own.children(daisy.id()));
        assertEquals(1, own.hiddenChildren(daisy.id()));
        assertEquals(0, own.hiddenChildren(bruno.id()), "a parent the player can't view gets no count");
        assertFalse(own.visible().contains(bruno.id()));

        FamilyIndex all = FamilyIndex.build(records, KAI, true);
        assertEquals(2, all.children(daisy.id()).size());
        assertEquals(0, all.hiddenChildren(daisy.id()));
    }

    @Test
    void remembersTheGrantItWasBuiltFor() {
        assertTrue(FamilyIndex.build(List.of(), KAI, true).viewAll());
        assertFalse(FamilyIndex.build(List.of(), KAI, false).viewAll());
        assertFalse(FamilyIndex.empty().viewAll());
    }

    @Test
    void sortsPetsByNameAndSpeciesWithWolvesFirst() {
        AnimalRecord zed = cow(1, "zed", KAI, null, null);
        AnimalRecord amy = new AnimalRecord(new UUID(0, 2), "minecraft:wolf", "Amy", false, null, null, 1, 2, false, null, null,
                KAI, "Kai", null, null, null);
        FamilyIndex index = FamilyIndex.build(List.of(zed, amy), KAI, false);
        assertEquals(List.of(amy.id(), zed.id()), index.visible());
        assertEquals(List.of("minecraft:wolf", "minecraft:cow"), index.species());
    }
}
