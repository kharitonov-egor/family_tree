package com.egakh.familytree.plugin.storage;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** The fixtures are real {@code familytree.dat} files written by the mod's smoke runs on 1.21.11 and 26.3. */
class StoreCompatibilityTest {
    private static final Logger LOGGER = Logger.getLogger("test");

    @TempDir Path temp;

    private static Nbt.Compound fixture(String name) throws IOException {
        try (InputStream in = StoreCompatibilityTest.class.getResourceAsStream("/fixtures/" + name)) {
            return Nbt.readCompressed(in);
        }
    }

    @Test void readsEveryRecordTheModWroteOn1_21_11() throws IOException {
        Nbt.Compound root = fixture("mod-1.21.11.dat");
        assertEquals(4671, root.get("DataVersion"));
        FamilyTreeStore store = FamilyTreeStore.decode(root.get("data"), LOGGER);
        assertFalse(store.readOnly());
        assertEquals(14, store.all().size());
        assertEquals(0, store.preservedRecordCount());
        assertTrue(store.all().stream().anyMatch(AnimalRecord::deceased));
        assertTrue(store.all().stream().anyMatch(record -> record.treeName() != null));
        AnimalRecord dead = store.all().stream().filter(AnimalRecord::deceased).findFirst().orElseThrow();
        assertNotNull(dead.deathWorldDay());
        assertNotNull(dead.deathCause());
        assertTrue(store.all().stream().anyMatch(record -> record.horseStats() != null
                && record.horseStats().speed() != null && record.speciesId().equals("minecraft:horse")));
        assertTrue(store.all().stream().anyMatch(record -> record.horseStats() != null && record.horseStats().strength() != null));
    }

    @Test void writesTheSameNbtTheModWrote() throws IOException {
        for (String name : List.of("mod-1.21.11.dat", "mod-26.3.dat")) {
            Nbt.Compound data = (Nbt.Compound) fixture(name).get("data");
            FamilyTreeStore store = FamilyTreeStore.decode(data, LOGGER);
            assertEquals(data, store.encodeData(), name);
        }
    }

    @Test void encodedRecordsMatchTheModsFieldTypesForANewRecord() {
        AnimalRecord pet = new AnimalRecord(new UUID(1, 2), "minecraft:wolf", "Rover", false, new UUID(3, 4), null,
                7, 1234, true, 15L, null, new UUID(5, 6), "Owner", "minecraft:pale",
                new AnimalRecord.LastSeen(1.5, 64, -8, "minecraft:overworld", 14), new AnimalRecord.DeathCause("Creeper", null));
        pet.setTreeName("Snowball");
        pet.setHorseStats(new HorseStats(0.3375, null, 30.0, 3));
        Nbt.Compound tag = RecordNbt.encode(pet, null);
        assertArrayEquals(new int[]{0, 1, 0, 2}, (int[]) tag.get("id"));
        assertEquals("minecraft:wolf", tag.get("species"));
        assertFalse(tag.has("name_auto"), "false booleans are omitted like optionalFieldOf defaults");
        assertEquals((byte) 1, tag.get("deceased"));
        assertEquals(7L, tag.get("birth_world_day"));
        assertEquals(15L, tag.get("death_world_day"));
        assertFalse(tag.has("death_epoch"));
        assertFalse(tag.has("parent_b"));
        Nbt.Compound seen = (Nbt.Compound) tag.get("last_seen");
        assertEquals(1.5, seen.get("x"));
        assertEquals(14L, seen.get("world_day"));
        Nbt.Compound cause = (Nbt.Compound) tag.get("death_cause");
        assertEquals("Creeper", cause.get("attacker"));
        assertFalse(cause.has("msg_id"));
        Nbt.Compound stats = (Nbt.Compound) tag.get("horse_stats");
        assertFalse(stats.has("jump_strength"));
        assertEquals(3, stats.get("strength"));
    }

    @Test void keepsFieldsItDoesNotKnow() throws RecordNbt.InvalidRecord {
        Nbt.Compound tag = RecordNbt.encode(new AnimalRecord(UUID.randomUUID(), "minecraft:cat", "Tom", false, null, null,
                1, 2, false, null, null, null, null, null, null, null), null);
        tag.put("future_field", "kept");
        Nbt.Compound stats = new Nbt.Compound();
        stats.put("speed", 0.2);
        stats.put("future_stat", 9);
        tag.put("horse_stats", stats);
        AnimalRecord record = RecordNbt.decode(tag);
        record.rename("Thomas", false);
        Nbt.Compound written = RecordNbt.encode(record, tag);
        assertEquals("kept", written.get("future_field"));
        assertEquals(9, ((Nbt.Compound) written.get("horse_stats")).get("future_stat"));
        assertEquals("Thomas", written.get("name"));
    }

