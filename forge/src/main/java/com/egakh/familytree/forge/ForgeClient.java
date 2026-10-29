package com.egakh.familytree.forge;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.keybind.FamilyTreeKeybinds;
import com.egakh.familytree.client.platform.ClientTransport;
import com.egakh.familytree.network.backport.PayloadCodecs;
import com.egakh.familytree.network.payloads.LinkParentsResult;
import com.egakh.familytree.network.payloads.RenamePetResult;
import com.egakh.familytree.network.payloads.SnapshotPagePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

final class ForgeClient {
    private ForgeClient() {}

    static void initialize(IEventBus modBus) {
        ClientTransport.configure(type -> {
            var connection = Minecraft.getInstance().getConnection();
            return connection != null && ForgeNetwork.present(type, connection.getConnection());
        }, packet -> {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null)
                connection.send(new ServerboundCustomPayloadPacket(packet.type().id(), PayloadCodecs.encode(packet)));
        });
        ForgeNetwork.clientbound(SnapshotPagePayload.TYPE, SnapshotPagePayload.STREAM_CODEC, FamilyTreeClient::receive);
        ForgeNetwork.clientbound(LinkParentsResult.TYPE, LinkParentsResult.STREAM_CODEC, FamilyTreeClient::receive);
        ForgeNetwork.clientbound(RenamePetResult.TYPE, RenamePetResult.STREAM_CODEC, FamilyTreeClient::receive);
        FamilyTreeClient client = new FamilyTreeClient();
        client.initialize();
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(FamilyTreeKeybinds.OPEN_TREE));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) client.tick(Minecraft.getInstance());
        });
    }
}
