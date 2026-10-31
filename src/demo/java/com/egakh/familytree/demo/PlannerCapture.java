package com.egakh.familytree.demo;

import com.egakh.familytree.breeding.BestPairs;
import com.egakh.familytree.breeding.BreedingPair;
import com.egakh.familytree.breeding.BreedingPlan;
import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.screen.BestPairsScreen;
import com.egakh.familytree.client.screen.BreedingPlannerScreen;
import com.egakh.familytree.client.screen.FamilyTreeBrowserScreen;
import com.egakh.familytree.client.screen.FamilyTreeScreen;
import com.egakh.familytree.client.screen.FamilyTreeViewScreen;
import com.egakh.familytree.client.screen.TreeLayout;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.util.HorseStatUnits;
import com.egakh.familytree.util.Kinship;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Breeds horses, a horse and a donkey, and llamas through four generations with vanilla feeding and AI breeding,
 * then checks every foal against the planner's prediction for its actual parents. The runner only resets breeding
 * cooldowns, grows foals, and tames them so later generations can breed; the game picks every stat.
 */
public final class PlannerCapture {
    private record Plan(String first, String second, String foal) {}

    private static final List<List<Plan>> ROUNDS = List.of(
            List.of(new Plan("Comet", "Dusk", "Ember"), new Plan("Blaze", "Willow", "Flint"), new Plan("Storm", "Maple", "Sage"),
                    new Plan("Hazel", "Jasper", "Mocha"), new Plan("Tuft", "Pebble", "Clover")),
            List.of(new Plan("Comet", "Dusk", "Ash"), new Plan("Blaze", "Willow", "Rowan"), new Plan("Storm", "Maple", "Fern"),
                    new Plan("Hazel", "Jasper", "Toffee"), new Plan("Tuft", "Pebble", "Bramble")),
            List.of(new Plan("Comet", "Dusk", "Juniper"), new Plan("Blaze", "Willow", "Wren"), new Plan("Storm", "Maple", "Thistle"),
                    new Plan("Hazel", "Jasper", "Biscuit"), new Plan("Tuft", "Pebble", "Nettle")),
            List.of(new Plan("Ember", "Flint", "Aspen"), new Plan("Ash", "Juniper", "Cinder"), new Plan("Rowan", "Sage", "Briar"),
                    new Plan("Fern", "Thistle", "Moss"), new Plan("Clover", "Bramble", "Pip")),
            List.of(new Plan("Aspen", "Briar", "Quill"), new Plan("Aspen", "Cinder", "Sorrel")),
            List.of(new Plan("Aspen", "Briar", "Tansy"), new Plan("Aspen", "Cinder", "Yarrow")));

    private final Map<String, Animal> animals = new LinkedHashMap<>();
    private final Map<String, UUID> ids = new LinkedHashMap<>();
    private final List<String> report = new ArrayList<>();
    private final Set<UUID> claimed = new HashSet<>();
    private int tick, roundTick, round = -1, uiTick = -1;
    private volatile int pendingFoals;
    private volatile String failure;
    private int checkedStats, inRange, foals;
    private final double[] predictedAbove = new double[4];
    private final int[] observedAbove = new int[4], counted = new int[4];
    private FamilyTreeBrowserScreen browser;
    private FamilyTreeSnapshotPayload snapshot;
    private int frames;

    public void tick(Minecraft client, DemoClient runner) throws Exception {
        if (client.level == null || client.player == null) return;
        if (failure != null) throw new AssertionError(failure);
        tick++;
        MinecraftServer server = client.getSingleplayerServer();
        if (tick == 1) server.execute(() -> run(() -> setup(server, client.player.getUUID())));
        if (tick == 40) startRound(server, client.player.getUUID(), 0);
        if (round >= 0 && uiTick < 0) {
            roundTick++;
            if (roundTick % 10 == 0) server.execute(() -> run(() -> collect(server, client.player.getUUID())));
            if (roundTick % 8 == 0 && round <= 1) runner.capture(client, String.format(Locale.ROOT, "planner-world-%04d.png", tick));
            if (pendingFoals == 0 && roundTick > 20) {
                if (round + 1 < ROUNDS.size()) startRound(server, client.player.getUUID(), round + 1);
                else uiTick = 0;
            } else if (roundTick == 1500) startRound(server, client.player.getUUID(), round);
            else if (roundTick > 3000) throw new AssertionError("Round " + (round + 1) + " still waits for " + pendingFoals + " foals");
        }
        if (uiTick >= 0) ui(client, runner, server);
    }

    private interface Step { void run() throws Exception; }

