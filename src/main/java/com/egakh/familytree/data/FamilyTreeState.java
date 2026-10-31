package com.egakh.familytree.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Dynamic;
import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.util.AnimalCategory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.5 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?}
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class FamilyTreeState extends SavedData {

    private static final String LEGACY_STATE_KEY = "familytree";
    private static final Identifier STATE_ID = Identifier.fromNamespaceAndPath("familytree", "familytree");
    private static final int CURRENT_DATA_VERSION = 1;

    private final Map<UUID, AnimalRecord> records = new ConcurrentHashMap<>();
    private final List<Dynamic<?>> rejectedRecords = new ArrayList<>();
    private Dynamic<?> protectedData;
    private boolean initialized;
    private boolean readOnly;
    private Path unreadableFile;
    private int unnamedFarmRecords = -1;
    private int countedLearnedVersion;
    private final Set<UUID> farmLimitNotified = ConcurrentHashMap.newKeySet();
    private long cleanupRetryNanos;

    public FamilyTreeState() {}

    public AnimalRecord get(UUID id) {
        return records.get(id);
    }

    public boolean contains(UUID id) {
        return records.containsKey(id);
    }

    public Collection<AnimalRecord> all() {
        return records.values();
    }

    public void put(AnimalRecord record) {
        if (readOnly) return;
        AnimalRecord previous = records.put(record.id(), record);
        if (previous != null) unnamedFarmRecords = -1;
        else if (unnamedFarmRecords >= 0 && FarmRecords.unnamedFarm(record)) unnamedFarmRecords++;
        setDirty();
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
    boolean cleanupDue(long now) {
        return now - cleanupRetryNanos >= 0;
    }

    void delayCleanup(long until) {
        cleanupRetryNanos = until;
    }

    public void update(UUID id, java.util.function.Consumer<AnimalRecord> mutator) {
        if (readOnly) return;
        AnimalRecord r = records.get(id);
        if (r == null) return;
        boolean counted = unnamedFarmRecords >= 0 && FarmRecords.unnamedFarm(r);
        mutator.accept(r);
        if (unnamedFarmRecords >= 0 && counted != FarmRecords.unnamedFarm(r)) unnamedFarmRecords += counted ? -1 : 1;
        setDirty();
    }

    /** Renames from the screen edit a record directly, outside {@link #update}. */
    public void recordEdited() {
        unnamedFarmRecords = -1;
        setDirty();
    }

    public int removeMatching(Predicate<AnimalRecord> predicate) {
        if (readOnly) return 0;
        int removed = 0;
        for (AnimalRecord record : new ArrayList<>(records.values())) {
            if (predicate.test(record) && records.remove(record.id()) != null) {
                removed++;
            }
        }
        if (removed > 0) {
            unnamedFarmRecords = -1;
            setDirty();
        }
        return removed;
    }

    public int removeAll(Collection<UUID> ids) {
        if (readOnly) return 0;
        int removed = 0;
        for (UUID id : ids) if (records.remove(id) != null) removed++;
        if (removed > 0) {
            unnamedFarmRecords = -1;
            setDirty();
        }
        return removed;
    }

    public static final Codec<FamilyTreeState> CODEC = new Codec<>() {
        @Override public <T> DataResult<Pair<FamilyTreeState, T>> decode(DynamicOps<T> ops, T input) {
            FamilyTreeState state = new FamilyTreeState();
            state.initialized = true;
            Dynamic<T> root = new Dynamic<>(ops, input);
            var versionField = root.get("data_version").result();
            var versionNumber = root.get("data_version").asNumber().result();
            int version = versionNumber.map(Number::intValue).orElse(CURRENT_DATA_VERSION);
            boolean invalidVersion = versionField.isPresent() && (versionNumber.isEmpty()
                    || versionNumber.get().doubleValue() != version);
            if (ops.getMap(input).result().isEmpty() || invalidVersion || version > CURRENT_DATA_VERSION || version < 0) {
                state.protectedData = root;
                state.readOnly = true;
                FamilyTreeMod.LOGGER.error("Family Tree data version {} is unsupported. Tracking and edits are disabled; the original data will be preserved.", version);
                return DataResult.success(Pair.of(state, ops.empty()));
            }
            var list = root.get("records").result();
            if (list.isPresent()) {
                var elements = ops.getStream(list.get().getValue());
                if (elements.result().isEmpty()) {
                    state.protectedData = root;
                    state.readOnly = true;
                    FamilyTreeMod.LOGGER.error("Family Tree records are not a list. The original data will be preserved.");
                } else elements.result().get().forEach(element -> {
                    var parsed = AnimalRecord.CODEC.parse(ops, element);
                    var record = parsed.result();
                    if (record.isPresent() && !state.records.containsKey(record.get().id())) {
                        state.records.put(record.get().id(), record.get());
                    } else {
                        state.rejectedRecords.add(new Dynamic<>(ops, element));
                        parsed.error().ifPresent(error -> FamilyTreeMod.LOGGER.warn("Preserving an unreadable pet record: {}", error.message()));
                    }
                });
            }
            if (!state.rejectedRecords.isEmpty()) FamilyTreeMod.LOGGER.warn(
                    "Family Tree loaded {} pets and preserved {} unreadable or duplicate records in the save. Restore a backup or repair those records to view them.",
                    state.records.size(), state.rejectedRecords.size());
            return DataResult.success(Pair.of(state, ops.empty()));
        }

        @Override public <T> DataResult<T> encode(FamilyTreeState state, DynamicOps<T> ops, T prefix) {
            if (state.protectedData != null) return DataResult.success(state.protectedData.convert(ops).getValue());
            List<T> encoded = new ArrayList<>();
            for (AnimalRecord record : state.records.values()) {
                var result = AnimalRecord.CODEC.encodeStart(ops, record);
                if (result.error().isPresent()) return DataResult.error(() -> result.error().get().message());
                encoded.add(result.getOrThrow());
            }
            for (Dynamic<?> rejected : state.rejectedRecords) encoded.add(rejected.convert(ops).getValue());
            return DataResult.success(ops.createMap(java.util.stream.Stream.of(
                    Pair.of(ops.createString("data_version"), ops.createInt(CURRENT_DATA_VERSION)),
                    Pair.of(ops.createString("records"), ops.createList(encoded.stream())))));
        }
    };

    public boolean readOnly() { return readOnly; }
    public int preservedRecordCount() { return rejectedRecords.size(); }

    /** The history file Family Tree could not read. While it is set, nothing is recorded or written over it. */
    public Path unreadableFile() { return unreadableFile; }

    @Override
    public boolean isDirty() {
        return unreadableFile == null && super.isDirty();
    }

    public Path backup(MinecraftServer server) throws IOException {
        return backup(server, "before-prune", true);
    }

    /** Automatic cleanup writes compact JSON, since it can run many times in a long-lived world. */
    public Path backup(MinecraftServer server, String prefix, boolean pretty) throws IOException {
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("familytree-backups");
        var json = CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, this).getOrThrow();
        var gson = pretty ? new com.google.gson.GsonBuilder().setPrettyPrinting().create() : new com.google.gson.Gson();
        return BackupFiles.write(directory, prefix, ".json", gson.toJson(json));
    }

    //? if >=26.1 {
    private static final SavedDataType<FamilyTreeState> TYPE = new SavedDataType<>(
            STATE_ID,
            FamilyTreeState::new,
            CODEC,
            null
    );
    //?} else if >=1.21.5 {
    /*// Same file as the 1.21.1 build: data/familytree.dat in the overworld folder.
    private static final SavedDataType<FamilyTreeState> TYPE = new SavedDataType<>(
            LEGACY_STATE_KEY,
            FamilyTreeState::new,
            CODEC,
            null
    );
    *///?} else if >=1.21 {
    /*private static final SavedData.Factory<FamilyTreeState> TYPE = new SavedData.Factory<>(
            FamilyTreeState::new, (tag, registries) -> CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(), null);

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }
    *///?} else {
    /*private static FamilyTreeState load(CompoundTag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }
    *///?}

    public static FamilyTreeState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        SavedDataStorage storage = overworld.getDataStorage();
        //? if >=1.21 {
        FamilyTreeState state = storage.computeIfAbsent(TYPE);
        //?} else {
        /*FamilyTreeState state = storage.computeIfAbsent(FamilyTreeState::load, FamilyTreeState::new, LEGACY_STATE_KEY);
        *///?}
        if (!state.initialized) {
            state.initialized = true;
            FamilyTreeState legacyState = loadWithoutMinecraft(savedFile(server),
                    server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(LEGACY_STATE_KEY + ".dat"), state);
            if (legacyState != null) {
                storage.set(TYPE, legacyState);
                legacyState.setDirty();
                return legacyState;
            }
        }
        return state;
    }

    public static FamilyTreeState get(Level world) {
        if (!(world instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("FamilyTreeState is server-only");
        }
        return get(serverLevel.getServer());
    }

    /** The file Minecraft reads and writes for this state. */
    private static Path savedFile(MinecraftServer server) {
        Path data = DimensionType.getStorageFolder(Level.OVERWORLD, server.getWorldPath(LevelResource.ROOT)).resolve("data");
        //? if >=26.1 {
        return data.resolve(STATE_ID.getNamespace()).resolve(STATE_ID.getPath() + ".dat");
        //?} else {
        /*return data.resolve(LEGACY_STATE_KEY + ".dat");
        *///?}
    }

    /**
     * Minecraft hands back a fresh state when the file is missing and also when reading it failed, and saves that
     * empty state over the file. When the file exists, read it here; 26.x worlds without it import the older file.
     * If neither read works, {@code fresh} is locked so it never writes, and the next start tries again.
     */
    static FamilyTreeState loadWithoutMinecraft(Path saved, Path legacy, FamilyTreeState fresh) {
        Path source = Files.exists(saved) ? saved : legacy;
        if (!Files.exists(source)) return null;
        try {
            FamilyTreeState loaded = readFile(source);
            Path backup = source.resolveSibling(source.getFileName() + ".migration-backup");
            if (!Files.exists(backup)) Files.copy(source, backup);
            if (source.equals(saved)) FamilyTreeMod.LOGGER.warn("Minecraft could not read Family Tree data at {}, so Family Tree read it directly. Original file preserved at {}", source, backup);
            else FamilyTreeMod.LOGGER.info("Imported legacy Family Tree data. Original file preserved at {}", backup);
            return loaded;
        } catch (IOException | RuntimeException failure) {
            fresh.lock(source, failure);
            return null;
        }
    }

    static FamilyTreeState readFile(Path file) throws IOException {
        CompoundTag root;
        try (InputStream in = Files.newInputStream(file)) {
            root = NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        }
        CompoundTag data = root.get("records") == null && root.get("data_version") == null ? root.getCompoundOrEmpty("data") : root;
        if (data.get("records") == null && data.get("data_version") == null) throw new IOException("No Family Tree records in " + file);
        return CODEC.parse(NbtOps.INSTANCE, data).getOrThrow();
    }

    void lock(Path file, Exception cause) {
        readOnly = true;
        unreadableFile = file;
        Path copy = BackupFiles.keepUnreadable(file, LocalDateTime.now());
        FamilyTreeMod.LOGGER.error("Family Tree could not read its history at {}. It will not record, edit, or save anything "
                + "until the file is repaired or restored, so the file stays as it is. {}", file,
                copy == null ? "Copying it failed." : "A copy is at " + copy + ".", cause);
    }

    public Map<UUID, java.util.List<UUID>> buildChildIndex() {
        Map<UUID, java.util.List<UUID>> index = new HashMap<>();
        for (AnimalRecord r : records.values()) {
            if (r.parentA() != null) {
                index.computeIfAbsent(r.parentA(), k -> new java.util.ArrayList<>()).add(r.id());
            }
            if (r.parentB() != null) {
                index.computeIfAbsent(r.parentB(), k -> new java.util.ArrayList<>()).add(r.id());
            }
        }
        return index;
    }
}
