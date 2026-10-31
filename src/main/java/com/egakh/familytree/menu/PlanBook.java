package com.egakh.familytree.menu;

import com.egakh.familytree.breeding.BreedingPlan;
import com.egakh.familytree.breeding.PlanText;
import com.egakh.familytree.text.ServerText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The planner's text for players without the client mod. Topics share a page when they fit. */
final class PlanBook {
    private static final int TITLE_LENGTH = 32;

    private PlanBook() {}

    /** A plan line with the muted explanations that follow it, kept together on one page. */
    record Paragraph(MutableComponent text, int lines) {}

    static ItemStack build(String language, BreedingPlan plan) {
        List<Component> pages = new ArrayList<>();
        for (List<Paragraph> page : MenuText.bookPages(paragraphs(language, plan), Paragraph::lines)) {
            MutableComponent text = Component.empty();
            for (Paragraph paragraph : page) {
                if (!text.getSiblings().isEmpty()) text.append("\n\n");
                text.append(paragraph.text());
            }
            pages.add(text);
        }
        String title = MenuText.trim(plan.first.name() + " × " + plan.second.name(), TITLE_LENGTH);
        return MenuItems.writtenBook(title, "Family Tree", pages);
    }

    static List<List<Paragraph>> paragraphs(String language, BreedingPlan plan) {
        List<List<Paragraph>> sections = new ArrayList<>();
        for (List<PlanText.Line> section : PlanText.sections(plan)) {
            List<Paragraph> paragraphs = new ArrayList<>();
            MutableComponent text = null;
            int lines = 0;
            for (PlanText.Line line : section) {
                Component shown = line.muted() ? line.text().copy().withStyle(ChatFormatting.DARK_GRAY) : line.text();
                int height = MenuText.bookLines(ServerText.plain(language, shown), false);
                if (text != null && line.muted()) {
                    text.append("\n").append(ServerText.localize(language, shown));
                    lines += height;
                    continue;
                }
                if (text != null) paragraphs.add(new Paragraph(text, lines));
                text = Component.empty().append(ServerText.localize(language, shown));
                lines = height;
            }
            if (text != null) paragraphs.add(new Paragraph(text, lines));
            sections.add(paragraphs);
        }
        return sections;
    }
}
