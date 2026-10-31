package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.screen.HeaderFlow.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out the browser header so the pet list keeps at least two full rows. When the header is too tall, the species
 * row first shares a row with the tabs, then the less used buttons fold into a More button, and last Sort and Showing
 * join them.
 */
final class BrowserLayout {
    static final int TOP = 30, MARGIN = 16, BOTTOM_MARGIN = 16, ARROW = 20, SEARCH_MIN = 120;
    static final int MIN_LIST = 2 * 42 + 8;

    record Widths(int discover, int[] filters, int sort, int scope, int settings, int help, int refresh, int feedback,
                  int more, int[] tabs, int[] species) {}

    record Plan(HeaderFlow.Result flow, boolean collapsed, boolean sortAndScopeInMore, int listTop) {
        HeaderFlow.Slot slot(String key) { return flow.find(key); }
    }

    private BrowserLayout() {}

    static Plan plan(Widths widths, int width, int height) {
        Plan plan = null;
        for (int stage = 0; stage < 4; stage++) {
            plan = attempt(widths, width, stage);
            if (height - BOTTOM_MARGIN - plan.listTop() >= MIN_LIST) break;
        }
        return plan;
    }

    private static Plan attempt(Widths widths, int width, int stage) {
        int left = MARGIN, right = width - MARGIN;
        boolean collapsed = stage >= 2, sortAndScopeInMore = stage >= 3 && widths.sort() + widths.scope() > 0;
        List<Item> items = new ArrayList<>();
        int group = 0;
        items.add(Item.flex("search", Math.max(SEARCH_MIN, (right - left) * 2 / 5), group, false));
        items.add(Item.fixed("discover", widths.discover(), group));
        group++;
        for (int i = 0; i < widths.filters().length; i++) items.add(Item.fixed("filter" + i, widths.filters()[i], group));
        if (widths.sort() > 0 && !sortAndScopeInMore) items.add(Item.fixed("sort", widths.sort(), ++group));
        if (widths.scope() > 0 && !sortAndScopeInMore) items.add(Item.fixed("scope", widths.scope(), ++group));
        if (collapsed) {
            items.add(Item.fixed("more", widths.more(), ++group));
        } else {
            items.add(Item.fixed("settings", widths.settings(), ++group));
            items.add(Item.fixed("help", widths.help(), ++group));
            items.add(Item.fixed("refresh", widths.refresh(), ++group));
            items.add(Item.fixed("feedback", widths.feedback(), ++group));
        }
        group++;
        for (int i = 0; i < widths.tabs().length; i++) items.add(Item.fixed("tab" + i, widths.tabs()[i], group));
        if (widths.species().length > 0) items.add(Item.flex("species", stripMinimum(widths.species()), ++group, stage == 0));
        HeaderFlow.Result flow = HeaderFlow.place(items, left, right, TOP);
        return new Plan(flow, collapsed, sortAndScopeInMore, flow.bottom(TOP));
    }

    static int stripWidth(int[] species) {
        int total = -HeaderFlow.GAP;
        for (int width : species) total += width + HeaderFlow.GAP;
        return total;
    }

    /** Room for both arrows and the widest species button, so every page shows at least one species. */
    static int stripMinimum(int[] species) {
        int widest = 0;
        for (int width : species) widest = Math.max(widest, width);
        return Math.min(stripWidth(species), 2 * (ARROW + HeaderFlow.GAP) + widest);
    }

    /** The species shown from {@code offset} in a strip of this width, as {@code [first, end)}; arrows are needed when not all fit. */
    static int[] visibleSpecies(int[] species, int offset, int width) {
        if (stripWidth(species) <= width) return new int[]{0, species.length};
        int room = width - 2 * (ARROW + HeaderFlow.GAP);
        int first = Math.max(0, Math.min(offset, species.length - 1));
        int used = -HeaderFlow.GAP, end = first;
        while (end < species.length && used + HeaderFlow.GAP + species[end] <= room) used += HeaderFlow.GAP + species[end++];
        if (end == first) end = first + 1;
        return new int[]{first, end};
    }

    /** The offset of the page before {@code offset}, filling the strip backwards. */
    static int previousPage(int[] species, int offset, int width) {
        int room = width - 2 * (ARROW + HeaderFlow.GAP);
        int used = -HeaderFlow.GAP, first = Math.min(offset, species.length);
        while (first > 0 && used + HeaderFlow.GAP + species[first - 1] <= room) used += HeaderFlow.GAP + species[--first];
        return first == offset ? Math.max(0, offset - 1) : first;
    }
}
