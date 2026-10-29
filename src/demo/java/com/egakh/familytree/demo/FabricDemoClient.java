package com.egakh.familytree.demo;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

public final class FabricDemoClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        DemoClient demo = new DemoClient();
        ClientTickEvents.END_CLIENT_TICK.register(demo::tick);
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) return;
        // Mod Menu is optional at runtime, so the demo reaches it by name.
        IntegrationCapture.modList = parent -> {
            try { return (Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen").getConstructor(Screen.class).newInstance(parent); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        };
        IntegrationCapture.configScreen = parent -> {
            try {
                return (Screen) Class.forName("com.terraformersmc.modmenu.ModMenu").getMethod("getConfigScreen", String.class, Screen.class)
                        .invoke(null, "familytree", parent);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        };
    }
}
