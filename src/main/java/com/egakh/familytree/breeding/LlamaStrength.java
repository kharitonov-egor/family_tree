package com.egakh.familytree.breeding;

import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;

/**
 * {@code Llama.getBreedOffspring} on every supported target: a uniform draw from 1 to the stronger parent's
 * strength, plus one with a 3% chance, clamped to 1..5 by {@code setStrength}.
 */
public final class LlamaStrength {
    public static final int MIN = 1;
    public static final int MAX = 5;
    private static final float BONUS = 0.03F;
    /** {@code nextFloat()} returns multiples of 2^-24, so this is the exact chance that it is below 0.03F. */
    public static final double BONUS_CHANCE = Math.ceil(BONUS * (double) (1 << 24)) / (1 << 24);

    private LlamaStrength() {}

    public static int sample(int parentA, int parentB, IntUnaryOperator nextInt, DoubleSupplier nextFloat) {
        int strength = nextInt.applyAsInt(Math.max(parentA, parentB)) + 1;
        if (nextFloat.getAsDouble() < BONUS) strength++;
        return Math.max(MIN, Math.min(MAX, strength));
    }

    /** Index k holds P(strength = k); index 0 is unused. */
    public static double[] distribution(int parentA, int parentB) {
        int top = Math.max(MIN, Math.min(MAX, Math.max(parentA, parentB)));
        double[] chance = new double[MAX + 1];
        for (int base = 1; base <= top; base++) {
            chance[base] += (1 - BONUS_CHANCE) / top;
            chance[Math.min(MAX, base + 1)] += BONUS_CHANCE / top;
        }
        return chance;
    }

    public static double mean(double[] distribution) {
        double mean = 0;
        for (int k = MIN; k <= MAX; k++) mean += k * distribution[k];
        return mean;
    }

    public static double chanceAbove(double[] distribution, int value) {
        double above = 0;
        for (int k = Math.max(MIN, value + 1); k <= MAX; k++) above += distribution[k];
        return above;
    }

    public static int lowest(double[] distribution) {
        for (int k = MIN; k <= MAX; k++) if (distribution[k] > 0) return k;
        return MIN;
    }

    public static int highest(double[] distribution) {
        for (int k = MAX; k >= MIN; k--) if (distribution[k] > 0) return k;
        return MAX;
    }

    /** The most likely values. Several strengths can tie, so this returns the first and last of the tied run. */
    public static int[] mostLikely(double[] distribution) {
        double best = 0;
        for (int k = MIN; k <= MAX; k++) best = Math.max(best, distribution[k]);
        int first = -1, last = -1;
        for (int k = MIN; k <= MAX; k++) {
            if (distribution[k] < best - 1e-12) continue;
            if (first < 0) first = k;
            last = k;
        }
        return new int[]{first, last};
    }
}
