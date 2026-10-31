package com.egakh.familytree.menu;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.text.ServerText;
import com.egakh.familytree.util.DeathMessages;
import com.egakh.familytree.util.HorseStatUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** A written book describing one pet's family. Children continue on new pages when a page is full. */
final class FamilyBook {
    private static final int NAME_LENGTH = 24;
    private static final int TITLE_LENGTH = 32;

    private FamilyBook() {}

    static ItemStack build(String language, AnimalRecord pet, Function<UUID, AnimalRecord> lookup,
                           List<AnimalRecord> children, int hiddenChildren, long currentDay) {
        List<Component> pages = new ArrayList<>();
        pages.add(overview(pet, currentDay));
        pages.add(ancestors(pet, lookup));
        pages.addAll(children(language, children, hiddenChildren));
        List<Component> localized = new ArrayList<>(pages.size());
        for (Component page : pages) localized.add(ServerText.localize(language, page));
        return MenuItems.writtenBook(MenuText.trim(pet.name(), TITLE_LENGTH), "Family Tree", localized);
    }

    /** Children the player can't view are counted, so a page never claims none were recorded when some were. */
    static List<Component> children(String language, List<AnimalRecord> children, int hiddenChildren) {
        Component heading = Component.translatable("familytree.book.children", children.size() + hiddenChildren);
        List<Component> lines = new ArrayList<>();
        for (AnimalRecord child : children) lines.add(nameOf(child));
        if (hiddenChildren > 0)
            lines.add(Component.translatable("familytree.menu.children.hidden", hiddenChildren).withStyle(ChatFormatting.DARK_GRAY));
        if (lines.isEmpty()) lines.add(Component.translatable("familytree.book.none").withStyle(ChatFormatting.DARK_GRAY));
        int headingLines = MenuText.bookLines(ServerText.plain(language, heading), true);
        List<Component> pages = new ArrayList<>();
        MutableComponent page = null;
        int used = 0;
        for (Component line : lines) {
            int height = MenuText.bookLines(ServerText.plain(language, line), false);
            if (page == null || used + height > MenuText.BOOK_LINES) {
                page = heading(heading);
                pages.add(page);
                used = headingLines;
            }
            page.append(line(line));
            used += height;
        }
        return pages;
    }

    private static Component overview(AnimalRecord pet, long currentDay) {
        MutableComponent page = Component.literal(MenuText.trim(pet.name(), NAME_LENGTH)).withStyle(ChatFormatting.BOLD);
        page.append(line(MenuItems.speciesName(pet.speciesId())));
        page.append(line(Component.translatable(pet.deceased() ? "familytree.screen.status.deceased" : "familytree.screen.status.alive")
                .withStyle(pet.deceased() ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GREEN)));
        page.append(Component.literal("\n"));
        long end = pet.deceased() && pet.deathWorldDay() != null ? pet.deathWorldDay() : currentDay;
        page.append(line(Component.translatable(pet.deceased() ? "familytree.menu.pet.lifespan" : "familytree.menu.pet.age",
                pet.birthWorldDay(), Math.max(0, end - pet.birthWorldDay()))));
        if (pet.ownerName() != null && !pet.ownerName().isBlank())
            page.append(line(Component.translatable("familytree.owner.tamed_by", pet.ownerName())));
        Component death = pet.deceased() ? DeathMessages.line(pet) : null;
        if (death != null) page.append(line(death));
        HorseStats stats = pet.horseStats();
        if (stats != null) {
            page.append(line(Component.translatable("familytree.stats.speed", HorseStatUnits.Stat.SPEED.display(stats))));
            page.append(line(Component.translatable("familytree.stats.jump", HorseStatUnits.Stat.JUMP.display(stats))));
            page.append(line(Component.translatable("familytree.stats.health", HorseStatUnits.Stat.HEALTH.display(stats))));
            if (stats.strength() != null) page.append(line(Component.translatable("familytree.stats.strength",
                    stats.strength(), HorseStatUnits.chestSlots(stats.strength()))));
        }
        return page;
    }

    private static Component ancestors(AnimalRecord pet, Function<UUID, AnimalRecord> lookup) {
        AnimalRecord a = lookup.apply(pet.parentA()), b = lookup.apply(pet.parentB());
        MutableComponent page = heading(Component.translatable("familytree.book.parents"));
        page.append(line(nameOf(a)));
        page.append(line(nameOf(b)));
        page.append(Component.literal("\n\n"));
        page.append(Component.translatable("familytree.book.grandparents").withStyle(ChatFormatting.BOLD));
        for (AnimalRecord parent : new AnimalRecord[]{a, b}) {
            page.append(line(nameOf(parent == null ? null : lookup.apply(parent.parentA()))));
            page.append(line(nameOf(parent == null ? null : lookup.apply(parent.parentB()))));
        }
        return page;
    }

    private static MutableComponent heading(Component text) {
        return Component.empty().append(text.copy().withStyle(ChatFormatting.BOLD));
    }

    private static MutableComponent line(Component text) {
        return Component.literal("\n").append(text);
    }

    private static Component nameOf(AnimalRecord pet) {
        if (pet == null) return Component.translatable("familytree.book.unknown").withStyle(ChatFormatting.DARK_GRAY);
        return Component.literal(MenuText.trim(pet.name(), NAME_LENGTH)).withStyle(pet.deceased() ? ChatFormatting.DARK_GRAY : ChatFormatting.BLACK);
    }
}
