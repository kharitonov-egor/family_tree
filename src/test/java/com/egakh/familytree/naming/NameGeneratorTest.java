package com.egakh.familytree.naming;

import com.egakh.familytree.data.AnimalRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NameGeneratorTest {
    @Test void farmAnimalsGetTheirSpeciesAndAShortCodeInsteadOfAPetName() {
        UUID id = UUID.fromString("3f2a1c00-0000-4000-8000-000000000001");
        assertEquals("Cow 3F2A1C", NameGenerator.farmName("minecraft:cow", id));
        assertEquals("Snow leopard 3F2A1C", NameGenerator.farmName("examplemod:snow_leopard", id));
        assertFalse(NameGenerator.isLegacyOverflowName(NameGenerator.farmName("examplemod:honey", id)));
    }

    @Test void farmNamesLeaveEveryPetNameFree() {
        List<AnimalRecord> farm = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            UUID id = new UUID(i * 0x1000_0000_0000L, i);
            farm.add(new AnimalRecord(id, "minecraft:cow", NameGenerator.farmName("minecraft:cow", id), true, null, null,
                    0, 0, false, null, null, null, null, null, null, null));
        }
        String puppy = NameGenerator.generate(UUID.randomUUID(), farm);
        assertFalse(puppy.matches(".*\\d.*"), "A pet name gained a number: " + puppy);
    }
}
