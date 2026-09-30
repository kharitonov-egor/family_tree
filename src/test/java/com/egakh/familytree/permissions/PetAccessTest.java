package com.egakh.familytree.permissions;

import com.egakh.familytree.data.AnimalRecord;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PetAccessTest {
    private final UUID owner = UUID.randomUUID();
    private final AnimalRecord pet = new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", "Moss", false,
            null, null, 1, 1000, false, null, null, owner, "Owner", null, null, null);

    @Test void publicViewingDoesNotAllowLocatingOrEditingAnotherPlayersPet() {
        UUID stranger = UUID.randomUUID();
        assertFalse(PetAccess.canView(pet, stranger, false));
        assertTrue(PetAccess.canView(pet, stranger, true));
        assertFalse(PetAccess.canManage(pet, stranger, false));
        assertTrue(PetAccess.canView(pet, owner, false));
        assertTrue(PetAccess.canManage(pet, owner, false));
    }

    @Test void checksTheCurrentOwnerAgainAfterOwnershipChanges() {
        UUID newOwner = UUID.randomUUID();
        pet.setOwner(newOwner, "New owner");
        assertFalse(PetAccess.canManage(pet, owner, false));
        assertTrue(PetAccess.canManage(pet, newOwner, false));
        assertTrue(PetAccess.canManage(pet, owner, true));
    }

    @Test void publicSnapshotsExcludeLocationsWithoutChangingTheSavedPet() {
        pet.updateLastSeen(17, 64, -25, "minecraft:overworld", 12);
        pet.setTreeName("Moss the elder");
        AnimalRecord publicPet = PetAccess.forViewer(pet, UUID.randomUUID(), false);
        assertNull(publicPet.lastSeen());
        assertEquals("Moss the elder", publicPet.name());
        assertNotNull(pet.lastSeen());
        AnimalRecord owned = PetAccess.forViewer(pet, owner, false);
        assertNotSame(pet, owned);
        assertEquals(pet.lastSeen(), owned.lastSeen());
        pet.setTreeName("Changed after snapshot");
        assertEquals("Moss the elder", owned.name());
        assertEquals(pet.lastSeen(), PetAccess.forViewer(pet, UUID.randomUUID(), true).lastSeen());
    }

    @Test void onlyOperatorsCanManageUnownedPetsAndMissingRecordsAreNeverAllowed() {
        pet.setOwner(null, null);
        assertFalse(PetAccess.canManage(pet, null, false));
        assertFalse(PetAccess.canManage(pet, owner, false));
        assertTrue(PetAccess.canManage(pet, null, true));
        assertFalse(PetAccess.canView(null, owner, true));
        assertFalse(PetAccess.canManage(null, owner, true));
    }
}
