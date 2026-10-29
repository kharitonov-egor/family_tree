package com.egakh.familytree.demo;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.integration.MapWaypoints;
import com.egakh.familytree.client.screen.FamilyTreeBrowserScreen;
import com.egakh.familytree.client.screen.FamilyTreeHelpScreen;
import com.egakh.familytree.client.screen.FamilyTreeSettingsScreen;
import com.egakh.familytree.client.screen.FamilyTreeViewScreen;
import com.egakh.familytree.client.screen.FeedbackScreen;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * A disposable scene for optional integrations. The listed mods must be on the development runtime classpath.
 * The tooltip screenshot comes before the browser opens, so any family lines in it came from the server provider.
 */
public final class IntegrationCapture {
    /** Each loader's demo entry point sets these to open its mod list and the config screen through the loader. */
    public static Function<Screen, Screen> modList = parent -> null;
    public static Function<Screen, Screen> configScreen = parent -> null;

    private final Set<String> mods = Set.of(System.getProperty("familytree.demo.integrations", "").split(","));
    private final boolean sendFeedback = Boolean.getBoolean("familytree.demo.sendFeedback");
    private final List<String> checks = new ArrayList<>();
    private int tick;
    private Animal comet, dusk, ember, woolly;
    private FamilyTreeBrowserScreen browser;
    private FeedbackScreen feedback;

