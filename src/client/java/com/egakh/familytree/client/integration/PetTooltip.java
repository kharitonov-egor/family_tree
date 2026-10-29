package com.egakh.familytree.client.integration;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.screen.HorseStatsText;
import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.integration.PetInsight;
import com.egakh.familytree.util.HorseStatUnits;
import com.egakh.familytree.util.HorseStatUnits.Stat;
import com.egakh.familytree.util.HorseStatUnits.Trend;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Tooltip lines for Jade and WTHIT, from the server's reply or else the last family snapshot this client received. */
public final class PetTooltip {
    private PetTooltip() {}

    public static List<Component> lines(CompoundTag serverData, UUID entityId) {
        PetInsight insight = PetInsight.read(serverData);
        if (insight == null) insight = FamilyTreeClient.latestInsight(entityId);
        return insight == null ? List.of() : lines(insight);
    }

    public static List<Component> lines(PetInsight insight) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("familytree.tooltip.name", insight.name()).withStyle(ChatFormatting.GREEN));
        lines.add(parents(insight).withStyle(ChatFormatting.GRAY));
        if (FamilyTreeClientSettings.showGeneration() && insight.generation() > 0)
            lines.add(Component.translatable("familytree.tooltip.generation", insight.generation()).withStyle(ChatFormatting.GRAY));
        if (FamilyTreeClientSettings.showHorseStats() && insight.stats() != null) {
            for (Stat stat : Stat.values()) {
                Component line = statLine(stat, insight);
                if (line != null) lines.add(line);
            }
        }
        return lines;
    }

    private static MutableComponent parents(PetInsight insight) {
        if (insight.parentA() == null && insight.parentB() == null)
            return Component.translatable("familytree.tooltip.parents_unknown");
        Component unknown = Component.translatable("familytree.tooltip.parent_unknown");
        return Component.translatable("familytree.tooltip.parents",
                insight.parentA() == null ? unknown : Component.literal(insight.parentA()),
                insight.parentB() == null ? unknown : Component.literal(insight.parentB()));
    }

    private static Component statLine(Stat stat, PetInsight insight) {
        HorseStats stats = insight.stats();
        Double value = stat.value(stats);
        if (value == null) return null;
        String key = stat.name().toLowerCase(Locale.ROOT);
        MutableComponent line = stat == Stat.STRENGTH
                ? Component.translatable("familytree.stats.strength", stats.strength(), HorseStatUnits.chestSlots(stats.strength()))
                : Component.translatable("familytree.stats." + key, stat.format(value));
        Trend trend = HorseStatUnits.compare(stat, stats, insight.parentAStats(), insight.parentBStats());
        String marker = HorseStatsText.marker(trend);
        if (!marker.isEmpty())
            line.append(Component.literal(" " + marker).withStyle(style -> style.withColor(HorseStatsText.markerColor(trend) & 0xFFFFFF)));
        Double a = stat.value(insight.parentAStats()), b = stat.value(insight.parentBStats());
        if (a != null || b != null) {
            line.append(Component.literal("  "));
            line.append(Component.translatable("familytree.stats.parents", a == null ? "?" : stat.format(a), b == null ? "?" : stat.format(b))
                    .withStyle(ChatFormatting.GRAY));
        }
        return line;
    }
}
