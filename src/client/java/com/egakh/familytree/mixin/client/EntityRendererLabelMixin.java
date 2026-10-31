package com.egakh.familytree.mixin.client;

import com.egakh.familytree.client.relatives.WorldRelatives;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.EntityAttachment;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.ModifyArg;
*///?}

/**
 * A highlighted relative shows its relation and name where Minecraft draws name tags, in place of its own name tag.
 * The label sits a little higher so a horse's head facing the camera doesn't cover it. Before 1.21.2, render returns
 * early when the entity shows no name, so the label hooks every return rather than only the last.
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererLabelMixin<T extends Entity> {
    //? if >=1.21.2 {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void familytree$relativeLabel(T entity, EntityRenderState state, float partialTick, CallbackInfo info) {
        Component label = WorldRelatives.label(entity);
        if (label == null || state.distanceToCameraSq > WorldRelatives.LABEL_RANGE_SQ) return;
        state.nameTag = label;
        var attachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick));
        state.nameTagAttachment = attachment == null ? null : attachment.add(0, WorldRelatives.LABEL_LIFT, 0);
    }
    //?} else if >=1.21 {
    /*@Shadow protected abstract boolean shouldShowName(T entity);

    @Shadow protected abstract void renderNameTag(T entity, Component name, PoseStack poseStack, MultiBufferSource buffers, int light, float partialTick);

    @ModifyArg(method = "render", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V"))
    private Component familytree$replaceName(T entity, Component name, PoseStack poseStack, MultiBufferSource buffers, int light, float partialTick) {
        Component label = WorldRelatives.label(entity);
        return label == null ? name : label;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void familytree$relativeLabel(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo info) {
        Component label = WorldRelatives.label(entity);
        if (label == null || shouldShowName(entity)) return;
        poseStack.pushPose();
        poseStack.translate(0, WorldRelatives.LABEL_LIFT, 0);
        renderNameTag(entity, label, poseStack, buffers, light, partialTick);
        poseStack.popPose();
    }
    *///?} else {
    /*@Shadow protected abstract boolean shouldShowName(T entity);

    @Shadow protected abstract void renderNameTag(T entity, Component name, PoseStack poseStack, MultiBufferSource buffers, int light);

    @ModifyArg(method = "render", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private Component familytree$replaceName(T entity, Component name, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Component label = WorldRelatives.label(entity);
        return label == null ? name : label;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void familytree$relativeLabel(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo info) {
        Component label = WorldRelatives.label(entity);
        if (label == null || shouldShowName(entity)) return;
        poseStack.pushPose();
        poseStack.translate(0, WorldRelatives.LABEL_LIFT, 0);
        renderNameTag(entity, label, poseStack, buffers, light);
        poseStack.popPose();
    }
    *///?}
}