    public void tick(Minecraft client, DemoClient runner) throws Exception {
        tick++;
        var server = client.getSingleplayerServer();
        if (tick == 1) server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(client.player.getUUID());
                for (String command : new String[]{"gamemode creative @a", "time set day", "weather clear", "gamerule doDaylightCycle false",
                        "kill @e[type=!minecraft:player]", "fill -10 100 -10 10 100 10 grass_block", "fill -10 101 -10 10 104 10 air"})
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                var world = server.overworld();
                FamilyTreeState.get(server).removeMatching(pet -> true);
                comet = HorseCapture.horse(world, "HORSE", "Comet", -3, 4, "CHESTNUT", "WHITE_DOTS", player);
                dusk = HorseCapture.horse(world, "HORSE", "Dusk", 3, 4, "BLACK", "WHITE_FIELD", player);
                ember = HorseCapture.horse(world, "HORSE", "Ember", 0, 0, "CREAMY", "WHITE", player);
                for (Animal horse : List.of(comet, dusk, ember)) ((Mob) horse).setNoAi(true);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "summon minecraft:sheep 8 101 0");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp @a 0 101 -2.2 0 28");
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
        if (tick == 20) server.execute(() -> {
            var state = FamilyTreeState.get(server);
            if (state.get(ember.getUUID()) == null) throw new AssertionError("The scene's horses were not recorded");
            var sheep = server.overworld().getEntitiesOfClass(Animal.class, new net.minecraft.world.phys.AABB(7, 100, -1, 9, 103, 1));
            if (sheep.size() != 1) throw new AssertionError("The scene's sheep is missing");
            woolly = sheep.get(0);
            ((Mob) woolly).setNoAi(true);
            try {
                woolly.getClass().getMethod("setColor", net.minecraft.world.item.DyeColor.class).invoke(woolly, net.minecraft.world.item.DyeColor.BLUE);
            } catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
            state.put(new com.egakh.familytree.data.AnimalRecord(woolly.getUUID(), "minecraft:sheep", "Woolly", false, null, null,
                    1, System.currentTimeMillis(), false, null, null, client.player.getUUID(), "Tester", "blue", null, null));
            state.update(ember.getUUID(), pet -> {
                pet.setParents(comet.getUUID(), dusk.getUUID());
                pet.setTreeName("Little Ember");
            });
        });
        if (tick == 100) {
            runner.capture(client, "integration-world.png");
            checks.add("world scene");
        }
        if (tick == 110) {
            browser = new FamilyTreeBrowserScreen();
            runner.show(client, browser);
            FamilyTreeClient.requestSnapshot(false);
        }
        if (tick == 130) {
            boolean maps = mods.contains("journeymap") || mods.contains("xaero");
            if (MapWaypoints.available() != maps) throw new AssertionError("Waypoint button availability should be " + maps);
            runner.show(client, new FamilyTreeViewScreen(browser, snapshot(), ember.getUUID()));
        }
        if (tick == 150) {
            runner.capture(client, "integration-tree.png");
            if (MapWaypoints.available()) {
                runner.press("Waypoint");
                checks.add("waypoint button");
            }
        }
        if (tick == 175 && MapWaypoints.available()) {
            runner.capture(client, "integration-waypoint.png");
            if (mods.contains("journeymap")) {
                if (journeyMapWaypoints() == 0) throw new AssertionError("JourneyMap has no Family Tree waypoint");
                checks.add("JourneyMap waypoint");
            }
        }
        if (tick == 180 && mods.contains("xaero")) {
            String add = xaeroAddLink(client);
            if (add == null) throw new AssertionError("Xaero's Minimap did not offer to add the shared waypoint. Chat: " + CHAT_SEEN);
            client.player.connection.sendChat(add);
            checks.add("Xaero add link");
        }
        if (tick == 186 && mods.contains("xaero")) runner.capture(client, "integration-xaero-added.png");
        if (tick == 190) {
            runner.show(client, new FamilyTreeHelpScreen(browser));
            runner.capture(client, "integration-help.png");
            runner.press("Send feedback");
        }
        if (tick == 200) {
            feedback = (FeedbackScreen) runner.current(client);
            for (var widget : feedback.children()) {
                if (widget instanceof MultiLineEditBox box) box.setValue("Automated Family Tree test from the development client. "
                        + "Tooltips, waypoints, and config screens were checked with: " + String.join(", ", mods) + ".");
                else if (widget instanceof EditBox box) box.setValue("ega.khar@gmail.com");
            }
        }
        if (tick == 210) {
            runner.capture(client, "integration-feedback.png");
            if (sendFeedback) runner.press("Send");
        }
        if (tick == 400) {
            runner.capture(client, "integration-feedback-result.png");
            if (sendFeedback) checks.add("feedback reply: " + feedbackStatus());
        }
        if (tick == 410) {
            Screen list = modList.apply(browser);
            if (list != null) {
                runner.show(client, list);
                checks.add("mod list");
            }
        }
        if (tick == 440) {
            if (runner.current(client) != browser) runner.capture(client, "integration-mod-list.png");
            Screen config = configScreen.apply(browser);
            if (config != null) {
                if (!(config instanceof FamilyTreeSettingsScreen)) throw new AssertionError("The config button opened " + config);
                runner.show(client, config);
                checks.add("config screen");
            }
        }
        if (tick == 460) runner.capture(client, "integration-config.png");
        if (tick == 465) {
            runner.show(client, null);
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp @a 8 101 -2.4 0 30"));
        }
        if (tick == 530) {
            if (mods.contains("jade") || mods.contains("wthit")) {
                runner.capture(client, "integration-variant.png");
                checks.add("variant tooltip");
            }
            Files.writeString(client.gameDirectory.toPath().resolve("demo-result.txt"),
                    "PASS: integrations [" + String.join(", ", mods) + "]; " + String.join("; ", checks) + "\n");
            client.stop();
        }
    }

    private FamilyTreeSnapshotPayload snapshot() throws Exception {
        var field = FamilyTreeBrowserScreen.class.getDeclaredField("snapshot");
        field.setAccessible(true);
        var snapshot = (FamilyTreeSnapshotPayload) field.get(browser);
        if (snapshot == null) throw new AssertionError("The browser did not receive a snapshot");
        return snapshot;
    }

    private String feedbackStatus() throws Exception {
        var field = FeedbackScreen.class.getDeclaredField("status");
        field.setAccessible(true);
        return ((net.minecraft.network.chat.Component) field.get(feedback)).getString();
    }

    /** Finds the command behind Xaero's Add link in the chat history, which a click would send through sendChat. */
    private static String xaeroAddLink(Minecraft client) throws Exception {
        Object chat = null;
        for (Object holder : new Object[]{client.gui, field(client.gui, "hud")}) {
            if (holder == null) continue;
            try { chat = holder.getClass().getMethod("getChat").invoke(holder); break; }
            catch (NoSuchMethodException other) { continue; }
        }
        if (chat == null) throw new IllegalStateException("Chat history is not reachable");
        for (var listField : chat.getClass().getDeclaredFields()) {
            if (!List.class.isAssignableFrom(listField.getType())) continue;
            listField.setAccessible(true);
            for (Object message : (List<?>) listField.get(chat)) {
                for (var method : message.getClass().getMethods()) {
                    if (method.getParameterCount() != 0 || !net.minecraft.network.chat.Component.class.isAssignableFrom(method.getReturnType())) continue;
                    String found = addLink((net.minecraft.network.chat.Component) method.invoke(message));
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private static final StringBuilder CHAT_SEEN = new StringBuilder();

    private static String addLink(net.minecraft.network.chat.Component component) throws Exception {
        Object click = component.getStyle().getClickEvent();
        if (CHAT_SEEN.length() < 4000) CHAT_SEEN.append(" | ").append(component.getContents().getClass().getSimpleName())
                .append(':').append(component.getString().replace('\n', ' ')).append(click == null ? "" : " click=" + click);
        if (click != null) for (String name : new String[]{"getValue", "command", "value"}) {
            try {
                Object value = click.getClass().getMethod(name).invoke(click);
                if (value instanceof String text && text.contains("xaero_waypoint_add:"))
                    return text.startsWith("/") ? text.substring(1) : text;
            } catch (NoSuchMethodException other) { continue; }
        }
        List<net.minecraft.network.chat.Component> children = new ArrayList<>(component.getSiblings());
        Object contents = component.getContents();
        try {
            for (Object argument : (Object[]) contents.getClass().getMethod("getArgs").invoke(contents))
                if (argument instanceof net.minecraft.network.chat.Component child) children.add(child);
        } catch (NoSuchMethodException notTranslatable) {
            // Literal text has no arguments.
        }
        for (var child : children) {
            String found = addLink(child);
            if (found != null) return found;
        }
        return null;
    }

    private static Object field(Object owner, String name) {
        try {
            var field = owner.getClass().getField(name);
            return field.get(owner);
        } catch (ReflectiveOperationException missing) {
            return null;
        }
    }

    private static int journeyMapWaypoints() throws Exception {
        var targets = MapWaypoints.class.getDeclaredField("TARGETS");
        targets.setAccessible(true);
        for (Object target : (List<?>) targets.get(null)) {
            if (!target.getClass().getSimpleName().equals("JourneyMapWaypoints")) continue;
            var apiField = target.getClass().getDeclaredField("api");
            apiField.setAccessible(true);
            Object api = apiField.get(target);
            var method = Class.forName("journeymap.api.v2.client.IClientAPI").getMethod("getWaypoints", String.class);
            return ((List<?>) method.invoke(api, "familytree")).size();
        }
        return 0;
    }
}
