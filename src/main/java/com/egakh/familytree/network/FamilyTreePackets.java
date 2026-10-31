package com.egakh.familytree.network;

import com.egakh.familytree.command.FamilyTreeCommand;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.naming.PetRenaming;
import com.egakh.familytree.network.payloads.*;
import com.egakh.familytree.permissions.FamilyTreePermission;
import com.egakh.familytree.permissions.ParentLinks;
import com.egakh.familytree.permissions.PetAccess;
import com.egakh.familytree.permissions.ServerPetAccess;
import com.egakh.familytree.platform.ServerTransport;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import com.egakh.familytree.settings.ServerSettings;
import com.egakh.familytree.text.ServerText;
import com.egakh.familytree.util.TimeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;

/** Both loaders dispatch these handlers on the server thread. */
public final class FamilyTreePackets {
    private static final RequestLimiter<UUID> OPEN = limiter(cooldowns -> cooldowns.open());
    private static final RequestLimiter<UUID> DISCOVERY = limiter(cooldowns -> cooldowns.scan());
    private static final RequestLimiter<UUID> LOCATE = limiter(cooldowns -> cooldowns.locate());
    private static final RequestLimiter<UUID> RENAME = limiter(cooldowns -> cooldowns.edit());
    private static final RequestLimiter<UUID> RELATIVES = limiter(cooldowns -> cooldowns.relatives());
    private static final RequestLimiter<MinecraftServer> SERVER_SCAN = limiter(cooldowns -> cooldowns.serverScan());
    private FamilyTreePackets() {}

    private static <K> RequestLimiter<K> limiter(java.util.function.ToIntFunction<ServerSettings.Cooldowns> millis) {
        return new RequestLimiter<>(() -> millis.applyAsInt(FamilyTreeServerSettings.current().cooldowns()) * 1_000_000L, System::nanoTime);
    }

    private static boolean denied(ServerPlayer player, FamilyTreePermission permission) {
        if (ServerPetAccess.allowed(player, permission)) return false;
        ServerText.send(player, Component.translatable("familytree.permission.denied", permission.node()));
        return true;
    }

    public static void open(OpenFamilyTreeRequest packet, ServerPlayer player) {
        if (denied(player, FamilyTreePermission.USE)) reject(player, packet.requestId());
        else if (OPEN.allow(player.getUUID())) sendSnapshot(player, packet.requestId(), packet.requestAll());
        else reject(player, packet.requestId());
    }

