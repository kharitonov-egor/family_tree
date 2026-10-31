package com.egakh.familytree.breeding;

import com.egakh.familytree.breeding.OffspringStats.Attribute;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/** Compares the exact distribution with samples drawn by the running Minecraft version's own breeding method. */
class OffspringStatsTest {
    private static final int SAMPLES = 200_000;
    private static Class<?> gameCode;
    private static Method gameMethod;

    @BeforeAll
    static void findGameMethod() throws Exception {
        gameCode = GameHorseCode.load();
        gameMethod = gameCode.getMethod("createOffspringAttribute",
                double.class, double.class, double.class, double.class, RandomSource.class);
    }

    private static double game(double a, double b, double min, double max, RandomSource random) {
        try {
            return (double) gameMethod.invoke(null, a, b, min, max, random);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    /** AbstractHorse's static ranges call its generators with all-zero and all-maximum draws, then store floats. */
    @Test
    void rangesMatchTheGameConstants() throws Exception {
        Method speed = gameCode.getMethod("generateSpeed", DoubleSupplier.class);
        Method jump = gameCode.getMethod("generateJumpStrength", DoubleSupplier.class);
        Method health = gameCode.getMethod("generateMaxHealth", IntUnaryOperator.class);
        DoubleSupplier zero = () -> 0.0, one = () -> 1.0;
        IntUnaryOperator lowest = bound -> 0, highest = bound -> bound - 1;
        assertEquals((float) (double) speed.invoke(null, zero), Attribute.SPEED.min, 0);
        assertEquals((float) (double) speed.invoke(null, one), Attribute.SPEED.max, 0);
        assertEquals((float) (double) jump.invoke(null, zero), Attribute.JUMP.min, 0);
        assertEquals((float) (double) jump.invoke(null, one), Attribute.JUMP.max, 0);
        assertEquals((float) health.invoke(null, lowest), Attribute.HEALTH.min, 0);
        assertEquals((float) health.invoke(null, highest), Attribute.HEALTH.max, 0);
        assertEquals(15, Attribute.HEALTH.min, 0);
        assertEquals(30, Attribute.HEALTH.max, 0);
    }

    @Test
    void copyOfTheFormulaGivesTheGamesValuesForTheSameDraws() {
        RandomSource left = RandomSource.create(42), right = RandomSource.create(42);
        double[][] parents = {{0.2, 0.3}, {0.1125, 0.3375}, {0.3375, 0.3375}, {0.05, 0.5}, {0.9, 0.95}, {16, 29.5}};
        for (double[] pair : parents) {
            Attribute attribute = pair[0] < 0.36 ? Attribute.SPEED : pair[0] < 2 ? Attribute.JUMP : Attribute.HEALTH;
            for (int i = 0; i < 1000; i++)
                assertEquals(game(pair[0], pair[1], attribute.min, attribute.max, left),
                        OffspringStats.sample(pair[0], pair[1], attribute.min, attribute.max, right::nextDouble), 0);
        }
    }

    @Test
    void exactDistributionMatchesTheGameOverManySamples() {
        Object[][] cases = {
                {Attribute.SPEED, 0.20, 0.25}, {Attribute.SPEED, Attribute.SPEED.max, Attribute.SPEED.max},
                {Attribute.SPEED, Attribute.SPEED.min, Attribute.SPEED.max}, {Attribute.SPEED, 0.30, 0.31},
                {Attribute.JUMP, 0.45, 0.95}, {Attribute.JUMP, 0.7, 0.7}, {Attribute.JUMP, 0.98, 1.0},
                {Attribute.HEALTH, 15.0, 15.0}, {Attribute.HEALTH, 22.0, 29.0}, {Attribute.HEALTH, 30.0, 40.0}};
        RandomSource random = RandomSource.create(20261030);
        for (Object[] test : cases) {
            Attribute attribute = (Attribute) test[0];
            double a = (double) test[1], b = (double) test[2];
            OffspringStats.Forecast forecast = OffspringStats.forecast(attribute, a, b);
            double[] samples = new double[SAMPLES];
            double sum = 0;
            for (int i = 0; i < SAMPLES; i++) {
                samples[i] = game(a, b, attribute.min, attribute.max, random);
                sum += samples[i];
            }
            Arrays.sort(samples);
            String label = attribute + " " + a + " " + b;
            assertTrue(samples[0] >= forecast.lowest - 1e-12, label + " lowest");
            assertTrue(samples[SAMPLES - 1] <= forecast.highest + 1e-12, label + " highest");
            assertTrue(samples[0] - forecast.lowest < (forecast.highest - forecast.lowest) * 0.06, label + " lowest is reachable");
            assertTrue(forecast.highest - samples[SAMPLES - 1] < (forecast.highest - forecast.lowest) * 0.06, label + " highest is reachable");
            for (int q = 1; q < 20; q++) {
                double v = forecast.lowest + (forecast.highest - forecast.lowest) * q / 20;
                double empirical = upperBound(samples, v) / (double) SAMPLES;
                assertEquals(forecast.cdf(v), empirical, 0.005, label + " cdf at " + v);
            }
            double mean = sum / SAMPLES, spread = (forecast.highest - forecast.lowest);
            assertEquals(forecast.mean(), mean, spread * 0.003, label + " mean");
            double above = 1 - upperBound(samples, forecast.betterParent()) / (double) SAMPLES;
            assertEquals(forecast.chanceAboveBoth(), above, 0.005, label + " beats both");
            double[] bars = forecast.histogram(BreedingPlan.BARS);
            assertEquals(1, Arrays.stream(bars).sum(), 1e-9, label + " bars");
            int peak = 0;
            int[] counts = new int[bars.length];
            for (double sample : samples) counts[Math.min(bars.length - 1, (int) ((sample - forecast.lowest) / spread * bars.length))]++;
            for (int i = 0; i < bars.length; i++) {
                assertEquals(bars[i], counts[i] / (double) SAMPLES, 0.006, label + " bar " + i);
                if (counts[i] > counts[peak]) peak = i;
            }
            double likely = forecast.mostLikely();
            double peakCenter = forecast.lowest + spread * (peak + 0.5) / bars.length;
            assertEquals(peakCenter, likely, spread * 1.6 / bars.length, label + " most likely");
        }
    }

    @Test
    void closedFormMeanMatchesNumericIntegration() {
        double[][] pairs = {{0.1125, 0.1125}, {0.2, 0.3}, {0.3375, 0.3375}, {0.1125, 0.3375}, {0.33, 0.335}};
        for (double[] pair : pairs) {
            OffspringStats.Forecast forecast = OffspringStats.forecast(Attribute.SPEED, pair[0], pair[1]);
            int steps = 20_000;
            double h = (forecast.highest - forecast.lowest) / steps, integral = 0;
            for (int i = 0; i < steps; i++) {
                double v = forecast.lowest + (i + 0.5) * h;
                integral += v * forecast.density(v) * h;
            }
            assertEquals(integral, forecast.mean(), 1e-6);
            assertEquals(1, forecast.cdf(forecast.highest), 0);
            assertEquals(0, forecast.cdf(forecast.lowest - 1e-9), 0);
        }
    }

    @Test
    void identicalTopParentsReflectBelowTheMaximum() {
        OffspringStats.Forecast best = OffspringStats.forecast(Attribute.SPEED, Attribute.SPEED.max, Attribute.SPEED.max);
        assertEquals(Attribute.SPEED.max, best.highest, 1e-12);
        assertEquals(Attribute.SPEED.max - 0.15 * Attribute.SPEED.width(), best.lowest, 1e-12);
        assertEquals(0, best.chanceAboveBoth(), 0);
        assertEquals(Attribute.SPEED.max, best.mostLikely(), Attribute.SPEED.width() * 0.002);
    }

    @Test
    void theMiddleOfTwoEqualParentsIsTheMostLikelyValue() {
        OffspringStats.Forecast forecast = OffspringStats.forecast(Attribute.JUMP, 0.7, 0.7);
        assertEquals(0.7, forecast.mostLikely(), 0.001);
        assertEquals(0.7, forecast.mean(), 1e-9);
        assertEquals(0.5, forecast.chanceAboveBoth(), 1e-9);
        assertEquals(0.7 - 0.09, forecast.lowest, 1e-6);
        assertEquals(0.7 + 0.09, forecast.highest, 1e-6);
    }

    /** Count of sorted values that are at most v. */
    private static int upperBound(double[] sorted, double v) {
        int low = 0, high = sorted.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (sorted[mid] <= v) low = mid + 1;
            else high = mid;
        }
        return low;
    }
}
