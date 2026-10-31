package com.egakh.familytree.plugin;

import com.egakh.familytree.permissions.FamilyTreePermission;
import com.egakh.familytree.plugin.command.FamilyTreeCommand;
import com.egakh.familytree.plugin.menu.MenuManager;
import com.egakh.familytree.plugin.network.ClientBridge;
import com.egakh.familytree.plugin.storage.FamilyTreeStore;
import com.egakh.familytree.plugin.storage.SaveQueue;
import com.egakh.familytree.plugin.storage.StoreFiles;
import com.egakh.familytree.plugin.text.Messages;
import com.egakh.familytree.plugin.text.TextRenderer;
import com.egakh.familytree.plugin.text.VanillaLang;
import com.egakh.familytree.plugin.track.LinkingStick;
import com.egakh.familytree.plugin.track.Tracker;
import com.egakh.familytree.plugin.track.TrackingListener;
import com.egakh.familytree.settings.ServerSettings;
import com.egakh.familytree.settings.SettingProblem;
import com.egakh.familytree.text.LangTables;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Family Tree on Spigot, Paper, and Paper forks. See docs/plugin.md. */
public final class FamilyTreePlugin extends JavaPlugin implements Listener {
    private static final long AUTOSAVE_TICKS = 6000L;

    private volatile PluginSettings.Loaded settings = PluginSettings.defaults();
    private TextRenderer renderer;
    private Access access;
    private Chat chat;
    private Cooldowns cooldowns;
    private StoreFiles files;
    private FamilyTreeStore store = FamilyTreeStore.locked();
    private Tracker tracker;
    private LinkingStick linkingStick;
    private MenuManager menus;
    private ClientBridge bridge;
    private FamilyTreeCommand command;
    private SaveQueue saves;

    @Override
    public void onEnable() {
        VanillaLang.useServerClassLoader(getServer().getClass().getClassLoader());
        reloadSettings();
        renderer = TextRenderer.detect(getLogger());
        access = new Access(this::settings);
        chat = new Chat(renderer, this::settings,
                sender -> bridge != null && sender instanceof Player player && bridge.hasClientMod(player)
                        && com.egakh.familytree.plugin.network.Payloads.hidesIsolates(player.getListeningPluginChannels()));
        cooldowns = new Cooldowns(this::settings);

        World main = Bukkit.getWorlds().get(0);
        Path levelRoot = levelRoot(main.getWorldFolder().toPath());
        files = StoreFiles.forLevel(levelRoot, Files.isDirectory(levelRoot.resolve("dimensions/minecraft/overworld")));
        store = files.load(getLogger());
        saves = new SaveQueue(files, getLogger());
        getLogger().info("Family Tree loaded " + store.all().size() + " records from " + files.file()
                + (store.readOnly() ? " (read-only)" : "") + ". Text: " + renderer.description() + ".");

        tracker = new Tracker(this);
        linkingStick = new LinkingStick(this, tracker);
        menus = new MenuManager(this);
        bridge = new ClientBridge(this);
        command = new FamilyTreeCommand(this);
        command.register();
        getServer().getPluginManager().registerEvents(new TrackingListener(this, tracker), this);
        Listener paperEvents = tracker.paperEvents();
        if (paperEvents != null) getServer().getPluginManager().registerEvents(paperEvents, this);
        getServer().getPluginManager().registerEvents(linkingStick, this);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(this, this);
        bridge.register();

        later(() -> {
            if (settings().discoverPetsOnLoad()) tracker.scanLoadedPets(record -> true);
        });
        getServer().getScheduler().runTaskTimer(this, this::save, AUTOSAVE_TICKS, AUTOSAVE_TICKS);
    }

    @Override
    public void onDisable() {
        if (menus != null) menus.closeAll();
        if (bridge != null) bridge.unregister();
        if (linkingStick != null) linkingStick.clear();
        if (saves == null) return;
        save();
        try {
            if (!saves.finish(30, TimeUnit.SECONDS)) getLogger().severe("Family Tree could not finish saving within 30 seconds");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Paper 26.x reports the overworld's dimension folder as the main world's folder. The level root holds level.dat. */
    private static Path levelRoot(Path worldFolder) {
        Path folder = worldFolder.toAbsolutePath().normalize();
        if (folder.endsWith(Path.of("dimensions", "minecraft", "overworld"))) return folder.getParent().getParent().getParent();
        return folder;
    }

    public List<SettingProblem> reloadSettings() {
        PluginSettings.Loaded loaded = PluginSettings.load(getDataFolder().toPath().resolve(PluginSettings.FILE_NAME), settings);
        if (loaded.readFailed()) {
            getLogger().warning("Failed to read " + PluginSettings.FILE_NAME + ". Family Tree keeps its current settings.");
            return null;
        }
        settings = loaded;
        for (SettingProblem problem : loaded.problems())
            getLogger().warning(PluginSettings.FILE_NAME + ": " + Messages.plain(LangTables.DEFAULT, problem.message(), problem.arguments()));
        return loaded.problems();
    }

    /** Encodes on the main thread, then writes on the save thread after any older write still queued. */
    public void save() {
        if (saves == null || !store.dirty()) return;
        byte[] bytes;
        try {
            bytes = StoreFiles.encode(store, dataVersion());
        } catch (IOException | RuntimeException failure) {
            getLogger().log(Level.SEVERE, "Could not encode Family Tree history", failure);
            return;
        }
        store.markSaved();
        FamilyTreeStore saved = store;
        saves.submit(bytes, () -> {
            if (isEnabled()) getServer().getScheduler().runTask(this, saved::markDirty);
        });
    }

    @SuppressWarnings("deprecation")
    private static int dataVersion() {
        return Bukkit.getUnsafe().getDataVersion();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        if (event.getWorld().equals(Bukkit.getWorlds().get(0))) save();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Path unreadable = store.unreadableFile();
        if (unreadable == null || files == null || !access.allowed(event.getPlayer(), FamilyTreePermission.RELOAD)) return;
        chat.fail(event.getPlayer(), "familytree.save.unreadable", files.shown(unreadable));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.forget(event.getPlayer().getUniqueId());
        linkingStick.forget(event.getPlayer().getUniqueId());
        menus.forget(event.getPlayer());
        bridge.forget(event.getPlayer());
    }

    public void later(Runnable task) {
        getServer().getScheduler().runTask(this, task);
    }

    public ServerSettings settings() { return settings.server(); }
    public boolean clientModSupport() { return settings.clientMod(); }
    public Access access() { return access; }
    public Chat chat() { return chat; }
    public Cooldowns cooldowns() { return cooldowns; }
    public FamilyTreeStore store() { return store; }
    public StoreFiles files() { return files; }
    public Tracker tracker() { return tracker; }
    public LinkingStick linkingStick() { return linkingStick; }
    public MenuManager menus() { return menus; }
    public ClientBridge bridge() { return bridge; }
    public FamilyTreeCommand command() { return command; }
}
