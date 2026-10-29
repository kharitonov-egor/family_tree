package com.egakh.familytree.data;

import com.egakh.familytree.FamilyTreeMod;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Opt-in removal of deceased unnamed animals other than pets. It always writes the JSON backup first. */
public final class FarmCleanup {
    private static final long RETRY_NANOS = 30_000_000_000L;

    private FarmCleanup() {}

    public static int autoPrune(MinecraftServer server, FamilyTreeState state, int limit) {
        long now = System.nanoTime();
        if (state.readOnly() || !state.cleanupDue(now)) return 0;
        int excess = state.recountUnnamedFarmRecords() - FarmRecords.cleanupTarget(limit);
        if (excess <= 0) return 0;
        List<UUID> ids = FarmRecords.removable(state.all()).stream().limit(excess).map(AnimalRecord::id).toList();
        if (ids.isEmpty()) {
            state.delayCleanup(now + RETRY_NANOS);
            return 0;
        }
        Path backup;
        try {
            backup = state.backup(server, "before-farm-cleanup-", false);
        } catch (IOException | RuntimeException failure) {
            FamilyTreeMod.LOGGER.error("Farm record cleanup canceled because the backup failed", failure);
            return 0;
        }
        int removed = state.removeAll(ids);
        FamilyTreeMod.LOGGER.info("Family Tree removed {} deceased unnamed animals other than pets. Backup: {}", removed, backup);
        return removed;
    }
}
