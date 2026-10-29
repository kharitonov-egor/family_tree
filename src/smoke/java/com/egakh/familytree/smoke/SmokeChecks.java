package com.egakh.familytree.smoke;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.naming.PetRenaming;
import com.egakh.familytree.permissions.PetAccess;
import com.egakh.familytree.platform.FamilyTreePaths;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import com.egakh.familytree.util.EntityVariants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
            String animals = animalChecks(server, world, state);
            Files.writeString(Path.of("smoke-result.txt"), "PASS: old history=" + oldCount
                    + "; automatic discovery; breeding mixin; owner inheritance; permissions; rename; death record"
                    + "; horse discovery, breeding, and owner inheritance; horse stats at birth and unload; llama strength"
                    + "; unclaimed camel and skeleton horse ignored; " + animals + "\n");
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

    /** Animals other than pets: entry rules, ownership, variants, settings, the farm limit, and opt-in cleanup. */
    private static String animalChecks(MinecraftServer server, ServerLevel world, FamilyTreeState state) throws Exception {
        Path config = FamilyTreePaths.config().resolve("familytree-server.properties");
        String original = Files.exists(config) ? Files.readString(config) : null;
        try {
            UUID breeder = UUID.randomUUID();
            Animal wild = place(world, "COW", 30), mate = place(world, "COW", 32);
            require(!state.contains(wild.getUUID()), "A wild cow was recorded when it loaded");
            Animal wildCalf = child(wild, mate, world);
            wild.finalizeSpawnChildFromBreeding(world, mate, wildCalf);
            require(!state.contains(wildCalf.getUUID()) && !state.contains(wild.getUUID()),
                    "Breeding without a player recorded wild cows");

            Animal ewe = place(world, "SHEEP", 34), ram = place(world, "SHEEP", 36);
            require(EntityVariants.apply(ewe, "red", server.registryAccess())
                    && EntityVariants.apply(ram, "white", server.registryAccess()), "Sheep colors were not applied");
            Animal lamb = child(ewe, ram, world);
            PetLifecycleListeners.recordBirth(world, ewe, ram, lamb, breeder, "Breeder");
            world.addFreshEntity(lamb);
            AnimalRecord lambRecord = state.get(lamb.getUUID());
            require(lambRecord != null && ewe.getUUID().equals(lambRecord.parentA()) && ram.getUUID().equals(lambRecord.parentB()),
                    "A player's breeding did not record the lamb's parents");
            require(breeder.equals(lambRecord.ownerId()) && breeder.equals(state.get(ewe.getUUID()).ownerId()),
                    "The breeder does not own the sheep family");
            require("pink".equals(lambRecord.variantId()) && "red".equals(state.get(ewe.getUUID()).variantId()),
                    "Wool colors were not recorded: " + lambRecord.variantId());
            require(lambRecord.nameAutogenerated() && lambRecord.name().startsWith("Sheep "), "Farm name: " + lambRecord.name());

            Animal secondLamb = child(ewe, ram, world);
            ewe.finalizeSpawnChildFromBreeding(world, ram, secondLamb);
            require(state.contains(secondLamb.getUUID()) && breeder.equals(state.get(secondLamb.getUUID()).ownerId()),
                    "A recorded family stopped growing when bred without a player");

            Animal axolotl = place(world, "AXOLOTL", 40);
            require(EntityVariants.apply(axolotl, "blue", server.registryAccess()), "Axolotl color was not applied");
            require(PetLifecycleListeners.addAnimal(world, state, axolotl, breeder, "Breeder") == PetLifecycleListeners.AddResult.ADDED
                    && "blue".equals(state.get(axolotl.getUUID()).variantId()), "Adding an axolotl lost its color");

            StringBuilder variants = new StringBuilder();
            String[][] checks = {{"AXOLOTL", "blue"}, {"PANDA", "brown/brown"}, {"RABBIT", "salt"}, {"FOX", "snow"},
                    {"MOOSHROOM", "brown"}, {"FROG", "minecraft:cold"}, {"PARROT", "gray"}, {"SHEEP", "pink"},
                    {"LLAMA", "gray"}, {"COW", "minecraft:warm"}, {"PIG", "minecraft:cold"}, {"CHICKEN", "minecraft:warm"}};
            // Cows, pigs, and chickens have variants from Minecraft 1.21.5.
            boolean farmVariants = exists("net.minecraft.world.entity.animal.CowVariant")
                    || exists("net.minecraft.world.entity.animal.cow.CowVariant");
            for (String[] check : checks) {
                Animal animal = create(world, check[0]);
                if (!farmVariants && List.of("COW", "PIG", "CHICKEN").contains(check[0])) continue;
                require(EntityVariants.apply(animal, check[1], server.registryAccess()), check[0] + " variant was not applied");
                require(check[1].equals(EntityVariants.read(animal)), check[0] + " variant reads " + EntityVariants.read(animal));
                variants.append(variants.length() == 0 ? "" : ",").append(check[0].toLowerCase(java.util.Locale.ROOT));
            }

            Files.writeString(config, "trackFarmAnimals=false\n");
            FamilyTreeServerSettings.load();
            Animal sow = place(world, "PIG", 42), boar = place(world, "PIG", 44), piglet = child(sow, boar, world);
            PetLifecycleListeners.recordBirth(world, sow, boar, piglet, breeder, "Breeder");
            require(!state.contains(piglet.getUUID()) && !state.contains(sow.getUUID()), "A disabled category was recorded");

            long pets = state.all().stream().filter(record -> record.speciesId().equals("minecraft:wolf")).count();
            int limit = state.recountUnnamedFarmRecords() + 3;
            Files.writeString(config, "farmRecordLimit=" + limit + "\n");
            FamilyTreeServerSettings.load();
            Animal cow = place(world, "COW", 46), bull = place(world, "COW", 48);
            Animal calf = child(cow, bull, world), lateCalf = child(cow, bull, world);
            PetLifecycleListeners.recordBirth(world, cow, bull, calf, breeder, "Breeder");
            require(state.contains(calf.getUUID()), "A calf under the farm limit was not recorded");
            PetLifecycleListeners.recordBirth(world, cow, bull, lateCalf, breeder, "Breeder");
            require(!state.contains(lateCalf.getUUID()), "The farm limit did not stop a new unnamed calf");

            PetLifecycleListeners.onDeath(calf, world.damageSources().fall());
            Files.writeString(config, "farmRecordLimit=" + limit + "\nautoPruneFarmRecords=true\n");
            FamilyTreeServerSettings.load();
            Path backups = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("familytree-backups");
            long before = cleanupBackups(backups);
            PetLifecycleListeners.recordBirth(world, cow, bull, lateCalf, breeder, "Breeder");
            require(cleanupBackups(backups) == before + 1, "Cleanup did not write a backup first");
            require(!state.contains(calf.getUUID()) && state.contains(lateCalf.getUUID()),
                    "Cleanup did not make room by removing the deceased unnamed calf");
            require(state.contains(lamb.getUUID()) && state.contains(ewe.getUUID()), "Cleanup removed living animals");
            require(pets == state.all().stream().filter(record -> record.speciesId().equals("minecraft:wolf")).count(),
                    "Cleanup removed pets");
            return "farm animals: wild ignored, player breeding, lineage without a player, added axolotl, variants ("
                    + variants + "), disabled category, farm limit, cleanup with backup";
        } finally {
            if (original == null) Files.deleteIfExists(config);
            else Files.writeString(config, original);
            FamilyTreeServerSettings.load();
        }
    }

    private static boolean exists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException missing) {
            return false;
        }
    }

    private static long cleanupBackups(Path directory) throws java.io.IOException {
        if (!Files.exists(directory)) return 0;
        try (var files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().startsWith("before-farm-cleanup-")).count();
        }
    }

    private static Animal place(ServerLevel world, String type, double x) throws Exception {
        Animal animal = create(world, type);
        animal.setPos(x, 64, 6);
        world.addFreshEntity(animal);
        return animal;
    }

    private static Animal child(Animal parent, Animal mate, ServerLevel world) {
        Animal child = (Animal) parent.getBreedOffspring(world, mate);
        child.setPos(parent.getX(), parent.getY(), parent.getZ() + 2);
        return child;
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
            Class<?> reference = Class.forName("net.minecraft.world.entity.EntityReference");
            try {
                field.set(horse, reference.getMethod("of", UUID.class).invoke(null, owner));
            } catch (NoSuchMethodException before1_21_9) {
                field.set(horse, reference.getConstructor(UUID.class).newInstance(owner));
            }
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
