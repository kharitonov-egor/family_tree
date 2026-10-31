package com.egakh.familytree.demo;

import com.egakh.familytree.client.keybind.FamilyTreeKeybinds;
import com.egakh.familytree.client.relatives.RelationStyle;
import com.egakh.familytree.client.relatives.WorldRelatives;
import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.platform.FamilyTreePaths;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import com.egakh.familytree.util.CloseRelation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A disposable scene for relatives in the world. Golden carrots and vanilla AI breeding produce three generations of
 * horses. One parent belongs to another player and the server hides it with viewPolicy=OWN_ONLY. The runner holds the
 * key in-process, aims at each animal, and checks which entities glow and what their labels say.
 */
public final class RelativesCapture {
    private static final UUID SAM = new UUID(1, 2);
    private final UUID mira = UUID.randomUUID();
    private static final int TIMEOUT = 900;

    private enum Stage { SETUP, EMBER, WILLOW, PIP, SPARK, FLINT, ARRANGE, CHECK, DONE }

    private final Map<String, UUID> family = new ConcurrentHashMap<>();
    private final List<String> passed = new ArrayList<>();
    private volatile Stage stage = Stage.SETUP;
    private int tick;
    private int stageTick;
    private int checkTick;
    private String originalServerSettings;
    private volatile UUID found;
    private volatile boolean pipIsMine;

    public void tick(Minecraft client, DemoClient runner) throws Exception {
        if (client.level == null || client.player == null) return;
        tick++;
        stageTick++;
        MinecraftServer server = client.getSingleplayerServer();
        if (stageTick > TIMEOUT) throw new AssertionError("Stage " + stage + " timed out");
        switch (stage) {
            case SETUP -> {
                if (tick == 1) server.execute(() -> clearScene(server));
                if (tick == 20) server.execute(() -> spawn(server, client.player.getUUID()));
                if (tick == 60) next(Stage.EMBER, () -> breed(server, "Comet", "Dusk"));
            }
            case EMBER -> await(server, "Comet", "Dusk", "Ember", Stage.WILLOW, () -> {
                rested(server, "Comet"); rested(server, "Dusk");
                breed(server, "Comet", "Dusk");
            });
            case WILLOW -> await(server, "Comet", "Dusk", "Willow", Stage.PIP, () -> {
                rested(server, "Comet");
                move(server, "Dusk", 9, 15);
                move(server, "Comet", -2, 9);
                move(server, "Mira", 1, 9);
                breed(server, "Comet", "Mira");
            });
            case PIP -> await(server, "Comet", "Mira", "Pip", Stage.SPARK, () -> {
                pipIsMine = client.player.getUUID().equals(FamilyTreeState.get(server).get(family.get("Pip")).ownerId());
                grownAndTamed(server, client.player.getUUID(), "Ember");
                move(server, "Ember", -6, 4);
                move(server, "Sable", -8, 4);
                breed(server, "Ember", "Sable");
            });
            case SPARK -> await(server, "Ember", "Sable", "Spark", Stage.FLINT, () -> {
                rested(server, "Ember"); rested(server, "Sable");
                breed(server, "Ember", "Sable");
            });
            case FLINT -> await(server, "Ember", "Sable", "Flint", Stage.ARRANGE, () -> {});
            case ARRANGE -> {
                if (stageTick == 1) server.execute(() -> arrange(server, client.player.getUUID()));
                if (stageTick == 40) next(Stage.CHECK, () -> {});
            }
            case CHECK -> check(client, runner);
            case DONE -> {}
        }
    }

