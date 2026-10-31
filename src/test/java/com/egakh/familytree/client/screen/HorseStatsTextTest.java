package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.util.HorseStatUnits.Stat;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** QA U11: Jade's own Horse Stats lines showed speed and jump next to Family Tree's. */
class HorseStatsTextTest {
    private static AnimalRecord horse(Integer strength) {
        AnimalRecord record = new AnimalRecord(new UUID(3, 1), "minecraft:horse", "Comet", false, null, null,
                1, 1, false, null, null, new UUID(1, 1), "Owner", null, null, null);
        record.setHorseStats(new HorseStats(0.3, 0.7, 26.0, strength));
        return record;
    }

    @Test
    void statsAnotherTooltipShowsAreLeftOut() {
        assertEquals(3, HorseStatsText.details(horse(null), id -> null).size());
        assertEquals(1, HorseStatsText.details(horse(null), id -> null, EnumSet.of(Stat.SPEED, Stat.JUMP)).size());
    }

    @Test
    void llamaStrengthCanBeLeftOutAlone() {
        assertEquals(4, HorseStatsText.details(horse(4), id -> null, EnumSet.noneOf(Stat.class)).size());
        assertEquals(3, HorseStatsText.details(horse(4), id -> null, EnumSet.of(Stat.STRENGTH)).size());
    }
}
