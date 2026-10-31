package com.egakh.familytree.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;


public final class FamilyTreeKeybinds {

    //? if >=1.21.9 {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("familytree", "familytree"));
    //?} else {
    /*private static final String CATEGORY = "key.categories.familytree";
    *///?}

    public static final KeyMapping OPEN_TREE = new KeyMapping(
            "key.familytree.open_tree",
            InputConstants.Type.KEYSYM,
            com.mojang.blaze3d.platform.InputConstants.KEY_H,
            CATEGORY
    );

    public static final KeyMapping SHOW_RELATIVES = new KeyMapping(
            "key.familytree.show_relatives",
            InputConstants.Type.KEYSYM,
            com.mojang.blaze3d.platform.InputConstants.KEY_GRAVE,
            CATEGORY
    );

    private FamilyTreeKeybinds() {}

}
