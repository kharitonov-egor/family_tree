package com.egakh.familytree.permissions;

import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.platform.PermissionBackend;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPetAccess {
    private static volatile PermissionBackend backend = PermissionBackend.NONE;
    private static volatile boolean failureLogged;

    private ServerPetAccess() {}

    public static void configure(PermissionBackend permissions) {
        backend = permissions;
        FamilyTreeMod.LOGGER.info("Family Tree checks its permission nodes through {}", permissions.name());
    }

    public static String backendName() {
        return backend.name();
    }

    /**
     * Asks the permission mod for a player's node, falling back to the node's default. The console and command
     * blocks have no player, so they always get the default, which grants operator-only nodes at their level.
     */
    public static boolean allowed(CommandSourceStack source, FamilyTreePermission permission) {
        boolean fallback = permission.fallback(FamilyTreeServerSettings.current(), operator(source));
        ServerPlayer player = source.getPlayer();
        if (player == null) return fallback;
        try {
            return backend.check(source, player, permission, fallback);
        } catch (RuntimeException failure) {
            if (!failureLogged) {
                failureLogged = true;
                FamilyTreeMod.LOGGER.warn("Family Tree could not check {} through {}, so it uses the default",
                        permission.node(), backend.name(), failure);
            }
            return fallback;
        }
    }

    /** What a node grants a player when no permission mod sets it. NeoForge and Forge register this as each node's default. */
    public static boolean fallback(ServerPlayer player, FamilyTreePermission permission) {
        return permission.fallback(FamilyTreeServerSettings.current(), operator(player.createCommandSourceStack()));
    }

    public static boolean allowed(ServerPlayer player, FamilyTreePermission permission) {
        return allowed(player.createCommandSourceStack(), permission);
    }

    /** Uses the player's own level, as 1.3.0 did, even when a command runs as the player. */
    public static boolean viewAll(ServerPlayer player) {
        return allowed(player.createCommandSourceStack(), FamilyTreePermission.VIEW_OTHERS);
    }

    public static boolean manageAll(CommandSourceStack source) {
        return allowed(source, FamilyTreePermission.MANAGE_OTHERS);
    }

    public static boolean canView(CommandSourceStack source, AnimalRecord pet) {
        ServerPlayer player = source.getPlayer();
        boolean all = player == null ? operator(source) : viewAll(player);
        return PetAccess.canView(pet, player == null ? null : player.getUUID(), all);
    }

    /** Ownership or familytree.others.manage. Each action also checks its own node. */
    public static boolean canManage(CommandSourceStack source, AnimalRecord pet) {
        ServerPlayer player = source.getPlayer();
        return PetAccess.canManage(pet, player == null ? null : player.getUUID(), manageAll(source));
    }

    public static boolean operator(CommandSourceStack source) {
        int level = FamilyTreeServerSettings.current().operatorLevel();
        //? if >=1.21.11 {
        var check = switch (level) {
            case 1 -> Commands.LEVEL_MODERATORS;
            case 3 -> Commands.LEVEL_ADMINS;
            case 4 -> Commands.LEVEL_OWNERS;
            default -> Commands.LEVEL_GAMEMASTERS;
        };
        return check.check(source.permissions());
        //?} else {
        /*return source.hasPermission(level);
        *///?}
    }
}
