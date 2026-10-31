package com.egakh.familytree.plugin.storage;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FarmRecords;
import com.egakh.familytree.util.AnimalCategory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * The plugin's copy of the mod's {@code FamilyTreeState}. It keeps readable records when others fail, preserves
 * unreadable and duplicate entries for the next save, and stops writing when the data version is unsupported.
 */
public final class FamilyTreeStore {
    public static final int CURRENT_DATA_VERSION = 1;

    private final Map<UUID, AnimalRecord> records = new ConcurrentHashMap<>();
    private final Map<UUID, Nbt.Compound> originals = new HashMap<>();
    private final List<Object> rejected = new ArrayList<>();
    private boolean readOnly;
    private boolean dirty;
    private int unnamedFarmRecords = -1;
    private int countedLearnedVersion;
    private final Set<UUID> farmLimitNotified = ConcurrentHashMap.newKeySet();
    private long cleanupRetryNanos;

    public static FamilyTreeStore empty() {
        return new FamilyTreeStore();
    }

    /** A store that refuses edits and never writes, used when the saved file can't be read safely. */
    public static FamilyTreeStore locked() {
        FamilyTreeStore store = new FamilyTreeStore();
        store.readOnly = true;
        return store;
    }

    /** Decodes the {@code data} compound of a saved file. */
    public static FamilyTreeStore decode(Object data, Logger logger) {
        FamilyTreeStore store = new FamilyTreeStore();
        if (!(data instanceof Nbt.Compound root)) {
            logger.severe("Family Tree data is not a compound. Tracking and edits are disabled; the original file will be preserved.");
            store.readOnly = true;
            return store;
        }
        Object version = root.get("data_version");
        int parsed = version instanceof Number number ? number.intValue() : CURRENT_DATA_VERSION;
        boolean invalid = version != null && (!(version instanceof Number number) || number.doubleValue() != parsed);
        if (invalid || parsed > CURRENT_DATA_VERSION || parsed < 0) {
            logger.severe("Family Tree data version " + version + " is unsupported. Tracking and edits are disabled; the original data will be preserved.");
            store.readOnly = true;
            return store;
        }
        Object list = root.get("records");
        if (list != null && !(list instanceof Nbt.ListTag)) {
            logger.severe("Family Tree records are not a list. The original data will be preserved.");
            store.readOnly = true;
            return store;
        }
        if (list instanceof Nbt.ListTag elements) {
            for (Object element : elements.values()) {
                try {
                    AnimalRecord record = RecordNbt.decode(element);
                    if (store.records.containsKey(record.id())) store.rejected.add(element);
                    else {
                        store.records.put(record.id(), record);
                        store.originals.put(record.id(), (Nbt.Compound) element);
                    }
                } catch (RecordNbt.InvalidRecord invalidRecord) {
                    store.rejected.add(element);
                    logger.warning("Preserving an unreadable pet record: " + invalidRecord.getMessage());
                }
            }
        }
        if (!store.rejected.isEmpty()) logger.warning("Family Tree loaded " + store.records.size() + " pets and preserved "
                + store.rejected.size() + " unreadable or duplicate records in the save. Restore a backup or repair those records to view them.");
        return store;
    }

    /** The {@code data} compound, in the mod's layout. Unreadable records follow the readable ones unchanged. */
    public Nbt.Compound encodeData() {
        Nbt.Compound data = new Nbt.Compound();
        data.put("data_version", CURRENT_DATA_VERSION);
        Nbt.ListTag list = new Nbt.ListTag();
        for (AnimalRecord record : records.values()) list.add(RecordNbt.encode(record, originals.get(record.id())));
        for (Object entry : rejected) list.add(entry);
        data.put("records", list);
        return data;
    }

    public AnimalRecord get(UUID id) {
        return id == null ? null : records.get(id);
    }

    public boolean contains(UUID id) {
        return id != null && records.containsKey(id);
    }

    public Collection<AnimalRecord> all() {
        return records.values();
    }

    public void put(AnimalRecord record) {
        if (readOnly) return;
        AnimalRecord previous = records.put(record.id(), record);
        if (previous != null) unnamedFarmRecords = -1;
        else if (unnamedFarmRecords >= 0 && FarmRecords.unnamedFarm(record)) unnamedFarmRecords++;
        dirty = true;
    }

    public void update(UUID id, Consumer<AnimalRecord> mutator) {
        if (readOnly) return;
        AnimalRecord record = records.get(id);
        if (record == null) return;
        boolean counted = unnamedFarmRecords >= 0 && FarmRecords.unnamedFarm(record);
        mutator.accept(record);
        if (unnamedFarmRecords >= 0 && counted != FarmRecords.unnamedFarm(record)) unnamedFarmRecords += counted ? -1 : 1;
        dirty = true;
    }

    /** Renames edit a record directly, outside {@link #update}. */
    public void recordEdited() {
        unnamedFarmRecords = -1;
        dirty = true;
    }

    public int removeMatching(Predicate<AnimalRecord> predicate) {
        if (readOnly) return 0;
        int removed = 0;
        for (AnimalRecord record : new ArrayList<>(records.values())) {
            if (predicate.test(record) && records.remove(record.id()) != null) {
                originals.remove(record.id());
                removed++;
            }
        }
        if (removed > 0) {
            unnamedFarmRecords = -1;
            dirty = true;
        }
        return removed;
    }

    public int removeAll(Collection<UUID> ids) {
        if (readOnly) return 0;
        int removed = 0;
        for (UUID id : ids) {
            if (records.remove(id) != null) {
                originals.remove(id);
                removed++;
            }
        }
        if (removed > 0) {
            unnamedFarmRecords = -1;
            dirty = true;
        }
        return removed;
    }

    public int unnamedFarmRecords() {
        if (unnamedFarmRecords < 0 || countedLearnedVersion != AnimalCategory.learnedVersion()) {
            countedLearnedVersion = AnimalCategory.learnedVersion();
            unnamedFarmRecords = FarmRecords.count(records.values());
        }
        return unnamedFarmRecords;
    }

    public int recountUnnamedFarmRecords() {
        unnamedFarmRecords = -1;
        return unnamedFarmRecords();
    }

    public boolean firstFarmLimitNotice(UUID player) {
        return farmLimitNotified.add(player);
    }

    /** A cleanup that found nothing to remove waits before scanning the whole history again. */
    public boolean cleanupDue(long now) {
        return now - cleanupRetryNanos >= 0;
    }

    public void delayCleanup(long until) {
        cleanupRetryNanos = until;
    }

    public boolean readOnly() { return readOnly; }
    public int preservedRecordCount() { return rejected.size(); }
    public boolean dirty() { return dirty && !readOnly; }
    public void markDirty() { dirty = true; }
    public void markSaved() { dirty = false; }

    public Map<UUID, List<UUID>> buildChildIndex() {
        Map<UUID, List<UUID>> index = new HashMap<>();
        for (AnimalRecord record : records.values()) {
            if (record.parentA() != null) index.computeIfAbsent(record.parentA(), key -> new ArrayList<>()).add(record.id());
            if (record.parentB() != null) index.computeIfAbsent(record.parentB(), key -> new ArrayList<>()).add(record.id());
        }
        return index;
    }

    /** Writes a JSON copy of the history in the layout of the mod's backups. Pruning cancels when this fails. */
    public Path backup(Path directory, String prefix, boolean pretty) throws IOException {
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, prefix, ".json");
        StringWriter text = new StringWriter();
        NbtJson.write(encodeData(), text, pretty);
        Files.writeString(file, text.toString());
        return file;
    }
}
