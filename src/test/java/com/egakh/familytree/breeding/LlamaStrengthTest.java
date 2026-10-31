package com.egakh.familytree.breeding;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlamaStrengthTest {
    @Test
    void distributionMatchesSamplingWithTheGamesRandomSource() {
        RandomSource random = RandomSource.create(5);
        int samples = 400_000;
        for (int a = 1; a <= 5; a++) {
            for (int b = 1; b <= 5; b++) {
                double[] exact = LlamaStrength.distribution(a, b);
                int[] counts = new int[6];
                for (int i = 0; i < samples; i++)
                    counts[LlamaStrength.sample(a, b, random::nextInt, random::nextFloat)]++;
                double total = 0;
                for (int k = 1; k <= 5; k++) {
                    assertEquals(exact[k], counts[k] / (double) samples, 0.004, a + "," + b + " strength " + k);
                    total += exact[k];
                }
                assertEquals(1, total, 1e-12);
            }
        }
    }

    @Test
    void onlyTheThreePercentBonusBeatsTheStrongerParent() {
        double[] three = LlamaStrength.distribution(3, 2);
        assertEquals(0.97 / 3, three[1], 1e-6);
        assertEquals(1.0 / 3, three[2], 1e-12);
        assertEquals(1.0 / 3, three[3], 1e-12);
        assertEquals(0.01, three[4], 1e-6);
        assertEquals(0.01, LlamaStrength.chanceAbove(three, 3), 1e-6);
        assertArrayEquals(new int[]{2, 3}, LlamaStrength.mostLikely(three));
        double[] five = LlamaStrength.distribution(5, 5);
        assertEquals(0, LlamaStrength.chanceAbove(five, 5), 0);
        assertEquals(1.03 / 5, five[5], 1e-6);
        assertEquals(5, LlamaStrength.highest(five));
        assertEquals(1, LlamaStrength.lowest(five));
    }
}