    /** Spawning waits a second after the previous run's horses are killed. */
    private void clearScene(MinecraftServer server) {
        try {
            for (String command : new String[]{"gamemode creative @a", "time set 6000", "weather clear",
                    "kill @e[type=!minecraft:player]", "fill -16 100 -8 16 100 20 grass_block",
                    "fill -16 101 -8 16 110 20 air", "fill -12 101 0 12 101 0 oak_fence", "fill -12 101 18 12 101 18 oak_fence",
                    "fill -12 101 1 -12 101 17 oak_fence", "fill 12 101 1 12 101 17 oak_fence", "tp @a 0 103 -6 0 15"})
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
            Path config = FamilyTreePaths.config().resolve("familytree-server.properties");
            originalServerSettings = Files.exists(config) ? Files.readString(config) : null;
            String settings = originalServerSettings == null ? "" : originalServerSettings.replaceAll("(?m)^viewPolicy=.*\\R?", "");
            Files.writeString(config, settings + "viewPolicy=OWN_ONLY\n");
            FamilyTreeServerSettings.load();
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private void spawn(MinecraftServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "kill @e[type=minecraft:item]");
            FamilyTreeState.get(server).removeMatching(pet -> true);
            long high = mira.getMostSignificantBits(), low = mira.getLeastSignificantBits();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), String.format(
                    "summon minecraft:horse 6 101 9 {UUID:[I;%d,%d,%d,%d],Tame:1b,Owner:[I;0,1,0,2]}",
                    (int) (high >> 32), (int) high, (int) (low >> 32), (int) low));
            var world = server.overworld();
            family.put("Comet", HorseCapture.horse(world, "HORSE", "Comet", -2, 9, "CHESTNUT", "WHITE_DOTS", player).getUUID());
            family.put("Dusk", HorseCapture.horse(world, "HORSE", "Dusk", 2, 9, "BLACK", "WHITE_FIELD", player).getUUID());
            family.put("Sable", HorseCapture.horse(world, "HORSE", "Sable", -9, 13, "DARK_BROWN", "NONE", player).getUUID());
            Entity summoned = world.getEntity(mira);
            if (summoned == null || !summoned.isAlive()) throw new AssertionError("The other player's horse was not summoned");
            summoned.setCustomName(Component.literal("Mira"));
            family.put("Mira", mira);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private void next(Stage stage, Runnable onServer) {
        this.stage = stage;
        stageTick = 0;
        found = null;
        Minecraft.getInstance().getSingleplayerServer().execute(onServer);
    }

    /** Waits for a recorded child of both parents that isn't already named, names it, then starts the next breeding. */
    private void await(MinecraftServer server, String a, String b, String child, Stage then, Runnable onServer) {
        if (found != null) {
            UUID id = found;
            family.put(child, id);
            next(then, () -> {
                Entity entity = server.overworld().getEntity(id);
                entity.setCustomName(Component.literal(child));
                onServer.run();
            });
            return;
        }
        if (stageTick % 10 != 0) return;
        Stage polling = stage;
        server.execute(() -> {
            if (stage != polling) return;
            UUID first = family.get(a), second = family.get(b);
            for (AnimalRecord pet : FamilyTreeState.get(server).all()) {
                if (pet.parentA() == null || pet.parentB() == null || family.containsValue(pet.id())) continue;
                if (!List.of(pet.parentA(), pet.parentB()).containsAll(List.of(first, second))) continue;
                if (server.overworld().getEntity(pet.id()) != null) found = pet.id();
            }
        });
    }