    private void run(Step step) {
        try { step.run(); }
        catch (Throwable error) {
            failure = error.toString();
            com.egakh.familytree.FamilyTreeMod.LOGGER.error("Planner capture failed", error);
        }
    }

    private void setup(MinecraftServer server, UUID playerId) throws Exception {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        for (String command : new String[]{"gamemode creative @a", "time set day", "gamerule doDaylightCycle false", "weather clear",
                "kill @e[type=!minecraft:player]", "fill -32 100 -8 32 100 16 grass_block", "fill -32 101 -8 32 106 16 air"})
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        for (int pen = 0; pen < 5; pen++) {
            int x0 = pen * 11 - 27, x1 = x0 + 8;
            for (String command : new String[]{"fill " + x0 + " 101 0 " + x1 + " 101 0 oak_fence", "fill " + x0 + " 101 8 " + x1 + " 101 8 oak_fence",
                    "fill " + x0 + " 101 0 " + x0 + " 101 8 oak_fence", "fill " + x1 + " 101 0 " + x1 + " 101 8 oak_fence"})
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp @a 0 104 -7 0 30");
        FamilyTreeState.get(server).removeMatching(pet -> true);
        ServerLevel world = server.overworld();
        founder(world, player, "HORSE", "Comet", "CHESTNUT", "WHITE_DOTS");
        founder(world, player, "HORSE", "Dusk", "BLACK", "WHITE_FIELD");
        founder(world, player, "HORSE", "Blaze", "WHITE", "NONE");
        founder(world, player, "HORSE", "Willow", "CREAMY", "BLACK_DOTS");
        founder(world, player, "HORSE", "Storm", "GRAY", "WHITE");
        founder(world, player, "HORSE", "Maple", "DARK_BROWN", "NONE");
        founder(world, player, "HORSE", "Hazel", "BROWN", "WHITE");
        founder(world, player, "DONKEY", "Jasper", null, null);
        llama(founder(world, player, "LLAMA", "Tuft", null, null), 4, "GRAY");
        llama(founder(world, player, "LLAMA", "Pebble", null, null), 2, "BROWN");
    }

    private Animal founder(ServerLevel world, ServerPlayer owner, String type, String name, String coat, String markings) throws Exception {
        Animal animal = HorseCapture.horse(world, type, name, 0, 4, coat, markings, owner);
        animals.put(name, animal);
        ids.put(name, animal.getUUID());
        return animal;
    }

    /** Llamas made without spawn setup have strength 0, which breeding can't use. */
    private static void llama(Animal llama, int strength, String color) throws Exception {
        Method setStrength = findMethod(llama.getClass(), "setStrength", int.class);
        setStrength.setAccessible(true);
        setStrength.invoke(llama, strength);
        // setVariant is private on newer versions, so search declared methods up the class chain.
        for (Class<?> type = llama.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals("setVariant") || method.getParameterCount() != 1 || !method.getParameterTypes()[0].isEnum()) continue;
                method.setAccessible(true);
                for (Object value : method.getParameterTypes()[0].getEnumConstants())
                    if (((Enum<?>) value).name().equals(color)) method.invoke(llama, value);
                return;
            }
        }
        throw new AssertionError("Llama has no setVariant");
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredMethod(name, parameters); }
            catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }

    private void startRound(MinecraftServer server, UUID playerId, int index) {
        round = index;
        roundTick = 0;
        pendingFoals = ROUNDS.get(index).size() - (int) ROUNDS.get(index).stream().filter(plan -> ids.containsKey(plan.foal())).count();
        server.execute(() -> run(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            List<Plan> plans = ROUNDS.get(index);
            for (int pen = 0; pen < plans.size(); pen++) {
                Plan plan = plans.get(pen);
                if (ids.containsKey(plan.foal())) continue;
                double x = pen * 11 - 27 + 4.5;
                Animal first = live(server, plan.first()), second = live(server, plan.second());
                first.setPos(x - 1.5, 101, 4.5);
                second.setPos(x + 1.5, 101, 4.5);
                for (Animal parent : List.of(first, second)) {
                    parent.setAge(0);
                    parent.setHealth(parent.getMaxHealth());
                    boolean llama = parent.getClass().getSimpleName().contains("Llama");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(llama ? Items.HAY_BLOCK : Items.GOLDEN_CARROT, 64));
                    parent.mobInteract(player, InteractionHand.MAIN_HAND);
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                if (!first.isInLove() || !second.isInLove())
                    throw new AssertionError("Feeding did not start breeding for " + plan.first() + " and " + plan.second());
            }
        }));
    }

    private Animal live(MinecraftServer server, String name) {
        Entity entity = server.overworld().getEntity(ids.get(name));
        if (!(entity instanceof Animal animal)) throw new AssertionError(name + " is not loaded");
        animals.put(name, animal);
        return animal;
    }

    /** Matches each new foal to its planned parents, then checks every stat against the planner's range. */
    private void collect(MinecraftServer server, UUID playerId) throws Exception {
        FamilyTreeState state = FamilyTreeState.get(server);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        int waiting = 0;
        for (Plan plan : ROUNDS.get(round)) {
            if (ids.containsKey(plan.foal())) continue;
            UUID a = ids.get(plan.first()), b = ids.get(plan.second());
            AnimalRecord child = state.all().stream().filter(pet -> !claimed.contains(pet.id()) && pet.parentA() != null && pet.parentB() != null
                    && Set.of(a, b).equals(Set.of(pet.parentA(), pet.parentB()))).findFirst().orElse(null);
            Entity entity = child == null ? null : server.overworld().getEntity(child.id());
            if (!(entity instanceof Animal foal) || child.horseStats() == null) {
                waiting++;
                continue;
            }
            claimed.add(child.id());
            ids.put(plan.foal(), child.id());
            animals.put(plan.foal(), foal);
            foal.setCustomName(Component.literal(plan.foal()));
            foal.setCustomNameVisible(true);
            foal.setAge(0);
            Method tame = findMethod(foal.getClass(), "tameWithName", Player.class);
            tame.invoke(foal, player);
            foal.setHealth(foal.getMaxHealth());
            for (String parent : List.of(plan.first(), plan.second())) live(server, parent).setAge(0);
            check(plan, state.get(a), state.get(b), child);
        }
        pendingFoals = waiting;
    }

    private void check(Plan plan, AnimalRecord first, AnimalRecord second, AnimalRecord child) {
        boolean llama = BreedingPair.llamaLike(child.speciesId());
        List<BreedingPlan.StatForecast> forecasts = BreedingPlan.forecasts(first.horseStats(), second.horseStats(), llama);
        StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%-8s %-8s x %-8s %s", plan.foal(), plan.first(), plan.second(),
                child.speciesId().replace("minecraft:", "")));
        HorseStats stats = child.horseStats();
        for (BreedingPlan.StatForecast forecast : forecasts) {
            HorseStatUnits.Stat stat = forecast.stat();
            Double raw = stat.value(stats);
            if (raw == null) throw new AssertionError(plan.foal() + " has no " + stat);
            double actual = stat == HorseStatUnits.Stat.STRENGTH ? HorseStatUnits.chestSlots(stats.strength()) : raw;
            boolean inside = actual >= forecast.lowest() - 1e-9 && actual <= forecast.highest() + 1e-9;
            checkedStats++;
            if (inside) inRange++;
            else failure = plan.foal() + " " + stat + " " + actual + " is outside " + forecast.lowest() + " to " + forecast.highest();
            double better = Math.max(forecast.parentA(), forecast.parentB());
            int index = stat.ordinal();
            predictedAbove[index] += forecast.chanceAboveBoth();
            if (actual > better + 1e-12) observedAbove[index]++;
            counted[index]++;
            line.append(String.format(Locale.ROOT, " | %s %.3f in [%.3f, %.3f] likely %.3f beats %.1f%%%s", stat.name().toLowerCase(Locale.ROOT),
                    actual, forecast.lowest(), forecast.highest(), forecast.likelyLow(), forecast.chanceAboveBoth() * 100,
                    actual > better + 1e-12 ? " (beat both)" : ""));
        }
        foals++;
        report.add(line.toString());
    }

    private void ui(Minecraft client, DemoClient runner, MinecraftServer server) throws Exception {
        uiTick++;
        if (uiTick == 1) {
            browser = new FamilyTreeBrowserScreen();
            runner.show(client, browser);
            FamilyTreeClient.requestSnapshot(false);
        }
        if (uiTick == 30) {
            snapshot = snapshot();
            if (snapshot == null) throw new AssertionError("No snapshot reached the browser");
            for (String name : ids.keySet())
                if (find(name) == null) throw new AssertionError(name + " is missing from the snapshot");
            runner.show(client, new FamilyTreeViewScreen(browser, snapshot, ids.get("Aspen")));
        }
        if (uiTick == 40) {
            FamilyTreeViewScreen tree = (FamilyTreeViewScreen) runner.current(client);
            click(tree, ids.get("Quill"), false);
            click(tree, ids.get("Sorrel"), true);
        }
        if (uiTick >= 40 && uiTick <= 220 && uiTick % 2 == 0) runner.capture(client, String.format(Locale.ROOT, "planner-clip-%04d.png", frames++));
        if (uiTick == 70) {
            runner.capture(client, "planner-compare.png");
            runner.press(DemoClient.tr("familytree.planner.button"));
        }
        if (uiTick == 80) {
            BreedingPlannerScreen planner = (BreedingPlannerScreen) runner.current(client);
            BreedingPlan plan = planner.plan();
            if (plan == null || !plan.canBreed || plan.relation.kind() != Kinship.Kind.SIBLING || plan.relation.half() != Kinship.Half.HALF)
                throw new AssertionError("Quill and Sorrel should plan as half siblings: " + (plan == null ? null : plan.relation));
            if (Math.abs(plan.inbreeding - 0.125) > 1e-9 && plan.inbreeding < 0.125)
                throw new AssertionError("Half siblings gave inbreeding " + plan.inbreeding);
            if (plan.stats.size() != 3) throw new AssertionError("Horse plan has " + plan.stats.size() + " stats");
        }
        if (uiTick == 120) {
            runner.capture(client, "planner-siblings.png");
            runner.press(DemoClient.tr("familytree.planner.best"));
        }
        if (uiTick == 150) {
            BestPairsScreen best = (BestPairsScreen) runner.current(client);
            if (best.outcome() == null || best.outcome().pairs().isEmpty()) throw new AssertionError("Best pairs found nothing");
            for (BestPairs.Ranked pair : best.outcome().pairs())
                if (pair.inbreeding() >= BestPairs.CLOSE) throw new AssertionError("Best pairs kept close relatives");
            runner.capture(client, "planner-best.png");
            clickRow(best, 0);
        }
        if (uiTick == 190) {
            if (!(runner.current(client) instanceof BreedingPlannerScreen top) || top.plan() == null || !top.plan().canBreed)
                throw new AssertionError("Clicking the top pair did not open it in the planner");
            runner.capture(client, "planner-best-pair.png");
        }
        if (uiTick == 230) {
            runner.show(client, new BreedingPlannerScreen(browser, snapshot, ids.get("Hazel"), ids.get("Jasper")));
        }
        if (uiTick == 245) {
            BreedingPlan mule = ((BreedingPlannerScreen) runner.current(client)).plan();
            if (mule == null || !mule.foalSpecies.equals(List.of(BreedingPair.MULE))) throw new AssertionError("Horse and donkey should plan a mule");
            runner.capture(client, "planner-mule.png");
            runner.show(client, new BreedingPlannerScreen(browser, snapshot, ids.get("Clover"), ids.get("Nettle")));
        }
        if (uiTick == 260) {
            BreedingPlan llama = ((BreedingPlannerScreen) runner.current(client)).plan();
            if (llama == null || llama.stats.isEmpty() || llama.stats.get(0).stat() != HorseStatUnits.Stat.STRENGTH)
                throw new AssertionError("Llama plan should lead with chest slots");
            runner.capture(client, "planner-llama.png");
            runner.show(client, null);
            server.execute(() -> run(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(client.player.getUUID());
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "familytree breed Quill Sorrel");
            }));
        }
        if (uiTick == 290) {
            runner.capture(client, "planner-chat.png");
            server.execute(() -> run(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(client.player.getUUID());
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "familytree menu");
            }));
        }
        if (uiTick == 310) clickItem(client, "Quill");
        if (uiTick == 330) clickItem(client, DemoClient.tr("familytree.menu.plan"));
        if (uiTick == 350) clickItem(client, "Sorrel");
        if (uiTick == 375) {
            String menu = client.player.containerMenu.getClass().getSimpleName();
            if (!menu.contains("Lectern") && !menu.equals("BookMenu")) throw new AssertionError("The chest menu plan opened " + menu + ", not a book");
            runner.capture(client, "planner-menu-book.png");
            client.player.closeContainer();
        }
        if (uiTick == 390) finish(client);
    }

    /** Clicks a chest menu slot the way a player does, so the server's menu handles a real click packet. */
    private static void clickItem(Minecraft client, String name) throws Exception {
        var menu = client.player.containerMenu;
        int slot = -1;
        for (int i = 0; i < Math.min(54, menu.slots.size()); i++)
            if (menu.slots.get(i).getItem().getHoverName().getString().equals(name)) slot = i;
        if (slot < 0) throw new AssertionError("The chest menu has no " + name);
        for (Method method : client.gameMode.getClass().getMethods()) {
            boolean click = method.getName().equals("handleInventoryMouseClick") || method.getName().equals("handleContainerInput");
            if (!click || method.getParameterCount() != 5) continue;
            Object pickup = null;
            for (Object value : method.getParameterTypes()[3].getEnumConstants())
                if (((Enum<?>) value).name().equals("PICKUP")) pickup = value;
            method.invoke(client.gameMode, menu.containerId, slot, 0, pickup, client.player);
            return;
        }
        throw new IllegalStateException("No inventory click method");
    }

    /** A real click on a card, through the tree screen's own hit testing. */
    private void click(FamilyTreeViewScreen tree, UUID id, boolean shift) throws Exception {
        Field treeField = FamilyTreeViewScreen.class.getDeclaredField("tree");
        treeField.setAccessible(true);
        TreeLayout.Node node = ((TreeLayout.Result) treeField.get(tree)).byId.get(id);
        if (node == null) throw new AssertionError("Card missing from the tree");
        Field viewportField = FamilyTreeViewScreen.class.getDeclaredField("viewport");
        viewportField.setAccessible(true);
        Object viewport = viewportField.get(tree);
        double zoom = number(viewport, "zoom"), panX = number(viewport, "panX"), panY = number(viewport, "panY");
        Field shiftField = FamilyTreeScreen.class.getDeclaredField("shiftClick");
        shiftField.setAccessible(true);
        shiftField.setBoolean(tree, shift);
        Method onClick = FamilyTreeViewScreen.class.getDeclaredMethod("onMouseClick", double.class, double.class, int.class, boolean.class);
        onClick.setAccessible(true);
        onClick.invoke(tree, node.x * zoom + panX + 3, node.y * zoom + panY + 3, InputConstants.MOUSE_BUTTON_LEFT, false);
        shiftField.setBoolean(tree, false);
        Method release = FamilyTreeViewScreen.class.getDeclaredMethod("onMouseRelease", int.class);
        release.setAccessible(true);
        release.invoke(tree, InputConstants.MOUSE_BUTTON_LEFT);
        Field compare = FamilyTreeViewScreen.class.getDeclaredField(shift ? "compareId" : "selectedId");
        compare.setAccessible(true);
        if (!id.equals(compare.get(tree))) throw new AssertionError((shift ? "Shift-click" : "Click") + " did not reach the card");
    }

    private static double number(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(owner);
    }

    private static void clickRow(BestPairsScreen screen, int row) throws Exception {
        Method listTop = BestPairsScreen.class.getDeclaredMethod("listTop");
        listTop.setAccessible(true);
        Method onClick = BestPairsScreen.class.getDeclaredMethod("onMouseClick", double.class, double.class, int.class, boolean.class);
        onClick.setAccessible(true);
        onClick.invoke(screen, screen.width / 2.0, (int) listTop.invoke(screen) + row * 26 + 8.0, InputConstants.MOUSE_BUTTON_LEFT, false);
    }

    private void finish(Minecraft client) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "%d foals over %d rounds; %d of %d stats inside the predicted range", foals, ROUNDS.size(), inRange, checkedStats));
        for (HorseStatUnits.Stat stat : HorseStatUnits.Stat.values()) {
            int i = stat.ordinal();
            if (counted[i] == 0) continue;
            lines.add(String.format(Locale.ROOT, "%s: %d of %d foals beat both parents; the planner expected %.1f", stat.name().toLowerCase(Locale.ROOT),
                    observedAbove[i], counted[i], predictedAbove[i]));
        }
        lines.add("");
        lines.addAll(report);
        Files.write(client.gameDirectory.toPath().resolve("planner-result.txt"), lines);
        if (inRange != checkedStats) throw new AssertionError("Some foal stats fell outside the planner's range");
        Files.writeString(client.gameDirectory.toPath().resolve("demo-result.txt"), String.format(Locale.ROOT,
                "PASS: planner; %d foals bred by vanilla AI over four generations; %d of %d stats inside the predicted range;"
                        + " Shift-click comparison; half siblings; best pairs; mule; llama chest slots; /familytree breed; chest menu book%n",
                foals, inRange, checkedStats));
        client.stop();
    }

    private FamilyTreeSnapshotPayload snapshot() throws Exception {
        Field field = FamilyTreeBrowserScreen.class.getDeclaredField("snapshot");
        field.setAccessible(true);
        return (FamilyTreeSnapshotPayload) field.get(browser);
    }

    private AnimalRecord find(String name) {
        UUID id = ids.get(name);
        return snapshot.records().stream().filter(pet -> pet.id().equals(id)).findFirst().orElse(null);
    }
}
