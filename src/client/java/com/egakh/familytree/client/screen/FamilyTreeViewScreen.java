package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
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

    private final Map<UUID, AnimalRecord> records = new HashMap<>();
    private final Map<UUID, List<UUID>> childIndex = new HashMap<>();
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
    }

    @Override
    protected void init() {
        tree = TreeLayout.layout(focusId, records, childIndex);
        TreeLayout.Node focus = tree.byId.get(focusId);
        if (focus != null) {
            panX = this.width / 2.0 - (focus.x + TreeLayout.NODE_WIDTH / 2.0);
            panY = this.height / 2.0 - (focus.y + TreeLayout.NODE_HEIGHT / 2.0);
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
                    n.id.equals(focusId), panX, panY, zoom);
        }

        gfx.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
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
        zoom = Math.max(0.3, Math.min(2.5, zoom * factor));
        double scale = zoom / oldZoom;
        panX = mouseX - (mouseX - panX) * scale;
        panY = mouseY - (mouseY - panY) * scale;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
