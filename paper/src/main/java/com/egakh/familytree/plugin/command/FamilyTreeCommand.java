package com.egakh.familytree.plugin.command;

import com.egakh.familytree.breeding.BreedingPlan;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FarmRecords;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.permissions.FamilyTreePermission;
import com.egakh.familytree.plugin.Access;
import com.egakh.familytree.plugin.Chat;
import com.egakh.familytree.plugin.FamilyTreePlugin;
import com.egakh.familytree.plugin.WorldClock;
import com.egakh.familytree.plugin.storage.FamilyTreeStore;
import com.egakh.familytree.plugin.text.GameText;
import com.egakh.familytree.plugin.text.PlanLines;
import com.egakh.familytree.plugin.text.Messages;
import com.egakh.familytree.plugin.text.Text;
import com.egakh.familytree.plugin.track.Tracker;
import com.egakh.familytree.text.UsageLine;
import com.egakh.familytree.util.Genealogy;
import com.egakh.familytree.util.HorseStatUnits;
import com.egakh.familytree.util.Kinship;
import com.egakh.familytree.util.PetNames;
import com.egakh.familytree.util.PetRelations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;

/** {@code /familytree} with the mod's subcommands, permission nodes, and messages. */
public final class FamilyTreeCommand implements TabExecutor {
    private static final List<String> SUBCOMMANDS = List.of("menu", "list", "scan", "locate", "info", "pair", "unpair",
            "setage", "setbirth", "breed", "prune", "reload", "settings", "confirmlink", "cancellink");
    private final FamilyTreePlugin plugin;
    private final Set<String> registeredAliases = new LinkedHashSet<>();
    private List<String> startupAliases;

    public FamilyTreeCommand(FamilyTreePlugin plugin) {
        this.plugin = plugin;
    }

    private Chat chat() { return plugin.chat(); }
    private Access access() { return plugin.access(); }
    private FamilyTreeStore store() { return plugin.store(); }

    public void register() {
        PluginCommand command = plugin.getCommand("familytree");
        if (command == null) throw new IllegalStateException("plugin.yml must declare /familytree");
        command.setExecutor(this);
        command.setTabCompleter(this);
        startupAliases = plugin.settings().commandAliases();
        registerAliases(startupAliases);
    }

    /** New aliases from a reload work at once. Removing one needs a restart, as in the mod. */
    public boolean aliasesChangedSinceStartup() {
        return startupAliases != null && !startupAliases.equals(plugin.settings().commandAliases());
    }

    public void registerAliases(List<String> aliases) {
        CommandMap map = commandMap();
        if (map == null) {
            if (!aliases.isEmpty()) plugin.getLogger().warning("Family Tree could not reach the command map, so commandAliases are skipped");
            return;
        }
        boolean added = false;
        for (String alias : aliases) {
            if (registeredAliases.contains(alias)) continue;
            if (map.getCommand(alias) != null) {
                plugin.getLogger().warning("Family Tree skipped the command alias /" + alias + " because another command uses it");
                continue;
            }
            Command forward = new Command(alias) {
                @Override public boolean execute(CommandSender sender, String label, String[] args) {
                    return onCommand(sender, this, label, args);
                }

                @Override public List<String> tabComplete(CommandSender sender, String label, String[] args) {
                    return onTabComplete(sender, this, label, args);
                }
            };
            forward.setDescription("Family Tree");
            map.register("familytree", forward);
            registeredAliases.add(alias);
            added = true;
        }
        if (added) {
            Object server = Bukkit.getServer();
            com.egakh.familytree.plugin.track.Compat.call(server, "syncCommands");
            for (Player player : Bukkit.getOnlinePlayers()) player.updateCommands();
        }
    }

    private static CommandMap commandMap() {
        Object map = com.egakh.familytree.plugin.track.Compat.call(Bukkit.getServer(), "getCommandMap");
        return map instanceof CommandMap commandMap ? commandMap : null;
    }

