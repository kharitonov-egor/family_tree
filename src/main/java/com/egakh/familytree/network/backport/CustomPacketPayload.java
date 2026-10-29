package com.egakh.familytree.network.backport;

//? if <1.21 {
/*import net.minecraft.resources.Identifier;

// Minecraft 1.20.5 added the payload API used by shared code. 1.20.1 builds compile against these copies.
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(Identifier id) {}
}
*///?}