    @Test void preservesUnreadableAndDuplicateRecords() {
        UUID id = UUID.randomUUID();
        Nbt.Compound good = RecordNbt.encode(new AnimalRecord(id, "minecraft:wolf", "Rex", false, null, null,
                1, 2, false, null, null, null, null, null, null, null), null);
        Nbt.Compound duplicate = good.copy();
        duplicate.put("name", "Other Rex");
        Nbt.Compound broken = good.copy();
        broken.remove("species");
        broken.put("id", RecordNbt.uuid(UUID.randomUUID()));
        Nbt.Compound wrongType = good.copy();
        wrongType.put("id", RecordNbt.uuid(UUID.randomUUID()));
        wrongType.put("owner", "not a uuid");
        Nbt.ListTag records = new Nbt.ListTag();
        for (Nbt.Compound entry : List.of(good, duplicate, broken, wrongType)) records.add(entry);
        Nbt.Compound data = new Nbt.Compound();
        data.put("data_version", 1);
        data.put("records", records);
        FamilyTreeStore store = FamilyTreeStore.decode(data, LOGGER);
        assertEquals(1, store.all().size());
        assertEquals(3, store.preservedRecordCount());
        store.update(id, record -> record.rename("Rexy", false));
        List<Object> written = ((Nbt.ListTag) store.encodeData().get("records")).values();
        assertEquals(4, written.size());
        assertTrue(written.containsAll(List.of(duplicate, broken, wrongType)));
    }

    @Test void unsupportedVersionsDisableWritesAndKeepTheFile() throws IOException {
        for (Object version : new Object[]{2, -1, 1.5, "one"}) {
            Nbt.Compound data = new Nbt.Compound();
            data.put("data_version", version);
            data.put("records", new Nbt.ListTag());
            FamilyTreeStore store = FamilyTreeStore.decode(data, LOGGER);
            assertTrue(store.readOnly(), String.valueOf(version));
            store.put(new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", "Rex", false, null, null,
                    1, 2, false, null, null, null, null, null, null, null));
            assertTrue(store.all().isEmpty());
            assertFalse(store.dirty());
        }
        Nbt.Compound notAList = new Nbt.Compound();
        notAList.put("records", "nope");
        assertTrue(FamilyTreeStore.decode(notAList, LOGGER).readOnly());
    }

    @Test void anUnreadableFileIsLockedAndNeverOverwritten() throws IOException {
        StoreFiles files = StoreFiles.forLevel(temp, false);
        Files.createDirectories(files.file().getParent());
        Files.write(files.file(), new byte[]{1, 2, 3});
        FamilyTreeStore store = files.load(LOGGER);
        assertTrue(store.readOnly());
        assertFalse(store.dirty());
    }

    @Test void a26xWorldImportsTheOlderFileOnceAndKeepsACopy() throws IOException {
        StoreFiles older = StoreFiles.forLevel(temp, false);
        Files.createDirectories(older.file().getParent());
        try (InputStream in = getClass().getResourceAsStream("/fixtures/mod-1.21.11.dat")) {
            Files.copy(in, older.file());
        }
        StoreFiles modern = StoreFiles.forLevel(temp, true);
        assertEquals(temp.resolve("dimensions/minecraft/overworld/data/familytree/familytree.dat"), modern.file());
        FamilyTreeStore imported = modern.load(LOGGER);
        assertEquals(14, imported.all().size());
        assertTrue(imported.dirty());
        assertTrue(Files.exists(temp.resolve("data/familytree.dat.migration-backup")));
        imported.removeMatching(record -> true);
        modern.write(StoreFiles.encode(imported, 5023));
        assertTrue(modern.load(LOGGER).all().isEmpty(), "an empty modern store must not import deleted history again");
    }

    @Test void savedFilesReloadWithTheirDataVersion() throws IOException {
        StoreFiles files = StoreFiles.forLevel(temp, false);
        FamilyTreeStore store = files.load(LOGGER);
        assertTrue(store.all().isEmpty());
        store.put(new AnimalRecord(new UUID(7, 8), "minecraft:sheep", "Sheep 1A2B3C", true, null, null,
                3, 4, false, null, null, new UUID(9, 9), "Alex", "pink", null, null));
        files.write(StoreFiles.encode(store, 3955));
        Nbt.Compound root = StoreFiles.read(files.file());
        assertEquals(3955, root.get("DataVersion"));
        FamilyTreeStore reloaded = files.load(LOGGER);
        AnimalRecord sheep = reloaded.get(new UUID(7, 8));
        assertEquals("pink", sheep.variantId());
        assertTrue(sheep.nameAutogenerated());
        assertEquals("Alex", sheep.ownerName());
    }

    @Test void backupsUseTheModsJsonLayout() throws IOException {
        FamilyTreeStore store = FamilyTreeStore.empty();
        store.put(new AnimalRecord(new UUID(0, 1), "minecraft:wolf", "Rex", true, null, null,
                3, 4, true, 9L, null, null, null, null, null, null));
        Path backup = store.backup(temp.resolve("familytree-backups"), "before-prune", true);
        var json = com.google.gson.JsonParser.parseString(Files.readString(backup)).getAsJsonObject();
        assertEquals(1, json.get("data_version").getAsInt());
        var record = json.getAsJsonArray("records").get(0).getAsJsonObject();
        assertEquals(4, record.getAsJsonArray("id").size());
        assertTrue(record.get("name_auto").getAsBoolean());
        assertTrue(record.get("deceased").getAsBoolean());
        assertEquals(9, record.get("death_world_day").getAsLong());
        assertTrue(backup.getFileName().toString().matches("before-prune-\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.json"),
                backup.getFileName().toString());
    }
}
