package com.egakh.familytree.plugin.text;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.logging.Logger;

/** Puts {@link Text} into chat, items, and books with Paper's Adventure API, or with BungeeCord chat on Spigot. */
public interface TextRenderer {
    void send(CommandSender to, Text text);

    void name(ItemMeta meta, Text name);

    void lore(ItemMeta meta, List<Text> lines);

    void openBook(Player player, String title, String author, List<Text> pages);

    String description();

    static TextRenderer detect(Logger logger) {
        try {
            Class<?> audience = Class.forName("net.kyori.adventure.audience.Audience");
            if (audience.isAssignableFrom(Player.class)) {
                return (TextRenderer) Class.forName("com.egakh.familytree.plugin.text.AdventureRenderer")
                        .getDeclaredConstructor().newInstance();
            }
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            logger.fine("Adventure is unavailable, using BungeeCord chat: " + unavailable);
        }
        return new BungeeRenderer();
    }
}
