package com.egakh.familytree.network.payloads;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.core.UUIDUtil;
import java.util.UUID;

public record OpenFamilyTreeRequest(UUID requestId, boolean requestAll) implements CustomPacketPayload {
    public OpenFamilyTreeRequest(boolean requestAll) { this(UUID.randomUUID(), requestAll); }
    public static final Type<OpenFamilyTreeRequest> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("familytree", "open_request_v3"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFamilyTreeRequest> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC,
                    OpenFamilyTreeRequest::requestId,
                    ByteBufCodecs.BOOL,
                    OpenFamilyTreeRequest::requestAll,
                    OpenFamilyTreeRequest::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
