package com.egakh.familytree.plugin.text;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Paper and its forks. Vanilla keys stay translatable everywhere, including item names and lore. */
final class AdventureRenderer implements TextRenderer {
    @Override public void send(CommandSender to, Text text) {
        ((Audience) to).sendMessage(component(text));
    }

    @Override public void name(ItemMeta meta, Text name) {
        meta.displayName(component(name));
    }

    @Override public void lore(ItemMeta meta, List<Text> lines) {
        List<Component> lore = new ArrayList<>(lines.size());
        for (Text line : lines) lore.add(component(line));
        meta.lore(lore);
    }

    @Override public void openBook(Player player, String title, String author, List<Text> pages) {
        List<Component> components = new ArrayList<>(pages.size());
        for (Text page : pages) components.add(component(page));
        ((Audience) player).openBook(Book.book(Component.text(title), Component.text(author), components));
    }

    @Override public String description() {
        return "Adventure";
    }

    static Component component(Text text) {
        Component component;
        if (text.literal() != null) component = Component.text(text.literal());
        else if (text.keybind() != null) component = Component.keybind(text.keybind());
        else {
            List<Component> args = new ArrayList<>(text.args().size());
            for (Text arg : text.args()) args.add(component(arg));
            component = Component.translatable(text.key(), args);
        }
        if (text.color() != null) component = component.color(NamedTextColor.NAMES.value(text.color().name().toLowerCase(java.util.Locale.ROOT)));
        if (text.bold() != null) component = component.decoration(TextDecoration.BOLD, text.bold());
        if (text.italic() != null) component = component.decoration(TextDecoration.ITALIC, text.italic());
        if (text.underlined() != null) component = component.decoration(TextDecoration.UNDERLINED, text.underlined());
        if (text.suggestCommand() != null) component = component.clickEvent(ClickEvent.suggestCommand(text.suggestCommand()));
        if (text.runCommand() != null) component = component.clickEvent(ClickEvent.runCommand(text.runCommand()));
        List<Component> children = new ArrayList<>(text.children().size());
        for (Text child : text.children()) children.add(component(child));
        return children.isEmpty() ? component : component.children(children);
    }
}
