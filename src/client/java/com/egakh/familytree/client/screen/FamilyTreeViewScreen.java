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
import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.platform.ClientTransport;
import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.breeding.BreedingPair;
import com.egakh.familytree.util.Genealogy;
import com.egakh.familytree.util.Kinship;
import com.egakh.familytree.util.KinshipText;
import com.egakh.familytree.util.PetNames;
import com.egakh.familytree.util.HorseStatUnits.Trend;
import com.egakh.familytree.util.VariantText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FamilyTreeViewScreen extends FamilyTreeScreen {

    private final Screen parent;
    private FamilyTreeSnapshotPayload snapshot;
    private final UUID focusId;
    private final String aggregateSpeciesId;
    private final List<UUID> forestIds;
    private final UUID relationFirst, relationSecond;
    private Kinship kinship;
    private UUID compareId;
    private Set<UUID> relationPath = Set.of();
    /** The comparison's sentences, worked out when the comparison changes rather than every frame. */
    private List<Component> relationText = List.of();
    /** Guidance under the relationship; it gives way on short canvases so the panel stays off the cards. */
    private List<Component> relationHints = List.of();
    private Button planButton;

    private final Map<UUID, AnimalRecord> records = new HashMap<>();
    private final Map<UUID, List<UUID>> childIndex = new HashMap<>();
    private final Map<UUID, Integer> generations = new HashMap<>();
    private final Map<UUID, TreeCard> cards = new HashMap<>();
    /** The height the last layout gave its cards, tried first so most layouts run once. */
    private int cardHeight = TreeLayout.NODE_HEIGHT;
    /** A species view shows its families a page at a time, so a farm of thousands opens at a readable zoom. */
    private static final int FOREST_PAGE_CARDS = 40;
    private int canvasTop = 106;
    private TreeToolbar.Layout toolbar;
    private final TreeViewport viewport = new TreeViewport();
    private List<List<UUID>> forestPages = List.of();
    /** The index of each page's first family, then the number of families. */
    private int[] pageFamilies = {0};
    private int page;
    private Button previousPage, nextPage;
    private TreeToolbar.Slot pageSlot;
    private EditBox find;
    private int findIndex;
    private final Set<UUID> olderShown = new HashSet<>();
    private final Set<UUID> youngerShown = new HashSet<>();
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
    private Component status = Component.empty();
    private boolean exporting;
    private PortraitCapture portraitCapture;
    private boolean dragging = false;
    private double lastMouseX;
    private double lastMouseY;
    private int statsLeft, statsTop, statsRight, statsBottom;

    public FamilyTreeViewScreen(Screen parent, FamilyTreeSnapshotPayload snapshot, UUID focusId) {
        super(Component.translatable("familytree.screen.view.title", nameIn(snapshot, focusId)));
        this.parent = parent;
        this.snapshot = snapshot;
        this.focusId = focusId;
        this.selectedId = focusId;
        this.aggregateSpeciesId = null;
        this.forestIds = null;
        this.relationFirst = this.relationSecond = null;
        loadRecords();
    }

    private FamilyTreeViewScreen(Screen parent, FamilyTreeSnapshotPayload snapshot, String aggregateSpeciesId, Component title) {
        super(title);
        this.parent = parent;
        this.snapshot = snapshot;
        this.focusId = null;
        this.aggregateSpeciesId = aggregateSpeciesId;
        this.forestIds = null;
        this.relationFirst = this.relationSecond = null;
        loadRecords();
    }

    private FamilyTreeViewScreen(Screen parent, FamilyTreeSnapshotPayload snapshot, UUID first, UUID second) {
        super(Component.translatable("familytree.kin.title", nameIn(snapshot, first), nameIn(snapshot, second)));
        this.parent = parent;
        this.snapshot = snapshot;
        this.focusId = null;
        this.aggregateSpeciesId = null;
        this.relationFirst = first;
        this.relationSecond = second;
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
        return snapshot.records().stream().filter(r -> r.id().equals(id)).findFirst()
                .map(r -> PetNames.isolate(r.name())).orElse("?");
    }

    /** Built from the current records, so a rename shows at once. Exports leave out the isolate marks. */
    private Component currentTitle(boolean isolated) {
        AnimalRecord focus = records.get(focusId);
        if (focus != null) return Component.translatable("familytree.screen.view.title", shown(focus.name(), isolated));
        AnimalRecord first = records.get(relationFirst), second = records.get(relationSecond);
        if (first != null && second != null)
            return Component.translatable("familytree.kin.title", shown(first.name(), isolated), shown(second.name(), isolated));
        return this.title;
    }

    private static String shown(String name, boolean isolated) {
        return isolated ? PetNames.isolate(name) : name;
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
        relationText = List.of();
        relationHints = List.of();
        if (!comparing) return;
        AnimalRecord a = records.get(selectedId), b = records.get(compareId);
        List<Component> text = new ArrayList<>();
        List<Component> hints = new ArrayList<>();
        text.add(KinshipText.describe(kinship.relation(a.id(), b.id()), PetNames.isolate(a.name()), PetNames.isolate(b.name())));
        if (BreedingPair.canBreed(a.speciesId(), b.speciesId())) {
            text.add(KinshipText.inbreeding(kinship.foalInbreeding(a.id(), b.id())));
            if (!a.deceased() && !b.deceased()) hints.add(Component.translatable("familytree.kin.plan_hint"));
        }
        hints.add(Component.translatable("familytree.kin.compare_hint"));
        relationText = text;
        relationHints = hints;
    }

    @Override
    protected void init() {
        dragging = false;
        // Another screen may have renamed an animal or changed what cards show.
        updateRelation();
        double centreX = Double.NaN, centreY = 0;
        if (fullTree != null && toolbar != null) {
            centreX = (canvasRight() / 2.0 - viewport.panX) / viewport.zoom;
            centreY = ((canvasTop + canvasBottom()) / 2.0 - viewport.panY) / viewport.zoom;
        }
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
        if (fullTree == null) buildTree();
        else refreshCards();
        addTools();
        if (Double.isNaN(centreX)) fitTree();
        else viewport.center(centreX, centreY, 0, canvasRight(), canvasTop, canvasBottom());
        updateControls();
    }

    /** Lays the tool buttons out to fit the window: rows above the tree, or a column beside it in a short window. */
    private void addTools() {
        boolean maps = MapWaypoints.available();
        boolean paged = forestPages.size() > 1;
        int pageText = 0;
        for (int i = 0; paged && i < forestPages.size(); i++) pageText = Math.max(pageText, this.font.width(pageLabel(i)));
        int[] widths = java.util.stream.IntStream.of(
                toolWidth("familytree.screen.tree.fit"), toolWidth("familytree.screen.tree.center"),
                toolWidth("familytree.screen.tree.collapse", "familytree.screen.tree.expand"),
                toolWidth("familytree.screen.tree.expand_all"), toolWidth("familytree.locate.button"),
                maps ? toolWidth("familytree.waypoint.button") : -1, toolWidth("familytree.export.button"),
                toolWidth("familytree.parents.button"), toolWidth("familytree.refresh"),
                toolWidth("familytree.export.open_folder"),
                paged ? 48 + pageText + 8 : -1,
                aggregateSpeciesId != null ? 110 : -1).filter(width -> width > 0).toArray();
        toolbar = TreeToolbar.arrange(widths, this.width, this.height);
        canvasTop = toolbar.canvasTop();
        java.util.Iterator<TreeToolbar.Slot> slots = toolbar.slots().iterator();
        tool(slots.next(), "familytree.screen.tree.fit", b -> fitTree());
        centerButton = tool(slots.next(), "familytree.screen.tree.center", b -> centerSelected());
        branchButton = tool(slots.next(), "familytree.screen.tree.collapse", b -> toggleBranch());
        expandAllButton = tool(slots.next(), "familytree.screen.tree.expand_all", b -> {
            collapsed.clear();
            rebuildTree(null);
        });
        branchButton.setTooltip(Tooltip.create(Component.translatable("familytree.screen.tree.branch_hint")));
        locateButton = tool(slots.next(), "familytree.locate.button", button -> {
            if (selectedId != null) {
                ClientTransport.send(new LocatePetRequest(selectedId));
                leaveForWorld();
            }
        });
        waypointButton = null;
        if (maps) {
            waypointButton = tool(slots.next(), "familytree.waypoint.button", button -> {
                AnimalRecord selected = records.get(selectedId);
                if (selected == null) return;
                MapWaypoints.request(selected);
                leaveForWorld();
            });
            waypointButton.setTooltip(Tooltip.create(Component.translatable("familytree.waypoint.tooltip", MapWaypoints.targetNames())));
        }
        exportButton = tool(slots.next(), "familytree.export.button", button -> exportTree());
        exportButton.active = !exporting;
        parentsButton = tool(slots.next(), "familytree.parents.button", button -> {
            AnimalRecord selected = records.get(selectedId);
            if (selected != null) minecraft.setScreen(new ParentLinksScreen(this, snapshot, selected));
        });
        tool(slots.next(), "familytree.refresh", button ->
                com.egakh.familytree.client.FamilyTreeClient.requestSnapshot(snapshot.viewingAll()));
        folderButton = tool(slots.next(), "familytree.export.open_folder", button -> {
            if (exportedFile == null) return;
            //? if >=26.3 {
            /*com.mojang.blaze3d.Blaze3D.openPath(exportedFile.getParent());
            *///?} else {
            net.minecraft.util.Util.getPlatform().openFile(exportedFile.getParent().toFile());
            //?}
        });
        folderButton.active = exportedFile != null;
        previousPage = nextPage = null;
        pageSlot = null;
        if (paged) {
            TreeToolbar.Slot slot = slots.next();
            previousPage = addRenderableWidget(Button.builder(Component.literal("<"), button -> turnPage(-1))
                    .bounds(slot.x(), slot.y(), 20, 18).build());
            nextPage = addRenderableWidget(Button.builder(Component.literal(">"), button -> turnPage(1))
                    .bounds(slot.x() + slot.width() - 20, slot.y(), 20, 18).build());
            pageSlot = slot;
        }
        if (aggregateSpeciesId != null) {
            TreeToolbar.Slot slot = slots.next();
            String previous = find == null ? "" : find.getValue();
            find = new EditBox(this.font, slot.x() + 1, slot.y() + 1, slot.width() - 2, 16,
                    Component.translatable("familytree.screen.tree.find"));
            find.setMaxLength(64);
            find.setValue(previous);
            find.setHint(Component.translatable("familytree.screen.tree.find"));
            find.setResponder(text -> findNext(false));
            addRenderableWidget(find);
        }
    }

    private int toolWidth(String... keys) {
        int width = 0;
        for (String key : keys) width = Math.max(width, this.font.width(Component.translatable(key)));
        return Math.max(40, width + 16);
    }

    private Button tool(TreeToolbar.Slot slot, String key, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.translatable(key), action)
                .bounds(slot.x(), slot.y(), slot.width(), 18).build());
    }

    private boolean singlePetTree() {
        return focusId != null && aggregateSpeciesId == null && forestIds == null;
    }

    /** Lays out the whole tree for the current page and depth, then hides collapsed branches. */
    private void buildTree() {
        if (aggregateSpeciesId != null) paginate();
        fullTree = layoutAll(cardHeight);
        int height = buildCards(fullTree);
        if (height != fullTree.cardHeight) fullTree = layoutAll(height);
        cardHeight = height;
        tree = layoutShown(fullTree);
    }

    private void paginate() {
        List<UUID> includedIds = snapshot.records().stream()
                .filter(r -> aggregateSpeciesId.equals(r.speciesId()))
                .map(AnimalRecord::id)
                .toList();
        List<List<UUID>> families = TreeLayout.families(includedIds, records, childIndex);
        forestPages = TreeLayout.pages(families, FOREST_PAGE_CARDS);
        pageFamilies = new int[forestPages.size() + 1];
        for (int i = 0, family = 0; i < forestPages.size(); i++) {
            pageFamilies[i] = family;
            for (int cards = 0; cards < forestPages.get(i).size(); ) cards += families.get(family++).size();
            pageFamilies[i + 1] = family;
        }
        page = Math.max(0, Math.min(page, forestPages.size() - 1));
    }

    private TreeLayout.Result layoutAll(int height) {
        if (forestIds != null) return TreeLayout.layoutForest(forestIds, records, childIndex, height);
        if (aggregateSpeciesId == null)
            return TreeLayout.layout(focusId, records, childIndex, Set.of(), olderShown, youngerShown, height);
        return TreeLayout.layoutForest(forestPages.isEmpty() ? List.of() : forestPages.get(page), records, childIndex, height);
    }

    private TreeLayout.Result layoutShown(TreeLayout.Result full) {
        if (collapsed.isEmpty()) return full;
        List<UUID> visible = TreeLayout.visibleIds(full, collapsed);
        if (singlePetTree()) {
            Set<UUID> hidden = new HashSet<>(full.byId.keySet());
            visible.forEach(hidden::remove);
            return TreeLayout.layout(focusId, records, childIndex, hidden, olderShown, youngerShown, full.cardHeight);
        }
        return TreeLayout.layoutForest(visible, records, childIndex, full.cardHeight);
    }

    /** Lays out every card's text and returns the height that fits the fullest one. */
    private int buildCards(TreeLayout.Result layout) {
        cards.clear();
        TreeCard.Measure measure = TreeCard.measure(this.font);
        for (TreeLayout.Node node : layout.nodes) cards.put(node.id, card(node.record, measure));
        return TreeCard.treeHeight(cards.values(), measure);
    }

    /** A rename or a settings change can change what cards show, and with it their height. */
    private void refreshCards() {
        if (buildCards(fullTree) != fullTree.cardHeight) rebuildTree(selectedId);
    }

    private TreeCard card(AnimalRecord record, TreeCard.Measure measure) {
        return new TreeCard(TreeCard.sources(record, generations.getOrDefault(record.id(), 0), snapshot.currentWorldDay(),
                records::get), PetFaceRenderer.hasFace(record), measure);
    }

    /** Text never draws a font pixel smaller than a screen pixel. */
    private double minTextScale() {
        if (this.minecraft == null || this.width <= 0) return 0.5;
        double guiScale = (double) this.minecraft.getWindow().getWidth() / this.width;
        return Math.min(1.0, 1.0 / Math.max(1.0, guiScale));
    }

    /** Closes the screen so the player sees the answer in chat; the open-tree key brings this tree back. */
    private void leaveForWorld() {
        if (this.minecraft == null) return;
        FamilyTreeClient.returnLater(this);
        this.minecraft.setScreen(null);
        FamilyTreeClient.showReturnHint(this.minecraft);
    }

    private int canvasRight() {
        return toolbar == null ? this.width : toolbar.canvasRight();
    }

    private int canvasBottom() {
        return toolbar == null ? this.height - 32 : toolbar.canvasBottom();
    }

    private boolean inCanvas(double x, double y) {
        return x >= 0 && x < canvasRight() && y >= canvasTop && y < canvasBottom();
    }

    /** Fitting and centering keep cards above the stats panel, unless the panel would leave too little room. */
    private int treeBottom() {
        int panel = statsPanelHeight();
        int reserved = canvasBottom() - panel - 6;
        return panel == 0 || reserved - canvasTop < (canvasBottom() - canvasTop) * 3 / 5 ? canvasBottom() : reserved;
    }

    private void fitTree() {
        if (tree == null) return;
        TreeLayout.Node anchor = tree.byId.get(selectedId);
        if (anchor == null && focusId != null) anchor = tree.byId.get(focusId);
        viewport.fit(tree, anchor, 0, canvasRight(), canvasTop, treeBottom());
    }

    private void centerSelected() {
        TreeLayout.Node selected = tree.byId.get(selectedId);
        if (selected == null) return;
        viewport.zoom = Math.max(1.0, viewport.zoom);
        viewport.center(selected.x + TreeLayout.NODE_WIDTH / 2.0,
                selected.y + tree.cardHeight / 2.0, 0, canvasRight(), canvasTop, treeBottom());
    }

    private void toggleBranch() {
        if (selectedId == null) return;
        if (!collapsed.remove(selectedId)) collapsed.add(selectedId);
        rebuildTree(selectedId);
    }

    /** Lays the tree out again while the card {@code anchorId}, the selection, or any shown card stays still on screen. */
    private void rebuildTree(UUID anchorId) {
        TreeLayout.Node before = anchorId == null ? null : tree.byId.get(anchorId);
        if (before == null) before = tree.byId.get(selectedId);
        if (before == null && !tree.nodes.isEmpty()) before = tree.nodes.get(0);
        buildTree();
        TreeLayout.Node after = before == null ? null : tree.byId.get(before.id);
        if (after != null) viewport.follow(before, after);
        if (!tree.byId.containsKey(selectedId)) selectedId = null;
        if (!tree.byId.containsKey(compareId)) compareId = null;
        updateRelation();
        updateControls();
    }

    private void turnPage(int step) {
        int target = Math.max(0, Math.min(forestPages.size() - 1, page + step));
        if (target == page) return;
        showPage(target);
        fitTree();
    }

    private void showPage(int target) {
        page = target;
        collapsed.clear();
        selectedId = null;
        compareId = null;
        buildTree();
        updateRelation();
        updateControls();
    }

    /** Finds animals of this species by name across every page; Enter moves to the next match. */
    private void findNext(boolean advance) {
        String query = find == null ? "" : find.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            status = Component.empty();
            return;
        }
        List<UUID> matches = new ArrayList<>();
        for (List<UUID> ids : forestPages)
            for (UUID id : ids)
                if (records.get(id).name().toLowerCase(Locale.ROOT).contains(query)) matches.add(id);
        if (matches.isEmpty()) {
            status = Component.translatable("familytree.screen.tree.find_none", find.getValue().trim());
            return;
        }
        findIndex = advance ? (findIndex + 1) % matches.size() : 0;
        UUID match = matches.get(findIndex);
        for (int i = 0; i < forestPages.size(); i++) {
            if (i != page && forestPages.get(i).contains(match)) showPage(i);
        }
        if (!tree.byId.containsKey(match)) {
            collapsed.clear();
            buildTree();
        }
        selectedId = match;
        updateRelation();
        updateControls();
        centerSelected();
        status = Component.translatable("familytree.screen.tree.find_result", findIndex + 1, matches.size());
    }

    @Override
    protected boolean onKeyPress(int key) {
        boolean enter = key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN
                || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER;
        if (enter && find != null && find.isFocused()) {
            findNext(true);
            return true;
        }
        return false;
    }

    private int planButtonWidth() {
        return Math.max(64, Math.min(110, this.font.width(Component.translatable("familytree.planner.button")) + 12));
    }

    private void updateControls() {
        centerButton.active = selectedId != null && tree.byId.containsKey(selectedId);
        AnimalRecord selected = records.get(selectedId);
        planButton.active = selected != null && !selected.deceased() && BreedingPair.breeds(selected.speciesId());
        centerButton.setTooltip(selected == null ? null : Tooltip.create(
                Component.translatable("familytree.screen.tree.selected", PetNames.isolate(selected.name()))));
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
        if (previousPage != null) previousPage.active = page > 0;
        if (nextPage != null) nextPage.active = page < forestPages.size() - 1;
    }

    public void applyRename(AnimalRecord updated) {
        AnimalRecord record = records.get(updated.id());
        if (record != null) {
            record.rename(updated.originalName(), updated.nameAutogenerated());
            record.setTreeName(updated.treeName());
        }
        if (parent instanceof FamilyTreeViewScreen previous) previous.applyRename(updated);
        updateRelation();
        if (centerButton != null) updateControls();
        if (fullTree != null && this.font != null && fullTree.byId.containsKey(updated.id())) refreshCards();
    }

    private void exportTree() {
        if (exporting || this.minecraft == null || tree == null) return;
        // Exported cards keep their fixed size, whatever height the screen gives its cards.
        TreeLayout.Result shown = tree.cardHeight == TreeLayout.NODE_HEIGHT ? tree : layoutShown(layoutAll(TreeLayout.NODE_HEIGHT));
        try {
            TreeImageExporter.requireExportable(shown);
        } catch (IllegalArgumentException tooLarge) {
            status = singlePetTree() ? Component.translatable("familytree.export.too_large")
                    : Component.translatable("familytree.export.family_too_large", shown.nodes.size());
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
        status = Component.translatable("familytree.export.saving");
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
        String fileSubject = focus == null ? currentTitle(false).getString() : focus.name();
        TreeImageExporter.Picture picture = TreeImageExporter.prepare(shown, currentTitle(false).getString(),
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
                return TreeImageExporter.save(picture, client.gameDirectory.toPath().resolve("screenshots/familytree"), fileSubject);
            } catch (java.io.IOException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }).whenComplete((path, failure) -> client.execute(() -> {
            exporting = false;
            exportButton.active = true;
            if (failure == null) {
                exportedFile = path;
                folderButton.active = true;
                status = Component.translatable("familytree.export.saved_file", path.getFileName().toString());
            } else status = Component.translatable("familytree.export.failed");
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
        if (portraitCapture != null && this.minecraft != null) portraitCapture.tick(this.minecraft, canvasRight());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, this.width, this.height, 0xCC0F1115);
        gfx.enableScissor(0, canvasTop, canvasRight(), canvasBottom());
        Marker hovered = null;
        TreeLayout.Node hoveredCard = hoveredNode(mouseX, mouseY);
        boolean hoveredCut = false;
        if (tree == null || tree.nodes.isEmpty()) {
            gfx.centeredText(this.font, Component.translatable("familytree.screen.empty"),
                    canvasRight() / 2, this.height / 2, 0xFFCCCCCC);
        } else {
            for (TreeLayout.Edge e : tree.edges) {
                if (!routeOnCanvas(e)) continue;
                if (relationPath.contains(e.from.id) && relationPath.contains(e.to.id)) continue;
                TreeRenderer.drawEdge(gfx, e, viewport.panX, viewport.panY, viewport.zoom);
            }
            // Highlighted links go on top, so crossing lines never hide the path.
            for (TreeLayout.Edge e : tree.edges) {
                if (!relationPath.contains(e.from.id) || !relationPath.contains(e.to.id)) continue;
                TreeRenderer.drawEdge(gfx, e, viewport.panX, viewport.panY, viewport.zoom,
                        TreeRenderer.RELATION_COLOR, viewport.zoom >= 0.65 ? 2 : 1);
            }
            double minTextScale = minTextScale();
            TreeCard.Measure measure = TreeCard.measure(this.font);
            for (TreeLayout.Node n : tree.nodes) {
                if (!onCanvas(n.x, n.y, TreeLayout.NODE_WIDTH, tree.cardHeight)) continue;
                TreeCard card = cards.computeIfAbsent(n.id, id -> card(n.record, measure));
                boolean cut = TreeRenderer.drawNode(gfx, this.font, n, card, tree.cardHeight,
                        n.id.equals(selectedId), n.id.equals(compareId) || relationPath.contains(n.id),
                        collapsed.contains(n.id), viewport.panX, viewport.panY, viewport.zoom, minTextScale);
                if (n == hoveredCard) hoveredCut = cut;
            }
            for (Marker marker : markers()) {
                boolean over = inCanvas(mouseX, mouseY) && marker.contains(mouseX, mouseY);
                if (over) hovered = marker;
                drawMarker(gfx, marker, over);
            }
        }
        //? if <1.21.6 {
        /*// Before 1.21.6 the GUI keeps a depth buffer, and card portraits sit above z=0.
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 400);
        *///?}
        drawStatsPanel(gfx);
        //? if <1.21.6 {
        /*gfx.pose().popPose();
        *///?}
        gfx.disableScissor();
        gfx.fill(0, 0, this.width, canvasTop, 0xFF151A21);
        if (toolbar != null && toolbar.side()) gfx.fill(canvasRight(), canvasTop, this.width, canvasBottom(), 0xFF151A21);
        gfx.fill(0, canvasBottom(), this.width, this.height, 0xFF151A21);
        gfx.text(this.font, TreeRenderer.ordered(this.font.plainSubstrByWidth(currentTitle(true).getString(),
                Math.max(1, this.width - 160 - planButtonWidth()))), 76, 13, 0xFFFFFFFF);
        if (pageSlot != null) {
            gfx.centeredText(this.font, pageLabel(page), pageSlot.x() + pageSlot.width() / 2, pageSlot.y() + 5, 0xFFE0E6EE);
        }
        Component hint = hovered != null
                ? Component.translatable(hovered.older() ? "familytree.screen.tree.show_older" : "familytree.screen.tree.show_younger")
                : status.getString().isEmpty() ? Component.translatable("familytree.screen.tree.controls_compare") : status;
        Component zoomLabel = Component.translatable("familytree.screen.tree.zoom", Math.round(viewport.zoom * 100));
        int hintWidth = Math.max(1, this.width - 24 - this.font.width(zoomLabel));
        if (toolbar != null && toolbar.compactFooter()) {
            gfx.text(this.font, this.font.plainSubstrByWidth(hint.getString(), hintWidth), 8, this.height - 12, 0xFFB0B7C0);
        } else {
            gfx.text(this.font, this.font.plainSubstrByWidth(hint.getString(), hintWidth), 8, this.height - 25, 0xFFB0B7C0);
            Component panHint = Component.translatable("familytree.screen.tree.pan_controls");
            gfx.text(this.font, this.font.plainSubstrByWidth(panHint.getString(), hintWidth), 8, this.height - 13, 0xFFB0B7C0);
        }
        gfx.text(this.font, zoomLabel, this.width - this.font.width(zoomLabel) - 8,
                this.height - (toolbar != null && toolbar.compactFooter() ? 12 : 14), 0xFFB0B7C0);
        if (portraitCapture != null) portraitCapture.draw(gfx, this.font, canvasRight(), canvasTop, canvasBottom());
        renderWidgets(gfx, mouseX, mouseY, delta);
        if (hoveredCard != null && hoveredCut && hovered == null && portraitCapture == null)
            drawCardTooltip(gfx, cards.get(hoveredCard.id), mouseX, mouseY);
    }

    private TreeLayout.Node hoveredNode(double mouseX, double mouseY) {
        if (dragging || !inCanvas(mouseX, mouseY) || inStatsPanel(mouseX, mouseY)) return null;
        return findNodeAt(mouseX, mouseY);
    }

    /** Text a card had to shorten, such as a long death message, reads in full on hover. */
    private void drawCardTooltip(GuiGraphicsExtractor gfx, TreeCard card, int mouseX, int mouseY) {
        if (card == null) return;
        int width = Math.max(120, Math.min(260, this.width / 2));
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        for (String line : card.fullLines()) lines.addAll(BidiLines.split(this.font, Component.literal(line), width));
        //? if >=1.21.6 {
        gfx.setTooltipForNextFrame(lines, mouseX, mouseY);
        //?} else {
        /*gfx.renderTooltip(this.font, lines, mouseX, mouseY);
        *///?}
    }

    private Component pageLabel(int index) {
        int first = pageFamilies[index] + 1, last = pageFamilies[index + 1], total = pageFamilies[forestPages.size()];
        return first == last ? Component.translatable("familytree.screen.tree.page_one", first, total)
                : Component.translatable("familytree.screen.tree.page", first, last, total);
    }

    /** A tab above a card whose older generations are left out, or below one whose younger generations are. */
    private record Marker(UUID id, boolean older, String label, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private List<Marker> markers() {
        List<Marker> markers = new ArrayList<>();
        if (tree == null) return markers;
        int height = this.font.lineHeight + 3;
        for (int pass = 0; pass < 2; pass++) {
            boolean older = pass == 0;
            for (Map.Entry<UUID, Integer> entry : (older ? tree.olderHidden : tree.youngerHidden).entrySet()) {
                TreeLayout.Node node = tree.byId.get(entry.getKey());
                if (node == null || !onCanvas(node.x, node.y - TreeLayout.WRAP_GAP, TreeLayout.NODE_WIDTH,
                        tree.cardHeight + TreeLayout.WRAP_GAP * 2)) continue;
                String label = (older ? HorseStatsText.BEATS_MARKER : HorseStatsText.TRAILS_MARKER) + " +" + entry.getValue();
                int width = this.font.width(label) + 8;
                int cardX = (int) Math.round(node.x * viewport.zoom + viewport.panX);
                int cardY = (int) Math.round(node.y * viewport.zoom + viewport.panY);
                int x = cardX + TreeViewport.nodeWidth(viewport.zoom) / 2 - width / 2;
                int y = older ? cardY - height - 1 : cardY + TreeRenderer.scaled(tree.cardHeight, viewport.zoom) + 1;
                markers.add(new Marker(node.id, older, label, x, y, width, height));
            }
        }
        return markers;
    }

    private void drawMarker(GuiGraphicsExtractor gfx, Marker marker, boolean hovered) {
        int border = hovered ? 0xFFFFD24A : 0xFFD9B25A;
        gfx.fill(marker.x(), marker.y(), marker.x() + marker.width(), marker.y() + marker.height(), border);
        gfx.fill(marker.x() + 1, marker.y() + 1, marker.x() + marker.width() - 1, marker.y() + marker.height() - 1,
                hovered ? 0xFF2A3440 : 0xFF151A21);
        gfx.text(this.font, marker.label(), marker.x() + 4, marker.y() + 2, border);
    }

    /** Large farm forests hold thousands of cards, so only cards on the canvas are drawn. */
    private boolean onCanvas(double x, double y, double width, double height) {
        double left = x * viewport.zoom + viewport.panX, top = y * viewport.zoom + viewport.panY;
        return left < canvasRight() && left + width * viewport.zoom >= 0
                && top < canvasBottom() && top + height * viewport.zoom >= canvasTop;
    }

    private boolean routeOnCanvas(TreeLayout.Edge edge) {
        double[] route = edge.route();
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < route.length; i += 2) {
            minX = Math.min(minX, route[i]);
            maxX = Math.max(maxX, route[i]);
            minY = Math.min(minY, route[i + 1]);
            maxY = Math.max(maxY, route[i + 1]);
        }
        return onCanvas(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** The relationship between the selected card and the Shift-clicked one, wrapped to the panel width. */
    private List<net.minecraft.util.FormattedCharSequence> relationLines(boolean full) {
        if (relationText.isEmpty() || tree == null) return List.of();
        int width = Math.max(80, Math.min(canvasRight() - 28, 420));
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        for (Component text : relationText) lines.addAll(BidiLines.split(this.font, text, width));
        if (full) for (Component text : relationHints) lines.addAll(BidiLines.split(this.font, text, width));
        return lines;
    }

    /** Like the stats panel, the relationship panel keeps to a third of the canvas: then it drops its title and hints. */
    private boolean compactRelation() {
        return (relationLines(true).size() + 1) * (this.font.lineHeight + 2) * 3 > canvasBottom() - canvasTop;
    }

    private int relationPanelHeight() {
        boolean compact = compactRelation();
        List<net.minecraft.util.FormattedCharSequence> lines = relationLines(!compact);
        return lines.isEmpty() ? 0 : (lines.size() + (compact ? 0 : 1)) * (this.font.lineHeight + 2) + 8;
    }

    private void drawRelationPanel(GuiGraphicsExtractor gfx) {
        boolean compact = compactRelation();
        List<net.minecraft.util.FormattedCharSequence> lines = relationLines(!compact);
        AnimalRecord a = records.get(selectedId), b = records.get(compareId);
        int lineHeight = this.font.lineHeight + 2;
        String title = Component.translatable("familytree.kin.title",
                PetNames.isolate(a.name()), PetNames.isolate(b.name())).getString();
        int contentWidth = compact ? 0 : this.font.width(title);
        for (var line : lines) contentWidth = Math.max(contentWidth, this.font.width(line));
        contentWidth = Math.min(contentWidth, Math.max(80, canvasRight() - 28));
        placePanel(contentWidth + 12, relationPanelHeight());
        gfx.fill(statsLeft, statsTop, statsRight, statsBottom, 0xE8151A21);
        gfx.fill(statsLeft, statsTop, statsRight, statsTop + 1, TreeRenderer.RELATION_COLOR);
        int y = statsTop + 5;
        if (!compact) {
            gfx.text(this.font, TreeRenderer.ordered(this.font.plainSubstrByWidth(title, contentWidth)), statsLeft + 6, y, 0xFFFFD24A);
            y += lineHeight;
        }
        for (int i = 0; i < lines.size(); i++) {
            gfx.text(this.font, lines.get(i), statsLeft + 6, y, i == 0 ? 0xFFFFFFFF : 0xFFB0B7C0);
            y += lineHeight;
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
        return fullStatsRows(pet) * (this.font.lineHeight + 2) * 3 > canvasBottom() - canvasTop;
    }

    private int statsPanelHeight() {
        if (compareId != null) return relationPanelHeight();
        AnimalRecord pet = statsSubject();
        if (pet == null) return 0;
        int lineHeight = this.font.lineHeight + 2;
        if (!compactStats(pet)) return fullStatsRows(pet) * lineHeight + 8;
        int width = this.font.width(pet.name() + ": ") + TreeRenderer.statsWidth(this.font, compactSegments(pet));
        return (width <= canvasRight() - 28 ? 1 : 2) * lineHeight + 8;
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
        int maxWidth = Math.max(80, canvasRight() - 16);
        if (compactStats(selected)) {
            String name = selected.name() + ": ";
            var segments = compactSegments(selected);
            int nameWidth = Math.min(this.font.width(name), maxWidth / 3);
            placePanel(Math.min(maxWidth, 12 + nameWidth + TreeRenderer.statsWidth(this.font, segments)), statsPanelHeight());
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
                PetNames.isolate(selected.name())).getString();
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
        placePanel(contentWidth + 12, statsPanelHeight());
        gfx.fill(statsLeft, statsTop, statsRight, statsBottom, 0xE8151A21);
        gfx.fill(statsLeft, statsTop, statsRight, statsTop + 1, 0xFF6FB3FF);
        int x = statsLeft + 6;
        int y = statsTop + 5;
        gfx.text(this.font, TreeRenderer.ordered(this.font.plainSubstrByWidth(title, contentWidth)), x, y, 0xFFFFD24A);
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

    /** Keeps the panel off the selected and compared cards when another corner of the canvas is free. */
    private void placePanel(int width, int height) {
        List<PanelPlacement.Box> shown = new ArrayList<>(2);
        for (UUID id : new UUID[]{selectedId, compareId}) {
            TreeLayout.Node node = id == null || tree == null ? null : tree.byId.get(id);
            if (node == null) continue;
            int x = (int) Math.round(node.x * viewport.zoom + viewport.panX);
            int y = (int) Math.round(node.y * viewport.zoom + viewport.panY);
            shown.add(new PanelPlacement.Box(x, y, x + TreeViewport.nodeWidth(viewport.zoom),
                    y + TreeRenderer.scaled(tree.cardHeight, viewport.zoom)));
        }
        PanelPlacement.Box box = PanelPlacement.place(new PanelPlacement.Box(0, canvasTop, canvasRight(), canvasBottom()),
                width, height, 6, shown);
        statsLeft = box.left();
        statsTop = box.top();
        statsRight = box.right();
        statsBottom = box.bottom();
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
        if (!inStatsPanel(mouseX, mouseY)) {
            for (Marker marker : markers()) {
                if (!marker.contains(mouseX, mouseY)) continue;
                (marker.older() ? olderShown : youngerShown).add(marker.id());
                rebuildTree(marker.id());
                return true;
            }
        }
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
            int h = TreeRenderer.scaled(tree.cardHeight, viewport.zoom);
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                return node;
            }
        }
        return null;
    }
}
