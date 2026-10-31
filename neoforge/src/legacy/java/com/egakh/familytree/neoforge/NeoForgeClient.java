package com.egakh.familytree.neoforge;

import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.keybind.FamilyTreeKeybinds;
import com.egakh.familytree.client.platform.ClientTransport;
import com.egakh.familytree.network.payloads.SnapshotPagePayload;
import com.egakh.familytree.network.payloads.RenamePetResult;
import com.egakh.familytree.client.integration.MapWaypoints;
import com.egakh.familytree.client.platform.ClientEnvironment;
import com.egakh.familytree.client.screen.FamilyTreeSettingsScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;


import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = FamilyTreeMod.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
    public NeoForgeClient(IEventBus modBus, ModContainer container) {
        ClientTransport.configure(type -> Minecraft.getInstance().getConnection() != null
                        && net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(Minecraft.getInstance().getConnection(), type.id()), net.neoforged.neoforge.network.PacketDistributor::sendToServer);
        ModList mods = ModList.get();
        ClientEnvironment.configure(new ClientEnvironment.Versions(version(mods, "familytree"), version(mods, "minecraft"),
                "NeoForge", version(mods, "neoforge")), mods::isLoaded);
        MapWaypoints.initialize();
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new FamilyTreeSettingsScreen(parent));
        FamilyTreeClient client = new FamilyTreeClient();
        client.initialize();
        FamilyTreeKeybinds.SHOW_RELATIVES.setKeyConflictContext(KeyConflictContext.IN_GAME);
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(FamilyTreeKeybinds.OPEN_TREE);
            event.register(FamilyTreeKeybinds.SHOW_RELATIVES);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> client.tick(Minecraft.getInstance()));
    }

    private static String version(ModList mods, String id) {
        return mods.getModContainerById(id).map(mod -> mod.getModInfo().getVersion().toString()).orElse("unknown");
    }
}
