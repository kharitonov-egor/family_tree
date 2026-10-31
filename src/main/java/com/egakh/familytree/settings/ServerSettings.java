package com.egakh.familytree.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Every value in {@code config/familytree-server.properties}. The defaults match Family Tree 1.3.0. */
public record ServerSettings(ViewPolicy viewPolicy, int operatorLevel, OwnerActions ownerActions,
                             boolean vanillaMenu, List<String> commandAliases, int listLimit, Cooldowns cooldowns,
                             boolean linkingStick, String linkingStickName, int linkingSelectionSeconds,
                             String serverLanguage, boolean farmLimitMessage, boolean locateTeleportLink,
                             boolean discoverPetsOnLoad, TrackingRules tracking) {

    public enum ViewPolicy { OP_ONLY_ALL, EVERYONE_ALL, OWN_ONLY }

    /** {@code false} leaves an action to operators and players granted its permission node. */
    public record OwnerActions(boolean locate, boolean rename, boolean editParents, boolean setAge) {
        public static final OwnerActions DEFAULT = new OwnerActions(true, true, true, true);
    }

    /** Milliseconds. Zero turns a cooldown off. */
    public record Cooldowns(int open, int scan, int serverScan, int locate, int edit) {
        public static final Cooldowns DEFAULT = new Cooldowns(500, 2_000, 2_000, 250, 250);
    }

    public static final String AUTO_LANGUAGE = "auto";
    public static final int MAX_COOLDOWN = 60_000;
    public static final int MAX_NAME_LENGTH = 50;
    static final Pattern ALIAS = Pattern.compile("[a-z0-9_-]{1,32}");
    static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}_[a-z]{2,4}");

    public static final ServerSettings DEFAULT = new ServerSettings(ViewPolicy.OP_ONLY_ALL, 2, OwnerActions.DEFAULT,
            true, List.of(), 100, Cooldowns.DEFAULT, true, "familytree", 120, AUTO_LANGUAGE, true, true, true,
            TrackingRules.DEFAULT);

    public ServerSettings {
        commandAliases = List.copyOf(commandAliases);
    }

    /** The settings read from a file, what was wrong with it, and the keys Family Tree doesn't know. */
    public record Parsed(ServerSettings settings, List<SettingProblem> problems, Set<String> rejected, Set<String> unknown) {}

    public static Parsed parse(Properties properties) {
        SettingsReader reader = new SettingsReader(properties);
        ServerSettings d = DEFAULT;
        ViewPolicy view = reader.choice("viewPolicy", ViewPolicy.class, d.viewPolicy);
        int level = reader.number("operatorLevel", d.operatorLevel, 1, 4);
        OwnerActions owners = new OwnerActions(
                reader.flag("ownersCanLocate", true), reader.flag("ownersCanRename", true),
                reader.flag("ownersCanEditParents", true), reader.flag("ownersCanSetAge", true));
        boolean menu = reader.flag("vanillaMenu", d.vanillaMenu);
        List<String> aliases = reader.aliases("commandAliases", ALIAS);
        int listLimit = reader.number("listLimit", d.listLimit, 1, 1_000);
        Cooldowns c = Cooldowns.DEFAULT;
        Cooldowns cooldowns = new Cooldowns(
                reader.number("openCooldownMillis", c.open(), 0, MAX_COOLDOWN),
                reader.number("scanCooldownMillis", c.scan(), 0, MAX_COOLDOWN),
                reader.number("serverScanCooldownMillis", c.serverScan(), 0, MAX_COOLDOWN),
                reader.number("locateCooldownMillis", c.locate(), 0, MAX_COOLDOWN),
                reader.number("editCooldownMillis", c.edit(), 0, MAX_COOLDOWN));
        boolean stick = reader.flag("linkingStick", d.linkingStick);
        String stickName = reader.text("linkingStickName", d.linkingStickName, ServerSettings::validStickName,
                "familytree.config.bad_name");
        int selection = reader.number("linkingSelectionSeconds", d.linkingSelectionSeconds, 10, 3_600);
        String language = reader.text("serverLanguage", d.serverLanguage,
                value -> value.equalsIgnoreCase(AUTO_LANGUAGE) || LANGUAGE.matcher(value.toLowerCase(java.util.Locale.ROOT)).matches(),
                "familytree.config.bad_language").toLowerCase(java.util.Locale.ROOT);
        boolean farmMessage = reader.flag("farmLimitMessage", d.farmLimitMessage);
        boolean teleport = reader.flag("locateTeleportLink", d.locateTeleportLink);
        boolean discover = reader.flag("discoverPetsOnLoad", d.discoverPetsOnLoad);
        TrackingRules tracking = TrackingRules.read(reader);

        Set<String> unknown = new TreeSet<>(properties.stringPropertyNames());
        for (Setting setting : SETTINGS) unknown.remove(setting.key());
        List<SettingProblem> problems = new ArrayList<>(reader.problems());
        for (String key : unknown) problems.add(new SettingProblem(key, "familytree.config.unknown_key", key));
        return new Parsed(new ServerSettings(view, level, owners, menu, aliases, listLimit, cooldowns, stick,
                stickName, selection, language, farmMessage, teleport, discover, tracking),
                List.copyOf(problems), reader.rejected(), Set.copyOf(unknown));
    }

    static boolean validStickName(String name) {
        return !name.isEmpty() && name.length() <= MAX_NAME_LENGTH
                && name.codePoints().noneMatch(c -> Character.isISOControl(c) || c == 0x00A7);
    }

    /** One line of the settings file and the comment above it. */
    public record Setting(String section, String key, List<String> comment, String allowed,
                          Function<ServerSettings, String> value) {
        public String defaultValue() {
            return value.apply(DEFAULT);
        }
    }

    private static final String VIEW = "Viewing and permissions";
    private static final String MENU = "Commands and the chest menu";
    private static final String COOLDOWNS = "Cooldowns, in milliseconds. 0 turns a cooldown off.";
    private static final String STICK = "Linking stick";
    private static final String CHAT = "Chat";
    private static final String ANIMALS = "Animals";
    private static final String BOOLEAN = "true, false";

    public static final List<Setting> SETTINGS = List.of(
            new Setting(VIEW, "viewPolicy", List.of(
                    "Who may browse every player's pets. Browsing never grants editing or coordinates.",
                    "OP_ONLY_ALL lets operators do this, EVERYONE_ALL every player, and OWN_ONLY nobody.",
                    "This is the default for the familytree.others.view permission node."),
                    "OP_ONLY_ALL, EVERYONE_ALL, OWN_ONLY", s -> s.viewPolicy.name()),
            new Setting(VIEW, "operatorLevel", List.of(
                    "The permission level Family Tree treats as an operator for its operator-only defaults.",
                    "2 matches /gamemode and /tp. 4 limits these defaults to server owners."),
                    "1 to 4", s -> Integer.toString(s.operatorLevel)),
            new Setting(VIEW, "ownersCanLocate", List.of(
                    "Whether players can locate their own pets and add map waypoints for them.",
                    "false leaves this to operators. Default for the familytree.locate node."),
                    BOOLEAN, s -> Boolean.toString(s.ownerActions.locate())),
            new Setting(VIEW, "ownersCanRename", List.of(
                    "Whether players can give their own pets a Family Tree name.",
                    "false leaves this to operators. Default for the familytree.rename node."),
                    BOOLEAN, s -> Boolean.toString(s.ownerActions.rename())),
            new Setting(VIEW, "ownersCanEditParents", List.of(
                    "Whether players can add, change, or clear their own pets' parents, by command, screen, menu, or linking stick.",
                    "false leaves this to operators. Default for the familytree.parents node."),
                    BOOLEAN, s -> Boolean.toString(s.ownerActions.editParents())),
            new Setting(VIEW, "ownersCanSetAge", List.of(
                    "Whether players can correct their own pets' ages with /familytree setage and setbirth.",
                    "false leaves this to operators. Default for the familytree.age node."),
                    BOOLEAN, s -> Boolean.toString(s.ownerActions.setAge())),
            new Setting(MENU, "vanillaMenu", List.of(
                    "Whether /familytree opens chest menus for players without the client mod.",
                    "false turns the menu off for everyone, and /familytree prints the command list."),
                    BOOLEAN, s -> Boolean.toString(s.vanillaMenu)),
            new Setting(MENU, "commandAliases", List.of(
                    "Comma-separated extra names for /familytree, such as pets,ft. Names use lowercase letters, digits, - and _.",
                    "Family Tree skips a name another command already uses. Changes take effect after /reload or a restart."),
                    "a comma-separated list, or empty", s -> String.join(",", s.commandAliases)),
            new Setting(MENU, "listLimit", List.of(
                    "How many animals /familytree list prints before it points players to the screens."),
                    "1 to 1000", s -> Integer.toString(s.listLimit)),
            new Setting(COOLDOWNS, "openCooldownMillis", List.of(
                    "Per player: opening the Family Tree screens or chest menu, and switching between own and all pets."),
                    "0 to " + MAX_COOLDOWN, s -> Integer.toString(s.cooldowns.open())),
            new Setting(COOLDOWNS, "scanCooldownMillis", List.of(
                    "Per player: /familytree scan and Find existing pets."),
                    "0 to " + MAX_COOLDOWN, s -> Integer.toString(s.cooldowns.scan())),
            new Setting(COOLDOWNS, "serverScanCooldownMillis", List.of(
                    "Shared by all players: the time between any two scans of the loaded animals."),
                    "0 to " + MAX_COOLDOWN, s -> Integer.toString(s.cooldowns.serverScan())),
            new Setting(COOLDOWNS, "locateCooldownMillis", List.of(
                    "Per player: Locate pet, map waypoints, and the menu's Locate button."),
                    "0 to " + MAX_COOLDOWN, s -> Integer.toString(s.cooldowns.locate())),
            new Setting(COOLDOWNS, "editCooldownMillis", List.of(
                    "Per player: renames and parent edits from the screens and the chest menu."),
                    "0 to " + MAX_COOLDOWN, s -> Integer.toString(s.cooldowns.edit())),
            new Setting(STICK, "linkingStick", List.of(
                    "Whether a stick renamed to linkingStickName links parents and adds animals when players click them.",
                    "false turns the stick off for everyone."),
                    BOOLEAN, s -> Boolean.toString(s.linkingStick)),
            new Setting(STICK, "linkingStickName", List.of(
                    "The anvil name that makes a stick the linking stick. Case and surrounding spaces don't matter."),
                    "1 to " + MAX_NAME_LENGTH + " characters", s -> s.linkingStickName),
            new Setting(STICK, "linkingSelectionSeconds", List.of(
                    "How long the linking stick keeps a selection before it starts over."),
                    "10 to 3600", s -> Integer.toString(s.linkingSelectionSeconds)),
            new Setting(CHAT, "serverLanguage", List.of(
                    "The language of Family Tree's chat messages and chest menus for players without the client mod.",
                    "auto uses each player's Minecraft language. A code such as en_us or de_de uses that language for everyone.",
                    "Players with the client mod see its screens in their own language either way."),
                    "auto, or a language code", s -> s.serverLanguage),
            new Setting(CHAT, "farmLimitMessage", List.of(
                    "Whether a player hears once when farmRecordLimit stops recording the animals they breed.",
                    "The server log notes it either way."),
                    BOOLEAN, s -> Boolean.toString(s.farmLimitMessage)),
            new Setting(CHAT, "locateTeleportLink", List.of(
                    "Whether operators can click the coordinates from Locate to fill in a teleport command."),
                    BOOLEAN, s -> Boolean.toString(s.locateTeleportLink)),
            new Setting(ANIMALS, "discoverPetsOnLoad", List.of(
                    "Whether tamed pets missing from the history join it when their chunk loads.",
                    "false leaves that to taming, breeding, /familytree scan, and Find existing pets.",
                    "Animals already in the history still update when they load."),
                    BOOLEAN, s -> Boolean.toString(s.discoverPetsOnLoad)),
            new Setting(ANIMALS, TrackingRules.FARM, List.of(
                    "Animals other than pets join when a player breeds them, names them with a name tag, or adds them",
                    "with the linking stick. Wild animals never join on their own.",
                    "Farm animals: cows, mooshrooms, sheep, pigs, chickens, rabbits, and goats."),
                    BOOLEAN, s -> Boolean.toString(s.tracking.farm())),
            new Setting(ANIMALS, TrackingRules.WILDLIFE, List.of(
                    "Foxes, axolotls, frogs, bees, turtles, pandas, ocelots, hoglins, striders, sniffers, armadillos,",
                    "and other vanilla animals."),
                    BOOLEAN, s -> Boolean.toString(s.tracking.wildlife())),
            new Setting(ANIMALS, TrackingRules.MODDED, List.of(
                    "Breedable animals added by other mods."),
                    BOOLEAN, s -> Boolean.toString(s.tracking.modded())),
            new Setting(ANIMALS, TrackingRules.TRACKED, List.of(
                    "Comma-separated species to record even when their category is off, such as minecraft:fox.",
                    "This file wins over the familytree:tracked and familytree:ignored entity type tags."),
                    "species ids, or empty", s -> String.join(",", new TreeSet<>(s.tracking.trackedSpecies()))),
            new Setting(ANIMALS, TrackingRules.IGNORED, List.of(
                    "Comma-separated species to never record. Ignored species also stop new pets.",
                    "A species listed here and in trackedSpecies is ignored."),
                    "species ids, or empty", s -> String.join(",", new TreeSet<>(s.tracking.ignoredSpecies()))),
            new Setting(ANIMALS, TrackingRules.LIMIT, List.of(
                    "How many unnamed animals other than pets the history keeps. At the limit, breeding stops adding them.",
                    "Pets and named animals are always recorded."),
                    "0 to " + TrackingRules.MAX_FARM_LIMIT, s -> Integer.toString(s.tracking.farmRecordLimit())),
            new Setting(ANIMALS, TrackingRules.AUTO_PRUNE, List.of(
                    "true backs up the history to familytree-backups/, then deletes the oldest deceased unnamed animals",
                    "other than pets whenever the limit is reached. Pets, named animals, and living animals are never deleted."),
                    BOOLEAN, s -> Boolean.toString(s.tracking.autoPruneFarm())));

    private static final List<String> HEADER = List.of(
            "Family Tree server settings",
            "",
            "Family Tree reads this file when the server starts and on /familytree reload, which needs the "
                    + "familytree.reload permission. It reports values it can't use in the log and to the admin who "
                    + "reloaded, uses the default for them, and keeps your text here so you can fix it. It rewrites this "
                    + "file to add missing settings and these comments, so comments you add are not kept.",
            "",
            "A permission mod such as LuckPerms can grant or deny each familytree.* permission node per player or "
                    + "group. The settings below decide what players get when no permission mod sets a node. Datapacks "
                    + "can add species to the familytree:tracked and familytree:ignored entity type tags.");

    /**
     * The file text for these settings. Values the admin wrote that Family Tree couldn't use keep their text,
     * and unknown keys stay at the end, so a typo is never silently lost.
     */
    public String fileText(Map<String, String> written, Set<String> rejected, Set<String> unknown) {
        StringBuilder out = new StringBuilder();
        for (String line : HEADER) comment(out, line);
        String section = null;
        for (Setting setting : SETTINGS) {
            if (!setting.section().equals(section)) {
                section = setting.section();
                out.append("\n# ").append("=".repeat(4)).append(' ').append(section).append(' ').append("=".repeat(4)).append('\n');
            }
            out.append('\n');
            for (String line : setting.comment()) comment(out, line);
            comment(out, "Allowed: " + setting.allowed() + ". Default: " + display(setting.defaultValue()));
            String value = rejected.contains(setting.key()) && written.containsKey(setting.key())
                    ? written.get(setting.key()) : setting.value().apply(this);
            out.append(escapeKey(setting.key())).append('=').append(escapeValue(value)).append('\n');
        }
        if (!unknown.isEmpty()) {
            out.append("\n# ==== Not recognized ====\n");
            out.append("# Family Tree doesn't use these settings. Check their spelling against the settings above.\n");
            for (String key : unknown) out.append(escapeKey(key)).append('=').append(escapeValue(written.getOrDefault(key, ""))).append('\n');
        }
        return out.toString();
    }

    private static final int COMMENT_WIDTH = 96;

    /** Writes a comment line, wrapped at word boundaries so the file reads well in narrow editors. */
    private static void comment(StringBuilder out, String text) {
        if (text.isEmpty()) {
            out.append("#\n");
            return;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > COMMENT_WIDTH) {
                out.append("# ").append(line).append('\n');
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        out.append("# ").append(line).append('\n');
    }

    private static String display(String value) {
        return value.isEmpty() ? "empty" : value;
    }

    private static String escapeKey(String key) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == ' ' || c == '=' || c == ':' || c == '#' || c == '!') out.append('\\');
            appendEscaped(out, c);
        }
        return out.toString();
    }

    /** Properties escaping for a value. Characters outside ASCII stay as written, since the file is UTF-8. */
    private static String escapeValue(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ' ' && i == 0) out.append('\\');
            appendEscaped(out, c);
        }
        return out.toString();
    }

    private static void appendEscaped(StringBuilder out, char c) {
        switch (c) {
            case '\\' -> out.append("\\\\");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            case '\t' -> out.append("\\t");
            case '\f' -> out.append("\\f");
            default -> {
                if (c < 0x20 || c == 0x7F) out.append(String.format("\\u%04x", (int) c));
                else out.append(c);
            }
        }
    }
}
