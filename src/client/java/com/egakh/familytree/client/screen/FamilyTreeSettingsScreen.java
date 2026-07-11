package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FamilyTreeSettingsScreen extends Screen {

    private final Screen parent;

    public FamilyTreeSettingsScreen(Screen parent) {
        super(Component.translatable("familytree.screen.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - 38;

        this.addRenderableWidget(Button.builder(showAgeLabel(), button -> {
                    FamilyTreeClientSettings.setShowAge(!FamilyTreeClientSettings.showAge());
                    button.setMessage(showAgeLabel());
                })
                .bounds(centerX - 100, top, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(showBirthDayLabel(), button -> {
                    FamilyTreeClientSettings.setShowBirthDay(!FamilyTreeClientSettings.showBirthDay());
                    button.setMessage(showBirthDayLabel());
                })
                .bounds(centerX - 100, top + 28, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(showGenerationLabel(), button -> {
                    FamilyTreeClientSettings.setShowGeneration(!FamilyTreeClientSettings.showGeneration());
                    button.setMessage(showGenerationLabel());
                })
                .bounds(centerX - 100, top + 56, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"),
                        button -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(parent);
                            }
                        })
                .bounds(centerX - 100, top + 84, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, this.width, this.height, 0xCC0F1115);
        super.extractRenderState(gfx, mouseX, mouseY, delta);
        gfx.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 52, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component showAgeLabel() {
        return Component.translatable("familytree.screen.settings.show_age",
                FamilyTreeClientSettings.showAge()
                        ? Component.translatable("familytree.screen.settings.on")
                        : Component.translatable("familytree.screen.settings.off"));
    }

    private static Component showBirthDayLabel() {
        return Component.translatable("familytree.screen.settings.show_birth_day",
                FamilyTreeClientSettings.showBirthDay()
                        ? Component.translatable("familytree.screen.settings.on")
                        : Component.translatable("familytree.screen.settings.off"));
    }

    private static Component showGenerationLabel() {
        return Component.translatable("familytree.screen.settings.show_generation",
                FamilyTreeClientSettings.showGeneration()
                        ? Component.translatable("familytree.screen.settings.on")
                        : Component.translatable("familytree.screen.settings.off"));
    }
}
