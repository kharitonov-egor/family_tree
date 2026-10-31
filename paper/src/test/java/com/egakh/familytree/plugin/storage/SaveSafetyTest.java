package com.egakh.familytree.plugin.storage;

import com.egakh.familytree.data.AnimalRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Review B1, m1, and m2 in the plugin. */
class SaveSafetyTest {
    private static final Logger LOGGER = Logger.getLogger("test");

    @TempDir Path temp;

    @Test void anUnreadableFileIsCopiedAndReportedButNeverWritten() throws IOException {
        StoreFiles files = StoreFiles.forLevel(temp, false);
        Files.createDirectories(files.file().getParent());
        byte[] truncated = Arrays.copyOf(fixtureBytes(), 64);
        Files.write(files.file(), truncated);

        FamilyTreeStore store = files.load(LOGGER);
        assertTrue(store.readOnly());
        assertFalse(store.dirty());
        assertEquals(files.file(), store.unreadableFile());
        assertEquals("data/familytree.dat", files.shown(store.unreadableFile()));
        assertArrayEquals(truncated, Files.readAllBytes(files.file()));
        List<Path> copies = copies(files.file());
        assertEquals(1, copies.size());
        assertArrayEquals(truncated, Files.readAllBytes(copies.get(0)));
        files.load(LOGGER);
        assertEquals(1, copies(files.file()).size());
    }

    @Test void aFailed26xImportWritesNoModernFileAndTheNextStartImports() throws IOException {
        Path legacy = temp.resolve("data/familytree.dat");
        Files.createDirectories(legacy.getParent());
        byte[] whole = fixtureBytes();
        Files.write(legacy, Arrays.copyOf(whole, whole.length / 2));
        StoreFiles modern = StoreFiles.forLevel(temp, true);

        FamilyTreeStore locked = modern.load(LOGGER);
        assertTrue(locked.readOnly());
        assertFalse(locked.dirty());
        assertEquals(legacy, locked.unreadableFile());
        assertFalse(Files.exists(modern.file()));

        Files.write(legacy, whole);
        FamilyTreeStore imported = modern.load(LOGGER);
        assertFalse(imported.readOnly());
        assertEquals(14, imported.all().size());
        assertTrue(imported.dirty());
    }

    @Test void theShutdownSaveIsWrittenAfterOlderQueuedSaves() throws Exception {
        StoreFiles files = StoreFiles.forLevel(temp, false);
        ExecutorService writer = Executors.newSingleThreadExecutor();
        CountDownLatch busy = new CountDownLatch(1);
        writer.execute(() -> {
            try {
                busy.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        SaveQueue saves = new SaveQueue(files, LOGGER, writer);
        FamilyTreeStore store = FamilyTreeStore.empty();
        for (int records = 1; records <= 3; records++) {
            store.put(new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", "Rex " + records, false, null, null,
                    1, 2, false, null, null, null, null, null, null, null));
            saves.submit(StoreFiles.encode(store, 3955), () -> fail("write failed"));
        }
        busy.countDown();
        assertTrue(saves.finish(10, TimeUnit.SECONDS));
        assertEquals(3, files.load(LOGGER).all().size(), "the last save submitted is the one on disk");
        saves.submit(StoreFiles.encode(FamilyTreeStore.empty(), 3955), () -> fail("write failed"));
        assertTrue(files.load(LOGGER).all().isEmpty(), "after shutdown, a save writes on the calling thread");
    }

    private byte[] fixtureBytes() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/mod-1.21.11.dat")) {
            return in.readAllBytes();
        }
    }

    private static List<Path> copies(Path file) throws IOException {
        try (Stream<Path> siblings = Files.list(file.getParent())) {
            return siblings.filter(path -> path.getFileName().toString().startsWith("familytree.dat.unreadable-")).toList();
        }
    }
}
