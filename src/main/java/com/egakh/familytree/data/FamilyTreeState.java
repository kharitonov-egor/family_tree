package com.egakh.familytree.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Dynamic;
import com.egakh.familytree.FamilyTreeMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=26.1 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?}
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        records.put(record.id(), record);
        setDirty();
    }

    public void update(UUID id, java.util.function.Consumer<AnimalRecord> mutator) {
        if (readOnly) return;
        AnimalRecord r = records.get(id);
        if (r == null) return;
        mutator.accept(r);
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

    public Path backup(MinecraftServer server) throws IOException {
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("familytree-backups");
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, "before-prune-", ".json");
        var json = CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, this).getOrThrow();
        Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
        return file;
    }

    //? if >=26.1 {
    private static final SavedDataType<FamilyTreeState> TYPE = new SavedDataType<>(
            STATE_ID,
            FamilyTreeState::new,
            CODEC,
            null
    );
    //?} else {
    /*private static final SavedData.Factory<FamilyTreeState> TYPE = new SavedData.Factory<>(
            FamilyTreeState::new, (tag, registries) -> CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(), null);

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }
    *///?}

    public static FamilyTreeState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        SavedDataStorage storage = overworld.getDataStorage();
        FamilyTreeState state = storage.computeIfAbsent(TYPE);
        if (!state.initialized) {
            state.initialized = true;
            FamilyTreeState legacyState = tryLoadLegacy(server);
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

    private static FamilyTreeState tryLoadLegacy(MinecraftServer server) {
        Path legacyPath = server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(LEGACY_STATE_KEY + ".dat");
        if (!Files.exists(legacyPath)) {
            return null;
        }

        try (InputStream in = Files.newInputStream(legacyPath)) {
            CompoundTag root = NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            CompoundTag data = root.get("records") == null ? root.getCompoundOrEmpty("data") : root;
            if (data.get("records") == null) return null;
            Path backup = legacyPath.resolveSibling("familytree.dat.migration-backup");
            if (!Files.exists(backup)) Files.copy(legacyPath, backup);
            FamilyTreeState imported = CODEC.parse(NbtOps.INSTANCE, data).getOrThrow();
            FamilyTreeMod.LOGGER.info("Imported legacy Family Tree data. Original file preserved at {}", backup);
            return imported;
        } catch (IOException | RuntimeException failure) {
            FamilyTreeMod.LOGGER.error("Could not import legacy Family Tree data at {}", legacyPath, failure);
            return null;
        }
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
