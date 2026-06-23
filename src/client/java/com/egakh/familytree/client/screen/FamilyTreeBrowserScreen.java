package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class FamilyTreeBrowserScreen extends Screen {
    private static WeakReference<FamilyTreeBrowserScreen> ACTIVE = new WeakReference<>(null);

    private FamilyTreeSnapshotPayload snapshot;
    private final List<AnimalRecord> filtered = new ArrayList<>();

    private EditBox search;
    private Button filterAll;
    private Button filterAlive;
    private Button filterDeceased;
    private Button settingsButton;
    private Button scopeButton;
    private boolean viewingAll = true;
    private final List<Button> speciesButtons = new ArrayList<>();

    private enum Filter { ALL, ALIVE, DECEASED }
    private Filter filter = Filter.ALL;

    private int scroll = 0;
    private static final int ROW_HEIGHT = 42;
    private static final int CARD_BG_ALIVE = 0x99202830;
    private static final int CARD_BG_DECEASED = 0x991A1A1A;
    private static final int CARD_HOVER_OVERLAY = 0x22FFFFFF;
    private static final int AVATAR_SIZE = 28;
    private static final int PILL_ALIVE = 0xFF3A9D5B;
    private static final int PILL_DECEASED = 0xFF9E3B3B;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_PRIMARY_DIM = 0xFFBBBBBB;
    private static final int TEXT_SECONDARY = 0xFFAAB1BB;
    private static final int TEXT_MUTED = 0xFF8A909A;
    private int speciesRows = 0;

    public FamilyTreeBrowserScreen() {
        super(Component.translatable("familytree.screen.browser.title"));
        ACTIVE = new WeakReference<>(this);
    }

    public static void deliverSnapshot(FamilyTreeSnapshotPayload payload) {
        FamilyTreeBrowserScreen screen = ACTIVE.get();
        if (screen != null) screen.applySnapshot(payload);
    }

    private void applySnapshot(FamilyTreeSnapshotPayload payload) {
        this.snapshot = payload;
        this.viewingAll = payload.viewingAll();
        recompute();
        refreshSpeciesButtons();
        refreshScopeButton();
    }

    private void refreshScopeButton() {
        if (scopeButton == null) return;
        boolean canToggle = snapshot != null && snapshot.mayViewAll();
        scopeButton.visible = canToggle;
        scopeButton.active = canToggle;
        scopeButton.setMessage(scopeLabel());
    }

    private Component scopeLabel() {
        return Component.translatable(viewingAll
                ? "familytree.screen.scope.all"
                : "familytree.screen.scope.mine");
    }

    @Override
    protected void init() {
        int top = 36;
        int leftPad = 16;
        int searchWidth = this.width - leftPad * 2;

        this.search = new EditBox(this.font, leftPad, top, searchWidth, 18,
                Component.translatable("familytree.screen.search"));
        this.search.setResponder(s -> recompute());
        this.search.setMaxLength(64);
        this.addRenderableWidget(this.search);
        this.setInitialFocus(this.search);

        int btnY = top + 24;
        int btnW = 80;
        this.filterAll = this.addRenderableWidget(Button.builder(Component.translatable("familytree.screen.filter.all"),
                        b -> { filter = Filter.ALL; recompute(); })
                .bounds(leftPad, btnY, btnW, 18).build());
        this.filterAlive = this.addRenderableWidget(Button.builder(Component.translatable("familytree.screen.filter.alive"),
                        b -> { filter = Filter.ALIVE; recompute(); })
                .bounds(leftPad + btnW + 4, btnY, btnW, 18).build());
        this.filterDeceased = this.addRenderableWidget(Button.builder(Component.translatable("familytree.screen.filter.deceased"),
                        b -> { filter = Filter.DECEASED; recompute(); })
                .bounds(leftPad + (btnW + 4) * 2, btnY, btnW, 18).build());
        this.settingsButton = this.addRenderableWidget(Button.builder(Component.translatable("familytree.screen.settings"),
                        b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new FamilyTreeSettingsScreen(this));
                            }
                        })
                .bounds(this.width - 100, btnY, 84, 18).build());

        this.scopeButton = this.addRenderableWidget(Button.builder(scopeLabel(),
                        b -> toggleScope())
                .bounds(this.width - 100 - 88, btnY, 84, 18).build());

        refreshSpeciesButtons();
        refreshScopeButton();
    }

    private void recompute() {
        filtered.clear();
        if (snapshot == null) return;
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        for (AnimalRecord r : snapshot.records()) {
            if (filter == Filter.ALIVE && r.deceased()) continue;
            if (filter == Filter.DECEASED && !r.deceased()) continue;
            if (!q.isEmpty()) {
                String hay = (r.name() + " " + r.speciesId()).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }
            filtered.add(r);
        }
        filtered.sort(Comparator.comparing(AnimalRecord::name, String.CASE_INSENSITIVE_ORDER));
        if (scroll * ROW_HEIGHT > Math.max(0, (filtered.size() - 1) * ROW_HEIGHT)) {
            scroll = 0;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gfx, mouseX, mouseY, delta);

        gfx.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        int listTop = getListTop();
        int listBottom = this.height - 16;
        int listLeft = 16;
        int listRight = this.width - 16;

        gfx.fill(listLeft, listTop, listRight, listBottom, 0x80000000);
        drawPanelBorder(gfx, listLeft, listTop, listRight, listBottom, 0x40FFFFFF);

        if (snapshot == null) {
            gfx.centeredText(this.font, Component.translatable("familytree.screen.loading"),
                    this.width / 2, listTop + 24, 0xFFCCCCCC);
            return;
        }

        if (filtered.isEmpty()) {
            gfx.centeredText(this.font, Component.translatable("familytree.screen.empty"),
                    this.width / 2, listTop + 24, 0xFFCCCCCC);
            return;
        }

        gfx.enableScissor(listLeft, listTop, listRight, listBottom);

        int y = listTop + 4 - scroll;
        for (int i = 0; i < filtered.size(); i++) {
            AnimalRecord r = filtered.get(i);
            int rowTop = y + i * ROW_HEIGHT;
            int rowBottom = rowTop + ROW_HEIGHT - 2;
            if (rowBottom < listTop || rowTop > listBottom) continue;
            boolean hover = mouseX >= listLeft && mouseX <= listRight
                    && mouseY >= rowTop && mouseY <= rowBottom;
            drawCard(gfx, r, listLeft + 4, rowTop, listRight - 4, rowBottom, hover);
        }
        gfx.disableScissor();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (snapshot == null) return false;

        double mouseX = event.x();
        double mouseY = event.y();

        int listTop = getListTop();
        int listBottom = this.height - 16;
        int listLeft = 16;
        int listRight = this.width - 16;

        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) {
            return false;
        }

        int relY = (int) (mouseY - (listTop + 4) + scroll);
        int idx = relY / ROW_HEIGHT;
        if (idx < 0 || idx >= filtered.size()) return false;
        AnimalRecord clicked = filtered.get(idx);
        if (this.minecraft != null) {
            this.minecraft.setScreen(new FamilyTreeViewScreen(this, snapshot, clicked.id()));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, scroll - (int) (verticalAmount * ROW_HEIGHT));
        int max = Math.max(0, filtered.size() * ROW_HEIGHT - (this.height - 16 - getListTop()));
        if (scroll > max) scroll = max;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String shortSpecies(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private int getListTop() {
        return 84 + speciesRows * 22;
    }

    private void toggleScope() {
        if (snapshot == null || !snapshot.mayViewAll()) return;
        FamilyTreeClient.requestSnapshot(!viewingAll);
    }

    private void openSpeciesForest(String speciesId, Component title) {
        if (this.minecraft == null || snapshot == null) return;
        this.minecraft.setScreen(FamilyTreeViewScreen.forSpecies(this, snapshot, speciesId, title));
    }

    private void refreshSpeciesButtons() {
        for (Button button : speciesButtons) {
            this.removeWidget(button);
        }
        speciesButtons.clear();
        speciesRows = 0;

        if (snapshot == null) return;

        Map<String, Component> species = collectSpecies(snapshot.records());
        if (species.isEmpty()) {
            return;
        }

        int leftPad = 16;
        int btnY = 36 + 24 + 24;
        int btnW = 96;
        int btnGap = 4;
        int perRow = Math.max(1, (this.width - leftPad * 2 + btnGap) / (btnW + btnGap));

        int index = 0;
        for (Map.Entry<String, Component> entry : species.entrySet()) {
            int row = index / perRow;
            int col = index % perRow;
            int x = leftPad + col * (btnW + btnGap);
            int y = btnY + row * 22;
            Button button = this.addRenderableWidget(Button.builder(entry.getValue(),
                            b -> openSpeciesForest(entry.getKey(), entry.getValue()))
                    .bounds(x, y, btnW, 18).build());
            speciesButtons.add(button);
            index++;
        }
        speciesRows = (species.size() + perRow - 1) / perRow;
    }

    private Map<String, Component> collectSpecies(Collection<AnimalRecord> records) {
        Map<String, Component> species = new LinkedHashMap<>();
        records.stream()
                .sorted(Comparator.comparing(record -> speciesSortKey(record.speciesId())))
                .forEach(record -> species.putIfAbsent(record.speciesId(), speciesLabel(record.speciesId())));

        return species;
    }

    private static String speciesSortKey(String speciesId) {
        return switch (speciesId) {
            case "minecraft:wolf" -> "01_dogs";
            case "minecraft:cat" -> "02_cats";
            case "minecraft:parrot" -> "03_parrots";
            default -> "99_" + shortSpecies(speciesId);
        };
    }

    private static Component speciesLabel(String speciesId) {
        return switch (speciesId) {
            case "minecraft:wolf" -> Component.translatable("familytree.screen.species.dogs");
            case "minecraft:cat" -> Component.translatable("familytree.screen.species.cats");
            case "minecraft:parrot" -> Component.translatable("familytree.screen.species.parrots");
            default -> Component.literal(titleCasePlural(shortSpecies(speciesId)));
        };
    }

    private static String titleCasePlural(String value) {
        if (value.isEmpty()) return value;
        String base = value.replace('_', ' ');
        String titled = Character.toUpperCase(base.charAt(0)) + base.substring(1);
        if (titled.endsWith("s")) return titled;
        return titled + "s";
    }

    private static String buildAgeSummary(long birthDay, long worldAge) {
        boolean showBirthDay = FamilyTreeClientSettings.showBirthDay();
        boolean showAge = FamilyTreeClientSettings.showAge();
        if (showBirthDay && showAge) {
            return "Day " + birthDay + " | " + worldAge + "d";
        }
        if (showBirthDay) {
            return "Day " + birthDay;
        }
        if (showAge) {
            return worldAge + "d";
        }
        return "";
    }

    private void drawCard(GuiGraphicsExtractor gfx, AnimalRecord r,
                          int left, int top, int right, int bottom, boolean hover) {
        int height = bottom - top;
        int accent = speciesAccentColor(r.speciesId());

        gfx.fill(left, top, right, bottom, r.deceased() ? CARD_BG_DECEASED : CARD_BG_ALIVE);
        if (hover) {
            gfx.fill(left, top, right, bottom, CARD_HOVER_OVERLAY);
        }
        gfx.fill(left, top, left + 3, bottom, accent);

        int avatarX = left + 10;
        int avatarY = top + (height - AVATAR_SIZE) / 2;
        drawAvatar(gfx, r, accent, avatarX, avatarY);

        boolean deceased = r.deceased();
        String statusText = Component.translatable(deceased
                ? "familytree.screen.status.deceased"
                : "familytree.screen.status.alive").getString();
        int pillW = this.font.width(statusText) + 12;
        int pillH = 14;
        int pillX = right - 10 - pillW;
        int pillY = top + 8;
        drawPill(gfx, pillX, pillY, pillW, pillH, statusText,
                deceased ? PILL_DECEASED : PILL_ALIVE);

        long worldAge = deceased && r.deathWorldDay() != null
                ? r.deathWorldDay() - r.birthWorldDay()
                : snapshot.currentWorldDay() - r.birthWorldDay();
        String ageStr = buildAgeSummary(r.birthWorldDay(), worldAge);
        if (!ageStr.isEmpty()) {
            int aw = this.font.width(ageStr);
            gfx.text(this.font, Component.literal(ageStr), right - 10 - aw, pillY + pillH + 4, TEXT_MUTED);
        }

        int textLeft = avatarX + AVATAR_SIZE + 10;
        int textMax = Math.max(10, pillX - 8 - textLeft);
        gfx.text(this.font, Component.literal(trimToWidth(r.name(), textMax)),
                textLeft, top + 9, deceased ? TEXT_PRIMARY_DIM : TEXT_PRIMARY);
        gfx.text(this.font, Component.literal(trimToWidth(buildSubtitle(r), textMax)),
                textLeft, top + 22, TEXT_SECONDARY);
    }

    private void drawAvatar(GuiGraphicsExtractor gfx, AnimalRecord r, int accent, int x, int y) {
        gfx.fill(x, y, x + AVATAR_SIZE, y + AVATAR_SIZE, accent);
        gfx.fill(x, y, x + AVATAR_SIZE, y + 1, 0x33FFFFFF);
        String initial = avatarInitial(r.name());
        gfx.centeredText(this.font, Component.literal(initial),
                x + AVATAR_SIZE / 2, y + (AVATAR_SIZE - this.font.lineHeight) / 2 + 1, 0xFFFFFFFF);
    }

    private void drawPill(GuiGraphicsExtractor gfx, int x, int y, int w, int h, String text, int bg) {
        gfx.fill(x, y, x + w, y + h, bg);
        gfx.fill(x, y, x + w, y + 1, 0x33FFFFFF);
        gfx.centeredText(this.font, Component.literal(text),
                x + w / 2, y + (h - this.font.lineHeight) / 2 + 1, 0xFFFFFFFF);
    }

    private static void drawPanelBorder(GuiGraphicsExtractor gfx, int l, int t, int r, int b, int color) {
        drawBorder(gfx, l, t, r, b, color);
    }

    private static void drawBorder(GuiGraphicsExtractor gfx, int l, int t, int r, int b, int color) {
        gfx.fill(l, t, r, t + 1, color);
        gfx.fill(l, b - 1, r, b, color);
        gfx.fill(l, t, l + 1, b, color);
        gfx.fill(r - 1, t, r, b, color);
    }

    private static int speciesAccentColor(String speciesId) {
        return switch (speciesId) {
            case "minecraft:wolf" -> 0xFF9AA0A8;
            case "minecraft:cat" -> 0xFFE0883B;
            case "minecraft:parrot" -> 0xFF4FAE5A;
            default -> 0xFF5B8DEF;
        };
    }

    private static String avatarInitial(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "?";
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String buildSubtitle(AnimalRecord r) {
        String species = capitalize(shortSpecies(r.speciesId()));
        String tamedBy = buildTamedByLine(r);
        return tamedBy.isEmpty() ? species : species + "  ·  " + tamedBy;
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) return value;
        String base = value.replace('_', ' ');
        return Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return text;
        return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width("..."))) + "...";
    }

    private Component tamedByComponent(String ownerName) {
        return Component.translatable("familytree.owner.tamed_by", ownerName);
    }

    private String buildTamedByLine(AnimalRecord record) {
        if (record.ownerName() == null || record.ownerName().isBlank()) {
            return "";
        }
        return tamedByComponent(record.ownerName()).getString();
    }
}
