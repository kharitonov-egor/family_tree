package com.egakh.familytree.util;

import com.egakh.familytree.settings.FamilyTreeServerSettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

import java.util.UUID;

public final class PetFilter {
    private PetFilter() {}

    /** Pets enter the history on their own once tamed. Other animals need a player to breed, name, or add them. */
    public static boolean isTrackable(Entity entity) {
        boolean tamed = entity instanceof TamableAnimal tameable ? tameable.isTame()
                : entity instanceof AbstractHorse horse && horse.isTamed() && ownerOf(horse) != null;
        return tamed && records(entity, AnimalCategory.PETS);
    }

    /** Applies the settings file, then the datapack tags, then the category switches. */
    public static boolean records(Entity entity, AnimalCategory category) {
        return FamilyTreeServerSettings.tracking().records(species(entity), category,
                SpeciesTags.has(entity.getType(), SpeciesTags.TRACKED), SpeciesTags.has(entity.getType(), SpeciesTags.IGNORED));
    }

    public static boolean isTameableSpecies(Entity entity) {
        return entity instanceof TamableAnimal || entity instanceof AbstractHorse;
    }

    public static boolean recordsNewAnimal(Entity entity) {
        return entity instanceof Animal && !isTameableSpecies(entity)
                && records(entity, category(entity));
    }

    public static AnimalCategory category(Entity entity) {
        String species = species(entity);
        AnimalCategory.learn(species, isTameableSpecies(entity));
        return AnimalCategory.of(species);
    }

    public static String species(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    /**
     * Camels always report as tamed, and skeleton trap horses are tamed without an owner.
     * These mounts are recorded once a player rides or breeds them.
     */
    public static boolean isUnclaimedMount(Entity entity) {
        return entity instanceof AbstractHorse horse && horse.isTamed() && ownerOf(horse) == null;
    }

    public static UUID ownerOf(Entity entity) {
        //? if >=1.21.5 {
        if (entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
            return ownable.getOwnerReference().getUUID();
        }
        //?} else {
        /*if (entity instanceof OwnableEntity ownable) return ownable.getOwnerUUID();
        *///?}
        return null;
    }
}
