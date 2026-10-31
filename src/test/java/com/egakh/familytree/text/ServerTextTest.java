package com.egakh.familytree.text;

import com.egakh.familytree.network.payloads.RelativesResult;
import com.egakh.familytree.network.payloads.SnapshotPagePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerTextTest {
    private static final String ARABIC = "كلب";

    @Test
    void rightToLeftNamesAreIsolatedOnlyForClientsWhoseFontHidesTheMarks() {
        Component death = Component.translatable("death.attack.cactus", ARABIC);
        Component line = Component.translatable("familytree.command.list.deceased_cause", ARABIC, "Wolf", 3, death);

        MutableComponent modded = ServerText.localize("en_us", line, false, true);
        Object[] args = ((TranslatableContents) modded.getContents()).getArgs();
        assertEquals("\u2068" + ARABIC + "\u2069", args[0]);
        assertEquals("Wolf", args[1]);
        assertEquals(3, args[2]);
        Object[] nested = ((TranslatableContents) ((Component) args[3]).getContents()).getArgs();
        assertEquals("\u2068" + ARABIC + "\u2069", nested[0]);

        Object[] vanilla = ((TranslatableContents) ServerText.localize("en_us", line).getContents()).getArgs();
        assertEquals(ARABIC, vanilla[0]);
        assertNotNull(((TranslatableContents) modded.getContents()).getFallback(), "Vanilla clients still get the English fallback");
    }

    @Test
    void familyTree130ClientsShareTheSnapshotChannelButLackTheFontThatHidesIsolates() {
        assertFalse(ServerText.hidesIsolates(type -> type == SnapshotPagePayload.TYPE));
        assertTrue(ServerText.hidesIsolates(type -> type == SnapshotPagePayload.TYPE || type == RelativesResult.TYPE));
    }

    private static Component locate() {
        return Component.translatable("familytree.command.locate.result", "Rex", Component.literal("1, 2, 3"),
                Component.translatable("familytree.dimension.overworld"));
    }

    @Test
    void automaticLanguageKeepsKeysForModdedClientsAndAddsAFallback() {
        MutableComponent localized = ServerText.localize("de_de", locate(), false);
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, localized.getContents());
        assertEquals("familytree.command.locate.result", contents.getKey());
        assertEquals(LangTables.template("de_de", "familytree.command.locate.result"), contents.getFallback());
    }

    @Test
    void aServerLanguageResolvesFamilyTreeTextSoModdedClientsShowItToo() {
        MutableComponent localized = ServerText.localize("ja_jp", locate(), true);
        assertTrue(translatableKeys(localized).isEmpty(), "a modded client would translate these itself: " + translatableKeys(localized));
        String expected = LangTables.template("ja_jp", "familytree.command.locate.result")
                .replace("%1$s", "Rex").replace("%2$s", "1, 2, 3")
                .replace("%3$s", LangTables.template("ja_jp", "familytree.dimension.overworld"));
        assertEquals(expected, localized.getString());
    }

    @Test
    void aServerLanguageLeavesMinecraftsOwnWordsToTheClient() {
        Component message = Component.translatable("familytree.command.list.alive", "Rex",
                Component.translatable("entity.minecraft.wolf"), 3, 4);
        MutableComponent localized = ServerText.localize("de_de", message, true);
        assertEquals(List.of("entity.minecraft.wolf"), translatableKeys(localized));
        assertTrue(localized.getString().startsWith("- Rex ["), localized.getString());
    }

    private static List<String> translatableKeys(Component component) {
        List<String> keys = new ArrayList<>();
        if (component.getContents() instanceof TranslatableContents translatable) {
            keys.add(translatable.getKey());
            for (Object arg : translatable.getArgs()) if (arg instanceof Component nested) keys.addAll(translatableKeys(nested));
        }
        for (Component sibling : component.getSiblings()) keys.addAll(translatableKeys(sibling));
        return keys;
    }
}
