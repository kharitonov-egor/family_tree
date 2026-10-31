package com.egakh.familytree.plugin.storage;

import com.egakh.familytree.data.BackupFiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Finds and writes {@code familytree.dat} where the mod keeps it, so a world keeps its history when it moves
 * between the mod and the plugin. Minecraft 1.20.1 to 1.21.11 use the overworld's {@code data/familytree.dat}.
 * 26.x worlds use {@code dimensions/minecraft/overworld/data/familytree/familytree.dat} and import the older file once.
 */
public final class StoreFiles {
    private final Path levelRoot;
    private final Path file;
    private final Path legacy;

    private StoreFiles(Path levelRoot, Path file, Path legacy) {
        this.levelRoot = levelRoot;
        this.file = file;
        this.legacy = legacy;
    }

    /** {@code dimensionFolders} is true for worlds in the 26.x layout. */
    public static StoreFiles forLevel(Path levelRoot, boolean dimensionFolders) {
        Path older = levelRoot.resolve("data").resolve("familytree.dat");
        if (!dimensionFolders) return new StoreFiles(levelRoot, older, null);
        Path modern = levelRoot.resolve("dimensions").resolve("minecraft").resolve("overworld")
                .resolve("data").resolve("familytree").resolve("familytree.dat");
        return new StoreFiles(levelRoot, modern, older);
    }

    public Path file() { return file; }

    /** A path inside the world as players see it in chat, with forward slashes. */
    public String shown(Path path) {
        Path world = levelRoot.toAbsolutePath().normalize(), absolute = path.toAbsolutePath().normalize();
        return (absolute.startsWith(world) ? world.relativize(absolute) : absolute).toString().replace('\\', '/');
    }

    public Path backups() { return levelRoot.resolve("familytree-backups"); }

    /**
     * A file that can't be read locks the store, so it is never overwritten. A 26.x world whose older file can't be
     * imported is locked too and writes no modern file, so the next start tries the import again.
     */
    public FamilyTreeStore load(Logger logger) {
        if (Files.exists(file)) {
            try {
                return FamilyTreeStore.decode(dataOf(read(file), file), logger);
            } catch (IOException | RuntimeException failure) {
                return unreadable(file, logger, failure);
            }
        }
        if (legacy != null && Files.exists(legacy)) {
            try {
                Object data = dataOf(read(legacy), legacy);
                Path backup = legacy.resolveSibling("familytree.dat.migration-backup");
                if (!Files.exists(backup)) Files.copy(legacy, backup);
                FamilyTreeStore imported = FamilyTreeStore.decode(data, logger);
                if (imported.readOnly()) {
                    logger.severe("Family Tree did not import " + legacy + ". The 26.x history file will not be created until the older file can be imported.");
                    return imported;
                }
                imported.markDirty();
                logger.info("Imported legacy Family Tree data. Original file preserved at " + backup);
                return imported;
            } catch (IOException | RuntimeException failure) {
                return unreadable(legacy, logger, failure);
            }
        }
        return FamilyTreeStore.empty();
    }

    private static FamilyTreeStore unreadable(Path file, Logger logger, Exception failure) {
        Path copy = BackupFiles.keepUnreadable(file, LocalDateTime.now());
        logger.log(Level.SEVERE, "Could not read Family Tree history at " + file
                + ". Tracking and edits are disabled until it is repaired or restored, and the file will not be overwritten. "
                + (copy == null ? "Copying it failed." : "A copy is at " + copy + "."), failure);
        return FamilyTreeStore.unreadable(file);
    }

    /** Encodes on the calling thread. The returned bytes can be written from another thread with {@link #write}. */
    public static byte[] encode(FamilyTreeStore store, int dataVersion) throws IOException {
        Nbt.Compound root = new Nbt.Compound();
        root.put("data", store.encodeData());
        root.put("DataVersion", dataVersion);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Nbt.writeCompressed(root, bytes);
        return bytes.toByteArray();
    }

    public synchronized void write(byte[] compressed) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temporary, compressed);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static Nbt.Compound read(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return Nbt.readCompressed(in);
        }
    }

    /** Minecraft keeps saved data under {@code data}. A file holding the records directly is read as it is. */
    private static Object dataOf(Nbt.Compound root, Path path) throws IOException {
        Object data = root.has("records") || root.has("data_version") ? root : root.get("data");
        if (data == null || data instanceof Nbt.Compound compound && !compound.has("records") && !compound.has("data_version"))
            throw new IOException("No Family Tree records in " + path);
        return data;
    }
}
