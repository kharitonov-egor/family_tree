package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.keybind.FamilyTreeKeybinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class FamilyTreeHelpScreen extends FamilyTreeScreen {
    private static final int TOP = 38;
    private final Screen parent;
    private final List<String> lines = new ArrayList<>();
    private int scroll;

    public FamilyTreeHelpScreen(Screen parent) { super(Component.translatable("familytree.help.title")); this.parent = parent; }

    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"), button -> onClose())
                .bounds(width / 2 - 104, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("familytree.feedback.button"), button -> minecraft.setScreen(new FeedbackScreen(this)))
                .bounds(width / 2 + 4, height - 28, 100, 20).build());
        lines.clear();
        for (Component paragraph : List.of(Component.translatable("familytree.help.discover"),
                Component.translatable("familytree.help.parents"), Component.translatable("familytree.help.breed"),
                Component.translatable("familytree.help.animals"),
                Component.translatable("familytree.help.relatives", keyName(FamilyTreeKeybinds.SHOW_RELATIVES)),
                Component.translatable("familytree.help.export"), Component.translatable("familytree.help.feedback"))) {
            if (!lines.isEmpty()) lines.add("");
            lines.addAll(wrap(paragraph, columnWidth()));
        }
        scroll = Math.min(scroll, maxScroll());
    }

    /** The grave key's label is a lone backtick, which reads as a typo in a sentence, so it gets a description. */
    static Component keyName(KeyMapping mapping) {
        Component name = mapping.getTranslatedKeyMessage();
        return "key.keyboard.grave.accent".equals(mapping.saveString()) ? Component.translatable("familytree.key.grave", name) : name;
    }

    private int columnWidth() { return Math.min(520, width - 32); }

    private int lineHeight(String line) { return line.isEmpty() ? 10 : font.lineHeight + 2; }

    private int bottom() { return height - 36; }

    private int maxScroll() {
        int total = 0;
        for (String line : lines) total += lineHeight(line);
        return Math.max(0, total - (bottom() - TOP));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, width, height, 0xFF151A21);
        gfx.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        int left = (width - columnWidth()) / 2, y = TOP - scroll;
        gfx.enableScissor(0, TOP, width, bottom());
        for (String line : lines) {
            if (!line.isEmpty() && y + font.lineHeight > TOP && y < bottom()) gfx.text(font, line, left, y, 0xFFB0B7C0);
            y += lineHeight(line);
        }
        gfx.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            int track = bottom() - TOP, thumb = Math.max(12, track * track / (track + max));
            int thumbTop = TOP + (track - thumb) * scroll / max;
            gfx.fill(width - 8, thumbTop, width - 5, thumbTop + thumb, 0x806FB3FF);
        }
        renderWidgets(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(verticalAmount * (font.lineHeight + 2) * 3)));
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
