package com.egakh.familytree.plugin;

import com.egakh.familytree.plugin.network.Payloads;
import com.egakh.familytree.settings.ServerSettings;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChatTest {
    private static final String ARABIC = "كلب";

    @Test
    void rightToLeftNamesAreIsolatedOnlyForPlayersWithTheMod() {
        String modded = new Chat(null, () -> ServerSettings.DEFAULT, sender -> true)
                .tr(null, "familytree.command.locate.result", ARABIC, "1, 2, 3", "overworld").plain(key -> key);
        String vanilla = new Chat(null, () -> ServerSettings.DEFAULT, sender -> false)
                .tr(null, "familytree.command.locate.result", ARABIC, "1, 2, 3", "overworld").plain(key -> key);

        assertEquals("\u2068" + ARABIC + "\u2069 is at 1, 2, 3 in overworld", modded);
        assertEquals(ARABIC + " is at 1, 2, 3 in overworld", vanilla);
    }

    @Test
    void familyTree130ClientsShareTheSnapshotChannelButLackTheFontThatHidesIsolates() {
        assertFalse(Payloads.hidesIsolates(Set.of(Payloads.SNAPSHOT)));
        assertTrue(Payloads.hidesIsolates(Set.of(Payloads.SNAPSHOT, Payloads.RELATIVES_RESULT)));
    }
}
