package com.egakh.familytree.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class WireCodecs {
    public static final StreamCodec<ByteBuf, Long> LONG = StreamCodec.of(ByteBuf::writeLong, ByteBuf::readLong);
    private WireCodecs() {}
}
