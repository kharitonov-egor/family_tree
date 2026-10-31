package com.egakh.familytree.demo;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.screen.FamilyTreeBrowserScreen;
import com.egakh.familytree.client.screen.FamilyTreeViewScreen;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.util.EntityVariants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/**
 * A disposable scene. The player names animals with real name tags and feeds sheep wheat. Vanilla AI breeds them.
 * With -PmoddedAnimal and -PmoddedFood the same steps run for a modded species instead of sheep.
 */
public final class AnimalCapture {
    private static final String MODDED = System.getProperty("familytree.demo.moddedAnimal", "");
    private static final String MODDED_FOOD = System.getProperty("familytree.demo.moddedFood", "minecraft:wheat");
    private int tick;
    private int bornTick;
    private Animal rosie, snowball, sky, poppy, azul, frogA, frogB;
    private volatile UUID pinkLamb, purpleLamb;
    private FamilyTreeBrowserScreen browser;
    private long exportsBefore;

    public void tick(Minecraft client, DemoClient runner) throws Exception {
        if (client.level == null || client.player == null) return;
        tick++;
        var server = client.getSingleplayerServer();
        boolean modded = !MODDED.isEmpty();
        String species = modded ? MODDED : "minecraft:sheep";
        if (tick == 1) {
            exportsBefore = runner.exportCount(client);
            server.execute(() -> {
                try {
                    for (String command : new String[]{"gamemode creative @a", "time set day", "weather clear",
                            "kill @e[type=!minecraft:player]", "fill -14 100 -4 14 100 18 grass_block",
                            "fill -9 101 0 9 101 0 oak_fence", "fill -9 101 9 9 101 9 oak_fence",
                            "fill -9 101 1 -9 101 8 oak_fence", "fill 9 101 1 9 101 8 oak_fence",
                            "fill 0 101 1 0 101 8 oak_fence", "fill 10 100 2 13 100 6 water", "tp @a 0 103 -6 0 25"})
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                    ServerLevel world = server.overworld();
                    ServerPlayer player = server.getPlayerList().getPlayer(client.player.getUUID());
                    FamilyTreeState.get(server).removeMatching(pet -> true);
                    rosie = animal(world, species, -6, 4, "red");
                    snowball = animal(world, species, -3, 6, "white");
                    sky = animal(world, species, 3, 4, "blue");
                    poppy = animal(world, species, 6, 6, "red");
                    nameTag(player, rosie, "Rosie");
                    nameTag(player, snowball, "Snowball");
                    nameTag(player, sky, "Sky");
                    nameTag(player, poppy, "Poppy");
                    if (!modded) {
                        azul = animal(world, "minecraft:axolotl", 11.5, 4, "blue");
                        nameTag(player, azul, "Azul");
                        frogA = animal(world, "minecraft:frog", -7, 7.5, "minecraft:cold");
                        frogB = animal(world, "minecraft:frog", -6, 7.5, "minecraft:warm");
                    }
                    var state = FamilyTreeState.get(server);
                    for (Animal named : modded ? List.of(rosie, snowball, sky, poppy) : List.of(rosie, snowball, sky, poppy, azul)) {
                        AnimalRecord record = state.get(named.getUUID());
                        if (record == null || !player.getUUID().equals(record.ownerId()))
                            throw new AssertionError("A name tag did not record " + named.getName().getString() + " for the player");
                    }
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
        }
        if (tick == 40) server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(client.player.getUUID());
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "item replace entity @a weapon.mainhand with " + (modded ? MODDED_FOOD : "minecraft:wheat") + " 64");
            for (Animal parent : List.of(rosie, snowball, sky, poppy)) parent.mobInteract(player, InteractionHand.MAIN_HAND);
            if (!modded) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "item replace entity @a weapon.mainhand with minecraft:slime_ball 64");
                frogA.mobInteract(player, InteractionHand.MAIN_HAND);
                frogB.mobInteract(player, InteractionHand.MAIN_HAND);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (!rosie.isInLove() || !sky.isInLove()) throw new AssertionError("Feeding did not start breeding");
        });
        if (tick > 40 && (pinkLamb == null || purpleLamb == null) && tick % 10 == 0) server.execute(() -> {
            pinkLamb = childOf(server, rosie, snowball);
            purpleLamb = childOf(server, sky, poppy);
        });
        if (pinkLamb == null || purpleLamb == null) {
            if (tick > 900) throw new AssertionError("Vanilla AI breeding did not produce both recorded babies");
            if (tick >= 30 && tick % 4 == 2) runner.capture(client, String.format("animals-%04d.png", tick));
            return;
        }
        if (bornTick == 0) bornTick = tick;
        int step = tick - bornTick;
        if (step % 4 == 2 && step < 60) runner.capture(client, String.format("animals-%04d.png", tick));
        if (step == 60) runner.capture(client, "animals-world.png");
        if (step == 70) {
            browser = new FamilyTreeBrowserScreen();
            runner.show(client, browser);
            FamilyTreeClient.requestSnapshot(false);
        }
        if (step == 90) {
            var snapshot = snapshot();
            AnimalRecord pink = find(snapshot, pinkLamb), purple = find(snapshot, purpleLamb);
            if (pink == null || purple == null) throw new AssertionError("The babies did not arrive over the network");
            if (!client.player.getUUID().equals(pink.ownerId())) throw new AssertionError("The breeder does not own the baby");
            if (!modded && (!"pink".equals(pink.variantId()) || !"purple".equals(purple.variantId())))
                throw new AssertionError("Wool colors did not mix: " + pink.variantId() + ", " + purple.variantId());
            if (!modded && !"mixed".equals(pink.variantOrigin()))
                throw new AssertionError("The lamb's origin was not saved at birth: " + pink.variantOrigin());
            if (!modded && (find(snapshot, frogA.getUUID()) == null || find(snapshot, frogB.getUUID()) == null
                    || !client.player.getUUID().equals(find(snapshot, frogA.getUUID()).ownerId())))
                throw new AssertionError("Breeding frogs did not record both parents for the player");
            if (!modded && (find(snapshot, azul.getUUID()) == null || !"blue".equals(find(snapshot, azul.getUUID()).variantId())))
                throw new AssertionError("The blue axolotl is missing or lost its color");
            runner.capture(client, "animals-browser.png");
        }
        if (step == 110) runner.show(client, new FamilyTreeViewScreen(browser, snapshot(), pinkLamb));
        if (step == 140) {
            runner.capture(client, "animals-tree.png");
            runner.press("Export family tree");
        }
        if (step == 200) {
            runner.capture(client, "animals-export-status.png");
            if (runner.exportCount(client) <= exportsBefore) throw new AssertionError("The family export is missing");
            if (!modded) runner.show(client, new FamilyTreeViewScreen(browser, snapshot(), azul.getUUID()));
        }
        if (step == 230 && !modded) runner.capture(client, "animals-axolotl.png");
        if (step == 240) {
            Files.writeString(client.gameDirectory.toPath().resolve("demo-result.txt"), modded
                    ? "PASS: " + MODDED + "; name tags record owned animals; feeding; AI breeding; baby and parents over the network; tree; PNG export\n"
                    : "PASS: sheep, frogs, and axolotl; name tags record owned animals; wheat; AI breeding; mixed wool colors"
                            + " saved at birth; frog parents recorded; blue axolotl; snapshot over the network; tree; PNG export\n");
            client.stop();
        }
    }

    private static UUID childOf(net.minecraft.server.MinecraftServer server, Animal a, Animal b) {
        var child = FamilyTreeState.get(server).all().stream().filter(pet -> pet.parentA() != null && pet.parentB() != null
                && List.of(a.getUUID(), b.getUUID()).containsAll(List.of(pet.parentA(), pet.parentB()))).findFirst().orElse(null);
        return child == null || server.overworld().getEntity(child.id()) == null ? null : child.id();
    }

    private FamilyTreeSnapshotPayload snapshot() throws Exception {
        var field = FamilyTreeBrowserScreen.class.getDeclaredField("snapshot");
        field.setAccessible(true);
        return (FamilyTreeSnapshotPayload) field.get(browser);
    }

    private static AnimalRecord find(FamilyTreeSnapshotPayload snapshot, UUID id) {
        if (snapshot == null) return null;
        return snapshot.records().stream().filter(pet -> pet.id().equals(id)).findFirst().orElse(null);
    }

    /** Uses a real name tag, so the name tag hook records the animal for the player. */
    static void nameTag(ServerPlayer player, Animal target, String name) throws Exception {
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        try {
            ItemStack.class.getMethod("setHoverName", Component.class).invoke(tag, Component.literal(name));
        } catch (NoSuchMethodException newerVersion) {
            Class<?> components = Class.forName("net.minecraft.core.component.DataComponents");
            Class<?> type = Class.forName("net.minecraft.core.component.DataComponentType");
            ItemStack.class.getMethod("set", type, Object.class).invoke(tag, components.getField("CUSTOM_NAME").get(null), Component.literal(name));
        }
        tag.interactLivingEntity(player, target, InteractionHand.MAIN_HAND);
        if (!target.hasCustomName()) throw new AssertionError("The name tag did not name " + name);
    }

    // Reflection keeps this development-only runner usable across the supported game APIs.
    private static Animal animal(ServerLevel world, String id, double x, double z, String variant) throws Exception {
        EntityType<?> type = null;
        for (EntityType<?> candidate : BuiltInRegistries.ENTITY_TYPE)
            if (String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(candidate)).equals(id)) type = candidate;
        if (type == null) throw new AssertionError("Unknown animal " + id);
        Animal animal = null;
        for (var method : type.getClass().getMethods()) {
            if (!method.getName().equals("create")) continue;
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(world))
                animal = (Animal) method.invoke(type, world);
            else if (method.getParameterCount() == 2 && method.getParameterTypes()[0].isInstance(world)
                    && method.getParameterTypes()[1].isEnum()) {
                Object reason = java.util.Arrays.stream(method.getParameterTypes()[1].getEnumConstants())
                        .filter(value -> value.toString().equals("LOAD")).findFirst().orElseThrow();
                animal = (Animal) method.invoke(type, world, reason);
            }
            if (animal != null) break;
        }
        if (animal == null) throw new IllegalStateException("No entity factory for " + id);
        animal.setPos(x, 101, z);
        EntityVariants.apply(animal, variant, world.registryAccess());
        animal.setCustomNameVisible(true);
        world.addFreshEntity(animal);
        return animal;
    }
}
