package com.egakh.familytree.mixin;

import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Markings;
import net.minecraft.world.entity.animal.equine.Variant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Horse.class)
public interface HorseVariantInvoker {
    @Invoker("setVariantAndMarkings")
    void familytree$setVariantAndMarkings(Variant variant, Markings markings);
}
