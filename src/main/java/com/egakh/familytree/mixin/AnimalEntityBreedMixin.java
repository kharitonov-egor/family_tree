package com.egakh.familytree.mixin;

import com.egakh.familytree.event.PetLifecycleListeners;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
public abstract class AnimalEntityBreedMixin {

    @Inject(
            method = "finalizeSpawnChildFromBreeding(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;Lnet/minecraft/world/entity/AgeableMob;)V",
            at = @At("TAIL")
    )
    private void familytree$onBreed(ServerLevel world, Animal other, AgeableMob child, CallbackInfo ci) {
        if (child instanceof Animal childAnimal) {
            Animal self = (Animal) (Object) this;
            PetLifecycleListeners.onBred(world, self, other, childAnimal);
        }
    }
}
