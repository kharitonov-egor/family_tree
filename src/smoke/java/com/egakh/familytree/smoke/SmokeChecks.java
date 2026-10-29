package com.egakh.familytree.smoke;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.naming.PetRenaming;
import com.egakh.familytree.permissions.PetAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Runs only in the separate smoke-test mod. */
public final class SmokeChecks {
    private SmokeChecks() {}

    public static void run(MinecraftServer server) {
        try {
            ServerLevel world = server.overworld();
            world.getChunk(0, 0);
            FamilyTreeState state = FamilyTreeState.get(server);
            int oldCount = state.all().size();
            if (Integer.getInteger("familytree.smoke.minimumHistory", 0) > oldCount)
                throw new AssertionError("Previous-release history did not load");
            Animal first = create(world, "WOLF"), second = create(world, "WOLF"), child = create(world, "WOLF");
            first.setPos(2, 64, 2);
            second.setPos(4, 64, 2);
            child.setPos(6, 64, 2);
            tame(first);
            tame(second);
            tame(child);
            world.addFreshEntity(first);
            world.addFreshEntity(second);
            world.addFreshEntity(child);
            require(state.contains(first.getUUID()) && state.contains(second.getUUID()) && state.contains(child.getUUID()),
                    "Entity-load discovery failed");
            UUID owner = UUID.randomUUID();
            state.update(first.getUUID(), pet -> pet.setOwner(owner, "Smoke owner"));
            state.update(second.getUUID(), pet -> pet.setOwner(owner, "Smoke owner"));
            first.finalizeSpawnChildFromBreeding(world, second, child);
            AnimalRecord baby = state.get(child.getUUID());
            require(first.getUUID().equals(baby.parentA()) && second.getUUID().equals(baby.parentB()), "Breeding mixin lost parents");
            require(owner.equals(baby.ownerId()), "Newborn did not inherit the recorded owner");
            require(!PetAccess.canManage(baby, UUID.randomUUID(), false), "Foreign player can edit pet");
            require(PetRenaming.rename(baby, owner, false, "Smoke puppy") == PetRenaming.Result.SUCCESS, "Rename failed");
            require(!baby.originalName().equals(baby.name()), "Tree rename changed original name");
            PetLifecycleListeners.onDeath(child, world.damageSources().fall());
            require(baby.deceased() && baby.deathCause() != null, "Death history missing");
            require(state.all().size() >= oldCount + 3, "Old history disappeared");
            horseChecks(world, state);
            Files.writeString(Path.of("smoke-result.txt"), "PASS: old history=" + oldCount
                    + "; automatic discovery; breeding mixin; owner inheritance; permissions; rename; death record"
                    + "; horse discovery, breeding, and owner inheritance; horse stats at birth and unload; llama strength"
                    + "; unclaimed camel and skeleton horse ignored\n");
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(Path.of("smoke-result.txt"), "FAIL: " + failure); } catch (Exception ignored) {}
        } finally {
            server.halt(false);
        }
    }

    private static void horseChecks(ServerLevel world, FamilyTreeState state) throws Exception {
        UUID owner = UUID.randomUUID();
        Animal mare = create(world, "HORSE"), stallion = create(world, "HORSE");
        tame(mare, owner);
        tame(stallion, owner);
        mare.setPos(10, 64, 2);
        stallion.setPos(12, 64, 2);
        world.addFreshEntity(mare);
        world.addFreshEntity(stallion);
        AnimalRecord mareRecord = state.get(mare.getUUID());
        require(mareRecord != null && owner.equals(mareRecord.ownerId()), "Owned horse was not discovered");
        require(sameStats(mareRecord.horseStats(), mare), "Horse stats were not recorded on discovery");

        Animal foal = (Animal) mare.getBreedOffspring(world, stallion);
        foal.setPos(11, 64, 4);
        mare.finalizeSpawnChildFromBreeding(world, stallion, foal);
        world.addFreshEntity(foal);
        AnimalRecord foalRecord = state.get(foal.getUUID());
        require(foalRecord != null && mare.getUUID().equals(foalRecord.parentA())
                && stallion.getUUID().equals(foalRecord.parentB()), "Foal lost its parents");
        require(owner.equals(foalRecord.ownerId()), "Foal did not inherit the owner");
        require(sameStats(foalRecord.horseStats(), foal), "Foal stats were not recorded at birth");
        foal.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
        PetLifecycleListeners.onUnload(foal, world);
        require(foalRecord.horseStats().speed() == 0.3, "Unloading the foal did not refresh its stats");

        Animal llama = create(world, "LLAMA");
        tame(llama, owner);
        llama.setPos(14, 64, 2);
        world.addFreshEntity(llama);
        Object strength = llama.getClass().getMethod("getStrength").invoke(llama);
        require(state.get(llama.getUUID()) != null && strength.equals(state.get(llama.getUUID()).horseStats().strength()),
                "Llama strength was not recorded");

        Animal camel = create(world, "CAMEL"), trapHorse = create(world, "SKELETON_HORSE");
        tame(trapHorse, null);
        camel.setPos(16, 64, 2);
        trapHorse.setPos(18, 64, 2);
        world.addFreshEntity(camel);
        world.addFreshEntity(trapHorse);
        require(!state.contains(camel.getUUID()), "A camel without a rider was recorded");
        require(!state.contains(trapHorse.getUUID()), "An unclaimed skeleton horse was recorded");
    }

    private static boolean sameStats(HorseStats stats, Animal horse) {
        return stats != null
                && stats.speed() == horse.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)
                && stats.jumpStrength() == horse.getAttributeBaseValue(Attributes.JUMP_STRENGTH)
                && stats.maxHealth() == horse.getAttributeBaseValue(Attributes.MAX_HEALTH);
    }

    // Reflection keeps this development-only runner usable across the supported game APIs.
    private static void tame(Animal horse, UUID owner) throws Exception {
        horse.getClass().getMethod("setTamed", boolean.class).invoke(horse, true);
        if (owner == null) return;
        try {
            horse.getClass().getMethod("setOwnerUUID", UUID.class).invoke(horse, owner);
        } catch (NoSuchMethodException newerVersion) {
            Class<?> type = horse.getClass();
            while (!type.getSimpleName().equals("AbstractHorse")) type = type.getSuperclass();
            var field = type.getDeclaredField("owner");
            field.setAccessible(true);
            field.set(horse, Class.forName("net.minecraft.world.entity.EntityReference").getMethod("of", UUID.class).invoke(null, owner));
        }
    }

    private static Animal create(ServerLevel world, String name) throws Exception {
        Class<?> types;
        try { types = Class.forName("net.minecraft.world.entity.EntityTypes"); }
        catch (ClassNotFoundException olderVersion) { types = Class.forName("net.minecraft.world.entity.EntityType"); }
        Object type = types.getField(name).get(null);
        for (var method : type.getClass().getMethods()) {
            if (!method.getName().equals("create")) continue;
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(world))
                return (Animal) method.invoke(type, world);
            if (method.getParameterCount() == 2 && method.getParameterTypes()[0].isInstance(world)
                    && method.getParameterTypes()[1].isEnum()) {
                Object reason = java.util.Arrays.stream(method.getParameterTypes()[1].getEnumConstants())
                        .filter(value -> value.toString().equals("LOAD")).findFirst().orElseThrow();
                return (Animal) method.invoke(type, world, reason);
            }
        }
        throw new IllegalStateException("No entity factory");
    }

    private static void tame(Animal animal) throws Exception {
        for (var method : TamableAnimal.class.getMethods()) {
            if (!method.getName().equals("setTame")) continue;
            if (method.getParameterCount() == 2) method.invoke(animal, true, false);
            else if (method.getParameterCount() == 1) method.invoke(animal, true);
            else continue;
            return;
        }
        throw new IllegalStateException("No tame setter");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
