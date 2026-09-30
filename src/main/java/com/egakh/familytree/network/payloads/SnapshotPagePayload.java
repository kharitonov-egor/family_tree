package com.egakh.familytree.network.payloads;

import com.egakh.familytree.data.AnimalRecord;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.UUID;

public record SnapshotPagePayload(UUID requestId, int index, int pageCount, int totalRecords,
                                  List<AnimalRecord> records, long currentWorldDay, long currentEpochMillis,
                                  boolean mayViewAll, boolean viewingAll, boolean mayManageAll)
        implements CustomPacketPayload {
    public static final Type<SnapshotPagePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("familytree", "snapshot_pages_v3"));
    private static final StreamCodec<net.minecraft.network.FriendlyByteBuf, List<AnimalRecord>> RECORDS_CODEC =
            AnimalRecord.STREAM_CODEC.apply(ByteBufCodecs.list(8192));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, SnapshotPagePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override public SnapshotPagePayload decode(net.minecraft.network.FriendlyByteBuf buf) {
                    return new SnapshotPagePayload(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            RECORDS_CODEC.decode(buf), buf.readLong(), buf.readLong(),
                            buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
                }
                @Override public void encode(net.minecraft.network.FriendlyByteBuf buf, SnapshotPagePayload page) {
                    buf.writeUUID(page.requestId());
                    buf.writeVarInt(page.index());
                    buf.writeVarInt(page.pageCount());
                    buf.writeVarInt(page.totalRecords());
                    RECORDS_CODEC.encode(buf, page.records());
                    buf.writeLong(page.currentWorldDay());
                    buf.writeLong(page.currentEpochMillis());
                    buf.writeBoolean(page.mayViewAll());
                    buf.writeBoolean(page.viewingAll());
                    buf.writeBoolean(page.mayManageAll());
                }
            };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
