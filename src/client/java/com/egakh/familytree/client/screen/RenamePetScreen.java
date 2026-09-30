package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.naming.PetRenaming;
import com.egakh.familytree.network.payloads.RenamePetRequest;
import com.egakh.familytree.network.payloads.RenamePetResult;
import com.egakh.familytree.client.platform.ClientTransport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


import java.util.UUID;

public final class RenamePetScreen extends FamilyTreeScreen {
    private final FamilyTreeViewScreen parent;
    private final AnimalRecord record;
    private EditBox nameInput;
    private Button saveButton;
    private Button resetButton;
    private UUID pendingRequest;
    private int pendingTicks;
    private Component error = Component.empty();
    private int panelX;
    private int panelY;
    private int panelWidth;

    public RenamePetScreen(FamilyTreeViewScreen parent, AnimalRecord record) {
        super(Component.translatable("familytree.rename.title"));
        this.parent = parent;
        this.record = record;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(320, this.width - 24);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - 178) / 2;
        String value = nameInput == null ? record.name() : nameInput.getValue();
        nameInput = new EditBox(this.font, panelX + 12, panelY + 78, panelWidth - 24, 20,
                Component.translatable("familytree.rename.label"));
        nameInput.setMaxLength(PetRenaming.MAX_NAME_LENGTH);
        nameInput.setValue(value);
        nameInput.setResponder(text -> {
            error = Component.empty();
            updateButtons();
        });
        this.addRenderableWidget(nameInput);
        this.setInitialFocus(nameInput);
        int buttonWidth = (panelWidth - 32) / 3;
        saveButton = this.addRenderableWidget(Button.builder(Component.translatable("familytree.rename.save"),
                button -> submit(nameInput.getValue())).bounds(panelX + 12, panelY + 144, buttonWidth, 20).build());
        resetButton = this.addRenderableWidget(Button.builder(Component.translatable("familytree.rename.reset"),
                button -> submit("")).bounds(panelX + 16 + buttonWidth, panelY + 144, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("familytree.rename.cancel"),
                button -> onClose()).bounds(panelX + 20 + buttonWidth * 2, panelY + 144, buttonWidth, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        if (saveButton == null) return;
        boolean ready = pendingRequest == null;
        saveButton.active = ready && PetRenaming.isValidName(nameInput.getValue())
                && !nameInput.getValue().strip().equals(record.name());
        resetButton.active = ready && record.treeName() != null;
        nameInput.setEditable(ready);
    }

    private void submit(String name) {
        if (pendingRequest != null) return;
        if (!name.isEmpty() && !PetRenaming.isValidName(name)) return;
        if (!ClientTransport.canSend(RenamePetRequest.TYPE)) {
            error = Component.translatable("familytree.rename.unsupported");
            return;
        }
        pendingRequest = UUID.randomUUID();
        pendingTicks = 0;
        error = Component.empty();
        updateButtons();
        ClientTransport.send(new RenamePetRequest(pendingRequest, record.id(), name));
    }

    public void receiveResult(RenamePetResult result) {
        if (!result.requestId().equals(pendingRequest)) return;
        pendingRequest = null;
        if (result.status() == PetRenaming.Result.SUCCESS.ordinal() && result.record().isPresent()) {
            parent.applyRename(result.record().get());
            onClose();
            return;
        }
        String key = switch (result.status()) {
            case 1 -> "familytree.rename.not_found";
            case 2 -> "familytree.rename.not_allowed";
            case 3 -> "familytree.rename.invalid";
            case 4 -> "familytree.command.read_only";
            case 5 -> "familytree.command.cooldown";
            default -> "familytree.rename.failed";
        };
        error = Component.translatable(key);
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingRequest != null && ++pendingTicks >= 200) {
            pendingRequest = null;
            error = Component.translatable("familytree.rename.timeout");
            updateButtons();
        }
    }

    @Override
    protected boolean onKeyPress(int key) {
        if ((key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) && saveButton.active) {
            submit(nameInput.getValue());
            return true;
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, this.width, this.height, 0xE610141A);
        gfx.fill(panelX, panelY, panelX + panelWidth, panelY + 178, 0xFF202830);
        gfx.centeredText(this.font, this.title, this.width / 2, panelY + 12, 0xFFFFFFFF);
        int y = panelY + 32;
        for (var line : this.font.split(Component.translatable("familytree.rename.hint"), panelWidth - 24)) {
            gfx.text(this.font, line, panelX + 12, y, 0xFFB0B7C0);
            y += this.font.lineHeight + 2;
        }
        gfx.text(this.font, Component.translatable("familytree.rename.label"), panelX + 12, panelY + 64, 0xFFFFFFFF);
        Component status = pendingRequest != null ? Component.translatable("familytree.rename.saving") : error;
        y = panelY + 106;
        for (var line : this.font.split(status, panelWidth - 24)) {
            gfx.text(this.font, line, panelX + 12, y, pendingRequest != null ? 0xFFB0B7C0 : 0xFFFF9292);
            y += this.font.lineHeight + 2;
        }
        renderWidgets(gfx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
