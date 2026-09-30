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

    private AnimalRecord pet(UUID owner) {
        return new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", "Rover", false, null, null,
                0, 0, false, null, null, owner, null, null, null, null);
    }
}