    public static void discover(DiscoverPetsRequest packet, ServerPlayer player) {
        if (denied(player, FamilyTreePermission.USE) || denied(player, FamilyTreePermission.SCAN)) {
            reject(player, packet.requestId());
            return;
        }
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

    public static void waypoint(WaypointRequest packet, ServerPlayer player) {
        if (!LOCATE.allow(player.getUUID())) {
            ServerTransport.send(player, WaypointResult.failed(packet.requestId(), WaypointResult.Status.BUSY));
            return;
        }
        FamilyTreeState state = FamilyTreeState.get(player.level().getServer());
        AnimalRecord pet = state.get(packet.petId());
        if (denied(player, FamilyTreePermission.LOCATE) || !ServerPetAccess.canManage(player.createCommandSourceStack(), pet)) {
            ServerTransport.send(player, WaypointResult.failed(packet.requestId(), WaypointResult.Status.NOT_ALLOWED));
            return;
        }
        FamilyTreeCommand.PetPosition position = FamilyTreeCommand.position(player.level().getServer(), state, pet);
        if (position == null) {
            ServerTransport.send(player, WaypointResult.failed(packet.requestId(), WaypointResult.Status.NO_POSITION));
            return;
        }
        String name = pet.name().length() > 128 ? pet.name().substring(0, 128) : pet.name();
        long packed = BlockPos.containing(position.x(), position.y(), position.z()).asLong();
        ServerTransport.send(player, new WaypointResult(packet.requestId(), WaypointResult.Status.FOUND.ordinal(),
                name, position.dimension(), packed));
    }

    /** Holding the key sends many requests, so a denied node answers quietly instead of with a chat message each time. */
    public static void relatives(RelativesRequest packet, ServerPlayer player) {
        if (!mayShowRelatives(player)) {
            ServerTransport.send(player, RelativesResult.empty(packet.requestId(), packet.target(), RelativesResult.Status.NOT_ALLOWED));
            return;
        }
        if (!RELATIVES.allow(player.getUUID())) {
            ServerTransport.send(player, RelativesResult.empty(packet.requestId(), packet.target(), RelativesResult.Status.BUSY));
            return;
        }
        ServerTransport.send(player, nearbyRelatives(player, packet.requestId(), packet.target()));
    }

    public static boolean mayShowRelatives(ServerPlayer player) {
        return ServerPetAccess.allowed(player, FamilyTreePermission.USE) && ServerPetAccess.allowed(player, FamilyTreePermission.RELATIVES);
    }

    /** Shared by the relatives packet and the server menu. */
    public static RelativesResult nearbyRelatives(ServerPlayer player, UUID requestId, UUID target) {
        FamilyTreeState state = FamilyTreeState.get(player.level().getServer());
        if (state.readOnly()) return RelativesResult.empty(requestId, target, RelativesResult.Status.UNAVAILABLE);
        Map<UUID, Double> nearby = new HashMap<>();
        for (Animal animal : player.level().getEntitiesOfClass(Animal.class,
                player.getBoundingBox().inflate(NearbyRelatives.RANGE), Animal::isAlive)) {
            double distance = animal.distanceToSqr(player);
            if (distance <= NearbyRelatives.RANGE * NearbyRelatives.RANGE) nearby.put(animal.getUUID(), distance);
        }
        return NearbyRelatives.answer(requestId, target, player.getUUID(), ServerPetAccess.viewAll(player),
                state::get, nearby);
    }

    public record RenameOutcome(PetRenaming.Result result, AnimalRecord record) {}

    /** Shared by the rename packet and the server menu. An empty name restores the original name. */
    public static RenameOutcome applyRename(ServerPlayer player, UUID petId, String name) {
        if (!RENAME.allow(player.getUUID())) return new RenameOutcome(PetRenaming.Result.BUSY, null);
        FamilyTreeState state = FamilyTreeState.get(player.level().getServer());
        if (state.readOnly()) return new RenameOutcome(PetRenaming.Result.READ_ONLY, null);
        AnimalRecord record = state.get(petId);
        if (!ServerPetAccess.allowed(player, FamilyTreePermission.RENAME)) return new RenameOutcome(PetRenaming.Result.NO_PERMISSION, record);
        PetRenaming.Result result = PetRenaming.rename(record, player.getUUID(),
                ServerPetAccess.manageAll(player.createCommandSourceStack()), name);
        if (result == PetRenaming.Result.SUCCESS) state.recordEdited();
        return new RenameOutcome(result, record);
    }

    /**
     * 1.4.0 clients name a denied {@code familytree.rename} on the rename screen. They are the first to receive
     * {@code relatives_result_v1}, so older clients get NOT_ALLOWED and the permission message in chat.
     */
    public static void rename(RenamePetRequest packet, ServerPlayer player) {
        boolean namesPermission = ServerTransport.canSend(player, RelativesResult.TYPE);
        RenameOutcome outcome = ServerPetAccess.allowed(player, FamilyTreePermission.RENAME)
                ? applyRename(player, packet.petId(), packet.name()) : new RenameOutcome(PetRenaming.Result.NO_PERMISSION, null);
        if (outcome.result() == PetRenaming.Result.NO_PERMISSION && !namesPermission)
            ServerText.send(player, Component.translatable("familytree.permission.denied", FamilyTreePermission.RENAME.node()));
        ServerTransport.send(player, new RenamePetResult(packet.requestId(), outcome.result().wireStatus(namesPermission),
                renameReply(outcome, player.getUUID(), ServerPetAccess.manageAll(player.createCommandSourceStack()),
                        ServerPetAccess.allowed(player, FamilyTreePermission.LOCATE))));
    }

    /** The renamed record goes back filtered like a snapshot, so a rename never reveals a position the viewer may not locate. */
    static Optional<AnimalRecord> renameReply(RenameOutcome outcome, UUID viewer, boolean operator, boolean mayLocate) {
        return outcome.result() == PetRenaming.Result.SUCCESS
                ? Optional.of(PetAccess.forViewer(outcome.record(), viewer, operator, mayLocate)) : Optional.empty();
    }

    /** Shared by the parent picker packet and the server menu. */
    public static ParentLinks.Result applyLink(ServerPlayer player, UUID child, UUID parentA, UUID parentB, boolean clear) {
        var state = FamilyTreeState.get(player.level().getServer());
        var result = !RENAME.allow(player.getUUID()) ? ParentLinks.Result.BUSY
                : state.readOnly() ? ParentLinks.Result.READ_ONLY
                : !ServerPetAccess.allowed(player, FamilyTreePermission.PARENTS) ? ParentLinks.Result.NOT_ALLOWED
                : ParentLinks.validate(child, parentA, parentB, clear, player.getUUID(),
                        ServerPetAccess.manageAll(player.createCommandSourceStack()), state::get);
        if (result == ParentLinks.Result.SUCCESS)
            state.update(child, pet -> pet.setParents(clear ? null : parentA, clear ? null : parentB));
        return result;
    }

    public static void link(LinkParentsRequest packet, ServerPlayer player) {
        if (denied(player, FamilyTreePermission.PARENTS)) {
            ServerTransport.send(player, new LinkParentsResult(packet.requestId(), ParentLinks.Result.NOT_ALLOWED.ordinal()));
            return;
        }
        ServerTransport.send(player, new LinkParentsResult(packet.requestId(),
                applyLink(player, packet.child(), packet.parentA(), packet.parentB(), packet.clear()).ordinal()));
    }

    public static boolean allowOpen(ServerPlayer player) {
        return OPEN.allow(player.getUUID());
    }

    public static boolean allowLocate(ServerPlayer player) {
        return LOCATE.allow(player.getUUID());
    }

    /** Renames, parent and age edits, and the linking stick share this cooldown. */
    public static boolean allowEdit(ServerPlayer player) {
        return RENAME.allow(player.getUUID());
    }

    public static boolean allowRelatives(ServerPlayer player) {
        return RELATIVES.allow(player.getUUID());
    }

    public static void sendSnapshot(ServerPlayer player, UUID requestId, boolean requestAll) {
        var world = player.level();
        FamilyTreeState state = FamilyTreeState.get(world.getServer());
        if (state.readOnly()) {
            ServerText.send(player, Component.translatable("familytree.command.read_only"));
            reject(player, requestId);
            return;
        }
        boolean mayViewAll = ServerPetAccess.viewAll(player);
        boolean viewingAll = mayViewAll && requestAll;
        boolean operator = ServerPetAccess.manageAll(player.createCommandSourceStack());
        boolean mayLocate = ServerPetAccess.allowed(player, FamilyTreePermission.LOCATE);
        List<AnimalRecord> visible = PetAccess.snapshot(state.all(), player.getUUID(), viewingAll, operator, mayLocate,
                SnapshotPages.MAX_RECORDS + 1L);
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
        OPEN.remove(player); DISCOVERY.remove(player); LOCATE.remove(player); RENAME.remove(player); RELATIVES.remove(player);
        ServerText.forget(player);
    }

    public static void stop() {
        OPEN.clear(); DISCOVERY.clear(); LOCATE.clear(); RENAME.clear(); RELATIVES.clear(); SERVER_SCAN.clear();
        ServerText.clear();
    }
}
