package com.egakh.familytree.mixin;

import net.minecraft.world.entity.animal.equine.Llama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Llama.class)
public interface LlamaVariantInvoker {
    @Invoker("setVariant")
    void familytree$setVariant(Llama.Variant variant);
}
