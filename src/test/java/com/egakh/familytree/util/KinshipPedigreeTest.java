package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Coefficients against hand-computed values, asked of one shared {@link Kinship} in orders that reuse its cache.
 * Pure Java, so the Paper plugin runs the same file against its own copy of the record.
 */
class KinshipPedigreeTest {
    private final Map<UUID, AnimalRecord> records = new HashMap<>();
    private final List<AnimalRecord> created = new ArrayList<>();

    private AnimalRecord animal(String name, AnimalRecord a, AnimalRecord b) {
        return animal(name, a == null ? null : a.id(), b == null ? null : b.id());
    }

    private AnimalRecord animal(String name, UUID a, UUID b) {
        AnimalRecord record = new AnimalRecord(UUID.randomUUID(), "minecraft:horse", name, false,
                a, b, 0, 0, false, null, null, null, null, null, null, null);
        records.put(record.id(), record);
        created.add(record);
        return record;
    }

    private record Query(AnimalRecord first, AnimalRecord second, double expected, String label) {}

    /** Asks every query of fresh instances, then of one instance in forward, reverse, and shuffled orders. */
    private void assertInEveryOrder(List<Query> queries) {
        for (Query query : queries)
            assertEquals(query.expected(), new Kinship(records).coancestry(query.first().id(), query.second().id()), 1e-12,
                    query.label() + " on a fresh instance");
        List<List<Query>> orders = new ArrayList<>();
        orders.add(queries);
        List<Query> reversed = new ArrayList<>(queries);
        Collections.reverse(reversed);
        orders.add(reversed);
        for (long seed = 1; seed <= 8; seed++) {
            List<Query> shuffled = new ArrayList<>(queries);
            Collections.shuffle(shuffled, new Random(seed));
            orders.add(shuffled);
        }
        for (List<Query> order : orders) {
            Kinship shared = new Kinship(records);
            for (Query query : order) {
                assertEquals(query.expected(), shared.coancestry(query.first().id(), query.second().id()), 1e-12,
                        query.label() + " on a shared instance");
                assertEquals(query.expected(), shared.coancestry(query.second().id(), query.first().id()), 1e-12,
                        query.label() + " reversed on a shared instance");
            }
        }
    }

    @Test
    void fullSiblings() {
        AnimalRecord sire = animal("Sire", (UUID) null, null), dam = animal("Dam", (UUID) null, null);
        AnimalRecord a = animal("A", sire, dam), b = animal("B", sire, dam);
        AnimalRecord foal = animal("Foal", a, b);
        assertInEveryOrder(List.of(
                new Query(a, b, 0.25, "full siblings"),
                new Query(sire, a, 0.25, "parent and foal"),
                new Query(sire, dam, 0, "unrelated founders"),
                new Query(foal, foal, (1 + 0.25) / 2, "an inbred foal with itself"),
                new Query(sire, foal, (0.25 + 0.25) / 2, "grandparent and inbred grandchild"),
                new Query(a, foal, (0.5 + 0.25) / 2, "parent and inbred foal")));
    }

    @Test
    void halfSiblings() {
        AnimalRecord shared = animal("Shared", (UUID) null, null);
        AnimalRecord damA = animal("DamA", (UUID) null, null), damB = animal("DamB", (UUID) null, null);
        AnimalRecord a = animal("A", shared, damA), b = animal("B", shared, damB);
        AnimalRecord foal = animal("Foal", a, b), outsider = animal("Outsider", (UUID) null, null);
        AnimalRecord grandFoal = animal("GrandFoal", foal, outsider);
        assertInEveryOrder(List.of(
                new Query(a, b, 0.125, "half siblings"),
                new Query(damA, b, 0, "one sibling's dam and the other sibling"),
                new Query(foal, foal, (1 + 0.125) / 2, "the foal of half siblings with itself"),
                new Query(shared, foal, (0.25 + 0.25) / 2, "shared parent and the foal of its two foals"),
                new Query(shared, grandFoal, 0.125, "shared parent and its great-grandfoal")));
    }

    @Test
    void doubleFirstCousins() {
        AnimalRecord g1 = animal("G1", (UUID) null, null), g2 = animal("G2", (UUID) null, null);
        AnimalRecord g3 = animal("G3", (UUID) null, null), g4 = animal("G4", (UUID) null, null);
        AnimalRecord p1 = animal("P1", g1, g2), p2 = animal("P2", g1, g2);
        AnimalRecord q1 = animal("Q1", g3, g4), q2 = animal("Q2", g3, g4);
        AnimalRecord a = animal("A", p1, q1), b = animal("B", p2, q2);
        AnimalRecord foal = animal("Foal", a, b);
        assertInEveryOrder(List.of(
                new Query(a, b, 0.125, "double first cousins"),
                new Query(p1, b, 0.125, "aunt and niece through one side only"),
                new Query(p1, q1, 0, "the cousins' unrelated parents"),
                new Query(g1, foal, 0.125, "great-grandparent of an inbred line"),
                new Query(foal, foal, (1 + 0.125) / 2, "the foal of double first cousins with itself")));
    }

