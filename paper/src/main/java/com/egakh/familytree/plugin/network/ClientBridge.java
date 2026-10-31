package com.egakh.familytree.plugin.network;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.naming.PetRenaming;
import com.egakh.familytree.permissions.FamilyTreePermission;
import com.egakh.familytree.permissions.ParentLinks;
import com.egakh.familytree.permissions.PetAccess;
import com.egakh.familytree.plugin.Edits;
import com.egakh.familytree.plugin.FamilyTreePlugin;
import com.egakh.familytree.plugin.WorldClock;
import com.egakh.familytree.plugin.command.FamilyTreeCommand;
import com.egakh.familytree.plugin.storage.FamilyTreeStore;
import com.egakh.familytree.plugin.track.Entities;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Lets players with the Family Tree client mod use its screens on a plugin server. The plugin registers the mod's
 * channels and answers with the bytes the mod's server would send. Handlers mirror the mod's {@code FamilyTreePackets}.
 */
public final class ClientBridge implements PluginMessageListener {
    private static final int HEADROOM = 64;
    private final FamilyTreePlugin plugin;
    private boolean registered;

    public ClientBridge(FamilyTreePlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (!plugin.clientModSupport() || registered) return;
        Messenger messenger = Bukkit.getMessenger();
        for (String channel : Payloads.OUTGOING) messenger.registerOutgoingPluginChannel(plugin, channel);
        for (String channel : Payloads.INCOMING) messenger.registerIncomingPluginChannel(plugin, channel, this);
        registered = true;
    }

    /** Bukkit announces channels to players as they join, so a change after a reload reaches players who join later. */
    public void update() {
        if (plugin.clientModSupport()) register();
        else unregister();
    }

