package com.egakh.familytree.plugin.menu;

import com.egakh.familytree.breeding.BreedingPlan;
import com.egakh.familytree.menu.MenuText;
import com.egakh.familytree.plugin.text.PlanLines;
import com.egakh.familytree.plugin.text.Text;
import com.egakh.familytree.plugin.text.VanillaLang;

import java.util.ArrayList;
import java.util.List;

/** The planner's text as book pages, laid out like the mod's. Topics share a page when they fit. */
final class PlanBook {
    private PlanBook() {}

    /** A plan line with the muted explanations that follow it, kept together on one page. */
    record Paragraph(Text text, int lines) {}

    static List<Text> pages(String language, BreedingPlan plan) {
        List<Text> pages = new ArrayList<>();
        for (List<Paragraph> page : MenuText.bookPages(paragraphs(language, plan), Paragraph::lines)) {
            Text text = Text.empty();
            boolean first = true;
            for (Paragraph paragraph : page) {
                if (!first) text.append("\n\n");
                text.append(paragraph.text());
                first = false;
            }
            pages.add(text);
        }
        return pages;
    }

    static List<List<Paragraph>> paragraphs(String language, BreedingPlan plan) {
        List<List<Paragraph>> sections = new ArrayList<>();
        for (List<PlanLines.Line> section : PlanLines.sections(language, plan)) {
            List<Paragraph> paragraphs = new ArrayList<>();
            Text text = null;
            int lines = 0;
            for (PlanLines.Line line : section) {
                Text shown = line.muted() ? line.text().copy().color(Text.Color.DARK_GRAY) : line.text();
                int height = MenuText.bookLines(shown.plain(VanillaLang::english), false);
                if (text != null && line.muted()) {
                    text.append("\n").append(shown);
                    lines += height;
                    continue;
                }
                if (text != null) paragraphs.add(new Paragraph(text, lines));
                text = Text.empty().append(shown);
                lines = height;
            }
            if (text != null) paragraphs.add(new Paragraph(text, lines));
            sections.add(paragraphs);
        }
        return sections;
    }
}
