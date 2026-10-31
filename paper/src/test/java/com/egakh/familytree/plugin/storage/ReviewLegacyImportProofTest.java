package com.egakh.familytree.plugin.storage;

import com.egakh.familytree.data.AnimalRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proof-of-bug tests from the 1.4.0 code review. They fail on d689590. */
class ReviewLegacyImportProofTest {
    private static final Logger LOGGER = Logger.getLogger("test");

    @TempDir Path temp;

    private void writeLegacy(Nbt.Compound root) throws IOException {
        Path legacy = temp.resolve("data/familytree.dat");
        Files.createDirectories(legacy.getParent());
        try (OutputStream out = Files.newOutputStream(legacy)) {
            Nbt.writeCompressed(root, out);
        }
    }

    @Test
    void unsupportedLegacyVersionStaysLockedOn26x() throws IOException {
        Nbt.Compound fixture;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/mod-1.21.11.dat")) {
            fixture = Nbt.readCompressed(in);
        }
        Nbt.Compound data = (Nbt.Compound) fixture.get("data");
        data.put("data_version", 2);
        writeLegacy(fixture);

        StoreFiles modern = StoreFiles.forLevel(temp, true);
        FamilyTreeStore store = modern.load(LOGGER);

        store.put(new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", "Rex", false, null, null,
                1, 2, false, null, null, null, null, null, null, null));
        assertTrue(store.readOnly(), "a 26.x world whose only history has data_version 2 must not get a writable empty store");
    }

    @Test
    void unreadableLegacyFileStaysLockedOn26x() throws IOException {
        Path legacy = temp.resolve("data/familytree.dat");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, new byte[]{0x1f, (byte) 0x8b, 8, 0});

        FamilyTreeStore store = StoreFiles.forLevel(temp, true).load(LOGGER);

        assertTrue(store.readOnly(), "a truncated legacy file must lock the store, as an unreadable modern file does");
    }
}
