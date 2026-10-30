package com.egakh.familytree.demo.forge;

import com.egakh.familytree.demo.DemoClient;
import com.egakh.familytree.demo.IntegrationCapture;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("familytree_demo")
public final class ForgeDemoClient {
    public ForgeDemoClient() {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        DemoClient demo = new DemoClient();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) demo.tick(Minecraft.getInstance());
        });
        IntegrationCapture.modList = ModListScreen::new;
        IntegrationCapture.configScreen = parent -> ModList.get().getModContainerById("familytree")
                .flatMap(mod -> ConfigScreenHandler.getScreenFactoryFor(mod.getModInfo()))
                .map(factory -> factory.apply(Minecraft.getInstance(), parent)).orElse(null);
    }
}
