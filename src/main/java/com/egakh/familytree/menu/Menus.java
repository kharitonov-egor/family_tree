package com.egakh.familytree.menu;

import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.network.FamilyTreePackets;
import com.egakh.familytree.text.ServerText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Chest and anvil menus, and written books, for players whose client does not have Family Tree. */
public final class Menus {
    private Menus() {}

    public static int open(ServerPlayer player) {
        FamilyTreeState state = FamilyTreeState.get(player.level().getServer());
        if (state.readOnly()) {
            ServerText.send(player, Component.translatable("familytree.command.read_only"));
            return 0;
        }
        if (!FamilyTreePackets.allowOpen(player)) {
            ServerText.send(player, Component.translatable("familytree.menu.wait"));
            return 0;
        }
        MenuSession session = new MenuSession(player);
        session.reload();
        session.showBrowser();
        return 1;
    }

    /**
     * Runs a button's action once the click packet is fully handled. Opening another menu inside the click
     * would let Minecraft apply the old menu's slot numbers to the new one.
     */
    static void afterClick(ServerPlayer owner, AbstractContainerMenu menu, Runnable action) {
        MinecraftServer server = owner.level().getServer();
        queue(server, new TickTask(server.getTickCount(), () -> {
            if (owner.containerMenu == menu && !owner.hasDisconnected()) action.run();
        }));
    }

    /**
     * Adds a task to the loop's queue. {@code execute} runs the task at once when it is called on the loop's thread
     * outside another task, which is where Minecraft 1.21.10 and newer handle packets.
     */
    static <R extends Runnable> void queue(BlockableEventLoop<R> loop, R task) {
        //? if >=1.21.2 {
        loop.schedule(task);
        //?} else {
        /*loop.tell(task);
        *///?}
    }

    /** Replaces the open menu without a close packet, so the client keeps its cursor where it was. */
    static void show(ServerPlayer player, Component title, MenuConstructor constructor) {
        if (player.containerMenu != player.inventoryMenu) player.doCloseContainer();
        player.openMenu(new SimpleMenuProvider(constructor, title));
    }

    /**
     * Opens a written book on the client the way Bukkit's {@code openBook} does: the client sees the book in its
     * offhand for one packet, opens it, and gets its real offhand item back. The server never holds the book, and
     * the book screen has no Take Book button.
     */
    static void openBook(ServerPlayer player, ItemStack book) {
        player.closeContainer();
        InventoryMenu inventory = player.inventoryMenu;
        int stateId = inventory.getStateId();
        player.connection.send(new ClientboundContainerSetSlotPacket(inventory.containerId, stateId, InventoryMenu.SHIELD_SLOT, book));
        player.connection.send(new ClientboundOpenBookPacket(InteractionHand.OFF_HAND));
        player.connection.send(new ClientboundContainerSetSlotPacket(inventory.containerId, stateId, InventoryMenu.SHIELD_SLOT,
                player.getOffhandItem().copy()));
    }

    /** Breaks text into tooltip lines. Wide CJK characters count as two columns. */
    static List<String> wrap(String text, int columns) {
        return MenuText.wrap(text, columns);
    }
}
