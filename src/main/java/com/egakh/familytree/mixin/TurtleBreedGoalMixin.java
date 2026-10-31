package com.egakh.familytree.mixin;

import com.egakh.familytree.event.PetLifecycleListeners;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Turtles breed through their own goal, which gives the mother an egg without {@code finalizeSpawnChildFromBreeding}. */
@Mixin(targets = "net.minecraft.world.entity.animal.turtle.Turtle$TurtleBreedGoal")
public abstract class TurtleBreedGoalMixin {

    @Inject(method = "breed()V", at = @At("TAIL"))
    private void familytree$onEggFertilized(CallbackInfo ci) {
        BreedGoalAccessor goal = (BreedGoalAccessor) this;
        Animal parent = goal.familytree$animal(), mate = goal.familytree$partner();
        if (mate != null && parent.level() instanceof ServerLevel world)
            PetLifecycleListeners.onBredWithoutBaby(world, parent, mate);
    }
}
