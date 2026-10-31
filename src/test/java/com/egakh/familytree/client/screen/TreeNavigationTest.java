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
        assertTrue(tree.minX * camera.zoom + camera.panX > 0);
        assertTrue(tree.minY * camera.zoom + camera.panY > 58);
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
