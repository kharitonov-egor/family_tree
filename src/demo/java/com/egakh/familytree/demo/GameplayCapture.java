package com.egakh.familytree.demo;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.screen.FamilyTreeBrowserScreen;
import com.egakh.familytree.client.screen.FamilyTreeViewScreen;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.nio.file.Files;
import java.util.UUID;

/** A disposable scene. Vanilla feeding and breeding create the recorded child. */
public final class GameplayCapture {
    private int tick;
    private Animal moss, fern;
    private volatile UUID puppy;
    private FamilyTreeBrowserScreen browser;
    private FamilyTreeViewScreen tree;
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
                    for (String command : new String[]{"gamemode creative @a", "time set day", "weather clear", "kill @e[type=minecraft:wolf]",
                            "fill -12 100 -12 12 100 12 grass_block", "fill -5 101 0 5 101 0 oak_fence",
                            "fill -5 101 8 5 101 8 oak_fence", "fill -5 101 1 -5 101 7 oak_fence",
                            "fill 5 101 1 5 101 7 oak_fence", "tp @a 0 101 1 0 20"})
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                    moss = wolf(server.overworld()); fern = wolf(server.overworld());
                    moss.setPos(-1, 101, 5); fern.setPos(1, 101, 5);
                    moss.setCustomName(Component.literal("Moss")); fern.setCustomName(Component.literal("Fern"));
                    moss.setCustomNameVisible(true); fern.setCustomNameVisible(true);
                    ((TamableAnimal) moss).tame(player); ((TamableAnimal) fern).tame(player);
                    ((TamableAnimal) moss).setOrderedToSit(false); ((TamableAnimal) fern).setOrderedToSit(false);
                    server.overworld().addFreshEntity(moss); server.overworld().addFreshEntity(fern);
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
        }
        if (tick == 60) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(client.player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEEF, 64));
            moss.mobInteract(player, InteractionHand.MAIN_HAND);
            fern.mobInteract(player, InteractionHand.MAIN_HAND);
            if (!moss.isInLove() || !fern.isInLove()) throw new AssertionError("Vanilla feeding did not start breeding");
        });
        if (tick == 150) server.execute(() -> {
            var state = FamilyTreeState.get(server);
            puppy = state.all().stream().filter(pet -> moss.getUUID().equals(pet.parentA()) && fern.getUUID().equals(pet.parentB())
                    || fern.getUUID().equals(pet.parentA()) && moss.getUUID().equals(pet.parentB()))
                    .map(pet -> pet.id()).findFirst().orElse(null);
            if (puppy != null) {
                var baby = server.overworld().getEntity(puppy);
                baby.setCustomName(Component.literal("Clover")); baby.setCustomNameVisible(true);
            }
        });
        if (tick == 180 && puppy == null) throw new AssertionError("Vanilla AI breeding did not produce a tracked child");
        if (tick == 195) server.execute(() -> {
            try {
                moss.getClass().getMethod("hurtServer", ServerLevel.class,
                        net.minecraft.world.damagesource.DamageSource.class, float.class)
                        .invoke(moss, server.overworld(), server.overworld().damageSources().fall(), 1000f);
                if (!FamilyTreeState.get(server).get(moss.getUUID()).deceased())
                    throw new AssertionError("Real death event was not recorded");
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
        if (tick == 240) {
            browser = new FamilyTreeBrowserScreen(); runner.show(client, browser); FamilyTreeClient.requestSnapshot(false);
        }
        if (tick == 260) {
            var field = FamilyTreeBrowserScreen.class.getDeclaredField("snapshot"); field.setAccessible(true);
            var snapshot = (FamilyTreeSnapshotPayload) field.get(browser);
            if (snapshot == null || snapshot.records().stream().noneMatch(pet -> pet.id().equals(puppy)))
                throw new AssertionError("Actual puppy did not arrive over the network");
            tree = new FamilyTreeViewScreen(browser, snapshot, puppy); runner.show(client, tree);
        }
        if (tick == 300) runner.press("Export family tree");
        if (tick >= 30 && tick <= 430 && tick % 4 == 2)
            runner.capture(client, String.format("gameplay-%04d.png", tick));
        if (tick == 400) runner.capture(client, "gameplay-tree.png");
        if (tick == 450) {
            if (runner.exportCount(client) <= exportsBefore) throw new AssertionError("Actual family export is missing");
            Files.writeString(client.gameDirectory.toPath().resolve("demo-result.txt"),
                    "PASS: vanilla feeding; AI breeding; tracked parents; real death event; network snapshot; PNG export\n");
            client.stop();
        }
    }

    private Animal wolf(ServerLevel world) throws Exception {
        var types = Class.forName("net.minecraft.world.entity.EntityTypes");
        Object type = types.getField("WOLF").get(null);
        for (var method : type.getClass().getMethods()) {
            if (method.getName().equals("create") && method.getParameterCount() == 2
                    && method.getParameterTypes()[0].isInstance(world) && method.getParameterTypes()[1].isEnum()) {
                Object reason = java.util.Arrays.stream(method.getParameterTypes()[1].getEnumConstants())
                        .filter(value -> value.toString().equals("LOAD")).findFirst().orElseThrow();
                return (Animal) method.invoke(type, world, reason);
            }
        }
        throw new IllegalStateException("No wolf factory");
    }
}
