package com.egakh.familytree.demo.neoforge;

import com.egakh.familytree.demo.DemoClient;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "familytree_demo", dist = Dist.CLIENT)
public final class NeoForgeDemoClient {
    public NeoForgeDemoClient() {
        DemoClient demo = new DemoClient();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> demo.tick(Minecraft.getInstance()));
    }
}
