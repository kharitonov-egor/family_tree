package com.egakh.familytree.client.fabric;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.keybind.FamilyTreeKeybinds;
import com.egakh.familytree.client.platform.ClientTransport;
import com.egakh.familytree.network.payloads.SnapshotPagePayload;
import com.egakh.familytree.network.payloads.RenamePetResult;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public final class FabricClientEntrypoint implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientTransport.configure(ClientPlayNetworking::canSend, ClientPlayNetworking::send);
        FamilyTreeClient client = new FamilyTreeClient();
        client.initialize();
        KeyMappingHelper.registerKeyMapping(FamilyTreeKeybinds.OPEN_TREE);
        ClientPlayNetworking.registerGlobalReceiver(SnapshotPagePayload.TYPE, (packet, context) ->
                context.client().execute(() -> FamilyTreeClient.receive(packet)));
        ClientPlayNetworking.registerGlobalReceiver(RenamePetResult.TYPE, (packet, context) ->
                context.client().execute(() -> FamilyTreeClient.receive(packet)));
        ClientPlayNetworking.registerGlobalReceiver(com.egakh.familytree.network.payloads.LinkParentsResult.TYPE, (packet, context) ->
                context.client().execute(() -> FamilyTreeClient.receive(packet)));
        ClientTickEvents.END_CLIENT_TICK.register(client::tick);
    }
}
