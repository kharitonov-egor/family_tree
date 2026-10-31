package com.egakh.familytree.settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A value in the server settings file that Family Tree could not use as written. {@code message} is a
 * translation key and {@code args} its arguments, so the log and the admin each read it in their language.
 */
public record SettingProblem(String key, String message, List<Object> args) {
    public SettingProblem(String key, String message, Object... args) {
        this(key, message, Collections.unmodifiableList(Arrays.asList(args.clone())));
    }

    public Object[] arguments() {
        return args.toArray();
    }
}
