package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.feedback.FeedbackSender;
import com.egakh.familytree.client.platform.ClientEnvironment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Lists every value that leaves the computer, and sends nothing until the player presses Send. */
public final class FeedbackScreen extends FamilyTreeScreen {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFB0B7C0;
    private static final int ERROR = 0xFFFF9292;
    private static final int SUCCESS = 0xFF8FE08F;
    private static final int MAX_MESSAGE_HEIGHT = 140;

    private final Screen parent;
    private MultiLineEditBox message;
    private EditBox contact;
    private Button sendButton;
    private boolean sending;
    private boolean sent;
    private Component status = Component.empty();
    private int left, contentWidth, contactLabelY, previewTop;

    public FeedbackScreen(Screen parent) {
        super(Component.translatable("familytree.feedback.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        contentWidth = Math.min(440, this.width - 32);
        left = (this.width - contentWidth) / 2;
        String previousMessage = message == null ? "" : message.getValue();
        String previousContact = contact == null ? "" : contact.getValue();
        int previewHeight = preview().stream().mapToInt(line -> this.font.split(line, contentWidth - 8).size()).sum()
                * (this.font.lineHeight + 2);
        int buttonsY = this.height - 26;
        int messageTop = 36;
        int messageHeight = Math.max(36, Math.min(MAX_MESSAGE_HEIGHT,
                buttonsY - 16 - previewHeight - 8 - 36 - 6 - messageTop));
        //? if >=1.21.6 {
        message = MultiLineEditBox.builder().setX(left).setY(messageTop)
                .setPlaceholder(Component.translatable("familytree.feedback.message_hint"))
                .build(this.font, contentWidth, messageHeight, Component.translatable("familytree.feedback.message"));
        //?} else {
        /*message = new MultiLineEditBox(this.font, left, messageTop, contentWidth, messageHeight,
                Component.translatable("familytree.feedback.message_hint"), Component.translatable("familytree.feedback.message"));
        *///?}
        message.setCharacterLimit(FeedbackSender.MAX_MESSAGE);
        message.setValue(previousMessage);
        message.setValueListener(text -> changed());
        addRenderableWidget(message);
        setInitialFocus(message);

        contactLabelY = messageTop + messageHeight + 6;
        contact = new EditBox(this.font, left, contactLabelY + 12, contentWidth, 18, Component.translatable("familytree.feedback.contact"));
        contact.setMaxLength(FeedbackSender.MAX_CONTACT);
        contact.setHint(Component.translatable("familytree.feedback.contact_hint"));
        contact.setValue(previousContact);
        contact.setResponder(text -> changed());
        addRenderableWidget(contact);
        previewTop = contactLabelY + 36;

        int buttonWidth = Math.min(120, (contentWidth - 8) / 2);
        sendButton = addRenderableWidget(Button.builder(Component.translatable("familytree.feedback.send"), button -> send())
                .bounds(this.width / 2 - buttonWidth - 4, buttonsY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("familytree.screen.back"), button -> onClose())
                .bounds(this.width / 2 + 4, buttonsY, buttonWidth, 20).build());
        updateButtons();
    }

    private void changed() {
        if (!sending) {
            sent = false;
            status = Component.empty();
        }
        updateButtons();
    }

    private void updateButtons() {
        if (sendButton == null) return;
        sendButton.active = !sending && !sent && !message.getValue().isBlank();
    }

    private FeedbackSender.Report report() {
        String language = this.minecraft == null ? "unknown" : this.minecraft.getLanguageManager().getSelected();
        return new FeedbackSender.Report(message == null ? "" : message.getValue().strip(),
                contact == null ? "" : contact.getValue().strip(), ClientEnvironment.versions(), language);
    }

    private void send() {
        if (sending) return;
        sending = true;
        status = Component.translatable("familytree.feedback.sending");
        updateButtons();
        FeedbackSender.send(report()).thenAccept(outcome -> this.minecraft.execute(() -> {
            sending = false;
            sent = outcome.sent();
            status = Component.translatable(sent ? "familytree.feedback.sent" : "familytree.feedback.error." + outcome.error());
            updateButtons();
        }));
    }

    /** Each value shown here is the value sent, so the list must change whenever the request body does. */
    private List<Component> preview() {
        FeedbackSender.Report report = report();
        ClientEnvironment.Versions versions = report.versions();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("familytree.feedback.preview"));
        lines.add(Component.translatable("familytree.feedback.preview.message", report.message().length(), FeedbackSender.MAX_MESSAGE));
        lines.add(report.contact().isEmpty() ? Component.translatable("familytree.feedback.preview.no_contact")
                : Component.translatable("familytree.feedback.preview.contact", report.contact()));
        lines.add(Component.translatable("familytree.feedback.preview.versions", versions.mod(), versions.minecraft(),
                versions.loader(), versions.loaderVersion()));
        lines.add(Component.translatable("familytree.feedback.preview.language", report.language()));
        lines.add(Component.translatable("familytree.feedback.notice"));
        return lines;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, this.width, this.height, 0xE610141A);
        gfx.centeredText(this.font, this.title, this.width / 2, 10, TEXT);
        gfx.text(this.font, Component.translatable("familytree.feedback.message"), left, 24, TEXT);
        gfx.text(this.font, Component.translatable("familytree.feedback.contact"), left, contactLabelY, TEXT);
        int y = previewTop;
        List<Component> preview = preview();
        for (int i = 0; i < preview.size(); i++) {
            int indent = i == 0 || i == preview.size() - 1 ? 0 : 8;
            for (FormattedCharSequence line : this.font.split(preview.get(i), contentWidth - indent)) {
                gfx.text(this.font, line, left + indent, y, i == 0 ? TEXT : MUTED);
                y += this.font.lineHeight + 2;
            }
        }
        int statusY = this.height - 40;
        for (FormattedCharSequence line : this.font.split(status, contentWidth)) {
            gfx.text(this.font, line, left, statusY, sent ? SUCCESS : sending ? MUTED : ERROR);
            statusY += this.font.lineHeight + 2;
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
