package com.egakh.familytree.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** QA R10: backups carry the date and time instead of random numbers. */
class BackupFilesTest {
    @TempDir Path directory;

    @Test void backupsAreNamedByDateAndNeverReplaceEachOther() throws IOException {
        LocalDateTime time = LocalDateTime.of(2026, 10, 31, 14, 5, 9);
        Path first = BackupFiles.write(directory, "before-prune", ".json", "{}", time);
        Path second = BackupFiles.write(directory, "before-prune", ".json", "[]", time);
        assertEquals("before-prune-2026-10-31_14-05-09.json", first.getFileName().toString());
        assertEquals("before-prune-2026-10-31_14-05-09-2.json", second.getFileName().toString());
        assertEquals("{}", Files.readString(first));
        assertEquals("[]", Files.readString(second));
    }

    @Test void anUnreadableFileIsCopiedOncePerDistinctContent() throws IOException {
        Path file = directory.resolve("familytree.dat");
        Files.write(file, new byte[]{1, 2, 3});
        Path copy = BackupFiles.keepUnreadable(file, LocalDateTime.of(2026, 10, 31, 1, 2, 3));
        assertEquals("familytree.dat.unreadable-2026-10-31_01-02-03", copy.getFileName().toString());
        assertEquals(copy, BackupFiles.keepUnreadable(file, LocalDateTime.of(2026, 11, 1, 1, 2, 3)));
        Files.write(file, new byte[]{4});
        Path changed = BackupFiles.keepUnreadable(file, LocalDateTime.of(2026, 10, 31, 1, 2, 3));
        assertNotEquals(copy, changed);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(copy));
        assertArrayEquals(new byte[]{4}, Files.readAllBytes(changed));
    }
}
