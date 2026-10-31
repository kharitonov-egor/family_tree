package com.egakh.familytree.demo;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.keybind.FamilyTreeKeybinds;
import com.egakh.familytree.client.platform.ClientTransport;
import com.egakh.familytree.client.relatives.WorldRelatives;
import com.egakh.familytree.client.screen.FamilyTreeBrowserScreen;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.network.payloads.OpenFamilyTreeRequest;
import com.egakh.familytree.network.payloads.RelativesRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;

/**
 * Joins a dedicated server with {@code -PdemoServer} and holds the relatives key. {@code -PrelativesServer=no_server}
 * expects a server without Family Tree, and {@code outdated} expects a Family Tree release without relatives.
 */
public final class RelativesServerCapture {
    private int tick;
    private FamilyTreeBrowserScreen browser;
    private final StringBuilder passed = new StringBuilder();

    public void tick(Minecraft client, DemoClient runner) throws Exception {
        if (client.level == null || client.player == null) return;
        tick++;
        String expected = System.getProperty("familytree.demo.relativesServer");
        boolean outdated = expected.equals("outdated");
        if (!outdated && !expected.equals("no_server")) throw new AssertionError("Unknown expectation " + expected);
        if (tick == 80) FamilyTreeKeybinds.SHOW_RELATIVES.setDown(true);
        if (tick == 90) {
            String message = overlay(client);
            String wanted = Component.translatable("familytree.relatives." + expected).getString();
            if (!wanted.equals(message)) throw new AssertionError("The action bar says '" + message + "', expected '" + wanted + "'");
            if (ClientTransport.canSend(RelativesRequest.TYPE)) throw new AssertionError("The server should not accept relatives requests");
            if (ClientTransport.canSend(OpenFamilyTreeRequest.TYPE) != outdated)
                throw new AssertionError("The server's Family Tree channels are not what " + expected + " expects");
            runner.capture(client, "relatives-server-" + expected + ".png");
            passed.append("key shows '").append(message).append("'");
        }
        if (tick == 110) {
            FamilyTreeKeybinds.SHOW_RELATIVES.setDown(false);
            if (WorldRelatives.outlineColor(client.player) != 0) throw new AssertionError("Something glows without a server reply");
        }
        if (outdated && tick == 120) {
            browser = new FamilyTreeBrowserScreen();
            runner.show(client, browser);
            FamilyTreeClient.requestSnapshot(true);
        }
        if (outdated && tick == 180) {
            var field = FamilyTreeBrowserScreen.class.getDeclaredField("snapshot");
            field.setAccessible(true);
            if (field.get(browser) == null) throw new AssertionError("The older server did not answer the Family Tree screen");
            runner.capture(client, "relatives-server-outdated-browser.png");
            passed.append("; the browser still loads from the older server");
        }
        if (tick == 200) {
            if (client.getConnection() == null) throw new AssertionError("The client was disconnected");
            Files.writeString(client.gameDirectory.toPath().resolve("demo-result.txt"),
                    "PASS: relatives on a server " + (outdated ? "with an older Family Tree" : "without Family Tree") + "; "
                            + passed + "; still connected\n");
            client.stop();
        }
    }

    // Reflection keeps this development-only runner usable across the supported game APIs.
    private static String overlay(Minecraft client) throws ReflectiveOperationException {
        Object owner = client.gui;
        try {
            owner = client.gui.getClass().getField("hud").get(client.gui);
        } catch (NoSuchFieldException olderVersion) {
            // Before 26.2 the action bar lives in Gui itself.
        }
        var field = owner.getClass().getDeclaredField("overlayMessageString");
        field.setAccessible(true);
        Object message = field.get(owner);
        return message == null ? null : ((Component) message).getString();
    }
}
