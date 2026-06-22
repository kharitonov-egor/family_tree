package com.egakh.familytree.settings;

import com.egakh.familytree.FamilyTreeMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

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
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("familytree-server.properties");

    private static volatile ViewPolicy viewPolicy = ViewPolicy.OP_ONLY_ALL;

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
        save();
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty(VIEW_POLICY_KEY, viewPolicy.name());
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(out, "Family Tree server settings. viewPolicy = OP_ONLY_ALL | EVERYONE_ALL | OWN_ONLY");
            }
        } catch (IOException e) {
            FamilyTreeMod.LOGGER.warn("Failed to write server settings", e);
        }
    }

    public static ViewPolicy viewPolicy() {
        return viewPolicy;
    }

    public static boolean mayViewAll(ServerPlayer player) {
        return switch (viewPolicy) {
            case EVERYONE_ALL -> true;
            case OP_ONLY_ALL -> player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
            case OWN_ONLY -> false;
        };
    }
}
