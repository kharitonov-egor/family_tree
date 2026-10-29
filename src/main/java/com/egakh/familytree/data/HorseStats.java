package com.egakh.familytree.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/** Base attribute values in Minecraft's units. A null value is unknown. */
public record HorseStats(Double speed, Double jumpStrength, Double maxHealth, Integer strength) {
    public static final Codec<HorseStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("speed").forGetter(stats -> Optional.ofNullable(stats.speed)),
            Codec.DOUBLE.optionalFieldOf("jump_strength").forGetter(stats -> Optional.ofNullable(stats.jumpStrength)),
            Codec.DOUBLE.optionalFieldOf("max_health").forGetter(stats -> Optional.ofNullable(stats.maxHealth)),
            Codec.INT.optionalFieldOf("strength").forGetter(stats -> Optional.ofNullable(stats.strength))
    ).apply(instance, (speed, jump, health, strength) ->
            new HorseStats(speed.orElse(null), jump.orElse(null), health.orElse(null), strength.orElse(null))));

    public static final StreamCodec<FriendlyByteBuf, HorseStats> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.DOUBLE), stats -> Optional.ofNullable(stats.speed),
            ByteBufCodecs.optional(ByteBufCodecs.DOUBLE), stats -> Optional.ofNullable(stats.jumpStrength),
            ByteBufCodecs.optional(ByteBufCodecs.DOUBLE), stats -> Optional.ofNullable(stats.maxHealth),
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), stats -> Optional.ofNullable(stats.strength),
            (speed, jump, health, strength) ->
                    new HorseStats(speed.orElse(null), jump.orElse(null), health.orElse(null), strength.orElse(null)));
}
