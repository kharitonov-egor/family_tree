package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TreeNavigationTest {
    private final Map<UUID, AnimalRecord> records = new HashMap<>();
    private final Map<UUID, List<UUID>> children = new HashMap<>();

    @Test
    void collapseHidesSharedDescendantsButKeepsOtherFamilies() {
        UUID first = pet(null, null);
        UUID second = pet(null, null);
        UUID child = pet(first, second);
        pet(child, null);
        UUID unrelated = pet(null, null);
        TreeLayout.Result tree = forest();

        assertEquals(Set.of(first, second, unrelated), new HashSet<>(TreeLayout.visibleIds(tree, Set.of(first))));
        assertEquals(records.keySet(), new HashSet<>(TreeLayout.visibleIds(tree, Set.of())));
        assertEquals(5, tree.nodes.size());
    }

    @Test
    void nestedCollapseSurvivesReopeningItsAncestor() {
        UUID founder = pet(null, null);
        UUID child = pet(founder, null);
        pet(child, null);
        TreeLayout.Result tree = forest();

        assertEquals(Set.of(founder), new HashSet<>(TreeLayout.visibleIds(tree, Set.of(founder, child))));
        assertEquals(Set.of(founder, child), new HashSet<>(TreeLayout.visibleIds(tree, Set.of(child))));
    }

    @Test
    void individualTreeRetainsBothEdgesToASharedChild() {
        UUID founder = pet(null, null);
        UUID first = pet(founder, null);
        UUID second = pet(founder, null);
        UUID child = pet(first, second);
        TreeLayout.Result tree = TreeLayout.layout(founder, records, children);

        assertEquals(2, tree.edges.stream().filter(edge -> edge.to.id.equals(child)).count());
        assertFalse(TreeLayout.visibleIds(tree, Set.of(second)).contains(child));
    }

    @Test
    void collapseTerminatesOnAnExistingCycle() {
        UUID first = pet(null, null);
        UUID second = pet(first, null);
        records.get(first).setParents(second, null);
        children.put(second, new ArrayList<>(List.of(first)));
        TreeLayout.Result tree = TreeLayout.layout(first, records, children);

        assertEquals(List.of(first), TreeLayout.visibleIds(tree, Set.of(first)));
    }

    @Test
    void fitStopsAtAReadableZoomAndCentersOnTheAnchorInsteadOfShrinkingAForest() {
        for (int i = 0; i < 150; i++) pet(null, null);
        TreeLayout.Result tree = forest();
        TreeLayout.Node anchor = tree.nodes.get(77);
        TreeViewport camera = new TreeViewport();

        assertFalse(camera.fit(tree, anchor, 0, 320, 58, 218));
        assertEquals(TreeViewport.MIN_FIT_ZOOM, camera.zoom);
        assertEquals(160, (anchor.x + TreeLayout.NODE_WIDTH / 2.0) * camera.zoom + camera.panX, 0.001);
        assertEquals(138, (anchor.y + TreeLayout.NODE_HEIGHT / 2.0) * camera.zoom + camera.panY, 0.001);

        assertFalse(camera.fit(tree, null, 0, 320, 58, 218));
        TreeLayout.Node first = tree.nodes.stream().min(java.util.Comparator.comparingDouble((TreeLayout.Node node) -> node.y)
                .thenComparingDouble(node -> node.x)).orElseThrow();
        assertEquals(13, first.x * camera.zoom + camera.panX, 0.001);
        assertEquals(71, first.y * camera.zoom + camera.panY, 0.001);
    }

    @Test
    void aPageOfFortyUnrelatedPetsFitsAWindowAtTheReadableZoom() {
        for (int i = 0; i < 40; i++) pet(null, null);
        TreeViewport camera = new TreeViewport();
        assertTrue(camera.fit(forest(), null, 0, 800, 76, 468), "Fit zoomed to " + camera.zoom);
    }

    @Test
    void aTwoCardTreeFitsTheDefaultWindowAtAReadableZoom() {
        UUID parent = pet(null, null);
        pet(parent, null);
        TreeLayout.Result tree = TreeLayout.layout(parent, records, children);
        int[] tools = {52, 64, 76, 54, 64, 104, 72, 50, 70};
        TreeToolbar.Layout bar = TreeToolbar.arrange(tools, 427, 240);
        TreeViewport camera = new TreeViewport();

        assertTrue(bar.side(), "854x480 at GUI scale 2 is too short for rows of buttons above the tree");
        assertTrue(camera.fit(tree, null, 0, bar.canvasRight(), bar.canvasTop(), bar.canvasBottom()));
        assertTrue(camera.zoom >= 0.55, "Fit zoomed to " + camera.zoom);
        for (TreeToolbar.Slot slot : bar.slots()) {
            assertTrue(slot.x() >= bar.canvasRight() && slot.x() + slot.width() <= 427 - TreeToolbar.MARGIN + 1);
            assertTrue(slot.y() + TreeToolbar.ROW_HEIGHT <= bar.canvasBottom());
        }

        TreeToolbar.Layout wide = TreeToolbar.arrange(tools, 800, 500);
        assertFalse(wide.side());
        assertEquals(1, wide.slots().stream().map(TreeToolbar.Slot::y).distinct().count());
        assertTrue(wide.canvasTop() <= 60);
    }

    @Test
    void scrollingOutStopsAtTheMinimumZoom() {
        TreeViewport camera = new TreeViewport();
        camera.zoomAt(100, 100, -1000);
        assertEquals(TreeViewport.MIN_ZOOM, camera.zoom);
    }

    @Test
    void relayoutKeepsTheAnchorCardStillOnScreen() {
        UUID first = pet(null, null);
        UUID second = pet(null, null);
        UUID child = pet(first, second);
        for (int i = 0; i < 4; i++) pet(child, null);
        TreeLayout.Result full = TreeLayout.layout(first, records, children);
        TreeLayout.Result hidden = TreeLayout.layout(first, records, children,
                new HashSet<>(full.byId.keySet()).stream().filter(id -> records.get(id).parentA() == child)
                        .collect(java.util.stream.Collectors.toSet()), Set.of(), Set.of());
        TreeViewport camera = new TreeViewport();
        camera.zoom = 0.7;
        camera.panX = 40;
        camera.panY = -12;
        TreeLayout.Node before = hidden.byId.get(child), after = full.byId.get(child);
        double x = before.x * camera.zoom + camera.panX, y = before.y * camera.zoom + camera.panY;

        camera.follow(before, after);

        assertEquals(x, after.x * camera.zoom + camera.panX, 0.0001);
        assertEquals(y, after.y * camera.zoom + camera.panY, 0.0001);
    }

    @Test
    void aParentsTreeShowsEachFoalsOtherParentBesideIt() {
        UUID comet = pet(null, null);
        UUID dusk = pet(null, null);
        UUID gnocchi = pet(comet, dusk);
        UUID ember = pet(gnocchi, null);
        UUID ash = pet(null, null);
        UUID pip = pet(ember, ash);
        TreeLayout.Result tree = TreeLayout.layout(comet, records, children);

        TreeLayout.Node partner = tree.byId.get(dusk);
        assertNotNull(partner, "Dusk, Gnocchi's other parent, is missing from Comet's tree");
        assertTrue(partner.partner);
        assertEquals(tree.byId.get(comet).y, partner.y);
        assertEquals(TreeLayout.NODE_WIDTH + TreeLayout.H_SPACING, partner.x - tree.byId.get(comet).x, 0.001);
        assertTrue(tree.edges.stream().anyMatch(edge -> edge.from.id.equals(dusk) && edge.to.id.equals(gnocchi)));
        assertNotNull(tree.byId.get(ash), "Ash, a grandchild's other parent, is missing");
        assertEquals(tree.byId.get(ember).y, tree.byId.get(ash).y);
        assertTrue(tree.edges.stream().anyMatch(edge -> edge.from.id.equals(ash) && edge.to.id.equals(pip)));

        List<UUID> visible = TreeLayout.visibleIds(tree, Set.of(comet));
        assertEquals(List.of(comet), visible, "Hiding Comet's branch leaves Dusk with no foal to explain");
    }

    @Test
    void deepLinesMarkTheTopCardAndShowMoreOnRequest() {
        UUID line = pet(null, null);
        UUID founder = line;
        for (int generation = 2; generation <= 25; generation++) line = pet(line, pet(null, null));
        TreeLayout.Result tree = TreeLayout.layout(line, records, children);

        assertEquals(1, tree.olderHidden.size());
        UUID top = tree.olderHidden.keySet().iterator().next();
        assertEquals(18, tree.olderHidden.get(top));
        assertEquals(tree.minY, tree.byId.get(top).y);

        TreeLayout.Result further = TreeLayout.layout(line, records, children, Set.of(), Set.of(top), Set.of());
        assertTrue(further.byId.containsKey(top));
        assertEquals(12, further.olderHidden.values().iterator().next());
        assertEquals(tree.nodes.size() + 12, further.nodes.size());

        TreeLayout.Result down = TreeLayout.layout(founder, records, children);
        assertEquals(1, down.youngerHidden.size());
        assertEquals(18, down.youngerHidden.values().iterator().next());
        assertTrue(tree.youngerHidden.isEmpty());
    }

    @Test
    void aLitterOfFortyFiveWrapsIntoRowsThatExportAndLinksAvoidCards() {
        UUID sire = pet(null, null);
        UUID dam = pet(null, null);
        for (int i = 0; i < 45; i++) pet(sire, dam);
        TreeLayout.Result tree = TreeLayout.layout(sire, records, children);

        assertEquals(47, tree.nodes.size());
        long rows = tree.nodes.stream().filter(node -> node.generation == -1).map(node -> node.y).distinct().count();
        assertTrue(rows >= 3 && rows <= 7, rows + " rows");
        double width = tree.maxX - tree.minX, height = tree.maxY - tree.minY;
        assertTrue(width < 3 * height, "Tree is " + width + " wide and " + height + " tall");
        assertDoesNotThrow(() -> com.egakh.familytree.client.export.TreeImageExporter.requireExportable(tree));
        assertEquals(90, tree.edges.size());
        assertLinksAvoidCards(tree);
        assertEquals(tree.nodes.size(), tree.nodes.stream().map(node -> node.x + "," + node.y).distinct().count(), "Cards overlap");
    }

    @Test
    void familiesPageLargestFirstAndKeepEachFamilyWhole() {
        for (int i = 0; i < 95; i++) pet(null, null);
        UUID a = pet(null, null), b = pet(null, null);
        for (int i = 0; i < 60; i++) pet(a, b);
        UUID c = pet(null, null);
        pet(c, null);

        List<List<UUID>> families = TreeLayout.families(records.keySet(), records, children);
        assertEquals(97, families.size());
        assertEquals(62, families.get(0).size());
        assertEquals(2, families.get(1).size());

        List<List<UUID>> pages = TreeLayout.pages(families, 40);
        assertEquals(62, pages.get(0).size(), "A family larger than a page gets its own page");
        assertTrue(pages.stream().skip(1).allMatch(page -> page.size() <= 40));
        assertEquals(records.size(), pages.stream().mapToInt(List::size).sum());
        assertEquals(4, pages.size());
    }

    @Test
    void aBredFarmFamilyWrapsSoEveryLinkAvoidsCards() {
        java.util.Random random = new java.util.Random(42);
        List<UUID> founders = new ArrayList<>();
        for (int i = 0; i < 120; i++) founders.add(pet(null, null));
        for (int i = 0; i < 240; i++) {
            UUID first = founders.get(random.nextInt(founders.size()));
            UUID second = founders.get(random.nextInt(founders.size()));
            pet(first, second.equals(first) ? null : second);
        }
        TreeLayout.Result tree = forest();
        double width = tree.maxX - tree.minX, height = tree.maxY - tree.minY;
        assertTrue(width < 4 * height, "Forest is " + width + " wide and " + height + " tall");
        assertLinksAvoidCards(tree);
    }

    @Test
    void hidingAPartnersBranchKeepsThePartnerSoTheBranchCanBeShownAgain() {
        UUID comet = pet(null, null);
        UUID dusk = pet(null, null);
        UUID gnocchi = pet(comet, dusk);
        TreeLayout.Result tree = TreeLayout.layout(comet, records, children);

        List<UUID> visible = TreeLayout.visibleIds(tree, Set.of(dusk));
        assertTrue(visible.contains(dusk), "Hiding Dusk's branch hid Dusk, so Show branch can't be pressed");
        assertFalse(visible.contains(gnocchi));
        Set<UUID> hidden = new HashSet<>(tree.byId.keySet());
        visible.forEach(hidden::remove);
        TreeLayout.Result collapsed = TreeLayout.layout(comet, records, children, hidden, Set.of(), Set.of());
        assertTrue(collapsed.byId.containsKey(dusk));
        assertFalse(collapsed.byId.containsKey(gnocchi));
    }

    @Test
    void linksThatSkipGenerationsGoAroundTheCardsBetween() {
        UUID grandparent = pet(null, null);
        UUID focus = pet(grandparent, null);
        UUID mate = pet(null, null);
        UUID child = pet(focus, mate);
        UUID foal = pet(child, grandparent);
        TreeLayout.Result tree = TreeLayout.layout(focus, records, children);
        assertTrue(tree.edges.stream().anyMatch(edge -> edge.from.id.equals(grandparent) && edge.to.id.equals(foal)));
        assertLinksAvoidCards(tree);
        assertLinksAvoidCards(forest());
    }

    /** Final QA N2: Comet's tree hung Sundae, bred from Comet and her foal Gelato, from the Comet and Dusk bus. */
    @Test
    void aBreedBackFoalHangsBelowItsYoungerParentInTheOlderParentsTree() {
        UUID comet = pet(null, null);
        UUID dusk = pet(null, null);
        UUID gelato = pet(comet, dusk);
        UUID sundae = pet(comet, gelato);
        TreeLayout.Result tree = TreeLayout.layout(comet, records, children);

        assertEquals(-1, tree.byId.get(gelato).generation);
        assertEquals(-2, tree.byId.get(sundae).generation, "Sundae sits beside Gelato as if they were full siblings");
        assertEquals(Set.of(comet, gelato), parentsDrawn(tree, sundae));
        assertEquals(Set.of(comet, dusk), parentsDrawn(tree, gelato));

        Map<String, TreeLayout.Result> views = new java.util.LinkedHashMap<>();
        for (UUID focus : List.of(comet, dusk, gelato, sundae)) views.put(records.get(focus).name(), TreeLayout.layout(focus, records, children));
        views.put("the species view", forest());
        views.forEach((view, layout) -> {
            assertEquals(Set.of(comet, gelato), parentsDrawn(layout, sundae), view);
            assertTrue(layout.byId.get(sundae).y > layout.byId.get(gelato).y, view);
            assertLinksAvoidCards(layout);
            double bus = horizontalYs(edge(layout, dusk, gelato)).iterator().next();
            for (TreeLayout.Edge intoGelato : List.of(edge(layout, comet, gelato), edge(layout, dusk, gelato)))
                assertTrue(lowestMeeting(edge(layout, comet, sundae), intoGelato) < bus,
                        "In " + view + ", Comet's link to Sundae joins the bus into Gelato");
        });
    }

    /** Sol's review of #24: with a third parent in Dusk's row, the lane for Comet's link to Sundae ran along Dusk's line. */
    @Test
    void aBreedBackLinkKeepsOffEveryOtherParentsLine() {
        UUID comet = pet(null, null);
        UUID dusk = pet(null, null);
        UUID ember = pet(null, null);
        UUID gelato = pet(comet, dusk);
        pet(dusk, ember);
        UUID sundae = pet(comet, gelato);
        TreeLayout.Result tree = TreeLayout.layout(dusk, records, children);

        assertEquals(Set.of(comet, gelato), parentsDrawn(tree, sundae));
        TreeLayout.Edge breedBack = edge(tree, comet, sundae);
        for (TreeLayout.Edge other : tree.edges) {
            if (other.from.id.equals(comet) || other.to.id.equals(sundae)) continue;
            assertEquals(0, sharedLength(breedBack, other), 0.001,
                    "Comet's link to Sundae runs along the link from " + other.from.record.name() + " to " + other.to.record.name());
        }
        assertLinksAvoidCards(tree);
    }

    @Test
    void everyLinkRunsDownAndSkippingLinksRunAlongNoUnrelatedLine() {
        java.util.Random random = new java.util.Random(7);
        List<UUID> herd = new ArrayList<>();
        for (int i = 0; i < 4; i++) herd.add(pet(null, null));
        for (int i = 0; i < 40; i++) {
            int recent = Math.max(0, herd.size() - 8);
            UUID a = herd.get(recent + random.nextInt(herd.size() - recent));
            UUID b = random.nextInt(4) == 0 ? herd.get(random.nextInt(herd.size())) : herd.get(recent + random.nextInt(herd.size() - recent));
            herd.add(pet(a, a.equals(b) ? null : b));
        }
        int checked = 0;
        for (UUID focus : herd) {
            TreeLayout.Result tree = TreeLayout.layout(focus, records, children);
            Map<Integer, Set<Double>> rows = new HashMap<>();
            for (TreeLayout.Node node : tree.nodes) rows.computeIfAbsent(node.generation, key -> new HashSet<>()).add(node.y);
            java.util.function.Predicate<TreeLayout.Node> wrapped = node -> rows.get(node.generation).size() > 1;
            // Cards at the six-generation limit still link to a parent drawn lower down; their tab offers the rest.
            List<TreeLayout.Edge> inside = tree.edges.stream().filter(edge -> edge.to.generation < TreeLayout.MAX_ANCESTOR_GENERATIONS
                    && edge.from.generation > -TreeLayout.MAX_DESCENDANT_GENERATIONS).toList();
            checked += inside.size();
            for (TreeLayout.Edge edge : inside) {
                assertTrue(edge.from.y < edge.to.y, "In " + records.get(focus).name() + "'s tree, the link from " + edge.from.record.name()
                        + " to " + edge.to.record.name() + " doesn't run down");
                if (edge.from.generation - edge.to.generation < 2 || wrapped.test(edge.from) || wrapped.test(edge.to)) continue;
                for (TreeLayout.Edge other : inside) {
                    if (other.from == edge.from || other.to == edge.to || wrapped.test(other.from) || wrapped.test(other.to)) continue;
                    assertEquals(0, sharedLength(edge, other), 0.001, "In " + records.get(focus).name() + "'s tree, the link from "
                            + edge.from.record.name() + " to " + edge.to.record.name() + " runs along the link from "
                            + other.from.record.name() + " to " + other.to.record.name());
                }
            }
        }
        assertTrue(checked > 500, checked + " links checked");
    }

    @Test
    void aFoalOfAParentAndItsGrandchildHangsBelowTheGrandchild() {
        UUID comet = pet(null, null);
        UUID dusk = pet(null, null);
        UUID gelato = pet(comet, dusk);
        UUID mate = pet(null, null);
        UUID fudge = pet(gelato, mate);
        UUID tiny = pet(comet, fudge);
        TreeLayout.Result tree = TreeLayout.layout(comet, records, children);

        assertEquals(-2, tree.byId.get(fudge).generation);
        assertEquals(-3, tree.byId.get(tiny).generation);
        assertEquals(Set.of(comet, fudge), parentsDrawn(tree, tiny));
        assertEquals(Set.of(gelato, mate), parentsDrawn(tree, fudge));
        assertLinksAvoidCards(tree);
        assertLinksAvoidCards(forest());
    }

    @Test
    void fullSiblingsBredTogetherShowTheirFoalBelowBothAndTheirSharedParents() {
        UUID comet = pet(null, null);
        UUID dusk = pet(null, null);
        UUID gelato = pet(comet, dusk);
        UUID mochi = pet(comet, dusk);
        UUID foal = pet(gelato, mochi);

        TreeLayout.Result parents = TreeLayout.layout(comet, records, children);
        assertEquals(-2, parents.byId.get(foal).generation);
        assertEquals(Set.of(gelato, mochi), parentsDrawn(parents, foal));
        assertEquals(Set.of(comet, dusk), parentsDrawn(parents, mochi));
        assertLinksAvoidCards(parents);

        TreeLayout.Result sibling = TreeLayout.layout(gelato, records, children);
        TreeLayout.Node mate = sibling.byId.get(mochi);
        assertTrue(mate.partner);
        assertEquals(Set.of(comet, dusk), parentsDrawn(sibling, mochi), "Mochi looks unrelated to Gelato");
        assertEquals(Set.of(gelato, mochi), parentsDrawn(sibling, foal));
        assertLinksAvoidCards(sibling);
        assertEquals(sibling.nodes.size(), sibling.nodes.stream().map(node -> node.x + "," + node.y).distinct().count(), "Cards overlap");
    }

    @Test
    void descendantDepthsFollowTheLongestLineAndSkipLoops() {
        UUID comet = pet(null, null);
        UUID gelato = pet(comet, null);
        UUID sundae = pet(comet, gelato);
        assertEquals(Map.of(comet, 0, gelato, 1, sundae, 2), TreeLayout.descendantDepths(comet, records, children));
        assertEquals(Map.of(sundae, 0, gelato, 1, comet, 2), TreeLayout.ancestorHeights(sundae, records));

        UUID focus = pet(null, null);
        UUID first = pet(focus, null);
        UUID second = pet(first, null);
        records.get(first).setParents(focus, second);
        children.computeIfAbsent(second, key -> new ArrayList<>()).add(first);
        UUID below = pet(second, null);
        assertEquals(Map.of(focus, 0), TreeLayout.descendantDepths(focus, records, children));
        assertEquals(Map.of(below, 0), TreeLayout.ancestorHeights(below, records));
        assertEquals(Set.of(focus, first, second, below), TreeLayout.layout(focus, records, children).byId.keySet());
        assertEquals(Set.of(focus, first, second, below), TreeLayout.layout(below, records, children).byId.keySet());
    }

    private static Set<UUID> parentsDrawn(TreeLayout.Result tree, UUID child) {
        Set<UUID> parents = new HashSet<>();
        for (TreeLayout.Edge edge : tree.edges) if (edge.to.id.equals(child)) parents.add(edge.from.id);
        return parents;
    }

    private static TreeLayout.Edge edge(TreeLayout.Result tree, UUID from, UUID to) {
        return tree.edges.stream().filter(edge -> edge.from.id.equals(from) && edge.to.id.equals(to)).findFirst().orElseThrow();
    }

    /** How far two links run along the same line. Crossing or meeting at one point counts as zero. */
    private static double sharedLength(TreeLayout.Edge a, TreeLayout.Edge b) {
        double shared = 0;
        double[] p = a.route(), q = b.route();
        for (int i = 2; i < p.length; i += 2) {
            for (int j = 2; j < q.length; j += 2) {
                boolean pFlat = p[i - 1] == p[i + 1], qFlat = q[j - 1] == q[j + 1];
                if (pFlat != qFlat) continue;
                int along = pFlat ? 0 : 1, across = 1 - along;
                if (p[i - 2 + across] != q[j - 2 + across]) continue;
                double start = Math.max(Math.min(p[i - 2 + along], p[i + along]), Math.min(q[j - 2 + along], q[j + along]));
                double end = Math.min(Math.max(p[i - 2 + along], p[i + along]), Math.max(q[j - 2 + along], q[j + along]));
                shared += Math.max(0, end - start);
            }
        }
        return shared;
    }

    /** The lowest point two links share, or negative infinity when they never touch. */
    private static double lowestMeeting(TreeLayout.Edge a, TreeLayout.Edge b) {
        double lowest = Double.NEGATIVE_INFINITY;
        double[] p = a.route(), q = b.route();
        for (int i = 2; i < p.length; i += 2) {
            for (int j = 2; j < q.length; j += 2) {
                double left = Math.max(Math.min(p[i - 2], p[i]), Math.min(q[j - 2], q[j]));
                double right = Math.min(Math.max(p[i - 2], p[i]), Math.max(q[j - 2], q[j]));
                double top = Math.max(Math.min(p[i - 1], p[i + 1]), Math.min(q[j - 1], q[j + 1]));
                double bottom = Math.min(Math.max(p[i - 1], p[i + 1]), Math.max(q[j - 1], q[j + 1]));
                if (left <= right && top <= bottom) lowest = Math.max(lowest, bottom);
            }
        }
        return lowest;
    }

    private static Set<Double> horizontalYs(TreeLayout.Edge edge) {
        Set<Double> ys = new HashSet<>();
        double[] route = edge.route();
        for (int i = 2; i < route.length; i += 2) if (route[i - 1] == route[i + 1] && route[i - 2] != route[i]) ys.add(route[i + 1]);
        return ys;
    }

    @Test
    void depthTabsCountOnlyTheGenerationsLeftOut() {
        UUID p5 = pet(null, null);
        UUID p4 = pet(p5, null);
        UUID p3 = pet(p4, null);
        UUID p2 = pet(p3, null);
        UUID p1 = pet(p2, null);
        UUID p = pet(p1, null);
        UUID q = pet(null, null);
        UUID t = pet(p, q);
        UUID x5 = pet(t, null);
        UUID x4 = pet(x5, null);
        UUID x3 = pet(x4, null);
        UUID x2 = pet(x3, null);
        UUID x = pet(x2, null);
        UUID focus = pet(p, x);
        TreeLayout.Result tree = TreeLayout.layout(focus, records, children);

        assertEquals(java.util.Map.of(t, 1), tree.olderHidden, "Only Q, one generation, is left out above T");
    }

    /** Every link segment stays outside every card, except where it meets its own two cards. */
    private static void assertLinksAvoidCards(TreeLayout.Result tree) {
        for (TreeLayout.Edge edge : tree.edges) {
            double[] route = edge.route();
            for (int i = 2; i < route.length; i += 2) {
                double x1 = Math.min(route[i - 2], route[i]), x2 = Math.max(route[i - 2], route[i]);
                double y1 = Math.min(route[i - 1], route[i + 1]), y2 = Math.max(route[i - 1], route[i + 1]);
                for (TreeLayout.Node node : tree.nodes) {
                    boolean crosses = x2 > node.x && x1 < node.x + TreeLayout.NODE_WIDTH
                            && y2 > node.y && y1 < node.y + TreeLayout.NODE_HEIGHT;
                    assertFalse(crosses, "A link from " + edge.from.record.name() + " to " + edge.to.record.name()
                            + " runs under " + node.record.name());
                }
            }
        }
    }

    @Test
    void zoomKeepsThePointUnderTheCursorFixed() {
        TreeViewport camera = new TreeViewport();
        camera.panX = -120;
        camera.panY = 30;
        camera.zoom = 0.5;
        double worldX = (137 - camera.panX) / camera.zoom;
        double worldY = (92 - camera.panY) / camera.zoom;

        camera.zoomAt(137, 92, 4);

        assertEquals(worldX, (137 - camera.panX) / camera.zoom, 0.000001);
        assertEquals(worldY, (92 - camera.panY) / camera.zoom, 0.000001);
        camera.zoomAt(137, 92, 1000);
        assertEquals(2.75, camera.zoom);
        assertEquals(worldX, (137 - camera.panX) / camera.zoom, 0.000001);
    }

    @Test
    void centerUsesTheCanvasBetweenTheToolbarAndFooter() {
        TreeViewport camera = new TreeViewport();
        camera.zoom = 1.25;
        camera.center(-300, 500, 0, 640, 58, 338);

        assertEquals(320, -300 * camera.zoom + camera.panX);
        assertEquals(198, 500 * camera.zoom + camera.panY);
    }

    @Test
    void compactCardsFitWithinTheirLayoutSpacing() {
        for (double zoom : new double[]{0.02, 0.2, 0.5, 0.74, 1, 2.75}) {
            assertTrue(TreeViewport.nodeWidth(zoom) < (TreeLayout.NODE_WIDTH + TreeLayout.H_SPACING) * zoom);
            assertTrue(TreeViewport.nodeHeight(zoom) < (TreeLayout.NODE_HEIGHT + TreeLayout.V_SPACING) * zoom);
        }
    }

    @Test
    void largeBredFarmsLayOutWithoutBreakingTheSort() {
        for (int seed = 0; seed < 5; seed++) {
            records.clear();
            children.clear();
            java.util.Random random = new java.util.Random(seed);
            List<UUID> herd = new ArrayList<>();
            for (int i = 0; i < 1500; i++) {
                UUID a = herd.size() < 2 || random.nextInt(4) == 0 ? null : herd.get(random.nextInt(herd.size()));
                UUID b = a == null ? null : herd.get(random.nextInt(herd.size()));
                herd.add(pet(a, b == a ? null : b));
            }
            TreeLayout.Result tree = forest();
            assertEquals(1500, tree.nodes.size());
        }
    }

    private TreeLayout.Result forest() {
        return TreeLayout.layoutForest(new ArrayList<>(records.keySet()), records, children);
    }

    private UUID pet(UUID first, UUID second) {
        UUID id = UUID.randomUUID();
        records.put(id, new AnimalRecord(id, "minecraft:wolf", "Wolf " + records.size(), false,
                first, second, 0, 0, false, null, null, null, null, null, null, null));
        if (first != null) children.computeIfAbsent(first, key -> new ArrayList<>()).add(id);
        if (second != null) children.computeIfAbsent(second, key -> new ArrayList<>()).add(id);
        return id;
    }
}
