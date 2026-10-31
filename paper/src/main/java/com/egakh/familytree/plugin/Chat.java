package com.egakh.familytree.plugin;

import com.egakh.familytree.plugin.text.Messages;
import com.egakh.familytree.plugin.text.Text;
import com.egakh.familytree.plugin.text.TextRenderer;
import com.egakh.familytree.settings.ServerSettings;
import com.egakh.familytree.text.LangTables;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/** Sends Family Tree's messages in each player's Minecraft language, or the one {@code serverLanguage} sets. */
public final class Chat {
    private final TextRenderer renderer;
    private final Supplier<ServerSettings> settings;

    public Chat(TextRenderer renderer, Supplier<ServerSettings> settings) {
        this.renderer = renderer;
        this.settings = settings;
    }

    public TextRenderer renderer() {
        return renderer;
    }

    public String language(CommandSender sender) {
        String forced = settings.get().serverLanguage();
        if (!forced.equals(ServerSettings.AUTO_LANGUAGE)) return LangTables.resolve(forced);
        return sender instanceof Player player ? LangTables.resolve(player.getLocale()) : LangTables.DEFAULT;
    }

    public Text tr(CommandSender sender, String key, Object... args) {
        return Messages.tr(language(sender), key, args);
    }

    public String plain(CommandSender sender, String key, Object... args) {
        return Messages.plain(language(sender), key, args);
    }

    public void send(CommandSender to, Text text) {
        renderer.send(to, text);
    }

    public void send(CommandSender to, String key, Object... args) {
        send(to, tr(to, key, args));
    }

    /** Failures are red, like Minecraft's own command errors. */
    public void fail(CommandSender to, String key, Object... args) {
        send(to, tr(to, key, args).color(Text.Color.RED));
    }

    public void fail(CommandSender to, Text text) {
        send(to, text.withDefaultColor(Text.Color.RED));
    }
}
