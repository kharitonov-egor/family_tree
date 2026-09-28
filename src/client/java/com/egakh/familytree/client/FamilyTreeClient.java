package com.egakh.familytree.client;

import com.egakh.familytree.client.keybind.FamilyTreeKeybinds;
import com.egakh.familytree.client.screen.FamilyTreeBrowserScreen;
import com.egakh.familytree.client.screen.FamilyTreeViewScreen;
import com.egakh.familytree.client.screen.RenamePetScreen;
import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.network.payloads.OpenFamilyTreeRequest;
import com.egakh.familytree.network.payloads.RenamePetResult;
import com.egakh.familytree.naming.PetRenaming;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class FamilyTreeClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FamilyTreeClientSettings.load();
        FamilyTreeKeybinds.register();

        ClientPlayNetworking.registerGlobalReceiver(FamilyTreeSnapshotPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> FamilyTreeBrowserScreen.deliverSnapshot(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(RenamePetResult.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> {
                if (payload.status() == PetRenaming.Result.SUCCESS.ordinal()) {
                    payload.record().ifPresent(updated -> {
                        FamilyTreeBrowserScreen.deliverRename(updated);
                        if (client.screen instanceof FamilyTreeViewScreen tree) tree.applyRename(updated);
                    });
                }
                if (client.screen instanceof RenamePetScreen rename) rename.receiveResult(payload);
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (FamilyTreeKeybinds.OPEN_TREE.consumeClick()) {
                if (client.level == null || client.player == null) continue;
                if (!ClientPlayNetworking.canSend(OpenFamilyTreeRequest.TYPE)) {
                    client.gui.setOverlayMessage(Component.translatable("familytree.not_available"), false);
                    continue;
                }
                client.setScreen(new FamilyTreeBrowserScreen());
                requestSnapshot(true);
            }
        });
    }

    public static void requestSnapshot(boolean requestAll) {
        if (ClientPlayNetworking.canSend(OpenFamilyTreeRequest.TYPE)) {
            ClientPlayNetworking.send(new OpenFamilyTreeRequest(requestAll));
        }
    }
}
