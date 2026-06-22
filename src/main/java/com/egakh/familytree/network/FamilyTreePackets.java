package com.egakh.familytree.network;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.network.payloads.OpenFamilyTreeRequest;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import com.egakh.familytree.util.TimeUtil;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public final class FamilyTreePackets {

    private static final int MAX_SNAPSHOT_RECORDS = 8192;

    private FamilyTreePackets() {}

    public static void registerCommon() {
        PayloadTypeRegistry.serverboundPlay().register(OpenFamilyTreeRequest.TYPE, OpenFamilyTreeRequest.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FamilyTreeSnapshotPayload.TYPE, FamilyTreeSnapshotPayload.STREAM_CODEC);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(OpenFamilyTreeRequest.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            boolean requestAll = payload.requestAll();
            context.server().execute(() -> sendSnapshot(player, requestAll));
        });
    }

    public static void sendSnapshot(ServerPlayer player, boolean requestAll) {
        ServerLevel world = player.level();
        FamilyTreeState state = FamilyTreeState.get(world.getServer());

        boolean mayViewAll = FamilyTreeServerSettings.mayViewAll(player);
        boolean viewingAll = mayViewAll && requestAll;

        UUID viewerId = player.getUUID();
        List<AnimalRecord> visible = state.all().stream()
                .filter(record -> viewingAll || viewerId.equals(record.ownerId()))
                .limit(MAX_SNAPSHOT_RECORDS)
                .toList();

        FamilyTreeSnapshotPayload payload = new FamilyTreeSnapshotPayload(
                visible,
                TimeUtil.currentWorldDay(world),
                TimeUtil.currentEpochMillis(),
                mayViewAll,
                viewingAll
        );
        ServerPlayNetworking.send(player, payload);
    }
}
