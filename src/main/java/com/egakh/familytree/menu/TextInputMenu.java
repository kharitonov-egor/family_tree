package com.egakh.familytree.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/** An anvil used as a text field. Clicking the result confirms the text; no item ever leaves the menu. */
final class TextInputMenu extends AnvilMenu {
    private static final int RESULT_SLOT = 2;
    private final ServerPlayer owner;
    private final BooleanSupplier throttle;
    private final Function<String, ItemStack> preview;
    private final Consumer<String> confirm;
    private String text;
    private boolean acting;

    TextInputMenu(int containerId, ServerPlayer owner, BooleanSupplier throttle, ItemStack input, String initial,
                  Function<String, ItemStack> preview, Consumer<String> confirm) {
        super(containerId, owner.getInventory(), ContainerLevelAccess.NULL);
        this.owner = owner;
        this.throttle = throttle;
        this.preview = preview;
        this.confirm = confirm;
        this.text = initial;
        inputSlots.setItem(0, input);
        createResult();
    }

    @Override
    public void createResult() {
        if (preview != null) resultSlots.setItem(0, preview.apply(text));
    }

    /** The client predicts its own anvil result and cost, so resend the server's view after every keystroke. */
    @Override
    public boolean setItemName(String name) {
        text = name == null ? "" : name;
        createResult();
        sendAllDataToRemote();
        return true;
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        owner.inventoryMenu.sendAllDataToRemote();
        if (acting || slot != RESULT_SLOT || button > 1) return;
        if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE || !throttle.getAsBoolean()) return;
        acting = true;
        String submitted = text;
        Menus.afterClick(owner, this, () -> confirm.accept(submitted));
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

    /** Anvils hand their inputs back on close. These inputs are display items, so empty them first. */
    @Override
    public void removed(Player player) {
        inputSlots.clearContent();
        resultSlots.clearContent();
        super.removed(player);
    }
}
