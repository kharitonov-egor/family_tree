package com.egakh.familytree.data;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnimalRecordCodecTest {
    @Test
    void readsOldRecordsWithoutAnOverrideAndKeepsEveryExistingField() {
        AnimalRecord original = pet();
        var oldData = AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow().getAsJsonObject();
        assertFalse(oldData.has("tree_name"));
        AnimalRecord decoded = AnimalRecord.CODEC.parse(JsonOps.INSTANCE, oldData).getOrThrow();
        assertEquals("Rover", decoded.name());
        assertNull(decoded.treeName());
        assertEquals(oldData, AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());
    }

    @Test
    void treeNamesRoundTripWithoutReplacingTheOldNameField() {
        AnimalRecord original = pet();
        var oldData = AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow().getAsJsonObject();
        original.setTreeName("Snowball");
        var data = AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow().getAsJsonObject();
        assertEquals("Rover", data.get("name").getAsString());
        assertEquals("Snowball", data.get("tree_name").getAsString());
        var decoded = AnimalRecord.CODEC.parse(JsonOps.INSTANCE, data).getOrThrow();
        assertEquals("Snowball", decoded.name());
        assertEquals("Rover", decoded.originalName());
        decoded.setTreeName(null);
        assertEquals(oldData, AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());
    }

    @Test
    void networkRoundTripKeepsBothNamesAndConsecutiveRecordsAligned() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AnimalRecord first = pet();
            first.setTreeName("Snowball");
            AnimalRecord second = pet();
            AnimalRecord.STREAM_CODEC.encode(buffer, first);
            AnimalRecord.STREAM_CODEC.encode(buffer, second);
            AnimalRecord decodedFirst = AnimalRecord.STREAM_CODEC.decode(buffer);
            AnimalRecord decodedSecond = AnimalRecord.STREAM_CODEC.decode(buffer);
            assertEquals(AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, first).getOrThrow(),
                    AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, decodedFirst).getOrThrow());
            assertEquals(AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, second).getOrThrow(),
                    AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, decodedSecond).getOrThrow());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private AnimalRecord pet() {
        return new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", "Rover", false,
                UUID.randomUUID(), UUID.randomUUID(), 7, 1234, true, 15L, 5678L,
                UUID.randomUUID(), "Owner", "minecraft:pale",
                new AnimalRecord.LastSeen(12, 64, -8, "minecraft:overworld", 14),
                new AnimalRecord.DeathCause("Creeper", "explosion"));
    }
}
