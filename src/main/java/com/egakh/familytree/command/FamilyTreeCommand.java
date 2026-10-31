package com.egakh.familytree.command;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.data.FarmRecords;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.event.PetLifecycleListeners;
import com.egakh.familytree.interaction.LinkingTool;
import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.breeding.BreedingPlan;
import com.egakh.familytree.breeding.PlanText;
import com.egakh.familytree.util.Kinship;
import com.egakh.familytree.menu.Menus;
import com.egakh.familytree.network.payloads.SnapshotPagePayload;
import com.egakh.familytree.platform.ServerTransport;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import com.egakh.familytree.util.DeathMessages;
import com.egakh.familytree.util.Genealogy;
import com.egakh.familytree.util.HorseStatUnits;
import com.egakh.familytree.util.TimeUtil;
import com.egakh.familytree.permissions.FamilyTreePermission;
import com.egakh.familytree.permissions.ServerPetAccess;
import com.egakh.familytree.network.FamilyTreePackets;
import com.egakh.familytree.util.PetNames;
import com.egakh.familytree.util.PetRelations;
import com.egakh.familytree.util.VariantText;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import com.egakh.familytree.text.LangTables;
import com.egakh.familytree.text.ServerText;
import com.egakh.familytree.text.UsageLine;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FamilyTreeCommand {
    private FamilyTreeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("familytree")
                .requires(source -> ServerPetAccess.allowed(source, FamilyTreePermission.USE))
                .executes(ctx -> runRoot(ctx.getSource()))
                .then(Commands.literal("menu").executes(ctx -> runMenu(ctx.getSource())))
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
                .then(Commands.literal("locate")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> runLocate(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("scan")
                        .executes(ctx -> denied(ctx.getSource(), FamilyTreePermission.SCAN) ? 0 : runScan(ctx.getSource())))
                .then(Commands.literal("confirmlink").executes(ctx -> LinkingTool.confirm(ctx.getSource())))
                .then(Commands.literal("cancellink").executes(ctx -> LinkingTool.cancel(ctx.getSource())))
                .then(pruneCommand())
                .then(Commands.literal("breed")
                        .then(Commands.argument("first", StringArgumentType.string())
                                .then(Commands.argument("second", StringArgumentType.string())
                                        .executes(ctx -> runBreed(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "first"),
                                                StringArgumentType.getString(ctx, "second"))))))
                .then(Commands.literal("reload")
                        .executes(ctx -> denied(ctx.getSource(), FamilyTreePermission.RELOAD) ? 0 : SettingsCommands.reload(ctx.getSource())))
                .then(Commands.literal("settings")
                        .executes(ctx -> denied(ctx.getSource(), FamilyTreePermission.SETTINGS) ? 0 : SettingsCommands.show(ctx.getSource(), false))
                        .then(Commands.literal("all").executes(ctx -> denied(ctx.getSource(), FamilyTreePermission.SETTINGS)
                                ? 0 : SettingsCommands.show(ctx.getSource(), true))))
                .then(Commands.literal("info").executes(ctx -> runInfoHelp(ctx.getSource()))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> runInfo(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
        );
        for (String alias : FamilyTreeServerSettings.commandAliases()) {
            if (dispatcher.getRoot().getChild(alias) != null) {
                FamilyTreeMod.LOGGER.warn("Family Tree skipped the command alias /{} because another command uses it", alias);
                continue;
            }
            dispatcher.register(Commands.literal(alias)
                    .requires(source -> ServerPetAccess.allowed(source, FamilyTreePermission.USE))
                    .executes(ctx -> runRoot(ctx.getSource())).redirect(root));
        }
    }

    /** Players without the client mod get the chest menu. Players with it are reminded of their key. */
    private static int runRoot(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        boolean clientMod = player != null && ServerTransport.canSend(player, SnapshotPagePayload.TYPE);
        if (player != null && !clientMod && FamilyTreeServerSettings.vanillaMenu()
                && ServerPetAccess.allowed(source, FamilyTreePermission.MENU))
            return Menus.open(player);
        String usage = LangTables.template(ServerText.language(source), "familytree.command.usage");
        if (usage != null) {
            String allowed = UsageLine.filter(usage, sub -> mayRun(source, sub));
            ServerText.success(source, () -> Component.literal(allowed), false);
        }
        if (clientMod) ServerText.success(source, () -> Component.translatable(mayRun(source, "menu")
                ? "familytree.command.menu_hint" : "familytree.command.key_hint", Component.keybind("key.familytree.open_tree")), false);
        if (ServerPetAccess.allowed(source, FamilyTreePermission.RELOAD) || ServerPetAccess.allowed(source, FamilyTreePermission.SETTINGS))
            ServerText.success(source, () -> Component.translatable("familytree.command.admin_usage"), false);
        return 1;
    }

    /** The subcommands the usage line lists for this player. */
    private static boolean mayRun(CommandSourceStack source, String subcommand) {
        FamilyTreePermission permission = switch (subcommand) {
            case "menu" -> source.getPlayer() != null && FamilyTreeServerSettings.vanillaMenu() ? FamilyTreePermission.MENU : null;
            case "scan" -> FamilyTreePermission.SCAN;
            case "locate" -> FamilyTreePermission.LOCATE;
            case "pair", "unpair" -> FamilyTreePermission.PARENTS;
            case "setage", "setbirth" -> FamilyTreePermission.AGE;
            case "breed" -> FamilyTreePermission.BREED;
            case "prune" -> FamilyTreePermission.PRUNE;
            default -> FamilyTreePermission.USE;
        };
        return permission != null && ServerPetAccess.allowed(source, permission);
    }

    /** Subcommands stay in the command tree for everyone, so a denied node gets this message instead of a parser error. */
    private static boolean denied(CommandSourceStack source, FamilyTreePermission permission) {
        if (ServerPetAccess.allowed(source, permission)) return false;
        ServerText.failure(source, Component.translatable("familytree.permission.denied", permission.node()));
        return true;
    }

    /** Commands share the screens' cooldowns. The console and command blocks have no player and no cooldown. */
    private static boolean busy(CommandSourceStack source, java.util.function.Predicate<ServerPlayer> limiter) {
        ServerPlayer player = source.getPlayer();
        if (player == null || limiter.test(player)) return false;
        ServerText.failure(source, Component.translatable("familytree.command.cooldown"));
        return true;
    }

    private static int runMenu(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            ServerText.failure(source, Component.translatable("familytree.menu.players_only"));
            return 0;
        }
        if (!FamilyTreeServerSettings.vanillaMenu()) {
            ServerText.failure(source, Component.translatable("familytree.menu.disabled"));
            return 0;
        }
        if (!ServerPetAccess.allowed(source, FamilyTreePermission.MENU)) {
            ServerText.failure(source, Component.translatable("familytree.permission.denied", FamilyTreePermission.MENU.node()));
            return 0;
        }
        return Menus.open(player);
    }

    private static int runList(CommandSourceStack source) {
        FamilyTreeState state = readable(source);
        if (state == null) return 0;
        List<AnimalRecord> all = state.all().stream()
                .filter(record -> ServerPetAccess.canView(source, record))
                .sorted(Comparator.comparing(AnimalRecord::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (all.isEmpty()) {
            ServerText.success(source, () -> Component.translatable("familytree.screen.empty"), false);
            return 1;
        }

        ServerText.success(source, () -> Component.translatable("familytree.command.list.header"), false);
        long currentDay = TimeUtil.currentWorldDay(source.getLevel());
        int limit = FamilyTreeServerSettings.current().listLimit();
        for (AnimalRecord r : all.subList(0, Math.min(limit, all.size()))) {
            Component line = formatLine(r, currentDay);
            ServerText.success(source, () -> line, false);
        }
        if (all.size() > limit) ServerText.success(source, () -> Component.translatable("familytree.command.list.more",
                all.size() - limit).withStyle(ChatFormatting.GRAY), false);
        return all.size();
    }

    private static int runInfo(CommandSourceStack source, String name) {
        FamilyTreeState state = readable(source);
        if (state == null) return 0;
        AnimalRecord match = find(source, state, name, false, id -> "/familytree info " + id);
        if (match == null) return 0;

        long currentDay = TimeUtil.currentWorldDay(source.getLevel());
        ServerText.success(source, () -> formatLine(match, currentDay).withStyle(ChatFormatting.YELLOW), false);

        java.util.Map<java.util.UUID, AnimalRecord> visible = new java.util.HashMap<>();
        for (AnimalRecord r : state.all()) if (ServerPetAccess.canView(source, r)) visible.put(r.id(), r);
        int generation = Genealogy.generationOf(match.id(), visible);
        ServerText.success(source, () -> Component.translatable("familytree.command.info.generation", generation), false);
        Component variant = VariantText.label(match);
        if (variant != null) {
            ServerText.success(source, () -> Component.translatable(VariantText.rare(match)
                    ? "familytree.command.info.variant_rare" : "familytree.command.info.variant", variant), false);
            Component parents = VariantText.parents(match, visible::get), note = VariantText.note(match, visible::get);
            if (parents != null) ServerText.success(source, () -> Component.literal("  ").append(parents), false);
            if (note != null) ServerText.success(source, () -> Component.literal("  ").append(note), false);
        }
        HorseStats stats = match.horseStats();
        if (stats != null) {
            ServerText.success(source, () -> Component.translatable("familytree.command.info.horse_stats",
                    HorseStatUnits.Stat.SPEED.display(stats), HorseStatUnits.Stat.JUMP.display(stats),
                    HorseStatUnits.Stat.HEALTH.display(stats)), false);
            if (stats.strength() != null) ServerText.success(source, () -> Component.translatable(
                    "familytree.command.info.llama_strength", stats.strength(), HorseStatUnits.chestSlots(stats.strength())), false);
        }

        if (match.parentA() != null) {
            AnimalRecord pa = state.get(match.parentA());
            if (ServerPetAccess.canView(source, pa)) {
                ServerText.success(source, () -> Component.translatable("familytree.command.info.parent_a", pa.name()), false);
            }
        }
        if (match.parentB() != null) {
            AnimalRecord pb = state.get(match.parentB());
            if (ServerPetAccess.canView(source, pb)) {
                ServerText.success(source, () -> Component.translatable("familytree.command.info.parent_b", pb.name()), false);
            }
        }
        return 1;
    }

    /** Uses only records the player may view, so the relationship never passes through hidden animals. */
    private static int runBreed(CommandSourceStack source, String firstName, String secondName) {
        if (denied(source, FamilyTreePermission.BREED)) return 0;
        FamilyTreeState state = readable(source);
        if (state == null) return 0;
        AnimalRecord first = find(source, state, firstName, false,
                id -> "/familytree breed " + id + " " + StringArgumentType.escapeIfRequired(secondName));
        if (first == null) return 0;
        AnimalRecord second = find(source, state, secondName, false,
                id -> "/familytree breed " + StringArgumentType.escapeIfRequired(firstName) + " " + id);
        if (second == null) return 0;
        java.util.Map<java.util.UUID, AnimalRecord> visible = new java.util.HashMap<>();
        for (AnimalRecord record : state.all()) if (ServerPetAccess.canView(source, record)) visible.put(record.id(), record);
        BreedingPlan plan = new BreedingPlan(first, second, new Kinship(visible));
        for (Component line : PlanText.lines(plan)) ServerText.success(source, () -> line, false);
        return plan.canBreed ? 1 : 0;
    }

    private static int runLocate(CommandSourceStack source, String typed) {
        if (denied(source, FamilyTreePermission.LOCATE)) return 0;
        FamilyTreeState state = readable(source);
        if (state == null || busy(source, FamilyTreePackets::allowLocate)) return 0;
        String name = PetNames.unquote(typed);
        PetNames.Lookup lookup = PetNames.find(state.all(), typed, record -> ServerPetAccess.canView(source, record),
                record -> ServerPetAccess.canManage(source, record));
        if (lookup.outcome() == PetNames.Outcome.NOT_ALLOWED) {
            ServerText.failure(source, Component.translatable("familytree.access.denied"));
            return 0;
        }
        List<AnimalRecord> matches = lookup.matches();
        if (matches.isEmpty()) {
            ServerText.failure(source, Component.translatable("familytree.command.info.unknown", name));
            return 0;
        }

        if (matches.size() > 1) {
            ServerText.success(source, () -> Component.translatable("familytree.command.locate.multiple",
                    matches.size(), name).withStyle(ChatFormatting.GRAY), false);
        }
        int located = 0;
        for (AnimalRecord pet : matches) {
            located += locateOne(source, state, pet);
        }
        return located;
    }

    /** {@code lastSeenDay} is null when the pet is loaded and the position is live. */
    public record PetPosition(double x, double y, double z, String dimension, Long lastSeenDay) {}

    /** The live position of a loaded pet, which also refreshes its record, or else its last known position. */
    public static PetPosition position(MinecraftServer server, FamilyTreeState state, AnimalRecord pet) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity live = level.getEntity(pet.id());
            if (live == null) continue;
            PetLifecycleListeners.stampLastSeen(level, state, live);
            return new PetPosition(live.getX(), live.getY(), live.getZ(), live.level().dimension().identifier().toString(), null);
        }
        AnimalRecord.LastSeen seen = pet.lastSeen();
        return seen == null ? null : new PetPosition(seen.x(), seen.y(), seen.z(), seen.dimension(), seen.worldDay());
    }

    private static int locateOne(CommandSourceStack source, FamilyTreeState state, AnimalRecord pet) {
        PetPosition position = position(source.getServer(), state, pet);
        if (position == null) {
            ServerText.failure(source, Component.translatable("familytree.command.locate.no_position", pet.name()));
            return 0;
        }
        double x = position.x();
        double y = position.y();
        double z = position.z();
        String dimension = position.dimension();
        Long lastSeenDay = position.lastSeenDay();

        int bx = (int) Math.round(x);
        int by = (int) Math.round(y);
        int bz = (int) Math.round(z);
        MutableComponent coords = Component.literal(bx + ", " + by + ", " + bz);
        if (FamilyTreeServerSettings.current().locateTeleportLink() && ServerPetAccess.operator(source)) {
            String tp = "/execute in " + dimension + " run tp @s " + bx + " " + by + " " + bz;
            coords = coords.withStyle(s -> s
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent.SuggestCommand(tp)));
        }

        MutableComponent result = pet.deceased()
                ? Component.translatable("familytree.command.locate.deceased_result", pet.name(),
                        pet.deathWorldDay() != null ? pet.deathWorldDay() : 0, coords, dimensionName(dimension))
                : Component.translatable("familytree.command.locate.result", pet.name(), coords, dimensionName(dimension));
        Entity sender = source.getEntity();
        if (sender != null && sender.level().dimension().identifier().toString().equals(dimension)) {
            long distance = Math.round(Math.sqrt(sender.distanceToSqr(x, y, z)));
            result.append(Component.translatable("familytree.command.locate.distance", distance));
        }
        if (lastSeenDay != null && !pet.deceased()) {
            result.append(Component.translatable("familytree.command.locate.last_seen", lastSeenDay)
                    .withStyle(ChatFormatting.GRAY));
        }
        MutableComponent line = result;
        ServerText.success(source, () -> line, false);
        return 1;
    }

    private static int runScan(CommandSourceStack source) {
        if (readable(source) == null) return 0;
        if (!FamilyTreePackets.allowScan(source.getServer(), source.getPlayer() == null ? null : source.getPlayer().getUUID())) {
            ServerText.failure(source, Component.translatable("familytree.command.cooldown"));
            return 0;
        }
        PetLifecycleListeners.ScanResult result = PetLifecycleListeners.scanLoadedPets(source.getServer(),
                record -> ServerPetAccess.canView(source, record));
        ServerText.success(source, () -> Component.translatable("familytree.command.scan.result",
                result.imported(), result.refreshed()), false);
        return result.totalTouched();
    }

    /**
     * The node is checked in the body, so a denied player gets Family Tree's message instead of an unknown command.
     * Plain Brigadier builders keep this testable: loading {@code Commands} needs Minecraft's registries.
     */
    static LiteralArgumentBuilder<CommandSourceStack> pruneCommand() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("prune")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("deceased")
                        .executes(ctx -> runPruneDeceased(ctx.getSource(), false))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("confirm")
                                .executes(ctx -> runPruneDeceased(ctx.getSource(), true))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("farm")
                        .executes(ctx -> runPruneFarm(ctx.getSource(), false))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("confirm")
                                .executes(ctx -> runPruneFarm(ctx.getSource(), true))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("species")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("species_id", StringArgumentType.greedyString())
                                .executes(ctx -> runPruneSpecies(ctx.getSource(), StringArgumentType.getString(ctx, "species_id")))));
    }

    private static int runPruneDeceased(CommandSourceStack source, boolean confirmed) {
        return runPrune(source, AnimalRecord::deceased, "deceased", confirmed);
    }

    private static int runPruneFarm(CommandSourceStack source, boolean confirmed) {
        return runPrune(source, record -> record.deceased() && FarmRecords.unnamedFarm(record), "farm", confirmed);
    }

    private static int runPruneSpecies(CommandSourceStack source, String argument) {
        PruneRequest request = PruneRequest.species(argument);
        return runPrune(source, record -> record.speciesId().equalsIgnoreCase(request.species()),
                "species " + request.species(), request.confirmed());
    }

    /** The first run only counts the records and asks for {@code confirm}, which backs up and then removes them. */
    private static int runPrune(CommandSourceStack source, java.util.function.Predicate<AnimalRecord> which,
                                String what, boolean confirmed) {
        if (denied(source, FamilyTreePermission.PRUNE)) return 0;
        FamilyTreeState state = readable(source);
        if (state == null) return 0;
        long matching = state.all().stream().filter(which).count();
        if (matching == 0) {
            ServerText.failure(source, Component.translatable("familytree.command.prune.none"));
            return 0;
        }
        if (!confirmed) {
            String command = "/familytree prune " + what + " confirm";
            Component link = Component.literal(command).withStyle(s -> s
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent.SuggestCommand(command)));
            ServerText.success(source, () -> Component.translatable("familytree.command.prune.confirm", matching, link), false);
            return 0;
        }
        if (!backupBeforePrune(source, state)) return 0;
        int removed = state.removeMatching(which);
        ServerText.success(source, () -> Component.translatable("familytree.command.prune.result", removed), false);
        return removed;
    }

    private static int runUnpair(CommandSourceStack source, String childName) {
        if (!mayEdit(source, FamilyTreePermission.PARENTS)) return 0;
        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        AnimalRecord child = find(source, state, childName, true, id -> "/familytree unpair " + id);
        if (child == null) {
            return 0;
        }

        state.update(child.id(), record -> record.setParents(null, null));
        ServerText.success(source, () -> Component.translatable("familytree.command.unpair.result", child.name()), false);
        return 1;
    }

    private static int runInfoHelp(CommandSourceStack source) {
        ServerText.success(source, () -> Component.translatable("familytree.command.info.help.title"), false);
        ServerText.success(source, () -> Component.translatable("familytree.command.info.help.summary"), false);
        ServerText.success(source, () -> Component.translatable("familytree.command.info.help.commands"), false);
        ServerText.success(source, () -> Component.translatable("familytree.command.info.help.scan"), false);
        ServerText.success(source, () -> Component.translatable("familytree.command.info.help.pair"), false);
        ServerText.success(source, () -> Component.translatable("familytree.command.info.help.unpair"), false);
        ServerText.success(source, () -> Component.translatable("familytree.command.info.help.setage"), false);
        ServerText.success(source, () -> Component.translatable("familytree.command.info.help.setbirth"), false);
        ServerText.success(source, () -> Component.translatable("familytree.command.info.help.breed"), false);
        return 1;
    }

    private static int runSetAge(CommandSourceStack source, String name, long days) {
        if (!mayEdit(source, FamilyTreePermission.AGE)) return 0;
        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        AnimalRecord pet = find(source, state, name, true, id -> "/familytree setage " + id + " " + days);
        if (pet == null) {
            return 0;
        }

        long anchorDay = pet.deceased() && pet.deathWorldDay() != null
                ? pet.deathWorldDay()
                : TimeUtil.currentWorldDay(source.getLevel());
        if (days > anchorDay) {
            ServerText.failure(source, days == 1
                    ? Component.translatable("familytree.command.setage.invalid_one", anchorDay)
                    : Component.translatable("familytree.command.setage.invalid", days, anchorDay));
            return 0;
        }

        long birthDay = anchorDay - days;
        state.update(pet.id(), record -> record.setBirthWorldDay(birthDay));
        ServerText.success(source, () -> days == 1
                ? Component.translatable("familytree.command.setage.result_one", pet.name(), birthDay)
                : Component.translatable("familytree.command.setage.result", pet.name(), days, birthDay), false);
        return 1;
    }

    private static int runSetBirthDay(CommandSourceStack source, String name, long birthDay) {
        if (!mayEdit(source, FamilyTreePermission.AGE)) return 0;
        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        AnimalRecord pet = find(source, state, name, true, id -> "/familytree setbirth " + id + " " + birthDay);
        if (pet == null) {
            return 0;
        }

        long maxDay = pet.deceased() && pet.deathWorldDay() != null
                ? pet.deathWorldDay()
                : TimeUtil.currentWorldDay(source.getLevel());
        if (birthDay > maxDay) {
            ServerText.failure(source, Component.translatable("familytree.command.setbirth.invalid", birthDay, maxDay));
            return 0;
        }

        state.update(pet.id(), record -> record.setBirthWorldDay(birthDay));
        long ageDays = maxDay - birthDay;
        ServerText.success(source, () -> ageDays == 1
                ? Component.translatable("familytree.command.setbirth.result_one", pet.name(), birthDay)
                : Component.translatable("familytree.command.setbirth.result", pet.name(), birthDay, ageDays), false);
        return 1;
    }

    private static int runPair(CommandSourceStack source, String parentAName, String parentBName, String childName) {
        if (!mayEdit(source, FamilyTreePermission.PARENTS)) return 0;
        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        String a = StringArgumentType.escapeIfRequired(parentAName), b = StringArgumentType.escapeIfRequired(parentBName),
                c = StringArgumentType.escapeIfRequired(childName);
        AnimalRecord parentA = find(source, state, parentAName, true, id -> "/familytree pair " + id + " " + b + " " + c);
        if (parentA == null) return 0;
        AnimalRecord parentB = find(source, state, parentBName, true, id -> "/familytree pair " + a + " " + id + " " + c);
        if (parentB == null) return 0;
        AnimalRecord child = find(source, state, childName, true, id -> "/familytree pair " + a + " " + b + " " + id);
        if (child == null) return 0;
        if (parentA.id().equals(parentB.id())) {
            ServerText.failure(source, Component.translatable("familytree.command.pair.same_parent"));
            return 0;
        }
        if (child.id().equals(parentA.id()) || child.id().equals(parentB.id())) {
            ServerText.failure(source, Component.translatable("familytree.command.pair.child_matches_parent"));
            return 0;
        }
        if (!PetRelations.speciesMatch(parentA, parentB, child)) {
            ServerText.failure(source, Component.translatable("familytree.command.pair.species_mismatch"));
            return 0;
        }

        if (Genealogy.wouldCreateCycle(child.id(), parentA.id(), parentB.id(), state::get)) {
            ServerText.failure(source, Component.translatable("familytree.command.pair.cycle"));
            return 0;
        }

        state.update(child.id(), record -> record.setParents(parentA.id(), parentB.id()));
        ServerText.success(source, () -> Component.translatable("familytree.command.pair.result",
                parentA.name(), parentB.name(), child.name()), false);
        return 1;
    }

    /** The node, then the edit cooldown, then a writable store, before any name lookup. */
    private static boolean mayEdit(CommandSourceStack source, FamilyTreePermission permission) {
        return !denied(source, permission) && !busy(source, FamilyTreePackets::allowEdit) && readable(source) != null;
    }

    /** An unreadable history holds no pets, so commands say it couldn't be read instead of finding nothing. */
    private static FamilyTreeState readable(CommandSourceStack source) {
        FamilyTreeState state = FamilyTreeState.get(source.getServer());
        if (!state.readOnly()) return state;
        ServerText.failure(source, Component.translatable("familytree.command.read_only"));
        return null;
    }

    /**
     * Resolves one pet and explains any failure once. {@code retry} rebuilds the command with a UUID in place of
     * this name, so each choice of an ambiguous name can be clicked.
     */
    private static AnimalRecord find(CommandSourceStack source, FamilyTreeState state, String name, boolean manage,
                                     java.util.function.Function<java.util.UUID, String> retry) {
        java.util.function.Predicate<AnimalRecord> visible = pet -> ServerPetAccess.canView(source, pet);
        PetNames.Lookup lookup = PetNames.find(state.all(), name, visible,
                manage ? pet -> ServerPetAccess.canManage(source, pet) : visible);
        switch (lookup.outcome()) {
            case FOUND -> {
                return lookup.pet();
            }
            case AMBIGUOUS -> {
                ServerText.failure(source, Component.translatable("familytree.command.ambiguous", PetNames.unquote(name)));
                for (AnimalRecord pet : lookup.matches().stream().limit(10).toList()) {
                    String command = retry.apply(pet.id());
                    MutableComponent line = Component.literal(pet.name() + " | ").append(speciesName(pet.speciesId())).append(" | ")
                            .append(Component.literal(pet.id().toString()).withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE));
                    ServerText.failure(source, line.withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(command))));
                }
            }
            case NOT_ALLOWED -> ServerText.failure(source, Component.translatable("familytree.access.denied"));
            case UNKNOWN -> ServerText.failure(source, Component.translatable("familytree.command.info.unknown", PetNames.unquote(name)));
        }
        return null;
    }

    private static boolean backupBeforePrune(CommandSourceStack source, FamilyTreeState state) {
        if (state.readOnly()) {
            ServerText.failure(source, Component.translatable("familytree.command.read_only"));
            return false;
        }
        try {
            var path = state.backup(source.getServer());
            ServerText.success(source, () -> Component.translatable("familytree.command.backup", path.getFileName().toString()), false);
            return true;
        } catch (java.io.IOException | RuntimeException failure) {
            com.egakh.familytree.FamilyTreeMod.LOGGER.error("Prune canceled because the backup failed", failure);
            ServerText.failure(source, Component.translatable("familytree.command.backup_failed"));
            return false;
        }
    }

    public static int locatePet(CommandSourceStack source, java.util.UUID id) {
        if (!ServerPetAccess.allowed(source, FamilyTreePermission.LOCATE)) {
            ServerText.failure(source, Component.translatable("familytree.permission.denied", FamilyTreePermission.LOCATE.node()));
            return 0;
        }
        FamilyTreeState state = readable(source);
        if (state == null) return 0;
        AnimalRecord pet = state.get(id);
        if (!ServerPetAccess.canManage(source, pet)) {
            ServerText.failure(source, Component.translatable("familytree.access.denied"));
            return 0;
        }
        return locateOne(source, state, pet);
    }

    private static MutableComponent formatLine(AnimalRecord r, long currentDay) {
        long worldAge = r.deceased() && r.deathWorldDay() != null
                ? r.deathWorldDay() - r.birthWorldDay()
                : currentDay - r.birthWorldDay();
        Component species = speciesName(r.speciesId());
        if (!r.deceased()) return Component.translatable("familytree.command.list.alive", r.name(), species, r.birthWorldDay(), worldAge);
        Component death = DeathMessages.of(r);
        if (death == null) return Component.translatable("familytree.command.list.deceased", r.name(), species, worldAge);
        return r.deathCause().msgId() != null
                ? Component.translatable("familytree.command.list.died", death, species, worldAge)
                : Component.translatable("familytree.command.list.deceased_cause", r.name(), species, worldAge, death);
    }

    private static Component speciesName(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return Component.translatableWithFallback("entity." + namespace + "." + path, path);
    }

    private static Component dimensionName(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> Component.translatable("familytree.dimension.overworld");
            case "minecraft:the_nether" -> Component.translatable("familytree.dimension.the_nether");
            case "minecraft:the_end" -> Component.translatable("familytree.dimension.the_end");
            default -> Component.literal(dimension.substring(dimension.indexOf(':') + 1));
        };
    }
}
