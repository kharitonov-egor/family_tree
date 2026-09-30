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

    private AnimalRecord pet() {
        return new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", "Rover", false, null, null,
                0, 0, false, null, null, null, null, null, null, null);
    }
}
