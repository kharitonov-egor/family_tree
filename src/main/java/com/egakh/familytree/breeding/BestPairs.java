package com.egakh.familytree.breeding;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.util.Kinship;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Ranks pairs by the expected value of a foal stat. Scores come from the exact mean, so ranking needs no sampling. */
public final class BestPairs {
    public enum Herd {
        HORSE(BreedingPair.HORSE), DONKEY(BreedingPair.DONKEY), MULE(BreedingPair.MULE), LLAMA(BreedingPair.LLAMA);

        public final String foalSpecies;

        Herd(String foalSpecies) {
            this.foalSpecies = foalSpecies;
        }

        public boolean member(String species) {
            return switch (this) {
                case HORSE -> BreedingPair.HORSE.equals(species);
                case DONKEY -> BreedingPair.DONKEY.equals(species);
                case MULE -> BreedingPair.HORSE.equals(species) || BreedingPair.DONKEY.equals(species);
                case LLAMA -> BreedingPair.llamaLike(species);
            };
        }

        public List<Rank> ranks() {
            return this == LLAMA ? List.of(Rank.OVERALL, Rank.STRENGTH, Rank.HEALTH)
                    : List.of(Rank.OVERALL, Rank.SPEED, Rank.JUMP, Rank.HEALTH);
        }
    }

    public enum Rank { OVERALL, SPEED, JUMP, HEALTH, STRENGTH }

    /** Close relatives give a foal an inbreeding coefficient of 1/8 or more: half siblings, parent and foal, and closer. */
    public enum Relatives { ALLOW, SKIP_CLOSE, SKIP_ALL }

    public enum Owners { ANY, SAME, MINE }

    public record Options(Herd herd, Rank rank, Relatives relatives, Owners owners, UUID viewer) {}

    public record Ranked(AnimalRecord first, AnimalRecord second, double score, double inbreeding) {}

    public record Outcome(List<Ranked> pairs, int candidates, long pairsConsidered, boolean trimmed) {}

    public static final double CLOSE = 1.0 / 8;
    public static final int MAX_CANDIDATES = 1500;

    private BestPairs() {}

    public static boolean usable(AnimalRecord pet, Options options) {
        if (pet == null || pet.deceased() || !options.herd().member(pet.speciesId())) return false;
        if (options.owners() == Owners.MINE && (options.viewer() == null || !options.viewer().equals(pet.ownerId()))) return false;
        return individual(pet, options.rank(), options.herd()) != null;
    }

    /** The herd's ranks that can score at least one pair. Every saved stat is optional, so a herd may lack some. */
    public static List<Rank> rankable(List<AnimalRecord> animals, Options options) {
        List<Rank> ranks = new ArrayList<>();
        for (Rank rank : options.herd().ranks()) {
            Options ranked = new Options(options.herd(), rank, options.relatives(), options.owners(), options.viewer());
            long members = 0, horses = 0;
            for (AnimalRecord pet : animals) {
                if (!usable(pet, ranked)) continue;
                members++;
                if (BreedingPair.HORSE.equals(pet.speciesId())) horses++;
            }
            if (options.herd() == Herd.MULE ? horses > 0 && members > horses : members >= 2) ranks.add(rank);
        }
        return ranks;
    }

