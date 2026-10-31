package com.egakh.familytree.plugin.text;

import net.md_5.bungee.api.ChatColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Relation labels use the mod's exact colors, which Minecraft's named chat colors can't show. */
class TextColorTest {
    @Test void rgbColorsReachBungeeChatAndLegacyItemText() {
        Text parent = Text.literal("Parent").rgb(0xE69F00);
        assertEquals(ChatColor.of("#E69F00"), BungeeRenderer.component(parent).getColor());
        assertTrue(BungeeRenderer.legacy(parent).startsWith(ChatColor.of("#E69F00").toString()));
        assertEquals(0xE69F00, parent.withDefaultColor(Text.Color.RED).rgb(), "a default color doesn't replace an RGB color");
        assertNull(parent.copy().color(Text.Color.GOLD).rgb(), "a named color replaces the RGB color");
    }
}
