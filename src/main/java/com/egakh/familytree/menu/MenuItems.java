package com.egakh.familytree.menu;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
//?} else {
/*import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
*///?}

import java.util.ArrayList;
import java.util.List;

/** Display-only item stacks for the server menus. Players can never take them. */
final class MenuItems {
    private MenuItems() {}

    static ItemStack icon(Item item, Component name, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(item);
        List<Component> lines = new ArrayList<>();
        for (Component line : lore) lines.add(plain(line, ChatFormatting.GRAY));
        //? if >=1.21 {
        stack.set(DataComponents.CUSTOM_NAME, plain(name, ChatFormatting.WHITE));
        if (!lines.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lines));
        if (glint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        //?} else {
        /*stack.setHoverName(plain(name, ChatFormatting.WHITE));
        if (!lines.isEmpty()) {
            ListTag tag = new ListTag();
            for (Component line : lines) tag.add(StringTag.valueOf(Component.Serializer.toJson(line)));
            stack.getOrCreateTagElement("display").put("Lore", tag);
        }
        *///?}
        return stack;
    }

    /** A blank pane for empty menu cells, without a tooltip where the game allows it. */
    static ItemStack filler() {
        ItemStack stack = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        //? if >=1.21.5 {
        stack.set(DataComponents.TOOLTIP_DISPLAY, new net.minecraft.world.item.component.TooltipDisplay(true, new java.util.LinkedHashSet<>()));
        //?}
        //? if >=1.21 <1.21.5 {
        /*stack.set(DataComponents.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
        *///?}
        //? if <1.21 {
        /*stack.setHoverName(Component.literal(" "));
        *///?}
        return stack;
    }

    static ItemStack writtenBook(String title, String author, List<Component> pages) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        //? if >=1.21 {
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0,
                pages.stream().map(Filterable::passThrough).toList(), true));
        //?} else {
        /*var tag = stack.getOrCreateTag();
        tag.putString("title", title);
        tag.putString("author", author);
        tag.putBoolean("resolved", true);
        ListTag list = new ListTag();
        for (Component page : pages) list.add(StringTag.valueOf(Component.Serializer.toJson(page)));
        tag.put("pages", list);
        *///?}
        return stack;
    }

    /** The species' spawn egg, or a name tag for animals without one. */
    static Item speciesIcon(String speciesId) {
        Identifier egg = Identifier.tryParse(speciesId + "_spawn_egg");
        Item item = egg == null ? null : BuiltInRegistries.ITEM.getOptional(egg).orElse(null);
        return item == null || item == Items.AIR ? Items.NAME_TAG : item;
    }

    /** The species name in the client's language, using the game's own entity names. */
    static MutableComponent speciesName(String speciesId) {
        int colon = speciesId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : speciesId.substring(0, colon);
        String path = colon < 0 ? speciesId : speciesId.substring(colon + 1);
        return Component.translatableWithFallback("entity." + namespace + "." + path.replace('/', '.'), path.replace('_', ' '));
    }

    private static Component plain(Component text, ChatFormatting color) {
        MutableComponent copy = text.copy();
        return copy.withStyle(style -> style.withItalic(false).withColor(style.getColor() == null
                ? net.minecraft.network.chat.TextColor.fromLegacyFormat(color) : style.getColor()));
    }
}
