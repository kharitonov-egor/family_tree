package com.egakh.familytree.breeding;

import com.egakh.familytree.breeding.BestPairs.Herd;
import com.egakh.familytree.breeding.BestPairs.Options;
import com.egakh.familytree.breeding.BestPairs.Owners;
import com.egakh.familytree.breeding.BestPairs.Rank;
import com.egakh.familytree.breeding.BestPairs.Relatives;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.util.Kinship;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BestPairsTest {
    private static final UUID ME = UUID.randomUUID(), FRIEND = UUID.randomUUID();
    private final Map<UUID, AnimalRecord> records = new HashMap<>();

    private AnimalRecord animal(String species, String name, double speed, double jump, double health, Integer strength,
                                UUID owner, AnimalRecord a, AnimalRecord b) {
        AnimalRecord record = new AnimalRecord(UUID.randomUUID(), species, name, false,
                a == null ? null : a.id(), b == null ? null : b.id(), 0, 0, false, null, null, owner, "Owner", null, null, null);
        record.setHorseStats(new HorseStats(speed, jump, health, strength));
        records.put(record.id(), record);
        return record;
    }

    private BestPairs.Outcome rank(Herd herd, Rank rank, Relatives relatives, Owners owners) {
        return BestPairs.rank(new ArrayList<>(records.values()), new Options(herd, rank, relatives, owners, ME), new Kinship(records), 50);
    }

    private static Set<String> names(BestPairs.Ranked pair) {
        return Set.of(pair.first().name(), pair.second().name());
    }

    @Test
    void ranksByTheExpectedFoalValue() {
        animal("minecraft:horse", "Slow", 0.12, 0.5, 20.0, null, ME, null, null);
        animal("minecraft:horse", "Quick", 0.30, 0.5, 20.0, null, ME, null, null);
        animal("minecraft:horse", "Quicker", 0.32, 0.5, 20.0, null, ME, null, null);
        animal("minecraft:horse", "Medium", 0.22, 0.95, 29.0, null, ME, null, null);
        BestPairs.Outcome outcome = rank(Herd.HORSE, Rank.SPEED, Relatives.ALLOW, Owners.ANY);
        assertEquals(6, outcome.pairsConsidered());
        assertEquals(Set.of("Quick", "Quicker"), names(outcome.pairs().get(0)));
        for (int i = 1; i < outcome.pairs().size(); i++)
            assertTrue(outcome.pairs().get(i - 1).score() >= outcome.pairs().get(i).score());
        BestPairs.Ranked best = outcome.pairs().get(0);
        assertEquals(OffspringStats.forecast(OffspringStats.Attribute.SPEED, 0.30, 0.32).mean(), best.score(), 1e-12);
        BestPairs.Outcome overall = rank(Herd.HORSE, Rank.OVERALL, Relatives.ALLOW, Owners.ANY);
        assertTrue(names(overall.pairs().get(0)).contains("Medium"), "jump and health count toward the overall score");
    }

    @Test
    void relativesAndOwnersFilterPairs() {
        AnimalRecord sire = animal("minecraft:horse", "Sire", 0.2, 0.6, 22.0, null, ME, null, null);
        AnimalRecord dam = animal("minecraft:horse", "Dam", 0.2, 0.6, 22.0, null, ME, null, null);
        animal("minecraft:horse", "Brother", 0.33, 0.6, 22.0, null, ME, sire, dam);
        animal("minecraft:horse", "Sister", 0.33, 0.6, 22.0, null, ME, sire, dam);
        animal("minecraft:horse", "Neighbor", 0.30, 0.6, 22.0, null, FRIEND, null, null);
        assertEquals(Set.of("Brother", "Sister"), names(rank(Herd.HORSE, Rank.SPEED, Relatives.ALLOW, Owners.ANY).pairs().get(0)));
        BestPairs.Outcome close = rank(Herd.HORSE, Rank.SPEED, Relatives.SKIP_CLOSE, Owners.ANY);
        for (BestPairs.Ranked pair : close.pairs()) assertTrue(pair.inbreeding() < BestPairs.CLOSE, names(pair).toString());
        assertTrue(names(close.pairs().get(0)).contains("Neighbor"));
        assertTrue(rank(Herd.HORSE, Rank.SPEED, Relatives.SKIP_ALL, Owners.ANY).pairs().stream().allMatch(pair -> pair.inbreeding() == 0));
        assertTrue(rank(Herd.HORSE, Rank.SPEED, Relatives.ALLOW, Owners.SAME).pairs().stream()
                .noneMatch(pair -> names(pair).contains("Neighbor")));
        BestPairs.Outcome mine = rank(Herd.HORSE, Rank.SPEED, Relatives.ALLOW, Owners.MINE);
        assertEquals(4, mine.candidates());
    }

    @Test
    void mulesPairOnlyHorsesWithDonkeysAndDeadAnimalsStayOut() {
        animal("minecraft:horse", "H1", 0.3, 0.7, 25.0, null, ME, null, null);
        animal("minecraft:horse", "H2", 0.25, 0.7, 25.0, null, ME, null, null);
        animal("minecraft:donkey", "D1", 0.175, 0.5, 20.0, null, ME, null, null);
        AnimalRecord dead = animal("minecraft:donkey", "D2", 0.3, 0.5, 20.0, null, ME, null, null);
        dead.markDeceased(5, 5, null);
        BestPairs.Outcome mules = rank(Herd.MULE, Rank.SPEED, Relatives.ALLOW, Owners.ANY);
        assertEquals(2, mules.pairs().size());
        for (BestPairs.Ranked pair : mules.pairs()) assertNotEquals(pair.first().speciesId(), pair.second().speciesId());
        assertEquals(1, rank(Herd.HORSE, Rank.SPEED, Relatives.ALLOW, Owners.ANY).pairs().size());
        assertTrue(rank(Herd.DONKEY, Rank.SPEED, Relatives.ALLOW, Owners.ANY).pairs().isEmpty());
    }

    @Test
    void llamasRankByChestSlots() {
        animal("minecraft:llama", "Weak", 0.175, 0.5, 20.0, 1, ME, null, null);
        animal("minecraft:llama", "Strong", 0.175, 0.5, 20.0, 5, ME, null, null);
        animal("minecraft:trader_llama", "Trader", 0.175, 0.5, 20.0, 4, ME, null, null);
        BestPairs.Outcome outcome = rank(Herd.LLAMA, Rank.STRENGTH, Relatives.ALLOW, Owners.ANY);
        assertEquals(3, outcome.pairs().size());
        // Only the stronger parent's strength counts, so both pairs with Strong tie.
        assertTrue(names(outcome.pairs().get(0)).contains("Strong"));
        assertTrue(names(outcome.pairs().get(1)).contains("Strong"));
        assertEquals(outcome.pairs().get(0).score(), outcome.pairs().get(1).score(), 1e-12);
        assertEquals(LlamaStrength.mean(LlamaStrength.distribution(5, 1)), outcome.pairs().get(0).score(), 1e-12);
        assertEquals(Set.of("Weak", "Trader"), names(outcome.pairs().get(2)));
    }

    @Test
    void aFewHundredHorsesRankQuickly() {
        Random random = new Random(1);
        List<AnimalRecord> founders = new ArrayList<>();
        for (int i = 0; i < 60; i++) founders.add(animal("minecraft:horse", "F" + i, 0.1125 + random.nextDouble() * 0.225,
                0.4 + random.nextDouble() * 0.6, 15 + random.nextDouble() * 15, null, ME, null, null));
        List<AnimalRecord> herd = new ArrayList<>(founders);
        for (int i = 0; i < 340; i++) {
            AnimalRecord a = herd.get(random.nextInt(herd.size())), b = herd.get(random.nextInt(herd.size()));
            if (a == b) continue;
            herd.add(animal("minecraft:horse", "B" + i, 0.1125 + random.nextDouble() * 0.225,
                    0.4 + random.nextDouble() * 0.6, 15 + random.nextDouble() * 15, null, ME, a, b));
        }
        long start = System.nanoTime();
        BestPairs.Outcome outcome = rank(Herd.HORSE, Rank.OVERALL, Relatives.SKIP_CLOSE, Owners.MINE);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(50, outcome.pairs().size());
        assertTrue(outcome.candidates() >= 390);
        assertTrue(millis < 5000, "ranking took " + millis + " ms");
    }
}
