package com.egakh.familytree.data;

import com.egakh.familytree.permissions.FamilyTreePermission;
import com.egakh.familytree.permissions.ServerPetAccess;
import com.egakh.familytree.text.ServerText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/** Tells operators who join that the history file could not be read and is being left alone. */
public final class SaveNotice {
    private SaveNotice() {}

    public static void joined(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Path file = FamilyTreeState.get(server).unreadableFile();
        if (file == null || !ServerPetAccess.allowed(player, FamilyTreePermission.RELOAD)) return;
        ServerText.send(player, Component.translatable("familytree.save.unreadable", shown(server, file))
                .withStyle(ChatFormatting.RED));
    }

    private static String shown(MinecraftServer server, Path file) {
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path absolute = file.toAbsolutePath().normalize();
        return (absolute.startsWith(world) ? world.relativize(absolute) : absolute).toString().replace('\\', '/');
    }
}
