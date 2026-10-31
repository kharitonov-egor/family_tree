package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.text.LangTables;
import com.ibm.icu.text.Bidi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class PetNamesTest {
    private final UUID alex = UUID.randomUUID();
    private final UUID kai = UUID.randomUUID();
    private final AnimalRecord rex = pet("Rex", alex);
    private final AnimalRecord twinA = pet("Twin", alex);
    private final AnimalRecord twinB = pet("Twin", alex);
    private final AnimalRecord spaced = pet("Treename Test", alex);
    private final List<AnimalRecord> records = List.of(rex, twinA, twinB, spaced);

    private static AnimalRecord pet(String name, UUID owner) {
        return new AnimalRecord(UUID.randomUUID(), "minecraft:wolf", name, false,
                null, null, 0, 0, false, null, null, owner, "Owner", null, null, null);
    }

    private Predicate<AnimalRecord> owns(UUID player) {
        return pet -> player.equals(pet.ownerId());
    }

    @Test
    void aPetThePlayerSeesButCannotManageIsRefusedNotUnknown() {
        PetNames.Lookup lookup = PetNames.find(records, "Rex", pet -> true, owns(kai));
        assertEquals(PetNames.Outcome.NOT_ALLOWED, lookup.outcome());
        assertNull(lookup.pet());
    }

    @Test
    void aPetThePlayerCannotSeeStaysUnknown() {
        assertEquals(PetNames.Outcome.UNKNOWN, PetNames.find(records, "Rex", owns(kai), owns(kai)).outcome());
        assertEquals(PetNames.Outcome.UNKNOWN, PetNames.find(records, "Nobody", pet -> true, pet -> true).outcome());
    }

    @Test
    void duplicateNamesAreAmbiguousAndAnExactUuidPicksOne() {
        PetNames.Lookup twins = PetNames.find(records, "twin", pet -> true, owns(alex));
        assertEquals(PetNames.Outcome.AMBIGUOUS, twins.outcome());
        assertEquals(2, twins.matches().size());
        assertNull(twins.pet());
        assertSame(twinB, PetNames.find(records, twinB.id().toString(), pet -> true, owns(alex)).pet());
    }

    @Test
    void oneMatchIsFound() {
        PetNames.Lookup lookup = PetNames.find(records, "rex", pet -> true, owns(alex));
        assertEquals(PetNames.Outcome.FOUND, lookup.outcome());
        assertSame(rex, lookup.pet());
    }

    @Test
    void quotedNamesFromGreedyArgumentsMatch() {
        assertSame(spaced, PetNames.find(records, "\"Treename Test\"", pet -> true, pet -> true).pet());
        assertSame(spaced, PetNames.find(records, "'Treename Test'", pet -> true, pet -> true).pet());
        assertSame(rex, PetNames.find(records, "\"Rex\"", pet -> true, pet -> true).pet());
    }

    @Test
    void aNameThatReallyHasQuotesStillMatchesAsTyped() {
        AnimalRecord quoted = pet("\"Rex\"", alex);
        assertSame(quoted, PetNames.find(List.of(rex, quoted), "\"Rex\"", pet -> true, pet -> true).pet());
    }

    @Test
    void unquoteStripsOnePairAndBrigadierEscapes() {
        assertEquals("Treename Test", PetNames.unquote("\"Treename Test\""));
        assertEquals("Big \"Rex\"", PetNames.unquote("\"Big \\\"Rex\\\"\""));
        assertEquals("back\\slash", PetNames.unquote("\"back\\\\slash\""));
        assertEquals("\"Rex\"", PetNames.unquote("\"\"Rex\"\""));
        assertEquals("Rex", PetNames.unquote("  Rex  "));
        assertEquals("\"Rex", PetNames.unquote("\"Rex"));
        assertEquals("'Rex\"", PetNames.unquote("'Rex\""));
        assertEquals("\"", PetNames.unquote("\""));
        assertEquals("", PetNames.unquote("\"\""));
    }

    private static final String ARABIC = "كلب سعيد";

    @Test
    void onlyNamesThatCouldReorderASentenceGetIsolated() {
        assertEquals("Rex", PetNames.isolate("Rex"));
        assertEquals("小白 🐶 Ünïcødé", PetNames.isolate("小白 🐶 Ünïcødé"));
        assertEquals("\u2068" + ARABIC + "\u2069", PetNames.isolate(ARABIC));
        assertEquals("\u2068Rex\u202E\u2069", PetNames.isolate("Rex\u202E"), "An unclosed override must not leak into the sentence");
        assertNull(PetNames.isolate(null));
    }

    @Test
    void controlsInsideANameCannotCloseItsIsolate() {
        assertEquals("\u2068" + ARABIC + "\u2069", PetNames.isolate("\u2069" + ARABIC));
        assertEquals("\u2068Rex\u2067" + ARABIC + "\u2069\u2069", PetNames.isolate("Rex\u2067" + ARABIC));

        String template = LangTables.template("en_us", "familytree.kin.parent");
        Bidi bidi = new Bidi(String.format(template, PetNames.isolate("\u2069" + ARABIC), "Cocoa"), Bidi.LEVEL_DEFAULT_LTR);
        assertEquals(0, bidi.getParaLevel());
        assertTrue(bidi.writeReordered(Bidi.DO_MIRRORING | Bidi.REMOVE_BIDI_CONTROLS).endsWith(" is Cocoa's parent."));
    }

    /** Minecraft orders text with ICU, where the first strong letter decides a line's direction. */
    @Test
    void anIsolatedRightToLeftNameKeepsTheKinshipSentenceLeftToRight() {
        String template = LangTables.template("en_us", "familytree.kin.parent");

        Bidi broken = new Bidi(String.format(template, ARABIC, "Cocoa"), Bidi.LEVEL_DEFAULT_LTR);
        assertEquals(1, broken.getParaLevel(), "Without isolates the Arabic name turns the sentence right to left");
        assertFalse(broken.writeReordered(Bidi.DO_MIRRORING).endsWith("parent."));

        Bidi fixed = new Bidi(String.format(template, PetNames.isolate(ARABIC), "Cocoa"), Bidi.LEVEL_DEFAULT_LTR);
        assertEquals(0, fixed.getParaLevel());
        assertTrue(fixed.writeReordered(Bidi.DO_MIRRORING | Bidi.REMOVE_BIDI_CONTROLS).endsWith(" is Cocoa's parent."));
    }
}
