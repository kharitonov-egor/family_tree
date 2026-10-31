package com.egakh.familytree.permissions;

import com.egakh.familytree.data.AnimalRecord;
import org.junit.jupiter.api.Test;
import java.util.List;
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
        AnimalRecord publicPet = PetAccess.forViewer(pet, UUID.randomUUID(), false, true);
        assertNull(publicPet.lastSeen());
        assertEquals("Moss the elder", publicPet.name());
        assertNotNull(pet.lastSeen());
        AnimalRecord owned = PetAccess.forViewer(pet, owner, false, true);
        assertNotSame(pet, owned);
        assertEquals(pet.lastSeen(), owned.lastSeen());
        pet.setTreeName("Changed after snapshot");
        assertEquals("Moss the elder", owned.name());
        pet.setHorseStats(new com.egakh.familytree.data.HorseStats(0.3, 0.9, 28.0, null));
        assertEquals(pet.horseStats(), PetAccess.forViewer(pet, UUID.randomUUID(), false, true).horseStats());
        assertEquals(pet.lastSeen(), PetAccess.forViewer(pet, UUID.randomUUID(), true, true).lastSeen());
    }

    @Test void snapshotsDropLocationsWhenTheViewerMayNotLocate() {
        UUID manager = UUID.randomUUID();
        pet.setOwner(owner, "Owner");
        pet.updateLastSeen(17, 64, -25, "minecraft:overworld", 12);
        AnimalRecord other = new AnimalRecord(UUID.randomUUID(), "minecraft:cat", "Tib", false,
                null, null, 1, 1000, false, null, null, UUID.randomUUID(), "Someone", null, null, null);
        other.updateLastSeen(5, 70, 5, "minecraft:the_nether", 3);
        List<AnimalRecord> records = List.of(pet, other);

        assertTrue(PetAccess.snapshot(records, owner, false, false, false, 10).stream().allMatch(r -> r.lastSeen() == null),
                "ownersCanLocate=false or a denied familytree.locate still sent the owner's coordinates");
        assertTrue(PetAccess.snapshot(records, manager, true, true, false, 10).stream().allMatch(r -> r.lastSeen() == null),
                "familytree.others.manage without familytree.locate still sent other players' coordinates");

        List<AnimalRecord> located = PetAccess.snapshot(records, owner, true, false, true, 10);
        assertEquals(2, located.size());
        for (AnimalRecord record : located)
            assertEquals(record.id().equals(pet.id()), record.lastSeen() != null, "only the owner's own pet keeps its position");
        assertTrue(PetAccess.snapshot(records, manager, true, true, true, 10).stream().allMatch(r -> r.lastSeen() != null));
        assertNotNull(pet.lastSeen());
    }

    @Test void snapshotsKeepOnlyVisibleRecordsInIdOrderUpToTheLimit() {
        pet.setOwner(owner, "Owner");
        AnimalRecord other = new AnimalRecord(UUID.randomUUID(), "minecraft:cat", "Tib", false,
                null, null, 1, 1000, false, null, null, UUID.randomUUID(), "Someone", null, null, null);
        assertEquals(List.of(pet.id()), PetAccess.snapshot(List.of(other, pet), owner, false, false, true, 10)
                .stream().map(AnimalRecord::id).toList());
        List<UUID> all = PetAccess.snapshot(List.of(other, pet), owner, true, false, true, 10).stream().map(AnimalRecord::id).toList();
        assertEquals(all.stream().sorted().toList(), all);
        assertEquals(1, PetAccess.snapshot(List.of(other, pet), owner, true, false, true, 1).size());
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
