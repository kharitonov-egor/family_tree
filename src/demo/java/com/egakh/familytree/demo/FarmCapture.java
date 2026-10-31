package com.egakh.familytree.demo;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.screen.FamilyTreeBrowserScreen;
import com.egakh.familytree.client.screen.FamilyTreeViewScreen;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.network.SnapshotPages;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.permissions.PetAccess;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * A disposable performance scene: the player feeds several hundred farm animals and vanilla AI breeds them.
 * It measures save size, snapshot time and size, and frame rates, then repeats the measurements after adding
 * synthetic records up to the default limit of 5,000 unnamed farm animals. Synthetic records are labeled as such.
 */
public final class FarmCapture {
    private static final String[][] HERD = {{"minecraft:cow", "WHEAT"}, {"minecraft:sheep", "WHEAT"},
            {"minecraft:pig", "CARROT"}, {"minecraft:chicken", "WHEAT_SEEDS"}};
    private static final int PAIRS_PER_SPECIES = 50;
    private final List<Animal> adults = new ArrayList<>();
    private final StringBuilder report = new StringBuilder();
    private int tick, stableTicks, lastCount, phaseStart;
    private String phase = "breeding";
    private FamilyTreeBrowserScreen browser;
    private FamilyTreeViewScreen forest;
    private long requestedAt;
    private FamilyTreeSnapshotPayload previous;

