package com.egakh.familytree.forge;

import com.egakh.familytree.network.FamilyTreePackets;
import com.egakh.familytree.network.backport.CustomPacketPayload;
import com.egakh.familytree.network.backport.PayloadCodecs;
import com.egakh.familytree.network.backport.RegistryFriendlyByteBuf;
import com.egakh.familytree.network.backport.StreamCodec;
import com.egakh.familytree.network.payloads.*;
import com.egakh.familytree.platform.ServerTransport;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** One Forge channel per payload, so 1.20.1 uses the same channel names as the other loaders. */
final class ForgeNetwork {
    private static final String PROTOCOL = "4";
    private static final Map<ResourceLocation, EventNetworkChannel> CHANNELS = new HashMap<>();

    private ForgeNetwork() {}

    static void register() {
        ServerTransport.configure((player, type) -> present(type, player.connection.connection), (player, packet) ->
                player.connection.send(new ClientboundCustomPayloadPacket(packet.type().id(), PayloadCodecs.encode(packet))));
        channel(SnapshotPagePayload.TYPE, SnapshotPagePayload.STREAM_CODEC);
        channel(LinkParentsResult.TYPE, LinkParentsResult.STREAM_CODEC);
        channel(RenamePetResult.TYPE, RenamePetResult.STREAM_CODEC);
        channel(WaypointResult.TYPE, WaypointResult.STREAM_CODEC);
        serverbound(LinkParentsRequest.TYPE, LinkParentsRequest.STREAM_CODEC, FamilyTreePackets::link);
        serverbound(OpenFamilyTreeRequest.TYPE, OpenFamilyTreeRequest.STREAM_CODEC, FamilyTreePackets::open);
        serverbound(DiscoverPetsRequest.TYPE, DiscoverPetsRequest.STREAM_CODEC, FamilyTreePackets::discover);
        serverbound(LocatePetRequest.TYPE, LocatePetRequest.STREAM_CODEC, FamilyTreePackets::locate);
        serverbound(RenamePetRequest.TYPE, RenamePetRequest.STREAM_CODEC, FamilyTreePackets::rename);
        serverbound(WaypointRequest.TYPE, WaypointRequest.STREAM_CODEC, FamilyTreePackets::waypoint);
    }

    static boolean present(CustomPacketPayload.Type<?> type, Connection connection) {
        EventNetworkChannel channel = CHANNELS.get(type.id());
        return channel != null && channel.isRemotePresent(connection);
    }

    static <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
        CHANNELS.get(type.id()).addListener((NetworkEvent.ServerCustomPayloadEvent event) -> {
            NetworkEvent.Context context = event.getSource().get();
            T packet = codec.decode(new RegistryFriendlyByteBuf(event.getPayload()));
            context.enqueueWork(() -> handler.accept(packet));
            context.setPacketHandled(true);
        });
    }

    private static <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        channel(type, codec).addListener((NetworkEvent.ClientCustomPayloadEvent event) -> {
            NetworkEvent.Context context = event.getSource().get();
            ServerPlayer player = context.getSender();
            if (player == null) return;
            T packet = codec.decode(new RegistryFriendlyByteBuf(event.getPayload()));
            context.enqueueWork(() -> handler.accept(packet, player));
            context.setPacketHandled(true);
        });
    }

    private static <T extends CustomPacketPayload> EventNetworkChannel channel(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        PayloadCodecs.register(type, codec);
        EventNetworkChannel channel = NetworkRegistry.ChannelBuilder.named(type.id())
                .networkProtocolVersion(() -> PROTOCOL)
                .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
                .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
                .eventNetworkChannel();
        CHANNELS.put(type.id(), channel);
        return channel;
    }
}
