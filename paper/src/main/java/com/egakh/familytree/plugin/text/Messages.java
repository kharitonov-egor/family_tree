package com.egakh.familytree.plugin.text;

import com.egakh.familytree.text.Templates;
import com.egakh.familytree.text.LangTables;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Family Tree's text in the player's language: the mod's 15 language files, plus a few strings only the plugin
 * uses. Players don't need the mod for any of it.
 */
public final class Messages {
    private static final Map<String, Map<String, String>> PLUGIN_TABLES = new ConcurrentHashMap<>();

    private Messages() {}

    public static String template(String language, String key) {
        String resolved = LangTables.resolve(language);
        String value = pluginTable(resolved).get(key);
        if (value == null) value = LangTables.template(resolved, key);
        if (value == null) value = pluginTable(LangTables.DEFAULT).get(key);
        return value;
    }

    public static boolean has(String language, String key) {
        return template(language, key) != null;
    }

    /** The key's text with its arguments in place. Text arguments keep their own styling and vanilla keys. */
    public static Text tr(String language, String key, Object... args) {
        String template = template(language, key);
        if (template == null) return Text.literal(key);
        Text out = Text.empty();
        for (Object part : Templates.split(template, List.of(args))) out.append(Text.of(part));
        return out;
    }

    /** Plain text for measuring and wrapping. Vanilla keys inside arguments read as English. */
    public static String plain(String language, String key, Object... args) {
        return tr(language, key, args).plain(VanillaLang::english);
    }

    static Map<String, String> pluginTable(String shipped) {
        return PLUGIN_TABLES.computeIfAbsent(shipped, Messages::load);
    }

    private static Map<String, String> load(String code) {
        try (InputStream in = Messages.class.getResourceAsStream("/familytree-plugin/lang/" + code + ".json")) {
            if (in == null) return Map.of();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) values.put(entry.getKey(), entry.getValue().getAsString());
                return Map.copyOf(values);
            }
        } catch (IOException | RuntimeException failure) {
            return Map.of();
        }
    }
}
