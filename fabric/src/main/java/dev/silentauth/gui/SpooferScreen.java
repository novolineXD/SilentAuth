package dev.silentauth.gui;

import dev.silentauth.account.SessionSwapper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;

/**
 * Changes just the username the client and offline servers see, without touching the account that
 * is logged in. A premium server checks the name against Mojang, so a spoofed name is rejected
 * there; it is meant for cracked / offline servers.
 */
public final class SpooferScreen extends ScreenBase {

    private static final int SPOOF = 1;
    private static final int RESET = 2;
    private static final int BACK = 3;
    private static final int PANEL_WIDTH = 330;

    private TextFieldWidget nameField;

    public SpooferScreen(Screen parent) {
        super(parent, "Spoof username");
    }

    @Override
    protected void buildLayout() {
        int left = centreLeft(PANEL_WIDTH);
        int top = panelTop();

        nameField = field(left + 20, top + 60, PANEL_WIDTH - 40, 16, "New username", 16);
        nameField.setText(SessionSwapper.currentUsername());
        addDrawableChild(nameField);
        setInitialFocus(nameField);

        addButton(new GlassButton(SPOOF, left + 20, top + 90, PANEL_WIDTH - 40, 24, "Spoof name").asPrimary());
        addButton(new GlassButton(RESET, left + 20, top + 118, (PANEL_WIDTH - 46) / 2, 24, "Reset"));
        addButton(new GlassButton(BACK, left + 26 + (PANEL_WIDTH - 46) / 2, top + 118,
                (PANEL_WIDTH - 46) / 2, 24, "Back"));
    }

    private int panelTop() {
        return Math.max(16, height / 2 - 100);
    }

    @Override
    protected void onButton(int id) {
        switch (id) {
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
                close();
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
    public boolean keyPressed(KeyInput input) {
        if (input.isEscape()) {
            close();
            return true;
        }
        if (input.isEnter()) {
            spoof();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY) {
        int left = centreLeft(PANEL_WIDTH);
        int top = panelTop();
        int bottom = top + 154;

        drawPanel(left, top, left + PANEL_WIDTH, bottom);
        drawTitle("Spoof username", top + 14);
        Text.drawCentred("Currently: " + SessionSwapper.currentUsername(), width / 2, top + 34, Theme.TEXT_DIM);

        drawField(nameField);

        drawStatus(bottom + 8);
        Text.drawCentred("Cracked / offline servers only", width / 2, bottom + 22, Theme.TEXT_FAINT);
    }
}
