package com.egakh.familytree.breeding;

import java.util.function.DoubleSupplier;

/**
 * Minecraft's foal attribute rule, read from {@code AbstractHorse.createOffspringAttribute} with javap on every
 * supported target from 1.20.1 to 26.4. The rule and its ranges are identical on all of them; 26.4 calls
 * {@code Math.clamp} where older versions call {@code Mth.clamp}, which gives the same result.
 *
 * <p>Each parent value is clamped to the attribute's range. The foal's value is the parents' average plus
 * {@code (|a - b| + 0.3 * (max - min)) * (U1 + U2 + U3 - 1.5) / 3} for three uniform draws, reflected back into the
 * range when it overshoots. The sum of three uniforms has the Irwin-Hall distribution, so every probability here
 * is exact and needs no sampling.
 */
public final class OffspringStats {
    /** Same expressions as {@code AbstractHorse}'s static ranges, so float rounding matches the game. */
    public enum Attribute {
        SPEED(speed(0.0), speed(1.0)),
        JUMP(jump(0.0), jump(1.0)),
        HEALTH(health(0), health(1));

        public final double min;
        public final double max;

        Attribute(float min, float max) {
            this.min = min;
            this.max = max;
        }

        public double width() {
            return max - min;
        }
    }

    public static final double CROSS_FACTOR = 0.15;
    private static final int MODE_SAMPLES = 2048;

    private OffspringStats() {}

    private static float speed(double draw) {
        return (float) ((0.45F + draw * 0.3 + draw * 0.3 + draw * 0.3) * 0.25);
    }

    private static float jump(double draw) {
        return (float) (0.4F + draw * 0.2 + draw * 0.2 + draw * 0.2);
    }

    /** {@code generateMaxHealth} adds {@code nextInt(8)} and {@code nextInt(9)}; the bounds use 0 and {@code bound - 1}. */
    private static float health(int top) {
        return 15.0F + top * 7 + top * 8;
    }

    /** The game's own computation, kept line for line so tests can sample it. */
    public static double sample(double parentA, double parentB, double min, double max, DoubleSupplier random) {
        if (max <= min) throw new IllegalArgumentException("Incorrect range for an attribute");
        parentA = clamp(parentA, min, max);
        parentB = clamp(parentB, min, max);
        double margin = CROSS_FACTOR * (max - min);
        double range = Math.abs(parentA - parentB) + margin * 2.0;
        double average = (parentA + parentB) / 2.0;
        double quality = (random.getAsDouble() + random.getAsDouble() + random.getAsDouble()) / 3.0 - 0.5;
        double value = average + range * quality;
        if (value > max) return max - (value - max);
        if (value < min) return min + (min - value);
        return value;
    }

    public static Forecast forecast(Attribute attribute, double parentA, double parentB) {
        return new Forecast(attribute.min, attribute.max, parentA, parentB);
    }

    /** The exact distribution of one foal attribute in Minecraft's units. */
    public static final class Forecast {
        public final double min, max;
        public final double parentA, parentB;
        private final double start, step;
        public final double lowest, highest;

        Forecast(double min, double max, double parentA, double parentB) {
            if (max <= min) throw new IllegalArgumentException("Incorrect range for an attribute");
            this.min = min;
            this.max = max;
            this.parentA = clamp(parentA, min, max);
            this.parentB = clamp(parentB, min, max);
            double range = Math.abs(this.parentA - this.parentB) + 2.0 * CROSS_FACTOR * (max - min);
            double average = (this.parentA + this.parentB) / 2.0;
            // value = start + step * S, where S is the sum of three uniform draws.
            this.start = average - range / 2.0;
            this.step = range / 3.0;
            double low = start, high = start + 3 * step;
            double foldedLow = low < min ? min : low, foldedHigh = high > max ? max : high;
            if (high > max) foldedLow = Math.min(foldedLow, 2 * max - high);
            if (low < min) foldedHigh = Math.max(foldedHigh, 2 * min - low);
            this.lowest = foldedLow;
            this.highest = foldedHigh;
        }

