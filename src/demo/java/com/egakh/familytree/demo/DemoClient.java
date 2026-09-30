package com.egakh.familytree.demo;

import com.egakh.familytree.client.screen.FamilyTreeBrowserScreen;
import com.egakh.familytree.client.screen.FamilyTreeViewScreen;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.data.FamilyTreeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DemoClient {
    private final GameplayCapture gameplay = new GameplayCapture();
    private int tick;
    private int totalTicks;
    private final List<AnimalRecord> pets = new ArrayList<>();
    private FamilyTreeBrowserScreen browser;
    private AnimalRecord willow, cedar;
    private Screen currentScreen;
    private FamilyTreeSnapshotPayload beforeDiscovery;
    private long exportsBefore;

    public void tick(Minecraft client) {
        try {
            if (++totalTicks > 2400) throw new AssertionError("Demo world did not open in two minutes");
            if (totalTicks == 600 && client.level == null) capture(client, "startup-blocked.png");
            if (client.level == null || client.player == null) return;
            if (Boolean.getBoolean("familytree.demo.gameplay")) { gameplay.tick(client, this); return; }
            tick++;
            if (tick == 1) {
                exportsBefore = exportCount(client);
                AnimalRecord probe = pet("Network check", null, null, "minecraft:pale", 1);
                client.getSingleplayerServer().execute(() -> FamilyTreeState.get(client.getSingleplayerServer()).put(probe));
                pets.clear();
            }
            if (tick == 10) {
                browser = new FamilyTreeBrowserScreen();
                show(client, browser);
                FamilyTreeClient.requestSnapshot(false);
            }
            if (tick == 25) {
                beforeDiscovery = receivedSnapshot();
                if (beforeDiscovery == null || beforeDiscovery.records().stream().noneMatch(pet -> pet.name().equals("Network check")))
                    throw new AssertionError("Server snapshot did not reach the browser");
                press("Find existing pets");
            }
            if (tick == 38 && receivedSnapshot() == beforeDiscovery)
                throw new AssertionError("Discovery did not return a new snapshot");
            if (tick == 40) {
                var moss = pet("Moss", null, null, "minecraft:pale", 2);
                var fern = pet("Fern", null, null, "minecraft:woods", 4);
                moss.markDeceased(82, 5000, new AnimalRecord.DeathCause("Creeper", "explosion"));
                pet("Juniper", moss, fern, "minecraft:woods", 18);
                willow = pet("Willow", moss, fern, "minecraft:chestnut", 44);
                cedar = pet("Cedar", null, null, "minecraft:snowy", 41);
                browser = new FamilyTreeBrowserScreen();
                show(client, browser);
                FamilyTreeBrowserScreen.deliverSnapshot(snapshot());
            }
            if (tick == 70) capture(client, "browser.png");
            if (tick == 90) openTree(client);
            if (tick == 120) capture(client, "tree-before.png");
            if (tick == 180) {
                pet("Clover", willow, cedar, "minecraft:chestnut", 108);
                pet("Birch", willow, cedar, "minecraft:snowy", 108);
                openTree(client);
            }
            if (tick >= 130 && tick <= 280 && tick % 4 == 2) capture(client, String.format("demo-%04d.png", tick));
            if (tick == 220) {
                capture(client, "tree.png");
                press("Export family tree");
            }
            if (tick == 320) {
                show(client, new com.egakh.familytree.client.screen.FamilyTreeHelpScreen(browser));
                capture(client, "getting-started.png");
            }
            if (tick == 340) {
                client.getSingleplayerServer().execute(() -> pets.forEach(pet -> FamilyTreeState.get(client.getSingleplayerServer()).put(pet)));
                var focused = new FamilyTreeViewScreen(browser, snapshot(), willow.id());
                show(client, focused);
                press("Add parents");
                currentScreen = activeScreen(client);
            }
            if (tick == 360) {
                capture(client, "parent-links.png");
                press("Parent 1: Moss");
                currentScreen = activeScreen(client);
            }
            if (tick == 380) {
                capture(client, "pet-picker.png");
                var choice = currentScreen.children().stream().filter(widget -> widget instanceof Button button && button.getMessage().getString().contains("Cedar")).findFirst().orElseThrow();
                press(((Button) choice).getMessage().getString());
                currentScreen = activeScreen(client);
            }
            if (tick == 400) press("Confirm parents");
            if (tick == 420) {
                if (activeScreen(client) instanceof com.egakh.familytree.client.screen.ParentLinksScreen)
                    throw new AssertionError("Parent edit did not return to the tree");
                var check = client.getSingleplayerServer().submit(() -> FamilyTreeState.get(client.getSingleplayerServer()).get(willow.id()).parentA()).get();
                if (!cedar.id().equals(check)) throw new AssertionError("Server did not apply parent edit");
                capture(client, "parent-links-success.png");
            }
            if (tick == 450) {
                capture(client, "export-success.png");
                if (exportCount(client) <= exportsBefore) throw new AssertionError("Export did not write a PNG");
                java.nio.file.Files.writeString(client.gameDirectory.toPath().resolve("demo-result.txt"),
                        "PASS: server snapshot; discovery round trip; browser; tree; portraits; PNG export; help; parent picker; server parent edit\n");
                client.stop();
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { java.nio.file.Files.writeString(client.gameDirectory.toPath().resolve("demo-result.txt"), "FAIL: " + failure); }
            catch (java.io.IOException ignored) {}
            client.stop();
        }
    }

    private void openTree(Minecraft client) throws Exception {
        show(client, FamilyTreeViewScreen.forSpecies(browser, snapshot(), "minecraft:wolf", Component.literal("Moss's family")));
    }

    private FamilyTreeSnapshotPayload snapshot() { return new FamilyTreeSnapshotPayload(List.copyOf(pets), 108, 6000, true, true, true); }

    private AnimalRecord pet(String name, AnimalRecord a, AnimalRecord b, String variant, long day) {
        var record = new AnimalRecord(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "minecraft:wolf", name, false, a == null ? null : a.id(), b == null ? null : b.id(),
                day, 1000, false, null, null, Minecraft.getInstance().player.getUUID(), "Alex", variant, null, null);
        pets.add(record);
        return record;
    }

    private FamilyTreeSnapshotPayload receivedSnapshot() throws Exception {
        var field = FamilyTreeBrowserScreen.class.getDeclaredField("snapshot");
        field.setAccessible(true);
        return (FamilyTreeSnapshotPayload) field.get(browser);
    }

    // Reflection keeps this development-only runner usable across the supported game APIs.
    private Screen activeScreen(Minecraft client) throws Exception {
        try { return (Screen) Minecraft.class.getField("screen").get(client); }
        catch (NoSuchFieldException newerVersion) {
            return (Screen) client.gui.getClass().getMethod("screen").invoke(client.gui);
        }
    }

    void show(Minecraft client, Screen screen) throws Exception {
        currentScreen = screen;
        try { Minecraft.class.getMethod("setScreen", Screen.class).invoke(client, screen); }
        catch (NoSuchMethodException newerVersion) { Minecraft.class.getMethod("setScreenAndShow", Screen.class).invoke(client, screen); }
    }

    void press(String label) throws Exception {
        for (var widget : currentScreen.children()) {
            if (!(widget instanceof Button button) || !button.getMessage().getString().equals(label)) continue;
            if (!button.active) throw new AssertionError(label + " is disabled");
            try { Button.class.getMethod("onPress").invoke(button); }
            catch (NoSuchMethodException newerVersion) {
                var infoType = Class.forName("net.minecraft.client.input.MouseButtonInfo");
                var eventType = Class.forName("net.minecraft.client.input.MouseButtonEvent");
                Object info = infoType.getConstructor(int.class, int.class).newInstance(0, 0);
                Object event = eventType.getConstructor(double.class, double.class, infoType)
                        .newInstance((double) button.getX() + 2, (double) button.getY() + 2, info);
                Button.class.getMethod("onPress", Class.forName("net.minecraft.client.input.InputWithModifiers")).invoke(button, event);
            }
            return;
        }
        throw new AssertionError("Missing button: " + label);
    }

    long exportCount(Minecraft client) throws java.io.IOException {
        var directory = client.gameDirectory.toPath().resolve("screenshots/familytree");
        if (!java.nio.file.Files.exists(directory)) return 0;
        try (var files = java.nio.file.Files.list(directory)) { return files.filter(file -> file.toString().endsWith(".png")).count(); }
    }

    void capture(Minecraft client, String name) throws Exception {
        Object renderTarget;
        try { renderTarget = Minecraft.class.getMethod("getMainRenderTarget").invoke(client); }
        catch (NoSuchMethodException newerVersion) {
            Object renderer = Minecraft.class.getField("gameRenderer").get(client);
            renderTarget = renderer.getClass().getMethod("mainRenderTarget").invoke(renderer);
        }
        for (var method : Screenshot.class.getMethods()) {
            if (!method.getName().equals("grab") || method.getParameterCount() < 4 || method.getParameterTypes()[1] != String.class) continue;
            java.util.function.Consumer<Component> callback = message -> {};
            if (method.getParameterCount() == 5)
                method.invoke(null, client.gameDirectory, name, renderTarget, 1, callback);
            else method.invoke(null, client.gameDirectory, name, renderTarget, callback);
            return;
        }
        throw new IllegalStateException("Screenshot API missing");
    }
}
