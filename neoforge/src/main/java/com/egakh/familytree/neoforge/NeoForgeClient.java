package com.egakh.familytree.neoforge;

import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.keybind.FamilyTreeKeybinds;
import com.egakh.familytree.client.platform.ClientTransport;
import com.egakh.familytree.network.payloads.SnapshotPagePayload;
import com.egakh.familytree.network.payloads.RenamePetResult;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = FamilyTreeMod.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
    public NeoForgeClient(IEventBus modBus) {
        ClientTransport.configure(type -> Minecraft.getInstance().getConnection() != null
                        && Minecraft.getInstance().getConnection().hasChannel(type), ClientPacketDistributor::sendToServer);
        FamilyTreeClient client = new FamilyTreeClient();
        client.initialize();
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(FamilyTreeKeybinds.OPEN_TREE));
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> {
            event.register(SnapshotPagePayload.TYPE, (packet, context) -> FamilyTreeClient.receive(packet));
            event.register(com.egakh.familytree.network.payloads.LinkParentsResult.TYPE, (packet, context) -> FamilyTreeClient.receive(packet));
            event.register(RenamePetResult.TYPE, (packet, context) -> FamilyTreeClient.receive(packet));
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> client.tick(Minecraft.getInstance()));
    }
}
