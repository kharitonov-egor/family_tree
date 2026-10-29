package com.egakh.familytree.network.backport;

//? if <1.21 {
/*import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Loader adapters for 1.20.1 send raw channel buffers, so they look up each payload's codec here.
public final class PayloadCodecs {
    private static final Map<Identifier, StreamCodec<? super RegistryFriendlyByteBuf, ? extends CustomPacketPayload>> CODECS =
            new ConcurrentHashMap<>();

    private PayloadCodecs() {}

    public static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,
                                                               StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        CODECS.put(type.id(), codec);
    }

    @SuppressWarnings("unchecked")
    public static FriendlyByteBuf encode(CustomPacketPayload payload) {
        var codec = (StreamCodec<? super RegistryFriendlyByteBuf, CustomPacketPayload>) CODECS.get(payload.type().id());
        if (codec == null) throw new IllegalArgumentException("Unregistered payload " + payload.type().id());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer());
        codec.encode(buffer, payload);
        return buffer;
    }
}
*///?}
