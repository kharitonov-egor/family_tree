package com.egakh.familytree.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * A six-row chest whose slots are buttons. Every click is refused before Minecraft moves an item, including
 * clicks on the player's own inventory, so shift-clicks, number keys, drags, double-clicks, and offhand swaps
 * change nothing. The server then resends the real contents.
 */
final class LockedChestMenu extends ChestMenu {
    static final int ROWS = 6;
    static final int SIZE = ROWS * 9;
    private final ServerPlayer owner;
    private final BooleanSupplier throttle;
    private final Map<Integer, Runnable> actions = new HashMap<>();
    private boolean acting;

    LockedChestMenu(int containerId, ServerPlayer owner, BooleanSupplier throttle) {
        super(MenuType.GENERIC_9x6, containerId, owner.getInventory(), new SimpleContainer(SIZE), ROWS);
        this.owner = owner;
        this.throttle = throttle;
    }

    void put(int slot, ItemStack stack, Runnable action) {
        getContainer().setItem(slot, stack);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    void fill(ItemStack filler, int from, int to) {
        for (int slot = from; slot < to; slot++) {
            if (getContainer().getItem(slot).isEmpty()) getContainer().setItem(slot, filler.copy());
        }
    }

    /** The client already moved items in its own view. The offhand and armor are outside this menu, so resend them too. */
    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        owner.inventoryMenu.sendAllDataToRemote();
        if (acting || slot < 0 || slot >= SIZE || button > 1) return;
        if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE) return;
        Runnable action = actions.get(slot);
        if (action == null || !throttle.getAsBoolean()) return;
        acting = true;
        Menus.afterClick(owner, this, action);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }
}
