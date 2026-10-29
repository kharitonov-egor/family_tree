package com.egakh.familytree.client.platform;

import java.util.function.Predicate;

/** Versions and installed mods, filled in by each loader's client entry point. */
public final class ClientEnvironment {
    public record Versions(String mod, String minecraft, String loader, String loaderVersion) {}

    private static Versions versions = new Versions("unknown", "unknown", "unknown", "unknown");
    private static Predicate<String> installed = id -> false;

    private ClientEnvironment() {}

    public static void configure(Versions loaded, Predicate<String> modLoaded) {
        versions = loaded;
        installed = modLoaded;
    }

    public static Versions versions() { return versions; }

    public static boolean isModLoaded(String id) { return installed.test(id); }
}
