package com.egakh.familytree.network.payloads;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LocatePetRequest(UUID petId) implements CustomPacketPayload {
    public static final Type<LocatePetRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("familytree", "locate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LocatePetRequest> STREAM_CODEC =
            StreamCodec.composite(UUIDUtil.STREAM_CODEC, LocatePetRequest::petId, LocatePetRequest::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
