package dev.silentauth.gui;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.Account;
import dev.silentauth.account.LoginService;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Adds an offline account: any username, no token. It only works on cracked / offline-mode
 * servers, where the name in the session is taken as-is.
 */
public final class GuiAddOffline extends SilentAuthScreen {

    private static final int PLAY = 1;
    private static final int SAVE = 2;
    private static final int BACK = 3;
    private static final int PANEL_WIDTH = 330;

    private GuiTextField nameField;

    public GuiAddOffline(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        int left = panelLeft();
        int top = panelTop();

        nameField = new GuiTextField(0, GlassFontRenderer.get(), left + 20, top + 44, PANEL_WIDTH - 40, 16);
        nameField.setMaxStringLength(16);
        nameField.setEnableBackgroundDrawing(false);
        nameField.setTextColor(Theme.TEXT);
        nameField.setFocused(true);

        buttonList.add(new GlassButton(PLAY, left + 20, top + 74, PANEL_WIDTH - 130, 22, "Play").asPrimary());
        buttonList.add(new GlassButton(SAVE, left + PANEL_WIDTH - 100, top + 74, 80, 22, "Save"));
        buttonList.add(new GlassButton(BACK, left + 20, top + 100, PANEL_WIDTH - 40, 22, "Back"));
    }

    private int panelLeft() {
        return width / 2 - PANEL_WIDTH / 2;
    }

    private int panelTop() {
        return Math.max(20, height / 2 - 90);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case PLAY:
                submit(true);
                break;
            case SAVE:
                submit(false);
                break;
            case BACK:
                back();
                break;
            default:
                break;
        }
    }

    private void submit(boolean andPlay) {
        String name = nameField.getText().trim();
        if (name.isEmpty() || name.length() > 16) {
            error("Type a username of 1 to 16 characters");
            return;
        }
        Account account = Account.offline(name);
        SilentAuth.accounts().add(account);
        if (!andPlay) {
            nameField.setText("");
            ok("Saved " + name);
            return;
        }
        LoginService.loginAsync(account, new LoginService.Callback() {
            @Override
            public void onResult(boolean success, String text) {
                result(success, text);
                if (success) {
                    back();
                }
            }
        });
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
            submit(true);
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
        int bottom = top + 134;

        drawPanel(left, top, left + PANEL_WIDTH, bottom);
        drawTitle("Offline account", top + 14);

        drawField(nameField, "Username");

        drawStatus(bottom + 8);
        Text.drawCentred("Only works on cracked / offline-mode servers", width / 2, bottom + 20, Theme.TEXT_FAINT);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
