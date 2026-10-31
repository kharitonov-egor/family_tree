package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Relationships and kinship coefficients from the recorded parent links. A parent ID without a record still
 * counts as an animal with unknown parents, so two foals of an unlisted parent are still siblings. Like
 * {@link Genealogy}, everything walks explicit stacks and tolerates cycles in damaged data.
 */
public final class Kinship {
    public enum Kind { SAME, ANCESTOR, DESCENDANT, SIBLING, AUNT_OR_UNCLE, NIECE_OR_NEPHEW, COUSIN, UNRELATED }

    /** Whether two siblings or cousins share both linking parents, one, or Family Tree can't tell. */
    public enum Half { FULL, HALF, UNKNOWN }

    /**
     * How {@code first} relates to {@code second} through their nearest shared ancestor: {@code up} generations
     * from {@code first} to it and {@code down} from {@code second}. An ancestor has {@code up == 0}.
     * Cousins have degree {@code min(up, down) - 1} and are removed {@code |up - down|} times.
     */
    public record Relation(Kind kind, int up, int down, Half half, List<UUID> commonAncestors) {
        public int cousinDegree() {
            return Math.min(up, down) - 1;
        }

        public int removed() {
            return Math.abs(up - down);
        }

        /** Generations between the closer animal and the shared ancestor's line, for "great" prefixes. */
        public int generations() {
            return Math.max(up, down);
        }

        static Relation unrelated() {
            return new Relation(Kind.UNRELATED, -1, -1, Half.UNKNOWN, List.of());
        }
    }

    private final Map<UUID, AnimalRecord> records;
    private final Map<UUID, Integer> generations;
    private final Map<Long, Double> memo = new HashMap<>();
    private final Map<UUID, Long> indices = new HashMap<>();

    public Kinship(Map<UUID, AnimalRecord> records) {
        this.records = records;
        this.generations = Genealogy.computeGenerations(records);
    }

    public Relation relation(UUID first, UUID second) {
        if (first == null || second == null) return Relation.unrelated();
        if (first.equals(second)) return new Relation(Kind.SAME, 0, 0, Half.UNKNOWN, List.of(first));
        Ancestry a = ancestry(first), b = ancestry(second);
        int bestUp = -1, bestDown = -1;
        for (var entry : a.distance.entrySet()) {
            Integer down = b.distance.get(entry.getKey());
            if (down == null) continue;
            int up = entry.getValue();
            if (bestUp < 0 || better(up, down, bestUp, bestDown)) {
                bestUp = up;
                bestDown = down;
            }
        }
        if (bestUp < 0) return Relation.unrelated();
        List<UUID> common = new ArrayList<>();
        for (var entry : a.distance.entrySet())
            if (entry.getValue() == bestUp && Integer.valueOf(bestDown).equals(b.distance.get(entry.getKey()))) common.add(entry.getKey());
        common.sort(Comparator.naturalOrder());
        Kind kind;
        if (bestUp == 0) kind = Kind.ANCESTOR;
        else if (bestDown == 0) kind = Kind.DESCENDANT;
        else if (bestUp == 1 && bestDown == 1) kind = Kind.SIBLING;
        else if (bestUp == 1) kind = Kind.AUNT_OR_UNCLE;
        else if (bestDown == 1) kind = Kind.NIECE_OR_NEPHEW;
        else kind = Kind.COUSIN;
        Half half = kind == Kind.ANCESTOR || kind == Kind.DESCENDANT ? Half.UNKNOWN : half(common, a, b);
        return new Relation(kind, bestUp, bestDown, half, List.copyOf(common));
    }

    /** Closest by total steps, then the most even split, so a parent link wins over a longer route. */
    private static boolean better(int up, int down, int bestUp, int bestDown) {
        int total = up + down, bestTotal = bestUp + bestDown;
        if (total != bestTotal) return total < bestTotal;
        return Math.abs(up - down) < Math.abs(bestUp - bestDown);
    }

    /**
     * Two shared ancestors at the nearest level make full relatives. With one, the relatives are half only when
     * both animals on the linking step have two known parents; otherwise the second parent might also be shared.
     */
    private Half half(List<UUID> common, Ancestry a, Ancestry b) {
        if (common.size() >= 2) return Half.FULL;
        UUID ancestor = common.get(0);
        AnimalRecord linkA = records.get(a.via.get(ancestor)), linkB = records.get(b.via.get(ancestor));
        if (linkA == null || linkB == null) return Half.UNKNOWN;
        boolean known = linkA.parentA() != null && linkA.parentB() != null && linkB.parentA() != null && linkB.parentB() != null;
        return known ? Half.HALF : Half.UNKNOWN;
    }

    /** Animals on the shortest routes from each animal up to their nearest shared ancestors. */
    public Set<UUID> path(UUID first, UUID second) {
        Relation relation = relation(first, second);
        Set<UUID> path = new LinkedHashSet<>();
        if (relation.kind() == Kind.UNRELATED) return path;
        Ancestry a = ancestry(first), b = ancestry(second);
        for (UUID ancestor : relation.commonAncestors()) {
            walk(a, first, ancestor, path);
            walk(b, second, ancestor, path);
        }
        return path;
    }

