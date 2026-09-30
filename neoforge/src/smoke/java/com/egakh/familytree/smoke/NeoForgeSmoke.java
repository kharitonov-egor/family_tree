package com.egakh.familytree.smoke;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("familytree_smoke")
public final class NeoForgeSmoke {
    private int ticks;
    public NeoForgeSmoke() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                event.getServer().getCommands().performPrefixedCommand(event.getServer().createCommandSourceStack(),
                        "forceload add 0 0"));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (++ticks == 40) SmokeChecks.run(event.getServer());
        });
    }
}
