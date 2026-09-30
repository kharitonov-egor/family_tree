package com.egakh.familytree.util;

import com.egakh.familytree.data.AnimalRecord;

public final class PetRelations {
    private PetRelations() {}

    public static boolean speciesMatch(AnimalRecord a, AnimalRecord b, AnimalRecord child) {
        return speciesMatch(a.speciesId(), b.speciesId(), child.speciesId());
    }

    public static boolean speciesMatch(String a, String b, String child) {
        if (a.equals(b) && a.equals(child)) return true;
        return child.equals("minecraft:mule")
                && ((a.equals("minecraft:horse") && b.equals("minecraft:donkey"))
                || (a.equals("minecraft:donkey") && b.equals("minecraft:horse")));
    }
}
