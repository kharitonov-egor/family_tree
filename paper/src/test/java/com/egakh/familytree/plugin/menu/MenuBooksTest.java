package com.egakh.familytree.plugin.menu;

import com.egakh.familytree.breeding.BreedingPlan;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.menu.MenuText;
import com.egakh.familytree.plugin.text.Text;
import com.egakh.familytree.plugin.text.VanillaLang;
import com.egakh.familytree.util.Kinship;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MenuBooksTest {
    private static AnimalRecord wolf(long id, String name, String coat) {
        return new AnimalRecord(new UUID(0, id), "minecraft:wolf", name, false, null, null, 1, 2, false, null, null,
                new UUID(9, 1), "Kai", coat, null, null);
    }

    @Test
    void aWolfPlanFitsTwoPagesAndNamesTheCoats() {
        AnimalRecord pepper = wolf(1, "Pepper", "minecraft:pale"), ziggy = wolf(2, "Ziggy", "minecraft:woods");
        BreedingPlan plan = new BreedingPlan(pepper, ziggy, new Kinship(Map.of(pepper.id(), pepper, ziggy.id(), ziggy)));
        for (String language : List.of("en_us", "de_de", "ja_jp")) {
            List<Text> pages = PlanBook.pages(language, plan);
            assertTrue(pages.size() <= 2, language + ": " + pages.size() + " pages");
        }
        String text = String.join("\n", PlanBook.pages("en_us", plan).stream().map(page -> page.plain(VanillaLang::english)).toList());
        assertTrue(text.contains("Variant: Pale 50%, Woods 50%"), text);
        assertFalse(text.contains("either parent"), text);
    }

    @Test
    void theBookCountsChildrenThePlayerCannotView() {
        List<Text> pages = FamilyBook.children("en_us", List.of(), 2);
        assertEquals(1, pages.size());
        String page = pages.get(0).plain(VanillaLang::english);
        assertTrue(page.contains("Children (2)") && page.contains("Children you can't view: 2"), page);
        assertFalse(page.contains("None recorded"), page);
    }

    @Test
    void longChildListsContinueOnNewPages() {
        List<AnimalRecord> children = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) children.add(wolf(10 + i, "Wolfgang Amadeus Mozart " + i, null));
        List<Text> pages = FamilyBook.children("en_us", children, 0);
        assertTrue(pages.size() >= 2);
        for (Text page : pages) {
            int lines = 0;
            for (String line : page.plain(VanillaLang::english).split("\n", -1)) lines += MenuText.bookLines(line, false);
            assertTrue(lines <= MenuText.BOOK_LINES, lines + " lines on one page");
        }
    }
}
