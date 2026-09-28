package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.network.payloads.RenamePetRequest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.egakh.familytree.util.Genealogy;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FamilyTreeViewScreen extends Screen {

    private final Screen parent;
    private final FamilyTreeSnapshotPayload snapshot;
    private final UUID focusId;
    private final String aggregateSpeciesId;

    private final Map<UUID, AnimalRecord> records = new HashMap<>();
    private final Map<UUID, List<UUID>> childIndex = new HashMap<>();
    private final Map<UUID, Integer> generations = new HashMap<>();
    private static final int CANVAS_TOP = 58;
    private final TreeViewport viewport = new TreeViewport();
    private final Set<UUID> collapsed = new HashSet<>();
    private TreeLayout.Result fullTree;
    private TreeLayout.Result tree;
    private UUID selectedId;
    private Button centerButton;
    private Button branchButton;
    private Button expandAllButton;
    private Button renameButton;
    private int previousWidth;
    private int previousHeight;
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
        this.selectedId = focusId;
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
        dragging = false;
        if (fullTree == null) {
            if (aggregateSpeciesId == null) {
                fullTree = TreeLayout.layout(focusId, records, childIndex);
            } else {
                List<UUID> includedIds = snapshot.records().stream()
                        .filter(r -> aggregateSpeciesId.equals(r.speciesId()))
                        .map(AnimalRecord::id)
                        .toList();
                fullTree = TreeLayout.layoutForest(includedIds, records, childIndex);
            }
            tree = fullTree;
            fitTree();
        } else {
            viewport.panX += (this.width - previousWidth) / 2.0;
            viewport.panY += (this.height - previousHeight) / 2.0;
        }
        previousWidth = this.width;
        previousHeight = this.height;
        this.addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"),
                        b -> onClose())
                .bounds(8, 8, 60, 18).build());
        renameButton = this.addRenderableWidget(Button.builder(Component.translatable("familytree.rename.button"),
                button -> {
                    AnimalRecord selected = records.get(selectedId);
                    if (selected != null && this.minecraft != null) {
                        this.minecraft.setScreen(new RenamePetScreen(this, selected));
                    }
                }).bounds(this.width - 72, 8, 64, 18).build());
        int x = Math.max(8, (this.width - 304) / 2);
        this.addRenderableWidget(Button.builder(Component.translatable("familytree.screen.tree.fit"),
                b -> fitTree()).bounds(x, 32, 56, 18).build());
        centerButton = this.addRenderableWidget(Button.builder(Component.translatable("familytree.screen.tree.center"),
                b -> centerSelected()).bounds(x + 60, 32, 78, 18).build());
        branchButton = this.addRenderableWidget(Button.builder(Component.empty(),
                b -> toggleBranch()).bounds(x + 142, 32, 92, 18).build());
        expandAllButton = this.addRenderableWidget(Button.builder(Component.translatable("familytree.screen.tree.expand_all"),
                b -> {
                    collapsed.clear();
                    rebuildTree();
                    fitTree();
                }).bounds(x + 238, 32, 66, 18).build());
        branchButton.setTooltip(Tooltip.create(Component.translatable("familytree.screen.tree.branch_hint")));
        updateControls();
    }

    private int canvasBottom() {
        return this.height - 32;
    }

    private boolean inCanvas(double x, double y) {
        return x >= 0 && x < this.width && y >= CANVAS_TOP && y < canvasBottom();
    }

    private void fitTree() {
        if (tree != null) viewport.fit(tree, this.width, CANVAS_TOP, canvasBottom());
    }

    private void centerSelected() {
        TreeLayout.Node selected = tree.byId.get(selectedId);
        if (selected == null) return;
        viewport.zoom = Math.max(1.0, viewport.zoom);
        viewport.center(selected.x + TreeLayout.NODE_WIDTH / 2.0,
                selected.y + TreeLayout.NODE_HEIGHT / 2.0, this.width, CANVAS_TOP, canvasBottom());
    }

    private void toggleBranch() {
        if (selectedId == null) return;
        if (!collapsed.remove(selectedId)) collapsed.add(selectedId);
        rebuildTree();
    }

    private void rebuildTree() {
        TreeLayout.Node oldSelection = tree.byId.get(selectedId);
        tree = collapsed.isEmpty() ? fullTree
                : TreeLayout.layoutForest(TreeLayout.visibleIds(fullTree, collapsed), records, childIndex);
        TreeLayout.Node newSelection = tree.byId.get(selectedId);
        if (oldSelection != null && newSelection != null) {
            viewport.panX += (oldSelection.x - newSelection.x) * viewport.zoom;
            viewport.panY += (oldSelection.y - newSelection.y) * viewport.zoom;
        } else if (newSelection == null) {
            selectedId = null;
        }
        updateControls();
    }

    private void updateControls() {
        centerButton.active = selectedId != null && tree.byId.containsKey(selectedId);
        AnimalRecord selected = records.get(selectedId);
        centerButton.setTooltip(selected == null ? null : Tooltip.create(
                Component.translatable("familytree.screen.tree.selected", selected.name())));
        branchButton.active = centerButton.active
                && fullTree.edges.stream().anyMatch(edge -> edge.from.id.equals(selectedId));
        branchButton.setMessage(Component.translatable(collapsed.contains(selectedId)
                ? "familytree.screen.tree.expand" : "familytree.screen.tree.collapse"));
        expandAllButton.active = !collapsed.isEmpty();
        renameButton.active = centerButton.active && ClientPlayNetworking.canSend(RenamePetRequest.TYPE);
    }

    public void applyRename(AnimalRecord updated) {
        AnimalRecord record = records.get(updated.id());
        if (record != null) {
            record.rename(updated.originalName(), updated.nameAutogenerated());
            record.setTreeName(updated.treeName());
        }
        if (parent instanceof FamilyTreeViewScreen previous) previous.applyRename(updated);
        if (centerButton != null) updateControls();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, this.width, this.height, 0xCC0F1115);
        gfx.enableScissor(0, CANVAS_TOP, this.width, canvasBottom());
        if (tree == null || tree.nodes.isEmpty()) {
            gfx.centeredText(this.font, Component.translatable("familytree.screen.empty"),
                    this.width / 2, this.height / 2, 0xFFCCCCCC);
        } else {
            for (TreeLayout.Edge e : tree.edges) {
                TreeRenderer.drawEdge(gfx, e.from, e.to, viewport.panX, viewport.panY, viewport.zoom);
            }
            for (TreeLayout.Node n : tree.nodes) {
                TreeRenderer.drawNode(gfx, this.font, n,
                        snapshot.currentWorldDay(), snapshot.currentEpochMillis(),
                        n.id.equals(selectedId), collapsed.contains(n.id), generations.getOrDefault(n.id, 0),
                        viewport.panX, viewport.panY, viewport.zoom);
            }
        }
        gfx.disableScissor();
        gfx.fill(0, 0, this.width, CANVAS_TOP, 0xFF151A21);
        gfx.fill(0, canvasBottom(), this.width, this.height, 0xFF151A21);
        AnimalRecord focus = records.get(focusId);
        Component currentTitle = focus == null ? this.title : Component.translatable("familytree.screen.view.title", focus.name());
        gfx.text(this.font, this.font.plainSubstrByWidth(currentTitle.getString(), Math.max(1, this.width - 156)),
                76, 13, 0xFFFFFFFF);
        Component hint = Component.translatable("familytree.screen.tree.controls");
        gfx.text(this.font, this.font.plainSubstrByWidth(hint.getString(), Math.max(1, this.width - 64)),
                8, this.height - 25, 0xFFB0B7C0);
        Component panHint = Component.translatable("familytree.screen.tree.pan_controls");
        gfx.text(this.font, this.font.plainSubstrByWidth(panHint.getString(), Math.max(1, this.width - 64)),
                8, this.height - 13, 0xFFB0B7C0);
        Component zoomLabel = Component.translatable("familytree.screen.tree.zoom", Math.round(viewport.zoom * 100));
        gfx.text(this.font, zoomLabel, this.width - this.font.width(zoomLabel) - 8,
                this.height - 14, 0xFFB0B7C0);
        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0 || !inCanvas(event.x(), event.y())) return false;
        TreeLayout.Node hit = findNodeAt(event.x(), event.y());
        if (hit != null) {
            boolean openTree = doubleClick && hit.id.equals(selectedId);
            selectedId = hit.id;
            updateControls();
            if (openTree && this.minecraft != null) {
                this.minecraft.setScreen(new FamilyTreeViewScreen(this, snapshot, hit.id));
                return true;
            }
        }
        dragging = true;
        lastMouseX = event.x();
        lastMouseY = event.y();
        return true;
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
            viewport.panX += (mouseX - lastMouseX);
            viewport.panY += (mouseY - lastMouseY);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!inCanvas(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        viewport.zoomAt(mouseX, mouseY, verticalAmount);
        return true;
    }

    @Override
    public void onClose() {
        dragging = false;
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private TreeLayout.Node findNodeAt(double mouseX, double mouseY) {
        if (tree == null) return null;
        for (TreeLayout.Node node : tree.nodes) {
            int x = (int) Math.round(node.x * viewport.zoom + viewport.panX);
            int y = (int) Math.round(node.y * viewport.zoom + viewport.panY);
            int w = TreeViewport.nodeWidth(viewport.zoom);
            int h = TreeViewport.nodeHeight(viewport.zoom);
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                return node;
            }
        }
        return null;
    }
}
