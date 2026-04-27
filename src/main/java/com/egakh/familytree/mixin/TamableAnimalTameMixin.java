package com.egakh.familytree.mixin;

import com.egakh.familytree.event.PetLifecycleListeners;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TamableAnimal.class)
public abstract class TamableAnimalTameMixin {

    @Inject(method = "tame(Lnet/minecraft/world/entity/player/Player;)V", at = @At("TAIL"))
    private void familytree$onTame(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            PetLifecycleListeners.onTamableAnimalTamed((TamableAnimal) (Object) this, serverPlayer);
        }
    }
}
