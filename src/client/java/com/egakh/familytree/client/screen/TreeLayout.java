package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TreeLayout {

    public static final int NODE_WIDTH = 140;
    public static final int NODE_HEIGHT = 64;
    public static final int H_SPACING = 24;
    public static final int V_SPACING = 60;

    public static final int MAX_ANCESTOR_GENERATIONS = 6;
    public static final int MAX_DESCENDANT_GENERATIONS = 6;

    public static final class Node {
        public final UUID id;
        public final AnimalRecord record;
        public final int generation;
        public double x;
        public double y;

        public Node(UUID id, AnimalRecord record, int generation) {
            this.id = id;
            this.record = record;
            this.generation = generation;
        }
    }

    public static final class Edge {
        public final Node from;
        public final Node to;
        public Edge(Node from, Node to) { this.from = from; this.to = to; }
    }

    public static final class Result {
        public final List<Node> nodes = new ArrayList<>();
        public final List<Edge> edges = new ArrayList<>();
        public final Map<UUID, Node> byId = new HashMap<>();
        public double minX, maxX, minY, maxY;
    }

    private TreeLayout() {}

    public static Result layout(UUID focusId,
                                Map<UUID, AnimalRecord> records,
                                Map<UUID, List<UUID>> childIndex) {
        Result r = new Result();
        AnimalRecord focusRecord = records.get(focusId);
        if (focusRecord == null) return r;

        Map<Integer, List<Node>> byGen = new HashMap<>();
        Set<UUID> seen = new HashSet<>();

        Node focusNode = new Node(focusId, focusRecord, 0);
        r.nodes.add(focusNode);
        r.byId.put(focusId, focusNode);
        byGen.computeIfAbsent(0, k -> new ArrayList<>()).add(focusNode);
        seen.add(focusId);

        // Walk ancestors up
        List<Node> currentLevel = List.of(focusNode);
        for (int gen = 1; gen <= MAX_ANCESTOR_GENERATIONS; gen++) {
            List<Node> next = new ArrayList<>();
            for (Node n : currentLevel) {
                addParent(n.record.parentA(), records, gen, r, byGen, seen, n, next, true);
                addParent(n.record.parentB(), records, gen, r, byGen, seen, n, next, true);
            }
            if (next.isEmpty()) break;
            currentLevel = next;
        }

        // Walk descendants down
        currentLevel = List.of(focusNode);
        for (int gen = 1; gen <= MAX_DESCENDANT_GENERATIONS; gen++) {
            List<Node> next = new ArrayList<>();
            for (Node parent : currentLevel) {
                List<UUID> children = childIndex.getOrDefault(parent.id, List.of());
                for (UUID childId : children) {
                    if (!seen.add(childId)) continue;
                    AnimalRecord rec = records.get(childId);
                    if (rec == null) continue;
                    Node child = new Node(childId, rec, -gen);
                    r.nodes.add(child);
                    r.byId.put(childId, child);
                    byGen.computeIfAbsent(-gen, k -> new ArrayList<>()).add(child);
                    r.edges.add(new Edge(parent, child));
                    next.add(child);
                }
            }
            if (next.isEmpty()) break;
            currentLevel = next;
        }

        // Layout: each generation row centered, spaced horizontally
        r.minX = Double.POSITIVE_INFINITY;
        r.maxX = Double.NEGATIVE_INFINITY;
        r.minY = Double.POSITIVE_INFINITY;
        r.maxY = Double.NEGATIVE_INFINITY;

        for (Map.Entry<Integer, List<Node>> e : byGen.entrySet()) {
            int gen = e.getKey();
            List<Node> row = e.getValue();
            int count = row.size();
            int rowWidth = count * NODE_WIDTH + Math.max(0, count - 1) * H_SPACING;
            double startX = -rowWidth / 2.0;
            double y = -gen * (NODE_HEIGHT + V_SPACING);
            for (int i = 0; i < count; i++) {
                Node n = row.get(i);
                n.x = startX + i * (NODE_WIDTH + H_SPACING);
                n.y = y;
                r.minX = Math.min(r.minX, n.x);
                r.maxX = Math.max(r.maxX, n.x + NODE_WIDTH);
                r.minY = Math.min(r.minY, n.y);
                r.maxY = Math.max(r.maxY, n.y + NODE_HEIGHT);
            }
        }
        if (r.nodes.isEmpty()) {
            r.minX = r.maxX = r.minY = r.maxY = 0;
        }
        return r;
    }

    private static void addParent(UUID parentId,
                                  Map<UUID, AnimalRecord> records,
                                  int gen,
                                  Result r,
                                  Map<Integer, List<Node>> byGen,
                                  Set<UUID> seen,
                                  Node child,
                                  List<Node> next,
                                  boolean ancestor) {
        if (parentId == null) return;
        AnimalRecord rec = records.get(parentId);
        if (rec == null) return;
        Node existing = r.byId.get(parentId);
        if (existing == null) {
            if (!seen.add(parentId)) return;
            existing = new Node(parentId, rec, gen);
            r.nodes.add(existing);
            r.byId.put(parentId, existing);
            byGen.computeIfAbsent(gen, k -> new ArrayList<>()).add(existing);
            next.add(existing);
        }
        r.edges.add(new Edge(existing, child));
    }
}
