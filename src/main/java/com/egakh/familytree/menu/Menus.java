package com.egakh.familytree.menu;

import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.network.FamilyTreePackets;
import com.egakh.familytree.text.ServerText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;

import java.util.List;

/** Chest, anvil, and lectern menus for players whose client does not have Family Tree. */
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
        owner.level().getServer().execute(() -> {
            if (owner.containerMenu == menu && !owner.hasDisconnected()) action.run();
        });
    }

    /** Replaces the open menu without a close packet, so the client keeps its cursor where it was. */
    static void show(ServerPlayer player, Component title, MenuConstructor constructor) {
        if (player.containerMenu != player.inventoryMenu) player.doCloseContainer();
        player.openMenu(new SimpleMenuProvider(constructor, title));
    }

    /** Breaks text into tooltip lines. Wide CJK characters count as two columns. */
    static List<String> wrap(String text, int columns) {
        return MenuText.wrap(text, columns);
    }
}
