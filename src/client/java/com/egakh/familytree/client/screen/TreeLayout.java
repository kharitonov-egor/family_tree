package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TreeLayout {

    public static final int NODE_WIDTH = 250;
    public static final int NODE_HEIGHT = 110;
    public static final int H_SPACING = 32;
    public static final int V_SPACING = 72;
    /** Rows of one wrapped generation sit closer together than generations do. */
    public static final int WRAP_GAP = 36;
    private static final int FOREST_EXTRA_SPACING = 56;
    private static final double FOREST_GAP = 96.0;
    private static final double MAX_SINGLE_ROW_WIDTH = 24 * (NODE_WIDTH + H_SPACING);
    /** A generation with more cards than this wraps into several rows. */
    static final int TREE_ROW_LIMIT = 10;
    static final int FOREST_ROW_LIMIT = 24;

    public static final int MAX_ANCESTOR_GENERATIONS = 6;
    public static final int MAX_DESCENDANT_GENERATIONS = 6;

    public static final class Node {
        public final UUID id;
        public final AnimalRecord record;
        public final int generation;
        /** Shown only as the other parent of a descendant, so the foal never looks like it had one parent. */
        public final boolean partner;
        public double x;
        public double y;
        private int row;
        private Band band;

        public Node(UUID id, AnimalRecord record, int generation) {
            this(id, record, generation, false);
        }

        Node(UUID id, AnimalRecord record, int generation, boolean partner) {
            this.id = id;
            this.record = record;
            this.generation = generation;
            this.partner = partner;
        }
    }

    public static final class Edge {
        public final Node from;
        public final Node to;
        private double[] route;

        public Edge(Node from, Node to) { this.from = from; this.to = to; }

        /**
         * Corner points as x, y pairs in layout coordinates, from the bottom of the parent's card to the top of the child's.
         * Each segment is horizontal or vertical. Links into a wrapped generation run down its left side, and links out of one
         * down its right side, so no link passes under a card.
         */
        public double[] route() {
            if (route != null) return route;
            double px = from.x + NODE_WIDTH / 2.0, py = from.y + (from.band == null ? NODE_HEIGHT : from.band.cardHeight);
            double cx = to.x + NODE_WIDTH / 2.0, cy = to.y;
            double middle = (py + cy) / 2;
            return new double[]{px, py, px, middle, cx, middle, cx, cy};
        }
    }

    public static final class Result {
        public final List<Node> nodes = new ArrayList<>();
        public final List<Edge> edges = new ArrayList<>();
        public final Map<UUID, Node> byId = new HashMap<>();
        /** Cards whose ancestors go further back than the tree shows, with how many more generations there are. */
        public final Map<UUID, Integer> olderHidden = new HashMap<>();
        /** Cards whose descendants go further down than the tree shows, with how many more generations there are. */
        public final Map<UUID, Integer> youngerHidden = new HashMap<>();
        public double minX, maxX, minY, maxY;
        /** Rows are spaced by this height, which the screen sizes to the cards' content. */
        public int cardHeight = NODE_HEIGHT;
    }

    private static final class Band {
        final List<Node> nodes = new ArrayList<>();
        int index;
        int rows = 1;
        double top;
        double bottom;
        double left;
        double right;
        double gap;
        double cardHeight = NODE_HEIGHT;
    }

    private record Link(UUID from, UUID to) {}

    private TreeLayout() {}

    /**
     * Collapsing a branch hides all its descendants, including descendants with another parent, and partners left with no
     * shown child. A collapsed card itself always stays, so its branch can be shown again.
     */
    public static List<UUID> visibleIds(Result tree, Set<UUID> collapsed) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (Edge edge : tree.edges) {
            children.computeIfAbsent(edge.from.id, key -> new ArrayList<>()).add(edge.to.id);
        }
        Set<UUID> hidden = new HashSet<>();
        for (UUID root : collapsed) {
            Set<UUID> visited = new HashSet<>();
            visited.add(root);
            ArrayDeque<UUID> pending = new ArrayDeque<>(children.getOrDefault(root, List.of()));
            while (!pending.isEmpty()) {
                UUID id = pending.removeLast();
                if (!visited.add(id)) continue;
                hidden.add(id);
                pending.addAll(children.getOrDefault(id, List.of()));
            }
        }
        for (Node node : tree.nodes) {
            List<UUID> shown = children.getOrDefault(node.id, List.of());
            if (node.partner && !collapsed.contains(node.id) && !shown.isEmpty() && hidden.containsAll(shown)) hidden.add(node.id);
        }
        return tree.nodes.stream().map(node -> node.id).filter(id -> !hidden.contains(id)).toList();
    }

    /** Separate families, largest first, then by the earliest name in each. */
    public static List<List<UUID>> families(Collection<UUID> ids,
                                            Map<UUID, AnimalRecord> records,
                                            Map<UUID, List<UUID>> childIndex) {
        Set<UUID> included = new HashSet<>();
        for (UUID id : ids) if (records.containsKey(id)) included.add(id);
        Map<UUID, List<UUID>> neighbours = new HashMap<>();
        for (UUID id : included) {
            List<UUID> links = new ArrayList<>();
            for (UUID parent : parents(records.get(id))) if (included.contains(parent)) links.add(parent);
            for (UUID child : childIndex.getOrDefault(id, List.of())) if (included.contains(child)) links.add(child);
            neighbours.put(id, links);
        }
        List<UUID> ordered = new ArrayList<>(included);
        ordered.sort(Comparator.comparing((UUID id) -> records.get(id).name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(UUID::toString));
        Set<UUID> visited = new HashSet<>();
        List<List<UUID>> families = new ArrayList<>();
        for (UUID start : ordered) {
            if (!visited.add(start)) continue;
            List<UUID> family = new ArrayList<>();
            family.add(start);
            for (int i = 0; i < family.size(); i++) {
                for (UUID next : neighbours.get(family.get(i))) {
                    if (visited.add(next)) family.add(next);
                }
            }
            families.add(family);
        }
        families.sort(Comparator.comparingInt((List<UUID> family) -> -family.size()));
        return families;
    }

    /** Fills each page with whole families up to {@code limit} cards. A family larger than that gets a page of its own. */
    public static List<List<UUID>> pages(List<List<UUID>> families, int limit) {
        List<List<UUID>> pages = new ArrayList<>();
        List<UUID> page = new ArrayList<>();
        for (List<UUID> family : families) {
            if (!page.isEmpty() && page.size() + family.size() > limit) {
                pages.add(page);
                page = new ArrayList<>();
            }
            page.addAll(family);
        }
        if (!page.isEmpty()) pages.add(page);
        return pages;
    }

    public static Result layoutForest(List<UUID> includedIds,
                                      Map<UUID, AnimalRecord> records,
                                      Map<UUID, List<UUID>> childIndex) {
        return layoutForest(includedIds, records, childIndex, NODE_HEIGHT);
    }

    public static Result layoutForest(List<UUID> includedIds,
                                      Map<UUID, AnimalRecord> records,
                                      Map<UUID, List<UUID>> childIndex,
                                      int cardHeight) {
        Result result = new Result();
        result.cardHeight = cardHeight;
        if (includedIds.isEmpty()) return result;

        List<Result> layouts = new ArrayList<>();
        double rowWidth = -FOREST_GAP, widest = 0, area = 0;
        for (List<UUID> component : families(includedIds, records, childIndex)) {
            Result componentLayout = layoutForestComponent(component, records, childIndex, cardHeight);
            if (componentLayout.nodes.isEmpty()) continue;
            layouts.add(componentLayout);
            double width = componentLayout.maxX - componentLayout.minX, height = componentLayout.maxY - componentLayout.minY;
            rowWidth += width + FOREST_GAP;
            widest = Math.max(widest, width);
            area += (width + FOREST_GAP) * (height + FOREST_GAP);
        }
        // A farm can hold hundreds of separate families. Past a few dozen cards, wrap them into rows about as wide as a screen.
        double maxRowWidth = rowWidth <= MAX_SINGLE_ROW_WIDTH ? rowWidth : Math.max(widest, Math.sqrt(area) * 1.6);
        double xOffset = 0.0, yOffset = 0.0, rowHeight = 0.0;

        for (Result componentLayout : layouts) {
            double componentWidth = componentLayout.maxX - componentLayout.minX;
            if (xOffset > 0 && xOffset + componentWidth > maxRowWidth) {
                xOffset = 0.0;
                yOffset += rowHeight + FOREST_GAP;
                rowHeight = 0.0;
            }
            rowHeight = Math.max(rowHeight, componentLayout.maxY - componentLayout.minY);
            double dx = xOffset - componentLayout.minX, dy = yOffset - componentLayout.minY;
            for (Node node : componentLayout.nodes) {
                node.x += dx;
                node.y += dy;
                result.nodes.add(node);
                result.byId.put(node.id, node);
            }
            for (Edge edge : componentLayout.edges) {
                for (int i = 0; i < edge.route.length; i += 2) {
                    edge.route[i] += dx;
                    edge.route[i + 1] += dy;
                }
                result.edges.add(edge);
            }
            xOffset += componentWidth + FOREST_GAP;
        }
        measure(result);
        return result;
    }

    public static Result layout(UUID focusId,
                                Map<UUID, AnimalRecord> records,
                                Map<UUID, List<UUID>> childIndex) {
        return layout(focusId, records, childIndex, Set.of(), Set.of(), Set.of());
    }

    /**
     * One animal's ancestors above it and descendants below, six generations each way. The generation limit restarts at
     * cards in {@code olderShown} or {@code youngerShown}, and cards in {@code hidden} are left out.
     */
    public static Result layout(UUID focusId,
                                Map<UUID, AnimalRecord> records,
                                Map<UUID, List<UUID>> childIndex,
                                Set<UUID> hidden,
                                Set<UUID> olderShown,
                                Set<UUID> youngerShown) {
        return layout(focusId, records, childIndex, hidden, olderShown, youngerShown, NODE_HEIGHT);
    }

    public static Result layout(UUID focusId,
                                Map<UUID, AnimalRecord> records,
                                Map<UUID, List<UUID>> childIndex,
                                Set<UUID> hidden,
                                Set<UUID> olderShown,
                                Set<UUID> youngerShown,
                                int cardHeight) {
        Result r = new Result();
        r.cardHeight = cardHeight;
        AnimalRecord focusRecord = records.get(focusId);
        if (focusRecord == null) return r;

        Map<Integer, Band> bands = new HashMap<>();
        Set<Link> linked = new HashSet<>();
        Node focusNode = add(r, bands, new Node(focusId, focusRecord, 0));

        Map<UUID, Integer> generationsLeft = new HashMap<>();
        generationsLeft.put(focusId, MAX_ANCESTOR_GENERATIONS);
        Map<UUID, Integer> heights = ancestorHeights(focusId, records);
        List<Link> laterLinks = new ArrayList<>();
        Map<UUID, Integer> laterLeft = new HashMap<>();
        List<Node> limitedUp = new ArrayList<>();
        List<Node> level = List.of(focusNode);
        while (!level.isEmpty()) {
            List<Node> next = new ArrayList<>();
            for (Node child : level) {
                int left = generationsLeft.get(child.id);
                if (left == 0) {
                    if (!olderShown.contains(child.id)) {
                        limitedUp.add(child);
                        continue;
                    }
                    left = MAX_ANCESTOR_GENERATIONS;
                }
                for (UUID parentId : parents(child.record)) {
                    AnimalRecord parent = records.get(parentId);
                    if (parent == null) continue;
                    Node node = r.byId.get(parentId);
                    if (node == null) {
                        // An ancestor that is also a further ancestor waits for its older line, so its links all run downward.
                        if (heights.getOrDefault(parentId, 0) > child.generation + 1) {
                            laterLinks.add(new Link(parentId, child.id));
                            laterLeft.merge(parentId, left - 1, Math::max);
                            continue;
                        }
                        node = add(r, bands, new Node(parentId, parent, child.generation + 1));
                        generationsLeft.put(parentId, left - 1);
                        next.add(node);
                    }
                    link(r, linked, node, child);
                }
            }
            if (next.isEmpty()) {
                // A parent whose older line runs past the generation limit sits just above the children shown instead.
                Map<UUID, Integer> cut = new LinkedHashMap<>();
                for (Link later : laterLinks) {
                    if (!r.byId.containsKey(later.from())) cut.merge(later.from(), r.byId.get(later.to()).generation + 1, Math::max);
                }
                Set<UUID> waitingAbove = new HashSet<>();
                for (UUID id : cut.keySet()) waitingAbove.addAll(parents(records.get(id)));
                for (Map.Entry<UUID, Integer> entry : cut.entrySet()) {
                    if (waitingAbove.contains(entry.getKey())) continue;
                    next.add(add(r, bands, new Node(entry.getKey(), records.get(entry.getKey()), entry.getValue())));
                    generationsLeft.put(entry.getKey(), laterLeft.get(entry.getKey()));
                }
            }
            level = next;
        }
        for (Link later : laterLinks) {
            Node parent = r.byId.get(later.from());
            if (parent != null) link(r, linked, parent, r.byId.get(later.to()));
        }

        generationsLeft.clear();
        generationsLeft.put(focusId, MAX_DESCENDANT_GENERATIONS);
        Map<UUID, Integer> depths = descendantDepths(focusId, records, childIndex);
        Map<UUID, Node> reachedFrom = new LinkedHashMap<>();
        List<Node> limitedDown = new ArrayList<>();
        level = List.of(focusNode);
        while (!level.isEmpty()) {
            List<Node> next = new ArrayList<>();
            for (Node parent : level) {
                int left = generationsLeft.get(parent.id);
                if (left == 0) {
                    if (!youngerShown.contains(parent.id)) {
                        limitedDown.add(parent);
                        continue;
                    }
                    left = MAX_DESCENDANT_GENERATIONS;
                }
                for (UUID childId : childIndex.getOrDefault(parent.id, List.of())) {
                    AnimalRecord child = records.get(childId);
                    if (child == null) continue;
                    Node node = r.byId.get(childId);
                    if (node == null) {
                        // A foal whose other parent is a younger descendant waits for that parent, so it sits below both.
                        if (depths.getOrDefault(childId, 0) > 1 - parent.generation) continue;
                        node = add(r, bands, new Node(childId, child, parent.generation - 1));
                        generationsLeft.put(childId, left - 1);
                        reachedFrom.put(childId, parent);
                        next.add(node);
                    }
                    link(r, linked, parent, node);
                }
            }
            level = next;
        }

        Map<UUID, Node> partnerOf = new LinkedHashMap<>();
        for (Map.Entry<UUID, Node> entry : reachedFrom.entrySet()) {
            Node child = r.byId.get(entry.getKey());
            Node shownParent = entry.getValue();
            for (UUID parentId : parents(child.record)) {
                if (parentId.equals(shownParent.id)) continue;
                AnimalRecord parent = records.get(parentId);
                if (parent == null) continue;
                Node node = r.byId.get(parentId);
                if (node == null) {
                    node = new Node(parentId, parent, shownParent.generation, true);
                    List<Node> row = bands.get(shownParent.generation).nodes;
                    int index = row.indexOf(shownParent) + 1;
                    while (index < row.size() && partnerOf.get(row.get(index).id) == shownParent) index++;
                    row.add(index, node);
                    partnerOf.put(parentId, shownParent);
                    r.nodes.add(node);
                    r.byId.put(parentId, node);
                }
                link(r, linked, node, child);
            }
        }
        // A partner whose parents are already drawn links to them, so a sibling bred to the focus reads as a sibling.
        for (UUID partnerId : partnerOf.keySet()) {
            Node partner = r.byId.get(partnerId);
            for (UUID parentId : parents(partner.record)) {
                Node parent = r.byId.get(parentId);
                if (parent != null && parent.generation > partner.generation) link(r, linked, parent, partner);
            }
        }

        for (Node node : limitedUp) {
            boolean more = false;
            for (UUID parentId : parents(node.record)) {
                if (!records.containsKey(parentId)) continue;
                Node shown = r.byId.get(parentId);
                if (shown == null) more = true;
                else link(r, linked, shown, node);
            }
            if (more) r.olderHidden.put(node.id, generationsAbove(node.id, records, r.byId.keySet()));
        }
        for (Node node : limitedDown) {
            boolean more = false;
            for (UUID childId : childIndex.getOrDefault(node.id, List.of())) {
                if (!records.containsKey(childId)) continue;
                Node shown = r.byId.get(childId);
                if (shown == null) more = true;
                else link(r, linked, node, shown);
            }
            if (more) r.youngerHidden.put(node.id, generationsBelow(node.id, records, childIndex, r.byId.keySet()));
        }

        if (!hidden.isEmpty()) removeHidden(r, bands, hidden);
        List<Band> placed = new ArrayList<>();

        List<Integer> order = new ArrayList<>(bands.keySet());
        order.sort(Comparator.reverseOrder());
        double top = 0;
        int index = 0;
        for (int generation : order) {
            Band band = bands.get(generation);
            if (band.nodes.isEmpty()) continue;
            band.index = index++;
            placed.add(band);
            band.gap = H_SPACING;
            band.cardHeight = cardHeight;
            if (band.nodes.size() > TREE_ROW_LIMIT) {
                placeGrid(band, top, 0);
            } else {
                double width = band.nodes.size() * NODE_WIDTH + (band.nodes.size() - 1) * H_SPACING;
                placeRow(band, top);
                for (int i = 0; i < band.nodes.size(); i++) band.nodes.get(i).x = -width / 2 + i * (NODE_WIDTH + H_SPACING);
                measureBand(band);
            }
            top = band.bottom + V_SPACING;
        }
        route(r.edges, placed);
        measure(r);
        return r;
    }

    private static Node add(Result r, Map<Integer, Band> bands, Node node) {
        r.nodes.add(node);
        r.byId.put(node.id, node);
        bands.computeIfAbsent(node.generation, key -> new Band()).nodes.add(node);
        return node;
    }

    private static void link(Result r, Set<Link> linked, Node parent, Node child) {
        if (linked.add(new Link(parent.id, child.id))) r.edges.add(new Edge(parent, child));
    }

    private static void removeHidden(Result r, Map<Integer, Band> bands, Set<UUID> hidden) {
        Set<UUID> dropped = new HashSet<>();
        for (Node node : r.nodes) if (hidden.contains(node.id)) dropped.add(node.id);
        r.nodes.removeIf(node -> dropped.contains(node.id));
        r.edges.removeIf(edge -> dropped.contains(edge.from.id) || dropped.contains(edge.to.id));
        for (UUID id : dropped) {
            r.byId.remove(id);
            r.olderHidden.remove(id);
            r.youngerHidden.remove(id);
        }
        for (Band band : bands.values()) band.nodes.removeIf(node -> dropped.contains(node.id));
    }

    private static List<UUID> parents(AnimalRecord record) {
        List<UUID> parents = new ArrayList<>(2);
        if (record == null) return parents;
        if (record.parentA() != null) parents.add(record.parentA());
        if (record.parentB() != null && !record.parentB().equals(record.parentA())) parents.add(record.parentB());
        return parents;
    }

    /**
     * Generations above the focus along each ancestor's longest line, so an animal that is both a parent and a
     * grandparent sits above both of the children it links to. Animals on a loop in the records, and their ancestors,
     * get no height.
     */
    static Map<UUID, Integer> ancestorHeights(UUID focusId, Map<UUID, AnimalRecord> records) {
        Set<UUID> reachable = new HashSet<>();
        ArrayDeque<UUID> pending = new ArrayDeque<>(List.of(focusId));
        while (!pending.isEmpty()) {
            for (UUID parent : parents(records.get(pending.removeLast()))) {
                if (records.containsKey(parent) && reachable.add(parent)) pending.add(parent);
            }
        }
        reachable.remove(focusId);
        Map<UUID, Integer> waiting = new HashMap<>();
        for (UUID id : reachable) waiting.put(id, 0);
        for (UUID id : reachable) for (UUID parent : parents(records.get(id))) waiting.computeIfPresent(parent, (key, count) -> count + 1);
        for (UUID parent : parents(records.get(focusId))) waiting.computeIfPresent(parent, (key, count) -> count + 1);
        Map<UUID, Integer> heights = new HashMap<>();
        heights.put(focusId, 0);
        ArrayDeque<UUID> ready = new ArrayDeque<>(List.of(focusId));
        while (!ready.isEmpty()) {
            UUID child = ready.removeFirst();
            for (UUID parent : parents(records.get(child))) {
                Integer left = waiting.get(parent);
                if (left == null) continue;
                heights.merge(parent, heights.get(child) + 1, Math::max);
                waiting.put(parent, left - 1);
                if (left == 1) ready.add(parent);
            }
        }
        heights.keySet().removeIf(id -> waiting.getOrDefault(id, 0) > 0);
        return heights;
    }

    /**
     * Generations below the focus along each descendant's longest line, so a foal bred back to an older relative sits
     * below its younger parent. Animals on a loop in the records, and their descendants, get no depth.
     */
    static Map<UUID, Integer> descendantDepths(UUID focusId, Map<UUID, AnimalRecord> records, Map<UUID, List<UUID>> childIndex) {
        Set<UUID> reachable = new HashSet<>();
        ArrayDeque<UUID> pending = new ArrayDeque<>(List.of(focusId));
        while (!pending.isEmpty()) {
            for (UUID child : childIndex.getOrDefault(pending.removeLast(), List.of())) {
                if (records.containsKey(child) && reachable.add(child)) pending.add(child);
            }
        }
        reachable.remove(focusId);
        Map<UUID, Integer> waiting = new HashMap<>();
        for (UUID id : reachable) {
            int count = 0;
            for (UUID parent : parents(records.get(id))) if (parent.equals(focusId) || reachable.contains(parent)) count++;
            waiting.put(id, count);
        }
        Map<UUID, Integer> depths = new HashMap<>();
        depths.put(focusId, 0);
        ArrayDeque<UUID> ready = new ArrayDeque<>(List.of(focusId));
        while (!ready.isEmpty()) {
            UUID parent = ready.removeFirst();
            for (UUID child : new LinkedHashSet<>(childIndex.getOrDefault(parent, List.of()))) {
                Integer left = waiting.get(child);
                if (left == null) continue;
                depths.merge(child, depths.get(parent) + 1, Math::max);
                waiting.put(child, left - 1);
                if (left == 1) ready.add(child);
            }
        }
        depths.keySet().removeIf(id -> waiting.getOrDefault(id, 0) > 0);
        return depths;
    }

    /** How many generations of ancestors the tree leaves out above a card, counting each at its nearest distance. */
    static int generationsAbove(UUID id, Map<UUID, AnimalRecord> records, Set<UUID> shown) {
        Set<UUID> seen = new HashSet<>(shown);
        seen.add(id);
        List<UUID> level = List.of(id);
        int count = 0;
        while (true) {
            List<UUID> next = new ArrayList<>();
            for (UUID child : level) {
                for (UUID parent : parents(records.get(child))) {
                    if (records.containsKey(parent) && seen.add(parent)) next.add(parent);
                }
            }
            if (next.isEmpty()) return count;
            count++;
            level = next;
        }
    }

    static int generationsBelow(UUID id, Map<UUID, AnimalRecord> records, Map<UUID, List<UUID>> childIndex, Set<UUID> shown) {
        Set<UUID> seen = new HashSet<>(shown);
        seen.add(id);
        List<UUID> level = List.of(id);
        int count = 0;
        while (true) {
            List<UUID> next = new ArrayList<>();
            for (UUID parent : level) {
                for (UUID child : childIndex.getOrDefault(parent, List.of())) {
                    if (records.containsKey(child) && seen.add(child)) next.add(child);
                }
            }
            if (next.isEmpty()) return count;
            count++;
            level = next;
        }
    }

    private static void placeRow(Band band, double top) {
        band.rows = 1;
        for (Node node : band.nodes) {
            node.y = top;
            node.row = 0;
            node.band = band;
        }
        band.top = top;
        band.bottom = top + band.cardHeight;
    }

    /** Wraps a wide generation into balanced rows centered on {@code center}, roughly 1.6 times as wide as tall. */
    private static void placeGrid(Band band, double top, double center) {
        int count = band.nodes.size();
        int columns = Math.max(6, (int) Math.ceil(Math.sqrt(count * 1.6)));
        int rows = (count + columns - 1) / columns;
        columns = (count + rows - 1) / rows;
        double pitch = NODE_WIDTH + band.gap;
        double gridWidth = columns * pitch - band.gap;
        double left = center - gridWidth / 2;
        band.rows = rows;
        band.top = top;
        for (int i = 0; i < count; i++) {
            Node node = band.nodes.get(i);
            int row = i / columns, column = i % columns;
            int inRow = Math.min(columns, count - row * columns);
            node.row = row;
            node.band = band;
            node.x = left + (columns - inRow) * pitch / 2 + column * pitch;
            node.y = top + row * (band.cardHeight + WRAP_GAP);
        }
        band.bottom = top + rows * band.cardHeight + (rows - 1) * WRAP_GAP;
        measureBand(band);
    }

    private static void measureBand(Band band) {
        band.left = Double.POSITIVE_INFINITY;
        band.right = Double.NEGATIVE_INFINITY;
        for (Node node : band.nodes) {
            band.left = Math.min(band.left, node.x);
            band.right = Math.max(band.right, node.x + NODE_WIDTH);
        }
    }

    /**
     * {@code bands} runs top to bottom. A link that skips generations turns off above its parent's bus, runs down its
     * own lane outside its parent's row and every band down to the child, and joins the child's line below the bus, so
     * it never runs along another parent's line.
     */
    private static void route(List<Edge> edges, List<Band> bands) {
        List<double[]> lanes = new ArrayList<>(), rails = new ArrayList<>();
        List<Edge> skipping = new ArrayList<>();
        for (Edge edge : edges) {
            Node parent = edge.from, child = edge.to;
            Band from = parent.band, to = child.band;
            if (from == null || to == null || to.top <= from.bottom) continue;
            if (to.index > from.index + 1) {
                skipping.add(edge);
                continue;
            }
            double cx = child.x + NODE_WIDTH / 2.0, exit = from.bottom + V_SPACING / 2.0;
            List<Double> points = new ArrayList<>();
            double x = leave(parent, points, lanes, exit);
            if (child.row == 0) {
                points.addAll(List.of(x, exit, cx, exit, cx, child.y));
            } else {
                double above = child.y - WRAP_GAP / 3.0, lane = to.left - to.gap / 2;
                lanes.add(new double[]{lane, exit, above});
                points.addAll(List.of(x, exit, lane, exit, lane, above, cx, above, cx, child.y));
            }
            edge.route = simplify(points);
        }
        for (Edge edge : skipping) {
            Node parent = edge.from, child = edge.to;
            Band from = parent.band, to = child.band;
            double cx = child.x + NODE_WIDTH / 2.0, turn = from.bottom + V_SPACING / 4.0;
            List<Double> points = new ArrayList<>();
            double x = leave(parent, points, null, turn);
            double left = from.left - from.gap / 2, right = from.right + from.gap / 2;
            for (int i = from.index + 1; i <= to.index; i++) {
                left = Math.min(left, bands.get(i).left - bands.get(i).gap / 2);
                right = Math.max(right, bands.get(i).right + bands.get(i).gap / 2);
            }
            double entry = child.row == 0 ? to.top - V_SPACING / 4.0 : child.y - WRAP_GAP / 3.0;
            double step = x - left <= right - x ? -from.gap / 2 : from.gap / 2;
            double lane = step < 0 ? left : right;
            while (laneTaken(lanes, lane, turn, entry)) lane += step;
            lanes.add(new double[]{lane, turn, entry});
            turn = freeRail(rails, turn, x, lane);
            if (child.row == 0) entry = freeRail(rails, entry, lane, cx);
            points.addAll(List.of(x, turn, lane, turn, lane, entry, cx, entry, cx, child.y));
            edge.route = simplify(points);
        }
    }

    /** Starts a route at the bottom of the parent's card. A parent above the last row of a wrapped band leaves by its right side. */
    private static double leave(Node parent, List<Double> points, List<double[]> lanes, double down) {
        Band from = parent.band;
        double px = parent.x + NODE_WIDTH / 2.0;
        points.addAll(List.of(px, parent.y + from.cardHeight));
        if (parent.row == from.rows - 1) return px;
        double below = parent.y + from.cardHeight + WRAP_GAP / 3.0, x = from.right + from.gap / 2;
        points.addAll(List.of(px, below, x, below));
        if (lanes != null) lanes.add(new double[]{x, below, down});
        return x;
    }

    /** A height near {@code y} where no other skipping link already runs across this span, kept clear of the buses. */
    private static double freeRail(List<double[]> rails, double y, double x1, double x2) {
        double from = Math.min(x1, x2), to = Math.max(x1, x2);
        for (double offset : new double[]{0, -4, 4, -8, 8, -12, 12, -16, 16}) {
            double candidate = y + offset;
            boolean taken = false;
            for (double[] rail : rails) taken |= Math.abs(rail[0] - candidate) < 1 && rail[1] < to && from < rail[2];
            if (!taken) {
                rails.add(new double[]{candidate, from, to});
                return candidate;
            }
        }
        return y;
    }

    private static boolean laneTaken(List<double[]> lanes, double x, double top, double bottom) {
        for (double[] lane : lanes) if (Math.abs(lane[0] - x) < 1 && lane[1] < bottom && top < lane[2]) return true;
        return false;
    }

    private static double[] simplify(List<Double> points) {
        List<Double> kept = new ArrayList<>();
        for (int i = 0; i < points.size(); i += 2) {
            double x = points.get(i), y = points.get(i + 1);
            int n = kept.size();
            if (n >= 2 && kept.get(n - 2) == x && kept.get(n - 1) == y) continue;
            if (n >= 4) {
                double ax = kept.get(n - 4), ay = kept.get(n - 3), bx = kept.get(n - 2), by = kept.get(n - 1);
                if ((ax == bx && bx == x) || (ay == by && by == y)) {
                    kept.set(n - 2, x);
                    kept.set(n - 1, y);
                    continue;
                }
            }
            kept.add(x);
            kept.add(y);
        }
        double[] route = new double[kept.size()];
        for (int i = 0; i < route.length; i++) route[i] = kept.get(i);
        return route;
    }

    private static void measure(Result result) {
        if (result.nodes.isEmpty()) {
            result.minX = result.maxX = result.minY = result.maxY = 0.0;
            return;
        }
        result.minX = Double.POSITIVE_INFINITY;
        result.maxX = Double.NEGATIVE_INFINITY;
        result.minY = Double.POSITIVE_INFINITY;
        result.maxY = Double.NEGATIVE_INFINITY;
        for (Node node : result.nodes) {
            result.minX = Math.min(result.minX, node.x);
            result.maxX = Math.max(result.maxX, node.x + NODE_WIDTH);
            result.minY = Math.min(result.minY, node.y);
            result.maxY = Math.max(result.maxY, node.y + result.cardHeight);
        }
        for (Edge edge : result.edges) {
            double[] route = edge.route();
            for (int i = 0; i < route.length; i += 2) {
                result.minX = Math.min(result.minX, route[i]);
                result.maxX = Math.max(result.maxX, route[i]);
            }
        }
    }

    private static Result layoutForestComponent(List<UUID> componentIds,
                                                Map<UUID, AnimalRecord> records,
                                                Map<UUID, List<UUID>> childIndex,
                                                int cardHeight) {
        Result result = new Result();
        result.cardHeight = cardHeight;
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
            for (UUID parent : parents(record)) if (included.contains(parent)) parents.add(parent);
            parentIds.put(id, parents);
            remainingParents.put(id, parents.size());
            if (parents.isEmpty()) {
                queue.add(id);
                generationById.put(id, 0);
            }
        }
        if (queue.isEmpty() && !componentIds.isEmpty()) {
            UUID fallback = componentIds.get(0);
            queue.add(fallback);
            generationById.put(fallback, 0);
        }

        Set<UUID> queued = new HashSet<>(queue);
        for (int i = 0; i < queue.size(); i++) {
            UUID parentId = queue.get(i);
            int parentGen = generationById.getOrDefault(parentId, 0);
            for (UUID childId : childIndex.getOrDefault(parentId, List.of())) {
                if (!included.contains(childId)) continue;
                int childGen = Math.max(generationById.getOrDefault(childId, 0), parentGen + 1);
                generationById.put(childId, childGen);
                remainingParents.computeIfPresent(childId, (id, count) -> Math.max(0, count - 1));
                if (remainingParents.getOrDefault(childId, 0) == 0 && queued.add(childId)) {
                    queue.add(childId);
                }
            }
        }

        for (UUID id : componentIds) {
            generationById.putIfAbsent(id, 0);
        }

        pullParentsDown(componentIds, childIndex, included, generationById);

        for (UUID id : componentIds) {
            AnimalRecord record = records.get(id);
            if (record == null) continue;
            Node node = new Node(id, record, generationById.get(id));
            result.nodes.add(node);
            result.byId.put(id, node);
            byGen.computeIfAbsent(node.generation, k -> new ArrayList<>()).add(node);
        }
        Set<Link> linked = new HashSet<>();
        for (UUID id : componentIds) {
            Node parentNode = result.byId.get(id);
            if (parentNode == null) continue;
            for (UUID childId : childIndex.getOrDefault(id, List.of())) {
                Node childNode = result.byId.get(childId);
                if (childNode != null) link(result, linked, parentNode, childNode);
            }
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
                sortByKey(row, node -> barycenterForParents(node.id, parentIds, parentOrder, row));
            }
            for (int generation = maxGeneration - 1; generation >= 0; generation--) {
                List<Node> row = byGen.get(generation);
                if (row == null) continue;
                Map<UUID, Integer> childOrder = indexById(byGen.get(generation + 1));
                sortByKey(row, node -> barycenterForChildren(node.id, childIndex, included, generationById, childOrder, row));
            }
        }

        double rowGap = H_SPACING + FOREST_EXTRA_SPACING;
        double top = 0;
        List<Band> bands = new ArrayList<>();
        for (int generation = 0; generation <= maxGeneration; generation++) {
            List<Node> row = byGen.get(generation);
            if (row == null || row.isEmpty()) continue;
            Band band = new Band();
            band.index = bands.size();
            bands.add(band);
            band.nodes.addAll(row);
            band.gap = rowGap;
            band.cardHeight = cardHeight;

            List<Double> desiredCenters = new ArrayList<>(row.size());
            for (int i = 0; i < row.size(); i++) {
                Node node = row.get(i);
                desiredCenters.add(desiredCenter(node, i, rowGap, parentIds, childIndex, included, generationById, result.byId));
            }
            double desiredMid = desiredCenters.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            if (row.size() > FOREST_ROW_LIMIT) {
                placeGrid(band, top, desiredMid);
                top = band.bottom + V_SPACING;
                continue;
            }
            placeRow(band, top);

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
                cursor = left + NODE_WIDTH + rowGap;
                minLeft = Math.min(minLeft, left);
                maxRight = Math.max(maxRight, left + NODE_WIDTH);
            }

            double shift = desiredMid - (minLeft + maxRight) / 2.0;
            for (Placement placement : placements) placement.node.x += shift;
            measureBand(band);
            top = band.bottom + V_SPACING;
        }

        route(result.edges, bands);
        for (Edge edge : result.edges) edge.route = edge.route();
        measure(result);
        return result;
    }

    private static void pullParentsDown(List<UUID> componentIds,
                                        Map<UUID, List<UUID>> childIndex,
                                        Set<UUID> included,
                                        Map<UUID, Integer> generationById) {
        List<UUID> order = new ArrayList<>(componentIds);
        order.sort(Comparator.comparingInt((UUID id) -> generationById.getOrDefault(id, 0)).reversed());
        for (UUID id : order) {
            int minChildGen = Integer.MAX_VALUE;
            for (UUID childId : childIndex.getOrDefault(id, List.of())) {
                if (!included.contains(childId)) continue;
                Integer childGen = generationById.get(childId);
                if (childGen != null) {
                    minChildGen = Math.min(minChildGen, childGen);
                }
            }
            if (minChildGen != Integer.MAX_VALUE) {
                generationById.put(id, Math.max(generationById.getOrDefault(id, 0), minChildGen - 1));
            }
        }
    }

    private static Map<UUID, Integer> indexById(List<Node> row) {
        Map<UUID, Integer> order = new HashMap<>();
        if (row == null) return order;
        for (int i = 0; i < row.size(); i++) {
            order.put(row.get(i).id, i);
        }
        return order;
    }

    /** Keys that fall back to a node's position must be read before the sort moves anything. */
    private static void sortByKey(List<Node> row, java.util.function.ToDoubleFunction<Node> key) {
        Map<UUID, Double> keys = new HashMap<>();
        for (Node node : row) keys.put(node.id, key.applyAsDouble(node));
        row.sort(Comparator.comparingDouble(node -> keys.get(node.id)));
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
}
