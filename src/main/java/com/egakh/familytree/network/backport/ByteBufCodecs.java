package com.egakh.familytree.network.backport;

//? if <1.21 {
/*import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Writes the same bytes as Minecraft 1.20.5+ ByteBufCodecs.
public final class ByteBufCodecs {
    public static final StreamCodec<ByteBuf, Boolean> BOOL = StreamCodec.of(ByteBuf::writeBoolean, ByteBuf::readBoolean);
    public static final StreamCodec<ByteBuf, Double> DOUBLE = StreamCodec.of(ByteBuf::writeDouble, ByteBuf::readDouble);
    public static final StreamCodec<ByteBuf, Integer> VAR_INT = StreamCodec.of(
            (buffer, value) -> new FriendlyByteBuf(buffer).writeVarInt(value), buffer -> new FriendlyByteBuf(buffer).readVarInt());
    public static final StreamCodec<ByteBuf, java.util.UUID> UUID = StreamCodec.of(
            (buffer, value) -> new FriendlyByteBuf(buffer).writeUUID(value), buffer -> new FriendlyByteBuf(buffer).readUUID());
    public static final StreamCodec<ByteBuf, String> STRING_UTF8 = stringUtf8(32767);

    private ByteBufCodecs() {}

    public static StreamCodec<ByteBuf, String> stringUtf8(int maxLength) {
        return StreamCodec.of((buffer, value) -> new FriendlyByteBuf(buffer).writeUtf(value, maxLength),
                buffer -> new FriendlyByteBuf(buffer).readUtf(maxLength));
    }

    public static <B extends ByteBuf, V> StreamCodec<B, Optional<V>> optional(StreamCodec<B, V> codec) {
        return StreamCodec.of((buffer, value) -> {
            buffer.writeBoolean(value.isPresent());
            value.ifPresent(present -> codec.encode(buffer, present));
        }, buffer -> buffer.readBoolean() ? Optional.of(codec.decode(buffer)) : Optional.empty());
    }

    public static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list(int maxSize) {
        return codec -> StreamCodec.of((buffer, values) -> {
            if (values.size() > maxSize) throw new EncoderException(values.size() + " elements exceeded max size of: " + maxSize);
            new FriendlyByteBuf(buffer).writeVarInt(values.size());
            for (V value : values) codec.encode(buffer, value);
        }, buffer -> {
            int size = new FriendlyByteBuf(buffer).readVarInt();
            if (size > maxSize) throw new DecoderException(size + " elements exceeded max size of: " + maxSize);
            List<V> values = new ArrayList<>(Math.min(size, 65536));
            for (int i = 0; i < size; i++) values.add(codec.decode(buffer));
            return values;
        });
    }
}
*///?}
