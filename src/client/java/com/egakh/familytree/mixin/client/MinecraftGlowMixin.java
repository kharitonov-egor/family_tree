package com.egakh.familytree.mixin.client;

import com.egakh.familytree.client.relatives.WorldRelatives;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Outlines a highlighted relative on this client only. The server and other players never see it. */
@Mixin(Minecraft.class)
abstract class MinecraftGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void familytree$outlineRelative(Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (WorldRelatives.outlineColor(entity) != 0) result.setReturnValue(true);
    }
}
