package dev.silentauth.gui;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.Account;
import dev.silentauth.account.LoginService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;

/**
 * Adds an offline account: any username, no token. It only works on cracked / offline-mode
 * servers, where the name in the session is taken as-is.
 */
public final class OfflineScreen extends ScreenBase {

    private static final int PLAY = 1;
    private static final int SAVE = 2;
    private static final int BACK = 3;
    private static final int PANEL_WIDTH = 330;

    private TextFieldWidget nameField;

    public OfflineScreen(Screen parent) {
        super(parent, "Offline account");
    }

    @Override
    protected void buildLayout() {
        int left = centreLeft(PANEL_WIDTH);
        int top = panelTop();

        nameField = field(left + 20, top + 44, PANEL_WIDTH - 40, 16, "Username", 16);
        addDrawableChild(nameField);
        setInitialFocus(nameField);

        addButton(new GlassButton(PLAY, left + 20, top + 74, PANEL_WIDTH - 130, 24, "Play").asPrimary());
        addButton(new GlassButton(SAVE, left + PANEL_WIDTH - 100, top + 74, 80, 24, "Save"));
        addButton(new GlassButton(BACK, left + 20, top + 104, PANEL_WIDTH - 40, 24, "Back"));
    }

    private int panelTop() {
        return Math.max(20, height / 2 - 90);
    }

    @Override
    protected void onButton(int id) {
        switch (id) {
            case PLAY:
                submit(true);
                break;
            case SAVE:
                submit(false);
                break;
            case BACK:
                close();
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
                    close();
                }
            }
        });
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.isEscape()) {
            close();
            return true;
        }
        if (input.isEnter()) {
            submit(true);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY) {
        int left = centreLeft(PANEL_WIDTH);
        int top = panelTop();
        int bottom = top + 138;

        drawPanel(left, top, left + PANEL_WIDTH, bottom);
        drawTitle("Offline account", top + 14);
        drawField(nameField);

        drawStatus(bottom + 8);
        Text.drawCentred("Only works on cracked / offline-mode servers", width / 2, bottom + 22, Theme.TEXT_FAINT);
    }
}
