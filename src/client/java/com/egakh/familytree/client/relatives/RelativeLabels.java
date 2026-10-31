package com.egakh.familytree.client.relatives;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
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
 * Draws a relative's label in place of its name tag: above the head and ears, on a dark background, over any model
 * in front of it, and at a size on screen that stops growing up close and stops shrinking far away.
 */
public final class RelativeLabels {
    public record Label(Component text, double base) {}

    private static final float NAME_TAG_SCALE = 0.025F;
    private static final double NEAR = 6.0, FAR = 12.0;
    private static final double HEAD_ROOM = 0.3, HORSE_HEAD = 0.6, LLAMA_HEAD = 0.2;
    /** Each of the two passes draws the background, so together they cover about 63% of what is behind the label. */
    private static final int TEXT = 0xFFFFFFFF, BACKGROUND = 0x64000000, FULL_BRIGHT = 0xF000F0;

    //? if >=1.21.2 {
    private static final Map<EntityRenderState, Label> PENDING = new WeakHashMap<>();
    //?}

    private RelativeLabels() {}

    /**
     * Height above the feet of the label's bottom edge. A horse's head and ears reach well above its hitbox and come
     * toward a camera it faces, so its label goes higher; a llama's head rises less, and a camel's hitbox covers its head.
     */
    public static double base(Entity entity) {
        double height = entity.getBbHeight();
        double head = entity instanceof Camel ? 0 : entity instanceof Llama ? LLAMA_HEAD : entity instanceof AbstractHorse ? HORSE_HEAD : 0;
        return height + HEAD_ROOM + height * head;
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
        // The see-through pass keeps the label over closer models; the normal pass writes depth so clouds behind it can't cover it.
        for (Font.DisplayMode mode : new Font.DisplayMode[]{Font.DisplayMode.SEE_THROUGH, Font.DisplayMode.NORMAL}) {
            //? if >=1.21.9 {
            out.submitText(poseStack, x, y, text, false, mode, FULL_BRIGHT, TEXT, BACKGROUND, 0);
            //?} else {
            /*font.drawInBatch(text, x, y, TEXT, false, poseStack.last().pose(), out, mode, BACKGROUND, FULL_BRIGHT);
            *///?}
        }
        poseStack.popPose();
    }
}