        /** P(foal value <= v). */
        public double cdf(double v) {
            if (v < lowest) return 0;
            if (v >= highest) return 1;
            double unfolded = rawCdf(v) - rawCdf(2 * min - v) + 1 - rawCdf(2 * max - v);
            return Math.max(0, Math.min(1, unfolded));
        }

        public double density(double v) {
            if (v < lowest || v > highest) return 0;
            return rawDensity(v) + rawDensity(2 * min - v) + rawDensity(2 * max - v);
        }

        /** Reflection moves the excess back inside, so the mean is the unfolded mean corrected at each bound. */
        public double mean() {
            double average = start + 1.5 * step;
            return average - 2 * step * IrwinHall.upperPartial((max - start) / step)
                    + 2 * step * IrwinHall.lowerPartial((min - start) / step);
        }

        public double mostLikely() {
            double best = lowest, bestDensity = -1;
            for (int i = 0; i <= MODE_SAMPLES; i++) {
                double v = lowest + (highest - lowest) * i / MODE_SAMPLES;
                double d = density(v);
                if (d > bestDensity + 1e-12) {
                    best = v;
                    bestDensity = d;
                }
            }
            return best;
        }

        public double betterParent() {
            return Math.max(parentA, parentB);
        }

        /** The chance the foal's value is strictly above both parents'. */
        public double chanceAboveBoth() {
            return 1 - cdf(betterParent());
        }

        public double chanceBelowBoth() {
            return cdf(Math.min(parentA, parentB));
        }

        /** Probability mass in {@code bins} equal slices from the lowest to the highest possible value. */
        public double[] histogram(int bins) {
            double[] mass = new double[bins];
            double previous = 0;
            for (int i = 0; i < bins; i++) {
                double edge = i == bins - 1 ? highest : lowest + (highest - lowest) * (i + 1) / bins;
                double next = cdf(edge);
                mass[i] = Math.max(0, next - previous);
                previous = next;
            }
            return mass;
        }

        private double rawCdf(double v) {
            return IrwinHall.cdf((v - start) / step);
        }

        private double rawDensity(double v) {
            return IrwinHall.density((v - start) / step) / step;
        }
    }

    /** The sum of three independent uniform draws on [0, 1). */
    static final class IrwinHall {
        private IrwinHall() {}

        static double cdf(double s) {
            if (s <= 0) return 0;
            if (s >= 3) return 1;
            if (s < 1) return s * s * s / 6;
            if (s < 2) return (-2 * s * s * s + 9 * s * s - 9 * s + 3) / 6;
            double r = 3 - s;
            return 1 - r * r * r / 6;
        }

        static double density(double s) {
            if (s <= 0 || s >= 3) return 0;
            if (s < 1) return s * s / 2;
            if (s < 2) return (-2 * s * s + 6 * s - 3) / 2;
            double r = 3 - s;
            return r * r / 2;
        }

        /** E[(t - S)+], the integral of the CDF from 0 to t. */
        static double lowerPartial(double t) {
            if (t <= 0) return 0;
            if (t >= 3) return t - 1.5;
            if (t < 1) return t * t * t * t / 24;
            if (t < 2) return 1.0 / 24 + (antiderivativeMiddle(t) - antiderivativeMiddle(1));
            double r = 3 - t;
            return 13.0 / 24 + (t - 2) + (r * r * r * r - 1) / 24;
        }

        /** E[(S - t)+]. */
        static double upperPartial(double t) {
            if (t >= 3) return 0;
            if (t <= 0) return 1.5 - t;
            return 1.5 - t + lowerPartial(t);
        }

        private static double antiderivativeMiddle(double s) {
            return (-s * s * s * s / 2 + 3 * s * s * s - 4.5 * s * s + 3 * s) / 6;
        }
    }

    static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
