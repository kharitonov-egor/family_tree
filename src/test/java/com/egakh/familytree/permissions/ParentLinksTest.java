package com.egakh.familytree.permissions;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.util.PetNames;
import com.egakh.familytree.util.PetRelations;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ParentLinksTest {
    @Test void validatesAllOwnersAndRejectsAncestryLoops() {
        UUID owner = UUID.randomUUID();
        AnimalRecord a = pet(owner), b = pet(owner), child = pet(owner);
        Map<UUID, AnimalRecord> pets = new HashMap<>();
        for (var pet : java.util.List.of(a, b, child)) pets.put(pet.id(), pet);
        assertEquals(ParentLinks.Result.SUCCESS, ParentLinks.validate(child.id(), a.id(), b.id(), false, owner, false, pets::get));
        b.setOwner(UUID.randomUUID(), null);
        assertEquals(ParentLinks.Result.NOT_ALLOWED, ParentLinks.validate(child.id(), a.id(), b.id(), false, owner, false, pets::get));
        b.setOwner(owner, null); a.setParents(child.id(), null);
        assertEquals(ParentLinks.Result.CYCLE, ParentLinks.validate(child.id(), a.id(), b.id(), false, owner, false, pets::get));
    }

    @Test void namesReturnEveryAliasMatchAndExactIdsDisambiguate() {
        var first = pet(null); var second = pet(null); second.setTreeName("Moss");
        assertEquals(2, PetNames.matches(java.util.List.of(first, second), "Rover", pet -> true).size());
        assertEquals(java.util.List.of(second), PetNames.matches(java.util.List.of(first, second), second.id().toString(), pet -> true));
    }

    @Test void permitsVanillaMuleParentageWithoutAllowingOtherMixedSpecies() {
        assertTrue(PetRelations.speciesMatch("minecraft:horse", "minecraft:donkey", "minecraft:mule"));
        assertFalse(PetRelations.speciesMatch("minecraft:wolf", "minecraft:cat", "minecraft:wolf"));
    }

    @Test void pickerOffersOnlyParentsTheServerAccepts() {
        UUID owner = UUID.randomUUID();
        AnimalRecord sheep = pet(owner, "minecraft:sheep"), ewe = pet(owner, "minecraft:sheep"), ram = pet(owner, "minecraft:sheep");
        AnimalRecord wolf = pet(owner, "minecraft:wolf"), lamb = pet(owner, "minecraft:sheep"), grandLamb = pet(owner, "minecraft:sheep");
        lamb.setParents(sheep.id(), ewe.id());
        grandLamb.setParents(lamb.id(), null);
        Map<UUID, AnimalRecord> pets = map(sheep, ewe, ram, wolf, lamb, grandLamb);

        assertTrue(ParentLinks.mayPick(sheep, ram, null, pets::get));
        assertFalse(ParentLinks.mayPick(sheep, wolf, null, pets::get), "another species");
        assertFalse(ParentLinks.mayPick(sheep, sheep, null, pets::get), "the child itself");
        assertFalse(ParentLinks.mayPick(sheep, lamb, null, pets::get), "a child would close a loop");
        assertFalse(ParentLinks.mayPick(sheep, grandLamb, null, pets::get), "a grandchild would close a loop");
        assertFalse(ParentLinks.mayPick(sheep, ram, ram, pets::get), "the other chosen parent");
        for (AnimalRecord candidate : pets.values())
            if (ParentLinks.mayPick(sheep, candidate, ram, pets::get))
                assertEquals(ParentLinks.Result.SUCCESS, ParentLinks.validate(sheep.id(), candidate.id(), ram.id(), false, owner, false, pets::get));
    }

    @Test void muleParentsMustBeOneHorseAndOneDonkey() {
        UUID owner = UUID.randomUUID();
        AnimalRecord mule = pet(owner, "minecraft:mule"), horse = pet(owner, "minecraft:horse"), mare = pet(owner, "minecraft:horse");
        AnimalRecord donkey = pet(owner, "minecraft:donkey"), otherMule = pet(owner, "minecraft:mule"), wolf = pet(owner, "minecraft:wolf");
        Map<UUID, AnimalRecord> pets = map(mule, horse, mare, donkey, otherMule, wolf);

        assertTrue(ParentLinks.mayPick(mule, horse, null, pets::get));
        assertTrue(ParentLinks.mayPick(mule, donkey, null, pets::get));
        assertFalse(ParentLinks.mayPick(mule, otherMule, null, pets::get), "mules are sterile in vanilla");
        assertTrue(ParentLinks.mayPick(mule, donkey, horse, pets::get));
        assertFalse(ParentLinks.mayPick(mule, mare, horse, pets::get), "two horses can't have a mule");
        assertTrue(ParentLinks.mayPick(mule, horse, wolf, pets::get), "a recorded parent that could never fit is ignored");
    }

    private static Map<UUID, AnimalRecord> map(AnimalRecord... records) {
        Map<UUID, AnimalRecord> pets = new HashMap<>();
        for (AnimalRecord record : records) pets.put(record.id(), record);
        return pets;
    }

    private AnimalRecord pet(UUID owner) {
        return pet(owner, "minecraft:wolf");
    }

    private AnimalRecord pet(UUID owner, String species) {
        return new AnimalRecord(UUID.randomUUID(), species, "Rover", false, null, null,
                0, 0, false, null, null, owner, null, null, null, null);
    }
}
