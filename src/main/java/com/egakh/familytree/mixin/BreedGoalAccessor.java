package com.egakh.familytree.mixin;

import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BreedGoal.class)
public interface BreedGoalAccessor {
    @Accessor("animal")
    Animal familytree$animal();

    @Accessor("partner")
    Animal familytree$partner();
}
