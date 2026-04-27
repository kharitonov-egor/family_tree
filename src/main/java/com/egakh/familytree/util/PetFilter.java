package com.egakh.familytree.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

public final class PetFilter {
    private PetFilter() {}

    public static boolean isTrackable(Entity entity) {
        if (entity instanceof TamableAnimal tameable) {
            return tameable.isTame();
        }
        if (entity instanceof AbstractHorse horse) {
            return horse.isTamed();
        }
        return false;
    }

    public static boolean isTameableSpecies(Entity entity) {
        return entity instanceof TamableAnimal || entity instanceof AbstractHorse;
    }
}
