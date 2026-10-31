package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class FamilyTreeSettingsScreen extends FamilyTreeScreen {

    private record Toggle(String key, BooleanSupplier value, Consumer<Boolean> setter) {
        Component label() {
            return Component.translatable(key, Component.translatable(value.getAsBoolean()
                    ? "familytree.screen.settings.on" : "familytree.screen.settings.off"));
        }
    }

    private static final List<Toggle> TOGGLES = List.of(
            new Toggle("familytree.screen.settings.show_age",
                    FamilyTreeClientSettings::showAge, FamilyTreeClientSettings::setShowAge),
            new Toggle("familytree.screen.settings.show_birth_day",
                    FamilyTreeClientSettings::showBirthDay, FamilyTreeClientSettings::setShowBirthDay),
            new Toggle("familytree.screen.settings.show_generation",
                    FamilyTreeClientSettings::showGeneration, FamilyTreeClientSettings::setShowGeneration),
            new Toggle("familytree.screen.settings.show_horse_stats",
                    FamilyTreeClientSettings::showHorseStats, FamilyTreeClientSettings::setShowHorseStats),
            new Toggle("familytree.screen.settings.show_relatives",
                    FamilyTreeClientSettings::showRelatives, FamilyTreeClientSettings::setShowRelatives),
            new Toggle("familytree.screen.settings.relative_lines",
                    FamilyTreeClientSettings::relativeLines, FamilyTreeClientSettings::setRelativeLines));

    private static final int ROW = 24;

    private final Screen parent;
    private int top;

    public FamilyTreeSettingsScreen(Screen parent) {
        super(Component.translatable("familytree.screen.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        top = Math.max(28, this.height / 2 - (TOGGLES.size() + 1) * ROW / 2 + 6);
        int y = top;
        for (Toggle toggle : TOGGLES) {
            this.addRenderableWidget(Button.builder(toggle.label(), button -> {
                        toggle.setter().accept(!toggle.value().getAsBoolean());
                        button.setMessage(toggle.label());
                    })
                    .bounds(centerX - 100, y, 200, 20)
                    .build());
            y += ROW;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"), button -> onClose())
                .bounds(centerX - 100, y + 4, 200, 20)
                .build());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, this.width, this.height, 0xCC0F1115);
        renderWidgets(gfx, mouseX, mouseY, delta);
        gfx.centeredText(this.font, this.title, this.width / 2, top - 16, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