    private static void walk(Ancestry ancestry, UUID start, UUID ancestor, Set<UUID> path) {
        UUID current = ancestor;
        path.add(current);
        for (int guard = 0; current != null && !current.equals(start) && guard < 10_000; guard++) {
            current = ancestry.via.get(current);
            if (current != null) path.add(current);
        }
    }

    /** The inbreeding coefficient of a foal from these two parents, which equals their coefficient of coancestry. */
    public double foalInbreeding(UUID parentA, UUID parentB) {
        return coancestry(parentA, parentB);
    }

    public double inbreeding(UUID animal) {
        AnimalRecord record = records.get(animal);
        return record == null ? 0 : coancestry(record.parentA(), record.parentB());
    }

    /**
     * The chance that a gene picked at random from each animal is the same copy by descent. An animal with
     * itself has (1 + F) / 2. For two animals, the one of the later generation can't be an ancestor of the
     * other, so its parents stand in for it.
     */
    public double coancestry(UUID first, UUID second) {
        if (first == null || second == null) return 0;
        long root = pair(first, second);
        Double known = lookup(root);
        if (known != null) return known;
        ArrayDeque<Long> pending = new ArrayDeque<>();
        Set<Long> active = new HashSet<>();
        pending.push(root);
        active.add(root);
        while (!pending.isEmpty()) {
            long key = pending.peek();
            if (lookup(key) != null) {
                pending.pop();
                active.remove(key);
                continue;
            }
            long[] parts = dependencies(key);
            boolean ready = true;
            for (long part : parts) {
                if (part == NONE || lookup(part) != null || active.contains(part)) continue;
                pending.push(part);
                active.add(part);
                ready = false;
            }
            if (!ready) continue;
            store(key, combine(key, parts));
            pending.pop();
            active.remove(key);
        }
        Double value = lookup(root);
        return value == null ? 0 : value;
    }

    private static final long NONE = Long.MIN_VALUE;

    private long[] dependencies(long key) {
        UUID x = animal(high(key)), y = animal(low(key));
        if (x.equals(y)) {
            AnimalRecord record = records.get(x);
            if (record == null || record.parentA() == null || record.parentB() == null) return new long[0];
            return new long[]{pair(record.parentA(), record.parentB())};
        }
        UUID expand = later(x, y), keep = expand.equals(x) ? y : x;
        AnimalRecord record = records.get(expand);
        if (record == null) return new long[0];
        return new long[]{record.parentA() == null ? NONE : pair(keep, record.parentA()),
                record.parentB() == null ? NONE : pair(keep, record.parentB())};
    }

    /** Pairs still being worked out count as zero, which only happens when damaged data has a cycle. */
    private double combine(long key, long[] parts) {
        UUID x = animal(high(key)), y = animal(low(key));
        if (x.equals(y)) return (1 + (parts.length == 0 ? 0 : valueOrZero(parts[0]))) / 2;
        double sum = 0;
        for (long part : parts) if (part != NONE) sum += valueOrZero(part);
        return sum / 2;
    }

    private double valueOrZero(long key) {
        Double value = lookup(key);
        return value == null ? 0 : value;
    }

    private UUID later(UUID x, UUID y) {
        int gx = generation(x), gy = generation(y);
        if (gx != gy) return gx > gy ? x : y;
        return x.compareTo(y) > 0 ? x : y;
    }

    private int generation(UUID id) {
        return generations.getOrDefault(id, 0);
    }

    private record Ancestry(Map<UUID, Integer> distance, Map<UUID, UUID> via) {}

    /** Breadth-first, so each ancestor keeps its shortest distance and the child that leads back to the start. */
    private Ancestry ancestry(UUID start) {
        Map<UUID, Integer> distance = new HashMap<>();
        Map<UUID, UUID> via = new HashMap<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        distance.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            AnimalRecord record = records.get(id);
            if (record == null) continue;
            for (UUID parent : new UUID[]{record.parentA(), record.parentB()}) {
                if (parent == null || distance.containsKey(parent)) continue;
                distance.put(parent, distance.get(id) + 1);
                via.put(parent, id);
                queue.add(parent);
            }
        }
        return new Ancestry(distance, via);
    }

    private final List<UUID> animals = new ArrayList<>();

    private long index(UUID id) {
        Long known = indices.get(id);
        if (known != null) return known;
        long next = animals.size();
        animals.add(id);
        indices.put(id, next);
        return next;
    }

    private UUID animal(long index) {
        return animals.get((int) index);
    }

    private long pair(UUID first, UUID second) {
        long a = index(first), b = index(second);
        return a >= b ? a << 32 | b : b << 32 | a;
    }

    private static long high(long key) {
        return key >>> 32;
    }

    private static long low(long key) {
        return key & 0xFFFFFFFFL;
    }

    private Double lookup(long key) {
        return memo.get(key);
    }

    private void store(long key, double value) {
        memo.put(key, value);
    }
}
