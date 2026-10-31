package com.egakh.familytree.settings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Reads typed values from the settings file. A value that can't be used falls back to the default, or the
 * nearest allowed number, and is reported once with the value Family Tree used instead.
 */
final class SettingsReader {
    static final Pattern SPECIES = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private final Properties properties;
    private final List<SettingProblem> problems = new ArrayList<>();
    private final Set<String> rejected = new LinkedHashSet<>();

    SettingsReader(Properties properties) {
        this.properties = properties;
    }

    List<SettingProblem> problems() {
        return List.copyOf(problems);
    }

    /** Keys whose written value Family Tree couldn't use as is. The file keeps their text for the admin to fix. */
    Set<String> rejected() {
        return Set.copyOf(rejected);
    }

    private void reject(String key, String message, Object... args) {
        problems.add(new SettingProblem(key, message, args));
        rejected.add(key);
    }

    private String raw(String key) {
        String value = properties.getProperty(key);
        return value == null ? null : value.trim();
    }

    boolean flag(String key, boolean fallback) {
        String value = raw(key);
        if (value == null) return fallback;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("true")) return true;
        if (lower.equals("false")) return false;
        reject(key, "familytree.config.not_boolean", key, value, Boolean.toString(fallback));
        return fallback;
    }

    /** Out-of-range numbers use the nearest limit, which is what 1.3.0 did for {@code farmRecordLimit}. */
    int number(String key, int fallback, int min, int max) {
        String value = raw(key);
        if (value == null) return fallback;
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            reject(key, "familytree.config.not_number", key, value, min, max, fallback);
            return fallback;
        }
        if (parsed < min || parsed > max) {
            int clamped = Math.max(min, Math.min(max, parsed));
            reject(key, "familytree.config.out_of_range", key, value, min, max, clamped);
            return clamped;
        }
        return parsed;
    }

    <E extends Enum<E>> E choice(String key, Class<E> type, E fallback) {
        String value = raw(key);
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            reject(key, "familytree.config.not_choice", key, value, choices(type), fallback.name());
            return fallback;
        }
    }

    static <E extends Enum<E>> String choices(Class<E> type) {
        List<String> names = new ArrayList<>();
        for (E constant : type.getEnumConstants()) names.add(constant.name());
        return String.join(", ", names);
    }

    String text(String key, String fallback, Predicate<String> valid, String message) {
        String value = raw(key);
        if (value == null) return fallback;
        if (valid.test(value)) return value;
        reject(key, message, key, value, fallback);
        return fallback;
    }

    /** Species ids without a namespace are vanilla, so {@code fox} means {@code minecraft:fox}. */
    Set<String> species(String key) {
        String value = raw(key);
        Set<String> ids = new LinkedHashSet<>();
        if (value == null || value.isEmpty()) return ids;
        for (String part : value.split(",")) {
            String id = part.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) continue;
            if (!id.contains(":")) id = "minecraft:" + id;
            if (SPECIES.matcher(id).matches()) ids.add(id);
            else reject(key, "familytree.config.bad_species", key, part.trim());
        }
        return ids;
    }

    List<String> aliases(String key, Pattern allowed) {
        String value = raw(key);
        List<String> aliases = new ArrayList<>();
        if (value == null) return aliases;
        for (String part : value.split(",")) {
            String alias = part.trim().toLowerCase(Locale.ROOT);
            if (alias.startsWith("/")) alias = alias.substring(1);
            if (alias.isEmpty() || alias.equals("familytree")) continue;
            if (!allowed.matcher(alias).matches()) {
                reject(key, "familytree.config.bad_alias", key, alias);
                continue;
            }
            if (!aliases.contains(alias)) aliases.add(alias);
        }
        return aliases;
    }
}
