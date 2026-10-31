package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.network.payloads.RenamePetRequest;
import com.egakh.familytree.network.payloads.LocatePetRequest;
import com.egakh.familytree.network.payloads.WaypointRequest;
import com.egakh.familytree.client.integration.MapWaypoints;
import com.egakh.familytree.client.export.TreeImageExporter;
import com.egakh.familytree.client.export.PetPortraits;
import com.egakh.familytree.client.export.PortraitCapture;
import com.egakh.familytree.permissions.PetAccess;
import java.util.concurrent.CompletableFuture;
import com.egakh.familytree.client.platform.ClientTransport;
import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.breeding.BreedingPair;
import com.egakh.familytree.util.Genealogy;
import com.egakh.familytree.util.Kinship;
import com.egakh.familytree.util.KinshipText;
import com.egakh.familytree.util.HorseStatUnits.Trend;
import com.egakh.familytree.util.VariantText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FamilyTreeViewScreen extends FamilyTreeScreen {

    private final Screen parent;
    private FamilyTreeSnapshotPayload snapshot;
    private final UUID focusId;
    private final String aggregateSpeciesId;
    private final List<UUID> forestIds;
    private Kinship kinship;
    private UUID compareId;
    private Set<UUID> relationPath = Set.of();
    private Button planButton;

    private final Map<UUID, AnimalRecord> records = new HashMap<>();
    private final Map<UUID, List<UUID>> childIndex = new HashMap<>();
    private final Map<UUID, Integer> generations = new HashMap<>();
    private static final int CANVAS_TOP = 106;
    private final TreeViewport viewport = new TreeViewport();
    private final Set<UUID> collapsed = new HashSet<>();
    private TreeLayout.Result fullTree;
    private TreeLayout.Result tree;
    private UUID selectedId;
    private Button centerButton;
    private Button branchButton;
    private Button expandAllButton;
    private Button renameButton;
    private Button locateButton;
    private Button waypointButton;
    private Button exportButton;
    private Button parentsButton, folderButton;
    private java.nio.file.Path exportedFile;
    private Component exportStatus = Component.empty();
    private boolean exporting;
    private PortraitCapture portraitCapture;
    private int previousWidth;
    private int previousHeight;
    private boolean dragging = false;
    private double lastMouseX;
    private double lastMouseY;
    private int statsLeft, statsTop, statsRight, statsBottom;

    public FamilyTreeViewScreen(Screen parent, FamilyTreeSnapshotPayload snapshot, UUID focusId) {
        super(Component.translatable("familytree.screen.view.title",
                snapshot.records().stream().filter(r -> r.id().equals(focusId)).findFirst()
                        .map(AnimalRecord::name).orElse("?")));
        this.parent = parent;
        this.snapshot = snapshot;
        this.focusId = focusId;
        this.selectedId = focusId;
        this.aggregateSpeciesId = null;
        this.forestIds = null;
        loadRecords();
    }

    private FamilyTreeViewScreen(Screen parent, FamilyTreeSnapshotPayload snapshot, String aggregateSpeciesId, Component title) {
        super(title);
        this.parent = parent;
        this.snapshot = snapshot;
        this.focusId = null;
        this.aggregateSpeciesId = aggregateSpeciesId;
        this.forestIds = null;
        loadRecords();
    }

    private FamilyTreeViewScreen(Screen parent, FamilyTreeSnapshotPayload snapshot, UUID first, UUID second) {
        super(Component.translatable("familytree.kin.title", nameIn(snapshot, first), nameIn(snapshot, second)));
        this.parent = parent;
        this.snapshot = snapshot;
        this.focusId = null;
        this.aggregateSpeciesId = null;
        loadRecords();
        Set<UUID> shown = new java.util.LinkedHashSet<>(kinship.path(first, second));
        shown.add(first);
        shown.add(second);
        shown.removeIf(id -> !records.containsKey(id));
        this.forestIds = List.copyOf(shown);
        this.selectedId = first;
        this.compareId = second;
        updateRelation();
    }

    /** The two animals and everyone on the shortest routes to their nearest shared ancestors. */
    public static FamilyTreeViewScreen forRelationship(Screen parent, FamilyTreeSnapshotPayload snapshot, UUID first, UUID second) {
        return new FamilyTreeViewScreen(parent, snapshot, first, second);
    }

    private static String nameIn(FamilyTreeSnapshotPayload snapshot, UUID id) {
        return snapshot.records().stream().filter(r -> r.id().equals(id)).findFirst().map(AnimalRecord::name).orElse("?");
    }

    public static FamilyTreeViewScreen forSpecies(Screen parent, FamilyTreeSnapshotPayload snapshot,
                                                  String speciesId, Component title) {
        return new FamilyTreeViewScreen(parent, snapshot, speciesId, title);
    }

    public void applySnapshot(FamilyTreeSnapshotPayload updated) {
        snapshot = updated;
        records.clear(); childIndex.clear(); generations.clear();
        loadRecords();
        updateRelation();
        fullTree = null;
        collapsed.clear();
        clearWidgets();
        init();
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
        kinship = new Kinship(records);
    }

    private void updateRelation() {
        boolean comparing = compareId != null && selectedId != null && !compareId.equals(selectedId)
                && records.containsKey(compareId) && records.containsKey(selectedId);
        if (!comparing) compareId = null;
        relationPath = comparing ? kinship.path(selectedId, compareId) : Set.of();
    }

    @Override
    protected void init() {
        dragging = false;
        if (fullTree == null) {
            if (forestIds != null) {
                fullTree = TreeLayout.layoutForest(forestIds, records, childIndex);
            } else if (aggregateSpeciesId == null) {
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
        Component planLabel = Component.translatable("familytree.planner.button");
        int planWidth = planButtonWidth();
        planButton = this.addRenderableWidget(Button.builder(planLabel, button -> {
            if (selectedId != null && this.minecraft != null)
                this.minecraft.setScreen(new BreedingPlannerScreen(this, snapshot, selectedId, compareId));
        }).bounds(this.width - 76 - planWidth, 8, planWidth, 18).build());
        planButton.setTooltip(Tooltip.create(Component.translatable("familytree.planner.button.hint")));
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
        boolean maps = MapWaypoints.available();
        locateButton = this.addRenderableWidget(Button.builder(Component.translatable("familytree.locate.button"),
                button -> {
                    if (selectedId != null) {
                        ClientTransport.send(new LocatePetRequest(selectedId));
                        this.minecraft.setScreen(null);
                    }
                }).bounds(x, 56, maps ? 67 : 138, 18).build());
        waypointButton = null;
        if (maps) {
            waypointButton = this.addRenderableWidget(Button.builder(Component.translatable("familytree.waypoint.button"),
                    button -> {
                        AnimalRecord selected = records.get(selectedId);
                        if (selected == null) return;
                        MapWaypoints.request(selected);
                        this.minecraft.setScreen(null);
                    }).bounds(x + 71, 56, 67, 18).build());
            waypointButton.setTooltip(Tooltip.create(Component.translatable("familytree.waypoint.tooltip", MapWaypoints.targetNames())));
        }
        exportButton = this.addRenderableWidget(Button.builder(Component.translatable("familytree.export.button"),
                button -> exportTree()).bounds(x + 142, 56, 162, 18).build());
        exportButton.active = !exporting;
        parentsButton = addRenderableWidget(Button.builder(Component.translatable("familytree.parents.button"), button -> {
            AnimalRecord selected = records.get(selectedId);
            if (selected != null) minecraft.setScreen(new ParentLinksScreen(this, snapshot, selected));
        }).bounds(x, 80, 112, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("familytree.refresh"), button ->
                com.egakh.familytree.client.FamilyTreeClient.requestSnapshot(snapshot.viewingAll()))
                .bounds(x + 116, 80, 68, 18).build());
        folderButton = addRenderableWidget(Button.builder(Component.translatable("familytree.export.open_folder"), button -> {
            if (exportedFile == null) return;
            //? if >=26.3 {
            /*com.mojang.blaze3d.Blaze3D.openPath(exportedFile.getParent());
            *///?} else {
            net.minecraft.util.Util.getPlatform().openFile(exportedFile.getParent().toFile());
            //?}
        }).bounds(x + 188, 80, 116, 18).build());
        folderButton.active = exportedFile != null;
        updateControls();
    }

    private int canvasBottom() {
        return this.height - 32;
    }

    private boolean inCanvas(double x, double y) {
        return x >= 0 && x < this.width && y >= CANVAS_TOP && y < canvasBottom();
    }

    /** Fitting and centering keep cards above the stats panel. */
    private int treeBottom() {
        int panel = statsPanelHeight();
        return panel == 0 ? canvasBottom() : canvasBottom() - panel - 6;
    }

    private void fitTree() {
        if (tree != null) viewport.fit(tree, this.width, CANVAS_TOP, treeBottom());
    }

    private void centerSelected() {
        TreeLayout.Node selected = tree.byId.get(selectedId);
        if (selected == null) return;
        viewport.zoom = Math.max(1.0, viewport.zoom);
        viewport.center(selected.x + TreeLayout.NODE_WIDTH / 2.0,
                selected.y + TreeLayout.NODE_HEIGHT / 2.0, this.width, CANVAS_TOP, treeBottom());
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

    private int planButtonWidth() {
        return Math.max(64, Math.min(110, this.font.width(Component.translatable("familytree.planner.button")) + 12));
    }

    private void updateControls() {
        centerButton.active = selectedId != null && tree.byId.containsKey(selectedId);
        AnimalRecord selected = records.get(selectedId);
        planButton.active = selected != null && !selected.deceased() && BreedingPair.breeds(selected.speciesId());
        centerButton.setTooltip(selected == null ? null : Tooltip.create(
                Component.translatable("familytree.screen.tree.selected", selected.name())));
        branchButton.active = centerButton.active
                && fullTree.edges.stream().anyMatch(edge -> edge.from.id.equals(selectedId));
        branchButton.setMessage(Component.translatable(collapsed.contains(selectedId)
                ? "familytree.screen.tree.expand" : "familytree.screen.tree.collapse"));
        expandAllButton.active = !collapsed.isEmpty();
        boolean canManage = this.minecraft != null && this.minecraft.player != null
                && PetAccess.canManage(selected, this.minecraft.player.getUUID(), snapshot.mayManageAll());
        parentsButton.active = centerButton.active && canManage && ClientTransport.canSend(com.egakh.familytree.network.payloads.LinkParentsRequest.TYPE);
        renameButton.active = centerButton.active && canManage && ClientTransport.canSend(RenamePetRequest.TYPE);
        locateButton.active = centerButton.active && canManage && ClientTransport.canSend(LocatePetRequest.TYPE);
        if (waypointButton != null) waypointButton.active = centerButton.active && canManage
                && (ClientTransport.canSend(WaypointRequest.TYPE) || selected.lastSeen() != null);
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

    private void exportTree() {
        if (exporting || this.minecraft == null || tree == null) return;
        TreeLayout.Result shown = tree;
        try {
            TreeImageExporter.requireExportable(shown);
        } catch (IllegalArgumentException tooLarge) {
            exportStatus = Component.translatable("familytree.export.too_large");
            return;
        }
        // Cats, wolves, and horses keep their texture portraits. Every other species uses its in-game model.
        Map<String, java.awt.image.BufferedImage> portraits = new HashMap<>();
        List<AnimalRecord> models = new ArrayList<>();
        for (TreeLayout.Node node : shown.nodes) {
            String key = PortraitCapture.key(node.record);
            if (portraits.containsKey(key) || models.stream().anyMatch(pet -> PortraitCapture.key(pet).equals(key))) continue;
            var texture = PetPortraits.read(node.record);
            if (texture != null) portraits.put(key, texture);
            else if (PetFaceRenderer.hasFace(node.record)) models.add(node.record);
        }
        exporting = true;
        exportButton.active = false;
        exportStatus = Component.translatable("familytree.export.saving");
        if (models.isEmpty()) {
            writeExport(shown, portraits);
            return;
        }
        portraitCapture = new PortraitCapture(models, captured -> {
            portraitCapture = null;
            portraits.putAll(captured);
            writeExport(shown, portraits);
        });
    }

    private void writeExport(TreeLayout.Result shown, Map<String, java.awt.image.BufferedImage> portraits) {
        var client = this.minecraft;
        if (client == null) return;
        AnimalRecord focus = records.get(focusId);
        Component exportTitle = focus == null ? this.title : Component.translatable("familytree.screen.view.title", focus.name());
        TreeImageExporter.Picture picture = TreeImageExporter.prepare(shown, exportTitle.getString(),
                "Family Tree | modrinth.com/mod/familytree",
                pet -> exportDetail(pet),
                pet -> pet.deceased()
                        ? Component.translatable("familytree.export.remembered", pet.deathWorldDay() == null ? "?" : pet.deathWorldDay()).getString()
                        : Component.translatable("familytree.screen.status.alive").getString(),
                pet -> portraits.get(PortraitCapture.key(pet)),
                pet -> FamilyTreeClientSettings.showHorseStats() ? HorseStatsText.card(pet, records::get) : List.of(),
                VariantText::rare,
                new TreeImageExporter.Legend(Component.translatable("familytree.export.legend.higher").getString(),
                        Component.translatable("familytree.export.legend.lower").getString(),
                        Component.translatable("familytree.export.hearts").getString(),
                        Component.translatable("familytree.export.legend.rare").getString()));
        CompletableFuture.supplyAsync(() -> {
            try {
                return TreeImageExporter.save(picture, client.gameDirectory.toPath().resolve("screenshots/familytree"));
            } catch (java.io.IOException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }).whenComplete((path, failure) -> client.execute(() -> {
            exporting = false;
            exportButton.active = true;
            if (failure == null) {
                exportedFile = path;
                folderButton.active = true;
                exportStatus = Component.translatable("familytree.export.saved_file", path.getFileName().toString());
            } else exportStatus = Component.translatable("familytree.export.failed");
            if (failure != null) com.egakh.familytree.FamilyTreeMod.LOGGER.warn("Could not export family tree", failure);
        }));
    }

    private String exportDetail(AnimalRecord pet) {
        String generation = Component.translatable("familytree.node.generation", generations.getOrDefault(pet.id(), 1)).getString();
        Component variant = VariantText.label(pet);
        return variant == null ? generation : generation + " \u00B7 " + variant.getString();
    }

    @Override
    public void tick() {
        super.tick();
        if (portraitCapture != null && this.minecraft != null) portraitCapture.tick(this.minecraft, this.width);
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
                if (!onCanvas(Math.min(e.from.x, e.to.x), Math.min(e.from.y, e.to.y),
                        Math.abs(e.from.x - e.to.x) + TreeLayout.NODE_WIDTH, Math.abs(e.from.y - e.to.y) + TreeLayout.NODE_HEIGHT)) continue;
                if (relationPath.contains(e.from.id) && relationPath.contains(e.to.id)) continue;
                TreeRenderer.drawEdge(gfx, e.from, e.to, viewport.panX, viewport.panY, viewport.zoom);
            }
            // Highlighted links go on top, so crossing lines never hide the path.
            for (TreeLayout.Edge e : tree.edges) {
                if (!relationPath.contains(e.from.id) || !relationPath.contains(e.to.id)) continue;
                TreeRenderer.drawEdge(gfx, e.from, e.to, viewport.panX, viewport.panY, viewport.zoom,
                        TreeRenderer.RELATION_COLOR, viewport.zoom >= 0.65 ? 2 : 1);
            }
            for (TreeLayout.Node n : tree.nodes) {
                if (!onCanvas(n.x, n.y, TreeLayout.NODE_WIDTH, TreeLayout.NODE_HEIGHT)) continue;
                TreeRenderer.drawNode(gfx, this.font, n,
                        snapshot.currentWorldDay(), snapshot.currentEpochMillis(),
                        n.id.equals(selectedId), n.id.equals(compareId) || relationPath.contains(n.id),
                        collapsed.contains(n.id), generations.getOrDefault(n.id, 0),
                        records::get, viewport.panX, viewport.panY, viewport.zoom);
            }
        }
        drawStatsPanel(gfx);
        gfx.disableScissor();
        gfx.fill(0, 0, this.width, CANVAS_TOP, 0xFF151A21);
        gfx.fill(0, canvasBottom(), this.width, this.height, 0xFF151A21);
        AnimalRecord focus = records.get(focusId);
        Component currentTitle = focus == null ? this.title : Component.translatable("familytree.screen.view.title", focus.name());
        gfx.text(this.font, this.font.plainSubstrByWidth(currentTitle.getString(), Math.max(1, this.width - 160 - planButtonWidth())),
                76, 13, 0xFFFFFFFF);
        Component hint = exportStatus.getString().isEmpty() ? Component.translatable("familytree.screen.tree.controls_compare") : exportStatus;
        gfx.text(this.font, this.font.plainSubstrByWidth(hint.getString(), Math.max(1, this.width - 64)),
                8, this.height - 25, 0xFFB0B7C0);
        Component panHint = Component.translatable("familytree.screen.tree.pan_controls");
        gfx.text(this.font, this.font.plainSubstrByWidth(panHint.getString(), Math.max(1, this.width - 64)),
                8, this.height - 13, 0xFFB0B7C0);
        Component zoomLabel = Component.translatable("familytree.screen.tree.zoom", Math.round(viewport.zoom * 100));
        gfx.text(this.font, zoomLabel, this.width - this.font.width(zoomLabel) - 8,
                this.height - 14, 0xFFB0B7C0);
        if (portraitCapture != null) portraitCapture.draw(gfx, this.font, this.width, CANVAS_TOP, canvasBottom());
        renderWidgets(gfx, mouseX, mouseY, delta);
    }

    /** Large farm forests hold thousands of cards, so only cards on the canvas are drawn. */
    private boolean onCanvas(double x, double y, double width, double height) {
        double left = x * viewport.zoom + viewport.panX, top = y * viewport.zoom + viewport.panY;
        return left < this.width && left + width * viewport.zoom >= 0
                && top < canvasBottom() && top + height * viewport.zoom >= CANVAS_TOP;
    }

    /** The relationship between the selected card and the Shift-clicked one, wrapped to the panel width. */
    private List<net.minecraft.util.FormattedCharSequence> relationLines() {
        AnimalRecord a = records.get(selectedId), b = records.get(compareId);
        if (a == null || b == null || tree == null) return List.of();
        int width = Math.max(80, Math.min(this.width - 28, 420));
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(this.font.split(KinshipText.describe(kinship.relation(a.id(), b.id()), a.name(), b.name()), width));
        if (BreedingPair.canBreed(a.speciesId(), b.speciesId())) {
            lines.addAll(this.font.split(KinshipText.inbreeding(kinship.foalInbreeding(a.id(), b.id())), width));
            if (!a.deceased() && !b.deceased())
                lines.addAll(this.font.split(Component.translatable("familytree.kin.plan_hint"), width));
        }
        lines.addAll(this.font.split(Component.translatable("familytree.kin.compare_hint"), width));
        return lines;
    }

    private int relationPanelHeight() {
        List<net.minecraft.util.FormattedCharSequence> lines = relationLines();
        return lines.isEmpty() ? 0 : (lines.size() + 1) * (this.font.lineHeight + 2) + 8;
    }

    private void drawRelationPanel(GuiGraphicsExtractor gfx) {
        List<net.minecraft.util.FormattedCharSequence> lines = relationLines();
        AnimalRecord a = records.get(selectedId), b = records.get(compareId);
        int lineHeight = this.font.lineHeight + 2;
        String title = Component.translatable("familytree.kin.title", a.name(), b.name()).getString();
        int contentWidth = this.font.width(title);
        for (var line : lines) contentWidth = Math.max(contentWidth, this.font.width(line));
        contentWidth = Math.min(contentWidth, Math.max(80, this.width - 28));
        statsLeft = 8;
        statsBottom = canvasBottom() - 6;
        statsTop = Math.max(CANVAS_TOP + 4, statsBottom - relationPanelHeight());
        statsRight = statsLeft + contentWidth + 12;
        gfx.fill(statsLeft, statsTop, statsRight, statsBottom, 0xE8151A21);
        gfx.fill(statsLeft, statsTop, statsRight, statsTop + 1, TreeRenderer.RELATION_COLOR);
        int y = statsTop + 5;
        gfx.text(this.font, this.font.plainSubstrByWidth(title, contentWidth), statsLeft + 6, y, 0xFFFFD24A);
        for (int i = 0; i < lines.size(); i++) {
            y += lineHeight;
            gfx.text(this.font, lines.get(i), statsLeft + 6, y, i == 0 ? 0xFFFFFFFF : 0xFFB0B7C0);
        }
    }

    private AnimalRecord statsSubject() {
        AnimalRecord selected = records.get(selectedId);
        if (tree == null || !tree.byId.containsKey(selectedId)) return null;
        return showsStats(selected) || VariantText.has(selected) ? selected : null;
    }

    private boolean showsStats(AnimalRecord pet) {
        return FamilyTreeClientSettings.showHorseStats() && HorseStatsText.has(pet)
                && !HorseStatsText.details(pet, records::get).isEmpty();
    }

    private List<HorseStatsText.DetailLine> statLines(AnimalRecord pet) {
        return showsStats(pet) ? HorseStatsText.details(pet, records::get) : List.of();
    }

    private List<Component> variantLines(AnimalRecord pet) {
        Component label = VariantText.label(pet);
        if (label == null) return List.of();
        List<Component> lines = new ArrayList<>();
        MutableComponent line = Component.translatable("familytree.variant.line", label);
        if (VariantText.rare(pet)) line.append(Component.literal(" " + VariantText.RARE_MARKER + " ")
                .append(Component.translatable("familytree.variant.rare"))
                .withStyle(style -> style.withColor(TextColor.fromRgb(VariantText.RARE_COLOR & 0xFFFFFF))));
        lines.add(line);
        Component parents = VariantText.parents(pet, records::get);
        if (parents != null) lines.add(parents);
        Component note = VariantText.note(pet, records::get);
        if (note != null) lines.add(note);
        return lines;
    }

    private List<HorseStatsText.Segment> compactSegments(AnimalRecord pet) {
        List<HorseStatsText.Segment> segments = new ArrayList<>(showsStats(pet) ? HorseStatsText.card(pet, records::get) : List.of());
        Component label = VariantText.label(pet);
        if (label != null) segments.add(new HorseStatsText.Segment(label.getString()
                + (VariantText.rare(pet) ? " " + VariantText.RARE_MARKER : ""), Trend.UNKNOWN));
        return segments;
    }

    private int fullStatsRows(AnimalRecord pet) {
        List<HorseStatsText.DetailLine> lines = statLines(pet);
        boolean marked = lines.stream().anyMatch(line -> !HorseStatsText.marker(line.trend()).isEmpty());
        return 1 + lines.size() + (marked ? 1 : 0) + variantLines(pet).size();
    }

    /** Short canvases, such as GUI scale 4 in a small window, get a one-line summary instead of the full panel. */
    private boolean compactStats(AnimalRecord pet) {
        return fullStatsRows(pet) * (this.font.lineHeight + 2) * 3 > canvasBottom() - CANVAS_TOP;
    }

    private int statsPanelHeight() {
        if (compareId != null) return relationPanelHeight();
        AnimalRecord pet = statsSubject();
        if (pet == null) return 0;
        int lineHeight = this.font.lineHeight + 2;
        if (!compactStats(pet)) return fullStatsRows(pet) * lineHeight + 8;
        int width = this.font.width(pet.name() + ": ") + TreeRenderer.statsWidth(this.font, compactSegments(pet));
        return (width <= this.width - 28 ? 1 : 2) * lineHeight + 8;
    }

    private void drawStatsPanel(GuiGraphicsExtractor gfx) {
        statsRight = statsLeft;
        if (compareId != null && relationPanelHeight() > 0) {
            drawRelationPanel(gfx);
            return;
        }
        AnimalRecord selected = statsSubject();
        if (selected == null) return;
        int lineHeight = this.font.lineHeight + 2;
        int maxWidth = Math.max(80, this.width - 16);
        statsLeft = 8;
        statsBottom = canvasBottom() - 6;
        statsTop = Math.max(CANVAS_TOP + 4, statsBottom - statsPanelHeight());
        if (compactStats(selected)) {
            String name = selected.name() + ": ";
            var segments = compactSegments(selected);
            int nameWidth = Math.min(this.font.width(name), maxWidth / 3);
            statsRight = Math.min(statsLeft + maxWidth,
                    statsLeft + 12 + nameWidth + TreeRenderer.statsWidth(this.font, segments));
            gfx.fill(statsLeft, statsTop, statsRight, statsBottom, 0xE8151A21);
            gfx.fill(statsLeft, statsTop, statsRight, statsTop + 1, 0xFF6FB3FF);
            gfx.text(this.font, this.font.plainSubstrByWidth(name, nameWidth), statsLeft + 6, statsTop + 5, 0xFFFFD24A);
            TreeRenderer.drawStats(gfx, this.font, segments, statsLeft + 6 + nameWidth, statsTop + 5,
                    statsRight - statsLeft - 12 - nameWidth, lineHeight, 0xFFFFFFFF);
            return;
        }
        List<HorseStatsText.DetailLine> lines = statLines(selected);
        List<Component> variants = variantLines(selected);
        String title = Component.translatable(lines.isEmpty() ? "familytree.variant.title" : "familytree.stats.title",
                selected.name()).getString();
        boolean marked = lines.stream().anyMatch(line -> !HorseStatsText.marker(line.trend()).isEmpty());
        Component legendText = Component.translatable("familytree.stats.legend",
                Component.literal(HorseStatsText.BEATS_MARKER).withStyle(style ->
                        style.withColor(net.minecraft.network.chat.TextColor.fromRgb(HorseStatsText.BEATS_COLOR & 0xFFFFFF))),
                Component.literal(HorseStatsText.TRAILS_MARKER).withStyle(style ->
                        style.withColor(net.minecraft.network.chat.TextColor.fromRgb(HorseStatsText.TRAILS_COLOR & 0xFFFFFF))));
        String legend = legendText.getString();
        int contentWidth = Math.min(maxWidth - 12, Math.max(this.font.width(title), marked ? this.font.width(legend) : 0));
        for (HorseStatsText.DetailLine line : lines) contentWidth = Math.min(maxWidth - 12,
                Math.max(contentWidth, detailWidth(line)));
        for (Component line : variants) contentWidth = Math.min(maxWidth - 12, Math.max(contentWidth, this.font.width(line)));
        statsRight = statsLeft + contentWidth + 12;
        gfx.fill(statsLeft, statsTop, statsRight, statsBottom, 0xE8151A21);
        gfx.fill(statsLeft, statsTop, statsRight, statsTop + 1, 0xFF6FB3FF);
        int x = statsLeft + 6;
        int y = statsTop + 5;
        gfx.text(this.font, this.font.plainSubstrByWidth(title, contentWidth), x, y, 0xFFFFD24A);
        for (HorseStatsText.DetailLine line : lines) {
            y += lineHeight;
            int cursor = x;
            gfx.text(this.font, this.font.plainSubstrByWidth(line.text(), contentWidth), cursor, y, 0xFFFFFFFF);
            cursor += this.font.width(line.text());
            String marker = HorseStatsText.marker(line.trend());
            if (!marker.isEmpty()) {
                gfx.text(this.font, marker, cursor + 4, y, HorseStatsText.markerColor(line.trend()));
                cursor += 4 + this.font.width(marker);
            }
            if (!line.parents().isEmpty() && cursor + 8 < x + contentWidth)
                gfx.text(this.font, this.font.plainSubstrByWidth(line.parents(), x + contentWidth - cursor - 8),
                        cursor + 8, y, 0xFFB0B7C0);
        }
        if (marked) {
            y += lineHeight;
            if (this.font.width(legendText) <= contentWidth) gfx.text(this.font, legendText, x, y, 0xFFB0B7C0);
            else gfx.text(this.font, this.font.plainSubstrByWidth(legend, contentWidth), x, y, 0xFFB0B7C0);
        }
        for (int i = 0; i < variants.size(); i++) {
            y += lineHeight;
            Component line = variants.get(i);
            int color = i == 0 ? 0xFFFFFFFF : 0xFFB0B7C0;
            if (this.font.width(line) <= contentWidth) gfx.text(this.font, line, x, y, color);
            else gfx.text(this.font, this.font.plainSubstrByWidth(line.getString(), contentWidth), x, y, color);
        }
    }

    private int detailWidth(HorseStatsText.DetailLine line) {
        String marker = HorseStatsText.marker(line.trend());
        return this.font.width(line.text()) + (marker.isEmpty() ? 0 : 4 + this.font.width(marker))
                + (line.parents().isEmpty() ? 0 : 8 + this.font.width(line.parents()));
    }

    private boolean inStatsPanel(double x, double y) {
        return statsRight > statsLeft && x >= statsLeft && x < statsRight && y >= statsTop && y < statsBottom;
    }

    @Override
    protected boolean onMouseClick(double mouseX, double mouseY, int button, boolean doubleClick) {
        if (!isLeftButton(button) || !inCanvas(mouseX, mouseY)) return false;
        TreeLayout.Node hit = inStatsPanel(mouseX, mouseY) ? null : findNodeAt(mouseX, mouseY);
        if (hit != null && shiftClick) {
            if (hit.id.equals(compareId)) compareId = null;
            else if (!hit.id.equals(selectedId)) compareId = hit.id;
            updateRelation();
            updateControls();
            return true;
        }
        if (hit != null) {
            boolean openTree = doubleClick && hit.id.equals(selectedId);
            if (hit.id.equals(compareId)) compareId = selectedId;
            selectedId = hit.id;
            updateRelation();
            updateControls();
            if (openTree && this.minecraft != null) {
                this.minecraft.setScreen(new FamilyTreeViewScreen(this, snapshot, hit.id));
                return true;
            }
        }
        dragging = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    @Override
    protected void onMouseRelease(int button) {
        if (isLeftButton(button)) dragging = false;
    }

    @Override
    protected boolean onMouseDrag(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            viewport.panX += (mouseX - lastMouseX);
            viewport.panY += (mouseY - lastMouseY);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return false;
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
