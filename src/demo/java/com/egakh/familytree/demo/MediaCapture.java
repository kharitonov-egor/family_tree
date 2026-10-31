package com.egakh.familytree.demo;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.screen.FamilyTreeBrowserScreen;
import com.egakh.familytree.client.screen.FamilyTreeViewScreen;
import com.egakh.familytree.client.screen.HorseStatsText;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.util.EntityVariants;
import com.egakh.familytree.util.HorseStatUnits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A disposable scene for the project page. The script tames and names eight horses, then leads one pair at a time
 * together and feeds them golden carrots. Vanilla AI breeds every foal across four generations. Between generations
 * the script names each foal with a name tag, grows it up, and tames it. Sheep and pandas breed the same way.
 * The blue axolotl is spawned blue. `media-timeline.txt` records when each shot starts, for cutting a screen recording.
 */
public final class MediaCapture {
    private static final int OX = Integer.getInteger("familytree.demo.mediaX", 0);
    private static final int OZ = Integer.getInteger("familytree.demo.mediaZ", 0);
    /** With "pandas", only the panda breeding and its shots run, for another try at a brown cub. */
    private static final boolean PANDAS_ONLY = "pandas".equals(System.getProperty("familytree.demo.mediaOnly", ""));

    private record Founder(String name, double x, double z, String coat, String markings) {}
    private record Pair(String a, String b, String child) {}

    private static final List<Founder> FOUNDERS = List.of(
            new Founder("Comet", -8, 5, "CHESTNUT", "WHITE_DOTS"), new Founder("Dusk", -6, 7, "BLACK", "WHITE_FIELD"),
            new Founder("Maple", -3, 14, "CREAMY", "NONE"), new Founder("Storm", -1, 16, "GRAY", "WHITE"),
            new Founder("Willow", 3, 15, "WHITE", "NONE"), new Founder("Blaze", 5, 13, "DARK_BROWN", "WHITE"),
            new Founder("Hazel", 7, 6, "BROWN", "BLACK_DOTS"), new Founder("Atlas", 9, 8, "BLACK", "NONE"));
    private static final List<Pair> PAIRS = List.of(
            new Pair("Comet", "Dusk", "Ember"), new Pair("Maple", "Storm", "Juniper"),
            new Pair("Willow", "Blaze", "Sable"), new Pair("Hazel", "Atlas", "Rowan"),
            new Pair("Ember", "Juniper", "Clover"), new Pair("Sable", "Rowan", "Pippin"),
            new Pair("Clover", "Pippin", "Nova"));

    private final List<String> timeline = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final Map<String, Animal> horses = new LinkedHashMap<>();
    private int tick, phase, phaseTick, pairIndex, pandaRound, bornTick, namedTick;
    private int oy;
    private volatile UUID child;
    private volatile int childTick;
    private Animal rosie, snowball, sky, poppy, azul;
    private final List<Animal> pandas = new ArrayList<>();
    private volatile UUID pinkLamb, purpleLamb, brownPanda;
    private FamilyTreeBrowserScreen browser;
    private FamilyTreeViewScreen tree;
    private long exportsBefore;
    private double[] fit;
    private String best;

    public void tick(Minecraft client, DemoClient runner) throws Exception {
        if (client.level == null || client.player == null) return;
        tick++;
        phaseTick++;
        var server = client.getSingleplayerServer();
        if (PANDAS_ONLY && (phase == 1 || phase == 2 || phase == 3 || phase == 6 || phase == 7)) {
            phase = phase == 6 ? 8 : phase < 4 ? 4 : phase;
            phaseTick = 1;
        }
        switch (phase) {
            case 0 -> setup(client, runner, server);
            case 1 -> breedHorses(client, runner, server);
            case 2 -> jadeShot(client, runner, server);
            case 3 -> breedSheep(client, runner, server);
            case 4 -> breedPandas(client, runner, server);
            case 5 -> browserShots(client, runner);
            case 6 -> treeShots(client, runner);
            case 7 -> exportAndWaypoint(client, runner, server);
            case 8 -> variantShots(client, runner);
            default -> finish(client);
        }
    }

    private void next(String label) {
        phase++;
        phaseTick = 0;
        mark(label);
    }

    private void mark(String label) {
        timeline.add(System.currentTimeMillis() + " " + tick + " " + label);
        try { Files.write(Minecraft.getInstance().gameDirectory.toPath().resolve("media-timeline.txt"), timeline); }
        catch (java.io.IOException ignored) {}
    }