    private boolean denied(CommandSender sender, FamilyTreePermission permission) {
        if (access().allowed(sender, permission)) return false;
        chat().fail(sender, "familytree.permission.denied", permission.node());
        return true;
    }

    /** Commands share the screens' cooldowns. The console has no player and no cooldown. */
    private boolean busy(CommandSender sender, Predicate<UUID> limiter) {
        if (!(sender instanceof Player player) || limiter.test(player.getUniqueId())) return false;
        chat().fail(sender, "familytree.command.cooldown");
        return true;
    }

    /** The node, then the edit cooldown, then a writable store, before any name lookup. */
    private boolean mayEdit(CommandSender sender, FamilyTreePermission permission) {
        if (denied(sender, permission) || busy(sender, plugin.cooldowns()::edit)) return false;
        if (!store().readOnly()) return true;
        chat().fail(sender, "familytree.command.read_only");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] raw) {
        if (denied(sender, FamilyTreePermission.USE)) return true;
        Args args = new Args(raw);
        String sub = args.next();
        if (sub == null) {
            runRoot(sender);
            return true;
        }
        switch (sub.toLowerCase(Locale.ROOT)) {
            case "menu" -> runMenu(sender);
            case "list" -> runList(sender);
            case "scan" -> { if (!denied(sender, FamilyTreePermission.SCAN)) runScan(sender); }
            case "locate" -> {
                String name = args.rest();
                if (name.isEmpty()) {
                    if (!denied(sender, FamilyTreePermission.LOCATE)) usage(sender, "/" + label + " locate <name>");
                } else runLocate(sender, name);
            }
            case "info" -> {
                String name = args.rest();
                if (name.isEmpty()) runInfoHelp(sender);
                else runInfo(sender, name);
            }
            case "pair" -> {
                String a = args.next(), b = args.next(), child = args.next();
                if (child == null || !args.done()) {
                    if (!denied(sender, FamilyTreePermission.PARENTS)) usage(sender, "/" + label + " pair <parent_a> <parent_b> <child>");
                } else runPair(sender, a, b, child);
            }
            case "unpair" -> {
                String child = args.next();
                if (child == null || !args.done()) {
                    if (!denied(sender, FamilyTreePermission.PARENTS)) usage(sender, "/" + label + " unpair <child>");
                } else runUnpair(sender, child);
            }
            case "setage", "setbirth" -> {
                String name = args.next();
                Long value = number(args.next());
                if (name == null || value == null || value < 0 || !args.done()) {
                    if (!denied(sender, FamilyTreePermission.AGE)) usage(sender, "/" + label + " " + sub.toLowerCase(Locale.ROOT) + (sub.equalsIgnoreCase("setage") ? " <name> <days>" : " <name> <day>"));
                } else if (sub.equalsIgnoreCase("setage")) runSetAge(sender, name, value);
                else runSetBirthDay(sender, name, value);
            }
            case "breed" -> {
                if (denied(sender, FamilyTreePermission.BREED)) break;
                String first = args.next(), second = args.next();
                if (second == null || !args.done()) usage(sender, "/" + label + " breed <first> <second>");
                else runBreed(sender, first, second);
            }
            case "prune" -> {
                if (denied(sender, FamilyTreePermission.PRUNE)) break;
                String what = args.next();
                if ("deceased".equalsIgnoreCase(what)) runPrune(sender, AnimalRecord::deceased);
                else if ("farm".equalsIgnoreCase(what)) runPrune(sender, record -> record.deceased() && FarmRecords.unnamedFarm(record));
                else if ("species".equalsIgnoreCase(what) && !args.done()) {
                    String species = args.rest().trim();
                    runPrune(sender, record -> record.speciesId().equalsIgnoreCase(species));
                } else usage(sender, "/" + label + " prune <deceased|farm|species <id>>");
            }
            case "reload" -> { if (!denied(sender, FamilyTreePermission.RELOAD)) SettingsCommands.reload(plugin, sender); }
            case "settings" -> {
                if (denied(sender, FamilyTreePermission.SETTINGS)) break;
                String all = args.next();
                SettingsCommands.show(plugin, sender, "all".equalsIgnoreCase(all));
            }
            case "confirmlink" -> plugin.linkingStick().confirm(sender);
            case "cancellink" -> plugin.linkingStick().cancel(sender);
            default -> runRoot(sender);
        }
        return true;
    }

    private void usage(CommandSender sender, String usage) {
        chat().fail(sender, Text.literal(usage));
    }

    private static Long number(String raw) {
        if (raw == null) return null;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /** Players without the client mod get the chest menu. Players with it are reminded of their key. */
    private void runRoot(CommandSender sender) {
        Player player = sender instanceof Player p ? p : null;
        boolean clientMod = player != null && plugin.bridge().hasClientMod(player);
        if (player != null && !clientMod && plugin.settings().vanillaMenu() && access().allowed(sender, FamilyTreePermission.MENU)) {
            plugin.menus().open(player);
            return;
        }
        String usage = Messages.template(chat().language(sender), "familytree.command.usage");
        if (usage != null) chat().send(sender, Text.literal(UsageLine.filter(usage, sub -> mayRunFromUsage(sender, sub))));
        if (clientMod) chat().send(sender, "familytree.command.menu_hint", Text.keybind("key.familytree.open_tree"));
        if (access().allowed(sender, FamilyTreePermission.RELOAD) || access().allowed(sender, FamilyTreePermission.SETTINGS))
            chat().send(sender, "familytree.command.admin_usage");
    }

    private boolean mayRunFromUsage(CommandSender sender, String sub) {
        if (sub.equals("menu") && (!(sender instanceof Player) || !plugin.settings().vanillaMenu())) return false;
        return mayUse(sender, sub);
    }

    private void runMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            chat().fail(sender, "familytree.menu.players_only");
            return;
        }
        if (!plugin.settings().vanillaMenu()) {
            chat().fail(sender, "familytree.menu.disabled");
            return;
        }
        if (denied(sender, FamilyTreePermission.MENU)) return;
        plugin.menus().open(player);
    }

    private void runList(CommandSender sender) {
        List<AnimalRecord> all = store().all().stream().filter(record -> access().canView(sender, record))
                .sorted(Comparator.comparing(AnimalRecord::name, String.CASE_INSENSITIVE_ORDER)).toList();
        if (all.isEmpty()) {
            chat().send(sender, "familytree.screen.empty");
            return;
        }
        chat().send(sender, "familytree.command.list.header");
        long currentDay = WorldClock.currentWorldDay();
        int limit = plugin.settings().listLimit();
        for (AnimalRecord record : all.subList(0, Math.min(limit, all.size()))) chat().send(sender, formatLine(sender, record, currentDay));
        if (all.size() > limit) chat().send(sender, chat().tr(sender, "familytree.command.list.more", all.size() - limit).color(Text.Color.GRAY));
    }

    private void runInfo(CommandSender sender, String name) {
        FamilyTreeStore state = store();
        AnimalRecord match = find(state, name, sender, false, id -> "/familytree info " + id);
        if (match == null) return;
        String language = chat().language(sender);
        long currentDay = WorldClock.currentWorldDay();
        chat().send(sender, formatLine(sender, match, currentDay).color(Text.Color.YELLOW));
        Map<UUID, AnimalRecord> visible = new HashMap<>();
        for (AnimalRecord record : state.all()) if (access().canView(sender, record)) visible.put(record.id(), record);
        chat().send(sender, "familytree.command.info.generation", Genealogy.generationOf(match.id(), visible));
        Text variant = GameText.variant(language, match);
        if (variant != null) {
            chat().send(sender, GameText.rare(match) ? "familytree.command.info.variant_rare" : "familytree.command.info.variant", variant);
            Text parents = GameText.variantParents(language, match, visible::get), note = GameText.variantNote(language, match, visible::get);
            if (parents != null) chat().send(sender, Text.literal("  ").append(parents));
            if (note != null) chat().send(sender, Text.literal("  ").append(note));
        }
        HorseStats stats = match.horseStats();
        if (stats != null) {
            chat().send(sender, "familytree.command.info.horse_stats", HorseStatUnits.Stat.SPEED.display(stats),
                    HorseStatUnits.Stat.JUMP.display(stats), HorseStatUnits.Stat.HEALTH.display(stats));
            if (stats.strength() != null) chat().send(sender, "familytree.command.info.llama_strength", stats.strength(),
                    HorseStatUnits.chestSlots(stats.strength()));
        }
        AnimalRecord parentA = state.get(match.parentA());
        if (parentA != null && access().canView(sender, parentA)) chat().send(sender, "familytree.command.info.parent_a", parentA.name());
        AnimalRecord parentB = state.get(match.parentB());
        if (parentB != null && access().canView(sender, parentB)) chat().send(sender, "familytree.command.info.parent_b", parentB.name());
    }

    /** Kinship uses only the animals this sender may view, as the screens do. */
    private void runBreed(CommandSender sender, String firstName, String secondName) {
        FamilyTreeStore state = store();
        AnimalRecord first = find(state, firstName, sender, false, id -> "/familytree breed " + id + " " + Args.quoteIfNeeded(secondName));
        if (first == null) return;
        AnimalRecord second = find(state, secondName, sender, false, id -> "/familytree breed " + Args.quoteIfNeeded(firstName) + " " + id);
        if (second == null) return;
        Map<UUID, AnimalRecord> visible = new HashMap<>();
        for (AnimalRecord record : state.all()) if (access().canView(sender, record)) visible.put(record.id(), record);
        BreedingPlan plan = new BreedingPlan(first, second, new Kinship(visible));
        for (Text line : PlanLines.chat(chat().language(sender), plan)) chat().send(sender, line);
    }

    private void runInfoHelp(CommandSender sender) {
        for (String key : List.of("title", "summary", "commands", "scan", "pair", "unpair", "setage", "setbirth", "breed"))
            chat().send(sender, "familytree.command.info.help." + key);
    }

    private void runLocate(CommandSender sender, String typed) {
        if (denied(sender, FamilyTreePermission.LOCATE) || busy(sender, plugin.cooldowns()::locate)) return;
        FamilyTreeStore state = store();
        String name = PetNames.unquote(typed);
        PetNames.Lookup lookup = PetNames.find(state.all(), typed, record -> access().canView(sender, record),
                record -> access().canManage(sender, record));
        if (lookup.outcome() == PetNames.Outcome.NOT_ALLOWED) {
            chat().fail(sender, "familytree.access.denied");
            return;
        }
        List<AnimalRecord> matches = lookup.matches();
        if (matches.isEmpty()) {
            chat().fail(sender, "familytree.command.info.unknown", name);
            return;
        }
        if (matches.size() > 1) chat().send(sender, chat().tr(sender, "familytree.command.locate.multiple", matches.size(), name).color(Text.Color.GRAY));
        for (AnimalRecord pet : matches) locateOne(sender, state, pet);
    }

    /** {@code lastSeenDay} is null when the animal is loaded and the position is live. */
    public record PetPosition(double x, double y, double z, String dimension, Long lastSeenDay) {}

    /** The live position of a loaded animal, which also refreshes its record, or else its last known position. */
    public PetPosition position(FamilyTreeStore state, AnimalRecord pet) {
        Entity live = Bukkit.getEntity(pet.id());
        if (live != null && live.isValid()) {
            plugin.tracker().stampLastSeen(state, live);
            Location location = live.getLocation();
            return new PetPosition(location.getX(), location.getY(), location.getZ(), WorldClock.dimension(live.getWorld()), null);
        }
        AnimalRecord.LastSeen seen = pet.lastSeen();
        return seen == null ? null : new PetPosition(seen.x(), seen.y(), seen.z(), seen.dimension(), seen.worldDay());
    }

    private void locateOne(CommandSender sender, FamilyTreeStore state, AnimalRecord pet) {
        PetPosition position = position(state, pet);
        if (position == null) {
            chat().fail(sender, "familytree.command.locate.no_position", pet.name());
            return;
        }
        int bx = (int) Math.round(position.x()), by = (int) Math.round(position.y()), bz = (int) Math.round(position.z());
        Text coords = Text.literal(bx + ", " + by + ", " + bz);
        if (plugin.settings().locateTeleportLink() && access().operator(sender))
            coords.color(Text.Color.AQUA).underlined(true).suggest("/execute in " + position.dimension() + " run tp @s " + bx + " " + by + " " + bz);
        Text where = dimensionName(sender, position.dimension());
        Text result = pet.deceased()
                ? chat().tr(sender, "familytree.command.locate.deceased_result", pet.name(),
                        pet.deathWorldDay() != null ? pet.deathWorldDay() : 0, coords, where)
                : chat().tr(sender, "familytree.command.locate.result", pet.name(), coords, where);
        if (sender instanceof Entity entity && WorldClock.dimension(entity.getWorld()).equals(position.dimension())) {
            Location here = entity.getLocation();
            double dx = here.getX() - position.x(), dy = here.getY() - position.y(), dz = here.getZ() - position.z();
            result.append(chat().tr(sender, "familytree.command.locate.distance", Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz))));
        }
        if (position.lastSeenDay() != null && !pet.deceased())
            result.append(chat().tr(sender, "familytree.command.locate.last_seen", position.lastSeenDay()).color(Text.Color.GRAY));
        chat().send(sender, result);
    }

    public boolean locatePet(CommandSender sender, UUID id) {
        FamilyTreeStore state = store();
        AnimalRecord pet = state.get(id);
        if (denied(sender, FamilyTreePermission.LOCATE)) return false;
        if (!access().canManage(sender, pet)) {
            chat().fail(sender, "familytree.access.denied");
            return false;
        }
        locateOne(sender, state, pet);
        return true;
    }

    private void runScan(CommandSender sender) {
        UUID player = sender instanceof Player p ? p.getUniqueId() : null;
        if (!plugin.cooldowns().scan(player)) {
            chat().fail(sender, "familytree.command.cooldown");
            return;
        }
        Tracker.ScanResult result = plugin.tracker().scanLoadedPets(record -> access().canView(sender, record));
        chat().send(sender, "familytree.command.scan.result", result.imported(), result.refreshed());
    }

    private void runPrune(CommandSender sender, Predicate<AnimalRecord> which) {
        FamilyTreeStore state = store();
        if (state.readOnly()) {
            chat().fail(sender, "familytree.command.read_only");
            return;
        }
        try {
            var path = state.backup(plugin.files().backups(), "before-prune-", true);
            chat().send(sender, "familytree.command.backup", path.getFileName().toString());
        } catch (java.io.IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Prune canceled because the backup failed", failure);
            chat().fail(sender, "familytree.command.backup_failed");
            return;
        }
        int removed = state.removeMatching(which);
        chat().send(sender, "familytree.command.prune.result", removed);
    }

    private void runUnpair(CommandSender sender, String childName) {
        if (!mayEdit(sender, FamilyTreePermission.PARENTS)) return;
        FamilyTreeStore state = store();
        AnimalRecord child = find(state, childName, sender, true, id -> "/familytree unpair " + id);
        if (child == null) return;
        state.update(child.id(), record -> record.setParents(null, null));
        chat().send(sender, "familytree.command.unpair.result", child.name());
    }

    private void runSetAge(CommandSender sender, String name, long days) {
        if (!mayEdit(sender, FamilyTreePermission.AGE)) return;
        FamilyTreeStore state = store();
        AnimalRecord pet = find(state, name, sender, true, id -> "/familytree setage " + id + " " + days);
        if (pet == null) return;
        long anchorDay = pet.deceased() && pet.deathWorldDay() != null ? pet.deathWorldDay() : WorldClock.currentWorldDay();
        if (days > anchorDay) {
            chat().fail(sender, "familytree.command.setage.invalid", days, anchorDay);
            return;
        }
        long birthDay = anchorDay - days;
        state.update(pet.id(), record -> record.setBirthWorldDay(birthDay));
        if (days == 1) chat().send(sender, "familytree.command.setage.result_one", pet.name(), birthDay);
        else chat().send(sender, "familytree.command.setage.result", pet.name(), days, birthDay);
    }

    private void runSetBirthDay(CommandSender sender, String name, long birthDay) {
        if (!mayEdit(sender, FamilyTreePermission.AGE)) return;
        FamilyTreeStore state = store();
        AnimalRecord pet = find(state, name, sender, true, id -> "/familytree setbirth " + id + " " + birthDay);
        if (pet == null) return;
        long maxDay = pet.deceased() && pet.deathWorldDay() != null ? pet.deathWorldDay() : WorldClock.currentWorldDay();
        if (birthDay > maxDay) {
            chat().fail(sender, "familytree.command.setbirth.invalid", birthDay, maxDay);
            return;
        }
        state.update(pet.id(), record -> record.setBirthWorldDay(birthDay));
        long ageDays = maxDay - birthDay;
        if (ageDays == 1) chat().send(sender, "familytree.command.setbirth.result_one", pet.name(), birthDay);
        else chat().send(sender, "familytree.command.setbirth.result", pet.name(), birthDay, ageDays);
    }

    private void runPair(CommandSender sender, String parentAName, String parentBName, String childName) {
        if (!mayEdit(sender, FamilyTreePermission.PARENTS)) return;
        FamilyTreeStore state = store();
        String a = Args.quoteIfNeeded(parentAName), b = Args.quoteIfNeeded(parentBName), c = Args.quoteIfNeeded(childName);
        AnimalRecord parentA = find(state, parentAName, sender, true, id -> "/familytree pair " + id + " " + b + " " + c);
        if (parentA == null) return;
        AnimalRecord parentB = find(state, parentBName, sender, true, id -> "/familytree pair " + a + " " + id + " " + c);
        if (parentB == null) return;
        AnimalRecord child = find(state, childName, sender, true, id -> "/familytree pair " + a + " " + b + " " + id);
        if (child == null) return;
        if (parentA.id().equals(parentB.id())) {
            chat().fail(sender, "familytree.command.pair.same_parent");
            return;
        }
        if (child.id().equals(parentA.id()) || child.id().equals(parentB.id())) {
            chat().fail(sender, "familytree.command.pair.child_matches_parent");
            return;
        }
        if (!PetRelations.speciesMatch(parentA, parentB, child)) {
            chat().fail(sender, "familytree.command.pair.species_mismatch");
            return;
        }
        if (Genealogy.wouldCreateCycle(child.id(), parentA.id(), parentB.id(), state::get)) {
            chat().fail(sender, "familytree.command.pair.cycle");
            return;
        }
        state.update(child.id(), record -> record.setParents(parentA.id(), parentB.id()));
        chat().send(sender, "familytree.command.pair.result", parentA.name(), parentB.name(), child.name());
    }

    /**
     * Resolves one pet and explains any failure once. {@code retry} rebuilds the command with a UUID in place of
     * this name, so each choice of an ambiguous name can be clicked.
     */
    private AnimalRecord find(FamilyTreeStore state, String name, CommandSender sender, boolean manage, Function<UUID, String> retry) {
        Predicate<AnimalRecord> visible = pet -> access().canView(sender, pet);
        PetNames.Lookup lookup = PetNames.find(state.all(), name, visible, manage ? pet -> access().canManage(sender, pet) : visible);
        switch (lookup.outcome()) {
            case FOUND -> {
                return lookup.pet();
            }
            case AMBIGUOUS -> {
                chat().fail(sender, "familytree.command.ambiguous", PetNames.unquote(name));
                for (AnimalRecord pet : lookup.matches().stream().limit(10).toList())
                    chat().fail(sender, Text.literal(pet.name() + " | ").append(GameText.species(pet.speciesId())).append(" | ")
                            .append(Text.literal(pet.id().toString()).color(Text.Color.AQUA).underlined(true))
                            .suggest(retry.apply(pet.id())));
            }
            case NOT_ALLOWED -> chat().fail(sender, "familytree.access.denied");
            case UNKNOWN -> chat().fail(sender, "familytree.command.info.unknown", PetNames.unquote(name));
        }
        return null;
    }

    private Text formatLine(CommandSender sender, AnimalRecord record, long currentDay) {
        String language = chat().language(sender);
        long worldAge = record.deceased() && record.deathWorldDay() != null
                ? record.deathWorldDay() - record.birthWorldDay() : currentDay - record.birthWorldDay();
        Text species = GameText.species(record.speciesId());
        if (!record.deceased())
            return chat().tr(sender, "familytree.command.list.alive", record.name(), species, record.birthWorldDay(), worldAge);
        Text death = GameText.death(language, record);
        if (death == null) return chat().tr(sender, "familytree.command.list.deceased", record.name(), species, worldAge);
        return record.deathCause().msgId() != null
                ? chat().tr(sender, "familytree.command.list.died", death, species, worldAge)
                : chat().tr(sender, "familytree.command.list.deceased_cause", record.name(), species, worldAge, death);
    }

    private Text dimensionName(CommandSender sender, String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> chat().tr(sender, "familytree.dimension.overworld");
            case "minecraft:the_nether" -> chat().tr(sender, "familytree.dimension.the_nether");
            case "minecraft:the_end" -> chat().tr(sender, "familytree.dimension.the_end");
            default -> Text.literal(dimension.substring(dimension.indexOf(':') + 1));
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!access().allowed(sender, FamilyTreePermission.USE) || args.length == 0) return List.of();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            List<String> visible = new ArrayList<>();
            for (String sub : SUBCOMMANDS) if (mayUse(sender, sub) && sub.startsWith(last)) visible.add(sub);
            return visible;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!mayUse(sender, sub)) return List.of();
        return switch (sub) {
            case "info" -> names(sender, false, last, true);
            case "breed" -> args.length <= 3 ? names(sender, false, last, false) : List.of();
            case "locate" -> names(sender, true, last, true);
            case "pair" -> args.length <= 4 ? names(sender, true, last, false) : List.of();
            case "unpair", "setage", "setbirth" -> args.length == 2 ? names(sender, true, last, false) : List.of();
            case "prune" -> args.length == 2 ? filter(List.of("deceased", "farm", "species"), last)
                    : args.length == 3 && args[1].equalsIgnoreCase("species")
                    ? filter(new ArrayList<>(new TreeSet<>(store().all().stream().map(AnimalRecord::speciesId).toList())), last) : List.of();
            case "settings" -> args.length == 2 ? filter(List.of("all"), last) : List.of();
            default -> List.of();
        };
    }

    private boolean mayUse(CommandSender sender, String sub) {
        FamilyTreePermission permission = switch (sub) {
            case "menu" -> FamilyTreePermission.MENU;
            case "scan" -> FamilyTreePermission.SCAN;
            case "locate" -> FamilyTreePermission.LOCATE;
            case "pair", "unpair" -> FamilyTreePermission.PARENTS;
            case "setage", "setbirth" -> FamilyTreePermission.AGE;
            case "breed" -> FamilyTreePermission.BREED;
            case "prune" -> FamilyTreePermission.PRUNE;
            case "reload" -> FamilyTreePermission.RELOAD;
            case "settings" -> FamilyTreePermission.SETTINGS;
            default -> FamilyTreePermission.USE;
        };
        return access().allowed(sender, permission);
    }

    private List<String> names(CommandSender sender, boolean manage, String prefix, boolean greedy) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (AnimalRecord pet : store().all()) {
            if (!(manage ? access().canManage(sender, pet) : access().canView(sender, pet))) continue;
            String name = greedy ? pet.name() : Args.quoteIfNeeded(pet.name());
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix) || pet.name().toLowerCase(Locale.ROOT).startsWith(prefix)) names.add(name);
            if (names.size() >= 100) break;
        }
        return new ArrayList<>(names);
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(option);
        return out;
    }
}
