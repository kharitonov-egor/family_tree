package com.egakh.familytree.network.payloads;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public record LinkParentsRequest(UUID requestId, UUID child, UUID parentA, UUID parentB, boolean clear)
        implements CustomPacketPayload {
    public static final Type<LinkParentsRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("familytree", "link_parents"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkParentsRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, LinkParentsRequest::requestId,
            UUIDUtil.STREAM_CODEC, LinkParentsRequest::child,
            UUIDUtil.STREAM_CODEC, LinkParentsRequest::parentA,
            UUIDUtil.STREAM_CODEC, LinkParentsRequest::parentB,
            ByteBufCodecs.BOOL, LinkParentsRequest::clear, LinkParentsRequest::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
