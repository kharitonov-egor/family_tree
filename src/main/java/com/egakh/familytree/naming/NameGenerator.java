package com.egakh.familytree.naming;

import com.egakh.familytree.data.AnimalRecord;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class NameGenerator {

    private static final String[] BASE_NAMES = {
            "Mochi", "Muffin", "Biscuit", "Cookie", "Waffle", "Pancake", "Cupcake", "Brownie",
            "Pudding", "Jelly", "Boba", "Donut", "Bagel", "Toast", "Honey", "Cocoa",
            "Marshmallow", "Peanut", "Pumpkin", "Nugget", "Cinnamon", "Sugar", "Toffee", "Caramel",
            "Maple", "Buttercup", "Sprinkle", "Taffy", "Churro", "Bonbon",
            "Pickle", "Bean", "Noodle", "Dumpling", "Taco", "Burrito", "Nacho", "Meatball",
            "Hotdog", "Cheddar", "Potato", "Wonton", "Ravioli", "Lasagna", "Pierogi", "Sausage",
            "Burger", "Pretzel", "Mustard", "Ketchup", "Mayo", "Relish", "Crouton", "Gravy",
            "Meatloaf", "Turnip", "Radish", "Cabbage", "Broccoli", "Cornbread",
            "Brie", "Gouda", "Brioche", "Truffle", "Saffron", "Basil", "Pepper", "Olive",
            "Fig", "Clove", "Rosemary", "Thyme", "Sage", "Vanilla", "Mocha", "Espresso",
            "Latte", "Macaron", "Ganache", "Eclair", "Crepe", "Sorbet", "Gelato", "Tiramisu",
            "Cannoli", "Risotto", "Gnocchi", "Pesto", "Focaccia", "Prosciutto"
    };

    private NameGenerator() {}

    public static String generate(UUID uuid, Collection<AnimalRecord> existingRecords) {
        Set<String> usedNames = new HashSet<>();
        for (AnimalRecord record : existingRecords) {
            usedNames.add(record.name().toLowerCase(Locale.ROOT));
        }

        long mixed = uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 1);
        int startIndex = Math.floorMod((int) (mixed ^ (mixed >>> 32)), BASE_NAMES.length);

        for (int offset = 0; offset < BASE_NAMES.length; offset++) {
            String candidate = BASE_NAMES[(startIndex + offset) % BASE_NAMES.length];
            if (!usedNames.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }

        String baseName = BASE_NAMES[startIndex];
        int suffix = 2;
        while (usedNames.contains((baseName + suffix).toLowerCase(Locale.ROOT))) {
            suffix++;
        }
        return baseName + suffix;
    }

    public static boolean isLegacyOverflowName(String name) {
        for (String baseName : BASE_NAMES) {
            if (!name.startsWith(baseName)) continue;
            String suffix = name.substring(baseName.length());
            if (suffix.matches("\\d{4,}")) {
                return true;
            }
        }
        return false;
    }
}
