package com.egakh.familytree.network.payloads;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.core.UUIDUtil;
import java.util.UUID;

public record DiscoverPetsRequest(UUID requestId, boolean requestAll) implements CustomPacketPayload {
    public DiscoverPetsRequest(boolean requestAll) { this(UUID.randomUUID(), requestAll); }
    public static final Type<DiscoverPetsRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("familytree", "discover_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DiscoverPetsRequest> STREAM_CODEC =
            StreamCodec.composite(UUIDUtil.STREAM_CODEC, DiscoverPetsRequest::requestId,
                    ByteBufCodecs.BOOL, DiscoverPetsRequest::requestAll, DiscoverPetsRequest::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
