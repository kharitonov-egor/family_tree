package com.egakh.familytree.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;


public final class FamilyTreeKeybinds {

    public static final KeyMapping OPEN_TREE = new KeyMapping(
            "key.familytree.open_tree",
            InputConstants.Type.KEYSYM,
            com.mojang.blaze3d.platform.InputConstants.KEY_H,
            //? if >=26.1 {
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("familytree", "familytree"))
            //?} else {
            /*"key.categories.familytree"
            *///?}
    );

    private FamilyTreeKeybinds() {}

}
