package com.egakh.familytree.plugin.menu;

import com.egakh.familytree.menu.MenuText;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * A six-row chest whose slots are buttons. {@link MenuManager} cancels every click and drag while it is open,
 * including clicks in the player's own inventory, before Minecraft moves an item.
 */
final class ChestView implements InventoryHolder {
    static final int ROWS = 6;
    static final int SIZE = ROWS * 9;
    private final Inventory inventory;
    private final Map<Integer, Runnable> actions = new HashMap<>();
    boolean acting;

    ChestView(String title) {
        this.inventory = Bukkit.createInventory(this, SIZE, MenuText.trim(title, 64));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void put(int slot, ItemStack stack, Runnable action) {
        inventory.setItem(slot, stack);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    void fill(ItemStack filler, int from, int to) {
        for (int slot = from; slot < to; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) inventory.setItem(slot, filler.clone());
        }
    }

    Runnable action(int slot) {
        return actions.get(slot);
    }
}
