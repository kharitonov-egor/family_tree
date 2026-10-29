package com.egakh.familytree.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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
        assertNull(decoded.horseStats());
        assertEquals(oldData, AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());
    }

    @Test
    void readsAHorseSavedByVersionOneOneWithUnknownStats() {
        var saved = JsonParser.parseString("""
                {"id":[1,2,3,4],"species":"minecraft:horse","name":"Comet",
                 "parent_a":[5,6,7,8],"birth_world_day":12,"birth_epoch":1700000000000,
                 "owner":[9,10,11,12],"owner_name":"Alex",
                 "last_seen":{"x":1.5,"y":64.0,"z":-3.5,"dimension":"minecraft:overworld","world_day":20},
                 "tree_name":"Old Comet"}""");
        AnimalRecord comet = AnimalRecord.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals("Old Comet", comet.name());
        assertEquals("Comet", comet.originalName());
        assertEquals("minecraft:horse", comet.speciesId());
        assertNull(comet.horseStats());
        assertNull(comet.variantId());
        assertEquals(saved, AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, comet).getOrThrow());
    }

    @Test
    void horseStatsRoundTripInSavesWithoutChangingTheOriginalFields() {
        AnimalRecord horse = pet();
        var oldData = AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, horse).getOrThrow().getAsJsonObject();
        horse.setHorseStats(new HorseStats(0.3375, 1.0, 30.0, 4));
        var data = AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, horse).getOrThrow().getAsJsonObject();
        for (String field : oldData.keySet()) assertEquals(oldData.get(field), data.get(field), field);
        assertEquals(0.3375, data.getAsJsonObject("horse_stats").get("speed").getAsDouble());
        AnimalRecord decoded = AnimalRecord.CODEC.parse(JsonOps.INSTANCE, data).getOrThrow();
        assertEquals(new HorseStats(0.3375, 1.0, 30.0, 4), decoded.horseStats());

        var nbt = (CompoundTag) AnimalRecord.CODEC.encodeStart(NbtOps.INSTANCE, horse).getOrThrow();
        assertEquals(new HorseStats(0.3375, 1.0, 30.0, 4),
                AnimalRecord.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow().horseStats());
    }

    @Test
    void missingStatValuesStayUnknown() {
        AnimalRecord horse = pet();
        horse.setHorseStats(new HorseStats(0.2, null, 22.0, null));
        var data = AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, horse).getOrThrow().getAsJsonObject();
        assertFalse(data.getAsJsonObject("horse_stats").has("jump_strength"));
        assertFalse(data.getAsJsonObject("horse_stats").has("strength"));
        data.add("horse_stats", JsonParser.parseString("{}"));
        HorseStats empty = AnimalRecord.CODEC.parse(JsonOps.INSTANCE, data).getOrThrow().horseStats();
        assertEquals(new HorseStats(null, null, null, null), empty);
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
            first.setHorseStats(new HorseStats(0.25, 0.75, 26.5, null));
            AnimalRecord second = pet();
            second.setHorseStats(new HorseStats(0.175, 0.5, 18.0, 3));
            AnimalRecord.STREAM_CODEC.encode(buffer, first);
            AnimalRecord.STREAM_CODEC.encode(buffer, second);
            AnimalRecord decodedFirst = AnimalRecord.STREAM_CODEC.decode(buffer);
            AnimalRecord decodedSecond = AnimalRecord.STREAM_CODEC.decode(buffer);
            assertEquals(AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, first).getOrThrow(),
                    AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, decodedFirst).getOrThrow());
            assertEquals(AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, second).getOrThrow(),
                    AnimalRecord.CODEC.encodeStart(JsonOps.INSTANCE, decodedSecond).getOrThrow());
            assertEquals(new HorseStats(0.175, 0.5, 18.0, 3), decodedSecond.horseStats());
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
