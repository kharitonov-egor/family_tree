package com.egakh.familytree.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** QA E5: thorns damage without an attacker printed "%1$s was killed while trying to hurt %2$s". */
class DeathMessageKeysTest {
    private static final Map<String, String> SAMPLE = Map.of(
            "death.attack.thorns", "%1$s was killed while trying to hurt %2$s",
            "death.attack.arrow", "%1$s was shot by %2$s",
            "death.attack.fall", "%1$s hit the ground too hard",
            "death.attack.generic", "%1$s died",
            "death.attack.old", "%s was slain by %s");

    @Test
    void messageThatNamesAnAttackerFallsBackWhenNoneWasRecorded() {
        assertEquals(DeathMessageKeys.GENERIC, DeathMessageKeys.key("thorns", false, SAMPLE::get));
        assertEquals(DeathMessageKeys.GENERIC, DeathMessageKeys.key("arrow", false, SAMPLE::get));
        assertEquals(DeathMessageKeys.GENERIC, DeathMessageKeys.key("old", false, SAMPLE::get));
    }

    @Test
    void recordedAttackerKeepsTheSpecificMessage() {
        assertEquals("death.attack.thorns", DeathMessageKeys.key("thorns", true, SAMPLE::get));
        assertEquals("death.attack.fall", DeathMessageKeys.key("fall", false, SAMPLE::get));
        assertEquals("death.attack.fall", DeathMessageKeys.key("fall", true, SAMPLE::get));
    }

    @Test
    void unknownMessageUsesGeneric() {
        assertEquals(DeathMessageKeys.GENERIC, DeathMessageKeys.key("modded_saw", true, SAMPLE::get));
    }

    @Test
    void noVanillaDeathMessageKeepsAPlaceholderWithoutAnAttacker() throws Exception {
        Map<String, String> english = vanillaEnglish();
        assumeTrue(english != null, "Minecraft's en_us.json is not on the test classpath");
        assertEquals(DeathMessageKeys.GENERIC, DeathMessageKeys.key("thorns", false, english::get));
        int checked = 0;
        for (String key : english.keySet()) {
            if (!key.startsWith("death.attack.")) continue;
            String chosen = DeathMessageKeys.key(key.substring("death.attack.".length()), false, english::get);
            String text = english.get(chosen);
            assertFalse(text.contains("%2$s"), key + " still reads " + text);
            checked++;
        }
        assertTrue(checked > 40, "Only " + checked + " death messages found");
    }

    private static Map<String, String> vanillaEnglish() throws Exception {
        try (InputStream in = DeathMessageKeysTest.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
            if (in == null) return null;
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet())
                if (entry.getValue().isJsonPrimitive()) values.put(entry.getKey(), entry.getValue().getAsString());
            return values;
        }
    }
}
