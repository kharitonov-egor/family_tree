package com.egakh.familytree.smoke;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.naming.PetRenaming;
import com.egakh.familytree.permissions.PetAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
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
            Animal first = wolf(world), second = wolf(world), child = wolf(world);
            first.setPos(2, 64, 2);
            second.setPos(4, 64, 2);
            child.setPos(6, 64, 2);
            ((TamableAnimal) first).setTame(true, false);
            ((TamableAnimal) second).setTame(true, false);
            ((TamableAnimal) child).setTame(true, false);
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
            Files.writeString(Path.of("smoke-result.txt"), "PASS: old history=" + oldCount
                    + "; automatic discovery; breeding mixin; owner inheritance; permissions; rename; death record\n");
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(Path.of("smoke-result.txt"), "FAIL: " + failure); } catch (Exception ignored) {}
        } finally {
            server.halt(false);
        }
    }

    private static Animal wolf(ServerLevel world) throws Exception {
        Class<?> types;
        try { types = Class.forName("net.minecraft.world.entity.EntityTypes"); }
        catch (ClassNotFoundException olderVersion) { types = Class.forName("net.minecraft.world.entity.EntityType"); }
        Object type = types.getField("WOLF").get(null);
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
