package com.egakh.familytree.network.payloads;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

/** {@code position} is a packed block position. Name and dimension are empty unless the status is FOUND. */
public record WaypointResult(UUID requestId, int status, String name, String dimension, long position) implements CustomPacketPayload {
    public enum Status { FOUND, NOT_ALLOWED, NO_POSITION, BUSY }

    public static final Type<WaypointResult> TYPE = new Type<>(Identifier.fromNamespaceAndPath("familytree", "waypoint_result_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WaypointResult> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, WaypointResult::requestId,
            ByteBufCodecs.VAR_INT, WaypointResult::status,
            ByteBufCodecs.stringUtf8(256), WaypointResult::name,
            ByteBufCodecs.stringUtf8(256), WaypointResult::dimension,
            ByteBufCodecs.LONG, WaypointResult::position, WaypointResult::new);

    public static WaypointResult failed(UUID requestId, Status status) {
        return new WaypointResult(requestId, status.ordinal(), "", "", 0);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
