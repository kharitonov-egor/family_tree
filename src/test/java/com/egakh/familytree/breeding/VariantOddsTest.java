package com.egakh.familytree.breeding;

import com.egakh.familytree.breeding.VariantOdds.Chance;
import com.egakh.familytree.breeding.VariantOdds.Kind;
import com.egakh.familytree.breeding.VariantOdds.Note;
import com.egakh.familytree.breeding.VariantOdds.Result;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class VariantOddsTest {
    private static final List<String> DYES = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private static Map<String, Double> odds(Result result, int group) {
        Map<String, Double> map = new HashMap<>();
        for (Chance chance : result.groups().get(group).chances()) map.put(chance.variant(), chance.probability());
        return map;
    }

    private static void assertOdds(Map<String, Double> expected, Map<String, Double> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (var entry : expected.entrySet()) assertEquals(entry.getValue(), actual.get(entry.getKey()), 1e-12, entry.getKey());
    }

    private static double total(Result result, int group) {
        double sum = result.groups().get(group).unpredictable();
        for (Chance chance : result.groups().get(group).chances()) sum += chance.probability();
        return sum;
    }

    @Test
    void woolMixesFollowTheDyeRecipesInThisVersionsData() throws Exception {
        Map<Set<String>, String> recipes = twoDyeRecipes();
        assertEquals(9, recipes.size(), "two-dye recipes in the game data: " + recipes);
        for (String a : DYES) {
            for (String b : DYES) {
                Result result = VariantOdds.forPair("minecraft:sheep", "minecraft:sheep", a, b);
                Map<String, Double> odds = odds(result, 0);
                assertEquals(Kind.WOOL, result.groups().get(0).kind());
                assertEquals(1, total(result, 0), 1e-12);
                String mixed = a.equals(b) ? null : recipes.get(Set.of(a, b));
                if (mixed != null) assertOdds(Map.of(mixed, 1.0), odds);
                else if (a.equals(b)) assertOdds(Map.of(a, 1.0), odds);
                else assertOdds(Map.of(a, 0.5, b, 0.5), odds);
            }
        }
        assertEquals("pink", VariantOdds.mixedWool("red", "white"));
        assertEquals("purple", VariantOdds.mixedWool("blue", "red"));
    }

    /**
     * Reads every shapeless recipe with two dye ingredients and a dye result. NeoForge ships its own copies of the
     * dye recipes that use {@code #c:dyes/<color>} tags, which hold the vanilla dyes, so every jar that has the
     * recipes is read and the first copy of each file wins, as a data pack override would.
     */
    private static Map<Set<String>, String> twoDyeRecipes() throws Exception {
        Map<String, Map.Entry<Set<String>, String>> byFile = new java.util.LinkedHashMap<>();
        boolean found = false;
        for (String folder : List.of("recipe", "recipes")) {
            for (URL marker : java.util.Collections.list(VariantOddsTest.class.getClassLoader()
                    .getResources("data/minecraft/" + folder + "/purple_dye.json"))) {
                found = true;
                try (JarFile jar = ((JarURLConnection) marker.openConnection()).getJarFile()) {
                    for (JarEntry entry : java.util.Collections.list(jar.entries())) {
                        String name = entry.getName();
                        if (!name.matches("data/minecraft/recipes?/[^/]+\\.json") || byFile.containsKey(name)) continue;
                        JsonObject recipe;
                        try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                            recipe = JsonParser.parseReader(reader).getAsJsonObject();
                        }
                        byFile.put(name, null);
                        if (!recipe.has("type") || !"minecraft:crafting_shapeless".equals(recipe.get("type").getAsString())) continue;
                        JsonArray ingredients = recipe.getAsJsonArray("ingredients");
                        if (ingredients.size() != 2) continue;
                        JsonElement result = recipe.get("result");
                        String id = result.isJsonPrimitive() ? result.getAsString()
                                : result.getAsJsonObject().has("id") ? result.getAsJsonObject().get("id").getAsString()
                                : result.getAsJsonObject().get("item").getAsString();
                        String output = dye(id);
                        String first = dye(item(ingredients.get(0))), second = dye(item(ingredients.get(1)));
                        if (output != null && first != null && second != null)
                            byFile.put(name, Map.entry(Set.of(first, second), output));
                    }
                }
            }
        }
        assertTrue(found, "Minecraft's recipe data is not on the test classpath");
        Map<Set<String>, String> mixes = new HashMap<>();
        for (var mix : byFile.values()) {
            if (mix == null) continue;
            String previous = mixes.put(mix.getKey(), mix.getValue());
            assertTrue(previous == null || previous.equals(mix.getValue()), "Two recipes mix " + mix.getKey() + " differently");
        }
        return mixes;
    }

    /** Plain items, and the {@code c:} or {@code forge:} dye tags that loaders put in their recipe copies. */
    private static String item(JsonElement ingredient) {
        String text = null;
        if (ingredient.isJsonPrimitive()) text = ingredient.getAsString();
        else if (ingredient.isJsonObject() && ingredient.getAsJsonObject().has("item")) text = ingredient.getAsJsonObject().get("item").getAsString();
        else if (ingredient.isJsonObject() && ingredient.getAsJsonObject().has("tag")) text = "#" + ingredient.getAsJsonObject().get("tag").getAsString();
        if (text == null || !text.startsWith("#")) return text;
        java.util.regex.Matcher tag = java.util.regex.Pattern.compile("#(?:c|forge):dyes/([a-z_]+)").matcher(text);
        return tag.matches() ? "minecraft:" + tag.group(1) + "_dye" : null;
    }

    private static String dye(String item) {
        if (item == null || !item.startsWith("minecraft:") || !item.endsWith("_dye")) return null;
        String color = item.substring("minecraft:".length(), item.length() - "_dye".length());
        return DYES.contains(color) ? color : null;
    }

    @Test
    void bredAxolotlsTakeAParentsColorExceptForTheBlueMutation() {
        Result result = VariantOdds.forPair("minecraft:axolotl", "minecraft:axolotl", "lucy", "wild");
        assertOdds(Map.of("lucy", 1199.0 / 2400, "wild", 1199.0 / 2400, "blue", 1.0 / 1200), odds(result, 0));
        RandomSource random = RandomSource.create(9);
        int samples = 600_000, blue = 0;
        for (int i = 0; i < samples; i++) {
            // Axolotl.getBreedOffspring: useRareVariant, then a coin flip between the parents.
            String baby = random.nextInt(1200) == 0 ? "blue" : random.nextBoolean() ? "lucy" : "wild";
            if (baby.equals("blue")) blue++;
        }
        assertEquals(1.0 / 1200, blue / (double) samples, 0.0002);
        assertOdds(Map.of("blue", 1.0), odds(VariantOdds.forPair("minecraft:axolotl", "minecraft:axolotl", "blue", "blue"), 0));
    }

    @Test
    void mooshroomsFlipOnlyWhenBothParentsMatch() {
        Map<String, Double> same = odds(VariantOdds.forPair("minecraft:mooshroom", "minecraft:mooshroom", "red", "red"), 0);
        assertEquals(1023.0 / 1024, same.get("red"), 1e-12);
        assertEquals(1.0 / 1024, same.get("brown"), 1e-12);
        assertOdds(Map.of("red", 0.5, "brown", 0.5), odds(VariantOdds.forPair("minecraft:mooshroom", "minecraft:mooshroom", "red", "brown"), 0));
    }

    @Test
    void pandaLooksMatchSamplingTheGamesGeneRule() {
        String[][] pairs = {{"brown/brown", "brown/brown"}, {"normal/brown", "brown/normal"}, {"lazy/weak", "weak/aggressive"},
                {"playful/normal", "worried/brown"}};
        RandomSource random = RandomSource.create(77);
        int samples = 300_000;
        for (String[] pair : pairs) {
            Result result = VariantOdds.forPair("minecraft:panda", "minecraft:panda", pair[0], pair[1]);
            assertEquals(Kind.LOOK, result.groups().get(0).kind());
            assertEquals(1, total(result, 0), 1e-9);
            Map<String, Double> expected = new HashMap<>();
            for (Chance chance : result.groups().get(0).chances()) expected.put(VariantOdds.pandaLook(chance.variant()), chance.probability());
            Map<String, Integer> counts = new HashMap<>();
            String[] a = pair[0].split("/"), b = pair[1].split("/");
            for (int i = 0; i < samples; i++) {
                String main, hidden;
                // Panda.setGeneFromParents with both parents present, then the two 1-in-32 mutations.
                if (random.nextBoolean()) {
                    main = random.nextBoolean() ? a[0] : a[1];
                    hidden = random.nextBoolean() ? b[0] : b[1];
                } else {
                    main = random.nextBoolean() ? b[0] : b[1];
                    hidden = random.nextBoolean() ? a[0] : a[1];
                }
                if (random.nextInt(32) == 0) main = randomGene(random);
                if (random.nextInt(32) == 0) hidden = randomGene(random);
                counts.merge(VariantOdds.pandaLook(main + "/" + hidden), 1, Integer::sum);
            }
            for (var entry : counts.entrySet()) {
                assertTrue(expected.containsKey(entry.getKey()), pair[0] + " x " + pair[1] + " missing " + entry.getKey());
                assertEquals(expected.get(entry.getKey()), entry.getValue() / (double) samples, 0.004, pair[0] + " x " + pair[1] + " " + entry.getKey());
            }
        }
        Map<String, Double> brown = new HashMap<>();
        for (Chance chance : VariantOdds.forPair("minecraft:panda", "minecraft:panda", "brown/brown", "brown/brown").groups().get(0).chances())
            brown.put(VariantOdds.pandaLook(chance.variant()), chance.probability());
        double keep = 31.0 / 32 + 1.0 / 32 * 2 / 16;
        assertEquals(keep * keep, brown.get("brown|"), 1e-12);
    }

    /** Panda.Gene.getRandom. */
    private static String randomGene(RandomSource random) {
        int i = random.nextInt(16);
        if (i == 0) return "lazy";
        if (i == 1) return "worried";
        if (i == 2) return "playful";
        if (i == 4) return "aggressive";
        if (i < 9) return "weak";
        return i < 11 ? "brown" : "normal";
    }

    @Test
    void horseCoatsAndMarkingsFollowTheNineAndFiveWayDraws() {
        Result result = VariantOdds.forPair("minecraft:horse", "minecraft:horse", "chestnut/white_dots", "black/white_dots");
        assertEquals(List.of(Kind.COAT, Kind.MARKINGS), result.groups().stream().map(VariantOdds.Group::kind).toList());
        Map<String, Double> coats = odds(result, 0), markings = odds(result, 1);
        assertEquals(4.0 / 9 + 1.0 / 63, coats.get("chestnut"), 1e-12);
        assertEquals(4.0 / 9 + 1.0 / 63, coats.get("black"), 1e-12);
        assertEquals(1.0 / 63, coats.get("gray"), 1e-12);
        assertEquals(7, coats.size());
        assertEquals(4.0 / 5 + 1.0 / 25, markings.get("white_dots"), 1e-12);
        assertEquals(1.0 / 25, markings.get("none"), 1e-12);
        assertEquals(1, total(result, 0), 1e-12);
        assertEquals(1, total(result, 1), 1e-12);
        RandomSource random = RandomSource.create(3);
        int samples = 300_000, chestnut = 0, none = 0;
        List<String> coatList = VariantOdds.HORSE_COATS, markList = VariantOdds.HORSE_MARKINGS;
        for (int i = 0; i < samples; i++) {
            int coat = random.nextInt(9);
            String c = coat < 4 ? "chestnut" : coat < 8 ? "black" : coatList.get(random.nextInt(coatList.size()));
            int mark = random.nextInt(5);
            String m = mark < 4 ? "white_dots" : markList.get(random.nextInt(markList.size()));
            if (c.equals("chestnut")) chestnut++;
            if (m.equals("none")) none++;
        }
        assertEquals(coats.get("chestnut"), chestnut / (double) samples, 0.003);
        assertEquals(markings.get("none"), none / (double) samples, 0.002);
    }

    @Test
    void rabbitsLeaveOneInTwentyToTheBiome() {
        Result result = VariantOdds.forPair("minecraft:rabbit", "minecraft:rabbit", "brown", "white");
        assertOdds(Map.of("brown", 0.475, "white", 0.475), odds(result, 0));
        assertEquals(0.05, result.groups().get(0).unpredictable(), 1e-12);
        assertEquals(Note.RABBIT_BIOME, result.note());
    }

    @Test
    void coinFlipSpeciesAndNotes() {
        assertOdds(Map.of("red", 0.5, "snow", 0.5), odds(VariantOdds.forPair("minecraft:fox", "minecraft:fox", "red", "snow"), 0));
        assertOdds(Map.of("gray", 0.5, "creamy", 0.5), odds(VariantOdds.forPair("minecraft:llama", "minecraft:trader_llama", "gray", "creamy"), 0));
        assertOdds(Map.of("minecraft:warm", 1.0), odds(VariantOdds.forPair("minecraft:cow", "minecraft:cow", "minecraft:warm", "minecraft:warm"), 0));
        assertTrue(VariantOdds.forPair("minecraft:cow", "minecraft:cow", null, null).groups().isEmpty());
        assertEquals(Note.NONE, VariantOdds.forPair("minecraft:pig", "minecraft:pig", null, null).note());
        assertEquals(Note.UNKNOWN_PARENT, VariantOdds.forPair("minecraft:axolotl", "minecraft:axolotl", "lucy", null).note());
        assertEquals(Note.FROG_BIOME, VariantOdds.forPair("minecraft:frog", "minecraft:frog", "minecraft:warm", "minecraft:cold").note());
        assertEquals(Note.MODDED, VariantOdds.forPair("naturalist:deer", "naturalist:deer", "a", "b").note());
        assertOdds(Map.of("minecraft:tabby", 0.5, "minecraft:black", 0.5),
                odds(VariantOdds.forPair("minecraft:cat", "minecraft:cat", "minecraft:tabby", "minecraft:black"), 0));
        assertOdds(Map.of("minecraft:pale", 0.5, "minecraft:woods", 0.5),
                odds(VariantOdds.forPair("minecraft:wolf", "minecraft:wolf", "minecraft:pale", "minecraft:woods"), 0));
        assertEquals(Note.UNKNOWN_PARENT, VariantOdds.forPair("minecraft:wolf", "minecraft:wolf", "minecraft:pale", null).note());
        assertTrue(VariantOdds.forPair("minecraft:wolf", "minecraft:wolf", null, null).groups().isEmpty());
        assertTrue(VariantOdds.forPair("minecraft:horse", "minecraft:donkey", "black/none", null).groups().isEmpty());
        assertTrue(VariantOdds.forPair("minecraft:goat", "minecraft:goat", null, null).groups().isEmpty());
        List<Double> sorted = new ArrayList<>();
        for (Chance chance : VariantOdds.forPair("minecraft:axolotl", "minecraft:axolotl", "gold", "gold").groups().get(0).chances())
            sorted.add(chance.probability());
        assertTrue(sorted.get(0) >= sorted.get(sorted.size() - 1));
    }
}
