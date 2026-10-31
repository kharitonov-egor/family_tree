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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Comparator;
import java.util.List;
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
        if (!plugin.access().allowed(player, FamilyTreePermission.LOCATE) || !plugin.access().canManage(player, pet)) {
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

    private void rename(Player player, Payloads.RenameRequest request) {
        PetRenaming.Result result = Edits.rename(plugin, player, request.petId(), request.name());
        AnimalRecord record = result == PetRenaming.Result.SUCCESS ? plugin.store().get(request.petId()) : null;
        send(player, Payloads.RENAME_RESULT, Payloads.encodeRenameResult(request.requestId(), result.ordinal(), record));
    }

    private void link(Player player, Payloads.LinkRequest request) {
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
        UUID me = player.getUniqueId();
        List<AnimalRecord> visible = state.all().stream()
                .filter(record -> PetAccess.canView(record, me, viewingAll))
                .sorted(Comparator.comparing(AnimalRecord::id))
                .limit(SnapshotPages.MAX_RECORDS + 1L)
                .map(record -> PetAccess.forViewer(record, me, operator)).toList();
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
