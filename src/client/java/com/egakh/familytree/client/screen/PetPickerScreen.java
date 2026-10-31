package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.util.VariantText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class PetPickerScreen extends FamilyTreeScreen {
    private static final int LIST_TOP = 64, ROW = 26, FACE = 22;
    private final Screen parent;
    private final List<AnimalRecord> pets;
    private final Consumer<UUID> choose;
    private final Map<String, Integer> nameCounts = new HashMap<>();
    private String query = "";
    private List<AnimalRecord> matches = List.of();
    private int scroll;

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
        search.setResponder(value -> { query = value; scroll = 0; refresh(); });
        addRenderableWidget(search); setInitialFocus(search);
        addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"), button -> onClose())
                .bounds(width / 2 - 45, height - 28, 90, 20).build());
        refresh();
    }

    private void refresh() {
        String q = query.toLowerCase(Locale.ROOT).strip();
        matches = pets.stream().filter(pet -> haystack(pet).contains(q)).toList();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private static String haystack(AnimalRecord pet) {
        Component variant = VariantText.label(pet);
        return (pet.name() + " " + pet.speciesId() + " " + FamilyTreeBrowserScreen.speciesName(pet.speciesId()).getString()
                + (variant == null ? "" : " " + variant.getString()) + " " + pet.id()).toLowerCase(Locale.ROOT);
    }

    /** Picks a pet as if its row were clicked. */
    public void pick(UUID id) {
        choose.accept(id);
        onClose();
    }

    private int listBottom() { return height - 36; }

    private int maxScroll() { return Math.max(0, matches.size() * ROW - (listBottom() - LIST_TOP)); }

    @Override public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, width, height, 0xFF151A21);
        gfx.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        if (matches.isEmpty()) gfx.centeredText(font, Component.translatable("familytree.parents.no_candidates"), width / 2, 80, 0xFFB0B7C0);
        int left = 16, right = width - 16, bottom = listBottom();
        gfx.enableScissor(left, LIST_TOP, right, bottom);
        for (int i = 0; i < matches.size(); i++) {
            int top = LIST_TOP + i * ROW - scroll;
            if (top + ROW < LIST_TOP || top > bottom) continue;
            boolean hover = mouseX >= left && mouseX < right && mouseY >= Math.max(top, LIST_TOP) && mouseY < Math.min(top + ROW - 2, bottom);
            drawRow(gfx, matches.get(i), left, top, right, hover);
        }
        gfx.disableScissor();
        if (maxScroll() > 0) {
            int track = bottom - LIST_TOP, thumb = Math.max(12, track * track / Math.max(track, matches.size() * ROW));
            int thumbTop = LIST_TOP + (track - thumb) * scroll / maxScroll();
            gfx.fill(width - 12, thumbTop, width - 9, thumbTop + thumb, 0x806FB3FF);
        }
        renderWidgets(gfx, mouseX, mouseY, delta);
    }

    private void drawRow(GuiGraphicsExtractor gfx, AnimalRecord pet, int left, int top, int right, boolean hover) {
        int accent = FamilyTreeBrowserScreen.speciesAccentColor(pet.speciesId());
        gfx.fill(left, top, right, top + ROW - 2, hover ? 0xFF2A3440 : 0xFF202830);
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
    protected boolean onMouseClick(double mouseX, double mouseY, int button, boolean doubleClick) {
        if (!isLeftButton(button) || mouseX < 16 || mouseX >= width - 16 || mouseY < LIST_TOP || mouseY >= listBottom()) return false;
        int offset = (int) mouseY - LIST_TOP + scroll;
        int index = offset / ROW;
        if (index < 0 || index >= matches.size() || offset % ROW >= ROW - 2) return false;
        pick(matches.get(index).id());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(verticalAmount * ROW)));
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
