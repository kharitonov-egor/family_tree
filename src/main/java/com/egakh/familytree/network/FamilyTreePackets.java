package com.egakh.familytree.network;

import com.egakh.familytree.command.FamilyTreeCommand;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.naming.PetRenaming;
import com.egakh.familytree.network.payloads.*;
import com.egakh.familytree.permissions.PetAccess;
import com.egakh.familytree.permissions.ServerPetAccess;
import com.egakh.familytree.platform.ServerTransport;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import com.egakh.familytree.util.TimeUtil;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;

/** Both loaders dispatch these handlers on the server thread. */
public final class FamilyTreePackets {
    private static final RequestLimiter<UUID> OPEN = new RequestLimiter<>(500_000_000L, System::nanoTime);
    private static final RequestLimiter<UUID> DISCOVERY = new RequestLimiter<>(2_000_000_000L, System::nanoTime);
    private static final RequestLimiter<UUID> LOCATE = new RequestLimiter<>(250_000_000L, System::nanoTime);
    private static final RequestLimiter<UUID> RENAME = new RequestLimiter<>(250_000_000L, System::nanoTime);
    private static final RequestLimiter<MinecraftServer> SERVER_SCAN = new RequestLimiter<>(2_000_000_000L, System::nanoTime);
    private FamilyTreePackets() {}

    public static void open(OpenFamilyTreeRequest packet, ServerPlayer player) {
        if (OPEN.allow(player.getUUID())) sendSnapshot(player, packet.requestId(), packet.requestAll());
        else reject(player, packet.requestId());
    }

    public static void discover(DiscoverPetsRequest packet, ServerPlayer player) {
        if (!allowScan(player.level().getServer(), player.getUUID())) {
            reject(player, packet.requestId());
            return;
        }
        PetLifecycleListeners.scanLoadedPets(player.level().getServer());
        sendSnapshot(player, packet.requestId(), packet.requestAll());
    }

    public static void locate(LocatePetRequest packet, ServerPlayer player) {
        if (LOCATE.allow(player.getUUID()))
            FamilyTreeCommand.locatePet(player.createCommandSourceStack(), packet.petId());
    }

    public static void rename(RenamePetRequest packet, ServerPlayer player) {
        if (!RENAME.allow(player.getUUID())) {
            ServerTransport.send(player, new RenamePetResult(packet.requestId(), PetRenaming.Result.BUSY.ordinal(), Optional.empty()));
            return;
        }
        FamilyTreeState state = FamilyTreeState.get(player.level().getServer());
        if (state.readOnly()) {
            ServerTransport.send(player, new RenamePetResult(packet.requestId(), PetRenaming.Result.READ_ONLY.ordinal(), Optional.empty()));
            return;
        }
        AnimalRecord record = state.get(packet.petId());
        PetRenaming.Result result = PetRenaming.rename(record, player.getUUID(),
                ServerPetAccess.operator(player.createCommandSourceStack()), packet.name());
        boolean success = result == PetRenaming.Result.SUCCESS;
        if (success) state.setDirty();
        ServerTransport.send(player, new RenamePetResult(packet.requestId(), result.ordinal(),
                success ? Optional.of(record) : Optional.empty()));
    }

    public static void link(LinkParentsRequest packet, ServerPlayer player) {
        var state = FamilyTreeState.get(player.level().getServer());
        var result = !RENAME.allow(player.getUUID()) ? com.egakh.familytree.permissions.ParentLinks.Result.BUSY
                : state.readOnly() ? com.egakh.familytree.permissions.ParentLinks.Result.READ_ONLY
                : com.egakh.familytree.permissions.ParentLinks.validate(packet.child(), packet.parentA(), packet.parentB(),
                        packet.clear(), player.getUUID(), ServerPetAccess.operator(player.createCommandSourceStack()), state::get);
        if (result == com.egakh.familytree.permissions.ParentLinks.Result.SUCCESS)
            state.update(packet.child(), pet -> pet.setParents(packet.clear() ? null : packet.parentA(), packet.clear() ? null : packet.parentB()));
        ServerTransport.send(player, new LinkParentsResult(packet.requestId(), result.ordinal()));
    }

    public static void sendSnapshot(ServerPlayer player, UUID requestId, boolean requestAll) {
        var world = player.level();
        FamilyTreeState state = FamilyTreeState.get(world.getServer());
        if (state.readOnly()) {
            player.sendSystemMessage(Component.translatable("familytree.command.read_only"));
            reject(player, requestId);
            return;
        }
        boolean mayViewAll = FamilyTreeServerSettings.mayViewAll(player);
        boolean viewingAll = mayViewAll && requestAll;
        boolean operator = ServerPetAccess.operator(player.createCommandSourceStack());
        List<AnimalRecord> visible = state.all().stream()
                .filter(record -> PetAccess.canView(record, player.getUUID(), viewingAll))
                .sorted(java.util.Comparator.comparing(AnimalRecord::id))
                .limit(SnapshotPages.MAX_RECORDS + 1L)
                .map(record -> PetAccess.forViewer(record, player.getUUID(), operator)).toList();
        try {
            for (var page : SnapshotPages.split(requestId, visible, TimeUtil.currentWorldDay(world),
                    TimeUtil.currentEpochMillis(), mayViewAll, viewingAll, operator)) ServerTransport.send(player, page);
        } catch (RuntimeException failure) {
            com.egakh.familytree.FamilyTreeMod.LOGGER.warn("Could not send family snapshot", failure);
            reject(player, requestId);
        }
    }

    private static void reject(ServerPlayer player, UUID requestId) {
        ServerTransport.send(player, new SnapshotPagePayload(requestId, 0, 0, 0, List.of(), 0, 0, false, false, false));
    }

    public static boolean allowScan(MinecraftServer server, UUID player) {
        return DISCOVERY.allow(player == null ? new UUID(0, 0) : player) && SERVER_SCAN.allow(server);
    }

    public static void disconnect(UUID player) {
        OPEN.remove(player); DISCOVERY.remove(player); LOCATE.remove(player); RENAME.remove(player);
    }

    public static void stop() {
        OPEN.clear(); DISCOVERY.clear(); LOCATE.clear(); RENAME.clear(); SERVER_SCAN.clear();
    }
}
