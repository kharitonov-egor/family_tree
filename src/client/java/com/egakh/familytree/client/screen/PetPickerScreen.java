package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.util.VariantText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Each row is a real button, so Tab, the arrow keys, Enter, and the narrator reach it; the screen draws the row over it. */
public final class PetPickerScreen extends FamilyTreeScreen {
    private static final int LIST_TOP = 64, ROW = 26, FACE = 22;
    private final Screen parent;
    private final List<AnimalRecord> pets;
    private final Consumer<UUID> choose;
    private final Map<String, Integer> nameCounts = new HashMap<>();
    private final List<Button> rows = new ArrayList<>();
    private final List<AnimalRecord> shown = new ArrayList<>();
    private String query = "";
    private List<AnimalRecord> matches = List.of();
    private int page;
    private Button previous, next;

    public PetPickerScreen(Screen parent, List<AnimalRecord> pets, Consumer<UUID> choose) {
        super(Component.translatable("familytree.parents.pick"));
        this.parent = parent; this.choose = choose;
        this.pets = pets.stream().sorted(Comparator.comparing(AnimalRecord::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(AnimalRecord::id)).toList();
        for (AnimalRecord pet : this.pets) nameCounts.merge(pet.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
    }

    @Override protected void init() {
        EditBox search = new EditBox(font, 16, 36, width - 32, 20, Component.translatable("familytree.screen.search"));
        search.setValue(query); search.setMaxLength(64);
        search.setHint(Component.translatable("familytree.screen.search"));
        search.setResponder(value -> { query = value; page = 0; refresh(); });
        addRenderableWidget(search); setInitialFocus(search);
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; refresh(); })
                .bounds(16, height - 28, 40, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; refresh(); })
                .bounds(width - 56, height - 28, 40, 20).build());
        Button back = addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"), button -> onClose())
                .bounds(width / 2 - 45, height - 28, 90, 20).build());
        for (Button button : List.of(previous, next, back)) button.setTabOrderGroup(1);
        refresh();
    }

    private int rowsPerPage() { return Math.max(1, (height - 36 - LIST_TOP) / ROW); }

    private void refresh() {
        rows.forEach(this::removeWidget);
        rows.clear();
        shown.clear();
        String q = query.toLowerCase(Locale.ROOT).strip();
        matches = pets.stream().filter(pet -> haystack(pet).contains(q)).toList();
        int perPage = rowsPerPage();
        page = Math.max(0, Math.min(page, (matches.size() - 1) / perPage));
        previous.active = page > 0;
        next.active = (page + 1) * perPage < matches.size();
        for (int i = page * perPage; i < Math.min(matches.size(), (page + 1) * perPage); i++) {
            AnimalRecord pet = matches.get(i);
            shown.add(pet);
            rows.add(addRenderableWidget(Button.builder(Component.empty(), button -> pick(pet.id()))
                    .bounds(16, LIST_TOP + (i - page * perPage) * ROW, width - 32, ROW - 2)
                    .createNarration(message -> Component.literal(pet.name() + ", " + subtitle(pet)))
                    .build()));
        }
    }

    private static String haystack(AnimalRecord pet) {
        Component variant = VariantText.label(pet);
        return (pet.name() + " " + pet.speciesId() + " " + FamilyTreeBrowserScreen.speciesName(pet.speciesId()).getString()
                + (variant == null ? "" : " " + variant.getString()) + " " + pet.id()).toLowerCase(Locale.ROOT);
    }

    /** Picks a pet as if its row were pressed. */
    public void pick(UUID id) {
        choose.accept(id);
        onClose();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, width, height, 0xFF151A21);
        gfx.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        if (matches.isEmpty()) gfx.centeredText(font, Component.translatable("familytree.parents.no_candidates"), width / 2, 80, 0xFFB0B7C0);
        renderWidgets(gfx, mouseX, mouseY, delta);
        for (int i = 0; i < rows.size(); i++) drawRow(gfx, shown.get(i), rows.get(i));
        if (previous.active || next.active) {
            String pageText = (page + 1) + " / " + ((matches.size() - 1) / rowsPerPage() + 1);
            gfx.text(font, pageText, 64, height - 22, 0xFF8A909A);
        }
    }

    private void drawRow(GuiGraphicsExtractor gfx, AnimalRecord pet, Button button) {
        int left = button.getX(), top = button.getY(), right = left + button.getWidth(), bottom = top + button.getHeight();
        int accent = FamilyTreeBrowserScreen.speciesAccentColor(pet.speciesId());
        gfx.fill(left, top, right, bottom, button.isHoveredOrFocused() ? 0xFF2A3440 : 0xFF202830);
        if (button.isFocused()) FamilyTreeBrowserScreen.drawBorder(gfx, left, top, right, bottom, 0xFFFFFFFF);
        int faceX = left + 4, faceY = top + 1;
        gfx.fill(faceX, faceY, faceX + FACE, faceY + FACE, 0xFF2A2A2A);
        FamilyTreeBrowserScreen.drawBorder(gfx, faceX, faceY, faceX + FACE, faceY + FACE, accent);
        PetFaceRenderer.drawFace(gfx, font, pet, faceX + 2, faceY + 2, FACE - 4);

        int textLeft = faceX + FACE + 8, textRight = right - 6;
        String id = nameCounts.getOrDefault(pet.name().toLowerCase(Locale.ROOT), 0) > 1 ? "#" + pet.id().toString().substring(0, 8) : "";
        if (!id.isEmpty()) {
            gfx.text(font, id, textRight - font.width(id), top + 4, 0xFF8A909A);
            textRight -= font.width(id) + 8;
        }
        gfx.text(font, trimToWidth(pet.name(), Math.max(10, textRight - textLeft)), textLeft, top + 4, pet.deceased() ? 0xFFBBBBBB : 0xFFFFFFFF);
        gfx.text(font, trimToWidth(subtitle(pet), Math.max(10, right - 6 - textLeft)), textLeft, top + 15, 0xFFAAB1BB);
    }

    private static String subtitle(AnimalRecord pet) {
        String text = FamilyTreeBrowserScreen.speciesName(pet.speciesId()).getString();
        Component variant = VariantText.label(pet);
        if (variant != null) text += "  ·  " + variant.getString();
        if (pet.deceased()) text += "  ·  " + Component.translatable("familytree.screen.status.deceased").getString();
        String owner = TreeRenderer.buildTamedByLine(pet);
        return owner.isEmpty() ? text : text + "  ·  " + owner;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount < 0 && next.active) { page++; refresh(); }
        else if (verticalAmount > 0 && previous.active) { page--; refresh(); }
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
