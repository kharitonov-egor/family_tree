package com.egakh.familytree.client.export;

import com.egakh.familytree.client.screen.PetFaceRenderer;
import com.egakh.familytree.data.AnimalRecord;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Exports reuse the in-game model portraits, so any species a player sees in the tree also appears in the PNG.
 * Each portrait is drawn on black and on white in the same frame and read back from the screen. Comparing the
 * two recovers transparency, including translucent parts such as bee wings. The PNG itself is still drawn off
 * the render thread.
 */
public final class PortraitCapture {
    private static final int PORTRAIT_PIXELS = 96;
    private static final int GAP = 4;
    private static final int HEADER = 16;
    private static final int TIMEOUT_TICKS = 60;

    private record Slot(String key, AnimalRecord record, int x, int y, int size) {}

    private final List<AnimalRecord> pending = new ArrayList<>();
    private final Map<String, BufferedImage> captured = new HashMap<>();
    private final Consumer<Map<String, BufferedImage>> done;
    private List<Slot> batch = List.of();
    private int framesDrawn;
    private int ticks;
    private boolean reading;
    private boolean finished;

    public PortraitCapture(Collection<AnimalRecord> records, Consumer<Map<String, BufferedImage>> done) {
        Map<String, AnimalRecord> unique = new LinkedHashMap<>();
        for (AnimalRecord record : records) unique.putIfAbsent(key(record), record);
        pending.addAll(unique.values());
        this.done = done;
    }

    public static String key(AnimalRecord record) {
        return record.speciesId() + "|" + record.variantId();
    }

    /** Call it after everything else the screen draws, so nothing covers the portraits. */
    public void draw(GuiGraphicsExtractor gfx, Font font, int screenWidth, int top, int bottom) {
        if (finished) return;
        if (batch.isEmpty() && !reading) nextBatch(screenWidth, top, bottom);
        //? if <1.21.6 {
        /*// Before 1.21.6 the GUI keeps a depth buffer, and the cards' text and models sit above z=0.
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 400);
        *///?}
        gfx.fill(0, top, screenWidth, bottom, 0xFF151A21);
        gfx.centeredText(font, Component.translatable("familytree.export.portraits"), screenWidth / 2, top + 4, 0xFFB0B7C0);
        for (Slot slot : batch) {
            gfx.fill(slot.x(), slot.y(), slot.x() + slot.size(), slot.y() + slot.size(), 0xFF000000);
            PetFaceRenderer.drawFace(gfx, font, slot.record(), slot.x(), slot.y(), slot.size());
            int white = slot.x() + slot.size() + GAP;
            gfx.fill(white, slot.y(), white + slot.size(), slot.y() + slot.size(), 0xFFFFFFFF);
            PetFaceRenderer.drawFace(gfx, font, slot.record(), white, slot.y(), slot.size());
        }
        //? if <1.21.6 {
        /*gfx.pose().popPose();
        *///?}
        framesDrawn++;
    }

    /** A minimized window never draws, so the capture gives up after a while. */
    public void tick(Minecraft client, int screenWidth) {
        if (finished) return;
        if (++ticks > TIMEOUT_TICKS) {
            finish();
            return;
        }
        if (reading || batch.isEmpty() || framesDrawn < 2) return;
        reading = true;
        List<Slot> slots = batch;
        double scale = (double) client.getWindow().getWidth() / screenWidth;
        //? if >=26.2 {
        /*var target = client.gameRenderer.mainRenderTarget();
        *///?} else {
        var target = client.getMainRenderTarget();
        //?}
        //? if >=1.21.5 {
        Screenshot.takeScreenshot(target, image -> client.execute(() -> readBatch(image, slots, scale)));
        //?} else {
        /*readBatch(Screenshot.takeScreenshot(target), slots, scale);
        *///?}
    }

    public boolean finished() {
        return finished;
    }

    private void readBatch(NativeImage image, List<Slot> slots, double scale) {
        try (image) {
            for (Slot slot : slots) {
                BufferedImage portrait = combine(image, slot, scale);
                if (portrait != null) captured.put(slot.key(), portrait);
            }
        } catch (RuntimeException failure) {
            com.egakh.familytree.FamilyTreeMod.LOGGER.warn("Could not read export portraits", failure);
        }
        batch = List.of();
        framesDrawn = 0;
        reading = false;
        ticks = 0;
        if (pending.isEmpty()) finish();
    }

    private static BufferedImage combine(NativeImage image, Slot slot, double scale) {
        int size = (int) Math.round(slot.size() * scale);
        int blackX = (int) Math.round(slot.x() * scale), y = (int) Math.round(slot.y() * scale);
        int whiteX = (int) Math.round((slot.x() + slot.size() + GAP) * scale);
        if (size <= 0 || whiteX + size > image.getWidth() || y + size > image.getHeight()) return null;
        BufferedImage portrait = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        boolean visible = false;
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int argb = transparentPixel(argb(image, blackX + px, y + py), argb(image, whiteX + px, y + py));
                visible |= argb != 0;
                portrait.setRGB(px, py, argb);
            }
        }
        return visible ? portrait : null;
    }

    /** A pixel drawn over black and over white differs by how much background shows through it. */
    static int transparentPixel(int onBlack, int onWhite) {
        int alpha = 0;
        for (int shift = 0; shift <= 16; shift += 8)
            alpha += 255 - (((onWhite >> shift) & 0xFF) - ((onBlack >> shift) & 0xFF));
        alpha = Math.max(0, Math.min(255, Math.round(alpha / 3f)));
        if (alpha == 0) return 0;
        int rgb = 0;
        for (int shift = 0; shift <= 16; shift += 8)
            rgb |= Math.min(255, ((onBlack >> shift) & 0xFF) * 255 / alpha) << shift;
        return alpha << 24 | rgb;
    }

    private static int argb(NativeImage image, int x, int y) {
        //? if >=1.21.2 {
        return image.getPixel(x, y);
        //?} else {
        /*int abgr = image.getPixelRGBA(x, y);
        return abgr & 0xFF00FF00 | (abgr & 0xFF) << 16 | (abgr >> 16) & 0xFF;
        *///?}
    }

    private void nextBatch(int screenWidth, int top, int bottom) {
        if (pending.isEmpty()) return;
        double scale = (double) Minecraft.getInstance().getWindow().getWidth() / screenWidth;
        int size = Math.max(16, (int) Math.ceil(PORTRAIT_PIXELS / scale));
        int pairWidth = size * 2 + GAP;
        int perRow = Math.max(1, (screenWidth - GAP * 2) / (pairWidth + GAP * 2));
        int rows = Math.max(1, (bottom - top - HEADER - GAP) / (size + GAP));
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < perRow * rows && !pending.isEmpty(); i++) {
            AnimalRecord record = pending.remove(0);
            slots.add(new Slot(key(record), record, GAP * 2 + (i % perRow) * (pairWidth + GAP * 2),
                    top + HEADER + (i / perRow) * (size + GAP), size));
        }
        batch = slots;
        framesDrawn = 0;
    }

    private void finish() {
        if (finished) return;
        finished = true;
        done.accept(Map.copyOf(captured));
    }
}
