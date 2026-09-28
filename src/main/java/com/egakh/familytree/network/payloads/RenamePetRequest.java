package com.egakh.familytree.network.payloads;

import com.egakh.familytree.naming.PetRenaming;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record RenamePetRequest(UUID requestId, UUID petId, String name) implements CustomPacketPayload {
    public static final Type<RenamePetRequest> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("familytree", "rename_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenamePetRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RenamePetRequest::requestId,
            UUIDUtil.STREAM_CODEC, RenamePetRequest::petId,
            ByteBufCodecs.stringUtf8(PetRenaming.MAX_NAME_LENGTH), RenamePetRequest::name,
            RenamePetRequest::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
