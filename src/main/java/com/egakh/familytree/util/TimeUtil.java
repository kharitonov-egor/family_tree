package com.egakh.familytree.util;

import net.minecraft.world.level.Level;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    private static final long MILLIS_PER_DAY = 86_400_000L;
    private static final DateTimeFormatter REAL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TimeUtil() {}

    public static long currentWorldDay(Level world) {
        return world.getGameTime() / 24000L;
    }

    public static long currentEpochMillis() {
        return System.currentTimeMillis();
    }

    public static long realDaysBetween(long fromMillis, long toMillis) {
        if (toMillis < fromMillis) return 0L;
        return (toMillis - fromMillis) / MILLIS_PER_DAY;
    }

    public static String formatRealDate(long epochMillis) {
        LocalDate date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate();
        return date.format(REAL_DATE);
    }
}
