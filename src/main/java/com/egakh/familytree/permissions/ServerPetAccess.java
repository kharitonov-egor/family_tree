package com.egakh.familytree.permissions;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPetAccess {
    private ServerPetAccess() {}

    public static boolean canView(CommandSourceStack source, AnimalRecord pet) {
        ServerPlayer player = source.getPlayer();
        boolean all = player == null ? operator(source) : FamilyTreeServerSettings.mayViewAll(player);
        return PetAccess.canView(pet, player == null ? null : player.getUUID(), all);
    }

    public static boolean canManage(CommandSourceStack source, AnimalRecord pet) {
        ServerPlayer player = source.getPlayer();
        return PetAccess.canManage(pet, player == null ? null : player.getUUID(), operator(source));
    }

    public static boolean operator(CommandSourceStack source) {
        //? if >=26.1 {
        return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
        //?} else {
        /*return source.hasPermission(2);
        *///?}
    }
}
