package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Records don't store age, so loaded animals are checked directly. A foal grows up after one world day of loaded
 * time, so an unloaded animal born from recorded parents in the last two days may still be a foal.
 */
final class FoalCheck {
    private final Set<UUID> babies = new HashSet<>();
    private final Set<UUID> adults = new HashSet<>();
    private final long currentWorldDay;

    FoalCheck(Minecraft client, long currentWorldDay) {
        this.currentWorldDay = currentWorldDay;
        if (client == null || client.level == null) return;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof AgeableMob mob)) continue;
            (mob.isBaby() ? babies : adults).add(entity.getUUID());
        }
    }

    boolean mayBeFoal(AnimalRecord pet) {
        if (pet == null || babies.contains(pet.id())) return pet != null;
        if (adults.contains(pet.id())) return false;
        boolean bred = pet.parentA() != null || pet.parentB() != null;
        return bred && currentWorldDay - pet.birthWorldDay() < 2;
    }
}
