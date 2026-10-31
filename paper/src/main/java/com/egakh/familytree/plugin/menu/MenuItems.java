package com.egakh.familytree.plugin.menu;

import com.egakh.familytree.plugin.text.Text;
import com.egakh.familytree.plugin.text.TextRenderer;
import com.egakh.familytree.plugin.track.Compat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Display-only item stacks for the menus. Players can never take them. */
final class MenuItems {
    private MenuItems() {}

    static ItemStack icon(TextRenderer renderer, Material material, Text name, List<Text> lore, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        renderer.name(meta, name.withDefaultColor(Text.Color.WHITE).italic(false));
        List<Text> lines = new ArrayList<>(lore.size());
        for (Text line : lore) lines.add(line.withDefaultColor(Text.Color.GRAY).italic(false));
        if (!lines.isEmpty()) renderer.lore(meta, lines);
        for (String flag : List.of("HIDE_ATTRIBUTES", "HIDE_ADDITIONAL_TOOLTIP", "HIDE_POTION_EFFECTS")) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flag));
            } catch (IllegalArgumentException missing) {
                // Older and newer servers name these flags differently.
            }
        }
        if (glint) glint(meta);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Minecraft 1.20.5 added a glint switch. Older servers get a hidden enchantment instead. */
    private static void glint(ItemMeta meta) {
        if (Compat.has(meta, "setEnchantmentGlintOverride", 1)) {
            Compat.call(meta, "setEnchantmentGlintOverride", Boolean.TRUE);
            return;
        }
        try {
            Class<?> type = Class.forName("org.bukkit.enchantments.Enchantment");
            Object enchantment = type.getMethod("getByKey", NamespacedKey.class).invoke(null, NamespacedKey.minecraft("unbreaking"));
            if (enchantment != null) {
                meta.getClass().getMethod("addEnchant", type, int.class, boolean.class).invoke(meta, enchantment, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // The icon still works without a glint.
        }
    }

    /** A blank pane for empty cells, without a tooltip where the server allows it. */
    static ItemStack filler() {
        ItemStack stack = new ItemStack(material("BLACK_STAINED_GLASS_PANE", Material.GLASS_PANE));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (Compat.has(meta, "setHideTooltip", 1)) Compat.call(meta, "setHideTooltip", Boolean.TRUE);
            else meta.setDisplayName(" ");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static Material material(String name, Material fallback) {
        Material material = Material.matchMaterial(name);
        return material == null || !material.isItem() ? fallback : material;
    }

    /** The species' spawn egg, or a name tag for animals without one. */
    static Material speciesIcon(String speciesId) {
        return material(speciesId + "_spawn_egg", Material.NAME_TAG);
    }
}