    /** One sire mated to his daughter, granddaughter, and great-granddaughter: F = 1/4, 3/8, 7/16. */
    @Test
    void threeGenerationsOfBackcrossesToTheSameSire() {
        AnimalRecord sire = animal("Sire", (UUID) null, null), dam = animal("Dam", (UUID) null, null);
        AnimalRecord g1 = animal("G1", sire, dam), g2 = animal("G2", sire, g1);
        AnimalRecord g3 = animal("G3", sire, g2), g4 = animal("G4", sire, g3);
        assertInEveryOrder(List.of(
                new Query(sire, g1, 0.25, "sire and daughter"),
                new Query(sire, g2, 0.375, "sire and the foal he had with his daughter"),
                new Query(sire, g3, 0.4375, "sire and the third backcross"),
                new Query(g2, g2, (1 + 0.25) / 2, "second generation with itself"),
                new Query(g3, g3, (1 + 0.375) / 2, "third generation with itself"),
                new Query(g4, g4, (1 + 0.4375) / 2, "fourth generation with itself"),
                new Query(g1, g2, (0.25 + 0.5) / 2, "daughter and her inbred foal"),
                new Query(dam, g3, 0.0625, "the founding dam and the third generation")));
        Kinship kinship = new Kinship(records);
        assertEquals(0.4375, kinship.inbreeding(g4.id()), 1e-12);
        assertEquals(0.375, kinship.inbreeding(g3.id()), 1e-12);
        assertEquals(0.25, kinship.inbreeding(g2.id()), 1e-12);
    }

    /** Each foal mated to its younger parent: F = 1/4, 3/8, 1/2, the same series as full-sibling mating. */
    @Test
    void threeGenerationsOfFoalsMatedToTheirYoungerParent() {
        AnimalRecord sire = animal("Sire", (UUID) null, null), dam = animal("Dam", (UUID) null, null);
        AnimalRecord g1 = animal("G1", sire, dam), g2 = animal("G2", sire, g1);
        AnimalRecord g3 = animal("G3", g1, g2), g4 = animal("G4", g2, g3);
        assertInEveryOrder(List.of(
                new Query(sire, g1, 0.25, "sire and daughter"),
                new Query(g1, g2, 0.375, "G1 and its foal by the sire"),
                new Query(g2, g3, 0.5, "G2 and its foal by G1"),
                new Query(g3, g4, 0.59375, "G3 and its foal by G2"),
                new Query(sire, g4, (0.375 + 0.3125) / 2, "the founding sire and G4")));
        Kinship kinship = new Kinship(records);
        assertEquals(0.5, kinship.inbreeding(g4.id()), 1e-12);
        assertEquals(0.375, kinship.inbreeding(g3.id()), 1e-12);
        assertEquals(0.25, kinship.inbreeding(g2.id()), 1e-12);
    }

    /** Random pedigrees with heavy inbreeding and unlisted parents, against the tabular method in birth order. */
    @Test
    void sharedInstanceMatchesTheTabularMethodOnRandomPedigrees() {
        for (long seed = 1; seed <= 20; seed++) {
            records.clear();
            created.clear();
            Random random = new Random(seed);
            List<UUID> unlisted = List.of(UUID.randomUUID(), UUID.randomUUID());
            List<UUID> order = new ArrayList<>(unlisted);
            for (int i = 0; i < 40; i++) {
                UUID a = pickParent(random, order), b = pickParent(random, order);
                if (a != null && a.equals(b)) b = null;
                order.add(animal("R" + i, a, b).id());
            }
            double[][] table = tabular(order);
            List<int[]> pairs = new ArrayList<>();
            for (int i = 0; i < order.size(); i++) for (int j = 0; j <= i; j++) pairs.add(new int[]{i, j});
            Collections.shuffle(pairs, random);
            Kinship shared = new Kinship(records);
            for (int[] pair : pairs)
                assertEquals(table[pair[0]][pair[1]], shared.coancestry(order.get(pair[0]), order.get(pair[1])), 1e-12,
                        "seed " + seed + " pair " + pair[0] + "," + pair[1]);
        }
    }

    private static UUID pickParent(Random random, List<UUID> earlier) {
        if (random.nextInt(5) == 0) return null;
        int recent = Math.min(earlier.size(), 8);
        return earlier.get(earlier.size() - 1 - random.nextInt(recent));
    }

    /** The textbook tabular method: every animal after its parents, unlisted parents as unrelated founders. */
    private double[][] tabular(List<UUID> order) {
        int n = order.size();
        Map<UUID, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) index.put(order.get(i), i);
        double[][] f = new double[n][n];
        for (int i = 0; i < n; i++) {
            AnimalRecord record = records.get(order.get(i));
            Integer a = record == null || record.parentA() == null ? null : index.get(record.parentA());
            Integer b = record == null || record.parentB() == null ? null : index.get(record.parentB());
            for (int j = 0; j < i; j++) {
                double value = ((a == null ? 0 : f[a][j]) + (b == null ? 0 : f[b][j])) / 2;
                f[i][j] = value;
                f[j][i] = value;
            }
            f[i][i] = (1 + (a == null || b == null ? 0 : f[a][b])) / 2;
        }
        return f;
    }
}
