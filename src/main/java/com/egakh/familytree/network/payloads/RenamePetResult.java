package com.egakh.familytree.network.payloads;

import com.egakh.familytree.data.AnimalRecord;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.UUID;

public record RenamePetResult(UUID requestId, int status, Optional<AnimalRecord> record) implements CustomPacketPayload {
    public static final Type<RenamePetResult> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("familytree", "rename_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenamePetResult> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RenamePetResult::requestId,
            ByteBufCodecs.VAR_INT, RenamePetResult::status,
            ByteBufCodecs.optional(AnimalRecord.STREAM_CODEC), RenamePetResult::record,
            RenamePetResult::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
