package com.egakh.familytree.settings;

import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.platform.FamilyTreePaths;
import com.egakh.familytree.text.LangTables;
import com.egakh.familytree.text.ServerText;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class FamilyTreeServerSettings {
    public static final String FILE_NAME = "familytree-server.properties";

    private static volatile ServerSettings current = ServerSettings.DEFAULT;
    private static volatile boolean loaded;
    private static volatile List<String> registeredAliases;

    private FamilyTreeServerSettings() {}

    /** The outcome of reading the file. {@code readFailed} means the previous settings stayed in place. */
    public record LoadResult(Path path, boolean readFailed, List<SettingProblem> problems) {}

    public static Path path() {
        return FamilyTreePaths.config().resolve(FILE_NAME);
    }

    public static synchronized LoadResult load() {
        Path path = path();
        String text = null;
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try {
                text = decode(Files.readAllBytes(path));
                properties.load(new StringReader(text));
            } catch (IOException | IllegalArgumentException failure) {
                FamilyTreeMod.LOGGER.warn("Failed to read {}. Family Tree keeps its {} settings.", path,
                        loaded ? "current" : "default", failure);
                loaded = true;
                return new LoadResult(path, true, List.of());
            }
        }
        ServerSettings.Parsed parsed = ServerSettings.parse(properties);
        List<SettingProblem> problems = new ArrayList<>(parsed.problems());
        problems.addAll(notes(parsed.settings()));
        if (registeredAliases != null && !registeredAliases.equals(parsed.settings().commandAliases()))
            problems.add(new SettingProblem("commandAliases", "familytree.config.restart", "commandAliases"));
        current = parsed.settings();
        loaded = true;

        Map<String, String> written = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) written.put(key, properties.getProperty(key).trim());
        String rendered = parsed.settings().fileText(written, parsed.rejected(), parsed.unknown());
        if (!rendered.equals(text)) {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, rendered, StandardCharsets.UTF_8);
            } catch (IOException failure) {
                FamilyTreeMod.LOGGER.warn("Failed to write {}", path, failure);
            }
        }
        for (SettingProblem problem : problems)
            FamilyTreeMod.LOGGER.warn("{}: {}", path.getFileName(), ServerText.plain(LangTables.DEFAULT, problem.message(), problem.arguments()));
        return new LoadResult(path, false, List.copyOf(problems));
    }

    /** 1.3.0 wrote the file as ISO-8859-1 with escapes, so files that aren't valid UTF-8 are read that way. */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /** Values that work but are likely mistakes: species Minecraft doesn't know, and languages without a translation. */
    private static List<SettingProblem> notes(ServerSettings settings) {
        List<SettingProblem> notes = new ArrayList<>();
        for (String key : List.of(TrackingRules.TRACKED, TrackingRules.IGNORED)) {
            var ids = key.equals(TrackingRules.TRACKED) ? settings.tracking().trackedSpecies() : settings.tracking().ignoredSpecies();
            for (String id : new java.util.TreeSet<>(ids)) {
                Identifier parsed = Identifier.tryParse(id);
                if (parsed == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(parsed))
                    notes.add(new SettingProblem(key, "familytree.config.unknown_species", key, id));
            }
        }
        String language = settings.serverLanguage();
        if (!language.equals(ServerSettings.AUTO_LANGUAGE) && !LangTables.resolve(language).equals(language))
            notes.add(new SettingProblem("serverLanguage", "familytree.config.language_fallback", language, LangTables.resolve(language)));
        return notes;
    }

    public static ServerSettings current() {
        return current;
    }

    public static ServerSettings.ViewPolicy viewPolicy() {
        return current.viewPolicy();
    }

    public static boolean vanillaMenu() {
        return current.vanillaMenu();
    }

    /** Commands register before the server starts, so this reads the file on first use. */
    public static List<String> commandAliases() {
        if (!loaded) load();
        registeredAliases = current.commandAliases();
        return registeredAliases;
    }

    public static TrackingRules tracking() {
        return current.tracking();
    }

    public static String language() {
        return current.serverLanguage();
    }

    public static boolean isDefault(ServerSettings.Setting setting) {
        return Objects.equals(setting.value().apply(current), setting.defaultValue());
    }
}
