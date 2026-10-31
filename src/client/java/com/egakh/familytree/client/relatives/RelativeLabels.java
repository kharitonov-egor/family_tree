package com.egakh.familytree.client.relatives;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.joml.Quaternionf;
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;

import java.util.Map;
import java.util.WeakHashMap;
//?}

/**
 * Draws a relative's label in place of its name tag: above the head and ears, on a dark background, in front of
 * whatever is between it and the camera, and at a size on screen that stops growing up close and stops shrinking far away.
 */
public final class RelativeLabels {
    public record Label(Component text, double base) {}

    private static final float NAME_TAG_SCALE = 0.025F;
    private static final double NEAR = 6.0, FAR = 16.0;
    private static final double HEAD_ROOM = 0.3, EQUINE_HEAD = 0.35;
    private static final int TEXT = 0xFFFFFFFF, BACKGROUND = 0xA0000000, FULL_BRIGHT = 0xF000F0;

    //? if >=1.21.2 {
    private static final Map<EntityRenderState, Label> PENDING = new WeakHashMap<>();
    //?}

    private RelativeLabels() {}

    /** Height above the feet of the label's bottom edge. Horses, llamas, and camels hold their heads above their hitbox. */
    public static double base(Entity entity) {
        double height = entity.getBbHeight();
        return height + HEAD_ROOM + (entity instanceof AbstractHorse ? height * EQUINE_HEAD : 0);
    }

    /** A name tag's scale between {@link #NEAR} and {@link #FAR} blocks, and the same size on screen outside that range. */
    public static float scale(double distanceSq) {
        double distance = Math.sqrt(Math.max(0, distanceSq));
        if (distance == 0) return 0;
        return (float) (NAME_TAG_SCALE * distance / Math.max(NEAR, Math.min(FAR, distance)));
    }

    //? if >=1.21.2 {
    public static void remember(EntityRenderState state, Label label) {
        if (label != null) PENDING.put(state, label);
        else if (!PENDING.isEmpty()) PENDING.remove(state);
    }

    public static Label pending(EntityRenderState state) {
        return PENDING.isEmpty() ? null : PENDING.get(state);
    }
    //?}

    public static void draw(PoseStack poseStack,
                            //? if >=1.21.9 {
                            SubmitNodeCollector out,
                            //?} else {
                            /*MultiBufferSource out,
                            *///?}
                            Quaternionf orientation, Label label, double distanceSq) {
        float scale = scale(distanceSq);
        if (scale <= 0) return;
        Font font = Minecraft.getInstance().font;
        FormattedCharSequence text = label.text().getVisualOrderText();
        float x = -font.width(text) / 2.0F, y = -font.lineHeight;
        poseStack.pushPose();
        poseStack.translate(0, label.base(), 0);
        poseStack.last().pose().rotate(orientation);
        //? if >=1.21 {
        poseStack.scale(scale, -scale, scale);
        //?} else {
        /*poseStack.scale(-scale, -scale, scale);
        *///?}
        //? if >=1.21.9 {
        out.submitText(poseStack, x, y, text, false, Font.DisplayMode.SEE_THROUGH, FULL_BRIGHT, TEXT, BACKGROUND, 0);
        //?} else {
        /*font.drawInBatch(text, x, y, TEXT, false, poseStack.last().pose(), out, Font.DisplayMode.SEE_THROUGH, BACKGROUND, FULL_BRIGHT);
        *///?}
        poseStack.popPose();
    }
}
