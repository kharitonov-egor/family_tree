package com.egakh.familytree.network.payloads;

import com.egakh.familytree.data.AnimalRecord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record FamilyTreeSnapshotPayload(List<AnimalRecord> records, long currentWorldDay, long currentEpochMillis)
        implements CustomPacketPayload {

    public static final Type<FamilyTreeSnapshotPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("familytree", "snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FamilyTreeSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    AnimalRecord.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    FamilyTreeSnapshotPayload::records,
                    ByteBufCodecs.LONG,
                    FamilyTreeSnapshotPayload::currentWorldDay,
                    ByteBufCodecs.LONG,
                    FamilyTreeSnapshotPayload::currentEpochMillis,
                    FamilyTreeSnapshotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
