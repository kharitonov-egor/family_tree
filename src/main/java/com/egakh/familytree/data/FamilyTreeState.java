package com.egakh.familytree.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FamilyTreeState extends SavedData {

    private static final String LEGACY_STATE_KEY = "familytree";
    private static final Identifier STATE_ID = Identifier.fromNamespaceAndPath("familytree", "familytree");

    private final Map<UUID, AnimalRecord> records = new ConcurrentHashMap<>();

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
        records.put(record.id(), record);
        setDirty();
    }

    public void update(UUID id, java.util.function.Consumer<AnimalRecord> mutator) {
        AnimalRecord r = records.get(id);
        if (r == null) return;
        mutator.accept(r);
        setDirty();
    }

    private static FamilyTreeState fromRecords(Collection<AnimalRecord> records) {
        FamilyTreeState state = new FamilyTreeState();
        for (AnimalRecord record : records) {
            state.records.put(record.id(), record);
        }
        return state;
    }

    private static final Codec<FamilyTreeState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AnimalRecord.CODEC.listOf().fieldOf("records")
                    .forGetter(state -> new ArrayList<>(state.records.values()))
    ).apply(instance, FamilyTreeState::fromRecords));

    private static final SavedDataType<FamilyTreeState> TYPE = new SavedDataType<>(
            STATE_ID,
            FamilyTreeState::new,
            CODEC,
            null
    );

    public static FamilyTreeState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        SavedDataStorage storage = overworld.getDataStorage();
        FamilyTreeState state = storage.computeIfAbsent(TYPE);
        if (state.all().isEmpty()) {
            FamilyTreeState legacyState = tryLoadLegacy(server);
            if (legacyState != null && !legacyState.all().isEmpty()) {
                storage.set(TYPE, legacyState);
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
            Tag dataNode = root.get("records");
            if (dataNode == null) return null;
            return AnimalRecord.CODEC.listOf()
                    .parse(NbtOps.INSTANCE, dataNode)
                    .resultOrPartial(err -> {})
                    .map(FamilyTreeState::fromRecords)
                    .orElse(null);
        } catch (IOException ignored) {
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
