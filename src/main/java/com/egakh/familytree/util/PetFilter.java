package com.egakh.familytree.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

import java.util.UUID;

public final class PetFilter {
    private PetFilter() {}

    public static boolean isTrackable(Entity entity) {
        if (entity instanceof TamableAnimal tameable) {
            return tameable.isTame();
        }
        if (entity instanceof AbstractHorse horse) {
            return horse.isTamed() && ownerOf(horse) != null;
        }
        return false;
    }

    public static boolean isTameableSpecies(Entity entity) {
        return entity instanceof TamableAnimal || entity instanceof AbstractHorse;
    }

    /**
     * Camels always report as tamed, and skeleton trap horses are tamed without an owner.
     * These mounts are recorded once a player rides or breeds them.
     */
    public static boolean isUnclaimedMount(Entity entity) {
        return entity instanceof AbstractHorse horse && horse.isTamed() && ownerOf(horse) == null;
    }

    public static UUID ownerOf(Entity entity) {
        //? if >=26.1 {
        if (entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
            return ownable.getOwnerReference().getUUID();
        }
        //?} else {
        /*if (entity instanceof OwnableEntity ownable) return ownable.getOwnerUUID();
        *///?}
        return null;
    }
}
