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

/** The planner's text for players without the client mod, one topic to a page. */
record PlanBook(ItemStack stack, int pages) {
    private static final int TITLE_LENGTH = 32;

    static PlanBook build(String language, BreedingPlan plan) {
        List<Component> pages = new ArrayList<>();
        for (List<PlanText.Line> section : PlanText.sections(plan)) {
            MutableComponent page = Component.empty();
            for (PlanText.Line line : section) {
                if (!page.getSiblings().isEmpty()) page.append("\n\n");
                page.append(line.muted() ? line.text().copy().withStyle(ChatFormatting.DARK_GRAY) : line.text());
            }
            pages.add(ServerText.localize(language, page));
        }
        String title = plan.first.name() + " × " + plan.second.name();
        if (title.length() > TITLE_LENGTH) title = title.substring(0, TITLE_LENGTH - 1) + "…";
        return new PlanBook(MenuItems.writtenBook(title, "Family Tree", pages), pages.size());
    }
}
