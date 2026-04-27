package com.egakh.familytree.naming;

import java.util.UUID;

public final class NameGenerator {

    private static final String[] ADJECTIVES = {
            "Swift", "Brave", "Tiny", "Mighty", "Sunny", "Shadow", "Frosty", "Lucky",
            "Quiet", "Wild", "Gentle", "Stormy", "Cosy", "Dusky", "Misty", "Bold",
            "Plucky", "Sleepy", "Curious", "Nimble", "Jolly", "Snappy", "Dapper",
            "Velvet", "Cinder", "Amber", "Hazel", "Silver", "Copper", "Ivory"
    };

    private static final String[] NOUNS = {
            "Maple", "River", "Pebble", "Willow", "Ember", "Fern", "Cloud", "Acorn",
            "Brook", "Thistle", "Hollow", "Aspen", "Bramble", "Cedar", "Drift",
            "Ridge", "Glade", "Marsh", "Cove", "Dune", "Fjord", "Grove", "Heath",
            "Knoll", "Loam", "Mire", "Reef", "Spire", "Tarn", "Vale"
    };

    private NameGenerator() {}

    public static String generate(UUID uuid) {
        long bits = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        int adj = Math.floorMod((int) (bits >>> 32), ADJECTIVES.length);
        int noun = Math.floorMod((int) bits, NOUNS.length);
        return ADJECTIVES[adj] + "-" + NOUNS[noun];
    }
}
