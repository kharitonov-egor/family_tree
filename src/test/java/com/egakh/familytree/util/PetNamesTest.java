package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
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
}
