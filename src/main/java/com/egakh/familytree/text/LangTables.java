package com.egakh.familytree.text;

import com.egakh.familytree.FamilyTreeMod;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The mod's own language files, read from the jar so the server can answer clients that lack them. */
public final class LangTables {
    public static final String DEFAULT = "en_us";
    /** Order matters for regional fallbacks: es_ar picks es_mx and zh_sg picks zh_cn. */
    public static final List<String> SHIPPED = List.of("en_us", "zh_cn", "zh_tw", "ru_ru", "uk_ua", "es_mx", "es_es",
            "pt_br", "de_de", "fr_fr", "it_it", "ja_jp", "ko_kr", "pl_pl", "tr_tr");
    private static final Map<String, Map<String, String>> TABLES = new ConcurrentHashMap<>();

    private LangTables() {}

    /** Picks the shipped language closest to a client's language code. */
    public static String resolve(String requested) {
        if (requested == null) return DEFAULT;
        String code = requested.trim().toLowerCase(Locale.ROOT);
        if (SHIPPED.contains(code)) return code;
        if (code.equals("zh_hk") || code.equals("zh_mo")) return "zh_tw";
        int separator = code.indexOf('_');
        String language = separator < 0 ? code : code.substring(0, separator);
        for (String shipped : SHIPPED) {
            if (shipped.startsWith(language + "_")) return shipped;
        }
        return DEFAULT;
    }

    /** The format string for a key in the resolved language, then English, or null when neither has it. */
    public static String template(String language, String key) {
        String value = table(resolve(language)).get(key);
        return value != null ? value : table(DEFAULT).get(key);
    }

    public static Map<String, String> table(String shipped) {
        if (!SHIPPED.contains(shipped)) return Map.of();
        return TABLES.computeIfAbsent(shipped, LangTables::load);
    }

    private static Map<String, String> load(String code) {
        String path = "/assets/" + FamilyTreeMod.MOD_ID + "/lang/" + code + ".json";
        try (InputStream in = LangTables.class.getResourceAsStream(path)) {
            if (in == null) return Map.of();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) values.put(entry.getKey(), entry.getValue().getAsString());
                return Map.copyOf(values);
            }
        } catch (IOException | RuntimeException failure) {
            FamilyTreeMod.LOGGER.warn("Could not read Family Tree language {}", code, failure);
            return Map.of();
        }
    }
}
