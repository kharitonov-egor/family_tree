package com.egakh.familytree.plugin.text;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Spigot has components only in chat and books. Item names there are legacy text, with vanilla names in English. */
final class BungeeRenderer implements TextRenderer {
    @Override public void send(CommandSender to, Text text) {
        to.spigot().sendMessage(component(text));
    }

    @Override public void name(ItemMeta meta, Text name) {
        meta.setDisplayName(legacy(name));
    }

    @Override public void lore(ItemMeta meta, List<Text> lines) {
        List<String> legacy = new ArrayList<>(lines.size());
        for (Text line : lines) legacy.add(legacy(line));
        meta.setLore(legacy);
    }

    @Override public void openBook(Player player, String title, String author, List<Text> pages) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(title);
        meta.setAuthor(author);
        List<BaseComponent[]> components = new ArrayList<>(pages.size());
        for (Text page : pages) components.add(new BaseComponent[]{component(page)});
        meta.spigot().setPages(components);
        book.setItemMeta(meta);
        player.openBook(book);
    }

    @Override public String description() {
        return "BungeeCord chat";
    }

    static BaseComponent component(Text text) {
        BaseComponent component;
        if (text.literal() != null) component = new TextComponent(text.literal());
        else if (text.keybind() != null) component = new net.md_5.bungee.api.chat.KeybindComponent(text.keybind());
        else {
            List<BaseComponent> args = new ArrayList<>(text.args().size());
            for (Text arg : text.args()) args.add(component(arg));
            TranslatableComponent translatable = new TranslatableComponent(text.key());
            if (!args.isEmpty()) translatable.setWith(args);
            component = translatable;
        }
        ChatColor color = color(text);
        if (color != null) component.setColor(color);
        if (text.bold() != null) component.setBold(text.bold());
        if (text.italic() != null) component.setItalic(text.italic());
        if (text.underlined() != null) component.setUnderlined(text.underlined());
        if (text.suggestCommand() != null) component.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, text.suggestCommand()));
        if (text.runCommand() != null) component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, text.runCommand()));
        for (Text child : text.children()) component.addExtra(component(child));
        return component;
    }

    private static ChatColor color(Text text) {
        if (text.rgb() != null) return ChatColor.of(String.format("#%06X", text.rgb()));
        return text.color() == null ? null : ChatColor.of(text.color().name());
    }

    /** Legacy text starts with a color code, which also turns off the italics Minecraft gives custom item names. */
    static String legacy(Text text) {
        StringBuilder out = new StringBuilder();
        appendLegacy(out, text, ChatColor.WHITE, false, false);
        return out.toString();
    }

    private static void appendLegacy(StringBuilder out, Text text, ChatColor inheritedColor, boolean inheritedBold, boolean inheritedUnderline) {
        ChatColor own = color(text);
        ChatColor color = own != null ? own : inheritedColor;
        boolean bold = text.bold() != null ? text.bold() : inheritedBold;
        boolean underline = text.underlined() != null ? text.underlined() : inheritedUnderline;
        String prefix = color.toString() + (bold ? ChatColor.BOLD : "") + (underline ? ChatColor.UNDERLINE : "");
        if (text.literal() != null || text.keybind() != null) {
            String value = text.literal() != null ? text.literal() : text.keybind();
            if (!value.isEmpty()) out.append(prefix).append(value);
        } else {
            String template = VanillaLang.english(text.key());
            for (Object part : Templates.split(template, text.args())) {
                if (part instanceof Text arg) appendLegacy(out, arg, color, bold, underline);
                else if (!part.toString().isEmpty()) out.append(prefix).append(part);
            }
        }
        for (Text child : text.children()) appendLegacy(out, child, color, bold, underline);
    }
}
