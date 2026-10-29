package com.egakh.familytree.menu;

import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.network.FamilyTreePackets;
import com.egakh.familytree.text.ServerText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;

import java.util.ArrayList;
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
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int width = 0;
        int lastSpace = -1;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\n') {
                lines.add(line.toString().strip());
                line.setLength(0);
                width = 0;
                lastSpace = -1;
                continue;
            }
            line.appendCodePoint(codePoint);
            width += wide(codePoint) ? 2 : 1;
            if (codePoint == ' ') lastSpace = line.length();
            if (width > columns) {
                int cut = lastSpace > 0 ? lastSpace : line.length() - Character.charCount(codePoint);
                if (cut <= 0) cut = line.length();
                lines.add(line.substring(0, cut).strip());
                String rest = line.substring(cut);
                line.setLength(0);
                line.append(rest);
                width = rest.codePoints().map(c -> wide(c) ? 2 : 1).sum();
                lastSpace = -1;
            }
        }
        if (!line.isEmpty()) lines.add(line.toString().strip());
        lines.removeIf(String::isEmpty);
        return lines;
    }

    private static boolean wide(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL
                || codePoint >= 0xFF00 && codePoint <= 0xFFEF || codePoint >= 0x3000 && codePoint <= 0x303F;
    }
}
