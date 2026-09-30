package com.egakh.familytree.client;

import com.egakh.familytree.client.keybind.FamilyTreeKeybinds;
import com.egakh.familytree.client.platform.ClientTransport;
import com.egakh.familytree.client.screen.FamilyTreeBrowserScreen;
import com.egakh.familytree.client.screen.FamilyTreeViewScreen;
import com.egakh.familytree.client.screen.RenamePetScreen;
import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.network.payloads.OpenFamilyTreeRequest;
import com.egakh.familytree.network.payloads.RenamePetResult;
import com.egakh.familytree.naming.PetRenaming;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.egakh.familytree.network.payloads.SnapshotPagePayload;
import com.egakh.familytree.network.payloads.DiscoverPetsRequest;
import java.util.UUID;

public final class FamilyTreeClient {
    private int welcomeTicks;
    private static final SnapshotAssembler ASSEMBLER = new SnapshotAssembler();
    private static int requestTicks;
    private static boolean pending;
    public void initialize() { FamilyTreeClientSettings.load(); }

    public static void receive(FamilyTreeSnapshotPayload packet) {
        FamilyTreeBrowserScreen.deliverSnapshot(packet);
    }

    public static void receive(SnapshotPagePayload packet) {
        if (!ASSEMBLER.accepts(packet.requestId())) return;
        try {
            if (packet.pageCount() == 0) throw new IllegalArgumentException("Server refused snapshot");
            ASSEMBLER.accept(packet).ifPresent(snapshot -> {
                pending = false;
                receive(snapshot);
                var client = Minecraft.getInstance();
                if (client.screen instanceof FamilyTreeViewScreen tree) tree.applySnapshot(snapshot);
            });
        } catch (IllegalArgumentException failure) {
            pending = false;
            ASSEMBLER.clear();
            FamilyTreeBrowserScreen.loadingFailed();
        }
    }

    public static void receive(com.egakh.familytree.network.payloads.LinkParentsResult packet) {
        var client = Minecraft.getInstance();
        if (client.screen instanceof com.egakh.familytree.client.screen.ParentLinksScreen screen) screen.receive(packet);
    }

    public static void receive(RenamePetResult packet) {
        Minecraft client = Minecraft.getInstance();
        if (packet.status() == PetRenaming.Result.SUCCESS.ordinal()) {
            packet.record().ifPresent(updated -> {
                FamilyTreeBrowserScreen.deliverRename(updated);
                if (client.screen instanceof FamilyTreeViewScreen tree) tree.applyRename(updated);
            });
        }
        if (client.screen instanceof RenamePetScreen rename) rename.receiveResult(packet);
    }

    public void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            welcomeTicks = 0;
            com.egakh.familytree.client.screen.PetFaceRenderer.clearCache();
            pending = false;
            ASSEMBLER.clear();
        } else if (pending && ++requestTicks >= 200) {
            pending = false;
            ASSEMBLER.clear();
            FamilyTreeBrowserScreen.loadingFailed();
        } else if (!FamilyTreeClientSettings.welcomeShown()
                && ClientTransport.canSend(OpenFamilyTreeRequest.TYPE) && ++welcomeTicks >= 60) {
            client.player.sendSystemMessage(Component.translatable("familytree.welcome",
                    FamilyTreeKeybinds.OPEN_TREE.getTranslatedKeyMessage()));
            FamilyTreeClientSettings.markWelcomeShown();
        }
        while (FamilyTreeKeybinds.OPEN_TREE.consumeClick()) {
            if (client.level == null || client.player == null) continue;
            if (!ClientTransport.canSend(OpenFamilyTreeRequest.TYPE)) {
                client.gui.setOverlayMessage(Component.translatable("familytree.not_available"), false);
                continue;
            }
            client.setScreen(new FamilyTreeBrowserScreen());
            requestSnapshot(true);
        }
    }

    public static void requestSnapshot(boolean requestAll) {
        UUID request = beginRequest();
        ClientTransport.send(new OpenFamilyTreeRequest(request, requestAll));
    }

    public static void discover(boolean requestAll) {
        UUID request = beginRequest();
        ClientTransport.send(new DiscoverPetsRequest(request, requestAll));
    }

    private static UUID beginRequest() {
        UUID id = UUID.randomUUID();
        ASSEMBLER.begin(id);
        pending = true;
        requestTicks = 0;
        return id;
    }
}
