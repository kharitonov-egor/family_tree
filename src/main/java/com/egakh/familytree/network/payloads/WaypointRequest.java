package com.egakh.familytree.network.payloads;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

/** Asks for a pet's position so the client can add a map waypoint. It never prints the position in chat. */
public record WaypointRequest(UUID requestId, UUID petId) implements CustomPacketPayload {
    public static final Type<WaypointRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("familytree", "waypoint_request_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WaypointRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, WaypointRequest::requestId,
            UUIDUtil.STREAM_CODEC, WaypointRequest::petId, WaypointRequest::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
