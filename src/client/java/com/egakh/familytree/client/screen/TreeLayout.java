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

    public static final int NODE_WIDTH = 220;
    public static final int NODE_HEIGHT = 92;
    public static final int H_SPACING = 32;
    public static final int V_SPACING = 72;

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

    public static Result layoutForest(List<UUID> includedIds,
                                      Map<UUID, AnimalRecord> records,
                                      Map<UUID, List<UUID>> childIndex) {
        Result result = new Result();
        if (includedIds.isEmpty()) return result;

        Set<UUID> included = new HashSet<>(includedIds);
        Map<UUID, List<UUID>> undirected = new HashMap<>();
        for (UUID id : included) {
            undirected.put(id, new ArrayList<>());
        }
        for (UUID id : included) {
            AnimalRecord record = records.get(id);
            if (record == null) continue;
            if (record.parentA() != null && included.contains(record.parentA())) {
                undirected.get(id).add(record.parentA());
                undirected.get(record.parentA()).add(id);
            }
            if (record.parentB() != null && included.contains(record.parentB())) {
                undirected.get(id).add(record.parentB());
                undirected.get(record.parentB()).add(id);
            }
            for (UUID childId : childIndex.getOrDefault(id, List.of())) {
                if (included.contains(childId)) {
                    undirected.get(id).add(childId);
                    undirected.get(childId).add(id);
                }
            }
        }

        Set<UUID> visited = new HashSet<>();
        List<List<UUID>> components = new ArrayList<>();
        for (UUID id : included) {
            if (!visited.add(id)) continue;
            List<UUID> component = new ArrayList<>();
            List<UUID> queue = new ArrayList<>();
            queue.add(id);
            for (int i = 0; i < queue.size(); i++) {
                UUID current = queue.get(i);
                component.add(current);
                for (UUID neighbor : undirected.getOrDefault(current, List.of())) {
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            components.add(component);
        }

        double xOffset = 0.0;
        double globalMinX = Double.POSITIVE_INFINITY;
        double globalMaxX = Double.NEGATIVE_INFINITY;
        double globalMinY = Double.POSITIVE_INFINITY;
        double globalMaxY = Double.NEGATIVE_INFINITY;

        for (List<UUID> component : components) {
            Result componentLayout = layoutForestComponent(component, records, childIndex);
            double componentWidth = componentLayout.nodes.isEmpty() ? 0.0 : componentLayout.maxX - componentLayout.minX;
            for (Node node : componentLayout.nodes) {
                node.x += xOffset - componentLayout.minX;
                result.nodes.add(node);
                result.byId.put(node.id, node);
                globalMinX = Math.min(globalMinX, node.x);
                globalMaxX = Math.max(globalMaxX, node.x + NODE_WIDTH);
                globalMinY = Math.min(globalMinY, node.y);
                globalMaxY = Math.max(globalMaxY, node.y + NODE_HEIGHT);
            }
            result.edges.addAll(componentLayout.edges);
            xOffset += componentWidth + 96.0;
        }

        if (result.nodes.isEmpty()) {
            result.minX = result.maxX = result.minY = result.maxY = 0.0;
        } else {
            result.minX = globalMinX;
            result.maxX = globalMaxX;
            result.minY = globalMinY;
            result.maxY = globalMaxY;
        }

        return result;
    }

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

    private static Result layoutForestComponent(List<UUID> componentIds,
                                                Map<UUID, AnimalRecord> records,
                                                Map<UUID, List<UUID>> childIndex) {
        Result result = new Result();
        Set<UUID> included = new HashSet<>(componentIds);
        Map<UUID, Integer> generationById = new HashMap<>();
        Map<Integer, List<Node>> byGen = new HashMap<>();
        Map<UUID, Integer> remainingParents = new HashMap<>();
        List<UUID> queue = new ArrayList<>();

        for (UUID id : componentIds) {
            AnimalRecord record = records.get(id);
            if (record == null) continue;
            int parentCount = 0;
            if (record.parentA() != null && included.contains(record.parentA())) parentCount++;
            if (record.parentB() != null && included.contains(record.parentB())) parentCount++;
            remainingParents.put(id, parentCount);
            if (parentCount == 0) {
                queue.add(id);
                generationById.put(id, 0);
            }
        }
        if (queue.isEmpty() && !componentIds.isEmpty()) {
            UUID fallback = componentIds.getFirst();
            queue.add(fallback);
            generationById.put(fallback, 0);
        }

        for (int i = 0; i < queue.size(); i++) {
            UUID parentId = queue.get(i);
            int parentGen = generationById.getOrDefault(parentId, 0);
            AnimalRecord parentRecord = records.get(parentId);
            if (parentRecord == null) continue;

            Node parentNode = result.byId.computeIfAbsent(parentId, id -> {
                Node node = new Node(id, parentRecord, parentGen);
                result.nodes.add(node);
                byGen.computeIfAbsent(parentGen, k -> new ArrayList<>()).add(node);
                return node;
            });

            for (UUID childId : childIndex.getOrDefault(parentId, List.of())) {
                if (!included.contains(childId)) continue;
                AnimalRecord childRecord = records.get(childId);
                if (childRecord == null) continue;
                int childGen = Math.max(generationById.getOrDefault(childId, 0), parentGen + 1);
                generationById.put(childId, childGen);
                remainingParents.computeIfPresent(childId, (id, count) -> Math.max(0, count - 1));
                if (remainingParents.getOrDefault(childId, 0) == 0 && !queue.contains(childId)) {
                    queue.add(childId);
                }

                Node childNode = result.byId.get(childId);
                if (childNode == null) {
                    childNode = new Node(childId, childRecord, childGen);
                    result.nodes.add(childNode);
                    result.byId.put(childId, childNode);
                    byGen.computeIfAbsent(childGen, k -> new ArrayList<>()).add(childNode);
                }
                result.edges.add(new Edge(parentNode, childNode));
            }
        }

        result.minX = Double.POSITIVE_INFINITY;
        result.maxX = Double.NEGATIVE_INFINITY;
        result.minY = Double.POSITIVE_INFINITY;
        result.maxY = Double.NEGATIVE_INFINITY;

        for (Map.Entry<Integer, List<Node>> entry : byGen.entrySet()) {
            int gen = entry.getKey();
            List<Node> row = entry.getValue();
            int count = row.size();
            int rowWidth = count * NODE_WIDTH + Math.max(0, count - 1) * H_SPACING;
            double startX = -rowWidth / 2.0;
            double y = gen * (NODE_HEIGHT + V_SPACING);
            for (int i = 0; i < count; i++) {
                Node node = row.get(i);
                node.x = startX + i * (NODE_WIDTH + H_SPACING);
                node.y = y;
                result.minX = Math.min(result.minX, node.x);
                result.maxX = Math.max(result.maxX, node.x + NODE_WIDTH);
                result.minY = Math.min(result.minY, node.y);
                result.maxY = Math.max(result.maxY, node.y + NODE_HEIGHT);
            }
        }

        if (result.nodes.isEmpty()) {
            result.minX = result.maxX = result.minY = result.maxY = 0.0;
        }

        return result;
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
