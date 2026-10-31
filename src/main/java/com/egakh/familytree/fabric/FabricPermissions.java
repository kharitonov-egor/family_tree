package com.egakh.familytree.fabric;

import com.egakh.familytree.permissions.FamilyTreePermission;
import com.egakh.familytree.platform.PermissionBackend;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** Loaded only when fabric-permissions-api is installed, which LuckPerms and other permission mods bundle. */
final class FabricPermissions implements PermissionBackend {
    static final String MOD_ID = "fabric-permissions-api-v0";

    @Override public boolean check(CommandSourceStack source, ServerPlayer player, FamilyTreePermission permission, boolean fallback) {
        return Permissions.check(source, permission.node(), fallback);
    }

    @Override public String name() {
        return "fabric-permissions-api";
    }
}
