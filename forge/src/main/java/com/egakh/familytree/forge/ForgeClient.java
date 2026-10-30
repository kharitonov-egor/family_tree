package com.egakh.familytree.forge;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.keybind.FamilyTreeKeybinds;
import com.egakh.familytree.client.platform.ClientTransport;
import com.egakh.familytree.network.backport.PayloadCodecs;
import com.egakh.familytree.network.payloads.LinkParentsResult;
import com.egakh.familytree.network.payloads.RenamePetResult;
import com.egakh.familytree.network.payloads.SnapshotPagePayload;
import com.egakh.familytree.network.payloads.WaypointResult;
import com.egakh.familytree.client.integration.MapWaypoints;
import com.egakh.familytree.client.platform.ClientEnvironment;
import com.egakh.familytree.client.screen.FamilyTreeSettingsScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
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
        ForgeNetwork.clientbound(WaypointResult.TYPE, WaypointResult.STREAM_CODEC, FamilyTreeClient::receive);
        ModList mods = ModList.get();
        ClientEnvironment.configure(new ClientEnvironment.Versions(version(mods, "familytree"), version(mods, "minecraft"),
                "Forge", version(mods, "forge")), mods::isLoaded);
        MapWaypoints.initialize();
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new FamilyTreeSettingsScreen(parent)));
        FamilyTreeClient client = new FamilyTreeClient();
        client.initialize();
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(FamilyTreeKeybinds.OPEN_TREE));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) client.tick(Minecraft.getInstance());
        });
    }

    private static String version(ModList mods, String id) {
        return mods.getModContainerById(id).map(mod -> mod.getModInfo().getVersion().toString()).orElse("unknown");
    }
}