    private void breed(MinecraftServer server, String a, String b) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_CARROT, 64));
        for (String name : List.of(a, b)) {
            Animal animal = (Animal) server.overworld().getEntity(family.get(name));
            animal.setHealth(animal.getMaxHealth());
            animal.mobInteract(player, InteractionHand.MAIN_HAND);
            if (!animal.isInLove()) throw new AssertionError("Golden carrots did not make " + name + " ready to breed");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** Skips the five-minute breeding cooldown. The next foal still comes from vanilla feeding and AI breeding. */
    private void rested(MinecraftServer server, String name) {
        ((AgeableMob) server.overworld().getEntity(family.get(name))).setAge(0);
    }

    /** Skips the 20 minutes a foal takes to grow, then tames it the way a rider would, so it can breed. */
    private void grownAndTamed(MinecraftServer server, UUID playerId, String name) {
        try {
            Animal animal = (Animal) server.overworld().getEntity(family.get(name));
            animal.setAge(0);
            animal.getClass().getMethod("tameWithName", Player.class).invoke(animal, server.getPlayerList().getPlayer(playerId));
            animal.setHealth(animal.getMaxHealth());
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
    }

    private void move(MinecraftServer server, String name, double x, double z) {
        Entity entity = server.overworld().getEntity(family.get(name));
        if (entity == null) throw new AssertionError(name + " (" + family.get(name) + ") is not loaded on the server; family " + family);
        entity.teleportTo(x, 101, z);
    }

    /** Lines the family up by generation for the camera: grandparents at the back, foals in front. */
    private void arrange(MinecraftServer server, UUID playerId) {
        double[][] places = {{-2.5, 12.5, -120}, {2.5, 12.5, 120}, {-7, 12.5, -120}, {7, 12.5, 120},
                {0, 9, 160}, {5, 9, 130}, {-5.5, 9, -130}, {-2, 6, -140}, {2, 6, 140}};
        String[] names = {"Comet", "Dusk", "Sable", "Mira", "Ember", "Willow", "Pip", "Spark", "Flint"};
        for (int i = 0; i < names.length; i++) {
            Mob mob = (Mob) server.overworld().getEntity(family.get(names[i]));
            float yaw = (float) places[i][2];
            mob.setNoAi(true);
            mob.teleportTo(places[i][0], 101, places[i][1]);
            mob.setYRot(yaw);
            mob.setYHeadRot(yaw);
            mob.setYBodyRot(yaw);
        }
        for (String grown : List.of("Willow", "Pip")) ((AgeableMob) server.overworld().getEntity(family.get(grown))).setAge(0);
        for (UUID id : family.values()) server.overworld().getEntity(id).setCustomNameVisible(false);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "kill @e[type=minecraft:item]");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "kill @e[type=minecraft:experience_orb]");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp @a 0 103.3 -0.5 0 20");
        var state = FamilyTreeState.get(server);
        if (!SAM.equals(state.get(mira).ownerId())) throw new AssertionError("Mira is not recorded for the other player");
    }

    private void check(Minecraft client, DemoClient runner) throws Exception {
        checkTick++;
        if (checkTick == 1) {
            hideHud(client, false);
            aim(client, "Ember");
            FamilyTreeKeybinds.SHOW_RELATIVES.setDown(true);
        }
        if (checkTick == 30) {
            Map<String, CloseRelation> relatives = new java.util.HashMap<>(Map.of("Comet", CloseRelation.PARENT, "Dusk", CloseRelation.PARENT,
                    "Willow", CloseRelation.SIBLING, "Spark", CloseRelation.CHILD, "Flint", CloseRelation.CHILD));
            if (pipIsMine) relatives.put("Pip", CloseRelation.HALF_SIBLING);
            expect(client, relatives, "Ember", pipIsMine ? List.of("Sable", "Mira") : List.of("Sable", "Mira", "Pip"));
            runner.capture(client, "relatives-ember.png");
            passed.add(pipIsMine ? "Ember: parents, sibling, half sibling, children"
                    : "Ember: parents, sibling, children; half sibling Pip, recorded for the other player, stays dark");
            aim(client, "Spark");
        }
        if (checkTick == 60) {
            expect(client, Map.of("Ember", CloseRelation.PARENT, "Sable", CloseRelation.PARENT, "Flint", CloseRelation.SIBLING,
                    "Comet", CloseRelation.GRANDPARENT, "Dusk", CloseRelation.GRANDPARENT), "Spark", List.of("Willow", "Pip", "Mira"));
            runner.capture(client, "relatives-spark.png");
            passed.add("Spark: parents, grandparents, sibling");
            aim(client, "Pip");
        }
        if (checkTick == 90) {
            if (pipIsMine) {
                expect(client, Map.of("Comet", CloseRelation.PARENT, "Ember", CloseRelation.HALF_SIBLING, "Willow", CloseRelation.HALF_SIBLING),
                        "Pip", List.of("Mira", "Dusk", "Sable", "Spark", "Flint"));
                passed.add("Pip: the other player's horse Mira, its parent, stays dark under viewPolicy=OWN_ONLY");
            } else {
                expect(client, Map.of("Ember", CloseRelation.PARENT, "Sable", CloseRelation.PARENT, "Flint", CloseRelation.SIBLING,
                        "Comet", CloseRelation.GRANDPARENT, "Dusk", CloseRelation.GRANDPARENT), "Spark", List.of("Pip", "Mira"));
                passed.add("Pip: looking at the other player's foal keeps Spark's family and lights nothing of Pip's");
            }
            runner.capture(client, "relatives-pip-hidden-parent.png");
            aim(client, "Comet");
        }
        if (checkTick == 120) {
            Map<String, CloseRelation> relatives = new java.util.HashMap<>(Map.of("Ember", CloseRelation.CHILD, "Willow", CloseRelation.CHILD,
                    "Spark", CloseRelation.GRANDCHILD, "Flint", CloseRelation.GRANDCHILD));
            if (pipIsMine) relatives.put("Pip", CloseRelation.CHILD);
            expect(client, relatives, "Comet", pipIsMine ? List.of("Dusk", "Sable", "Mira") : List.of("Dusk", "Sable", "Mira", "Pip"));
            runner.capture(client, "relatives-comet.png");
            passed.add("Comet: children and grandchildren");
            FamilyTreeKeybinds.SHOW_RELATIVES.setDown(false);
        }
        if (checkTick == 130) {
            if (anyHighlighted(client)) throw new AssertionError("Highlights stayed after the key was released");
            FamilyTreeClientSettings.setShowRelatives(false);
            aim(client, "Ember");
            FamilyTreeKeybinds.SHOW_RELATIVES.setDown(true);
        }
        if (checkTick == 160) {
            if (anyHighlighted(client)) throw new AssertionError("The key still highlights relatives with the setting off");
            var field = WorldRelatives.class.getDeclaredField("CACHE");
            field.setAccessible(true);
            if (!((Map<?, ?>) field.get(null)).isEmpty()) throw new AssertionError("The client asked the server with the setting off");
            FamilyTreeKeybinds.SHOW_RELATIVES.setDown(false);
            FamilyTreeClientSettings.setShowRelatives(true);
            passed.add("setting off: no highlights and no request");
        }
        if (checkTick >= 170 && media(client, runner, checkTick - 170)) finish(client);
    }

    /** About nine seconds of frames for the clip while the camera drifts sideways, then a still with the lines drawn. */
    private boolean media(Minecraft client, DemoClient runner, int frame) throws Exception {
        if (frame == 0) {
            hideHud(client, true);
            client.player.getAbilities().flying = true;
        }
        if (frame <= 180) {
            double x = -1 + 2 * frame / 180.0;
            client.player.setPos(x, 102.9, 1.5);
            client.player.setDeltaMovement(0, 0, 0);
            ease(client, frame < 100 ? "Ember" : "Comet", frame == 0 ? 1.0F : 0.15F);
        }
        if (frame == 20) FamilyTreeKeybinds.SHOW_RELATIVES.setDown(true);
        if (frame <= 180 && frame % 2 == 0) runner.capture(client, String.format("relatives-clip-%04d.png", frame));
        if (frame == 185) {
            client.player.setPos(0, 103.3, -0.5);
            aim(client, "Ember");
        }
        if (frame == 215) runner.capture(client, "relatives-still.png");
        return frame >= 225;
    }

    private void ease(Minecraft client, String name, float amount) {
        float[] wanted = angles(client, name);
        float yaw = client.player.getYRot() + net.minecraft.util.Mth.wrapDegrees(wanted[0] - client.player.getYRot()) * amount;
        float pitch = client.player.getXRot() + (wanted[1] - client.player.getXRot()) * amount;
        client.player.yRotO = client.player.getYRot();
        client.player.xRotO = client.player.getXRot();
        client.player.setYRot(yaw);
        client.player.setXRot(pitch);
    }

    private void finish(Minecraft client) throws Exception {
        FamilyTreeKeybinds.SHOW_RELATIVES.setDown(false);
        hideHud(client, false);
        Path config = FamilyTreePaths.config().resolve("familytree-server.properties");
        if (originalServerSettings != null) Files.writeString(config, originalServerSettings);
        else Files.deleteIfExists(config);
        client.getSingleplayerServer().execute(FamilyTreeServerSettings::load);
        stage = Stage.DONE;
        Files.writeString(client.gameDirectory.toPath().resolve("demo-result.txt"),
                "PASS: relatives; golden carrots; AI breeding over three generations; " + String.join("; ", passed) + "\n");
        client.stop();
    }

    private void aim(Minecraft client, String name) {
        float[] wanted = angles(client, name);
        client.player.setYRot(wanted[0]);
        client.player.setXRot(wanted[1]);
        client.player.yRotO = wanted[0];
        client.player.xRotO = wanted[1];
    }

    private float[] angles(Minecraft client, String name) {
        Entity target = clientEntity(client, family.get(name));
        if (target == null) throw new AssertionError(name + " is not loaded on the client");
        var eye = client.player.getEyePosition(1.0F);
        double dx = target.getX() - eye.x;
        double dy = target.getY() + target.getBbHeight() * 0.5 - eye.y;
        double dz = target.getZ() - eye.z;
        return new float[]{(float) Math.toDegrees(Math.atan2(-dx, dz)),
                (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }

    private void expect(Minecraft client, Map<String, CloseRelation> relatives, String focus, List<String> dark) {
        Entity center = clientEntity(client, family.get(focus));
        if (WorldRelatives.outlineColor(center) != RelationStyle.FOCUS)
            throw new AssertionError(focus + " is not marked as the animal looked at");
        if (!WorldRelatives.label(center).getString().contains(focus)) throw new AssertionError(focus + " has no label");
        for (var expected : relatives.entrySet()) {
            Entity entity = clientEntity(client, family.get(expected.getKey()));
            if (WorldRelatives.outlineColor(entity) != expected.getValue().color())
                throw new AssertionError(expected.getKey() + " should glow as " + expected.getValue() + " of " + focus);
            String label = WorldRelatives.label(entity).getString();
            String relation = Component.translatable(expected.getValue().translationKey()).getString();
            if (!label.contains(relation) || !label.contains(expected.getKey()))
                throw new AssertionError(expected.getKey() + " has the label '" + label + "'");
        }
        for (String name : dark) {
            if (WorldRelatives.outlineColor(clientEntity(client, family.get(name))) != 0)
                throw new AssertionError(name + " should not glow for " + focus);
        }
    }

    private boolean anyHighlighted(Minecraft client) {
        for (UUID id : family.values()) if (WorldRelatives.outlineColor(clientEntity(client, id)) != 0) return true;
        return false;
    }

    // Reflection keeps this development-only runner usable across the supported game APIs.
    private static void hideHud(Minecraft client, boolean hidden) throws ReflectiveOperationException {
        try {
            client.options.getClass().getField("hideGui").setBoolean(client.options, hidden);
        } catch (NoSuchFieldException newerVersion) {
            Object hud = client.gui.getClass().getField("hud").get(client.gui);
            var field = hud.getClass().getDeclaredField("isHidden");
            field.setAccessible(true);
            field.setBoolean(hud, hidden);
        }
    }

    private static Entity clientEntity(Minecraft client, UUID id) {
        for (Entity entity : client.level.entitiesForRendering()) if (entity.getUUID().equals(id)) return entity;
        return null;
    }
}
