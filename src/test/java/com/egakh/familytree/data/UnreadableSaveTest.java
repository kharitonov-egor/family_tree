package com.egakh.familytree.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Review B1 and m2: a history file that can't be read is never written over, and the next start reads it again. */
class UnreadableSaveTest {
    @TempDir Path world;

    /**
     * Cuts the file just after the gzip header. A cut deeper in builds a crash report, which needs SharedConstants,
     * and NeoForge 1.21.10 and newer can't load that class outside FML. The real-server runs cover cuts mid-file.
     */
    private static byte[] truncate(byte[] file) {
        return Arrays.copyOf(file, 12);
    }

    @Test void aTruncatedSaveLocksTheFreshStateAndKeepsTheOriginalBytes() throws IOException {
        Path saved = world.resolve("data/familytree.dat");
        byte[] truncated = truncate(history(saved, 3));
        Files.write(saved, truncated);

        FamilyTreeState fresh = new FamilyTreeState();
        fresh.setDirty();
        assertNull(FamilyTreeState.loadWithoutMinecraft(saved, saved, fresh));
        assertTrue(fresh.readOnly());
        assertEquals(saved, fresh.unreadableFile());
        assertFalse(fresh.isDirty(), "Minecraft saves only dirty data, so a locked state must never report dirty");
        fresh.put(pet());
        fresh.setDirty();
        assertFalse(fresh.isDirty());
        assertTrue(fresh.all().isEmpty());
        assertArrayEquals(truncated, Files.readAllBytes(saved));
        assertEquals(1, copies(saved).size());
        assertArrayEquals(truncated, Files.readAllBytes(copies(saved).get(0)));

        FamilyTreeState restart = new FamilyTreeState();
        assertNull(FamilyTreeState.loadWithoutMinecraft(saved, saved, restart));
        assertTrue(restart.readOnly());
        assertEquals(1, copies(saved).size(), "A second start with the same broken file keeps the one copy");
    }

    @Test void aSaveMinecraftCouldNotReadIsReadDirectly() throws IOException {
        Path saved = world.resolve("data/familytree.dat");
        history(saved, 2);
        FamilyTreeState fresh = new FamilyTreeState();
        FamilyTreeState loaded = FamilyTreeState.loadWithoutMinecraft(saved, saved, fresh);
        assertNotNull(loaded);
        assertEquals(2, loaded.all().size());
        assertFalse(loaded.readOnly());
        assertNull(loaded.unreadableFile());
        assertTrue(Files.exists(saved.resolveSibling("familytree.dat.migration-backup")));
    }

    @Test void aFailed26xImportStaysLockedWritesNothingAndRetriesNextStart() throws IOException {
        Path saved = world.resolve("dimensions/minecraft/overworld/data/familytree/familytree.dat");
        Path legacy = world.resolve("data/familytree.dat");
        byte[] whole = history(legacy, 4);
        Files.write(legacy, truncate(whole));

        FamilyTreeState fresh = new FamilyTreeState();
        fresh.setDirty();
        assertNull(FamilyTreeState.loadWithoutMinecraft(saved, legacy, fresh));
        assertTrue(fresh.readOnly());
        assertFalse(fresh.isDirty());
        assertFalse(Files.exists(saved), "No modern file, so the next start imports again");

        Files.write(legacy, whole);
        FamilyTreeState imported = FamilyTreeState.loadWithoutMinecraft(saved, legacy, new FamilyTreeState());
        assertNotNull(imported);
        assertEquals(4, imported.all().size());
        assertFalse(imported.readOnly());
    }

    @Test void aFileWithoutRecordsIsNotTreatedAsAnEmptyHistory() throws IOException {
        Path saved = world.resolve("data/familytree.dat");
        Files.createDirectories(saved.getParent());
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", 3955);
        try (OutputStream out = Files.newOutputStream(saved)) {
            NbtIo.writeCompressed(root, out);
        }
        FamilyTreeState fresh = new FamilyTreeState();
        assertNull(FamilyTreeState.loadWithoutMinecraft(saved, saved, fresh));
        assertTrue(fresh.readOnly());
    }

