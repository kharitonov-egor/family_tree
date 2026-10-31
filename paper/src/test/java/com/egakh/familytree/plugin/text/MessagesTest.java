package com.egakh.familytree.plugin.text;

import com.egakh.familytree.text.LangTables;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    @Test void templatesFollowMinecraftsFormat() {
        assertEquals(List.of("a ", "X", " b ", "Y"), Templates.split("a %s b %s", List.of("X", "Y")));
        assertEquals(List.of("Y", " then ", "X"), Templates.split("%2$s then %1$s", List.of("X", "Y")));
        assertEquals(List.of("100", "%", " ", "X"), Templates.split("100%% %s", List.of("X")));
        assertEquals(List.of("odd %d"), Templates.split("odd %d", List.of("X")));
    }

    @Test void familyTreeKeysResolveOnTheServerAndVanillaKeysStayTranslatable() {
        Text line = Messages.tr("de_de", "familytree.command.list.alive", "Rex", Text.vanilla("entity.minecraft.wolf"), 3, 9);
        String plain = line.plain(key -> key.equals("entity.minecraft.wolf") ? "Wolf" : key);
        assertTrue(plain.contains("Rex") && plain.contains("Wolf") && plain.contains("9"), plain);
        assertNotEquals(Messages.plain("en_us", "familytree.menu.close"), Messages.plain("ja_jp", "familytree.menu.close"));
        assertTrue(line.children().stream().anyMatch(child -> "entity.minecraft.wolf".equals(child.key())));
    }

    @Test void pluginStringsFallBackToEnglishAndUnknownKeysStayVisible() {
        assertTrue(Messages.plain("xx_yy", "familytree.plugin.prompt.search").contains("cancel"));
        assertEquals("familytree.no.such.key", Messages.plain("en_us", "familytree.no.such.key"));
    }

    /** Like the mod's LangParityTest, for the strings only the plugin has. */
    @Test void everyPluginLanguageHasTheSameKeysAndArguments() throws Exception {
        JsonObject english = table("en_us");
        for (String code : LangTables.SHIPPED) {
            JsonObject table = table(code);
            assertEquals(english.keySet(), table.keySet(), code);
            for (String key : english.keySet())
                assertEquals(arguments(english.get(key).getAsString()), arguments(table.get(key).getAsString()), code + " " + key);
        }
    }

    private static JsonObject table(String code) throws Exception {
        try (var in = MessagesTest.class.getResourceAsStream("/familytree-plugin/lang/" + code + ".json")) {
            assertNotNull(in, code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Set<String> arguments(String template) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = Pattern.compile("%(\\d+\\$)?s").matcher(template);
        int next = 1;
        while (matcher.find()) found.add(matcher.group(1) == null ? (next++) + "$" : matcher.group(1));
        return found;
    }
}
