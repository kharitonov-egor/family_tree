package com.egakh.familytree.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
//?}

/** Adapts input events while keeping tree navigation identical on each Minecraft version. */
public abstract class FamilyTreeScreen extends Screen {
    protected FamilyTreeScreen(Component title) { super(title); }
    protected boolean onMouseClick(double x, double y, int button, boolean doubleClick) { return false; }
    protected void onMouseRelease(int button) {}
    protected boolean onMouseDrag(double x, double y, int button, double dx, double dy) { return false; }
    protected boolean onKeyPress(int key) { return false; }

    // Minecraft 26.3 numbers mouse buttons from 1 as SDL does; older versions number them from 0.
    protected static boolean isLeftButton(int button) { return button == InputConstants.MOUSE_BUTTON_LEFT; }

    protected void renderWidgets(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        //? if >=1.21.6 {
        super.extractRenderState(gfx, mouseX, mouseY, delta);
        //?} else {
        /*// Before 1.21.6, Screen.render also blurs everything already drawn.
        for (var widget : this.children()) {
            if (widget instanceof net.minecraft.client.gui.components.Renderable renderable)
                renderable.render(gfx, mouseX, mouseY, delta);
        }
        *///?}
    }

    //? if >=1.21.9 {
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick) || onMouseClick(event.x(), event.y(), event.button(), doubleClick);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        onMouseRelease(event.button());
        return super.mouseReleased(event);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return onMouseDrag(event.x(), event.y(), event.button(), dx, dy) || super.mouseDragged(event, dx, dy);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        return onKeyPress(event.key()) || super.keyPressed(event);
    }
    //?} else {
    /*private long lastClick;
    private double lastClickX, lastClickY;
    @Override public boolean mouseClicked(double x, double y, int button) {
        long now = System.nanoTime();
        boolean doubleClick = isLeftButton(button) && now - lastClick < 250_000_000L
                && Math.abs(x - lastClickX) < 4 && Math.abs(y - lastClickY) < 4;
        lastClick = now;
        lastClickX = x;
        lastClickY = y;
        return super.mouseClicked(x, y, button) || onMouseClick(x, y, button, doubleClick);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        onMouseRelease(button);
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return onMouseDrag(x, y, button, dx, dy) || super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        return onKeyPress(key) || super.keyPressed(key, scanCode, modifiers);
    }
    *///?}
    //? if <1.21 {
    /*// Newer Screen.render draws the dimmed background itself, and scrolling reports both axes.
    @Override public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        renderBackground(gfx);
        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        return mouseScrolled(x, y, 0, amount);
    }
    public boolean mouseScrolled(double x, double y, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(x, y, verticalAmount);
    }
    *///?}
}
