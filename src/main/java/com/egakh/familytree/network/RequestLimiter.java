package com.egakh.familytree.network;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Shared cooldowns for commands and network requests. Call on the server thread. */
public final class RequestLimiter<K> {
    private final Map<K, Long> last = new HashMap<>();
    private final long interval;
    private final LongSupplier clock;

    public RequestLimiter(long interval, LongSupplier clock) {
        this.interval = interval;
        this.clock = clock;
    }

    public boolean allow(K key) {
        long now = clock.getAsLong();
        Long previous = last.get(key);
        if (previous != null && now - previous < interval) return false;
        last.put(key, now);
        return true;
    }

    public void remove(K key) { last.remove(key); }
    public void clear() { last.clear(); }
}
