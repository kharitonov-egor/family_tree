package com.egakh.familytree.mixin;

import com.egakh.familytree.event.PetLifecycleListeners;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Foxes breed through their own goal, which adds the kit to the world without {@code finalizeSpawnChildFromBreeding}. */
@Mixin(targets = "net.minecraft.world.entity.animal.fox.Fox$FoxBreedGoal")
public abstract class FoxBreedGoalMixin {

    @ModifyArg(
            method = "breed()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V")
    )
    private Entity familytree$onKitBorn(Entity kit) {
        BreedGoalAccessor goal = (BreedGoalAccessor) this;
        Animal parent = goal.familytree$animal(), mate = goal.familytree$partner();
        if (kit instanceof Animal child && mate != null && parent.level() instanceof ServerLevel world)
            PetLifecycleListeners.onBred(world, parent, mate, child);
        return kit;
    }
}
