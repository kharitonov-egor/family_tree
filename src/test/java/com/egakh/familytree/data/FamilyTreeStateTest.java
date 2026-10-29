package com.egakh.familytree.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FamilyTreeStateTest {
    @Test void preservesMalformedRecordsAlongsideReadableRecordsOnTheNextSave() {
        FamilyTreeState original = new FamilyTreeState(); original.put(pet());
        JsonObject data = FamilyTreeState.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow().getAsJsonObject();
        JsonObject broken = new JsonObject(); broken.addProperty("name", "unreadable history");
        data.getAsJsonArray("records").add(broken);
        FamilyTreeState recovered = FamilyTreeState.CODEC.parse(JsonOps.INSTANCE, data).getOrThrow();
        assertEquals(1, recovered.all().size()); assertEquals(1, recovered.preservedRecordCount());
        JsonArray saved = FamilyTreeState.CODEC.encodeStart(JsonOps.INSTANCE, recovered).getOrThrow().getAsJsonObject().getAsJsonArray("records");
        assertEquals(2, saved.size()); assertEquals(broken, saved.get(1));
    }

    @Test void unsupportedFutureDataIsReadOnlyAndRoundTripsWithoutChangingAnything() {
        JsonObject future = new JsonObject(); future.addProperty("data_version", 999);
        future.addProperty("new_field", "keep me"); future.add("records", new JsonArray());
        FamilyTreeState protectedState = FamilyTreeState.CODEC.parse(JsonOps.INSTANCE, future).getOrThrow();
        assertTrue(protectedState.readOnly()); protectedState.put(pet());
        assertTrue(protectedState.all().isEmpty());
        assertEquals(future, FamilyTreeState.CODEC.encodeStart(JsonOps.INSTANCE, protectedState).getOrThrow());
    }

    @Test void malformedVersionAndNonMapDataStayReadOnlyAndUnchanged() {
        for (var input : java.util.List.of(com.google.gson.JsonParser.parseString("{\"data_version\":\"broken\",\"records\":[]}"),
                com.google.gson.JsonParser.parseString("[]"))) {
            var state = FamilyTreeState.CODEC.parse(JsonOps.INSTANCE, input).getOrThrow();
            assertTrue(state.readOnly());
            assertEquals(input, FamilyTreeState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow());
        }
    }

    @Test void duplicateIdsArePreservedAndAnEmptiedStoreStaysEmptyOnReload() {
        var state = new FamilyTreeState(); state.put(pet());
        var data = FamilyTreeState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().getAsJsonObject();
        data.getAsJsonArray("records").add(data.getAsJsonArray("records").get(0).deepCopy());
        var decoded = FamilyTreeState.CODEC.parse(JsonOps.INSTANCE, data).getOrThrow();
        assertEquals(1, decoded.all().size()); assertEquals(1, decoded.preservedRecordCount());
        assertEquals(2, FamilyTreeState.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow()
                .getAsJsonObject().getAsJsonArray("records").size());
        state.removeMatching(record -> true);
        var empty = FamilyTreeState.CODEC.parse(JsonOps.INSTANCE,
                FamilyTreeState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow();
        assertTrue(empty.all().isEmpty());
    }

    @Test void farmCountFollowsNewRecordsNamesAndRemovals() {
        var state = new FamilyTreeState();
        state.put(pet());
        assertEquals(0, state.unnamedFarmRecords());
        AnimalRecord cow = farm("minecraft:cow"), sheep = farm("minecraft:sheep");
        state.put(cow);
        state.put(sheep);
        assertEquals(2, state.unnamedFarmRecords());
        state.update(sheep.id(), record -> record.rename("Dolly", false));
        assertEquals(1, state.unnamedFarmRecords(), "Naming through update is counted without a recount");
        state.update(cow.id(), record -> record.setTreeName("Bessie"));
        assertEquals(0, state.unnamedFarmRecords());
        cow.setTreeName(null);
        state.recordEdited();
        assertEquals(1, state.unnamedFarmRecords(), "A screen rename outside update is counted after recordEdited");
        assertEquals(1, state.removeAll(java.util.List.of(cow.id(), UUID.randomUUID())));
        assertEquals(0, state.unnamedFarmRecords());
        assertEquals(2, state.all().size());
    }

    @Test void releaseOneTwoHistoryLoadsUnchangedNextToFarmAnimals() {
        var saved = com.google.gson.JsonParser.parseString("""
                {"data_version":1,"records":[
                 {"id":[1,2,3,4],"species":"minecraft:wolf","name":"Moss","birth_world_day":2,
                  "birth_epoch":1000,"deceased":true,"death_world_day":82,"death_epoch":5000,"owner":[5,6,7,8],
                  "owner_name":"Alex","variant_id":"minecraft:pale","death_cause":{"attacker":"Creeper","msg_id":"explosion"}},
                 {"id":[9,10,11,12],"species":"minecraft:horse","name":"Comet","parent_a":[1,2,3,4],
                  "birth_world_day":12,"birth_epoch":1700000000000,"owner":[5,6,7,8],"owner_name":"Alex",
                  "variant_id":"chestnut/white_dots","tree_name":"Old Comet",
                  "horse_stats":{"speed":0.3375,"jump_strength":0.7,"max_health":30.0}}]}""").getAsJsonObject();
        FamilyTreeState state = FamilyTreeState.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(2, state.all().size());
        assertEquals(0, state.unnamedFarmRecords());
        AnimalRecord lamb = farm("minecraft:sheep");
        lamb.setVariantId("pink");
        state.put(lamb);
        JsonArray records = FamilyTreeState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()
                .getAsJsonObject().getAsJsonArray("records");
        for (var old : saved.getAsJsonArray("records")) assertTrue(records.contains(old), old.toString());
        assertEquals(3, records.size());
    }

    private static AnimalRecord farm(String species) {
        return new AnimalRecord(UUID.randomUUID(), species, "Cow 1A2B", true, null, null,
                0, 0, false, null, null, UUID.randomUUID(), "Alex", null, null, null);
    }

    private AnimalRecord pet() {
        return new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", "Rover", false, null, null,
                0, 0, false, null, null, null, null, null, null, null);
    }
}
