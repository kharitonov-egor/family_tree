package com.egakh.familytree.network.payloads;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

/** Asks which relatives of the animal the player looks at are loaded near the player. */
public record RelativesRequest(UUID requestId, UUID target) implements CustomPacketPayload {
    public static final Type<RelativesRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("familytree", "relatives_request_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RelativesRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RelativesRequest::requestId,
            UUIDUtil.STREAM_CODEC, RelativesRequest::target, RelativesRequest::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
