package com.egakh.familytree.text;

import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.network.payloads.RelativesResult;
import com.egakh.familytree.platform.ServerTransport;
import com.egakh.familytree.util.PetNames;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Gives Family Tree's translatable text a fallback in the receiving player's language. Clients with the mod
 * use their own language files; vanilla clients show the fallback the server resolved. When {@code serverLanguage}
 * names a language, Family Tree's text is sent already resolved, so clients with the mod show that language too.
 */
public final class ServerText {
    private static final String PREFIX = FamilyTreeMod.MOD_ID + ".";
    private static final Map<UUID, String> REPORTED_LANGUAGES = new ConcurrentHashMap<>();

    private ServerText() {}

    public static String language(ServerPlayer player) {
        String forced = com.egakh.familytree.settings.FamilyTreeServerSettings.language();
        if (!forced.equals(com.egakh.familytree.settings.ServerSettings.AUTO_LANGUAGE)) return LangTables.resolve(forced);
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
        return template == null ? key : format(template, args);
    }

    /** A component's text in the given language, for text the server must measure. Vanilla keys use the server's language. */
    public static String plain(String language, Component component) {
        StringBuilder out = new StringBuilder();
        appendPlain(language, component, out);
        return out.toString();
    }

    private static void appendPlain(String language, Component component, StringBuilder out) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            String template = key.startsWith(PREFIX) ? LangTables.template(language, key) : null;
            if (template == null) template = translatable.getFallback();
            if (template == null) template = Language.getInstance().getOrDefault(key);
            Object[] args = translatable.getArgs().clone();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component nested) args[i] = plain(language, nested);
            }
            out.append(format(template, args));
        } else {
            out.append(component.plainCopy().getString());
        }
        for (Component sibling : component.getSiblings()) appendPlain(language, sibling, out);
    }

    private static String format(String template, Object[] args) {
        try {
            return String.format(java.util.Locale.ROOT, template, args);
        } catch (java.util.IllegalFormatException invalid) {
            return template;
        }
    }

    public static void success(CommandSourceStack source, Supplier<Component> message, boolean broadcast) {
        source.sendSuccess(() -> localize(language(source), message.get(), forced(), isolates(source.getPlayer())), broadcast);
    }

    public static void failure(CommandSourceStack source, Component message) {
        source.sendFailure(localize(language(source), message, forced(), isolates(source.getPlayer())));
    }

    public static void send(ServerPlayer player, Component message) {
        player.sendSystemMessage(localize(language(player), message, forced(), isolates(player)));
    }

    private static boolean isolates(ServerPlayer player) {
        return player != null && hidesIsolates(type -> ServerTransport.canSend(player, type));
    }

    /**
     * Vanilla fonts, and Family Tree before 1.4.0, draw bidi isolates as boxes. 1.4.0 added both the font that hides
     * them and the relatives channel, so a client listening on that channel draws them as nothing.
     */
    static boolean hidesIsolates(Predicate<CustomPacketPayload.Type<?>> listens) {
        return listens.test(RelativesResult.TYPE);
    }

    private static boolean forced() {
        return !com.egakh.familytree.settings.FamilyTreeServerSettings.language()
                .equals(com.egakh.familytree.settings.ServerSettings.AUTO_LANGUAGE);
    }

    /**
     * Copies the component, giving every Family Tree key a fallback in the player's language, including keys nested in
     * arguments. The language file wins over a fallback the code supplied, which is usually English.
     */
    public static MutableComponent localize(String language, Component component) {
        return localize(language, component, forced());
    }

    /** With {@code resolve}, Family Tree's keys become literal text, while Minecraft's own keys stay translatable. */
    static MutableComponent localize(String language, Component component, boolean resolve) {
        return localize(language, component, resolve, false);
    }

    /**
     * With {@code isolate}, text arguments that hold right-to-left text are wrapped in bidi isolates, so a pet's
     * Arabic or Hebrew name can't reverse the sentence it sits in.
     */
    static MutableComponent localize(String language, Component component, boolean resolve, boolean isolate) {
        MutableComponent copy;
        if (component.getContents() instanceof TranslatableContents translatable) {
            Object[] args = translatable.getArgs().clone();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component nested) args[i] = localize(language, nested, resolve, isolate);
                else if (isolate && args[i] instanceof String text) args[i] = PetNames.isolate(text);
            }
            String key = translatable.getKey();
            String template = key.startsWith(PREFIX) ? LangTables.template(language, key) : null;
            String fallback = template != null ? template : translatable.getFallback();
            if (resolve && template != null) {
                copy = Component.empty();
                for (Object part : Templates.split(template, java.util.Arrays.asList(args)))
                    copy.append(part instanceof Component text ? text : Component.literal(String.valueOf(part)));
            } else {
                copy = fallback == null ? Component.translatable(key, args) : Component.translatableWithFallback(key, fallback, args);
            }
        } else {
            copy = component.plainCopy();
        }
        copy.setStyle(component.getStyle());
        for (Component sibling : component.getSiblings()) copy.append(localize(language, sibling, resolve, isolate));
        return copy;
    }
}
