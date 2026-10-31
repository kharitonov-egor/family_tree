package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.util.HorseStatUnits;
import com.egakh.familytree.util.HorseStatUnits.Stat;
import com.egakh.familytree.util.HorseStatUnits.Trend;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Player-facing horse stat text shared by tree cards, the details panel, the browser, and exports. */
public final class HorseStatsText {
    public static final String BEATS_MARKER = "▲";
    public static final String TRAILS_MARKER = "▼";
    public static final int BEATS_COLOR = 0xFF6FD26F;
    public static final int TRAILS_COLOR = 0xFFE86B6B;

    public record Segment(String text, Trend trend) {}

    public record DetailLine(String text, Trend trend, String parents) {}

    private HorseStatsText() {}

    public static boolean has(AnimalRecord pet) {
        return pet != null && pet.horseStats() != null;
    }

    public static List<Segment> card(AnimalRecord pet, Function<UUID, AnimalRecord> lookup) {
        List<Segment> segments = new ArrayList<>();
        if (!has(pet)) return segments;
        HorseStats stats = pet.horseStats();
        for (Stat stat : Stat.values()) {
            Double value = stat.value(stats);
            if (value == null) continue;
            String shown = stat == Stat.STRENGTH
                    ? String.valueOf(HorseStatUnits.chestSlots(stats.strength()))
                    : stat.format(value);
            segments.add(new Segment(Component.translatable("familytree.stats.short." + key(stat), shown).getString(),
                    trend(stat, pet, lookup)));
        }
        return segments;
    }

    public static List<DetailLine> details(AnimalRecord pet, Function<UUID, AnimalRecord> lookup) {
        return details(pet, lookup, Set.of());
    }

    /** The detail lines without {@code hidden}, such as the stats another mod's tooltip already shows. */
    public static List<DetailLine> details(AnimalRecord pet, Function<UUID, AnimalRecord> lookup, Set<Stat> hidden) {
        List<DetailLine> lines = new ArrayList<>();
        if (!has(pet)) return lines;
        HorseStats stats = pet.horseStats();
        AnimalRecord a = parent(pet.parentA(), lookup), b = parent(pet.parentB(), lookup);
        for (Stat stat : Stat.values()) {
            Double value = stat.value(stats);
            if (value == null || hidden.contains(stat)) continue;
            String text = stat == Stat.STRENGTH
                    ? Component.translatable("familytree.stats.strength", stats.strength(),
                            HorseStatUnits.chestSlots(stats.strength())).getString()
                    : Component.translatable("familytree.stats." + key(stat), stat.format(value)).getString();
            lines.add(new DetailLine(text, trend(stat, pet, lookup), parentValues(stat, a, b)));
        }
        return lines;
    }

    public static String sortValueLabel(Stat stat) {
        return Component.translatable("familytree.screen.sort." + key(stat)).getString();
    }

    public static Trend trend(Stat stat, AnimalRecord pet, Function<UUID, AnimalRecord> lookup) {
        AnimalRecord a = parent(pet.parentA(), lookup), b = parent(pet.parentB(), lookup);
        return HorseStatUnits.compare(stat, pet.horseStats(), a == null ? null : a.horseStats(), b == null ? null : b.horseStats());
    }

    public static String marker(Trend trend) {
        return switch (trend) {
            case BEATS_BOTH -> BEATS_MARKER;
            case TRAILS_BOTH -> TRAILS_MARKER;
            default -> "";
        };
    }

    public static int markerColor(Trend trend) {
        return trend == Trend.BEATS_BOTH ? BEATS_COLOR : TRAILS_COLOR;
    }

    private static String parentValues(Stat stat, AnimalRecord a, AnimalRecord b) {
        Double first = a == null ? null : stat.value(a.horseStats());
        Double second = b == null ? null : stat.value(b.horseStats());
        if (first == null && second == null) return "";
        return Component.translatable("familytree.stats.parents",
                first == null ? "?" : stat.format(first), second == null ? "?" : stat.format(second)).getString();
    }

    private static AnimalRecord parent(UUID id, Function<UUID, AnimalRecord> lookup) {
        return id == null ? null : lookup.apply(id);
    }

    private static String key(Stat stat) {
        return stat.name().toLowerCase(java.util.Locale.ROOT);
    }
}
