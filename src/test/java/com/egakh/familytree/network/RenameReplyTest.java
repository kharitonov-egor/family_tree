package com.egakh.familytree.network;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.naming.PetRenaming;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RenameReplyTest {
    private final UUID owner = UUID.randomUUID();
    private final AnimalRecord saved = new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", "Rex", false,
            null, null, 1, 1000, false, null, null, owner, "Owner", null,
            new AnimalRecord.LastSeen(12345.25, 64, -98765.5, "minecraft:the_nether", 12), null);
    private final FamilyTreePackets.RenameOutcome renamed = new FamilyTreePackets.RenameOutcome(PetRenaming.Result.SUCCESS, saved);

    @Test
    void aRenameReplyHidesThePositionWhenTheViewerMayNotLocate() {
        AnimalRecord ownReply = FamilyTreePackets.renameReply(renamed, owner, false, false).orElseThrow();
        assertNull(ownReply.lastSeen(), "ownersCanLocate=false or a denied familytree.locate");
        AnimalRecord managerReply = FamilyTreePackets.renameReply(renamed, UUID.randomUUID(), true, false).orElseThrow();
        assertNull(managerReply.lastSeen(), "familytree.others.manage without familytree.locate");
        assertNotNull(saved.lastSeen(), "the saved record keeps its position");
    }

    @Test
    void aViewerWhoMayLocateStillGetsThePositionAndTheNewName() {
        saved.setTreeName("Moss");
        AnimalRecord reply = FamilyTreePackets.renameReply(renamed, owner, false, true).orElseThrow();
        assertEquals(saved.lastSeen(), reply.lastSeen());
        assertEquals("Moss", reply.name());
        assertNotSame(saved, reply);
    }

    @Test
    void onlyASuccessfulRenameSendsARecord() {
        assertTrue(FamilyTreePackets.renameReply(new FamilyTreePackets.RenameOutcome(PetRenaming.Result.NO_PERMISSION, saved),
                owner, false, true).isEmpty());
    }
}
