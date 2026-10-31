package com.egakh.familytree.platform;

import com.egakh.familytree.permissions.FamilyTreePermission;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** A loader's permission API. Each loader adapter installs one; without a permission mod it returns the fallback. */
public interface PermissionBackend {
    PermissionBackend NONE = new PermissionBackend() {
        @Override public boolean check(CommandSourceStack source, ServerPlayer player, FamilyTreePermission permission, boolean fallback) {
            return fallback;
        }

        @Override public String name() {
            return "none";
        }
    };

    boolean check(CommandSourceStack source, ServerPlayer player, FamilyTreePermission permission, boolean fallback);

    /** Shown in the log and in /familytree settings. */
    String name();
}
