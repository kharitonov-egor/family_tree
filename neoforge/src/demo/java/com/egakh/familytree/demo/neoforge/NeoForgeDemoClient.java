package com.egakh.familytree.demo.neoforge;

import com.egakh.familytree.demo.DemoClient;
import com.egakh.familytree.demo.IntegrationCapture;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "familytree_demo", dist = Dist.CLIENT)
public final class NeoForgeDemoClient {
    public NeoForgeDemoClient() {
        DemoClient demo = new DemoClient();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> demo.tick(Minecraft.getInstance()));
        IntegrationCapture.modList = NeoForgeDemoClient::modList;
        IntegrationCapture.configScreen = parent -> ModList.get().getModContainerById("familytree")
                .flatMap(mod -> IConfigScreenFactory.getForMod(mod.getModInfo()).map(factory -> factory.createScreen(mod, parent)))
                .orElse(null);
    }

    // NeoForge 26 moved the mod list into a modlist package and builds it through a factory.
    private static Screen modList(Screen parent) {
        try {
            try {
                return (Screen) Class.forName("net.neoforged.neoforge.client.gui.ModListScreen").getConstructor(Screen.class).newInstance(parent);
            } catch (ClassNotFoundException newerVersion) {
                return (Screen) Class.forName("net.neoforged.neoforge.client.gui.modlist.ModListScreen")
                        .getMethod("create", Screen.class).invoke(null, parent);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
