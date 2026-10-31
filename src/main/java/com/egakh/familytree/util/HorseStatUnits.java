package com.egakh.familytree.util;

import com.egakh.familytree.data.HorseStats;

import java.util.Locale;
import java.util.function.Function;

/** Converts base attributes to the units the Minecraft Wiki uses for horses. */
public final class HorseStatUnits {
    public static final double BLOCKS_PER_SECOND_PER_SPEED = 43.17;
    private static final double GRAVITY = 0.08;
    private static final double VERTICAL_DRAG = 0.98;

    public enum Stat {
        SPEED(HorseStats::speed, HorseStatUnits::blocksPerSecond, 1),
        JUMP(HorseStats::jumpStrength, HorseStatUnits::jumpHeight, 2),
        HEALTH(HorseStats::maxHealth, HorseStatUnits::hearts, 1),
        STRENGTH(stats -> stats.strength() == null ? null : stats.strength().doubleValue(), slots -> slots, 0);

        private final Function<HorseStats, Double> raw;
        private final Function<Double, Double> convert;
        private final int decimals;

        Stat(Function<HorseStats, Double> raw, Function<Double, Double> convert, int decimals) {
            this.raw = raw;
            this.convert = convert;
            this.decimals = decimals;
        }

        /** The value in player units, or null when it is unknown. */
        public Double value(HorseStats stats) {
            Double base = stats == null ? null : raw.apply(stats);
            return base == null || !Double.isFinite(base) ? null : convert.apply(base);
        }

        /** The value in Minecraft's attribute units, or null when it is unknown. */
        public Double base(HorseStats stats) {
            Double base = stats == null ? null : raw.apply(stats);
            return base == null || !Double.isFinite(base) ? null : base;
        }

        public double toPlayerUnits(double base) {
            return convert.apply(base);
        }

        public String format(double value) {
            return String.format(Locale.ROOT, "%." + decimals + "f", value);
        }

        /** The formatted value in player units, or "?" when it is unknown. */
        public String display(HorseStats stats) {
            Double value = value(stats);
            return value == null ? "?" : format(value);
        }

        /** Compares at display precision, so equal-looking numbers never get a marker. */
        public double rounded(double value) {
            return Double.parseDouble(format(value));
        }
    }

    public enum Trend { BEATS_BOTH, TRAILS_BOTH, BETWEEN, UNKNOWN }

    private HorseStatUnits() {}

    public static double blocksPerSecond(double speed) {
        return speed * BLOCKS_PER_SECOND_PER_SPEED;
    }

    /** Simulates a full-charge jump: move, apply gravity, then drag, until the horse starts falling. */
    public static double jumpHeight(double jumpStrength) {
        double height = 0;
        double velocity = jumpStrength;
        for (int tick = 0; velocity > 0 && tick < 1000; tick++) {
            height += velocity;
            velocity = (velocity - GRAVITY) * VERTICAL_DRAG;
        }
        return height;
    }

    public static double hearts(double maxHealth) {
        return maxHealth / 2.0;
    }

    public static int chestSlots(int strength) {
        return strength * 3;
    }

    public static Trend compare(Stat stat, HorseStats child, HorseStats parentA, HorseStats parentB) {
        Double value = stat.value(child), a = stat.value(parentA), b = stat.value(parentB);
        if (value == null || a == null || b == null) return Trend.UNKNOWN;
        double shown = stat.rounded(value), best = stat.rounded(Math.max(a, b)), worst = stat.rounded(Math.min(a, b));
        if (shown > best) return Trend.BEATS_BOTH;
        if (shown < worst) return Trend.TRAILS_BOTH;
        return Trend.BETWEEN;
    }
}
