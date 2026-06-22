package com.egakh.familytree.network.payloads;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenFamilyTreeRequest(boolean requestAll) implements CustomPacketPayload {
    public static final Type<OpenFamilyTreeRequest> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("familytree", "open_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFamilyTreeRequest> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    OpenFamilyTreeRequest::requestAll,
                    OpenFamilyTreeRequest::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
