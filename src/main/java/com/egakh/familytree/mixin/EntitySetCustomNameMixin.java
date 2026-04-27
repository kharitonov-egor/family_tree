package com.egakh.familytree.mixin;

import com.egakh.familytree.event.PetLifecycleListeners;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntitySetCustomNameMixin {

    @Inject(method = "setCustomName(Lnet/minecraft/network/chat/Component;)V", at = @At("RETURN"))
    private void familytree$onCustomNameSet(Component name, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        PetLifecycleListeners.onCustomNameChanged(self, name);
    }
}
