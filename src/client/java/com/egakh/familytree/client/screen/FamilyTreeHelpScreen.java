package com.egakh.familytree.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FamilyTreeHelpScreen extends FamilyTreeScreen {
    private final Screen parent;
    public FamilyTreeHelpScreen(Screen parent) { super(Component.translatable("familytree.help.title")); this.parent = parent; }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"), button -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, width, height, 0xFF151A21);
        gfx.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        int y = 38;
        for (String key : java.util.List.of("familytree.help.discover", "familytree.help.parents", "familytree.help.breed", "familytree.help.export")) {
            for (var line : font.split(Component.translatable(key), Math.min(520, width - 32))) {
                gfx.text(font, line, Math.max(16, (width - 520) / 2), y, 0xFFB0B7C0); y += font.lineHeight + 2;
            }
            y += 10;
        }
        renderWidgets(gfx, mouseX, mouseY, delta);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
