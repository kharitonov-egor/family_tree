package com.egakh.familytree.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Holds the browser buttons that don't fit when the GUI scale leaves little room for the pet list. */
public final class BrowserMoreScreen extends FamilyTreeScreen {
    private final FamilyTreeBrowserScreen browser;
    private final boolean sortAndScope;

    public BrowserMoreScreen(FamilyTreeBrowserScreen browser, boolean sortAndScope) {
        super(Component.translatable("familytree.screen.browser.title"));
        this.browser = browser;
        this.sortAndScope = sortAndScope;
    }

    @Override
    protected void init() {
        int width = Math.min(200, this.width - 32), x = (this.width - width) / 2;
        boolean sort = sortAndScope && browser.sortAvailable(), scope = sortAndScope && browser.scopeAvailable();
        int extra = (sort ? 24 : 0) + (scope ? 24 : 0);
        int y = Math.max(32, this.height / 2 - 56 - extra / 2);
        if (sort) {
            addRenderableWidget(Button.builder(browser.sortLabel(), b -> {
                browser.cycleSort();
                b.setMessage(browser.sortLabel());
            }).bounds(x, y, width, 20).build());
            y += 24;
        }
        if (scope) {
            addRenderableWidget(Button.builder(browser.scopeLabel(), b -> {
                browser.toggleScope();
                onClose();
            }).bounds(x, y, width, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable("familytree.help.button"), b -> browser.openHelp())
                .bounds(x, y, width, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("familytree.screen.settings"), b -> browser.openSettings())
                .bounds(x, y + 24, width, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("familytree.refresh"), b -> {
            browser.refresh();
            onClose();
        }).bounds(x, y + 48, width, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("familytree.feedback.button"), b -> browser.openFeedback())
                .bounds(x, y + 72, width, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"), b -> onClose())
                .bounds(x, y + 104, width, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, this.width, this.height, 0xFF151A21);
        gfx.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
        renderWidgets(gfx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(browser);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
