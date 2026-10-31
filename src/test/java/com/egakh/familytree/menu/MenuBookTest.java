package com.egakh.familytree.menu;

import com.egakh.familytree.breeding.BreedingPlan;
import com.egakh.familytree.breeding.PlanText;
import com.egakh.familytree.breeding.VariantOdds;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.text.ServerText;
import com.egakh.familytree.util.Kinship;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MenuBookTest {
    @Test
    void trimsTitlesWithoutSplittingAnEmoji() {
        String title = "Clover the Brave Pony 🐴 × Daisy 🐴";
        assertTrue(Character.isHighSurrogate(title.charAt(22)));
        String cut = MenuText.trim(title, 24);
        assertEquals("Clover the Brave Pony …", cut);
        assertTrue(cut.chars().noneMatch(c -> Character.isSurrogate((char) c)), cut);
        assertEquals("Daisy", MenuText.trim("Daisy", 32));
        assertEquals(32, MenuText.trim("a".repeat(40), 32).length());
    }

    @Test
    void countsBookLinesLikeTheClientDid() {
        // Line counts read from a vanilla 26.3 client's book screen in the 1.4.0 play test.
        assertEquals(2, MenuText.bookLines("Planning Pepper and Ziggy", false));
        assertEquals(1, MenuText.bookLines("Foal: Wolf", false));
        assertEquals(3, MenuText.bookLines("Pepper and Ziggy are not related as far as Family Tree knows.", false));
        assertEquals(2, MenuText.bookLines("Foal's inbreeding coefficient: 0%", false));
        assertEquals(4, MenuText.bookLines("Vanilla Minecraft ignores inbreeding, so this is information, not a penalty.", false));
        assertEquals(3, MenuText.bookLines("The foal takes either parent's coat, each half the time.", false));
        assertEquals(2, MenuText.bookLines("one\ntwo", false));
        assertEquals(2, MenuText.bookLines("x".repeat(20), false), "a word wider than the page breaks inside it");
    }

    @Test
    void countsWiderCyrillicAndLatinLetters() {
        assertEquals(1, MenuText.bookLines("Ю".repeat(14), false));
        assertEquals(2, MenuText.bookLines("Ю".repeat(15), false));
        assertEquals(2, MenuText.bookLines("Щ".repeat(13), false));
        assertEquals(2, MenuText.bookLines("Æ".repeat(12), false));
        assertEquals(1, MenuText.bookLines("Привет мир", false));
    }

    @Test
    void cyrillicChildNamesContinueOnNewPages() {
        List<AnimalRecord> children = new java.util.ArrayList<>();
        for (int i = 0; i < 13; i++) children.add(wolf(30 + i, "Ю".repeat(18), null));
        List<Component> pages = FamilyBook.children("ru_ru", children, 0);
        assertTrue(pages.size() >= 2, pages.size() + " pages for 13 two-line names");
        for (Component page : pages) {
            int lines = 0;
            for (String line : ServerText.plain("ru_ru", page).split("\n", -1)) lines += MenuText.bookLines(line, false);
            assertTrue(lines <= MenuText.BOOK_LINES, lines + " lines on one page");
        }
    }

    @Test
    void vanillaClientsGetTranslatedCoatNames() {
        Component pale = PlanText.label("minecraft:wolf", VariantOdds.Kind.VARIANT, "minecraft:pale");
        assertEquals("Blass", ServerText.localize("de_de", pale).getString());
        Component tuxedo = PlanText.label("minecraft:cat", VariantOdds.Kind.VARIANT, "minecraft:black");
        assertEquals("Tuxedo", ServerText.localize("en_us", tuxedo).getString());
        Component modded = PlanText.label("minecraft:wolf", VariantOdds.Kind.VARIANT, "otherpack:moon_grey");
        assertEquals("Moon grey", ServerText.localize("de_de", modded).getString());
    }

    @Test
    void sectionsShareAPageWhenTheyFit() {
        List<List<Integer>> sections = List.of(List.of(2, 1, 3), List.of(6), List.of(2));
        assertEquals(List.of(List.of(2, 1, 3), List.of(6, 2)), MenuText.bookPages(sections, Integer::intValue));
        List<List<Integer>> long_ = List.of(List.of(1), List.of(5, 5, 5, 5));
        assertEquals(List.of(List.of(1, 5, 5), List.of(5, 5)), MenuText.bookPages(long_, Integer::intValue));
    }

    private static AnimalRecord wolf(long id, String name, String coat) {
        return new AnimalRecord(new UUID(0, id), "minecraft:wolf", name, false, null, null, 1, 2, false, null, null,
                new UUID(9, 1), "Kai", coat, null, null);
    }

    @Test
    void aWolfPlanFitsTwoPagesAndNamesTheCoats() {
        AnimalRecord pepper = wolf(1, "Pepper", "minecraft:pale"), ziggy = wolf(2, "Ziggy", "minecraft:woods");
        BreedingPlan plan = new BreedingPlan(pepper, ziggy, new Kinship(Map.of(pepper.id(), pepper, ziggy.id(), ziggy)));
        for (String language : List.of("en_us", "de_de", "ja_jp")) {
            List<List<PlanBook.Paragraph>> pages = MenuText.bookPages(PlanBook.paragraphs(language, plan), PlanBook.Paragraph::lines);
            assertTrue(pages.size() <= 2, language + ": " + pages.size() + " pages");
        }
        String text = String.join("\n", PlanBook.paragraphs("en_us", plan).stream().flatMap(List::stream)
                .map(paragraph -> ServerText.plain("en_us", paragraph.text())).toList());
        assertTrue(text.contains("Variant: Pale 50%, Woods 50%"), text);
        assertFalse(text.contains("either parent"), text);
        String german = String.join("\n", PlanBook.paragraphs("de_de", plan).stream().flatMap(List::stream)
                .map(paragraph -> ServerText.plain("de_de", paragraph.text())).toList());
        assertTrue(german.contains("Blass") && german.contains("Wald"), german);
    }

    @Test
    void theBookCountsChildrenThePlayerCannotView() {
        List<Component> pages = FamilyBook.children("en_us", List.of(), 1);
        assertEquals(1, pages.size());
        String page = ServerText.plain("en_us", pages.get(0));
        assertTrue(page.contains("Children (1)") && page.contains("Children you can't view: 1"), page);
        assertFalse(page.contains("None recorded"), page);
        assertTrue(ServerText.plain("en_us", FamilyBook.children("en_us", List.of(), 0).get(0)).contains("None recorded"));
    }

    @Test
    void longChildListsContinueOnNewPages() {
        List<AnimalRecord> children = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) children.add(wolf(10 + i, "Wolfgang Amadeus Mozart " + i, null));
        List<Component> pages = FamilyBook.children("en_us", children, 0);
        assertTrue(pages.size() >= 2, "12 names of two lines each need two pages");
        for (Component page : pages) {
            int lines = 0;
            for (String line : ServerText.plain("en_us", page).split("\n", -1)) lines += MenuText.bookLines(line, false);
            assertTrue(lines <= MenuText.BOOK_LINES, lines + " lines on one page");
        }
    }
}
