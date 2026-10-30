package com.egakh.familytree.client.fabric;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.keybind.FamilyTreeKeybinds;
import com.egakh.familytree.client.platform.ClientTransport;
import com.egakh.familytree.network.payloads.LinkParentsResult;
import com.egakh.familytree.network.payloads.SnapshotPagePayload;
import com.egakh.familytree.network.payloads.RenamePetResult;
import com.egakh.familytree.network.payloads.WaypointResult;
import com.egakh.familytree.client.integration.MapWaypoints;
import com.egakh.familytree.client.platform.ClientEnvironment;
import net.fabricmc.loader.api.FabricLoader;
//? if <1.21 {
/*import com.egakh.familytree.network.backport.PayloadCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.function.Consumer;
*///?}
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public final class FabricClientEntrypoint implements ClientModInitializer {
    @Override public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        boolean quilt = loader.isModLoaded("quilt_loader");
        ClientEnvironment.configure(new ClientEnvironment.Versions(version(loader, "familytree"), version(loader, "minecraft"),
                quilt ? "Quilt Loader" : "Fabric Loader", version(loader, quilt ? "quilt_loader" : "fabricloader")), loader::isModLoaded);
        MapWaypoints.initialize();
        FamilyTreeClient client = new FamilyTreeClient();
        client.initialize();
        KeyMappingHelper.registerKeyMapping(FamilyTreeKeybinds.OPEN_TREE);
        //? if >=1.21 {
        ClientTransport.configure(ClientPlayNetworking::canSend, ClientPlayNetworking::send);
        ClientPlayNetworking.registerGlobalReceiver(SnapshotPagePayload.TYPE, (packet, context) ->
                context.client().execute(() -> FamilyTreeClient.receive(packet)));
        ClientPlayNetworking.registerGlobalReceiver(RenamePetResult.TYPE, (packet, context) ->
                context.client().execute(() -> FamilyTreeClient.receive(packet)));
        ClientPlayNetworking.registerGlobalReceiver(LinkParentsResult.TYPE, (packet, context) ->
                context.client().execute(() -> FamilyTreeClient.receive(packet)));
        ClientPlayNetworking.registerGlobalReceiver(WaypointResult.TYPE, (packet, context) ->
                context.client().execute(() -> FamilyTreeClient.receive(packet)));
        //?} else {
        /*ClientTransport.configure(type -> ClientPlayNetworking.canSend(type.id()),
                packet -> ClientPlayNetworking.send(packet.type().id(), PayloadCodecs.encode(packet)));
        receive(SnapshotPagePayload.TYPE, SnapshotPagePayload.STREAM_CODEC, FamilyTreeClient::receive);
        receive(RenamePetResult.TYPE, RenamePetResult.STREAM_CODEC, FamilyTreeClient::receive);
        receive(LinkParentsResult.TYPE, LinkParentsResult.STREAM_CODEC, FamilyTreeClient::receive);
        receive(WaypointResult.TYPE, WaypointResult.STREAM_CODEC, FamilyTreeClient::receive);
        *///?}
        ClientTickEvents.END_CLIENT_TICK.register(client::tick);
    }

    private static String version(FabricLoader loader, String id) {
        return loader.getModContainer(id).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }

    //? if <1.21 {
    /*private static <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type.id(), (client, listener, buffer, sender) -> {
            T packet = codec.decode(new RegistryFriendlyByteBuf(buffer));
            client.execute(() -> handler.accept(packet));
        });
    }
    *///?}
}
