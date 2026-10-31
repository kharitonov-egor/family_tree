package com.egakh.familytree.plugin.network;

import com.egakh.familytree.data.AnimalRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RenameReplyTest {
    private final UUID owner = UUID.randomUUID();
    private final AnimalRecord saved = new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", "Rex", false,
            null, null, 1, 1000, false, null, null, owner, "Owner", null,
            new AnimalRecord.LastSeen(12345.25, 64, -98765.5, "minecraft:the_nether", 12), null);

    private static AnimalRecord decode(byte[] reply) {
        Wire.Reader in = new Wire.Reader(reply);
        in.uuid();
        in.varInt();
        return in.bool() ? Payloads.record(in) : null;
    }

    @Test
    void aRenameReplyHidesThePositionWhenTheViewerMayNotLocate() {
        assertNull(decode(ClientBridge.renameReply(UUID.randomUUID(), 0, saved, owner, false, false)).lastSeen());
        assertNull(decode(ClientBridge.renameReply(UUID.randomUUID(), 0, saved, UUID.randomUUID(), true, false)).lastSeen());
        assertNotNull(saved.lastSeen());
    }

    @Test
    void aViewerWhoMayLocateStillGetsThePosition() {
        assertEquals(saved.lastSeen(), decode(ClientBridge.renameReply(UUID.randomUUID(), 0, saved, owner, false, true)).lastSeen());
        assertNull(decode(ClientBridge.renameReply(UUID.randomUUID(), 6, null, owner, false, true)));
    }
}
