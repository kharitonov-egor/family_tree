package com.egakh.familytree.mixin.client;

import com.egakh.familytree.client.relatives.WorldRelatives;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Minecraft colors an entity's outline with its team color, so a highlighted relative reports its relation's color. */
@Mixin(Entity.class)
abstract class EntityOutlineColorMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void familytree$relationColor(CallbackInfoReturnable<Integer> result) {
        int color = WorldRelatives.outlineColor((Entity) (Object) this);
        if (color != 0) result.setReturnValue(color);
    }
}
