package com.egakh.familytree.network;

import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.network.payloads.OpenFamilyTreeRequest;
import com.egakh.familytree.util.TimeUtil;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class FamilyTreePackets {

    private FamilyTreePackets() {}

    public static void registerCommon() {
        PayloadTypeRegistry.serverboundPlay().register(OpenFamilyTreeRequest.TYPE, OpenFamilyTreeRequest.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FamilyTreeSnapshotPayload.TYPE, FamilyTreeSnapshotPayload.STREAM_CODEC);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(OpenFamilyTreeRequest.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> sendSnapshot(player));
        });
    }

    public static void sendSnapshot(ServerPlayer player) {
        ServerLevel world = player.level();
        FamilyTreeState state = FamilyTreeState.get(world.getServer());
        FamilyTreeSnapshotPayload payload = new FamilyTreeSnapshotPayload(
                new ArrayList<>(state.all()),
                TimeUtil.currentWorldDay(world),
                TimeUtil.currentEpochMillis()
        );
        ServerPlayNetworking.send(player, payload);
    }
}
