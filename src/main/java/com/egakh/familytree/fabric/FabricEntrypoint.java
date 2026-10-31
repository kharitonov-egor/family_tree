package com.egakh.familytree.fabric;

import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.command.FamilyTreeCommand;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.interaction.LinkingTool;
import com.egakh.familytree.network.FamilyTreePackets;
import com.egakh.familytree.permissions.ServerPetAccess;
import com.egakh.familytree.network.payloads.*;
import com.egakh.familytree.platform.FamilyTreePaths;
import com.egakh.familytree.platform.ServerTransport;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
//? if >=1.21 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//?} else {
/*import com.egakh.familytree.network.backport.PayloadCodecs;
*///?}
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.BiConsumer;

public final class FabricEntrypoint implements ModInitializer {
    @Override public void onInitialize() {
        FamilyTreePaths.configure(FabricLoader.getInstance().getConfigDir());
        if (FabricLoader.getInstance().isModLoaded(FabricPermissions.MOD_ID)) ServerPetAccess.configure(new FabricPermissions());
        //? if >=1.21 {
        ServerTransport.configure(ServerPlayNetworking::canSend, ServerPlayNetworking::send);
        PayloadTypeRegistry.clientboundPlay().register(SnapshotPagePayload.TYPE, SnapshotPagePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RenamePetResult.TYPE, RenamePetResult.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LinkParentsResult.TYPE, LinkParentsResult.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WaypointResult.TYPE, WaypointResult.STREAM_CODEC);
        //?} else {
        /*ServerTransport.configure((player, type) -> ServerPlayNetworking.canSend(player, type.id()),
                (player, packet) -> ServerPlayNetworking.send(player, packet.type().id(), PayloadCodecs.encode(packet)));
        PayloadCodecs.register(SnapshotPagePayload.TYPE, SnapshotPagePayload.STREAM_CODEC);
        PayloadCodecs.register(RenamePetResult.TYPE, RenamePetResult.STREAM_CODEC);
        PayloadCodecs.register(LinkParentsResult.TYPE, LinkParentsResult.STREAM_CODEC);
        PayloadCodecs.register(WaypointResult.TYPE, WaypointResult.STREAM_CODEC);
        *///?}
        register(LinkParentsRequest.TYPE, LinkParentsRequest.STREAM_CODEC, FamilyTreePackets::link);
        register(OpenFamilyTreeRequest.TYPE, OpenFamilyTreeRequest.STREAM_CODEC, FamilyTreePackets::open);
        register(DiscoverPetsRequest.TYPE, DiscoverPetsRequest.STREAM_CODEC, FamilyTreePackets::discover);
        register(LocatePetRequest.TYPE, LocatePetRequest.STREAM_CODEC, FamilyTreePackets::locate);
        register(RenamePetRequest.TYPE, RenamePetRequest.STREAM_CODEC, FamilyTreePackets::rename);
        register(WaypointRequest.TYPE, WaypointRequest.STREAM_CODEC, FamilyTreePackets::waypoint);
        ServerEntityEvents.ENTITY_LOAD.register(PetLifecycleListeners::onLoad);
        ServerEntityEvents.ENTITY_UNLOAD.register(PetLifecycleListeners::onUnload);
        ServerLivingEntityEvents.AFTER_DEATH.register(PetLifecycleListeners::onDeath);
        UseEntityCallback.EVENT.register(LinkingTool::onUseEntity);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> FamilyTreeServerSettings.load());
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> FamilyTreeCommand.register(dispatcher));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            FamilyTreePackets.disconnect(handler.player.getUUID());
            LinkingTool.disconnect(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { FamilyTreePackets.stop(); LinkingTool.stop(); });
        FamilyTreeMod.LOGGER.info("Family Tree initialized on Fabric");
    }

    private static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        //? if >=1.21 {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (packet, context) ->
                context.server().execute(() -> handler.accept(packet, context.player())));
        //?} else {
        /*PayloadCodecs.register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, listener, buffer, sender) -> {
            T packet = codec.decode(new RegistryFriendlyByteBuf(buffer));
            server.execute(() -> handler.accept(packet, player));
        });
        *///?}
    }
}
