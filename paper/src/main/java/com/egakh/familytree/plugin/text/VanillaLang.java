package com.egakh.familytree.plugin.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minecraft's English names, read from the server jar. Spigot can't put translatable text on items, so item names
 * there use these; chat and books keep the client's own language.
 */
public final class VanillaLang {
    private static volatile Map<String, String> english;
    private static volatile ClassLoader serverLoader = VanillaLang.class.getClassLoader();

    private VanillaLang() {}

    public static void useServerClassLoader(ClassLoader loader) {
        serverLoader = loader;
        english = null;
    }

    public static boolean has(String key) {
        Map<String, String> table = table();
        return table.isEmpty() || table.containsKey(key);
    }

    public static String english(String key) {
        String value = table().get(key);
        if (value != null) return value;
        int dot = key.lastIndexOf('.');
        String last = dot < 0 ? key : key.substring(dot + 1);
        String spaced = last.replace('_', ' ');
        return spaced.isEmpty() ? key : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1).toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> table() {
        Map<String, String> loaded = english;
        if (loaded == null) english = loaded = load();
        return loaded;
    }

    private static Map<String, String> load() {
        try (InputStream in = serverLoader.getResourceAsStream("assets/minecraft/lang/en_us.json")) {
            if (in == null) return Map.of();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) values.put(entry.getKey(), entry.getValue().getAsString());
                }
                return Map.copyOf(values);
            }
        } catch (java.io.IOException | RuntimeException failure) {
            return Map.of();
        }
    }
}