    @Test void rawRecordsWithoutTheSavedDataWrapperStillImport() throws IOException {
        Path legacy = world.resolve("data/familytree.dat");
        Files.createDirectories(legacy.getParent());
        FamilyTreeState state = new FamilyTreeState();
        state.put(pet());
        try (OutputStream out = Files.newOutputStream(legacy)) {
            NbtIo.writeCompressed((CompoundTag) FamilyTreeState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow(), out);
        }
        FamilyTreeState imported = FamilyTreeState.loadWithoutMinecraft(world.resolve("missing.dat"), legacy, new FamilyTreeState());
        assertNotNull(imported);
        assertEquals(1, imported.all().size());
    }

    /** Before 1.21.5, Minecraft hands the codec an empty compound when the file has no data wrapper. */
    @Test void aRawHistoryMinecraftReadsAsEmptyIsReadDirectlyInsteadOfSavedOver() throws IOException {
        Path saved = world.resolve("data/familytree.dat");
        Files.createDirectories(saved.getParent());
        FamilyTreeState raw = new FamilyTreeState();
        raw.put(pet());
        raw.put(pet());
        try (OutputStream out = Files.newOutputStream(saved)) {
            NbtIo.writeCompressed((CompoundTag) FamilyTreeState.CODEC.encodeStart(NbtOps.INSTANCE, raw).getOrThrow(), out);
        }
        FamilyTreeState fromMinecraft = FamilyTreeState.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        FamilyTreeState used = FamilyTreeState.afterMinecraft(fromMinecraft, saved, saved);
        assertNotSame(fromMinecraft, used);
        assertEquals(2, used.all().size());
        assertFalse(used.readOnly());
    }

    @Test void aRejected26xImportLocksWithoutWritingSoTheNextStartRetries() throws IOException {
        Path saved = world.resolve("dimensions/minecraft/overworld/data/familytree/familytree.dat");
        Path legacy = world.resolve("data/familytree.dat");
        Files.createDirectories(legacy.getParent());
        FamilyTreeState state = new FamilyTreeState();
        state.put(pet());
        CompoundTag data = (CompoundTag) FamilyTreeState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        data.putInt("data_version", 2);
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        try (OutputStream out = Files.newOutputStream(legacy)) {
            NbtIo.writeCompressed(root, out);
        }
        FamilyTreeState fresh = new FamilyTreeState();
        fresh.setDirty();
        assertSame(fresh, FamilyTreeState.afterMinecraft(fresh, saved, legacy));
        assertTrue(fresh.readOnly());
        assertFalse(fresh.isDirty());
        assertEquals(legacy, fresh.unreadableFile());

        data.putInt("data_version", 1);
        try (OutputStream out = Files.newOutputStream(legacy)) {
            NbtIo.writeCompressed(root, out);
        }
        FamilyTreeState imported = FamilyTreeState.afterMinecraft(new FamilyTreeState(), saved, legacy);
        assertEquals(1, imported.all().size());
        assertFalse(imported.readOnly());
    }

    @Test void aNewWorldHasNothingToRead() {
        Path saved = world.resolve("data/familytree.dat");
        FamilyTreeState fresh = new FamilyTreeState();
        assertNull(FamilyTreeState.loadWithoutMinecraft(saved, saved, fresh));
        assertFalse(fresh.readOnly());
    }

    private static byte[] history(Path file, int pets) throws IOException {
        Files.createDirectories(file.getParent());
        FamilyTreeState state = new FamilyTreeState();
        for (int i = 0; i < pets; i++) state.put(pet());
        CompoundTag root = new CompoundTag();
        root.put("data", FamilyTreeState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow());
        root.putInt("DataVersion", 3955);
        try (OutputStream out = Files.newOutputStream(file)) {
            NbtIo.writeCompressed(root, out);
        }
        return Files.readAllBytes(file);
    }

    private static List<Path> copies(Path file) throws IOException {
        try (Stream<Path> siblings = Files.list(file.getParent())) {
            return siblings.filter(path -> path.getFileName().toString().startsWith(file.getFileName() + ".unreadable-")).toList();
        }
    }

    private static AnimalRecord pet() {
        return new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", "Rover", false, null, null,
                0, 0, false, null, null, null, null, null, null, null);
    }
}