    /** The selected tab's button is disabled, so pressing it again is skipped. */
    private static void tab(Minecraft client, DemoClient runner, String key) throws Exception {
        String label = DemoClient.tr(key);
        for (var widget : runner.current(client).children())
            if (widget instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(label)
                    && !button.active) return;
        runner.press(label);
    }

    private void setup(Minecraft client, DemoClient runner, MinecraftServer server) throws Exception {
        if (phaseTick == 1) {
            exportsBefore = runner.exportCount(client);
            mark("setup");
            server.execute(() -> {
                try {
                    ServerLevel world = server.overworld();
                    oy = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, OX, OZ);
                    for (String rule : new String[]{"sendCommandFeedback", "send_command_feedback", "doDaylightCycle", "advance_time",
                            "doWeatherCycle", "advance_weather", "doMobSpawning", "spawn_mobs", "announceAdvancements", "show_advancement_messages"})
                        run(server, "gamerule " + rule + " false");
                    run(server, "advancement grant @a only minecraft:husbandry/breed_an_animal", "advancement grant @a only minecraft:husbandry/tame_an_animal", "recipe give @a *", "gamemode creative @a", "time set 4000", "weather clear", "difficulty peaceful",
                            "kill @e[type=!minecraft:player]",
                            fill(-20, -4, -20, 20, -2, 20, "dirt"), fill(-20, -1, -20, 20, -1, 20, "grass_block"),
                            fill(-20, 0, -20, 20, 12, 20, "air"),
                            fill(-11, 0, 2, 11, 0, 2, "oak_fence"), fill(-11, 0, 19, 11, 0, 19, "oak_fence"),
                            fill(-11, 0, 3, -11, 0, 18, "oak_fence"), fill(11, 0, 3, 11, 0, 18, "oak_fence"),
                            fill(13, 0, 2, 19, 0, 2, "oak_fence"), fill(13, 0, 9, 19, 0, 9, "oak_fence"),
                            fill(13, 0, 3, 13, 0, 8, "oak_fence"), fill(19, 0, 3, 19, 0, 8, "oak_fence"),
                            fill(13, 0, 11, 23, 1, 11, "oak_fence"), fill(13, 0, 21, 23, 1, 21, "oak_fence"),
                            fill(13, 0, 12, 13, 1, 20, "oak_fence"), fill(23, 0, 12, 23, 1, 20, "oak_fence"),
                            fill(15, 0, 16, 21, 0, 16, "bamboo"),
                            fill(-17, -1, 6, -14, -1, 9, "water"),
                            fill(-10, 0, 18, -9, 0, 18, "hay_block"), fill(10, 0, 3, 10, 0, 3, "hay_block"),
                            fill(-4, 0, 9, -4, 0, 9, "poppy"), fill(6, 0, 10, 6, 0, 10, "dandelion"),
                            fill(-7, 0, 12, -7, 0, 12, "cornflower"), fill(2, 0, 6, 2, 0, 6, "oxeye_daisy"));
                    FamilyTreeState.get(server).removeMatching(pet -> true);
                    ServerPlayer player = player(server, client);
                    for (Founder founder : FOUNDERS) horses.put(founder.name(), horse(world, player, founder));
                    rosie = animal(world, player, "minecraft:sheep", 14.5, 4.5, "red", "Rosie");
                    snowball = animal(world, player, "minecraft:sheep", 15.5, 6.5, "white", "Snowball");
                    sky = animal(world, player, "minecraft:sheep", 17.5, 4.5, "blue", "Sky");
                    poppy = animal(world, player, "minecraft:sheep", 18, 7, "red", "Poppy");
                    for (double[] spot : new double[][]{{15.5, 13.5}, {20.5, 13.5}, {15.5, 18.5}, {20.5, 18.5}, {18, 13.5}, {18, 18.5}})
                        pandas.add(animal(world, player, "minecraft:panda", spot[0], spot[1], "normal/brown", null));
                    azul = animal(world, player, "minecraft:axolotl", -15.5, 7.5, "blue", "Azul");
                    flying(server, client);
                    camera(server, client, 0, 3.5, -4, 0, 1, 11);
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
        }
        if (phaseTick == 40) server.execute(() -> decorate(server));
        if (phaseTick == 80) next("horses");
    }

    private void breedHorses(Minecraft client, DemoClient runner, MinecraftServer server) throws Exception {
        Pair pair = PAIRS.get(pairIndex);
        if (phaseTick == 1) {
            child = null;
            childTick = 0;
            mark("pair " + pair.a() + " x " + pair.b());
            server.execute(() -> {
                horses.get(pair.a()).teleportTo(OX - 1.2, oy, OZ + 10.5);
                horses.get(pair.b()).teleportTo(OX + 1.2, oy, OZ + 10.5);
                camera(server, client, 0, 2.2, 4.5, 0, 0.9, 10.5);
            });
        }
        if (phaseTick == 20) server.execute(() -> {
            ServerPlayer player = player(server, client);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_CARROT, 64));
            Animal a = horses.get(pair.a()), b = horses.get(pair.b());
            a.mobInteract(player, InteractionHand.MAIN_HAND);
            b.mobInteract(player, InteractionHand.MAIN_HAND);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (!a.isInLove() || !b.isInLove()) throw new AssertionError("Golden carrots did not start breeding " + pair);
        });
        if (phaseTick > 20 && child == null && phaseTick % 5 == 0) server.execute(() -> {
            UUID found = childOf(server, horses.get(pair.a()), horses.get(pair.b()));
            if (found != null) { child = found; childTick = phaseTick; }
        });
        if (child == null) {
            if (phaseTick > 900) throw new AssertionError("Vanilla AI breeding did not produce a foal for " + pair);
            return;
        }
        int sinceBirth = phaseTick - childTick;
        if (sinceBirth == 50) server.execute(() -> {
            try {
                Animal foal = (Animal) server.overworld().getEntity(child);
                AnimalCapture.nameTag(player(server, client), foal, pair.child());
                horses.put(pair.child(), foal);
                if (pairIndex == PAIRS.size() - 1) return;
                foal.setAge(0);
                foal.getClass().getMethod("tameWithName", Player.class).invoke(foal, player(server, client));
                foal.setHealth(foal.getMaxHealth());
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
        if (sinceBirth == 80) {
            notes.add(pair.child() + " born from " + pair.a() + " and " + pair.b());
            pairIndex++;
            phaseTick = 0;
            if (pairIndex == PAIRS.size()) next("jade");
        }
    }

    private void jadeShot(Minecraft client, DemoClient runner, MinecraftServer server) throws Exception {
        Animal nova = horses.get("Nova");
        if (phaseTick == 1) server.execute(() -> {
            int back = 0;
            for (var entry : horses.entrySet()) {
                ((Mob) entry.getValue()).setNoAi(true);
                if (List.of("Nova", "Clover", "Pippin").contains(entry.getKey())) continue;
                entry.getValue().teleportTo(OX - 9 + 2.6 * back++, oy, OZ + 17.5);
            }
            nova.teleportTo(OX + 0.5, oy, OZ + 10.5);
            horses.get("Clover").teleportTo(OX - 2, oy, OZ + 12.5);
            horses.get("Pippin").teleportTo(OX + 3, oy, OZ + 12.5);
            camera(server, client, 0.2, 0, 7.4, 0.5, 0.7, 10.5);
        });
        if (phaseTick == 60) runner.capture(client, "media-jade.png");
        if (phaseTick == 100) {
            server.execute(() -> {
                for (Animal horse : horses.values()) ((Mob) horse).setNoAi(false);
            });
            next("sheep");
        }
    }

    /** One pair at a time, because sheep in love pair with the nearest partner and can breed through a fence. */
    private void breedSheep(Minecraft client, DemoClient runner, MinecraftServer server) throws Exception {
        boolean second = pinkLamb != null;
        if (phaseTick == 1) server.execute(() -> camera(server, client, 16, 3.2, -1.5, 16, 0.5, 5.5));
        if (phaseTick == 30 || second && bornTick > 0 && phaseTick == bornTick + 40) server.execute(() -> {
            ServerPlayer player = player(server, client);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 64));
            for (Animal sheep : second ? List.of(sky, poppy) : List.of(rosie, snowball)) sheep.mobInteract(player, InteractionHand.MAIN_HAND);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        if (phaseTick > 30 && phaseTick % 10 == 0) server.execute(() -> {
            if (pinkLamb == null) pinkLamb = childOf(server, rosie, snowball);
            else if (purpleLamb == null) purpleLamb = childOf(server, sky, poppy);
        });
        if (pinkLamb != null && bornTick == 0) { bornTick = phaseTick; mark("pink lamb"); }
        if (phaseTick > 1800) throw new AssertionError("Vanilla AI breeding did not produce both lambs");
        if (purpleLamb != null && namedTick == 0) namedTick = phaseTick;
        if (namedTick > 0 && phaseTick - namedTick == 60) {
            namedTick = 0;
            next("pandas");
        }
    }

    private void breedPandas(Minecraft client, DemoClient runner, MinecraftServer server) throws Exception {
        if (phaseTick == 1) {
            mark("panda round " + pandaRound);
            server.execute(() -> {
                camera(server, client, 18, 5, 6, 18, 0.5, 16);
                ServerPlayer player = player(server, client);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BAMBOO, 64));
                for (Animal panda : pandas) {
                    panda.setAge(0);
                    panda.mobInteract(player, InteractionHand.MAIN_HAND);
                }
            });
        }
        if (phaseTick > 20 && phaseTick % 10 == 0 && brownPanda == null) server.execute(() -> {
            for (AnimalRecord record : FamilyTreeState.get(server).all())
                if ("minecraft:panda".equals(record.speciesId()) && record.parentA() != null
                        && "brown/brown".equals(record.variantId())) brownPanda = record.id();
        });
        if (brownPanda != null && namedTick == 0) {
            namedTick = tick;
            notes.add("Brown panda born in round " + pandaRound);
            server.execute(() -> {
                try { AnimalCapture.nameTag(player(server, client), (Animal) server.overworld().getEntity(brownPanda), "Cocoa"); }
                catch (Exception failure) { throw new RuntimeException(failure); }
            });
        }
        if (namedTick > 0 && tick - namedTick == 80) next("browser");
        if (brownPanda == null && phaseTick == 400) {
            pandaRound++;
            phaseTick = 0;
            if (pandaRound == 6) {
                notes.add("No brown panda after six rounds");
                next("browser");
            }
        }
    }

    private void browserShots(Minecraft client, DemoClient runner) throws Exception {
        if (phaseTick == 1) {
            client.getSingleplayerServer().execute(() -> camera(client.getSingleplayerServer(), client, -1, 3.5, -3, 0, 0.5, 11));
            browser = new FamilyTreeBrowserScreen();
            runner.show(client, browser);
            FamilyTreeClient.requestSnapshot(false);
        }
        if (phaseTick == 30) {
            if (snapshot() == null) throw new AssertionError("The browser did not receive a snapshot");
            mark("browser pets");
            tab(client, runner, "familytree.screen.category.pets");
        }
        if (phaseTick == 50) runner.capture(client, "media-browser-pets.png");
        if (phaseTick == 70) { mark("browser farm"); tab(client, runner, "familytree.screen.category.farm"); }
        if (phaseTick == 95) runner.capture(client, "media-browser-farm.png");
        if (phaseTick == 110) { mark("browser wildlife"); tab(client, runner, "familytree.screen.category.wildlife"); }
        if (phaseTick == 135) runner.capture(client, "media-browser-wildlife.png");
        if (phaseTick == 150) { mark("browser all"); tab(client, runner, "familytree.screen.category.all"); }
        String query = "blue";
        if (phaseTick >= 170 && phaseTick < 170 + query.length() * 4 && (phaseTick - 170) % 4 == 0) {
            if (phaseTick == 170) mark("browser search");
            EditBox search = field(browser, FamilyTreeBrowserScreen.class, "search");
            search.setFocused(true);
            search.setValue(query.substring(0, (phaseTick - 170) / 4 + 1));
        }
        if (phaseTick == 230) runner.capture(client, "media-browser-search.png");
        if (phaseTick == 260) {
            EditBox search = field(browser, FamilyTreeBrowserScreen.class, "search");
            search.setValue("");
            tab(client, runner, "familytree.screen.category.pets");
            next("tree");
        }
    }

    private void treeShots(Minecraft client, DemoClient runner) throws Exception {
        UUID nova = horses.get("Nova").getUUID();
        if (phaseTick == 1) {
            tree = new FamilyTreeViewScreen(browser, snapshot(), nova);
            runner.show(client, tree);
        }
        if (phaseTick == 5) fit = view();
        if (phaseTick == 20) runner.capture(client, "media-tree-fit.png");
        if (phaseTick == 25) mark("tree pan");
        if (phaseTick >= 25 && phaseTick <= 205) {
            double[] novaCard = node(nova);
            double[] left = null, right = null;
            for (Founder founder : FOUNDERS) {
                double[] card = node(horses.get(founder.name()).getUUID());
                if (left == null || card[0] < left[0]) left = card;
                if (right == null || card[0] > right[0]) right = card;
            }
            int t = phaseTick - 25;
            double[] close = {1.1, novaCard[0], novaCard[1] - 70};
            double[] founders = {0.95, left[0] + 230, left[1] + 60}, others = {0.95, right[0] - 230, right[1] + 60};
            if (t <= 40) move(fit, close, t / 40.0);
            else if (t <= 70) move(close, close, 1);
            else if (t <= 110) move(close, founders, (t - 70) / 40.0);
            else if (t <= 150) move(founders, others, (t - 110) / 40.0);
            else move(others, fit, (t - 150) / 30.0);
            if (t == 55) runner.capture(client, "media-tree-nova.png");
        }
        if (phaseTick == 215) {
            mark("tree pan end");
            best = bestFoal();
            notes.add("Foal with the most stats above both parents: " + best);
            tree = new FamilyTreeViewScreen(browser, snapshot(), horses.get(best).getUUID());
            runner.show(client, tree);
        }
        if (phaseTick == 220) {
            Animal foal = horses.get(best);
            AnimalRecord record = snapshot().records().stream().filter(pet -> pet.id().equals(foal.getUUID())).findFirst().orElseThrow();
            double[] child = node(foal.getUUID()), a = node(record.parentA()), b = node(record.parentB());
            double[] view = {1.0, (a[0] + b[0] + child[0]) / 3, (a[1] + child[1]) / 2};
            move(view, view, 1);
        }
        if (phaseTick == 240) runner.capture(client, "media-foal-stats.png");
        if (phaseTick == 250) {
            tree = new FamilyTreeViewScreen(browser, snapshot(), nova);
            runner.show(client, tree);
            next("export");
        }
    }

    private void exportAndWaypoint(Minecraft client, DemoClient runner, MinecraftServer server) throws Exception {
        if (phaseTick == 5) runner.press(DemoClient.tr("familytree.export.button"));
        if (phaseTick == 80) runner.capture(client, "media-export-status.png");
        if (phaseTick == 100 && runner.exportCount(client) <= exportsBefore) throw new AssertionError("The export is missing");
        if (phaseTick == 110 && com.egakh.familytree.client.integration.MapWaypoints.available()) {
            mark("waypoint");
            runner.press(DemoClient.tr("familytree.waypoint.button"));
        }
        if (phaseTick == 150) {
            runner.capture(client, "media-waypoint-button.png");
            runner.show(client, null);
            server.execute(() -> {
                Animal nova = horses.get("Nova");
                ((Mob) nova).setNoAi(true);
                camera(server, client, nova.getX() - OX - 6, 2.5, nova.getZ() - OZ - 7, nova.getX() - OX, 0.8, nova.getZ() - OZ);
            });
        }
        if (phaseTick == 220) runner.capture(client, "media-waypoint-world.png");
        if (phaseTick == 230) next("variants");
    }

    private void variantShots(Minecraft client, DemoClient runner) throws Exception {
        if (phaseTick == 1 && pinkLamb != null) runner.show(client, new FamilyTreeViewScreen(browser, snapshot(), pinkLamb));
        if (phaseTick == 30 && pinkLamb != null) runner.capture(client, "media-sheep-variant.png");
        if (phaseTick == 40 && brownPanda != null) runner.show(client, new FamilyTreeViewScreen(browser, snapshot(), brownPanda));
        if (phaseTick == 70 && brownPanda != null) runner.capture(client, "media-panda-variant.png");
        if (phaseTick == 80) runner.show(client, new FamilyTreeViewScreen(browser, snapshot(), azul.getUUID()));
        if (phaseTick == 110) runner.capture(client, "media-axolotl.png");
        if (phaseTick == 120) next("done");
    }

    private void finish(Minecraft client) throws Exception {
        if (phaseTick != 1) return;
        var dir = client.gameDirectory.toPath();
        Files.write(dir.resolve("media-timeline.txt"), timeline);
        Files.writeString(dir.resolve("demo-result.txt"), "PASS: media scene; four horse generations from vanilla AI breeding;"
                + " sheep and panda breeding; browser tabs and search; tree pan and zoom; PNG export\n"
                + String.join("\n", notes) + "\n");
        client.stop();
    }

    private String bestFoal() throws Exception {
        var snapshot = snapshot();
        Map<UUID, AnimalRecord> byId = new LinkedHashMap<>();
        for (AnimalRecord record : snapshot.records()) byId.put(record.id(), record);
        String best = "Nova";
        int bestScore = -1;
        for (Pair pair : PAIRS) {
            AnimalRecord foal = byId.get(horses.get(pair.child()).getUUID());
            int beats = 0, trails = 0;
            for (HorseStatUnits.Stat stat : HorseStatUnits.Stat.values()) {
                var trend = HorseStatsText.trend(stat, foal, byId::get);
                if (trend == HorseStatUnits.Trend.BEATS_BOTH) beats++;
                if (trend == HorseStatUnits.Trend.TRAILS_BOTH) trails++;
            }
            int score = (beats > 0 ? 10 : 0) + (trails > 0 ? 5 : 0) + beats + trails;
            notes.add(pair.child() + ": " + beats + " above both parents, " + trails + " below both");
            if (score > bestScore) { bestScore = score; best = pair.child(); }
        }
        return best;
    }

    private FamilyTreeSnapshotPayload snapshot() throws Exception {
        return field(browser, FamilyTreeBrowserScreen.class, "snapshot");
    }

    private Object viewport() throws Exception { return field(tree, FamilyTreeViewScreen.class, "viewport"); }

    private double[] view() throws Exception {
        Object viewport = viewport();
        double zoom = accessible(viewport, "zoom").getDouble(viewport);
        double panX = accessible(viewport, "panX").getDouble(viewport);
        double panY = accessible(viewport, "panY").getDouble(viewport);
        int top = 106, bottom = treeBottom();
        return new double[]{zoom, (tree.width / 2.0 - panX) / zoom, ((top + bottom) / 2.0 - panY) / zoom};
    }

    private int treeBottom() throws Exception {
        var method = FamilyTreeViewScreen.class.getDeclaredMethod("treeBottom");
        method.setAccessible(true);
        return (int) method.invoke(tree);
    }

    /** Moves the camera between two views of zoom and centre, eased so the motion starts and ends slowly. */
    private void move(double[] from, double[] to, double t) throws Exception {
        t = Math.max(0, Math.min(1, t));
        double eased = t * t * (3 - 2 * t);
        Object viewport = viewport();
        double zoom = from[0] + (to[0] - from[0]) * eased;
        double x = from[1] + (to[1] - from[1]) * eased, y = from[2] + (to[2] - from[2]) * eased;
        accessible(viewport, "zoom").setDouble(viewport, zoom);
        var center = viewport.getClass().getDeclaredMethod("center", double.class, double.class, int.class, int.class, int.class);
        center.setAccessible(true);
        center.invoke(viewport, x, y, tree.width, 106, treeBottom());
    }

    private static java.lang.reflect.Field accessible(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private double[] node(UUID id) throws Exception {
        Object layout = field(tree, FamilyTreeViewScreen.class, "tree");
        Object node = ((Map<?, ?>) layout.getClass().getField("byId").get(layout)).get(id);
        if (node == null) throw new AssertionError("The tree has no card for " + id);
        var type = node.getClass();
        return new double[]{type.getField("x").getDouble(node) + com.egakh.familytree.client.screen.TreeLayout.NODE_WIDTH / 2.0,
                type.getField("y").getDouble(node) + com.egakh.familytree.client.screen.TreeLayout.NODE_HEIGHT / 2.0};
    }

    private void select(UUID id) throws Exception {
        var selected = FamilyTreeViewScreen.class.getDeclaredField("selectedId");
        selected.setAccessible(true);
        selected.set(tree, id);
        var update = FamilyTreeViewScreen.class.getDeclaredMethod("updateControls");
        update.setAccessible(true);
        update.invoke(tree);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(owner);
    }

    private static UUID childOf(MinecraftServer server, Animal a, Animal b) {
        var found = FamilyTreeState.get(server).all().stream().filter(pet -> pet.parentA() != null && pet.parentB() != null
                && List.of(a.getUUID(), b.getUUID()).containsAll(List.of(pet.parentA(), pet.parentB()))).findFirst().orElse(null);
        return found == null || server.overworld().getEntity(found.id()) == null ? null : found.id();
    }

    private static ServerPlayer player(MinecraftServer server, Minecraft client) {
        return server.getPlayerList().getPlayer(client.player.getUUID());
    }

    private static void run(MinecraftServer server, String... commands) {
        var source = server.createCommandSourceStack().withSuppressedOutput();
        for (String command : commands) server.getCommands().performPrefixedCommand(source, command);
    }

    private String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        return "fill " + (OX + x1) + " " + (oy + y1) + " " + (OZ + z1) + " " + (OX + x2) + " " + (oy + y2) + " " + (OZ + z2) + " " + block;
    }

    /** Places the player's feet at a scene-relative position, with the eyes looking at another. */
    private void camera(MinecraftServer server, Minecraft client, double x, double y, double z, double lookX, double lookY, double lookZ) {
        double dx = lookX - x, dy = lookY - (y + 1.62), dz = lookZ - z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz)), pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        run(server, String.format(java.util.Locale.ROOT, "tp %s %.2f %.2f %.2f %.1f %.1f", client.player.getScoreboardName(),
                OX + x, oy + y, OZ + z, yaw, pitch));
    }

    /** Trees and grass around the scene, placed with Minecraft's own world generation features. */
    private void decorate(MinecraftServer server) {
        var random = new java.util.Random(7);
        String[] trees = {"oak", "birch", "fancy_oak", "oak", "birch", "oak"};
        for (int i = 0; i < 46; i++) {
            double angle = random.nextDouble() * Math.PI * 2, radius = 27 + random.nextDouble() * 34;
            run(server, String.format(java.util.Locale.ROOT, "place feature minecraft:%s %d %d %d", trees[i % trees.length],
                    OX + (int) (Math.cos(angle) * radius), oy, OZ + 10 + (int) (Math.sin(angle) * radius)));
        }
        for (int i = 0; i < 60; i++) {
            double angle = random.nextDouble() * Math.PI * 2, radius = 22 + random.nextDouble() * 40;
            String feature = i % 3 == 0 ? "flower_plain" : i % 3 == 1 ? "patch_grass" : "patch_tall_grass";
            run(server, String.format(java.util.Locale.ROOT, "place feature minecraft:%s %d %d %d", feature,
                    OX + (int) (Math.cos(angle) * radius), oy, OZ + 10 + (int) (Math.sin(angle) * radius)));
        }
    }

    private static void flying(MinecraftServer server, Minecraft client) {
        ServerPlayer player = player(server, client);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    private Animal horse(ServerLevel world, ServerPlayer owner, Founder founder) throws Exception {
        Animal animal = HorseCapture.create(world, "HORSE");
        animal.setPos(OX + founder.x(), oy, OZ + founder.z());
        Class<?> base = animal.getClass();
        while (!base.getSimpleName().equals("AbstractHorse")) base = base.getSuperclass();
        var randomize = base.getDeclaredMethod("randomizeAttributes", net.minecraft.util.RandomSource.class);
        randomize.setAccessible(true);
        randomize.invoke(animal, animal.getRandom());
        animal.setHealth(animal.getMaxHealth());
        for (var method : animal.getClass().getDeclaredMethods()) {
            if (!method.getName().equals("setVariantAndMarkings")) continue;
            method.setAccessible(true);
            method.invoke(animal, constant(method.getParameterTypes()[0], founder.coat()), constant(method.getParameterTypes()[1], founder.markings()));
        }
        world.addFreshEntity(animal);
        animal.getClass().getMethod("tameWithName", Player.class).invoke(animal, owner);
        AnimalCapture.nameTag(owner, animal, founder.name());
        return animal;
    }

    private Animal animal(ServerLevel world, ServerPlayer owner, String id, double x, double z, String variant, String name) throws Exception {
        String path = id.substring(id.indexOf(':') + 1).toUpperCase(java.util.Locale.ROOT);
        Animal animal = HorseCapture.create(world, path);
        animal.setPos(OX + x, oy + ("minecraft:axolotl".equals(id) ? -1 : 0), OZ + z);
        EntityVariants.apply(animal, variant, world.registryAccess());
        world.addFreshEntity(animal);
        if (name != null) AnimalCapture.nameTag(owner, animal, name);
        return animal;
    }

    private static Object constant(Class<?> type, String name) {
        for (Object value : type.getEnumConstants()) if (((Enum<?>) value).name().equals(name)) return value;
        throw new IllegalArgumentException(name);
    }
}
