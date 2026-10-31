package com.egakh.familytree.mixin.client;

import com.egakh.familytree.client.relatives.RelativeLabels;
import com.egakh.familytree.client.relatives.WorldRelatives;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//?}
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?}
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} else if >=1.21.9 {
/*import net.minecraft.client.renderer.state.CameraRenderState;
*///?}
//? if <1.21.9 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
*///?}

/**
 * A highlighted relative shows its relation and name in place of its own name tag, drawn by {@link RelativeLabels}.
 * Before 1.21.2, render returns early when the entity shows no name, so the label hooks every return.
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererLabelMixin<T extends Entity> {
    //? if >=1.21.2 {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void familytree$relativeLabel(T entity, EntityRenderState state, float partialTick, CallbackInfo info) {
        Component label = WorldRelatives.label(entity);
        if (label == null || state.distanceToCameraSq > WorldRelatives.LABEL_RANGE_SQ) {
            RelativeLabels.remember(state, null);
            return;
        }
        state.nameTag = null;
        RelativeLabels.remember(state, new RelativeLabels.Label(label, RelativeLabels.base(entity)));
    }
    //?}

    //? if >=1.21.9 {
    @Inject(method = "submit", at = @At("RETURN"))
    private void familytree$drawLabel(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                      CameraRenderState camera, CallbackInfo info) {
        RelativeLabels.Label label = RelativeLabels.pending(state);
        if (label != null) RelativeLabels.draw(poseStack, collector, camera.orientation, label, state.distanceToCameraSq);
    }
    //?} else if >=1.21.2 {
    /*@Inject(method = "render", at = @At("RETURN"))
    private void familytree$drawLabel(EntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo info) {
        RelativeLabels.Label label = RelativeLabels.pending(state);
        if (label != null) RelativeLabels.draw(poseStack, buffers,
                Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation(), label, state.distanceToCameraSq);
    }
    *///?} else {
    /*@Inject(method = "render", at = @At("RETURN"))
    private void familytree$drawLabel(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo info) {
        Component label = WorldRelatives.label(entity);
        if (label == null) return;
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        double distanceSq = dispatcher.distanceToSqr(entity);
        if (distanceSq <= WorldRelatives.LABEL_RANGE_SQ) RelativeLabels.draw(poseStack, buffers, dispatcher.cameraOrientation(),
                new RelativeLabels.Label(label, RelativeLabels.base(entity)), distanceSq);
    }
    *///?}

    //? if >=1.21 && <1.21.2 {
    /*@Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void familytree$hideName(T entity, Component name, PoseStack poseStack, MultiBufferSource buffers, int light, float partialTick, CallbackInfo info) {
        if (WorldRelatives.label(entity) != null) info.cancel();
    }
    *///?} else if <1.21 {
    /*@Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void familytree$hideName(T entity, Component name, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo info) {
        if (WorldRelatives.label(entity) != null) info.cancel();
    }
    *///?}
}
