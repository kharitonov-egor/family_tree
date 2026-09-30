package com.egakh.familytree.network.payloads;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public record LinkParentsResult(UUID requestId, int status) implements CustomPacketPayload {
    public static final Type<LinkParentsResult> TYPE = new Type<>(Identifier.fromNamespaceAndPath("familytree", "link_parents_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkParentsResult> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, LinkParentsResult::requestId,
            ByteBufCodecs.VAR_INT, LinkParentsResult::status, LinkParentsResult::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
