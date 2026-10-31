package com.egakh.familytree.client.screen;

import com.egakh.familytree.breeding.BestPairs;
import com.egakh.familytree.breeding.BestPairs.Herd;
import com.egakh.familytree.breeding.BestPairs.Owners;
import com.egakh.familytree.breeding.BestPairs.Rank;
import com.egakh.familytree.breeding.BestPairs.Relatives;
import com.egakh.familytree.breeding.BreedingPlan;
import com.egakh.familytree.breeding.PlanText;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.util.HorseStatUnits.Stat;
import com.egakh.familytree.util.Kinship;
import com.egakh.familytree.util.KinshipText;
import com.egakh.familytree.util.PetNames;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BestPairsScreen extends FamilyTreeScreen {
    private static final int SUMMARY_TOP = 80, ROW_HEIGHT = 26, LIMIT = 50;
    private static final int TEXT = 0xFFFFFFFF, MUTED = 0xFFB0B7C0, HEADING = 0xFFFFD24A;

    private final Screen parent;
    private final FamilyTreeSnapshotPayload snapshot;
    private final Map<UUID, AnimalRecord> records = new HashMap<>();
    private final Kinship kinship;
    private final List<Herd> herds = new ArrayList<>();
    private Herd herd;
    private Rank rank = Rank.OVERALL;
    private Relatives relatives = Relatives.SKIP_CLOSE;
    private Owners owners;
    private List<AnimalRecord> adults = List.of();
    private BestPairs.Outcome outcome;
    private final List<List<String>> details = new ArrayList<>();
    private int scroll;

    public BestPairsScreen(Screen parent, FamilyTreeSnapshotPayload snapshot, Herd preferred) {
        super(Component.translatable("familytree.best.title"));
        this.parent = parent;
        this.snapshot = snapshot;
        for (AnimalRecord record : snapshot.records()) records.put(record.id(), record);
        this.kinship = new Kinship(records);
        this.owners = snapshot.viewingAll() ? Owners.MINE : Owners.ANY;
        this.herd = preferred;
    }

    @Override
    protected void init() {
        FoalCheck foals = new FoalCheck(minecraft, snapshot.currentWorldDay());
        adults = snapshot.records().stream().filter(pet -> !pet.deceased() && !foals.mayBeFoal(pet)).toList();
        herds.clear();
        for (Herd candidate : Herd.values()) if (!BestPairs.rankable(adults, options(candidate, rank)).isEmpty()) herds.add(candidate);
        if (!herds.contains(herd)) herd = herds.isEmpty() ? null : herds.get(0);
        List<Rank> ranks = herd == null ? List.of() : BestPairs.rankable(adults, options(herd, rank));
        if (!ranks.isEmpty() && !ranks.contains(rank)) rank = ranks.get(0);
        int rowWidth = Math.min(width - 16, 440), x = (width - rowWidth) / 2, half = (rowWidth - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"), button -> onClose())
                .bounds(8, 8, 60, 18).build());
        List<String> foal = herd == null ? List.of() : List.of(herd.foalSpecies);
        Button herdButton = addRenderableWidget(Button.builder(Component.translatable("familytree.best.herd",
                PlanText.baby(foal), PlanText.foal(foal)), button -> {
            herd = herds.get((herds.indexOf(herd) + 1) % herds.size());
            reopen();
        }).bounds(x, 32, half, 18).build());
        herdButton.active = herds.size() > 1;
        Button rankButton = addRenderableWidget(Button.builder(Component.translatable("familytree.best.rank", rankName(rank)), button -> {
            rank = ranks.get((ranks.indexOf(rank) + 1) % ranks.size());
            reopen();
        }).bounds(x + half + 4, 32, rowWidth - half - 4, 18).build());
        rankButton.active = ranks.size() > 1;
        rankButton.setTooltip(Tooltip.create(Component.translatable("familytree.best.rank.hint")));
        Button relativesButton = addRenderableWidget(Button.builder(Component.translatable(
                "familytree.best.relatives." + relatives.name().toLowerCase(Locale.ROOT)), button -> {
            relatives = Relatives.values()[(relatives.ordinal() + 1) % Relatives.values().length];
            reopen();
        }).bounds(x, 56, half, 18).build());
        relativesButton.setTooltip(Tooltip.create(Component.translatable("familytree.best.relatives.hint")));
        Button ownersButton = addRenderableWidget(Button.builder(Component.translatable(
                "familytree.best.owners." + owners.name().toLowerCase(Locale.ROOT)), button -> {
            owners = Owners.values()[(owners.ordinal() + 1) % Owners.values().length];
            reopen();
        }).bounds(x + half + 4, 56, rowWidth - half - 4, 18).build());
        ownersButton.setTooltip(Tooltip.create(Component.translatable("familytree.best.owners.hint")));
        compute();
    }

    private void reopen() {
        scroll = 0;
        clearWidgets();
        init();
    }

    private BestPairs.Options options(Herd candidate, Rank chosen) {
        UUID viewer = minecraft == null || minecraft.player == null ? null : minecraft.player.getUUID();
        return new BestPairs.Options(candidate, chosen, relatives, owners, viewer);
    }

    private void compute() {
        details.clear();
        outcome = herd == null ? null : BestPairs.rank(adults, options(herd, rank), kinship, LIMIT);
        if (outcome == null) return;
        for (BestPairs.Ranked pair : outcome.pairs()) details.add(describe(pair));
    }

    /** The foal's expected value for each stat, with the chance of beating both parents for the ranked stat. */
    private List<String> describe(BestPairs.Ranked pair) {
        List<String> parts = new ArrayList<>();
        boolean llama = herd == Herd.LLAMA;
        for (BreedingPlan.StatForecast stat : BreedingPlan.forecasts(pair.first().horseStats(), pair.second().horseStats(), llama)) {
            if (llama && (stat.stat() == Stat.SPEED || stat.stat() == Stat.JUMP)) continue;
            String value = stat.stat() == Stat.STRENGTH ? String.format(Locale.ROOT, "%.1f", stat.mean()) : stat.stat().format(stat.mean());
            String text = Component.translatable("familytree.stats.short." + PlanText.key(stat.stat()), value).getString();
            if (ranked(stat.stat())) text += " (" + Component.translatable("familytree.best.beats", KinshipText.percent(stat.chanceAboveBoth())).getString() + ")";
            parts.add(text);
        }
        parts.add(Component.translatable("familytree.best.inbreeding", KinshipText.percent(pair.inbreeding())).getString());
        return parts;
    }

    private boolean ranked(Stat stat) {
        return switch (rank) {
            case SPEED -> stat == Stat.SPEED;
            case JUMP -> stat == Stat.JUMP;
            case HEALTH -> stat == Stat.HEALTH;
            case STRENGTH -> stat == Stat.STRENGTH;
            case OVERALL -> false;
        };
    }

    private static Component rankName(Rank rank) {
        return switch (rank) {
            case OVERALL -> Component.translatable("familytree.best.rank.overall");
            case STRENGTH -> Component.translatable("familytree.planner.stat.strength");
            default -> Component.translatable("familytree.screen.sort." + rank.name().toLowerCase(Locale.ROOT));
        };
    }

    private Component summary() {
        if (herd == null) return Component.translatable("familytree.best.no_herd");
        if (outcome.pairs().isEmpty()) return Component.translatable("familytree.best.none");
        return Component.translatable(outcome.trimmed() ? "familytree.best.summary_trimmed" : "familytree.best.summary",
                outcome.pairsConsidered(), outcome.candidates());
    }

    /** The summary wraps to two lines at most, so the list starts below it at every GUI scale. */
    private List<net.minecraft.util.FormattedCharSequence> summaryLines() {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(summary(), Math.min(width - 16, 440));
        return lines.size() > 2 ? lines.subList(0, 2) : lines;
    }

    private int listTop() {
        return SUMMARY_TOP + summaryLines().size() * (font.lineHeight + 2) + 4;
    }

    private int listBottom() {
        return height - 8;
    }

    private int maxScroll() {
        int rows = outcome == null ? 0 : outcome.pairs().size();
        return Math.max(0, rows * ROW_HEIGHT - (listBottom() - listTop()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, width, height, 0xFF151A21);
        gfx.centeredText(font, title, width / 2, 13, TEXT);
        int rowWidth = Math.min(width - 16, 440), x = (width - rowWidth) / 2;
        List<net.minecraft.util.FormattedCharSequence> summary = summaryLines();
        for (int i = 0; i < summary.size(); i++) gfx.text(font, summary.get(i), x, SUMMARY_TOP + i * (font.lineHeight + 2), MUTED);
        int listTop = listTop();
        if (outcome != null && !outcome.pairs().isEmpty()) {
            gfx.enableScissor(0, listTop, width, listBottom());
            for (int i = 0; i < outcome.pairs().size(); i++) {
                int top = listTop + i * ROW_HEIGHT - scroll;
                if (top + ROW_HEIGHT < listTop || top > listBottom()) continue;
                BestPairs.Ranked pair = outcome.pairs().get(i);
                boolean hover = mouseX >= x && mouseX < x + rowWidth && mouseY >= top && mouseY < top + ROW_HEIGHT - 2
                        && mouseY >= listTop && mouseY < listBottom();
                gfx.fill(x, top, x + rowWidth, top + ROW_HEIGHT - 2, hover ? 0xFF2A3440 : 0xFF202830);
                String name = Component.translatable("familytree.best.row", i + 1, PetNames.isolate(pair.first().name()), PetNames.isolate(pair.second().name())).getString();
                gfx.text(font, font.plainSubstrByWidth(name, rowWidth - 8), x + 4, top + 3, TEXT);
                String line = String.join("  ·  ", details.get(i));
                gfx.text(font, font.plainSubstrByWidth(line, rowWidth - 8), x + 4, top + 3 + font.lineHeight + 1,
                        rank == Rank.OVERALL ? MUTED : HEADING);
            }
            gfx.disableScissor();
        }
        renderWidgets(gfx, mouseX, mouseY, delta);
    }

    @Override
    protected boolean onMouseClick(double mouseX, double mouseY, int button, boolean doubleClick) {
        if (!isLeftButton(button) || outcome == null || mouseY < listTop() || mouseY >= listBottom()) return false;
        int rowWidth = Math.min(width - 16, 440), x = (width - rowWidth) / 2;
        if (mouseX < x || mouseX >= x + rowWidth) return false;
        int index = (int) ((mouseY - listTop() + scroll) / ROW_HEIGHT);
        if (index < 0 || index >= outcome.pairs().size()) return false;
        BestPairs.Ranked pair = outcome.pairs().get(index);
        if (parent instanceof BreedingPlannerScreen planner) {
            planner.choosePair(pair.first().id(), pair.second().id());
            minecraft.setScreen(planner);
        } else {
            minecraft.setScreen(new BreedingPlannerScreen(this, snapshot, pair.first().id(), pair.second().id()));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(verticalAmount * ROW_HEIGHT)));
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

    public BestPairs.Outcome outcome() {
        return outcome;
    }
}
