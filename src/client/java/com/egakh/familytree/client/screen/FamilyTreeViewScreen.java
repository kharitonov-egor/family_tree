package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.util.Genealogy;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FamilyTreeViewScreen extends Screen {

    private final Screen parent;
    private final FamilyTreeSnapshotPayload snapshot;
    private final UUID focusId;
    private final String aggregateSpeciesId;

    private final Map<UUID, AnimalRecord> records = new HashMap<>();
    private final Map<UUID, List<UUID>> childIndex = new HashMap<>();
    private final Map<UUID, Integer> generations = new HashMap<>();
    private TreeLayout.Result tree;

    private double panX = 0;
    private double panY = 0;
    private double zoom = 1.0;
    private boolean dragging = false;
    private double lastMouseX;
    private double lastMouseY;

    public FamilyTreeViewScreen(Screen parent, FamilyTreeSnapshotPayload snapshot, UUID focusId) {
        super(Component.translatable("familytree.screen.view.title",
                snapshot.records().stream().filter(r -> r.id().equals(focusId)).findFirst()
                        .map(AnimalRecord::name).orElse("?")));
        this.parent = parent;
        this.snapshot = snapshot;
        this.focusId = focusId;
        this.aggregateSpeciesId = null;
        loadRecords();
    }

    private FamilyTreeViewScreen(Screen parent, FamilyTreeSnapshotPayload snapshot, String aggregateSpeciesId, Component title) {
        super(title);
        this.parent = parent;
        this.snapshot = snapshot;
        this.focusId = null;
        this.aggregateSpeciesId = aggregateSpeciesId;
        loadRecords();
    }

    public static FamilyTreeViewScreen forSpecies(Screen parent, FamilyTreeSnapshotPayload snapshot,
                                                  String speciesId, Component title) {
        return new FamilyTreeViewScreen(parent, snapshot, speciesId, title);
    }

    private void loadRecords() {
        for (AnimalRecord r : snapshot.records()) {
            records.put(r.id(), r);
        }
        for (AnimalRecord r : snapshot.records()) {
            if (r.parentA() != null) {
                childIndex.computeIfAbsent(r.parentA(), k -> new ArrayList<>()).add(r.id());
            }
            if (r.parentB() != null) {
                childIndex.computeIfAbsent(r.parentB(), k -> new ArrayList<>()).add(r.id());
            }
        }
        generations.putAll(Genealogy.computeGenerations(records));
    }

    @Override
    protected void init() {
        if (aggregateSpeciesId == null) {
            tree = TreeLayout.layout(focusId, records, childIndex);
        } else {
            List<UUID> includedIds = snapshot.records().stream()
                    .filter(r -> aggregateSpeciesId.equals(r.speciesId()))
                    .map(AnimalRecord::id)
                    .toList();
            tree = TreeLayout.layoutForest(includedIds, records, childIndex);
        }
        if (tree != null && !tree.nodes.isEmpty()) {
            double availableWidth = Math.max(200.0, this.width - 80.0);
            double availableHeight = Math.max(200.0, this.height - 120.0);
            double treeWidth = Math.max(1.0, tree.maxX - tree.minX);
            double treeHeight = Math.max(1.0, tree.maxY - tree.minY);
            double fitX = availableWidth / treeWidth;
            double fitY = availableHeight / treeHeight;
            zoom = Math.max(0.35, Math.min(1.25, Math.min(fitX, fitY)));

            double scaledTreeWidth = treeWidth * zoom;
            double scaledTreeHeight = treeHeight * zoom;
            panX = (this.width - scaledTreeWidth) / 2.0 - tree.minX * zoom;
            panY = (this.height - scaledTreeHeight) / 2.0 - tree.minY * zoom;
        }
        this.addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"),
                        b -> {
                            if (this.minecraft != null) this.minecraft.setScreen(parent);
                        })
                .bounds(8, 8, 60, 18).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, this.width, this.height, 0xCC0F1115);
        super.extractRenderState(gfx, mouseX, mouseY, delta);

        if (tree == null || tree.nodes.isEmpty()) {
            gfx.centeredText(this.font, Component.translatable("familytree.screen.empty"),
                    this.width / 2, this.height / 2, 0xFFCCCCCC);
            return;
        }

        for (TreeLayout.Edge e : tree.edges) {
            TreeRenderer.drawEdge(gfx, e.from, e.to, panX, panY, zoom);
        }
        for (TreeLayout.Node n : tree.nodes) {
            TreeRenderer.drawNode(gfx, this.font, n,
                    snapshot.currentWorldDay(), snapshot.currentEpochMillis(),
                    n.id.equals(focusId), generations.getOrDefault(n.id, 0), panX, panY, zoom);
        }

        gfx.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        TreeLayout.Node hit = findNodeAt(event.x(), event.y());
        if (hit != null) {
            if (this.minecraft != null && aggregateSpeciesId != null) {
                this.minecraft.setScreen(new FamilyTreeViewScreen(parent, snapshot, hit.id));
            }
            return true;
        }
        if (event.button() == 0) {
            dragging = true;
            lastMouseX = event.x();
            lastMouseY = event.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging) {
            double mouseX = event.x();
            double mouseY = event.y();
            panX += (mouseX - lastMouseX);
            panY += (mouseY - lastMouseY);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double oldZoom = zoom;
        double factor = Math.pow(1.1, verticalAmount);
        zoom = Math.max(0.2, Math.min(2.75, zoom * factor));
        double scale = zoom / oldZoom;
        panX = mouseX - (mouseX - panX) * scale;
        panY = mouseY - (mouseY - panY) * scale;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private TreeLayout.Node findNodeAt(double mouseX, double mouseY) {
        if (tree == null) return null;
        for (TreeLayout.Node node : tree.nodes) {
            int x = (int) Math.round(node.x * zoom + panX);
            int y = (int) Math.round(node.y * zoom + panY);
            int w = Math.max(110, (int) Math.round(TreeLayout.NODE_WIDTH * zoom));
            int h = Math.max(50, (int) Math.round(TreeLayout.NODE_HEIGHT * zoom));
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                return node;
            }
        }
        return null;
    }
}
