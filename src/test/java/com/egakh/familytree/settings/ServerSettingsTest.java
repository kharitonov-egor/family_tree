package com.egakh.familytree.settings;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ServerSettingsTest {
    /** What Family Tree 1.3.0 wrote with Properties.store, including its escaped colons. */
    private static final String CONFIG_1_3_0 = String.join("\n",
            "#Family Tree server settings.",
            "#viewPolicy = OP_ONLY_ALL | EVERYONE_ALL | OWN_ONLY",
            "#vanillaMenu = true | false\\: open chest menus for players without the client mod.",
            "#Thu Oct 29 18:02:11 UTC 2026",
            "trackedSpecies=",
            "farmRecordLimit=5000",
            "viewPolicy=OP_ONLY_ALL",
            "commandAliases=",
            "autoPruneFarmRecords=false",
            "trackFarmAnimals=true",
            "ignoredSpecies=",
            "trackWildlife=true",
            "vanillaMenu=true",
            "trackModdedAnimals=true", "");

    private static Properties load(String text) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        return properties;
    }

    private static Map<String, String> written(Properties properties) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) values.put(key, properties.getProperty(key).trim());
        return values;
    }

    private static String rewrite(String text) throws IOException {
        Properties properties = load(text);
        ServerSettings.Parsed parsed = ServerSettings.parse(properties);
        return parsed.settings().fileText(written(properties), parsed.rejected(), parsed.unknown());
    }

    @Test void aDefault130FileLoadsAsTheDefaultsWithoutProblems() throws IOException {
        ServerSettings.Parsed parsed = ServerSettings.parse(load(CONFIG_1_3_0));
        assertEquals(ServerSettings.DEFAULT, parsed.settings());
        assertEquals(List.of(), parsed.problems());
        assertEquals(Set.of(), parsed.unknown());
    }

    @Test void aCustomized130FileKeepsEveryValue() throws IOException {
        String text = CONFIG_1_3_0.replace("viewPolicy=OP_ONLY_ALL", "viewPolicy=EVERYONE_ALL")
                .replace("commandAliases=", "commandAliases=pets,ft")
                .replace("trackedSpecies=", "trackedSpecies=minecraft\\:fox")
                .replace("ignoredSpecies=", "ignoredSpecies=minecraft\\:bee,naturalist\\:snail")
                .replace("trackFarmAnimals=true", "trackFarmAnimals=false")
                .replace("farmRecordLimit=5000", "farmRecordLimit=1200")
                .replace("autoPruneFarmRecords=false", "autoPruneFarmRecords=true")
                .replace("vanillaMenu=true", "vanillaMenu=false");
        ServerSettings.Parsed parsed = ServerSettings.parse(load(text));
        ServerSettings settings = parsed.settings();
        assertEquals(List.of(), parsed.problems());
        assertEquals(ServerSettings.ViewPolicy.EVERYONE_ALL, settings.viewPolicy());
        assertEquals(List.of("pets", "ft"), settings.commandAliases());
        assertFalse(settings.vanillaMenu());
        assertEquals(new TrackingRules(false, true, true, Set.of("minecraft:fox"), Set.of("minecraft:bee", "naturalist:snail"),
                1200, true), settings.tracking());
        assertEquals(ServerSettings.Cooldowns.DEFAULT, settings.cooldowns());
        assertEquals(2, settings.operatorLevel());

        ServerSettings again = ServerSettings.parse(load(rewrite(text))).settings();
        assertEquals(settings, again, "The rewritten file must load to the same settings");
    }

    @Test void theGeneratedFileDocumentsEverySettingWithItsDefaultAndAllowedValues() {
        String text = ServerSettings.DEFAULT.fileText(Map.of(), Set.of(), Set.of());
        Set<String> keys = new HashSet<>();
        for (ServerSettings.Setting setting : ServerSettings.SETTINGS) {
            assertTrue(keys.add(setting.key()), "Duplicate setting " + setting.key());
            int line = text.indexOf("\n" + setting.key() + "=");
            assertTrue(line > 0, setting.key() + " is missing");
            String before = text.substring(text.lastIndexOf("\n\n", line), line);
            assertTrue(before.contains("# Allowed: " + setting.allowed() + ". Default: "), setting.key() + " lacks allowed values");
            assertFalse(setting.comment().isEmpty(), setting.key() + " lacks a comment");
        }
    }

    @Test void theGeneratedFileLoadsBackToTheDefaultsAndRewritesToItself() throws IOException {
        String text = ServerSettings.DEFAULT.fileText(Map.of(), Set.of(), Set.of());
        ServerSettings.Parsed parsed = ServerSettings.parse(load(text));
        assertEquals(ServerSettings.DEFAULT, parsed.settings());
        assertEquals(List.of(), parsed.problems());
        assertEquals(text, rewrite(text), "An unchanged file must not be rewritten");
    }

    @Test void invalidValuesUseDefaultsAndKeepTheirTextForTheAdminToFix() throws IOException {
        String text = String.join("\n", "viewPolicy=everyone", "vanillaMenu=yes", "operatorLevel=9",
                "scanCooldownMillis=soon", "listLimit=0", "linkingStickName=", "serverLanguage=english",
                "trackedSpecies=minecraft:fox,not a species", "commandAliases=pets,Ft!", "");
        ServerSettings.Parsed parsed = ServerSettings.parse(load(text));
        ServerSettings settings = parsed.settings();
        assertEquals(ServerSettings.ViewPolicy.OP_ONLY_ALL, settings.viewPolicy());
        assertTrue(settings.vanillaMenu());
        assertEquals(4, settings.operatorLevel(), "Out-of-range numbers use the nearest limit");
        assertEquals(2_000, settings.cooldowns().scan());
        assertEquals(1, settings.listLimit());
        assertEquals("familytree", settings.linkingStickName());
        assertEquals("auto", settings.serverLanguage());
        assertEquals(Set.of("minecraft:fox"), settings.tracking().trackedSpecies());
        assertEquals(List.of("pets"), settings.commandAliases());

        Map<String, String> messages = new LinkedHashMap<>();
        for (SettingProblem problem : parsed.problems()) messages.put(problem.key(), problem.message());
        assertEquals(Map.of("viewPolicy", "familytree.config.not_choice", "vanillaMenu", "familytree.config.not_boolean",
                "operatorLevel", "familytree.config.out_of_range", "scanCooldownMillis", "familytree.config.not_number",
                "listLimit", "familytree.config.out_of_range", "linkingStickName", "familytree.config.bad_name",
                "serverLanguage", "familytree.config.bad_language", "trackedSpecies", "familytree.config.bad_species",
                "commandAliases", "familytree.config.bad_alias"), messages);

        String rewritten = rewrite(text);
        assertTrue(rewritten.contains("\nviewPolicy=everyone\n"));
        assertTrue(rewritten.contains("\noperatorLevel=9\n"));
        assertTrue(rewritten.contains("\ntrackedSpecies=minecraft:fox,not a species\n"));
        assertTrue(rewritten.contains("\nfarmRecordLimit=5000\n"), "Missing settings are added with their defaults");
    }

    @Test void problemsNameTheValueFamilyTreeUsesInstead() throws IOException {
        SettingProblem problem = ServerSettings.parse(load("farmRecordLimit=250000\n")).problems().get(0);
        assertEquals("familytree.config.out_of_range", problem.message());
        assertEquals(List.of("farmRecordLimit", "250000", 0, TrackingRules.MAX_FARM_LIMIT, TrackingRules.MAX_FARM_LIMIT), problem.args());
    }

    @Test void unknownKeysAreReportedAndKeptAtTheEnd() throws IOException {
        String text = "viewpolicy=EVERYONE_ALL\nvanillaMenu=false\n";
        ServerSettings.Parsed parsed = ServerSettings.parse(load(text));
        assertEquals(Set.of("viewpolicy"), parsed.unknown());
        assertEquals(ServerSettings.ViewPolicy.OP_ONLY_ALL, parsed.settings().viewPolicy());
        assertTrue(parsed.problems().stream().anyMatch(p -> p.message().equals("familytree.config.unknown_key")));
        String rewritten = rewrite(text);
        assertTrue(rewritten.indexOf("\nviewpolicy=EVERYONE_ALL\n") > rewritten.indexOf("Not recognized"));
        assertEquals(rewritten, rewrite(rewritten));
    }

    @Test void newSettingsReadTheirValues() throws IOException {
        String text = String.join("\n", "operatorLevel=3", "ownersCanLocate=false", "ownersCanSetAge=false",
                "listLimit=25", "openCooldownMillis=0", "serverScanCooldownMillis=10000", "editCooldownMillis=1000",
                "linkingStick=false", "linkingStickName=Pedigree Wand", "linkingSelectionSeconds=30",
                "serverLanguage=DE_DE", "farmLimitMessage=false", "locateTeleportLink=false", "discoverPetsOnLoad=false", "");
        ServerSettings.Parsed parsed = ServerSettings.parse(load(text));
        assertEquals(List.of(), parsed.problems());
        ServerSettings settings = parsed.settings();
        assertEquals(3, settings.operatorLevel());
        assertEquals(new ServerSettings.OwnerActions(false, true, true, false), settings.ownerActions());
        assertEquals(25, settings.listLimit());
        assertEquals(new ServerSettings.Cooldowns(0, 2_000, 10_000, 250, 1_000, 200), settings.cooldowns());
        assertFalse(settings.linkingStick());
        assertEquals("Pedigree Wand", settings.linkingStickName());
        assertEquals(30, settings.linkingSelectionSeconds());
        assertEquals("de_de", settings.serverLanguage());
        assertFalse(settings.farmLimitMessage());
        assertFalse(settings.locateTeleportLink());
        assertFalse(settings.discoverPetsOnLoad());
    }

    @Test void valuesWithSpecialCharactersSurviveTheRewrite() throws IOException {
        String text = "linkingStickName=Árbol \\: genealógico #1\n";
        ServerSettings settings = ServerSettings.parse(load(text)).settings();
        assertEquals("Árbol : genealógico #1", settings.linkingStickName());
        assertEquals(settings, ServerSettings.parse(load(rewrite(text))).settings());
    }

    @Test
    void reloadCountsOnlyProblemsAndSaysOneProblemInTheSingular() {
        assertEquals("familytree.reload.no_problems", SettingProblem.summaryKey(0));
        assertEquals("familytree.reload.one_problem", SettingProblem.summaryKey(1));
        assertEquals("familytree.reload.problems", SettingProblem.summaryKey(2));
    }
}
