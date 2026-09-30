package com.egakh.familytree.client.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ClientTransport {
    private static Predicate<CustomPacketPayload.Type<?>> supported = type -> false;
    private static Consumer<CustomPacketPayload> sender;
    private ClientTransport() {}
    public static void configure(Predicate<CustomPacketPayload.Type<?>> check, Consumer<CustomPacketPayload> send) {
        supported = check;
        sender = send;
    }
    public static boolean canSend(CustomPacketPayload.Type<?> type) { return supported.test(type); }
    public static void send(CustomPacketPayload packet) {
        if (canSend(packet.type())) sender.accept(packet);
    }
}
