package com.egakh.familytree.settings;

import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.platform.FamilyTreePaths;
import net.minecraft.server.level.ServerPlayer;
import com.egakh.familytree.permissions.ServerPetAccess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public final class FamilyTreeServerSettings {

    public enum ViewPolicy {
        OP_ONLY_ALL,
        EVERYONE_ALL,
        OWN_ONLY;

        static ViewPolicy parse(String raw) {
            if (raw == null) return OP_ONLY_ALL;
            try {
                return ViewPolicy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return OP_ONLY_ALL;
            }
        }
    }

    private static final String VIEW_POLICY_KEY = "viewPolicy";
    private static final String VANILLA_MENU_KEY = "vanillaMenu";
    private static final String COMMAND_ALIASES_KEY = "commandAliases";
    private static final java.util.regex.Pattern ALIAS = java.util.regex.Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Path CONFIG_PATH = FamilyTreePaths.config()
            .resolve("familytree-server.properties");

    private static final String HEADER = String.join("\n",
            "Family Tree server settings.",
            "viewPolicy = OP_ONLY_ALL | EVERYONE_ALL | OWN_ONLY",
            "vanillaMenu = true | false: open chest menus for players without the client mod.",
            "commandAliases = comma-separated extra names for /familytree, such as pets,ft",
            "Animals other than pets enter the history when a player breeds them, names them with a name tag,",
            "or adds them with the linking stick. Wild animals never enter on their own.",
            "trackFarmAnimals: cows, mooshrooms, sheep, pigs, chickens, rabbits, and goats.",
            "trackWildlife: foxes, axolotls, frogs, bees, turtles, pandas, ocelots, hoglins, striders, sniffers,",
            "  armadillos, and other vanilla animals.",
            "trackModdedAnimals: breedable animals added by other mods.",
            "trackedSpecies / ignoredSpecies: comma-separated ids such as minecraft:fox. Ignored species also stop new pets.",
            "farmRecordLimit: unnamed animals other than pets the history keeps (0 to " + TrackingRules.MAX_FARM_LIMIT + ").",
            "  At the limit, breeding stops adding them. Pets and named animals are always recorded.",
            "autoPruneFarmRecords: true to back up the history, then delete the oldest deceased unnamed",
            "  animals other than pets whenever the limit is reached. Pets and named animals are never deleted.");

    private static volatile ViewPolicy viewPolicy = ViewPolicy.OP_ONLY_ALL;
    private static volatile boolean vanillaMenu = true;
    private static volatile java.util.List<String> commandAliases = java.util.List.of();
    private static volatile boolean loaded;
    private static volatile TrackingRules tracking = TrackingRules.DEFAULT;

    private FamilyTreeServerSettings() {}

    public static void load() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                properties.load(in);
            } catch (IOException e) {
                FamilyTreeMod.LOGGER.warn("Failed to read server settings, using defaults", e);
            }
        }
        viewPolicy = ViewPolicy.parse(properties.getProperty(VIEW_POLICY_KEY));
        vanillaMenu = !"false".equalsIgnoreCase(properties.getProperty(VANILLA_MENU_KEY, "true").trim());
        commandAliases = parseAliases(properties.getProperty(COMMAND_ALIASES_KEY, ""));
        loaded = true;
        tracking = TrackingRules.parse(properties);
        save();
    }

    static java.util.List<String> parseAliases(String raw) {
        java.util.List<String> aliases = new java.util.ArrayList<>();
        for (String part : raw.split(",")) {
            String alias = part.trim().toLowerCase(Locale.ROOT);
            if (alias.startsWith("/")) alias = alias.substring(1);
            if (alias.isEmpty() || alias.equals("familytree")) continue;
            if (!ALIAS.matcher(alias).matches()) {
                FamilyTreeMod.LOGGER.warn("Ignoring Family Tree command alias '{}'. Use lowercase letters, digits, - or _.", alias);
                continue;
            }
            if (!aliases.contains(alias)) aliases.add(alias);
        }
        return java.util.List.copyOf(aliases);
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty(VIEW_POLICY_KEY, viewPolicy.name());
        properties.setProperty(VANILLA_MENU_KEY, Boolean.toString(vanillaMenu));
        properties.setProperty(COMMAND_ALIASES_KEY, String.join(",", commandAliases));
        tracking.store(properties);
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(out, HEADER);
            }
        } catch (IOException e) {
            FamilyTreeMod.LOGGER.warn("Failed to write server settings", e);
        }
    }

    public static ViewPolicy viewPolicy() {
        return viewPolicy;
    }

    public static boolean vanillaMenu() {
        return vanillaMenu;
    }

    /** Commands register before the server starts, so this reads the file on first use. */
    public static java.util.List<String> commandAliases() {
        if (!loaded) load();
        return commandAliases;
    }

    public static TrackingRules tracking() {
        return tracking;
    }

    public static boolean mayViewAll(ServerPlayer player) {
        return switch (viewPolicy) {
            case EVERYONE_ALL -> true;
            case OP_ONLY_ALL -> ServerPetAccess.operator(player.createCommandSourceStack());
            case OWN_ONLY -> false;
        };
    }
}
