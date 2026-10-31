package com.egakh.familytree.plugin.track;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calls API methods by name. Several Bukkit types changed from enums or classes to interfaces between 1.20.1 and
 * 26.x, and code compiled against one shape fails against the other, so the plugin reaches them reflectively.
 */
public final class Compat {
    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private Compat() {}

    public static Object call(Object target, String name, Object... args) {
        if (target == null) return null;
        Method method = find(target.getClass(), name, args.length);
        if (method == null) return null;
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    public static boolean has(Object target, String name, int arity) {
        return target != null && find(target.getClass(), name, arity) != null;
    }

    private static Method find(Class<?> type, String name, int arity) {
        return METHODS.computeIfAbsent(type.getName() + "#" + name + "/" + arity, key -> {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == arity) {
                    try {
                        method.setAccessible(true);
                    } catch (RuntimeException inaccessible) {
                        continue;
                    }
                    return Optional.of(publicVersion(method));
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    /** Implementation classes may be package-private, so prefer the interface's declaration of the method. */
    private static Method publicVersion(Method method) {
        for (Class<?> type : method.getDeclaringClass().getInterfaces()) {
            try {
                return type.getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException ignored) {
                // Keep looking.
            }
        }
        return method;
    }
}
