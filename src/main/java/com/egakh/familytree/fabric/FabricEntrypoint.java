package com.egakh.familytree.fabric;

import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.command.FamilyTreeCommand;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.interaction.LinkingTool;
import com.egakh.familytree.network.FamilyTreePackets;
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
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
        ServerTransport.configure(ServerPlayNetworking::canSend, ServerPlayNetworking::send);
        PayloadTypeRegistry.clientboundPlay().register(SnapshotPagePayload.TYPE, SnapshotPagePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RenamePetResult.TYPE, RenamePetResult.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LinkParentsResult.TYPE, LinkParentsResult.STREAM_CODEC);
        register(LinkParentsRequest.TYPE, LinkParentsRequest.STREAM_CODEC, FamilyTreePackets::link);
        register(OpenFamilyTreeRequest.TYPE, OpenFamilyTreeRequest.STREAM_CODEC, FamilyTreePackets::open);
        register(DiscoverPetsRequest.TYPE, DiscoverPetsRequest.STREAM_CODEC, FamilyTreePackets::discover);
        register(LocatePetRequest.TYPE, LocatePetRequest.STREAM_CODEC, FamilyTreePackets::locate);
        register(RenamePetRequest.TYPE, RenamePetRequest.STREAM_CODEC, FamilyTreePackets::rename);
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
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (packet, context) ->
                context.server().execute(() -> handler.accept(packet, context.player())));
    }
}
