package com.egakh.familytree.client.screen;

import com.egakh.familytree.breeding.BestPairs;
import com.egakh.familytree.breeding.BreedingPair;
import com.egakh.familytree.breeding.BreedingPlan;
import com.egakh.familytree.breeding.PlanText;
import com.egakh.familytree.breeding.VariantOdds;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.util.Kinship;
import com.egakh.familytree.util.KinshipText;
import com.egakh.familytree.util.PetNames;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BreedingPlannerScreen extends FamilyTreeScreen {
    private static final int CONTENT_TOP = 82;
    private static final int TEXT = 0xFFFFFFFF, MUTED = 0xFFB0B7C0, HEADING = 0xFFFFD24A, WARNING = 0xFFFFB35C, ERROR = 0xFFFF9292;
    private static final int BAR = 0xFF6FB3FF, BAR_ABOVE = HorseStatsText.BEATS_COLOR, BAR_BASE = 0xFF3A4350, PARENT_MARK = 0xFFFFD24A;

    private final Screen parent;
    private final FamilyTreeSnapshotPayload snapshot;
    private final Map<UUID, AnimalRecord> records = new HashMap<>();
    private final Kinship kinship;
    private UUID firstId, secondId;
    private BreedingPlan plan;
    private FoalCheck foals;
    private int scroll, contentHeight;
    private Button treeButton;

    private interface Block {
        int height();
        void draw(GuiGraphicsExtractor gfx, int x, int y, int width);
    }

    private final List<Block> blocks = new ArrayList<>();

    public BreedingPlannerScreen(Screen parent, FamilyTreeSnapshotPayload snapshot, UUID firstId, UUID secondId) {
        super(Component.translatable("familytree.planner.title"));
        this.parent = parent;
        this.snapshot = snapshot;
        for (AnimalRecord record : snapshot.records()) records.put(record.id(), record);
        this.kinship = new Kinship(records);
        this.firstId = usable(records.get(firstId)) ? firstId : null;
        this.secondId = this.firstId != null && mates(records.get(this.firstId), records.get(secondId)) ? secondId : null;
    }

    /** Shows another pair in this planner, so choosing from Best pairs returns here instead of stacking a new planner. */
    void choosePair(UUID first, UUID second) {
        firstId = usable(records.get(first)) ? first : null;
        secondId = firstId != null && mates(records.get(firstId), records.get(second)) ? second : null;
        scroll = 0;
    }

    private static boolean usable(AnimalRecord pet) {
        return pet != null && !pet.deceased() && BreedingPair.breeds(pet.speciesId());
    }

    private static boolean mates(AnimalRecord first, AnimalRecord second) {
        return first != null && usable(second) && !first.id().equals(second.id())
                && BreedingPair.canBreed(first.speciesId(), second.speciesId());
    }

    @Override
    protected void init() {
        foals = new FoalCheck(minecraft, snapshot.currentWorldDay());
        int rowWidth = Math.min(width - 16, 440), x = (width - rowWidth) / 2, half = (rowWidth - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"), button -> onClose())
                .bounds(8, 8, 60, 18).build());
        addRenderableWidget(Button.builder(parentLabel(1, firstId), button -> pickFirst()).bounds(x, 32, half, 18).build());
        Button second = addRenderableWidget(Button.builder(parentLabel(2, secondId), button -> pickSecond())
                .bounds(x + half + 4, 32, rowWidth - half - 4, 18).build());
        second.active = firstId != null;
        treeButton = addRenderableWidget(Button.builder(Component.translatable("familytree.planner.show_tree"), button -> {
            if (firstId != null && secondId != null)
                minecraft.setScreen(FamilyTreeViewScreen.forRelationship(this, snapshot, firstId, secondId));
        }).bounds(x, 56, half, 18).build());
        treeButton.active = firstId != null && secondId != null;
        addRenderableWidget(Button.builder(Component.translatable("familytree.planner.best"), button ->
                minecraft.setScreen(new BestPairsScreen(this, snapshot, herdFor(records.get(firstId)))))
                .bounds(x + half + 4, 56, rowWidth - half - 4, 18).build());
        rebuild();
    }

    private Component parentLabel(int number, UUID id) {
        AnimalRecord pet = records.get(id);
        return pet == null ? Component.translatable("familytree.planner.choose", number)
                : Component.translatable("familytree.parents.choose", number, PetNames.isolate(pet.name()));
    }

    /** Counts living animals per species once, so large farms don't compare every pair. */
    private void pickFirst() {
        Map<String, Integer> living = new HashMap<>();
        for (AnimalRecord pet : snapshot.records()) if (usable(pet)) living.merge(pet.speciesId(), 1, Integer::sum);
        List<AnimalRecord> candidates = snapshot.records().stream().filter(BreedingPlannerScreen::usable)
                .filter(pet -> living.entrySet().stream().anyMatch(other -> BreedingPair.canBreed(pet.speciesId(), other.getKey())
                        && other.getValue() > (other.getKey().equals(pet.speciesId()) ? 1 : 0))).toList();
        minecraft.setScreen(new PetPickerScreen(this, candidates, chosen -> {
            firstId = chosen;
            if (!mates(records.get(firstId), records.get(secondId))) secondId = null;
            scroll = 0;
        }));
    }

    private void pickSecond() {
        AnimalRecord first = records.get(firstId);
        if (first == null) return;
        List<AnimalRecord> candidates = snapshot.records().stream().filter(pet -> mates(first, pet)).toList();
        minecraft.setScreen(new PetPickerScreen(this, candidates, chosen -> {
            secondId = chosen;
            scroll = 0;
        }));
    }

    static BestPairs.Herd herdFor(AnimalRecord pet) {
        if (pet == null) return null;
        for (BestPairs.Herd herd : BestPairs.Herd.values())
            if (herd != BestPairs.Herd.MULE && herd.member(pet.speciesId())) return herd;
        return null;
    }

    private void rebuild() {
        blocks.clear();
        AnimalRecord first = records.get(firstId), second = records.get(secondId);
        plan = first != null && second != null ? new BreedingPlan(first, second, kinship) : null;
        if (plan == null) {
            paragraph(Component.translatable(first == null ? "familytree.planner.empty" : "familytree.planner.pick_mate", first == null ? "" : PetNames.isolate(first.name())), MUTED);
            contentHeight = measure();
            return;
        }
        if (!plan.canBreed) paragraph(Component.translatable("familytree.planner.cannot_breed", PetNames.isolate(first.name()), PetNames.isolate(second.name())), ERROR);
        else paragraph(Component.translatable("familytree.planner.foal", PlanText.foal(plan.foalSpecies)), TEXT);
        for (AnimalRecord pet : List.of(first, second))
            if (foals.mayBeFoal(pet)) paragraph(Component.translatable("familytree.planner.maybe_foal", PetNames.isolate(pet.name())), WARNING);
        paragraph(KinshipText.describe(plan.relation, PetNames.isolate(first.name()), PetNames.isolate(second.name())), TEXT);
        if (plan.canBreed) {
            paragraph(KinshipText.inbreeding(plan.inbreeding), TEXT);
            paragraph(Component.translatable("familytree.planner.inbreeding.note"), MUTED);
        }
        gap();
        if (!plan.stats.isEmpty()) {
            heading(Component.translatable("familytree.planner.stats"));
            statTable(plan.stats);
            paragraph(Component.translatable("familytree.planner.stats.hint"), MUTED);
            gap();
        }
        for (AnimalRecord pet : plan.missingStats) paragraph(Component.translatable("familytree.planner.stats.unknown", PetNames.isolate(pet.name())), MUTED);
        if (plan.fixedStats) paragraph(Component.translatable("familytree.planner.stats.camel"), MUTED);
        VariantOdds.Result variants = plan.variants;
        if (!variants.groups().isEmpty() || variants.note() != VariantOdds.Note.NONE) {
            heading(Component.translatable("familytree.planner.variants"));
            for (VariantOdds.Group group : variants.groups()) paragraph(PlanText.group(plan.foalSpecies.get(0), group), TEXT);
            Component note = PlanText.note(variants.note());
            if (note != null) paragraph(note, MUTED);
        }
        contentHeight = measure();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int measure() {
        int total = 0;
        for (Block block : blocks) total += block.height();
        return total;
    }

    private int contentWidth() {
        return Math.min(width - 24, 440);
    }

    private void paragraph(Component text, int color) {
        List<FormattedCharSequence> lines = BidiLines.split(font, text, Math.max(40, contentWidth()));
        int lineHeight = font.lineHeight + 2;
        blocks.add(new Block() {
            public int height() { return lines.size() * lineHeight; }
            public void draw(GuiGraphicsExtractor gfx, int x, int y, int width) {
                for (int i = 0; i < lines.size(); i++) gfx.text(font, lines.get(i), x, y + i * lineHeight, color);
            }
        });
    }

    private void heading(Component text) {
        blocks.add(new Block() {
            public int height() { return font.lineHeight + 4; }
            public void draw(GuiGraphicsExtractor gfx, int x, int y, int width) {
                gfx.text(font, text, x, y, HEADING);
                gfx.fill(x, y + font.lineHeight + 1, x + width, y + font.lineHeight + 2, 0x406FB3FF);
            }
        });
    }

    private void gap() {
        blocks.add(new Block() {
            public int height() { return 6; }
            public void draw(GuiGraphicsExtractor gfx, int x, int y, int width) {}
        });
    }

    private void statTable(List<BreedingPlan.StatForecast> stats) {
        Component[] headers = {Component.empty(), Component.translatable("familytree.planner.column.lowest"),
                Component.translatable("familytree.planner.column.likely"), Component.translatable("familytree.planner.column.highest"),
                Component.empty(), Component.translatable("familytree.planner.column.beats")};
        List<String[]> rows = new ArrayList<>();
        for (BreedingPlan.StatForecast stat : stats) rows.add(new String[]{
                Component.translatable("familytree.planner.stat." + PlanText.key(stat.stat())).getString(),
                PlanText.format(stat.stat(), stat.lowest()), PlanText.likely(stat).getString(),
                PlanText.format(stat.stat(), stat.highest()), "", KinshipText.percent(stat.chanceAboveBoth())});
        int[] widths = new int[headers.length];
        for (int c = 0; c < headers.length; c++) {
            widths[c] = font.width(headers[c]);
            for (String[] row : rows) widths[c] = Math.max(widths[c], font.width(row[c]));
        }
        int gap = 8, fixed = 0;
        for (int c = 0; c < headers.length; c++) if (c != 4) fixed += widths[c] + gap;
        int barWidth = Math.max(24, Math.min(120, contentWidth() - fixed - gap));
        widths[4] = barWidth;
        int rowHeight = font.lineHeight + 5;
        blocks.add(new Block() {
            public int height() { return rowHeight * (rows.size() + 1); }
            public void draw(GuiGraphicsExtractor gfx, int x, int y, int width) {
                int cx = x;
                for (int c = 0; c < headers.length; c++) {
                    if (c > 0 && c != 4) gfx.text(font, headers[c], cx + widths[c] - font.width(headers[c]), y, MUTED);
                    cx += widths[c] + gap;
                }
                for (int r = 0; r < rows.size(); r++) {
                    int rowY = y + rowHeight * (r + 1);
                    String[] row = rows.get(r);
                    cx = x;
                    for (int c = 0; c < headers.length; c++) {
                        if (c == 4) drawBar(gfx, stats.get(r), cx, rowY - 1, widths[c], font.lineHeight + 1);
                        else if (c == 0) gfx.text(font, row[c], cx, rowY, TEXT);
                        else gfx.text(font, row[c], cx + widths[c] - font.width(row[c]), rowY,
                                    c == 5 ? HorseStatsText.BEATS_COLOR : c == 2 ? HEADING : TEXT);
                        cx += widths[c] + gap;
                    }
                }
            }
        });
    }

    private static void drawBar(GuiGraphicsExtractor gfx, BreedingPlan.StatForecast stat, int x, int y, int width, int height) {
        double[] bars = stat.bars();
        double peak = 0;
        for (double mass : bars) peak = Math.max(peak, mass);
        gfx.fill(x, y + height - 1, x + width, y + height, BAR_BASE);
        if (peak <= 0) return;
        for (int i = 0; i < bars.length; i++) {
            int left = x + (int) Math.round(width * i / (double) bars.length);
            int right = Math.max(left + 1, x + (int) Math.round(width * (i + 1) / (double) bars.length) - (bars.length <= 8 ? 1 : 0));
            int barHeight = bars[i] <= 0 ? 0 : Math.max(1, (int) Math.round((height - 1) * bars[i] / peak));
            double center = (i + 0.5) / bars.length;
            if (barHeight > 0) gfx.fill(left, y + height - 1 - barHeight, right, y + height - 1, center > stat.betterParentAt() ? BAR_ABOVE : BAR);
        }
        double at = stat.betterParentAt();
        if (at >= 0 && at <= 1) {
            int mark = Math.min(x + width - 1, x + (int) Math.round(width * at));
            gfx.fill(mark, y, mark + 1, y + height, PARENT_MARK);
        }
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (height - 8 - CONTENT_TOP));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, width, height, 0xFF151A21);
        gfx.centeredText(font, title, width / 2, 13, TEXT);
        int x = (width - contentWidth()) / 2, bottom = height - 8;
        gfx.enableScissor(0, CONTENT_TOP - 2, width, bottom);
        int y = CONTENT_TOP - scroll;
        for (Block block : blocks) {
            int h = block.height();
            if (y + h >= CONTENT_TOP - 2 && y <= bottom) block.draw(gfx, x, y, contentWidth());
            y += h;
        }
        gfx.disableScissor();
        if (maxScroll() > 0) {
            int track = bottom - CONTENT_TOP, thumb = Math.max(12, track * track / Math.max(track, contentHeight));
            int top = CONTENT_TOP + (track - thumb) * scroll / maxScroll();
            gfx.fill(width - 6, top, width - 3, top + thumb, 0x806FB3FF);
        }
        renderWidgets(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(verticalAmount * 16)));
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public BreedingPlan plan() {
        return plan;
    }
}
