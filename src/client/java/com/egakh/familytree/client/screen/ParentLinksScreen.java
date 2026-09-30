package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.network.payloads.FamilyTreeSnapshotPayload;
import com.egakh.familytree.network.payloads.LinkParentsRequest;
import com.egakh.familytree.network.payloads.LinkParentsResult;
import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.client.platform.ClientTransport;
import com.egakh.familytree.permissions.PetAccess;
import com.egakh.familytree.permissions.ParentLinks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ParentLinksScreen extends FamilyTreeScreen {
    private final FamilyTreeViewScreen parent;
    private final FamilyTreeSnapshotPayload snapshot;
    private final AnimalRecord child;
    private final Map<UUID, AnimalRecord> records = new HashMap<>();
    private UUID a, b, pending;
    private int pendingTicks;
    private Button save, clear;
    private Component error = Component.empty();

    public ParentLinksScreen(FamilyTreeViewScreen parent, FamilyTreeSnapshotPayload snapshot, AnimalRecord child) {
        super(Component.translatable("familytree.parents.title", child.name()));
        this.parent = parent; this.snapshot = snapshot; this.child = child;
        a = child.parentA(); b = child.parentB();
        snapshot.records().forEach(pet -> records.put(pet.id(), pet));
    }

    @Override protected void init() {
        int x = (width - Math.min(360, width - 24)) / 2, w = Math.min(360, width - 24);
        addRenderableWidget(Button.builder(label(a, 1), button -> choose(true)).bounds(x, 78, w, 20).build());
        addRenderableWidget(Button.builder(label(b, 2), button -> choose(false)).bounds(x, 104, w, 20).build());
        save = addRenderableWidget(Button.builder(Component.translatable("familytree.parents.confirm"), button -> submit(false))
                .bounds(x, 140, w, 20).build());
        clear = addRenderableWidget(Button.builder(Component.translatable("familytree.parents.clear"), button -> submit(true))
                .bounds(x, 166, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"), button -> onClose())
                .bounds(x, height - 28, w, 20).build());
        save.active = pending == null && a != null && b != null;
        clear.active = pending == null && (child.parentA() != null || child.parentB() != null);
    }

    private Component label(UUID id, int number) {
        AnimalRecord pet = records.get(id);
        return Component.translatable("familytree.parents.choose", number, pet == null ? "?" : pet.name());
    }

    private void choose(boolean first) {
        var player = minecraft.player;
        if (player == null || pending != null) return;
        minecraft.setScreen(new PetPickerScreen(this, records.values().stream()
                .filter(pet -> !pet.id().equals(child.id()) && PetAccess.canManage(pet, player.getUUID(), snapshot.mayManageAll()))
                .toList(), chosen -> { if (first) a = chosen; else b = chosen; }));
    }

    private void submit(boolean remove) {
        if (pending != null || minecraft.player == null) return;
        ParentLinks.Result result = ParentLinks.validate(child.id(), a, b, remove, minecraft.player.getUUID(), snapshot.mayManageAll(), records::get);
        if (result != ParentLinks.Result.SUCCESS) { error = message(result.ordinal()); return; }
        if (remove) {
            minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(confirmed -> {
                minecraft.setScreen(this);
                if (confirmed) send(true);
            }, Component.translatable("familytree.parents.clear_confirm", child.name()), Component.translatable("familytree.parents.clear_hint")));
        } else send(false);
    }

    private void send(boolean remove) {
        pending = UUID.randomUUID(); pendingTicks = 0;
        save.active = false; clear.active = false;
        UUID zero = new UUID(0, 0);
        ClientTransport.send(new LinkParentsRequest(pending, child.id(), a == null ? zero : a, b == null ? zero : b, remove));
    }

    public void receive(LinkParentsResult result) {
        if (!result.requestId().equals(pending)) return;
        pending = null;
        if (result.status() == 0) {
            minecraft.setScreen(parent);
            FamilyTreeClient.requestSnapshot(snapshot.viewingAll());
        } else { error = message(result.status()); clearWidgets(); init(); }
    }

    private Component message(int status) {
        String key = switch (status) {
            case 1 -> "familytree.access.denied";
            case 3 -> "familytree.parents.distinct";
            case 4 -> "familytree.command.pair.species_mismatch";
            case 5 -> "familytree.command.pair.cycle";
            case 6 -> "familytree.command.read_only";
            case 7 -> "familytree.command.cooldown";
            default -> "familytree.parents.failed";
        };
        return Component.translatable(key);
    }

    @Override public void tick() {
        if (pending != null && ++pendingTicks >= 200) {
            pending = null; error = Component.translatable("familytree.rename.timeout"); clearWidgets(); init();
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, width, height, 0xFF151A21);
        gfx.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        int y = 34;
        for (var line : font.split(Component.translatable("familytree.parents.hint"), width - 32)) {
            gfx.text(font, line, 16, y, 0xFFB0B7C0); y += font.lineHeight + 2;
        }
        y = 198;
        for (var line : font.split(pending == null ? error : Component.translatable("familytree.rename.saving"), width - 32)) {
            gfx.text(font, line, 16, y, 0xFFFF9292); y += font.lineHeight + 2;
        }
        renderWidgets(gfx, mouseX, mouseY, delta);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
