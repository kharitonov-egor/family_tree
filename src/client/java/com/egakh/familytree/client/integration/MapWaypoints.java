package com.egakh.familytree.client.integration;

import com.egakh.familytree.client.platform.ClientEnvironment;
import com.egakh.familytree.client.platform.ClientTransport;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.WaypointRequest;
import com.egakh.familytree.network.payloads.WaypointResult;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/** Adds pet waypoints to the minimap mods that are installed. Without one, the tree screen shows no waypoint button. */
public final class MapWaypoints {
    public interface Target {
        Component name();
        void add(String petName, BlockPos position, String dimension);
    }

    private static final List<Target> TARGETS = new CopyOnWriteArrayList<>();
    private static UUID pendingRequest;

    private MapWaypoints() {}

    public static void initialize() {
        if (ClientEnvironment.isModLoaded("xaerominimap") || ClientEnvironment.isModLoaded("xaerominimapfair"))
            register(new XaeroWaypoints());
    }

    public static void register(Target target) {
        TARGETS.removeIf(existing -> existing.getClass() == target.getClass());
        TARGETS.add(target);
    }

    public static boolean available() {
        return !TARGETS.isEmpty();
    }

    public static Component targetNames() {
        return Component.literal(TARGETS.stream().map(target -> target.name().getString()).collect(Collectors.joining(", ")));
    }

    /** Servers without the waypoint channel still get a waypoint at the last position in the client's snapshot. */
    public static void request(AnimalRecord pet) {
        if (ClientTransport.canSend(WaypointRequest.TYPE)) {
            pendingRequest = UUID.randomUUID();
            ClientTransport.send(new WaypointRequest(pendingRequest, pet.id()));
            return;
        }
        AnimalRecord.LastSeen seen = pet.lastSeen();
        if (seen == null) tell(Component.translatable("familytree.command.locate.no_position", pet.name()));
        else addEverywhere(pet.name(), BlockPos.containing(seen.x(), seen.y(), seen.z()), seen.dimension());
    }

    public static void receive(WaypointResult result) {
        if (!result.requestId().equals(pendingRequest)) return;
        pendingRequest = null;
        WaypointResult.Status[] statuses = WaypointResult.Status.values();
        WaypointResult.Status status = result.status() >= 0 && result.status() < statuses.length ? statuses[result.status()] : null;
        if (status == WaypointResult.Status.FOUND) {
            addEverywhere(result.name(), BlockPos.of(result.position()), result.dimension());
            return;
        }
        tell(Component.translatable(status == WaypointResult.Status.BUSY ? "familytree.waypoint.busy"
                : status == WaypointResult.Status.NO_POSITION ? "familytree.waypoint.no_position" : "familytree.access.denied"));
    }

    public static void clear() {
        pendingRequest = null;
    }

    private static void addEverywhere(String petName, BlockPos position, String dimension) {
        for (Target target : TARGETS) {
            try {
                target.add(petName, position, dimension);
            } catch (RuntimeException | LinkageError failure) {
                com.egakh.familytree.FamilyTreeMod.LOGGER.warn("Could not add a {} waypoint", target.name().getString(), failure);
                tell(Component.translatable("familytree.waypoint.failed", target.name()));
            }
        }
    }

    public static void tell(Component message) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        //? if >=26.1 {
        player.sendSystemMessage(message);
        //?} else {
        /*player.displayClientMessage(message, false);
        *///?}
    }

    public static String coordinates(BlockPos position) {
        return String.format(Locale.ROOT, "%d, %d, %d", position.getX(), position.getY(), position.getZ());
    }
}
