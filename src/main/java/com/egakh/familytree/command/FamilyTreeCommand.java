package com.egakh.familytree.command;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.util.TimeUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;

public final class FamilyTreeCommand {

    private FamilyTreeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("familytree")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() ->
                            Component.translatable("familytree.command.usage"), false);
                    return 1;
                })
                .then(Commands.literal("list").executes(ctx -> runList(ctx.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> runInfo(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
        );
    }

    private static int runList(CommandSourceStack source) {
        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        List<AnimalRecord> all = state.all().stream()
                .sorted(Comparator.comparing(AnimalRecord::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("familytree.screen.empty"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("familytree.command.list.header"), false);
        long currentDay = TimeUtil.currentWorldDay(source.getLevel());
        long currentEpoch = TimeUtil.currentEpochMillis();
        for (AnimalRecord r : all) {
            String line = formatLine(r, currentDay, currentEpoch);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return all.size();
    }

    private static int runInfo(CommandSourceStack source, String name) {
        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        AnimalRecord match = state.all().stream()
                .filter(r -> r.name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
        if (match == null) {
            source.sendFailure(Component.translatable("familytree.command.info.unknown", name));
            return 0;
        }

        long currentDay = TimeUtil.currentWorldDay(source.getLevel());
        long currentEpoch = TimeUtil.currentEpochMillis();
        source.sendSuccess(() -> Component.literal(formatLine(match, currentDay, currentEpoch))
                .withStyle(ChatFormatting.YELLOW), false);
        if (match.parentA() != null) {
            AnimalRecord pa = state.get(match.parentA());
            if (pa != null) {
                source.sendSuccess(() -> Component.literal("  parent A: " + pa.name()), false);
            }
        }
        if (match.parentB() != null) {
            AnimalRecord pb = state.get(match.parentB());
            if (pb != null) {
                source.sendSuccess(() -> Component.literal("  parent B: " + pb.name()), false);
            }
        }
        return 1;
    }

    private static String formatLine(AnimalRecord r, long currentDay, long currentEpoch) {
        long worldAge = r.deceased() && r.deathWorldDay() != null
                ? r.deathWorldDay() - r.birthWorldDay()
                : currentDay - r.birthWorldDay();
        long realAge = r.deceased() && r.deathEpochMillis() != null
                ? TimeUtil.realDaysBetween(r.birthEpochMillis(), r.deathEpochMillis())
                : TimeUtil.realDaysBetween(r.birthEpochMillis(), currentEpoch);

        String suffix = r.deceased()
                ? " (deceased; lived " + worldAge + " world-days / " + realAge + " real-days)"
                : " - born day " + r.birthWorldDay() + " (" + worldAge + " world-days old, " + realAge + " real-days old)";
        return "- " + r.name() + " [" + shortSpecies(r.speciesId()) + "]" + suffix;
    }

    private static String shortSpecies(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
