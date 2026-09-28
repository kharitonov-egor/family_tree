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
    void fitIncludesAnEntireLargeTreeInsideTheCanvas() {
        for (int i = 0; i < 150; i++) pet(null, null);
        TreeLayout.Result tree = forest();
        TreeViewport camera = new TreeViewport();
        camera.fit(tree, 320, 58, 218);

        assertTrue(camera.zoom < 0.2);
        assertTrue(tree.minX * camera.zoom + camera.panX >= 15.99);
        assertTrue(tree.maxX * camera.zoom + camera.panX <= 304.01);
        assertTrue(tree.minY * camera.zoom + camera.panY >= 73.99);
        assertTrue(tree.maxY * camera.zoom + camera.panY <= 202.01);
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
        camera.center(-300, 500, 640, 58, 338);

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
