package com.egakh.familytree.demo;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.screen.FamilyTreeBrowserScreen;
import com.egakh.familytree.client.screen.FamilyTreeViewScreen;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/** A disposable scene. Vanilla feeding and AI breeding produce the recorded foal and its stats. */
public final class HorseCapture {
    private int tick;
    private int foalTick;
    private Animal comet, dusk, sandy;
    private volatile UUID foal;
    private FamilyTreeBrowserScreen browser;
    private long exportsBefore;

    public void tick(Minecraft client, DemoClient runner) throws Exception {
        if (client.level == null || client.player == null) return;
        tick++;
        var server = client.getSingleplayerServer();
        if (tick == 1) {
            exportsBefore = runner.exportCount(client);
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(client.player.getUUID());
                    for (String command : new String[]{"gamemode creative @a", "time set day", "weather clear",
                            "kill @e[type=!minecraft:player]", "fill -14 100 -4 14 100 18 grass_block",
                            "fill -9 101 0 9 101 0 oak_fence", "fill -9 101 16 9 101 16 oak_fence",
                            "fill -9 101 1 -9 101 15 oak_fence", "fill 9 101 1 9 101 15 oak_fence", "tp @a 0 102 -3 0 25"})
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                    ServerLevel world = server.overworld();
                    FamilyTreeState.get(server).removeMatching(pet -> true);
                    comet = horse(world, "HORSE", "Comet", -2, 9, "CHESTNUT", "WHITE_DOTS", player);
                    dusk = horse(world, "HORSE", "Dusk", 2, 9, "BLACK", "WHITE_FIELD", player);
                    sandy = horse(world, "CAMEL", "Sandy", 6, 13, null, null, null);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    sandy.mobInteract(player, InteractionHand.MAIN_HAND);
                    player.stopRiding();
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp @a 0 102 -3 0 25");
                    var camel = FamilyTreeState.get(server).get(sandy.getUUID());
                    if (camel == null || !player.getUUID().equals(camel.ownerId()))
                        throw new AssertionError("Riding the camel did not record it for the rider");
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
        }
        if (tick == 40) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(client.player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_CARROT, 64));
            comet.mobInteract(player, InteractionHand.MAIN_HAND);
            dusk.mobInteract(player, InteractionHand.MAIN_HAND);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (!comet.isInLove() || !dusk.isInLove()) throw new AssertionError("Golden carrots did not start breeding");
        });
        if (tick > 40 && foal == null && tick % 10 == 0) server.execute(() -> {
            var child = FamilyTreeState.get(server).all().stream().filter(pet -> pet.parentA() != null && pet.parentB() != null
                    && List.of(comet.getUUID(), dusk.getUUID()).containsAll(List.of(pet.parentA(), pet.parentB())))
                    .findFirst().orElse(null);
            var entity = child == null ? null : server.overworld().getEntity(child.id());
            if (entity == null) return;
            entity.setCustomName(Component.literal("Ember"));
            entity.setCustomNameVisible(true);
            foal = child.id();
        });
        if (foal == null) {
            if (tick > 600) throw new AssertionError("Vanilla AI breeding did not produce a tracked foal");
            if (tick >= 30 && tick % 4 == 2) runner.capture(client, String.format("horses-%04d.png", tick));
            return;
        }
        if (foalTick == 0) foalTick = tick;
        int step = tick - foalTick;
        if (step % 4 == 2 && step < 60) runner.capture(client, String.format("horses-%04d.png", tick));
        if (step == 60) runner.capture(client, "horses-world.png");
        if (step == 70) {
            browser = new FamilyTreeBrowserScreen();
            runner.show(client, browser);
            FamilyTreeClient.requestSnapshot(false);
        }
        if (step == 90) {
            var snapshot = snapshot();
            var ember = find(snapshot, foal);
            if (ember == null || ember.horseStats() == null || ember.horseStats().speed() == null)
                throw new AssertionError("The foal's stats did not arrive over the network");
            for (var parent : List.of(comet.getUUID(), dusk.getUUID()))
                if (find(snapshot, parent) == null || find(snapshot, parent).horseStats() == null)
                    throw new AssertionError("A parent's stats did not arrive over the network");
            if (find(snapshot, sandy.getUUID()) == null) throw new AssertionError("The ridden camel is missing");
            runner.capture(client, "horses-browser.png");
            runner.press(DemoClient.tr("familytree.screen.sort", DemoClient.tr("familytree.screen.sort.name")));
        }
        if (step == 100) {
            var field = FamilyTreeBrowserScreen.class.getDeclaredField("filtered");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") var shown = (List<AnimalRecord>) field.get(browser);
            for (int i = 1; i < shown.size(); i++) {
                Double previous = shown.get(i - 1).horseStats() == null ? null : shown.get(i - 1).horseStats().speed();
                Double current = shown.get(i).horseStats() == null ? null : shown.get(i).horseStats().speed();
                if (current != null && (previous == null || previous < current))
                    throw new AssertionError("The browser is not sorted by speed");
            }
            runner.capture(client, "horses-browser-speed.png");
        }
        if (step == 110) runner.show(client, new FamilyTreeViewScreen(browser, snapshot(), foal));
        if (step == 140) {
            runner.capture(client, "horses-tree.png");
            runner.press(DemoClient.tr("familytree.export.button"));
        }
        if (step == 200) {
            runner.capture(client, "horses-export-status.png");
            if (runner.exportCount(client) <= exportsBefore) throw new AssertionError("The horse family export is missing");
            Files.writeString(client.gameDirectory.toPath().resolve("demo-result.txt"),
                    "PASS: horses; riding records a camel; golden carrots; AI breeding; foal and parent stats over the network;"
                            + " browser sorted by speed; tree; PNG export\n");
            client.stop();
        }
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

    // Reflection keeps this development-only runner usable across the supported game APIs.
    private static Animal horse(ServerLevel world, String type, String name, double x, double z,
                                String coat, String markings, ServerPlayer owner) throws Exception {
        Animal animal = create(world, type);
        animal.setPos(x, 101, z);
        Class<?> base = animal.getClass();
        while (!base.getSimpleName().equals("AbstractHorse")) base = base.getSuperclass();
        var randomize = base.getDeclaredMethod("randomizeAttributes", net.minecraft.util.RandomSource.class);
        randomize.setAccessible(true);
        randomize.invoke(animal, animal.getRandom());
        animal.setHealth(animal.getMaxHealth());
        if (coat != null) for (var method : animal.getClass().getDeclaredMethods()) {
            if (!method.getName().equals("setVariantAndMarkings")) continue;
            method.setAccessible(true);
            method.invoke(animal, constant(method.getParameterTypes()[0], coat), constant(method.getParameterTypes()[1], markings));
        }
        animal.setCustomName(Component.literal(name));
        animal.setCustomNameVisible(true);
        if (owner != null) animal.getClass().getMethod("tameWithName", Player.class).invoke(animal, owner);
        world.addFreshEntity(animal);
        return animal;
    }

    private static Object constant(Class<?> type, String name) {
        for (Object value : type.getEnumConstants()) if (((Enum<?>) value).name().equals(name)) return value;
        throw new IllegalArgumentException(name);
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
        throw new IllegalStateException("No entity factory for " + name);
    }
}
