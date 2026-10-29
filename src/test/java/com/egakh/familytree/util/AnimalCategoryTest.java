package com.egakh.familytree.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.egakh.familytree.util.AnimalCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class AnimalCategoryTest {
    @Test void vanillaSpeciesHaveFixedCategories() {
        for (String pet : new String[]{"minecraft:wolf", "minecraft:cat", "minecraft:parrot", "minecraft:horse",
                "minecraft:mule", "minecraft:trader_llama", "minecraft:camel", "minecraft:nautilus"}) {
            assertEquals(PETS, of(pet), pet);
            assertFalse(knownNonPet(pet), pet);
        }
        for (String farm : new String[]{"minecraft:cow", "minecraft:mooshroom", "minecraft:sheep", "minecraft:pig",
                "minecraft:chicken", "minecraft:rabbit", "minecraft:goat"}) {
            assertEquals(FARM, of(farm), farm);
            assertTrue(knownNonPet(farm), farm);
        }
        for (String wild : new String[]{"minecraft:fox", "minecraft:axolotl", "minecraft:frog", "minecraft:bee",
                "minecraft:turtle", "minecraft:panda", "minecraft:ocelot", "minecraft:hoglin", "minecraft:strider",
                "minecraft:sniffer", "minecraft:armadillo"}) {
            assertEquals(WILDLIFE, of(wild), wild);
            assertTrue(knownNonPet(wild), wild);
        }
        learn("minecraft:cow", true);
        assertEquals(FARM, of("minecraft:cow"), "Vanilla classification never changes");
    }

    @Test void moddedSpeciesStayProtectedUntilAnEntityShowsTheyAreNotPets() {
        String animal = "testmod:animal_" + UUID.randomUUID().toString().substring(0, 8);
        assertEquals(MODDED, of(animal));
        assertFalse(knownNonPet(animal), "Limits and cleanup skip species they have not seen");
        int version = learnedVersion();
        learn(animal, false);
        assertTrue(knownNonPet(animal));
        assertEquals(MODDED, of(animal));
        assertTrue(learnedVersion() > version);
        version = learnedVersion();
        learn(animal, false);
        assertEquals(version, learnedVersion(), "Seeing the same species again changes nothing");

        String pet = "testmod:pet_" + UUID.randomUUID().toString().substring(0, 8);
        learn(pet, true);
        assertEquals(PETS, of(pet));
        assertFalse(knownNonPet(pet));
    }
}
