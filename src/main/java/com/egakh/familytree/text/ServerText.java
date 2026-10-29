package com.egakh.familytree.text;

import com.egakh.familytree.FamilyTreeMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Gives Family Tree's translatable text a fallback in the receiving player's language. Clients with the mod
 * use their own language files; vanilla clients show the fallback the server resolved.
 */
public final class ServerText {
    private static final String PREFIX = FamilyTreeMod.MOD_ID + ".";
    private static final Map<UUID, String> REPORTED_LANGUAGES = new ConcurrentHashMap<>();

    private ServerText() {}

    public static String language(ServerPlayer player) {
        if (player == null) return LangTables.DEFAULT;
        //? if >=1.21 {
        return LangTables.resolve(player.clientInformation().language());
        //?} else {
        /*return LangTables.resolve(REPORTED_LANGUAGES.get(player.getUUID()));
        *///?}
    }

    public static String language(CommandSourceStack source) {
        return language(source.getPlayer());
    }

    /** Minecraft 1.20.1 discards the client's language, so a mixin reports it here. */
    public static void rememberLanguage(UUID player, String language) {
        if (language != null && language.length() <= 16) REPORTED_LANGUAGES.put(player, language);
    }

    public static void forget(UUID player) {
        REPORTED_LANGUAGES.remove(player);
    }

    public static void clear() {
        REPORTED_LANGUAGES.clear();
    }

    public static MutableComponent tr(String language, String key, Object... args) {
        return localize(language, Component.translatable(key, args));
    }

    /** Resolves a key to plain text on the server, for text the server must measure or wrap. */
    public static String plain(String language, String key, Object... args) {
        String template = LangTables.template(language, key);
        if (template == null) return key;
        try {
            return String.format(java.util.Locale.ROOT, template, args);
        } catch (java.util.IllegalFormatException invalid) {
            return template;
        }
    }

    public static void success(CommandSourceStack source, Supplier<Component> message, boolean broadcast) {
        source.sendSuccess(() -> localize(language(source), message.get()), broadcast);
    }

    public static void failure(CommandSourceStack source, Component message) {
        source.sendFailure(localize(language(source), message));
    }

    public static void send(ServerPlayer player, Component message) {
        player.sendSystemMessage(localize(language(player), message));
    }

    /** Copies the component, adding fallbacks to every Family Tree key, including keys nested in arguments. */
    public static MutableComponent localize(String language, Component component) {
        MutableComponent copy;
        if (component.getContents() instanceof TranslatableContents translatable) {
            Object[] args = translatable.getArgs().clone();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component nested) args[i] = localize(language, nested);
            }
            String fallback = translatable.getFallback();
            if (fallback == null && translatable.getKey().startsWith(PREFIX)) fallback = LangTables.template(language, translatable.getKey());
            copy = fallback == null ? Component.translatable(translatable.getKey(), args)
                    : Component.translatableWithFallback(translatable.getKey(), fallback, args);
        } else {
            copy = component.plainCopy();
        }
        copy.setStyle(component.getStyle());
        for (Component sibling : component.getSiblings()) copy.append(localize(language, sibling));
        return copy;
    }
}
