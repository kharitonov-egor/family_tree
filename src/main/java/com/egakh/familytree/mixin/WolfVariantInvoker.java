package com.egakh.familytree.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Wolf.class)
public interface WolfVariantInvoker {
    @Invoker("getVariant")
    Holder<WolfVariant> familytree$getVariant();

    @Invoker("setVariant")
    void familytree$setVariant(Holder<WolfVariant> variant);
}
