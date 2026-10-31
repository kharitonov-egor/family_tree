package com.egakh.familytree.data;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

/** Dated copies of the history: prune backups and copies of a history file Family Tree could not read. */
public final class BackupFiles {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private BackupFiles() {}

    public static String timestamp(LocalDateTime time) {
        return time.format(TIME);
    }

    /** Writes {@code prefix-<date>_<time>.json}, adding -2, -3, and so on when that name is taken. */
    public static Path write(Path directory, String prefix, String suffix, String content) throws IOException {
        return write(directory, prefix, suffix, content, LocalDateTime.now());
    }

    static Path write(Path directory, String prefix, String suffix, String content, LocalDateTime time) throws IOException {
        Files.createDirectories(directory);
        String base = prefix + "-" + timestamp(time);
        for (int attempt = 1; ; attempt++) {
            Path file = directory.resolve(base + (attempt == 1 ? "" : "-" + attempt) + suffix);
            try {
                Files.writeString(file, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return file;
            } catch (FileAlreadyExistsException taken) {
                if (attempt >= 1000) throw taken;
            }
        }
    }

    /**
     * Copies {@code file} to {@code <name>.unreadable-<date>_<time>} beside it, unless an earlier copy has the same bytes.
     * Returns that copy, or null when copying failed.
     */
    public static Path keepUnreadable(Path file, LocalDateTime time) {
        String prefix = file.getFileName() + ".unreadable-";
        try {
            try (Stream<Path> siblings = Files.list(file.toAbsolutePath().getParent())) {
                for (Path sibling : (Iterable<Path>) siblings::iterator) {
                    if (sibling.getFileName().toString().startsWith(prefix) && Files.mismatch(sibling, file) == -1) return sibling;
                }
            }
            Path copy = file.resolveSibling(prefix + timestamp(time));
            for (int attempt = 2; Files.exists(copy); attempt++) copy = file.resolveSibling(prefix + timestamp(time) + "-" + attempt);
            Files.copy(file, copy);
            return copy;
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }
}
