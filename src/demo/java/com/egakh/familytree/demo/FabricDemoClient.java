package com.egakh.familytree.demo;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FabricDemoClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        DemoClient demo = new DemoClient();
        ClientTickEvents.END_CLIENT_TICK.register(demo::tick);
    }
}
