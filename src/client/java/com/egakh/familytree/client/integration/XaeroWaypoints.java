package com.egakh.familytree.client.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Xaero's Minimap has no stable API, but it turns waypoint shares in system chat into an Add link.
 * The share stays on this client. In testing, a share passed to ChatListener locally never reached Xaero's handler,
 * so it goes to that handler directly, and appears as plain chat text if the handler is missing or declines it.
 */
final class XaeroWaypoints implements MapWaypoints.Target {
    private static final int GREEN = 10;
    private static Method handler;
    private static boolean looked;

    @Override public Component name() {
        return Component.translatable("familytree.waypoint.xaero");
    }

    @Override public void add(String petName, BlockPos position, String dimension) {
        Component share = Component.literal(share(petName, position, dimension));
        if (!offeredByXaero(share)) Minecraft.getInstance().getChatListener().handleSystemMessage(share, false);
    }

    private static boolean offeredByXaero(Component share) {
        if (!looked) {
            looked = true;
            try {
                handler = Class.forName("xaero.common.core.XaeroMinimapCore").getMethod("onSystemChat", Component.class);
            } catch (ReflectiveOperationException | LinkageError missing) {
                com.egakh.familytree.FamilyTreeMod.LOGGER.warn("Xaero's Minimap has no chat share handler; showing the waypoint as text");
            }
        }
        if (handler == null) return false;
        try {
            return Boolean.TRUE.equals(handler.invoke(null, share));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            com.egakh.familytree.FamilyTreeMod.LOGGER.warn("Xaero's Minimap could not read the waypoint share", failure);
            return false;
        }
    }

    static String share(String petName, BlockPos position, String dimension) {
        String name = petName.isBlank() ? "Pet" : petName.strip();
        String initials = name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        return "<Family Tree> xaero-waypoint:" + escape(name) + ":" + escape(initials) + ":" + position.getX() + ":"
                + position.getY() + ":" + position.getZ() + ":" + GREEN + ":false:0:Internal-" + escape(directory(dimension)) + "-waypoints";
    }

    /** Xaero's directory names: overworld, the_nether, the_end, or dim%namespace$path for other dimensions. */
    private static String directory(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> "overworld";
            case "minecraft:the_nether" -> "the_nether";
            case "minecraft:the_end" -> "the_end";
            default -> "dim%" + dimension.replace(':', '$').replace('/', '%');
        };
    }

    // Mirrors Xaero's share escaping, which it reverses when it reads the share.
    private static String escape(String text) {
        return text.replace(":", "^col^").replace("-", "^min^").replace("_", "-").replace("*", "^ast^");
    }
}
