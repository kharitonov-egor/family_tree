package com.egakh.familytree.menu;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Shows a written book through the lectern screen. Page buttons work; Take Book does nothing. */
final class BookMenu extends LecternMenu {
    private static final int TAKE_BOOK = 3;

    BookMenu(int containerId, ItemStack book, int pageCount) {
        super(containerId, new SimpleContainer(book), new Pages(pageCount));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return id != TAKE_BOOK && super.clickMenuButton(player, id);
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {}

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    private static final class Pages implements ContainerData {
        private final int count;
        private int page;

        Pages(int count) {
            this.count = Math.max(1, count);
        }

        @Override public int get(int index) { return page; }
        @Override public void set(int index, int value) { page = Math.max(0, Math.min(count - 1, value)); }
        @Override public int getCount() { return 1; }
    }
}
