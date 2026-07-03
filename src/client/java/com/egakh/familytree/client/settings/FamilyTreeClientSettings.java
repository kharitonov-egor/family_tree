package com.egakh.familytree.client.settings;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class FamilyTreeClientSettings {

    private static final String SHOW_AGE_KEY = "showAge";
    private static final String SHOW_BIRTH_DAY_KEY = "showBirthDay";
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("familytree-client.properties");

    private static boolean showAge = false;
    private static boolean showBirthDay = false;

    private FamilyTreeClientSettings() {}

    public static void load() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                properties.load(in);
            } catch (IOException ignored) {
            }
        }
        showAge = Boolean.parseBoolean(properties.getProperty(SHOW_AGE_KEY, "false"));
        showBirthDay = Boolean.parseBoolean(properties.getProperty(SHOW_BIRTH_DAY_KEY, "false"));
    }

    public static void save() {
        Properties properties = new Properties();
        properties.setProperty(SHOW_AGE_KEY, Boolean.toString(showAge));
        properties.setProperty(SHOW_BIRTH_DAY_KEY, Boolean.toString(showBirthDay));
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(out, "Family Tree client settings");
            }
        } catch (IOException ignored) {
        }
    }

    public static boolean showAge() {
        return showAge;
    }

    public static void setShowAge(boolean value) {
        showAge = value;
        save();
    }

    public static boolean showBirthDay() {
        return showBirthDay;
    }

    public static void setShowBirthDay(boolean value) {
        showBirthDay = value;
        save();
    }
}
