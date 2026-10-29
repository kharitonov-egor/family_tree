package com.egakh.familytree.util;

import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.util.HorseStatUnits.Stat;
import com.egakh.familytree.util.HorseStatUnits.Trend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HorseStatUnitsTest {
    @Test void speedMatchesTheWikiRangeInBlocksPerSecond() {
        assertEquals(4.86, HorseStatUnits.blocksPerSecond(0.1125), 0.005);
        assertEquals(9.71, HorseStatUnits.blocksPerSecond(0.225), 0.005);
        assertEquals(14.57, HorseStatUnits.blocksPerSecond(0.3375), 0.005);
    }

    @Test void jumpHeightMatchesTheWikiValues() {
        assertEquals(1.153, HorseStatUnits.jumpHeight(0.4), 0.0005);
        assertEquals(3.124, HorseStatUnits.jumpHeight(0.7), 0.0005);
        assertEquals(5.9197, HorseStatUnits.jumpHeight(1.0), 0.00005);
        assertEquals(0, HorseStatUnits.jumpHeight(0), 0);
        assertTrue(HorseStatUnits.jumpHeight(0.71) > HorseStatUnits.jumpHeight(0.7));
    }

    @Test void healthIsShownInHeartsAndLlamaStrengthInChestSlots() {
        assertEquals(7.5, HorseStatUnits.hearts(15), 0);
        assertEquals(15, HorseStatUnits.hearts(30), 0);
        for (int strength = 1; strength <= 5; strength++) assertEquals(3 * strength, HorseStatUnits.chestSlots(strength));
    }

    @Test void formatsInPlayerUnitsAndMarksUnknownValues() {
        HorseStats stats = new HorseStats(0.3375, 1.0, 30.0, 3);
        assertEquals("14.6", Stat.SPEED.display(stats));
        assertEquals("5.92", Stat.JUMP.display(stats));
        assertEquals("15.0", Stat.HEALTH.display(stats));
        assertEquals("3", Stat.STRENGTH.display(stats));
        HorseStats partial = new HorseStats(0.2, null, Double.NaN, null);
        assertEquals("?", Stat.JUMP.display(partial));
        assertEquals("?", Stat.HEALTH.display(partial));
        assertEquals("?", Stat.STRENGTH.display(partial));
        assertEquals("?", Stat.SPEED.display(null));
    }

    @Test void marksFoalsThatBeatOrTrailBothParents() {
        HorseStats fast = new HorseStats(0.30, 0.9, 28.0, null);
        HorseStats slow = new HorseStats(0.20, 0.5, 18.0, null);
        HorseStats middle = new HorseStats(0.25, 0.7, 22.0, null);
        HorseStats fastest = new HorseStats(0.31, 0.95, 29.0, null);
        HorseStats slowest = new HorseStats(0.19, 0.45, 16.0, null);
        for (Stat stat : new Stat[]{Stat.SPEED, Stat.JUMP, Stat.HEALTH}) {
            assertEquals(Trend.BEATS_BOTH, HorseStatUnits.compare(stat, fastest, fast, slow), stat.name());
            assertEquals(Trend.TRAILS_BOTH, HorseStatUnits.compare(stat, slowest, slow, fast), stat.name());
            assertEquals(Trend.BETWEEN, HorseStatUnits.compare(stat, middle, fast, slow), stat.name());
            assertEquals(Trend.UNKNOWN, HorseStatUnits.compare(stat, middle, fast, null), stat.name());
        }
        assertEquals(Trend.UNKNOWN, HorseStatUnits.compare(Stat.STRENGTH, middle, fast, slow));
        assertEquals(Trend.BEATS_BOTH, HorseStatUnits.compare(Stat.STRENGTH,
                new HorseStats(null, null, null, 4), new HorseStats(null, null, null, 3), new HorseStats(null, null, null, 2)));
    }

    @Test void valuesThatLookEqualAtDisplayPrecisionGetNoMarker() {
        HorseStats parent = new HorseStats(0.30000, 0.8, 24.0, null);
        HorseStats foal = new HorseStats(0.30001, 0.80001, 24.01, null);
        for (Stat stat : new Stat[]{Stat.SPEED, Stat.JUMP, Stat.HEALTH})
            assertEquals(Trend.BETWEEN, HorseStatUnits.compare(stat, foal, parent, parent), stat.name());
    }
}
