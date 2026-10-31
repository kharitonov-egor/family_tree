package com.egakh.familytree.network.payloads;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.UUID;

/**
 * Relatives of {@code target} that are loaded near the player, closest relation first. It carries entity UUIDs the
 * client already tracks and never a position. {@code total} counts every nearby relative before the cap.
 */
public record RelativesResult(UUID requestId, UUID target, int status, String targetName, int total,
                              List<Relative> relatives) implements CustomPacketPayload {
    public static final int MAX_RELATIVES = 32;

    /** The network sends the ordinal, so add new statuses at the end. */
    public enum Status { FOUND, NOT_TRACKED, BUSY, UNAVAILABLE, NOT_ALLOWED }

    /** {@code relation} is a {@link com.egakh.familytree.util.CloseRelation} ordinal. */
    public record Relative(UUID id, int relation, String name) {
        public static final StreamCodec<ByteBuf, Relative> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Relative::id,
                ByteBufCodecs.VAR_INT, Relative::relation,
                ByteBufCodecs.stringUtf8(256), Relative::name, Relative::new);
    }

    public static final Type<RelativesResult> TYPE = new Type<>(Identifier.fromNamespaceAndPath("familytree", "relatives_result_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RelativesResult> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RelativesResult::requestId,
            UUIDUtil.STREAM_CODEC, RelativesResult::target,
            ByteBufCodecs.VAR_INT, RelativesResult::status,
            ByteBufCodecs.stringUtf8(256), RelativesResult::targetName,
            ByteBufCodecs.VAR_INT, RelativesResult::total,
            Relative.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_RELATIVES)), RelativesResult::relatives, RelativesResult::new);

    public static RelativesResult empty(UUID requestId, UUID target, Status status) {
        return new RelativesResult(requestId, target, status.ordinal(), "", 0, List.of());
    }

    public Status statusValue() {
        Status[] values = Status.values();
        return status >= 0 && status < values.length ? values[status] : Status.UNAVAILABLE;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
