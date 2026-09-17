package dev.silentauth.gui;

import dev.silentauth.account.SessionSwapper;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Changes just the username the client and offline servers see, without touching the account
 * that is logged in. Handy for cracked servers; a premium server checks the name against
 * Mojang, so a spoofed name is rejected there.
 */
public final class GuiSpoofer extends SilentAuthScreen {

    private static final int SPOOF = 1;
    private static final int RESET = 2;
    private static final int BACK = 3;
    private static final int PANEL_WIDTH = 330;

    private GuiTextField nameField;

    public GuiSpoofer(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        int left = panelLeft();
        int top = panelTop();

        nameField = new GuiTextField(0, GlassFontRenderer.get(), left + 20, top + 60, PANEL_WIDTH - 40, 16);
        nameField.setMaxStringLength(16);
        nameField.setEnableBackgroundDrawing(false);
        nameField.setTextColor(Theme.TEXT);
        nameField.setFocused(true);
        nameField.setText(SessionSwapper.currentUsername());

        buttonList.add(new GlassButton(SPOOF, left + 20, top + 90, PANEL_WIDTH - 40, 22, "Spoof name").asPrimary());
        buttonList.add(new GlassButton(RESET, left + 20, top + 116, (PANEL_WIDTH - 46) / 2, 22, "Reset"));
        buttonList.add(new GlassButton(BACK, left + 26 + (PANEL_WIDTH - 46) / 2, top + 116,
                (PANEL_WIDTH - 46) / 2, 22, "Back"));
    }

    private int panelLeft() {
        return width / 2 - PANEL_WIDTH / 2;
    }

    private int panelTop() {
        return Math.max(16, height / 2 - 100);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case SPOOF:
                spoof();
                break;
            case RESET:
                if (SessionSwapper.hasOriginal()) {
                    SessionSwapper.restoreOriginal();
                    nameField.setText(SessionSwapper.originalUsername());
                    ok("Name reset to " + SessionSwapper.originalUsername());
                } else {
                    error("The original session was not captured");
                }
                break;
            case BACK:
                back();
                break;
            default:
                break;
        }
    }

    private void spoof() {
        String name = nameField.getText().trim();
        if (name.isEmpty() || name.length() > 16) {
            error("Type a username of 1 to 16 characters");
            return;
        }
        SessionSwapper.spoof(name);
        ok("Name spoofed to " + name);
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (handledBack(keyCode)) {
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            spoof();
            return;
        }
        nameField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackdrop();

        int left = panelLeft();
        int top = panelTop();
        int bottom = top + 150;

        drawPanel(left, top, left + PANEL_WIDTH, bottom);
        drawTitle("Spoof username", top + 14);
        Text.drawCentred("Currently: " + SessionSwapper.currentUsername(), width / 2, top + 34, Theme.TEXT_DIM);

        drawField(nameField, "New username");

        drawStatus(bottom + 8);
        Text.drawCentred("Cracked / offline servers only", width / 2, bottom + 20, Theme.TEXT_FAINT);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
