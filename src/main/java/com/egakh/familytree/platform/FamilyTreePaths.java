package com.egakh.familytree.platform;

import java.nio.file.Path;

public final class FamilyTreePaths {
    private static Path config = Path.of("config");
    private FamilyTreePaths() {}
    public static Path config() { return config; }
    public static void configure(Path directory) { config = directory; }
}
