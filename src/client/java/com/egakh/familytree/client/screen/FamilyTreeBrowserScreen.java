package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.data.AnimalRecord;
import net.minecraft.client.renderer.RenderPipelines;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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
    private static final Identifier DEFAULT_CAT_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/cat/cat_tabby.png");
    private static final int CAT_TEXTURE_WIDTH = 64;
    private static final int CAT_TEXTURE_HEIGHT = 32;
    private static final float CAT_FACE_U = 5.0f;
    private static final float CAT_FACE_V = 4.0f;
    private static final int CAT_FACE_WIDTH = 6;
    private static final int CAT_FACE_HEIGHT = 6;

    private static WeakReference<FamilyTreeBrowserScreen> ACTIVE = new WeakReference<>(null);

    private FamilyTreeSnapshotPayload snapshot;
    private final List<AnimalRecord> filtered = new ArrayList<>();

    private EditBox search;
    private Button filterAll;
    private Button filterAlive;
    private Button filterDeceased;
    private Button settingsButton;
    private final List<Button> speciesButtons = new ArrayList<>();

    private enum Filter { ALL, ALIVE, DECEASED }
    private Filter filter = Filter.ALL;

    private int scroll = 0;
    private static final int ROW_HEIGHT = 40;
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
        recompute();
        refreshSpeciesButtons();
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

        refreshSpeciesButtons();
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
            gfx.fill(listLeft + 4, rowTop, listRight - 4, rowBottom,
                    hover ? 0x40FFFFFF : 0x20000000);

            boolean isCat = isCat(r);
            int iconSize = isCat ? 26 : 0;
            int textLeft = listLeft + 10 + (isCat ? iconSize + 8 : 0);
            if (isCat) {
                drawCatFace(gfx, catTexture(r), listLeft + 10, rowTop + 8, iconSize);
            }

            int textColor = r.deceased() ? 0xFF888888 : 0xFFFFFFFF;
            gfx.text(this.font, Component.literal(r.name()),
                    textLeft, rowTop + 4, textColor);

            String species = shortSpecies(r.speciesId());
            gfx.text(this.font, Component.literal(species),
                    textLeft, rowTop + 15, 0xFFAAAAAA);
            String tamedBy = buildTamedByLine(r);
            if (!tamedBy.isEmpty()) {
                gfx.text(this.font, Component.literal(tamedBy),
                        textLeft, rowTop + 26, 0xFFAAAAAA);
            }

            long worldAge = r.deceased() && r.deathWorldDay() != null
                    ? r.deathWorldDay() - r.birthWorldDay()
                    : snapshot.currentWorldDay() - r.birthWorldDay();
            String right = buildAgeSummary(r.birthWorldDay(), worldAge);
            if (r.deceased()) {
                right = right.isEmpty() ? "deceased" : right + " | deceased";
            }
            if (!right.isEmpty()) {
                int rw = this.font.width(right);
                gfx.text(this.font, Component.literal(right),
                        listRight - 10 - rw, rowTop + 9, 0xFFCCCCCC);
            }
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

    private static boolean isCat(AnimalRecord record) {
        return "minecraft:cat".equals(record.speciesId());
    }

    private static Identifier catTexture(AnimalRecord record) {
        if (record.textureId() != null && !record.textureId().isBlank()) {
            Identifier texture = normalizeCatTexture(record.textureId());
            if (texture != null) {
                return texture;
            }
        }
        return DEFAULT_CAT_TEXTURE;
    }

    private static Identifier normalizeCatTexture(String rawId) {
        Identifier id = Identifier.tryParse(rawId);
        if (id == null) {
            return null;
        }
        if (id.getPath().startsWith("textures/")) {
            return id;
        }
        return id.withPrefix("textures/").withSuffix(".png");
    }

    private static void drawCatFace(GuiGraphicsExtractor gfx, Identifier texture, int x, int y, int size) {
        gfx.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, CAT_FACE_U, CAT_FACE_V,
                size, size, CAT_FACE_WIDTH, CAT_FACE_HEIGHT, CAT_TEXTURE_WIDTH, CAT_TEXTURE_HEIGHT);
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
