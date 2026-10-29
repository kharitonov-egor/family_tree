package com.egakh.familytree.network.backport;

//? if <1.21 {
/*import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface StreamCodec<B, V> {
    V decode(B buffer);

    void encode(B buffer, V value);

    default <O> StreamCodec<B, O> apply(CodecOperation<B, V, O> operation) {
        return operation.apply(this);
    }

    interface CodecOperation<B, S, T> {
        StreamCodec<B, T> apply(StreamCodec<B, S> codec);
    }

    static <B, V> StreamCodec<B, V> of(BiConsumer<B, V> encoder, Function<B, V> decoder) {
        return new StreamCodec<>() {
            @Override public V decode(B buffer) { return decoder.apply(buffer); }
            @Override public void encode(B buffer, V value) { encoder.accept(buffer, value); }
        };
    }

    static <B, C, T1> StreamCodec<B, C> composite(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                                 Function<T1, C> factory) {
        return of((buffer, value) -> codec1.encode(buffer, getter1.apply(value)),
                buffer -> factory.apply(codec1.decode(buffer)));
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                                     StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
                                                     BiFunction<T1, T2, C> factory) {
        return of((buffer, value) -> {
            codec1.encode(buffer, getter1.apply(value));
            codec2.encode(buffer, getter2.apply(value));
        }, buffer -> {
            T1 first = codec1.decode(buffer);
            T2 second = codec2.decode(buffer);
            return factory.apply(first, second);
        });
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                                         StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
                                                         StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
                                                         Function3<T1, T2, T3, C> factory) {
        return of((buffer, value) -> {
            codec1.encode(buffer, getter1.apply(value));
            codec2.encode(buffer, getter2.apply(value));
            codec3.encode(buffer, getter3.apply(value));
        }, buffer -> {
            T1 first = codec1.decode(buffer);
            T2 second = codec2.decode(buffer);
            T3 third = codec3.decode(buffer);
            return factory.apply(first, second, third);
        });
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                                             StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
                                                             StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
                                                             StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
                                                             Function4<T1, T2, T3, T4, C> factory) {
        return of((buffer, value) -> {
            codec1.encode(buffer, getter1.apply(value));
            codec2.encode(buffer, getter2.apply(value));
            codec3.encode(buffer, getter3.apply(value));
            codec4.encode(buffer, getter4.apply(value));
        }, buffer -> {
            T1 first = codec1.decode(buffer);
            T2 second = codec2.decode(buffer);
            T3 third = codec3.decode(buffer);
            T4 fourth = codec4.decode(buffer);
            return factory.apply(first, second, third, fourth);
        });
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                                                 StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
                                                                 StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
                                                                 StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
                                                                 StreamCodec<? super B, T5> codec5, Function<C, T5> getter5,
                                                                 Function5<T1, T2, T3, T4, T5, C> factory) {
        return of((buffer, value) -> {
            codec1.encode(buffer, getter1.apply(value));
            codec2.encode(buffer, getter2.apply(value));
            codec3.encode(buffer, getter3.apply(value));
            codec4.encode(buffer, getter4.apply(value));
            codec5.encode(buffer, getter5.apply(value));
        }, buffer -> {
            T1 first = codec1.decode(buffer);
            T2 second = codec2.decode(buffer);
            T3 third = codec3.decode(buffer);
            T4 fourth = codec4.decode(buffer);
            T5 fifth = codec5.decode(buffer);
            return factory.apply(first, second, third, fourth, fifth);
        });
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                                                     StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
                                                                     StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
                                                                     StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
                                                                     StreamCodec<? super B, T5> codec5, Function<C, T5> getter5,
                                                                     StreamCodec<? super B, T6> codec6, Function<C, T6> getter6,
                                                                     Function6<T1, T2, T3, T4, T5, T6, C> factory) {
        return of((buffer, value) -> {
            codec1.encode(buffer, getter1.apply(value));
            codec2.encode(buffer, getter2.apply(value));
            codec3.encode(buffer, getter3.apply(value));
            codec4.encode(buffer, getter4.apply(value));
            codec5.encode(buffer, getter5.apply(value));
            codec6.encode(buffer, getter6.apply(value));
        }, buffer -> {
            T1 first = codec1.decode(buffer);
            T2 second = codec2.decode(buffer);
            T3 third = codec3.decode(buffer);
            T4 fourth = codec4.decode(buffer);
            T5 fifth = codec5.decode(buffer);
            T6 sixth = codec6.decode(buffer);
            return factory.apply(first, second, third, fourth, fifth, sixth);
        });
    }
}
*///?}
