package com.egakh.familytree.plugin.menu;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.menu.MenuText;
import com.egakh.familytree.plugin.text.GameText;
import com.egakh.familytree.plugin.text.Messages;
import com.egakh.familytree.plugin.text.Text;
import com.egakh.familytree.plugin.text.VanillaLang;
import com.egakh.familytree.util.HorseStatUnits;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** A written book describing one animal's family. Children continue on new pages when a page is full. */
record FamilyBook(String title, List<Text> pages) {
    private static final int NAME_LENGTH = 24;
    private static final int TITLE_LENGTH = 32;

    static FamilyBook build(String language, AnimalRecord pet, Function<UUID, AnimalRecord> lookup,
                            List<AnimalRecord> children, int hiddenChildren, long currentDay) {
        List<Text> pages = new ArrayList<>();
        pages.add(overview(language, pet, currentDay));
        pages.add(ancestors(language, pet, lookup));
        pages.addAll(children(language, children, hiddenChildren));
        return new FamilyBook(MenuText.trim(pet.name(), TITLE_LENGTH), pages);
    }

    /** Children the player can't view are counted, so a page never claims none were recorded when some were. */
    static List<Text> children(String language, List<AnimalRecord> children, int hiddenChildren) {
        List<Text> lines = new ArrayList<>();
        for (AnimalRecord child : children) lines.add(nameOf(language, child));
        if (hiddenChildren > 0)
            lines.add(Messages.tr(language, "familytree.menu.children.hidden", hiddenChildren).color(Text.Color.DARK_GRAY));
        if (lines.isEmpty()) lines.add(Messages.tr(language, "familytree.book.none").color(Text.Color.DARK_GRAY));
        int total = children.size() + hiddenChildren;
        int headingLines = MenuText.bookLines(Messages.plain(language, "familytree.book.children", total), true);
        List<Text> pages = new ArrayList<>();
        Text page = null;
        int used = 0;
        for (Text line : lines) {
            int height = MenuText.bookLines(line.plain(VanillaLang::english), false);
            if (page == null || used + height > MenuText.BOOK_LINES) {
                page = heading(Messages.tr(language, "familytree.book.children", total));
                pages.add(page);
                used = headingLines;
            }
            page.append(line(line));
            used += height;
        }
        return pages;
    }

    private static Text overview(String language, AnimalRecord pet, long currentDay) {
        Text page = Text.empty().append(Text.literal(MenuText.trim(pet.name(), NAME_LENGTH)).bold(true));
        page.append(line(GameText.species(pet.speciesId())));
        page.append(line(Messages.tr(language, pet.deceased() ? "familytree.screen.status.deceased" : "familytree.screen.status.alive")
                .color(pet.deceased() ? Text.Color.DARK_RED : Text.Color.DARK_GREEN)));
        page.append("\n");
        long end = pet.deceased() && pet.deathWorldDay() != null ? pet.deathWorldDay() : currentDay;
        page.append(line(Messages.tr(language, pet.deceased() ? "familytree.menu.pet.lifespan" : "familytree.menu.pet.age",
                pet.birthWorldDay(), Math.max(0, end - pet.birthWorldDay()))));
        if (pet.ownerName() != null && !pet.ownerName().isBlank())
            page.append(line(Messages.tr(language, "familytree.owner.tamed_by", pet.ownerName())));
        Text death = pet.deceased() ? GameText.deathLine(language, pet) : null;
        if (death != null) page.append(line(death));
        HorseStats stats = pet.horseStats();
        if (stats != null) {
            page.append(line(Messages.tr(language, "familytree.stats.speed", HorseStatUnits.Stat.SPEED.display(stats))));
            page.append(line(Messages.tr(language, "familytree.stats.jump", HorseStatUnits.Stat.JUMP.display(stats))));
            page.append(line(Messages.tr(language, "familytree.stats.health", HorseStatUnits.Stat.HEALTH.display(stats))));
            if (stats.strength() != null) page.append(line(Messages.tr(language, "familytree.stats.strength",
                    stats.strength(), HorseStatUnits.chestSlots(stats.strength()))));
        }
        return page;
    }

    private static Text ancestors(String language, AnimalRecord pet, Function<UUID, AnimalRecord> lookup) {
        AnimalRecord a = lookup.apply(pet.parentA()), b = lookup.apply(pet.parentB());
        Text page = heading(Messages.tr(language, "familytree.book.parents"));
        page.append(line(nameOf(language, a)));
        page.append(line(nameOf(language, b)));
        page.append("\n\n");
        page.append(Messages.tr(language, "familytree.book.grandparents").bold(true));
        for (AnimalRecord parent : new AnimalRecord[]{a, b}) {
            page.append(line(nameOf(language, parent == null ? null : lookup.apply(parent.parentA()))));
            page.append(line(nameOf(language, parent == null ? null : lookup.apply(parent.parentB()))));
        }
        return page;
    }

    private static Text heading(Text text) {
        return Text.empty().append(text.bold(true));
    }

    private static Text line(Text text) {
        return Text.literal("\n").append(text);
    }

    private static Text nameOf(String language, AnimalRecord pet) {
        if (pet == null) return Messages.tr(language, "familytree.book.unknown").color(Text.Color.DARK_GRAY);
        return Text.literal(MenuText.trim(pet.name(), NAME_LENGTH)).color(pet.deceased() ? Text.Color.DARK_GRAY : Text.Color.BLACK);
    }
}
