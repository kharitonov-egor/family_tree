package com.egakh.familytree.neoforge;

import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.command.FamilyTreeCommand;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.interaction.LinkingTool;
import com.egakh.familytree.network.FamilyTreePackets;
import com.egakh.familytree.network.payloads.*;
import com.egakh.familytree.platform.FamilyTreePaths;
import com.egakh.familytree.platform.ServerTransport;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(FamilyTreeMod.MOD_ID)
public final class NeoForgeEntrypoint {
    public NeoForgeEntrypoint(IEventBus modBus) {
        FamilyTreePaths.configure(FMLPaths.CONFIGDIR.get());
        ServerTransport.configure((player, type) -> player.connection.hasChannel(type), PacketDistributor::sendToPlayer);
        modBus.addListener(this::registerPackets);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> FamilyTreeCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> FamilyTreeServerSettings.load());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (EntityJoinLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) PetLifecycleListeners.onLoad(event.getEntity(), level);
        });
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) PetLifecycleListeners.onUnload(event.getEntity(), level);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDeathEvent event) ->
                PetLifecycleListeners.onDeath(event.getEntity(), event.getSource()));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> {
            InteractionResult result = LinkingTool.onUseEntity(event.getEntity(), event.getLevel(),
                    event.getHand(), event.getTarget(), null);
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
            FamilyTreePackets.disconnect(event.getEntity().getUUID()); LinkingTool.disconnect(event.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> { FamilyTreePackets.stop(); LinkingTool.stop(); });
        FamilyTreeMod.LOGGER.info("Family Tree initialized on NeoForge");
    }

    private void registerPackets(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2").optional();
        registrar.playToClient(SnapshotPagePayload.TYPE, SnapshotPagePayload.STREAM_CODEC);
        registrar.playToClient(LinkParentsResult.TYPE, LinkParentsResult.STREAM_CODEC);
        registrar.playToServer(LinkParentsRequest.TYPE, LinkParentsRequest.STREAM_CODEC, (packet, context) ->
                FamilyTreePackets.link(packet, (ServerPlayer) context.player()));
        registrar.playToClient(RenamePetResult.TYPE, RenamePetResult.STREAM_CODEC);
        registrar.playToServer(OpenFamilyTreeRequest.TYPE, OpenFamilyTreeRequest.STREAM_CODEC, (packet, context) ->
                FamilyTreePackets.open(packet, (ServerPlayer) context.player()));
        registrar.playToServer(DiscoverPetsRequest.TYPE, DiscoverPetsRequest.STREAM_CODEC, (packet, context) ->
                FamilyTreePackets.discover(packet, (ServerPlayer) context.player()));
        registrar.playToServer(LocatePetRequest.TYPE, LocatePetRequest.STREAM_CODEC, (packet, context) ->
                FamilyTreePackets.locate(packet, (ServerPlayer) context.player()));
        registrar.playToServer(RenamePetRequest.TYPE, RenamePetRequest.STREAM_CODEC, (packet, context) ->
                FamilyTreePackets.rename(packet, (ServerPlayer) context.player()));
    }
}
