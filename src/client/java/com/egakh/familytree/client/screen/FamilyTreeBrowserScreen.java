package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.util.TimeUtil;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FamilyTreeBrowserScreen extends Screen {

    private static WeakReference<FamilyTreeBrowserScreen> ACTIVE = new WeakReference<>(null);

    private FamilyTreeSnapshotPayload snapshot;
    private final List<AnimalRecord> filtered = new ArrayList<>();

    private EditBox search;
    private Button filterAll;
    private Button filterAlive;
    private Button filterDeceased;

    private enum Filter { ALL, ALIVE, DECEASED }
    private Filter filter = Filter.ALL;

    private int scroll = 0;
    private static final int ROW_HEIGHT = 28;

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

        int listTop = 84;
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

            int textColor = r.deceased() ? 0xFF888888 : 0xFFFFFFFF;
            gfx.text(this.font, Component.literal(r.name()),
                    listLeft + 10, rowTop + 4, textColor);

            String species = shortSpecies(r.speciesId());
            gfx.text(this.font, Component.literal(species),
                    listLeft + 10, rowTop + 15, 0xFFAAAAAA);

            long worldAge = r.deceased() && r.deathWorldDay() != null
                    ? r.deathWorldDay() - r.birthWorldDay()
                    : snapshot.currentWorldDay() - r.birthWorldDay();
            long realAge = r.deceased() && r.deathEpochMillis() != null
                    ? TimeUtil.realDaysBetween(r.birthEpochMillis(), r.deathEpochMillis())
                    : TimeUtil.realDaysBetween(r.birthEpochMillis(), snapshot.currentEpochMillis());
            String right = "Day " + r.birthWorldDay()
                    + " | " + worldAge + "d (W)"
                    + " | " + realAge + "d (R)"
                    + (r.deceased() ? " | deceased" : "");
            int rw = this.font.width(right);
            gfx.text(this.font, Component.literal(right),
                    listRight - 10 - rw, rowTop + 9, 0xFFCCCCCC);
        }
        gfx.disableScissor();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (snapshot == null) return false;

        double mouseX = event.x();
        double mouseY = event.y();

        int listTop = 84;
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
        int max = Math.max(0, filtered.size() * ROW_HEIGHT - (this.height - 16 - 88));
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
}