    public static Outcome rank(List<AnimalRecord> animals, Options options, Kinship kinship, int limit) {
        List<AnimalRecord> candidates = new ArrayList<>();
        for (AnimalRecord pet : animals) if (usable(pet, options)) candidates.add(pet);
        boolean trimmed = candidates.size() > MAX_CANDIDATES;
        if (trimmed) {
            candidates.sort(Comparator.comparingDouble((AnimalRecord pet) -> individual(pet, options.rank(), options.herd())).reversed()
                    .thenComparing(AnimalRecord::id));
            candidates = new ArrayList<>(candidates.subList(0, MAX_CANDIDATES));
        } else candidates.sort(Comparator.comparing(AnimalRecord::id));

        int n = candidates.size();
        long[] packed = new long[n < 2 ? 0 : n * (n - 1) / 2];
        int count = 0;
        for (int i = 0; i < n; i++) {
            AnimalRecord a = candidates.get(i);
            for (int j = i + 1; j < n; j++) {
                AnimalRecord b = candidates.get(j);
                if (!pairs(a, b, options)) continue;
                double score = score(a.horseStats(), b.horseStats(), options.rank(), options.herd());
                if (Double.isNaN(score)) continue;
                packed[count++] = (long) Float.floatToIntBits((float) Math.max(0, score)) << 32 | (long) i * n + j;
            }
        }
        long[] used = Arrays.copyOf(packed, count);
        Arrays.sort(used);
        List<Ranked> result = new ArrayList<>();
        for (int k = used.length - 1; k >= 0 && result.size() < limit; k--) {
            long index = used[k] & 0xFFFFFFFFL;
            AnimalRecord a = candidates.get((int) (index / n)), b = candidates.get((int) (index % n));
            double inbreeding = kinship.foalInbreeding(a.id(), b.id());
            if (options.relatives() == Relatives.SKIP_CLOSE && inbreeding >= CLOSE - 1e-9) continue;
            if (options.relatives() == Relatives.SKIP_ALL && inbreeding > 1e-9) continue;
            result.add(new Ranked(a, b, score(a.horseStats(), b.horseStats(), options.rank(), options.herd()), inbreeding));
        }
        return new Outcome(result, n, count, trimmed);
    }

    private static boolean pairs(AnimalRecord a, AnimalRecord b, Options options) {
        if (options.herd() == Herd.MULE && a.speciesId().equals(b.speciesId())) return false;
        if (!BreedingPair.canBreed(a.speciesId(), b.speciesId())) return false;
        return options.owners() != Owners.SAME || a.ownerId() != null && Objects.equals(a.ownerId(), b.ownerId());
    }

    /** The expected foal value in Minecraft's units, or for {@link Rank#OVERALL} the mean share of each stat's range. */
    public static double score(HorseStats a, HorseStats b, Rank rank, Herd herd) {
        if (a == null || b == null) return Double.NaN;
        return switch (rank) {
            case SPEED -> expected(OffspringStats.Attribute.SPEED, a.speed(), b.speed());
            case JUMP -> expected(OffspringStats.Attribute.JUMP, a.jumpStrength(), b.jumpStrength());
            case HEALTH -> expected(OffspringStats.Attribute.HEALTH, a.maxHealth(), b.maxHealth());
            case STRENGTH -> a.strength() == null || b.strength() == null ? Double.NaN
                    : LlamaStrength.mean(LlamaStrength.distribution(a.strength(), b.strength()));
            case OVERALL -> herd == Herd.LLAMA
                    ? average(share(Rank.STRENGTH, score(a, b, Rank.STRENGTH, herd)), share(Rank.HEALTH, score(a, b, Rank.HEALTH, herd)))
                    : average(share(Rank.SPEED, score(a, b, Rank.SPEED, herd)), share(Rank.JUMP, score(a, b, Rank.JUMP, herd)),
                    share(Rank.HEALTH, score(a, b, Rank.HEALTH, herd)));
        };
    }

    /** One animal's own value on the same scale, used to trim very large herds before pairing. */
    static Double individual(AnimalRecord pet, Rank rank, Herd herd) {
        HorseStats stats = pet.horseStats();
        if (stats == null) return null;
        double value = score(stats, stats, rank, herd);
        return Double.isNaN(value) ? null : value;
    }

    private static double expected(OffspringStats.Attribute attribute, Double a, Double b) {
        if (a == null || b == null || !Double.isFinite(a) || !Double.isFinite(b)) return Double.NaN;
        return OffspringStats.forecast(attribute, a, b).mean();
    }

    private static double share(Rank rank, double value) {
        if (Double.isNaN(value)) return Double.NaN;
        return switch (rank) {
            case SPEED -> fraction(OffspringStats.Attribute.SPEED, value);
            case JUMP -> fraction(OffspringStats.Attribute.JUMP, value);
            case HEALTH -> fraction(OffspringStats.Attribute.HEALTH, value);
            case STRENGTH -> (value - LlamaStrength.MIN) / (LlamaStrength.MAX - LlamaStrength.MIN);
            case OVERALL -> value;
        };
    }

    private static double fraction(OffspringStats.Attribute attribute, double value) {
        return (value - attribute.min) / attribute.width();
    }

    private static double average(double... values) {
        double sum = 0;
        for (double value : values) {
            if (Double.isNaN(value)) return Double.NaN;
            sum += value;
        }
        return sum / values.length;
    }
}
