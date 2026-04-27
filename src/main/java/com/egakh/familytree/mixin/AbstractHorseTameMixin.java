package com.egakh.familytree.mixin;

import com.egakh.familytree.event.PetLifecycleListeners;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorse.class)
public abstract class AbstractHorseTameMixin {

    @Inject(method = "tameWithName(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("RETURN"))
    private void familytree$onTameWithName(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && player instanceof ServerPlayer serverPlayer) {
            PetLifecycleListeners.onHorseTamed((AbstractHorse) (Object) this, serverPlayer);
        }
    }
}
