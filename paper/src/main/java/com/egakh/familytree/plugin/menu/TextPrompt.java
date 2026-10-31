package com.egakh.familytree.plugin.menu;

import com.egakh.familytree.plugin.FamilyTreePlugin;
import com.egakh.familytree.plugin.track.Compat;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Text entry for search and rename. Paper, and Spigot from 1.21, open a real anvil: clicking the result confirms the
 * text, and the display item is cleared before Minecraft could hand it back. Older Spigot servers have no API for an
 * anvil that reads typed text, so the player types in chat instead.
 */
final class TextPrompt {
    private static final String CANCEL = "cancel";
    private static final String EMPTY = "-";
    private final FamilyTreePlugin plugin;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    private static final class State {
        final Inventory anvil;
        final Function<String, ItemStack> preview;
        final Consumer<String> confirm;
        final Runnable cancelled;
        volatile String text;
        boolean acting;

        State(Inventory anvil, String text, Function<String, ItemStack> preview, Consumer<String> confirm, Runnable cancelled) {
            this.anvil = anvil;
            this.text = text;
            this.preview = preview;
            this.confirm = confirm;
            this.cancelled = cancelled;
        }
    }

    TextPrompt(FamilyTreePlugin plugin) {
        this.plugin = plugin;
    }

    /** {@code chatKey} explains the chat fallback; {@code chatArg} fills it in. */
    void open(Player player, String title, ItemStack input, String initial, Function<String, ItemStack> preview,
              Consumer<String> confirm, Runnable cancelled, String chatKey, Object... chatArgs) {
        cancel(player);
        Inventory anvil = openAnvil(player, title);
        if (anvil != null) {
            anvil.setItem(0, input);
            states.put(player.getUniqueId(), new State(anvil, initial, preview, confirm, cancelled));
            return;
        }
        player.closeInventory();
        states.put(player.getUniqueId(), new State(null, initial, preview, confirm, cancelled));
        plugin.chat().send(player, chatKey, chatArgs);
    }

    private static Inventory openAnvil(Player player, String title) {
        Object view = null;
        try {
            if (Compat.has(player, "openAnvil", 2)) view = Compat.call(player, "openAnvil", null, true);
            if (view == null) {
                Class<?> menuType = Class.forName("org.bukkit.inventory.MenuType");
                Object anvil = menuType.getField("ANVIL").get(null);
                Object created = anvil.getClass().getMethod("create", HumanEntity.class, String.class).invoke(anvil, player, title);
                if (created instanceof InventoryView inventoryView) {
                    player.openInventory(inventoryView);
                    view = created;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            view = null;
        }
        if (view == null) return null;
        Compat.call(view, "setTitle", title);
        Object top = Compat.call(view, "getTopInventory");
        return top instanceof Inventory inventory ? inventory : null;
    }

    boolean owns(HumanEntity who, Inventory top) {
        State state = states.get(who.getUniqueId());
        return state != null && state.anvil != null && state.anvil.equals(top);
    }

    void clicked(HumanEntity who, int rawSlot, ClickType click) {
        State state = states.get(who.getUniqueId());
        if (state == null || state.acting || rawSlot != 2) return;
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
        state.acting = true;
        String submitted = state.text == null ? "" : state.text;
        plugin.later(() -> {
            if (states.get(who.getUniqueId()) != state) return;
            clear(state);
            states.remove(who.getUniqueId());
            state.confirm.accept(submitted);
        });
    }

    /** The client predicts its own anvil result and cost, so the server resends its view after every keystroke. */
    void prepared(PrepareAnvilEvent event) {
        for (HumanEntity viewer : event.getViewers()) {
            State state = states.get(viewer.getUniqueId());
            if (state == null || state.anvil == null || !state.anvil.equals(event.getInventory())) continue;
            Object text = Compat.call(Compat.call(event, "getView"), "getRenameText");
            if (!(text instanceof String)) text = Compat.call(event.getInventory(), "getRenameText");
            state.text = text instanceof String typed ? typed : "";
            event.setResult(state.preview.apply(state.text));
            if (Compat.call(Compat.call(event, "getView"), "setRepairCost", 0) == null)
                Compat.call(event.getInventory(), "setRepairCost", 0);
            if (viewer instanceof Player player) plugin.later(player::updateInventory);
        }
    }

    void closed(HumanEntity who, Inventory top) {
        State state = states.get(who.getUniqueId());
        if (state == null || state.anvil == null || !state.anvil.equals(top)) return;
        clear(state);
        states.remove(who.getUniqueId());
    }

    /** Runs on the chat thread. Returns true when the message answered a prompt. */
    boolean chatted(Player player, String message) {
        State state = states.get(player.getUniqueId());
        if (state == null || state.anvil != null) return false;
        String text = message.strip();
        plugin.later(() -> {
            if (states.remove(player.getUniqueId()) != state) return;
            if (text.toLowerCase(Locale.ROOT).equals(CANCEL)) state.cancelled.run();
            else state.confirm.accept(text.equals(EMPTY) ? "" : text);
        });
        return true;
    }

    /** Anvils hand their inputs back on close. These are display items, so empty the anvil first. */
    void cancel(Player player) {
        State state = states.remove(player.getUniqueId());
        if (state != null) clear(state);
    }

    private static void clear(State state) {
        if (state.anvil == null) return;
        for (int slot = 0; slot < state.anvil.getSize(); slot++) state.anvil.setItem(slot, null);
    }
}
