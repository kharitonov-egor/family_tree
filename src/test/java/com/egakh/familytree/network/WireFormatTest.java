package com.egakh.familytree.network;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
//? if >=1.21 {
import net.minecraft.core.RegistryAccess;
//?}
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

/** 1.20.1 encodes payloads with backported codecs. The expected bytes are shared by every target. */
class WireFormatTest {
    @Test void snapshotPagesKeepTheirByteLayout() {
        AnimalRecord pet = new AnimalRecord(new UUID(1, 2), "minecraft:wolf", "Rover", false, new UUID(3, 4), null,
                7, 1234, true, 15L, null, new UUID(5, 6), "Owner", "minecraft:pale",
                new AnimalRecord.LastSeen(1.5, 64, -8, "minecraft:overworld", 14), new AnimalRecord.DeathCause("Creeper", null));
        pet.setTreeName("Snowball");
        var page = new SnapshotPagePayload(new UUID(7, 8), 0, 1, 1, List.of(pet), 5, 100, true, false, true);
        assertEquals("0000000000000007000000000000000800010101000000000000000100000000000000020e6d696e6563726166743a776f6c6605526f76657200010000000000000003000000000000000400000000000000000700000000000004d20101000000000000000f00010000000000000005000000000000"
                + "000601054f776e6572010e6d696e6563726166743a70616c65013ff80000000000004050000000000000c020000000000000136d696e6563726166743a6f766572776f726c64000000000000000e01010743726565706572000108536e6f7762616c6c00000000000000050000000000000064010001", bytes(SnapshotPagePayload.STREAM_CODEC, page, FriendlyByteBuf::new));
    }

    @Test void requestsAndResultsKeepTheirByteLayout() {
        assertEquals("0000000000000009000000000000000a01", bytes(OpenFamilyTreeRequest.STREAM_CODEC, new OpenFamilyTreeRequest(new UUID(9, 10), true), WireFormatTest::registryBuffer));
        assertEquals("000000000000000b000000000000000c00", bytes(DiscoverPetsRequest.STREAM_CODEC, new DiscoverPetsRequest(new UUID(11, 12), false), WireFormatTest::registryBuffer));
        assertEquals("000000000000000d000000000000000e", bytes(LocatePetRequest.STREAM_CODEC, new LocatePetRequest(new UUID(13, 14)), WireFormatTest::registryBuffer));
        assertEquals("000000000000000f000000000000001000000000000000110000000000000012084665726e20e78cab", bytes(RenamePetRequest.STREAM_CODEC, new RenamePetRequest(new UUID(15, 16), new UUID(17, 18), "Fern \u732b"), WireFormatTest::registryBuffer));
        assertEquals("0000000000000013000000000000001400000000000000150000000000000016000000000000001700000000000000180000000000000000000000000000000001", bytes(LinkParentsRequest.STREAM_CODEC, new LinkParentsRequest(new UUID(19, 20), new UUID(21, 22), new UUID(23, 24), new UUID(0, 0), true), WireFormatTest::registryBuffer));
        assertEquals("0000000000000019000000000000001aac02", bytes(LinkParentsResult.STREAM_CODEC, new LinkParentsResult(new UUID(25, 26), 300), WireFormatTest::registryBuffer));
        assertEquals("000000000000001b000000000000001c0200", bytes(RenamePetResult.STREAM_CODEC, new RenamePetResult(new UUID(27, 28), 2, Optional.empty()), WireFormatTest::registryBuffer));
    }

    private static <B extends ByteBuf, T> String bytes(StreamCodec<? super B, T> codec, T value, Function<ByteBuf, B> wrap) {
        B buffer = wrap.apply(Unpooled.buffer());
        try {
            codec.encode(buffer, value);
            String written = ByteBufUtil.hexDump(buffer);
            T decoded = codec.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            buffer.clear();
            codec.encode(buffer, decoded);
            assertEquals(written, ByteBufUtil.hexDump(buffer));
            return written;
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf registryBuffer(ByteBuf buffer) {
        //? if >=1.21 {
        return new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY);
        //?} else {
        /*return new RegistryFriendlyByteBuf(buffer);
        *///?}
    }
}
