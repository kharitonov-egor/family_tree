package com.egakh.familytree.plugin.menu;

import com.egakh.familytree.plugin.FamilyTreePlugin;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens the chest, anvil, and book menus for players without the client mod, and refuses every click in them.
 * Clicks are cancelled at the lowest priority and again at the highest, so no item ever moves.
 */
public final class MenuManager implements Listener {
    private final FamilyTreePlugin plugin;
    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();
    final TextPrompt prompts;

    public MenuManager(FamilyTreePlugin plugin) {
        this.plugin = plugin;
        this.prompts = new TextPrompt(plugin);
    }

    FamilyTreePlugin plugin() {
        return plugin;
    }

    public boolean open(Player player) {
        if (plugin.store().readOnly()) {
            plugin.chat().send(player, "familytree.command.read_only");
            return false;
        }
        if (!plugin.cooldowns().open(player.getUniqueId())) {
            plugin.chat().send(player, "familytree.menu.wait");
            return false;
        }
        MenuSession session = new MenuSession(this, player);
        sessions.put(player.getUniqueId(), session);
        session.reload();
        session.showBrowser();
        return true;
    }

    /** Opens a chest view in place of the current one. Bukkit closes the old view on the server only, so the cursor stays put. */
    void show(MenuSession session, ChestView view) {
        prompts.cancel(session.player());
        session.player().openInventory(view.getInventory());
        session.current = view;
    }

    /** Runs a button's action once the click is fully handled, and only if the same view is still open. */
    void afterClick(MenuSession session, ChestView view, Runnable action) {
        plugin.later(() -> {
            if (session.current == view && session.player().isOnline()) action.run();
        });
    }

    private static ChestView chestOf(Inventory top) {
        InventoryHolder holder = top == null ? null : top.getHolder();
        return holder instanceof ChestView view ? view : null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void refuseClick(InventoryClickEvent event) {
        if (chestOf(event.getInventory()) != null || prompts.owns(event.getWhoClicked(), event.getInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (prompts.owns(who, event.getInventory())) {
            event.setCancelled(true);
            resync(who);
            prompts.clicked(who, event.getRawSlot(), event.getClick());
            return;
        }
        ChestView view = chestOf(event.getInventory());
        if (view == null) return;
        event.setCancelled(true);
        resync(who);
        MenuSession session = sessions.get(who.getUniqueId());
        if (session == null || session.current != view || view.acting) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= ChestView.SIZE) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
        Runnable action = view.action(slot);
        if (action == null || !session.allowAction()) return;
        view.acting = true;
        afterClick(session, view, action);
    }

    /** The client already moved items in its own view; resend the real contents once the click is over. */
    private void resync(HumanEntity who) {
        if (who instanceof Player player) plugin.later(player::updateInventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void refuseDrag(InventoryDragEvent event) {
        if (chestOf(event.getInventory()) != null || prompts.owns(event.getWhoClicked(), event.getInventory())) {
            event.setCancelled(true);
            resync(event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void refuseDragAgain(InventoryDragEvent event) {
        if (chestOf(event.getInventory()) != null || prompts.owns(event.getWhoClicked(), event.getInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        prompts.closed(event.getPlayer(), event.getInventory());
        ChestView view = chestOf(event.getInventory());
        MenuSession session = sessions.get(event.getPlayer().getUniqueId());
        if (view != null && session != null && session.current == view) session.current = null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnvilText(PrepareAnvilEvent event) {
        prompts.prepared(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (prompts.chatted(event.getPlayer(), event.getMessage())) event.setCancelled(true);
    }

    public void forget(Player player) {
        sessions.remove(player.getUniqueId());
        prompts.cancel(player);
    }

    public void closeAll() {
        for (MenuSession session : new ArrayList<>(sessions.values())) {
            if (session.current != null) session.player().closeInventory();
            prompts.cancel(session.player());
        }
        sessions.clear();
    }
}
