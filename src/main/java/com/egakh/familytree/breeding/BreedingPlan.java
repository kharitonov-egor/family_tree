package com.egakh.familytree.breeding;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.util.HorseStatUnits;
import com.egakh.familytree.util.HorseStatUnits.Stat;
import com.egakh.familytree.util.Kinship;

import java.util.ArrayList;
import java.util.List;

/** Everything the planner shows for one pair, computed from records only. */
public final class BreedingPlan {
    public static final int BARS = 24;

    /**
     * One foal stat in player units. Chest slots stand in for llama strength. {@code likelyLow} and
     * {@code likelyHigh} differ only when several llama strengths tie as most likely. {@code bars} split the
     * possible values into equal slices, and {@code betterParentAt} is the better parent's place along them, from
     * 0 to 1, so the screen can mark the values that beat both parents.
     */
    public record StatForecast(Stat stat, double lowest, double likelyLow, double likelyHigh, double highest,
                               double mean, double chanceAboveBoth, double[] bars, double betterParentAt,
                               double parentA, double parentB) {}

    public final AnimalRecord first;
    public final AnimalRecord second;
    public final boolean canBreed;
    public final List<String> foalSpecies;
    public final Kinship.Relation relation;
    public final double inbreeding;
    public final List<StatForecast> stats;
    /** Parents whose stats Family Tree hasn't read yet, for species that inherit stats. */
    public final List<AnimalRecord> missingStats;
    public final boolean fixedStats;
    public final VariantOdds.Result variants;

    public BreedingPlan(AnimalRecord first, AnimalRecord second, Kinship kinship) {
        this.first = first;
        this.second = second;
        this.canBreed = first != null && second != null && !first.id().equals(second.id())
                && BreedingPair.canBreed(first.speciesId(), second.speciesId());
        this.foalSpecies = canBreed ? BreedingPair.foalSpecies(first.speciesId(), second.speciesId()) : List.of();
        this.relation = first == null || second == null ? null : kinship.relation(first.id(), second.id());
        this.inbreeding = first == null || second == null ? 0 : kinship.foalInbreeding(first.id(), second.id());
        List<StatForecast> forecasts = new ArrayList<>();
        List<AnimalRecord> missing = new ArrayList<>();
        boolean inherits = canBreed && foalSpecies.stream().allMatch(BreedingPair::inheritsStats);
        if (inherits) {
            if (first.horseStats() == null) missing.add(first);
            if (second.horseStats() == null) missing.add(second);
            if (missing.isEmpty()) forecasts = forecasts(first.horseStats(), second.horseStats(),
                    foalSpecies.stream().allMatch(BreedingPair::llamaLike));
        }
        this.stats = List.copyOf(forecasts);
        this.missingStats = List.copyOf(missing);
        this.fixedStats = canBreed && foalSpecies.contains(BreedingPair.CAMEL);
        this.variants = canBreed && foalSpecies.size() >= 1 && !BreedingPair.MULE.equals(foalSpecies.get(0))
                ? VariantOdds.forPair(first.speciesId(), second.speciesId(), first.variantId(), second.variantId())
                : new VariantOdds.Result(List.of(), VariantOdds.Note.NONE);
    }

    /** Llamas list chest slots first, since strength is what llama breeders select for. */
    public static List<StatForecast> forecasts(HorseStats a, HorseStats b, boolean llama) {
        List<StatForecast> list = new ArrayList<>();
        if (llama) addStrength(list, a, b);
        add(list, Stat.SPEED, OffspringStats.Attribute.SPEED, a, b);
        add(list, Stat.JUMP, OffspringStats.Attribute.JUMP, a, b);
        add(list, Stat.HEALTH, OffspringStats.Attribute.HEALTH, a, b);
        return list;
    }

    private static void add(List<StatForecast> list, Stat stat, OffspringStats.Attribute attribute, HorseStats a, HorseStats b) {
        Double first = stat.base(a), second = stat.base(b);
        if (first == null || second == null) return;
        OffspringStats.Forecast forecast = OffspringStats.forecast(attribute, first, second);
        double likely = stat.toPlayerUnits(forecast.mostLikely());
        double span = forecast.highest - forecast.lowest;
        double betterAt = span <= 0 ? 1 : (forecast.betterParent() - forecast.lowest) / span;
        list.add(new StatForecast(stat, stat.toPlayerUnits(forecast.lowest), likely, likely,
                stat.toPlayerUnits(forecast.highest), stat.toPlayerUnits(forecast.mean()), forecast.chanceAboveBoth(),
                forecast.histogram(BARS), betterAt, stat.toPlayerUnits(forecast.parentA), stat.toPlayerUnits(forecast.parentB)));
    }

    private static void addStrength(List<StatForecast> list, HorseStats a, HorseStats b) {
        if (a.strength() == null || b.strength() == null) return;
        double[] chance = LlamaStrength.distribution(a.strength(), b.strength());
        int lowest = LlamaStrength.lowest(chance), highest = LlamaStrength.highest(chance);
        int[] likely = LlamaStrength.mostLikely(chance);
        double[] bars = new double[highest - lowest + 1];
        for (int k = lowest; k <= highest; k++) bars[k - lowest] = chance[k];
        int better = Math.max(a.strength(), b.strength());
        double betterAt = (better - lowest + 1) / (double) bars.length;
        list.add(new StatForecast(Stat.STRENGTH, slots(lowest), slots(likely[0]), slots(likely[1]), slots(highest),
                3 * LlamaStrength.mean(chance), LlamaStrength.chanceAbove(chance, better),
                bars, betterAt, slots(a.strength()), slots(b.strength())));
    }

    private static double slots(int strength) {
        return HorseStatUnits.chestSlots(strength);
    }
}
