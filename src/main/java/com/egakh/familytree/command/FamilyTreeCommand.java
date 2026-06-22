package com.egakh.familytree.command;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.util.TimeUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
                .then(Commands.literal("pair")
                        .then(Commands.argument("parent_a", StringArgumentType.string())
                                .then(Commands.argument("parent_b", StringArgumentType.string())
                                        .then(Commands.argument("child", StringArgumentType.string())
                                                .executes(ctx -> runPair(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "parent_a"),
                                                        StringArgumentType.getString(ctx, "parent_b"),
                                                        StringArgumentType.getString(ctx, "child")
                                                ))))))
                .then(Commands.literal("unpair")
                        .then(Commands.argument("child", StringArgumentType.string())
                                .executes(ctx -> runUnpair(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "child")))))
                .then(Commands.literal("setage")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("days", LongArgumentType.longArg(0))
                                        .executes(ctx -> runSetAge(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                LongArgumentType.getLong(ctx, "days"))))))
                .then(Commands.literal("setbirth")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("day", LongArgumentType.longArg(0))
                                        .executes(ctx -> runSetBirthDay(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                LongArgumentType.getLong(ctx, "day"))))))
                .then(Commands.literal("scan").executes(ctx -> runScan(ctx.getSource())))
                .then(Commands.literal("prune")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("deceased")
                                .executes(ctx -> runPruneDeceased(ctx.getSource())))
                        .then(Commands.literal("species")
                                .then(Commands.argument("species_id", StringArgumentType.greedyString())
                                        .executes(ctx -> runPruneSpecies(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "species_id"))))))
                .then(Commands.literal("info").executes(ctx -> runInfoHelp(ctx.getSource()))
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
        for (AnimalRecord r : all) {
            String line = formatLine(r, currentDay);
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
        source.sendSuccess(() -> Component.literal(formatLine(match, currentDay))
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

    private static int runScan(CommandSourceStack source) {
        PetLifecycleListeners.ScanResult result = PetLifecycleListeners.scanLoadedPets(source.getServer());
        source.sendSuccess(() -> Component.translatable("familytree.command.scan.result",
                result.imported(), result.refreshed()), false);
        return result.totalTouched();
    }

    private static int runPruneDeceased(CommandSourceStack source) {
        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        int removed = state.removeMatching(AnimalRecord::deceased);
        source.sendSuccess(() -> Component.translatable("familytree.command.prune.result", removed), false);
        return removed;
    }

    private static int runPruneSpecies(CommandSourceStack source, String speciesId) {
        String normalized = speciesId.trim();
        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        int removed = state.removeMatching(record -> record.speciesId().equalsIgnoreCase(normalized));
        source.sendSuccess(() -> Component.translatable("familytree.command.prune.result", removed), false);
        return removed;
    }

    private static int runUnpair(CommandSourceStack source, String childName) {
        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        AnimalRecord child = findByName(state, childName);
        if (child == null) {
            source.sendFailure(Component.translatable("familytree.command.info.unknown", childName));
            return 0;
        }

        state.update(child.id(), record -> record.setParents(null, null));
        source.sendSuccess(() -> Component.translatable("familytree.command.unpair.result", child.name()), false);
        return 1;
    }

    private static int runInfoHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("familytree.command.info.help.title"), false);
        source.sendSuccess(() -> Component.translatable("familytree.command.info.help.summary"), false);
        source.sendSuccess(() -> Component.translatable("familytree.command.info.help.commands"), false);
        source.sendSuccess(() -> Component.translatable("familytree.command.info.help.scan"), false);
        source.sendSuccess(() -> Component.translatable("familytree.command.info.help.pair"), false);
        source.sendSuccess(() -> Component.translatable("familytree.command.info.help.unpair"), false);
        source.sendSuccess(() -> Component.translatable("familytree.command.info.help.setage"), false);
        source.sendSuccess(() -> Component.translatable("familytree.command.info.help.setbirth"), false);
        return 1;
    }

    private static int runSetAge(CommandSourceStack source, String name, long days) {
        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        AnimalRecord pet = findByName(state, name);
        if (pet == null) {
            source.sendFailure(Component.translatable("familytree.command.info.unknown", name));
            return 0;
        }

        long anchorDay = pet.deceased() && pet.deathWorldDay() != null
                ? pet.deathWorldDay()
                : TimeUtil.currentWorldDay(source.getLevel());
        if (days > anchorDay) {
            source.sendFailure(Component.translatable("familytree.command.setage.invalid", days, anchorDay));
            return 0;
        }

        long birthDay = anchorDay - days;
        state.update(pet.id(), record -> record.setBirthWorldDay(birthDay));
        source.sendSuccess(() -> Component.translatable("familytree.command.setage.result",
                pet.name(), days, birthDay), false);
        return 1;
    }

    private static int runSetBirthDay(CommandSourceStack source, String name, long birthDay) {
        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        AnimalRecord pet = findByName(state, name);
        if (pet == null) {
            source.sendFailure(Component.translatable("familytree.command.info.unknown", name));
            return 0;
        }

        long maxDay = pet.deceased() && pet.deathWorldDay() != null
                ? pet.deathWorldDay()
                : TimeUtil.currentWorldDay(source.getLevel());
        if (birthDay > maxDay) {
            source.sendFailure(Component.translatable("familytree.command.setbirth.invalid", birthDay, maxDay));
            return 0;
        }

        state.update(pet.id(), record -> record.setBirthWorldDay(birthDay));
        long ageDays = maxDay - birthDay;
        source.sendSuccess(() -> Component.translatable("familytree.command.setbirth.result",
                pet.name(), birthDay, ageDays), false);
        return 1;
    }

    private static int runPair(CommandSourceStack source, String parentAName, String parentBName, String childName) {
        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        AnimalRecord parentA = findByName(state, parentAName);
        AnimalRecord parentB = findByName(state, parentBName);
        AnimalRecord child = findByName(state, childName);

        if (parentA == null) {
            source.sendFailure(Component.translatable("familytree.command.info.unknown", parentAName));
            return 0;
        }
        if (parentB == null) {
            source.sendFailure(Component.translatable("familytree.command.info.unknown", parentBName));
            return 0;
        }
        if (child == null) {
            source.sendFailure(Component.translatable("familytree.command.info.unknown", childName));
            return 0;
        }
        if (parentA.id().equals(parentB.id())) {
            source.sendFailure(Component.translatable("familytree.command.pair.same_parent"));
            return 0;
        }
        if (child.id().equals(parentA.id()) || child.id().equals(parentB.id())) {
            source.sendFailure(Component.translatable("familytree.command.pair.child_matches_parent"));
            return 0;
        }
        if (!parentA.speciesId().equals(parentB.speciesId()) || !parentA.speciesId().equals(child.speciesId())) {
            source.sendFailure(Component.translatable("familytree.command.pair.species_mismatch"));
            return 0;
        }

        state.update(child.id(), record -> record.setParents(parentA.id(), parentB.id()));
        source.sendSuccess(() -> Component.translatable("familytree.command.pair.result",
                parentA.name(), parentB.name(), child.name()), false);
        return 1;
    }

    private static AnimalRecord findByName(FamilyTreeState state, String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return state.all().stream()
                .filter(record -> record.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private static String formatLine(AnimalRecord r, long currentDay) {
        long worldAge = r.deceased() && r.deathWorldDay() != null
                ? r.deathWorldDay() - r.birthWorldDay()
                : currentDay - r.birthWorldDay();

        String suffix = r.deceased()
                ? " (deceased; lived " + worldAge + " world-days)"
                : " - born day " + r.birthWorldDay() + " (" + worldAge + " world-days old)";
        return "- " + r.name() + " [" + shortSpecies(r.speciesId()) + "]" + suffix;
    }

    private static String shortSpecies(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
