package com.egakh.familytree.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public final class ServerTransport {
    private static BiPredicate<ServerPlayer, CustomPacketPayload.Type<?>> supported = (player, type) -> false;
    private static BiConsumer<ServerPlayer, CustomPacketPayload> sender;
    private ServerTransport() {}
    public static void configure(BiPredicate<ServerPlayer, CustomPacketPayload.Type<?>> check,
                                  BiConsumer<ServerPlayer, CustomPacketPayload> send) {
        supported = check;
        sender = send;
    }
    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) { return supported.test(player, type); }
    public static void send(ServerPlayer player, CustomPacketPayload packet) {
        if (canSend(player, packet.type())) sender.accept(player, packet);
    }
}
