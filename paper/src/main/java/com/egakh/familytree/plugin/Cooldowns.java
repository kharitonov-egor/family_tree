package com.egakh.familytree.plugin;

import com.egakh.familytree.network.RequestLimiter;
import com.egakh.familytree.settings.ServerSettings;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** The mod's per-player and server-wide cooldowns, read from the settings on every request. */
public final class Cooldowns {
    private static final UUID CONSOLE = new UUID(0, 0);
    private final Supplier<ServerSettings> settings;
    private final RequestLimiter<UUID> open = limiter(ServerSettings.Cooldowns::open);
    private final RequestLimiter<UUID> discovery = limiter(ServerSettings.Cooldowns::scan);
    private final RequestLimiter<UUID> locate = limiter(ServerSettings.Cooldowns::locate);
    private final RequestLimiter<UUID> edit = limiter(ServerSettings.Cooldowns::edit);
    private final RequestLimiter<UUID> relatives = limiter(ServerSettings.Cooldowns::relatives);
    private final RequestLimiter<String> serverScan = limiter(ServerSettings.Cooldowns::serverScan);

    public Cooldowns(Supplier<ServerSettings> settings) {
        this.settings = settings;
    }

    private <K> RequestLimiter<K> limiter(ToIntFunction<ServerSettings.Cooldowns> millis) {
        return new RequestLimiter<>(() -> millis.applyAsInt(settings.get().cooldowns()) * 1_000_000L, System::nanoTime);
    }

    public boolean open(UUID player) { return open.allow(player); }
    public boolean locate(UUID player) { return locate.allow(player); }
    public boolean edit(UUID player) { return edit.allow(player); }
    public boolean relatives(UUID player) { return relatives.allow(player); }

    /** Commands and discovery share the player's cooldown and the server's. */
    public boolean scan(UUID player) {
        return discovery.allow(player == null ? CONSOLE : player) && serverScan.allow("server");
    }

    public void forget(UUID player) {
        open.remove(player);
        discovery.remove(player);
        locate.remove(player);
        edit.remove(player);
        relatives.remove(player);
    }
}
