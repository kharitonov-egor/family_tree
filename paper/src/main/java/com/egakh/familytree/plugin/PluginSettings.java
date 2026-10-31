package com.egakh.familytree.plugin;

import com.egakh.familytree.settings.ServerSettings;
import com.egakh.familytree.settings.SettingProblem;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code plugins/FamilyTree/config.yml}. It holds the mod's server settings under the same names, read and checked
 * by the mod's {@link ServerSettings}, plus {@value #CLIENT_MOD}, which only the plugin has.
 */
public final class PluginSettings {
    public static final String FILE_NAME = "config.yml";
    public static final String CLIENT_MOD = "clientMod";
    /** Bukkit only tells plugins whether a player is an operator, not at which level. */
    static final Set<String> NOT_ON_PLUGIN = Set.of("operatorLevel");
    private static final Pattern PLAIN = Pattern.compile("[A-Za-z0-9_.,-]+");
    private static final Set<String> YAML_WORDS = Set.of("y", "n", "yes", "no", "on", "off", "null", "~");

    private static final List<String> HEADER = List.of(
            "Family Tree plugin settings",
            "",
            "Family Tree reads this file when the server starts and on /familytree reload, which needs the",
            "familytree.reload permission. It reports values it can't use in the log and to the admin who reloaded,",
            "uses the default for them, and keeps your text here so you can fix it. It rewrites this file to add",
            "missing settings and these comments, so comments you add are not kept.",
            "",
            "A permission plugin such as LuckPerms can grant or deny each familytree.* permission node per player or",
            "group. The settings below decide what players get when no permission plugin sets a node.",
            "The settings match the mod's config/familytree-server.properties, so a server can switch between them.");

    public record Loaded(ServerSettings server, boolean clientMod, List<SettingProblem> problems, boolean readFailed) {}

    private PluginSettings() {}

    public static Loaded load(Path file, Loaded previous) {
        String text = null;
        YamlConfiguration yaml = new YamlConfiguration();
        if (Files.exists(file)) {
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
                yaml.loadFromString(text);
            } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
                return new Loaded(previous.server(), previous.clientMod(), List.of(), true);
            }
        }
        Map<String, String> written = new LinkedHashMap<>();
        for (String key : yaml.getKeys(false)) written.put(key, scalar(yaml.get(key)));
        Properties properties = new Properties();
        written.forEach((key, value) -> {
            if (!key.equals(CLIENT_MOD)) properties.setProperty(key, value);
        });
        ServerSettings.Parsed parsed = ServerSettings.parse(properties);
        List<SettingProblem> problems = new ArrayList<>(parsed.problems());
        boolean clientMod = true;
        Set<String> rejected = new java.util.HashSet<>(parsed.rejected());
        String rawClient = written.get(CLIENT_MOD);
        if (rawClient != null) {
            String value = rawClient.trim().toLowerCase(java.util.Locale.ROOT);
            if (value.equals("false")) clientMod = false;
            else if (!value.equals("true")) {
                problems.add(new SettingProblem(CLIENT_MOD, "familytree.config.not_boolean", CLIENT_MOD, rawClient, "true"));
                rejected.add(CLIENT_MOD);
            }
        }
        String rendered = render(parsed.settings(), clientMod, written, rejected, parsed.unknown());
        if (!rendered.equals(text)) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, rendered, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // The settings still apply; the next load tries to write the file again.
            }
        }
        return new Loaded(parsed.settings(), clientMod, List.copyOf(problems), false);
    }

    public static Loaded defaults() {
        return new Loaded(ServerSettings.DEFAULT, true, List.of(), false);
    }

    public static boolean isDefault(ServerSettings current, ServerSettings.Setting setting) {
        return Objects.equals(setting.value().apply(current), setting.defaultValue());
    }

    static String render(ServerSettings settings, boolean clientMod, Map<String, String> written,
                         Set<String> rejected, Set<String> unknown) {
        StringBuilder out = new StringBuilder();
        for (String line : HEADER) out.append(line.isEmpty() ? "#" : "# " + line).append('\n');
        String section = null;
        for (ServerSettings.Setting setting : ServerSettings.SETTINGS) {
            if (NOT_ON_PLUGIN.contains(setting.key())) continue;
            if (!setting.section().equals(section)) {
                section = setting.section();
                out.append("\n# ==== ").append(section).append(" ====\n");
            }
            String value = rejected.contains(setting.key()) && written.containsKey(setting.key())
                    ? written.get(setting.key()) : setting.value().apply(settings);
            entry(out, setting.comment(), setting.allowed(), setting.defaultValue(), setting.key(), value);
        }
        out.append("\n# ==== Paper and Spigot ====\n");
        entry(out, List.of("Whether players with the Family Tree client mod get its screens on this server.",
                        "false sends them to the chest menu like everyone else."),
                "true, false", "true", CLIENT_MOD,
                rejected.contains(CLIENT_MOD) ? written.get(CLIENT_MOD) : Boolean.toString(clientMod));
        List<String> extra = new ArrayList<>();
        for (String key : unknown) if (!NOT_ON_PLUGIN.contains(key)) extra.add(key);
        if (!extra.isEmpty()) {
            out.append("\n# ==== Not recognized ====\n");
            out.append("# Family Tree doesn't use these settings. Check their spelling against the settings above.\n");
            for (String key : extra) out.append(key(key)).append(": ").append(value(written.getOrDefault(key, ""))).append('\n');
        }
        return out.toString();
    }

    private static void entry(StringBuilder out, List<String> comment, String allowed, String fallback, String key, String value) {
        out.append('\n');
        for (String line : comment) out.append("# ").append(line).append('\n');
        out.append("# Allowed: ").append(allowed).append(". Default: ").append(fallback.isEmpty() ? "empty" : fallback).append('\n');
        out.append(key(key)).append(": ").append(value(value)).append('\n');
    }

    private static String key(String key) {
        return PLAIN.matcher(key).matches() ? key : quote(key);
    }

    private static String value(String value) {
        if (value.equals("true") || value.equals("false")) return value;
        if (PLAIN.matcher(value).matches() && !YAML_WORDS.contains(value.toLowerCase(java.util.Locale.ROOT))) return value;
        return quote(value);
    }

    private static String quote(String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    /** Lists from hand-edited YAML become the comma-separated form the settings use. */
    private static String scalar(Object value) {
        if (value == null) return "";
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>(list.size());
            for (Object element : list) parts.add(String.valueOf(element).trim());
            return String.join(",", parts);
        }
        return String.valueOf(value).trim();
    }
}
