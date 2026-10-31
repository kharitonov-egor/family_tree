package com.egakh.familytree.permissions;

import com.egakh.familytree.settings.ServerSettings;

import java.util.function.Function;

/**
 * Permission nodes for LuckPerms and other permission mods. Without one, or when a node is unset, each node
 * falls back to the default its server settings give it, which matches Family Tree 1.3.0.
 */
public enum FamilyTreePermission {
    USE("use", settings -> Default.EVERYONE),
    MENU("menu", settings -> Default.EVERYONE),
    SCAN("scan", settings -> Default.EVERYONE),
    LOCATE("locate", settings -> owners(settings.ownerActions().locate())),
    RENAME("rename", settings -> owners(settings.ownerActions().rename())),
    PARENTS("parents", settings -> owners(settings.ownerActions().editParents())),
    AGE("age", settings -> owners(settings.ownerActions().setAge())),
    LINKING_STICK("linkingstick", settings -> Default.EVERYONE),
    BREED("breed", settings -> Default.EVERYONE),
    VIEW_OTHERS("others.view", settings -> switch (settings.viewPolicy()) {
        case EVERYONE_ALL -> Default.EVERYONE;
        case OP_ONLY_ALL -> Default.OPERATORS;
        case OWN_ONLY -> Default.NOBODY;
    }),
    MANAGE_OTHERS("others.manage", settings -> Default.OPERATORS),
    PRUNE("prune", settings -> Default.OPERATORS),
    RELOAD("reload", settings -> Default.OPERATORS),
    SETTINGS("settings", settings -> Default.OPERATORS);

    public enum Default { EVERYONE, OPERATORS, NOBODY }

    public static final String PREFIX = "familytree.";

    private final String path;
    private final Function<ServerSettings, Default> fallback;

    FamilyTreePermission(String path, Function<ServerSettings, Default> fallback) {
        this.path = path;
        this.fallback = fallback;
    }

    /** The node name without the mod id, as NeoForge and Forge register it. */
    public String path() {
        return path;
    }

    public String node() {
        return PREFIX + path;
    }

    public Default fallback(ServerSettings settings) {
        return fallback.apply(settings);
    }

    /** What the node grants when no permission mod decides. */
    public boolean fallback(ServerSettings settings, boolean operator) {
        return switch (fallback(settings)) {
            case EVERYONE -> true;
            case OPERATORS -> operator;
            case NOBODY -> false;
        };
    }

    private static Default owners(boolean allowed) {
        return allowed ? Default.EVERYONE : Default.OPERATORS;
    }
}
