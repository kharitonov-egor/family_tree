package com.egakh.familytree.plugin;

import com.egakh.familytree.settings.ServerSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PluginSettingsTest {
    @TempDir Path temp;

    @Test void writesEverySharedSettingWithItsCommentAndDefault() throws Exception {
        Path file = temp.resolve("config.yml");
        PluginSettings.Loaded loaded = PluginSettings.load(file, PluginSettings.defaults());
        assertEquals(ServerSettings.DEFAULT, loaded.server());
        assertTrue(loaded.clientMod());
        assertTrue(loaded.problems().isEmpty(), loaded.problems().toString());
        String text = Files.readString(file);
        for (ServerSettings.Setting setting : ServerSettings.SETTINGS) {
            if (PluginSettings.NOT_ON_PLUGIN.contains(setting.key())) assertFalse(text.contains("\n" + setting.key() + ":"));
            else assertTrue(text.contains("\n" + setting.key() + ":"), setting.key());
        }
        assertTrue(text.contains("\nclientMod: true"));
        assertEquals(text, Files.readString(writeAgain(file)), "a second load leaves the file unchanged");
    }

    @Test void readsYamlListsKeepsBadValuesAndUnknownKeys() throws Exception {
        Path file = temp.resolve("config.yml");
        Files.writeString(file, """
                viewPolicy: EVERYONE_ALL
                commandAliases: [pets, ft]
                trackedSpecies:
                  - minecraft:fox
                listLimit: lots
                clientMod: false
                vanilaMenu: false
                """);
        PluginSettings.Loaded loaded = PluginSettings.load(file, PluginSettings.defaults());
        assertEquals(ServerSettings.ViewPolicy.EVERYONE_ALL, loaded.server().viewPolicy());
        assertEquals(java.util.List.of("pets", "ft"), loaded.server().commandAliases());
        assertTrue(loaded.server().tracking().trackedSpecies().contains("minecraft:fox"));
        assertEquals(ServerSettings.DEFAULT.listLimit(), loaded.server().listLimit());
        assertFalse(loaded.clientMod());
        assertEquals(2, loaded.problems().size(), loaded.problems().toString());
        String text = Files.readString(file);
        assertTrue(text.contains("listLimit: lots"), "an unusable value keeps the admin's text");
        assertTrue(text.contains("vanilaMenu: 'false'") || text.contains("vanilaMenu: false"), text);
        assertTrue(text.contains("Not recognized"));
        assertTrue(text.contains("commandAliases: 'pets,ft'") || text.contains("commandAliases: pets,ft"));
    }

    @Test void anUnreadableFileKeepsTheCurrentSettings() throws Exception {
        Path file = temp.resolve("config.yml");
        Files.writeString(file, "viewPolicy: [unclosed");
        PluginSettings.Loaded previous = new PluginSettings.Loaded(ServerSettings.DEFAULT, false, java.util.List.of(), false);
        PluginSettings.Loaded loaded = PluginSettings.load(file, previous);
        assertTrue(loaded.readFailed());
        assertFalse(loaded.clientMod());
        assertEquals("viewPolicy: [unclosed", Files.readString(file));
    }

    private static Path writeAgain(Path file) {
        PluginSettings.load(file, PluginSettings.defaults());
        return file;
    }
}
