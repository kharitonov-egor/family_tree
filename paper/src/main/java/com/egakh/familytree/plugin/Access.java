package com.egakh.familytree.plugin;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.permissions.FamilyTreePermission;
import com.egakh.familytree.permissions.PetAccess;
import com.egakh.familytree.settings.ServerSettings;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/**
 * The mod's permission nodes through Bukkit's permission API, which LuckPerms implements. A node a permission
 * plugin doesn't set falls back to the default the settings give it, as in the mod. plugin.yml declares every node
 * with {@code default: false}, because Bukkit and LuckPerms treat a plugin.yml default as a set node.
 */
public final class Access {
    private final Supplier<ServerSettings> settings;

    public Access(Supplier<ServerSettings> settings) {
        this.settings = settings;
    }

    public boolean operator(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender || sender.isOp();
    }

    public boolean allowed(CommandSender sender, FamilyTreePermission permission) {
        boolean fallback = permission.fallback(settings.get(), operator(sender));
        if (!(sender instanceof Player player)) return fallback;
        return player.isPermissionSet(permission.node()) ? player.hasPermission(permission.node()) : fallback;
    }

    public boolean viewAll(Player player) {
        return allowed(player, FamilyTreePermission.VIEW_OTHERS);
    }

    public boolean manageAll(CommandSender sender) {
        return allowed(sender, FamilyTreePermission.MANAGE_OTHERS);
    }

    public boolean canView(CommandSender sender, AnimalRecord pet) {
        Player player = sender instanceof Player p ? p : null;
        boolean all = player == null ? operator(sender) : viewAll(player);
        return PetAccess.canView(pet, player == null ? null : player.getUniqueId(), all);
    }

    /** Ownership or familytree.others.manage. Each action also checks its own node. */
    public boolean canManage(CommandSender sender, AnimalRecord pet) {
        Player player = sender instanceof Player p ? p : null;
        return PetAccess.canManage(pet, player == null ? null : player.getUniqueId(), manageAll(sender));
    }
}
