package com.egakh.familytree.demo.forge;

import com.egakh.familytree.demo.DemoClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
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
    }
}
