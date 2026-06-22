package com.egakh.familytree.network.payloads;

import com.egakh.familytree.data.AnimalRecord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record FamilyTreeSnapshotPayload(List<AnimalRecord> records, long currentWorldDay, long currentEpochMillis,
                                        boolean mayViewAll, boolean viewingAll)
        implements CustomPacketPayload {

    private static final int MAX_RECORDS = 8192;

    public static final Type<FamilyTreeSnapshotPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("familytree", "snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FamilyTreeSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    AnimalRecord.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_RECORDS)),
                    FamilyTreeSnapshotPayload::records,
                    ByteBufCodecs.LONG,
                    FamilyTreeSnapshotPayload::currentWorldDay,
                    ByteBufCodecs.LONG,
                    FamilyTreeSnapshotPayload::currentEpochMillis,
                    ByteBufCodecs.BOOL,
                    FamilyTreeSnapshotPayload::mayViewAll,
                    ByteBufCodecs.BOOL,
                    FamilyTreeSnapshotPayload::viewingAll,
                    FamilyTreeSnapshotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
