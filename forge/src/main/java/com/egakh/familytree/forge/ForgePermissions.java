package com.egakh.familytree.forge;

import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.permissions.FamilyTreePermission;
import com.egakh.familytree.permissions.ServerPetAccess;
import com.egakh.familytree.platform.PermissionBackend;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.handler.DefaultPermissionHandler;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge's PermissionAPI, which LuckPerms replaces with its own handler. With Forge's default handler,
 * Family Tree uses its own defaults directly so commands run as a player keep the level of whoever ran them.
 */
public final class ForgePermissions implements PermissionBackend {
    private final Map<FamilyTreePermission, PermissionNode<Boolean>> nodes = new EnumMap<>(FamilyTreePermission.class);
    private final Set<UUID> loggedIn = ConcurrentHashMap.newKeySet();

    public ForgePermissions() {
        for (FamilyTreePermission permission : FamilyTreePermission.values())
            nodes.put(permission, new PermissionNode<>(FamilyTreeMod.MOD_ID, permission.path(), PermissionTypes.BOOLEAN,
                    (player, id, context) -> player != null && ServerPetAccess.fallback(player, permission)));
    }

    public void register(PermissionGatherEvent.Nodes event) {
        event.addNodes(nodes.values().toArray(new PermissionNode<?>[0]));
    }

    /**
     * LuckPerms can't answer for a player until the login event, but Minecraft sends the command tree before it.
     * Until then a player gets the defaults, and the tree is sent again once the permission mod is ready.
     */
    public void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        loggedIn.add(player.getUUID());
        if (!DefaultPermissionHandler.IDENTIFIER.equals(PermissionAPI.getActivePermissionHandler()))
            player.level().getServer().getCommands().sendCommands(player);
    }

    public void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        loggedIn.remove(event.getEntity().getUUID());
    }

    @Override public boolean check(CommandSourceStack source, ServerPlayer player, FamilyTreePermission permission, boolean fallback) {
        if (DefaultPermissionHandler.IDENTIFIER.equals(PermissionAPI.getActivePermissionHandler())) return fallback;
        if (!loggedIn.contains(player.getUUID())) return fallback;
        return PermissionAPI.getPermission(player, nodes.get(permission));
    }

    @Override public String name() {
        Object handler = PermissionAPI.getActivePermissionHandler();
        return handler == null ? "Forge PermissionAPI" : "Forge PermissionAPI (" + handler + ")";
    }
}
