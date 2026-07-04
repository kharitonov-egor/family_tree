package com.egakh.familytree.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class FamilyTreeKeybinds {

    public static final KeyMapping OPEN_TREE = new KeyMapping(
            "key.familytree.open_tree",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("familytree", "familytree"))
    );

    private FamilyTreeKeybinds() {}

    public static void register() {
        KeyMappingHelper.registerKeyMapping(OPEN_TREE);
    }
}
