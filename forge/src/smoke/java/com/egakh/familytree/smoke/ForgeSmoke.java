package com.egakh.familytree.smoke;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("familytree_smoke")
public final class ForgeSmoke {
    private int ticks;
    public ForgeSmoke() {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                event.getServer().getCommands().performPrefixedCommand(event.getServer().createCommandSourceStack(),
                        "forceload add 0 0"));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END && ++ticks == 40) SmokeChecks.run(event.getServer());
        });
    }
}