    public void unregister() {
        if (!registered) return;
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin);
        registered = false;
    }

    /** Clients announce the channels they listen on when they join; the mod listens on its snapshot channel. */
    public boolean hasClientMod(Player player) {
        return registered && plugin.clientModSupport() && player.getListeningPluginChannels().contains(Payloads.SNAPSHOT);
    }

    public void forget(Player player) {
        // Cooldowns are per player and cleared by the plugin on quit.
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.later(() -> onPluginMessageReceived(channel, player, message));
            return;
        }
        if (!player.isOnline() || !plugin.clientModSupport()) return;
        try {
            switch (channel) {
                case Payloads.OPEN -> open(player, Payloads.decodeOpen(message));
                case Payloads.DISCOVER -> discover(player, Payloads.decodeOpen(message));
                case Payloads.LOCATE -> locate(player, Payloads.decodeLocate(message));
                case Payloads.RENAME -> rename(player, Payloads.decodeRename(message));
                case Payloads.LINK -> link(player, Payloads.decodeLink(message));
                case Payloads.WAYPOINT -> waypoint(player, Payloads.decodeWaypoint(message));
                case Payloads.RELATIVES -> relatives(player, Payloads.decodeRelatives(message));
                default -> { }
            }
        } catch (RuntimeException malformed) {
            plugin.getLogger().log(Level.FINE, "Ignored a malformed Family Tree message on " + channel + " from " + player.getName(), malformed);
        }
    }

    private boolean denied(Player player, FamilyTreePermission permission) {
        if (plugin.access().allowed(player, permission)) return false;
        plugin.chat().send(player, "familytree.permission.denied", permission.node());
        return true;
    }

    private void open(Player player, Payloads.OpenRequest request) {
        if (denied(player, FamilyTreePermission.USE)) reject(player, request.requestId());
        else if (plugin.cooldowns().open(player.getUniqueId())) sendSnapshot(player, request.requestId(), request.requestAll());
        else reject(player, request.requestId());
    }

    private void discover(Player player, Payloads.OpenRequest request) {
        if (denied(player, FamilyTreePermission.USE) || denied(player, FamilyTreePermission.SCAN)
                || !plugin.cooldowns().scan(player.getUniqueId())) {
            reject(player, request.requestId());
            return;
        }
        plugin.tracker().scanLoadedPets(record -> true);
        sendSnapshot(player, request.requestId(), request.requestAll());
    }

    private void locate(Player player, Payloads.LocateRequest request) {
        if (plugin.cooldowns().locate(player.getUniqueId())) plugin.command().locatePet(player, request.petId());
    }

    private void waypoint(Player player, Payloads.WaypointRequest request) {
        if (!plugin.cooldowns().locate(player.getUniqueId())) {
            send(player, Payloads.WAYPOINT_RESULT, Payloads.encodeWaypointResult(
                    Payloads.WaypointResult.failed(request.requestId(), Payloads.WaypointResult.Status.BUSY)));
            return;
        }
        FamilyTreeStore state = plugin.store();
        AnimalRecord pet = state.get(request.petId());
        if (denied(player, FamilyTreePermission.LOCATE) || !plugin.access().canManage(player, pet)) {
            send(player, Payloads.WAYPOINT_RESULT, Payloads.encodeWaypointResult(
                    Payloads.WaypointResult.failed(request.requestId(), Payloads.WaypointResult.Status.NOT_ALLOWED)));
            return;
        }
        FamilyTreeCommand.PetPosition position = plugin.command().position(state, pet);
        if (position == null) {
            send(player, Payloads.WAYPOINT_RESULT, Payloads.encodeWaypointResult(
                    Payloads.WaypointResult.failed(request.requestId(), Payloads.WaypointResult.Status.NO_POSITION)));
            return;
        }
        String name = pet.name().length() > 128 ? pet.name().substring(0, 128) : pet.name();
        long packed = Payloads.packBlockPos((int) Math.floor(position.x()), (int) Math.floor(position.y()), (int) Math.floor(position.z()));
        send(player, Payloads.WAYPOINT_RESULT, Payloads.encodeWaypointResult(new Payloads.WaypointResult(request.requestId(),
                Payloads.WaypointResult.Status.FOUND.ordinal(), name, position.dimension(), packed)));
    }

    /** Holding the key sends many requests, so a denied node answers quietly instead of with a chat message each time. */
    private void relatives(Player player, Payloads.RelativesRequest request) {
        Payloads.RelativesResult result = !mayShowRelatives(player)
                ? Payloads.RelativesResult.empty(request.requestId(), request.target(), Payloads.RelativesResult.Status.NOT_ALLOWED)
                : !plugin.cooldowns().relatives(player.getUniqueId())
                ? Payloads.RelativesResult.empty(request.requestId(), request.target(), Payloads.RelativesResult.Status.BUSY)
                : nearbyRelatives(player, request.requestId(), request.target());
        send(player, Payloads.RELATIVES_RESULT, Payloads.encodeRelativesResult(result));
    }

    public boolean mayShowRelatives(Player player) {
        return plugin.access().allowed(player, FamilyTreePermission.USE) && plugin.access().allowed(player, FamilyTreePermission.RELATIVES);
    }

    /** Shared by the relatives channel and the chest menu. */
    public Payloads.RelativesResult nearbyRelatives(Player player, UUID requestId, UUID target) {
        FamilyTreeStore state = plugin.store();
        if (state.readOnly()) return Payloads.RelativesResult.empty(requestId, target, Payloads.RelativesResult.Status.UNAVAILABLE);
        double range = NearbyRelatives.RANGE;
        Location at = player.getLocation();
        Map<UUID, Double> nearby = new HashMap<>();
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!Entities.isAnimal(entity) || entity.isDead()) continue;
            double distance = entity.getLocation().distanceSquared(at);
            if (distance <= range * range) nearby.put(entity.getUniqueId(), distance);
        }
        return NearbyRelatives.answer(requestId, target, player.getUniqueId(), plugin.access().viewAll(player), state::get, nearby);
    }

    /**
     * 1.4.0 clients name a denied {@code familytree.rename} on the rename screen. They are the first to listen on
     * the relatives result channel, so older clients get NOT_ALLOWED and the permission message in chat, as in the mod.
     */
    private void rename(Player player, Payloads.RenameRequest request) {
        boolean namesPermission = player.getListeningPluginChannels().contains(Payloads.RELATIVES_RESULT);
        PetRenaming.Result result = plugin.access().allowed(player, FamilyTreePermission.RENAME)
                ? Edits.rename(plugin, player, request.petId(), request.name()) : PetRenaming.Result.NO_PERMISSION;
        if (result == PetRenaming.Result.NO_PERMISSION && !namesPermission)
            plugin.chat().send(player, "familytree.permission.denied", FamilyTreePermission.RENAME.node());
        AnimalRecord record = result == PetRenaming.Result.SUCCESS ? plugin.store().get(request.petId()) : null;
        send(player, Payloads.RENAME_RESULT, renameReply(request.requestId(), result.wireStatus(namesPermission), record,
                player.getUniqueId(), plugin.access().manageAll(player), plugin.access().allowed(player, FamilyTreePermission.LOCATE)));
    }

    /** The renamed record goes back filtered like a snapshot, so a rename never reveals a position the viewer may not locate. */
    static byte[] renameReply(UUID requestId, int status, AnimalRecord saved, UUID viewer, boolean operator, boolean mayLocate) {
        return Payloads.encodeRenameResult(requestId, status, saved == null ? null : PetAccess.forViewer(saved, viewer, operator, mayLocate));
    }

    private void link(Player player, Payloads.LinkRequest request) {
        if (denied(player, FamilyTreePermission.PARENTS)) {
            send(player, Payloads.LINK_RESULT, Payloads.encodeLinkResult(request.requestId(), ParentLinks.Result.NOT_ALLOWED.ordinal()));
            return;
        }
        ParentLinks.Result result = Edits.link(plugin, player, request.child(), request.parentA(), request.parentB(), request.clear());
        send(player, Payloads.LINK_RESULT, Payloads.encodeLinkResult(request.requestId(), result.ordinal()));
    }

    private void sendSnapshot(Player player, UUID requestId, boolean requestAll) {
        FamilyTreeStore state = plugin.store();
        if (state.readOnly()) {
            plugin.chat().send(player, "familytree.command.read_only");
            reject(player, requestId);
            return;
        }
        boolean mayViewAll = plugin.access().viewAll(player);
        boolean viewingAll = mayViewAll && requestAll;
        boolean operator = plugin.access().manageAll(player);
        boolean mayLocate = plugin.access().allowed(player, FamilyTreePermission.LOCATE);
        List<AnimalRecord> visible = PetAccess.snapshot(state.all(), player.getUniqueId(), viewingAll, operator, mayLocate,
                SnapshotPages.MAX_RECORDS + 1L);
        try {
            for (Payloads.SnapshotPage page : SnapshotPages.split(requestId, visible, WorldClock.currentWorldDay(),
                    WorldClock.currentEpochMillis(), mayViewAll, viewingAll, operator, maxMessageBytes() - HEADROOM))
                send(player, Payloads.SNAPSHOT, Payloads.encodePage(page));
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not send family snapshot", failure);
            reject(player, requestId);
        }
    }

    private void reject(Player player, UUID requestId) {
        send(player, Payloads.SNAPSHOT, Payloads.encodePage(new Payloads.SnapshotPage(requestId, 0, 0, 0, List.of(), 0, 0, false, false, false)));
    }

    private void send(Player player, String channel, byte[] bytes) {
        player.sendPluginMessage(plugin, channel, bytes);
    }

    /** Spigot 1.20.1 allows 32,766 bytes per plugin message; newer servers allow 1 MiB. Read at runtime, not compile time. */
    static int maxMessageBytes() {
        try {
            return Messenger.class.getField("MAX_MESSAGE_SIZE").getInt(null);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return 32766;
        }
    }
}
