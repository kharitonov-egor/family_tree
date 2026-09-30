package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class PetNames {
    private PetNames() {}

    public static List<AnimalRecord> matches(Collection<AnimalRecord> records, String name,
                                              Predicate<AnimalRecord> allowed) {
        UUID id;
        try { id = UUID.fromString(name); }
        catch (IllegalArgumentException invalid) { id = null; }
        UUID exact = id;
        return records.stream().filter(allowed)
                .filter(pet -> exact == null ? pet.matchesName(name) : pet.id().equals(exact))
                .sorted(Comparator.comparing(AnimalRecord::id)).toList();
    }
}
