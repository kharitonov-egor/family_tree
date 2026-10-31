package com.egakh.familytree.client.integration;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.screen.HorseStatsText;
import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.integration.PetInsight;
import com.egakh.familytree.util.HorseStatUnits.Stat;
import com.egakh.familytree.util.VariantText;
import com.egakh.familytree.util.PetNames;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Tooltip lines for Jade and WTHIT, from the server's reply or else the last family snapshot this client received. */
public final class PetTooltip {
    private PetTooltip() {}

    /** {@code shownElsewhere} names the horse stats the tooltip mod's own lines already show. */
    public static List<Component> lines(CompoundTag serverData, UUID entityId, Set<Stat> shownElsewhere) {
        PetInsight insight = PetInsight.read(serverData);
        if (insight == null) insight = FamilyTreeClient.latestInsight(entityId);
        return insight == null ? List.of() : lines(insight, shownElsewhere);
    }

    public static List<Component> lines(PetInsight insight, Set<Stat> shownElsewhere) {
        AnimalRecord pet = insight.pet();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("familytree.tooltip.name", PetNames.isolate(pet.name())).withStyle(ChatFormatting.GREEN));
        lines.add(parents(insight).withStyle(ChatFormatting.GRAY));
        if (FamilyTreeClientSettings.showGeneration() && insight.generation() > 0)
            lines.add(Component.translatable("familytree.tooltip.generation", insight.generation()).withStyle(ChatFormatting.GRAY));
        MutableComponent variant = VariantText.label(pet);
        if (variant != null) {
            MutableComponent line = Component.translatable("familytree.variant.line", variant);
            if (VariantText.rare(pet))
                line.append(Component.literal(" " + VariantText.RARE_MARKER).withStyle(style -> style.withColor(VariantText.RARE_COLOR & 0xFFFFFF)));
            lines.add(line);
            Component note = VariantText.note(pet, insight::parent);
            if (note != null) lines.add(note.copy().withStyle(ChatFormatting.GRAY));
        }
        if (FamilyTreeClientSettings.showHorseStats()) {
            for (HorseStatsText.DetailLine stat : HorseStatsText.details(pet, insight::parent, shownElsewhere)) {
                MutableComponent line = Component.literal(stat.text());
                String marker = HorseStatsText.marker(stat.trend());
                if (!marker.isEmpty())
                    line.append(Component.literal(" " + marker).withStyle(style -> style.withColor(HorseStatsText.markerColor(stat.trend()) & 0xFFFFFF)));
                if (!stat.parents().isEmpty()) line.append(Component.literal("  " + stat.parents()).withStyle(ChatFormatting.GRAY));
                lines.add(line);
            }
        }
        return lines;
    }

    private static MutableComponent parents(PetInsight insight) {
        AnimalRecord a = insight.parent(insight.pet().parentA()), b = insight.parent(insight.pet().parentB());
        if (a == null && b == null) return Component.translatable("familytree.tooltip.parents_unknown");
        Component unknown = Component.translatable("familytree.tooltip.parent_unknown");
        return Component.translatable("familytree.tooltip.parents",
                a == null ? unknown : Component.literal(PetNames.isolate(a.name())), b == null ? unknown : Component.literal(PetNames.isolate(b.name())));
    }
}
