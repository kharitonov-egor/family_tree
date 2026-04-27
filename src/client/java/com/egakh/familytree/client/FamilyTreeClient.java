package com.egakh.familytree.client;

import com.egakh.familytree.client.keybind.FamilyTreeKeybinds;
import com.egakh.familytree.client.screen.FamilyTreeBrowserScreen;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.network.payloads.OpenFamilyTreeRequest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class FamilyTreeClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FamilyTreeKeybinds.register();

        ClientPlayNetworking.registerGlobalReceiver(FamilyTreeSnapshotPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> FamilyTreeBrowserScreen.deliverSnapshot(payload));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (FamilyTreeKeybinds.OPEN_TREE.consumeClick()) {
                if (client.level == null || client.player == null) continue;
                client.setScreen(new FamilyTreeBrowserScreen());
                ClientPlayNetworking.send(new OpenFamilyTreeRequest());
            }
        });
    }
}
