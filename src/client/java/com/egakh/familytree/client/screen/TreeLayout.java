package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;

import java.util.ArrayList;
import java.util.Comparator;
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
    private static final int FOREST_EXTRA_SPACING = 56;

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
        Map<UUID, List<UUID>> parentIds = new HashMap<>();
        Map<UUID, Integer> remainingParents = new HashMap<>();
        List<UUID> queue = new ArrayList<>();

        for (UUID id : componentIds) {
            AnimalRecord record = records.get(id);
            if (record == null) continue;
            List<UUID> parents = new ArrayList<>(2);
            int parentCount = 0;
            if (record.parentA() != null && included.contains(record.parentA())) {
                parentCount++;
                parents.add(record.parentA());
            }
            if (record.parentB() != null && included.contains(record.parentB())) {
                parentCount++;
                parents.add(record.parentB());
            }
            parentIds.put(id, parents);
            remainingParents.put(id, parentCount);
            if (parentCount == 0) {
                queue.add(id);
                generationById.put(id, 0);
            }
        }
        if (queue.isEmpty() && !componentIds.isEmpty()) {
            UUID fallback = componentIds.get(0);
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

        for (UUID id : componentIds) {
            if (result.byId.containsKey(id)) continue;
            AnimalRecord record = records.get(id);
            if (record == null) continue;
            int generation = generationById.getOrDefault(id, 0);
            Node node = new Node(id, record, generation);
            result.nodes.add(node);
            result.byId.put(id, node);
            byGen.computeIfAbsent(generation, k -> new ArrayList<>()).add(node);
        }

        int maxGeneration = byGen.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (List<Node> row : byGen.values()) {
            row.sort(Comparator.comparing(node -> node.record.name(), String.CASE_INSENSITIVE_ORDER));
        }
        for (int pass = 0; pass < 3; pass++) {
            for (int generation = 1; generation <= maxGeneration; generation++) {
                List<Node> row = byGen.get(generation);
                if (row == null) continue;
                Map<UUID, Integer> parentOrder = indexById(byGen.get(generation - 1));
                row.sort(Comparator.comparingDouble(node ->
                        barycenterForParents(node.id, parentIds, parentOrder, row)));
            }
            for (int generation = maxGeneration - 1; generation >= 0; generation--) {
                List<Node> row = byGen.get(generation);
                if (row == null) continue;
                Map<UUID, Integer> childOrder = indexById(byGen.get(generation + 1));
                row.sort(Comparator.comparingDouble(node ->
                        barycenterForChildren(node.id, childIndex, included, generationById, childOrder, row)));
            }
        }

        result.minX = Double.POSITIVE_INFINITY;
        result.maxX = Double.NEGATIVE_INFINITY;
        result.minY = Double.POSITIVE_INFINITY;
        result.maxY = Double.NEGATIVE_INFINITY;

        for (int generation = 0; generation <= maxGeneration; generation++) {
            List<Node> row = byGen.get(generation);
            if (row == null || row.isEmpty()) continue;

            double rowGap = H_SPACING + FOREST_EXTRA_SPACING;
            double y = generation * (NODE_HEIGHT + V_SPACING);
            List<Double> desiredCenters = new ArrayList<>(row.size());
            for (int i = 0; i < row.size(); i++) {
                Node node = row.get(i);
                desiredCenters.add(desiredCenter(node, i, rowGap, parentIds, childIndex, included, generationById, result.byId));
            }

            List<Placement> placements = new ArrayList<>(row.size());
            for (int i = 0; i < row.size(); i++) {
                placements.add(new Placement(row.get(i), desiredCenters.get(i)));
            }
            placements.sort(Comparator.comparingDouble(placement -> placement.desiredCenter));

            double cursor = Double.NEGATIVE_INFINITY;
            double minLeft = Double.POSITIVE_INFINITY;
            double maxRight = Double.NEGATIVE_INFINITY;
            for (Placement placement : placements) {
                double targetLeft = placement.desiredCenter - NODE_WIDTH / 2.0;
                double left = Math.max(targetLeft, cursor);
                placement.node.x = left;
                placement.node.y = y;
                cursor = left + NODE_WIDTH + rowGap;
                minLeft = Math.min(minLeft, left);
                maxRight = Math.max(maxRight, left + NODE_WIDTH);
            }

            double desiredMid = placements.stream().mapToDouble(placement -> placement.desiredCenter).average().orElse(0.0);
            double actualMid = (minLeft + maxRight) / 2.0;
            double shift = desiredMid - actualMid;
            for (Placement placement : placements) {
                placement.node.x += shift;
                result.minX = Math.min(result.minX, placement.node.x);
                result.maxX = Math.max(result.maxX, placement.node.x + NODE_WIDTH);
                result.minY = Math.min(result.minY, placement.node.y);
                result.maxY = Math.max(result.maxY, placement.node.y + NODE_HEIGHT);
            }
        }

        if (result.nodes.isEmpty()) {
            result.minX = result.maxX = result.minY = result.maxY = 0.0;
        }

        return result;
    }

    private static Map<UUID, Integer> indexById(List<Node> row) {
        Map<UUID, Integer> order = new HashMap<>();
        if (row == null) return order;
        for (int i = 0; i < row.size(); i++) {
            order.put(row.get(i).id, i);
        }
        return order;
    }

    private static double barycenterForParents(UUID nodeId,
                                               Map<UUID, List<UUID>> parentIds,
                                               Map<UUID, Integer> parentOrder,
                                               List<Node> currentRow) {
        List<UUID> parents = parentIds.getOrDefault(nodeId, List.of());
        double sum = 0.0;
        int count = 0;
        for (UUID parentId : parents) {
            Integer index = parentOrder.get(parentId);
            if (index != null) {
                sum += index;
                count++;
            }
        }
        if (count == 0) {
            return fallbackIndex(nodeId, currentRow);
        }
        return sum / count;
    }

    private static double barycenterForChildren(UUID nodeId,
                                                Map<UUID, List<UUID>> childIndex,
                                                Set<UUID> included,
                                                Map<UUID, Integer> generationById,
                                                Map<UUID, Integer> childOrder,
                                                List<Node> currentRow) {
        double sum = 0.0;
        int count = 0;
        int currentGeneration = generationById.getOrDefault(nodeId, 0);
        for (UUID childId : childIndex.getOrDefault(nodeId, List.of())) {
            if (!included.contains(childId)) continue;
            if (generationById.getOrDefault(childId, currentGeneration + 1) != currentGeneration + 1) continue;
            Integer index = childOrder.get(childId);
            if (index != null) {
                sum += index;
                count++;
            }
        }
        if (count == 0) {
            return fallbackIndex(nodeId, currentRow);
        }
        return sum / count;
    }

    private static int fallbackIndex(UUID nodeId, List<Node> row) {
        for (int i = 0; i < row.size(); i++) {
            if (row.get(i).id.equals(nodeId)) return i;
        }
        return 0;
    }

    private static double desiredCenter(Node node,
                                        int fallbackIndex,
                                        double rowGap,
                                        Map<UUID, List<UUID>> parentIds,
                                        Map<UUID, List<UUID>> childIndex,
                                        Set<UUID> included,
                                        Map<UUID, Integer> generationById,
                                        Map<UUID, Node> allNodes) {
        List<UUID> parents = parentIds.getOrDefault(node.id, List.of());
        double parentSum = 0.0;
        int parentCount = 0;
        for (UUID parentId : parents) {
            Node parent = allNodes.get(parentId);
            if (parent != null) {
                parentSum += parent.x + NODE_WIDTH / 2.0;
                parentCount++;
            }
        }
        if (parentCount > 0) {
            return parentSum / parentCount;
        }

        double childSum = 0.0;
        int childCount = 0;
        int currentGeneration = generationById.getOrDefault(node.id, 0);
        for (UUID childId : childIndex.getOrDefault(node.id, List.of())) {
            if (!included.contains(childId)) continue;
            if (generationById.getOrDefault(childId, currentGeneration + 1) != currentGeneration + 1) continue;
            Node child = allNodes.get(childId);
            if (child != null && child.x != 0.0) {
                childSum += child.x + NODE_WIDTH / 2.0;
                childCount++;
            }
        }
        if (childCount > 0) {
            return childSum / childCount;
        }

        return fallbackIndex * (NODE_WIDTH + rowGap);
    }

    private record Placement(Node node, double desiredCenter) {}

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