    public void tick(Minecraft client, DemoClient runner) throws Exception {
        if (client.level == null || client.player == null) return;
        tick++;
        MinecraftServer server = client.getSingleplayerServer();
        if (tick == 1) server.execute(() -> {
            try {
                for (String command : new String[]{"gamemode creative @a", "time set day", "weather clear", "gamerule doMobLoot false",
                        "kill @e[type=!minecraft:player]", "fill -34 100 -34 34 100 34 grass_block", "fill -34 101 -34 34 103 34 air",
                        "fill -33 101 -33 33 101 -33 oak_fence", "fill -33 101 33 33 101 33 oak_fence",
                        "fill -33 101 -32 -33 101 32 oak_fence", "fill 33 101 -32 33 101 32 oak_fence", "tp @a 0 140 -50 0 40"})
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                FamilyTreeState.get(server).removeMatching(pet -> true);
                ServerLevel world = server.overworld();
                Random random = new Random(7);
                for (int species = 0; species < HERD.length; species++)
                    for (int i = 0; i < PAIRS_PER_SPECIES * 2; i++)
                        adults.add(animal(world, HERD[species][0], -30 + random.nextInt(61), -30 + random.nextInt(61)));
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
        if (tick == 40) server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(client.player.getUUID());
            for (Animal adult : adults) {
                String id = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(adult.getType()));
                for (String[] herd : HERD) if (herd[0].equals(id)) player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item(herd[1]), 64));
                adult.mobInteract(player, InteractionHand.MAIN_HAND);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        if (phase.equals("breeding") && tick > 60 && tick % 20 == 0) {
            int count = server.submit(() -> FamilyTreeState.get(server).all().size()).get();
            stableTicks = count == lastCount ? stableTicks + 20 : 0;
            lastCount = count;
            if (stableTicks >= 200 || tick > 2400) {
                long babies = server.submit(() -> FamilyTreeState.get(server).all().stream().filter(pet -> pet.parentA() != null).count()).get();
                if (babies < 100) throw new AssertionError("Only " + babies + " babies were recorded");
                report.append("Bred scene: ").append(adults.size()).append(" adults fed by the player; vanilla AI bred ")
                        .append(babies).append(" babies; ").append(count).append(" records\n");
                startMeasuring(client, runner, "bred");
            }
            return;
        }
        if (phase.startsWith("measure")) measure(client, runner, server);
    }

    private void startMeasuring(Minecraft client, DemoClient runner, String label) throws Exception {
        phase = "measure-" + label;
        phaseStart = tick;
        runner.capture(client, "farm-" + label + "-world.png");
    }

    private void measure(Minecraft client, DemoClient runner, MinecraftServer server) throws Exception {
        int step = tick - phaseStart;
        String label = phase.substring("measure-".length());
        if (step == 1) {
            report.append(server.submit(() -> saveAndSnapshot(server, client.player.getUUID())).get());
            report.append(label).append(" world view: ").append(client.getFps()).append(" fps\n");
            runner.show(client, new com.egakh.familytree.client.screen.FamilyTreeHelpScreen(null));
        }
        if (step == 60) report.append(label).append(" text-only Getting started screen, for comparison: ")
                .append(client.getFps()).append(" fps\n");
        if (step == 70) {
            browser = new FamilyTreeBrowserScreen();
            runner.show(client, browser);
            previous = snapshot();
            requestedAt = System.nanoTime();
            FamilyTreeClient.requestSnapshot(true);
        }
        if (step > 70 && step < 200 && requestedAt != 0 && snapshot() != previous) {
            report.append(label).append(" snapshot round trip: ").append((System.nanoTime() - requestedAt) / 1_000_000)
                    .append(" ms for ").append(snapshot().records().size()).append(" records\n");
            requestedAt = 0;
        }
        if (step == 200) {
            if (requestedAt != 0) throw new AssertionError("The snapshot did not arrive");
            report.append(label).append(" browser, all animals: ").append(client.getFps()).append(" fps\n");
            runner.capture(client, "farm-" + label + "-browser.png");
            long start = System.nanoTime();
            forest = FamilyTreeViewScreen.forSpecies(browser, snapshot(), "minecraft:sheep", Component.literal("Sheep"));
            runner.show(client, forest);
            report.append(label).append(" sheep forest opened in ").append((System.nanoTime() - start) / 1_000_000).append(" ms with ")
                    .append(snapshot().records().stream().filter(pet -> pet.speciesId().equals("minecraft:sheep")).count()).append(" sheep\n");
        }
        if (step == 300) {
            report.append(label).append(" sheep forest, whole forest: ").append(client.getFps()).append(" fps\n");
            runner.capture(client, "farm-" + label + "-forest.png");
            forest.mouseScrolled(forest.width / 2.0, forest.height / 2.0, 0, 30);
        }
        if (step == 400) {
            report.append(label).append(" sheep forest, zoomed to one family: ").append(client.getFps()).append(" fps\n");
            runner.capture(client, "farm-" + label + "-forest-zoom.png");
            runner.show(client, null);
        }
        if (step == 480 && label.equals("bred")) {
            report.append("Synthetic records added up to 5,000 unnamed farm animals: ")
                    .append(server.submit(() -> addSynthetic(server, client.player.getUUID())).get()).append("\n");
            startMeasuring(client, runner, "at-limit");
        }
        if (step == 480 && label.equals("at-limit")) {
            Files.writeString(client.gameDirectory.toPath().resolve("perf-result.txt"), report.toString());
            Files.writeString(client.gameDirectory.toPath().resolve("demo-result.txt"),
                    "PASS: farm performance; see perf-result.txt\n" + report);
            client.stop();
        }
    }

    private FamilyTreeSnapshotPayload snapshot() throws Exception {
        if (browser == null) return null;
        var field = FamilyTreeBrowserScreen.class.getDeclaredField("snapshot");
        field.setAccessible(true);
        return (FamilyTreeSnapshotPayload) field.get(browser);
    }

    /** Times what autosave and a view-all snapshot do on the server thread. */
    private static String saveAndSnapshot(MinecraftServer server, UUID player) {
        try {
            FamilyTreeState state = FamilyTreeState.get(server);
            long start = System.nanoTime();
            CompoundTag tag = (CompoundTag) FamilyTreeState.CODEC.encodeStart(NbtOps.INSTANCE, state).result().orElseThrow();
            long encode = System.nanoTime() - start;
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, compressed);
            start = System.nanoTime();
            List<AnimalRecord> visible = state.all().stream().filter(pet -> PetAccess.canView(pet, player, true))
                    .sorted(java.util.Comparator.comparing(AnimalRecord::id)).map(pet -> PetAccess.forViewer(pet, player, true, true)).toList();
            var pages = SnapshotPages.split(UUID.randomUUID(), visible, 1, 1, true, true, true);
            long split = System.nanoTime() - start;
            long bytes = 0;
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                for (var page : pages) {
                    buffer.clear();
                    com.egakh.familytree.network.payloads.SnapshotPagePayload.STREAM_CODEC.encode(buffer, page);
                    bytes += buffer.readableBytes();
                }
            } finally { buffer.release(); }
            start = System.nanoTime();
            var backup = state.backup(server, "perf-measure-", false);
            long backupTime = System.nanoTime() - start, backupBytes = Files.size(backup);
            Files.delete(backup);
            return String.format(java.util.Locale.ROOT, "%d records (%d unnamed farm): save encode %.1f ms, familytree.dat %.1f KiB compressed;"
                            + " snapshot built in %.1f ms, %d pages, %.1f KiB; cleanup backup written in %.1f ms, %.1f KiB JSON%n",
                    state.all().size(), state.recountUnnamedFarmRecords(), encode / 1e6, compressed.size() / 1024.0,
                    split / 1e6, pages.size(), bytes / 1024.0, backupTime / 1e6, backupBytes / 1024.0);
        } catch (Exception failure) { throw new RuntimeException(failure); }
    }

    private static String addSynthetic(MinecraftServer server, UUID player) {
        FamilyTreeState state = FamilyTreeState.get(server);
        Random random = new Random(11);
        String[][] species = {{"minecraft:cow", "Cow"}, {"minecraft:sheep", "Sheep"}, {"minecraft:pig", "Pig"}, {"minecraft:chicken", "Chicken"}};
        String[] wool = {"white", "pink", "red", "black", "gray", "brown", "lime", "purple"};
        List<List<AnimalRecord>> families = new ArrayList<>();
        for (int i = 0; i < species.length; i++) families.add(new ArrayList<>());
        int added = 0, missing = 5_000 - state.recountUnnamedFarmRecords();
        while (added < missing) {
            int kind = random.nextInt(species.length);
            List<AnimalRecord> herd = families.get(kind);
            AnimalRecord a = herd.size() < 2 || random.nextInt(4) == 0 ? null : herd.get(random.nextInt(herd.size()));
            AnimalRecord b = a == null ? null : herd.get(random.nextInt(herd.size()));
            UUID id = UUID.randomUUID();
            long day = random.nextInt(400);
            var record = new AnimalRecord(id, species[kind][0], species[kind][1] + " " + Integer.toHexString(random.nextInt(0xFFFF)).toUpperCase(),
                    true, a == null ? null : a.id(), b == null || b == a ? null : b.id(), day, 1_700_000_000_000L + day * 1_200_000,
                    false, null, null, player, "Player", kind == 1 ? wool[random.nextInt(wool.length)] : null,
                    new AnimalRecord.LastSeen(random.nextInt(2000) - 1000, 70, random.nextInt(2000) - 1000, "minecraft:overworld", day + 10), null);
            if (random.nextInt(3) == 0) record.markDeceased(day + 30, 1_700_000_000_000L + (day + 30) * 1_200_000,
                    new AnimalRecord.DeathCause("Player", "player"));
            state.put(record);
            herd.add(record);
            added++;
        }
        return added + " records, linked into families, a third of them deceased";
    }

    // Reflection keeps this development-only runner usable across the supported game APIs.
    private static Item item(String name) {
        try { return (Item) Items.class.getField(name).get(null); }
        catch (ReflectiveOperationException missing) { throw new IllegalStateException(name, missing); }
    }

    private static Animal animal(ServerLevel world, String id, double x, double z) throws Exception {
        EntityType<?> type = null;
        for (EntityType<?> candidate : BuiltInRegistries.ENTITY_TYPE)
            if (String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(candidate)).equals(id)) type = candidate;
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
        animal.setPos(x + 0.5, 101, z + 0.5);
        world.addFreshEntity(animal);
        return animal;
    }
}
