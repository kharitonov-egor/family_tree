package com.egakh.familytree.smoke;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class FabricSmoke implements ModInitializer {
    private int ticks;
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(), "forceload add 0 0"));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks == 40) SmokeChecks.run(server);
        });
    }
}
