package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

public final class PetPickerScreen extends FamilyTreeScreen {
    private final Screen parent;
    private final List<AnimalRecord> pets;
    private final Consumer<UUID> choose;
    private String query = "";
    private int page;
    private final java.util.List<Button> choices = new java.util.ArrayList<>();
    private Button previous, next;
    private int rows;

    public PetPickerScreen(Screen parent, List<AnimalRecord> pets, Consumer<UUID> choose) {
        super(Component.translatable("familytree.parents.pick"));
        this.parent = parent; this.choose = choose;
        this.pets = pets.stream().sorted(Comparator.comparing(AnimalRecord::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(AnimalRecord::id)).toList();
    }

    @Override protected void init() {
        rows = Math.max(1, (height - 112) / 24);
        EditBox search = new EditBox(font, 16, 36, width - 32, 20, Component.translatable("familytree.screen.search"));
        search.setValue(query); search.setMaxLength(64);
        search.setHint(Component.translatable("familytree.screen.search"));
        search.setResponder(value -> { query = value; page = 0; refresh(); });
        addRenderableWidget(search); setInitialFocus(search);
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; refresh(); })
                .bounds(16, height - 30, 40, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; refresh(); })
                .bounds(width - 56, height - 30, 40, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"), button -> onClose())
                .bounds(width / 2 - 45, height - 30, 90, 20).build());
        refresh();
    }

    private void refresh() {
        choices.forEach(this::removeWidget); choices.clear();
        String q = query.toLowerCase(Locale.ROOT).strip();
        var matches = pets.stream().filter(pet -> (pet.name() + " " + pet.speciesId() + " " + pet.id()).toLowerCase(Locale.ROOT).contains(q)).toList();
        page = Math.max(0, Math.min(page, Math.max(0, (matches.size() - 1) / rows)));
        previous.active = page > 0; next.active = (page + 1) * rows < matches.size();
        for (int i = page * rows; i < Math.min(matches.size(), (page + 1) * rows); i++) {
            AnimalRecord pet = matches.get(i);
            String label = pet.id().toString().substring(0, 8) + " | " + pet.name() + " | "
                    + pet.speciesId().substring(pet.speciesId().indexOf(':') + 1);
            choices.add(addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label, width - 48)),
                    button -> { choose.accept(pet.id()); onClose(); }).bounds(16, 64 + (i % rows) * 24, width - 32, 20).build()));
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, width, height, 0xFF151A21);
        gfx.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        if (choices.isEmpty()) gfx.centeredText(font, Component.translatable("familytree.parents.no_candidates"), width / 2, 80, 0xFFB0B7C0);
        renderWidgets(gfx, mouseX, mouseY, delta);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
