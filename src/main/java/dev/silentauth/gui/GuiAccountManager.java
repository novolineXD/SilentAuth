package dev.silentauth.gui;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.Account;
import dev.silentauth.account.LoginService;
import dev.silentauth.account.SessionSwapper;
import dev.silentauth.account.TokenParser;
import dev.silentauth.account.Validity;
import dev.silentauth.auth.AuthException;
import dev.silentauth.auth.SessionTokenAuth;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Async;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole mod on one screen: paste a token at the top, click a name to play as it.
 *
 * <p>The first row is always the session the launcher started with, so there is always
 * something to go back to and no separate restore button is needed. Tokens validate
 * themselves when the screen opens.</p>
 */
public final class GuiAccountManager extends SilentAuthScreen {

    private static final int ADD = 1;
    private static final int MICROSOFT = 2;
    private static final int PROXIES = 3;
    private static final int CLOSE = 4;

    private static final int PANEL_WIDTH = 330;
    private static final int ROW_HEIGHT = 28;
    private static final int MAX_ROWS = 6;
    /** Title, field, status band, two button rows and the bottom padding. */
    private static final int CHROME_HEIGHT = 76 + 16 + 50 + 14;

    private GuiTextField tokenField;
    private ListWidget list;
    private List<Account> shown = new ArrayList<Account>();

    public GuiAccountManager(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        shown = SilentAuth.accounts().all();

        int left = panelLeft();
        int top = panelTop();
        int fieldTop = top + 40;
        int listTop = top + 76;
        int listHeight = listHeight();

        String carried = tokenField == null ? "" : tokenField.getText();
        tokenField = new GuiTextField(0, GlassFontRenderer.get(), left + 20, fieldTop, PANEL_WIDTH - 110, 12);
        tokenField.setMaxStringLength(4096);
        tokenField.setEnableBackgroundDrawing(false);
        tokenField.setTextColor(Theme.TEXT);
        tokenField.setText(carried);
        tokenField.setFocused(true);

        buttonList.add(new GlassButton(ADD, left + PANEL_WIDTH - 82, fieldTop - 6, 62, 24, "Add").asPrimary());

        list = new AccountList(left + 8, listTop, PANEL_WIDTH - 16, listHeight, ROW_HEIGHT);

        int buttonsTop = listTop + listHeight + 16;
        int half = (PANEL_WIDTH - 40 - 10) / 2;
        buttonList.add(new GlassButton(MICROSOFT, left + 20, buttonsTop, half, 22, "Microsoft sign in"));
        buttonList.add(new GlassButton(PROXIES, left + 30 + half, buttonsTop, half, 22, "Proxies"));
        buttonList.add(new GlassButton(CLOSE, left + 20, buttonsTop + 28, PANEL_WIDTH - 40, 22, "Close"));

        SilentAuth.checker().checkAll(shown);
    }

    /** Rebuilds the layout after the number of rows changed, keeping anything half typed. */
    private void relayout() {
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                initGui();
            }
        });
    }

    // ------------------------------------------------------------------ layout

    private int panelLeft() {
        return width / 2 - PANEL_WIDTH / 2;
    }

    /** How many rows fit, so the panel never runs off a short screen at a large gui scale. */
    private int listHeight() {
        int room = (height - 20 - CHROME_HEIGHT) / ROW_HEIGHT;
        int rows = Math.min(Math.min(MAX_ROWS, Math.max(1, room)), Math.max(1, shown.size() + 1));
        return rows * ROW_HEIGHT;
    }

    private int panelHeight() {
        return CHROME_HEIGHT + listHeight();
    }

    private int panelTop() {
        return Math.max(6, (height - panelHeight()) / 2);
    }

    private static boolean isOwnRow(int index) {
        return index == 0;
    }

    private Account accountAt(int index) {
        int offset = index - 1;
        return offset < 0 || offset >= shown.size() ? null : shown.get(offset);
    }

    // ------------------------------------------------------------------ actions

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ADD:
                addPastedToken();
                break;
            case MICROSOFT:
                mc.displayGuiScreen(new GuiDeviceLogin(this, SilentAuth.proxies().getDefault()));
                break;
            case PROXIES:
                mc.displayGuiScreen(new GuiProxyManager(this));
                break;
            case CLOSE:
                back();
                break;
            default:
                break;
        }
    }

    /** Stores the pasted token and switches to it in one go. */
    private void addPastedToken() {
        if (busy) {
            return;
        }
        String pasted = tokenField.getText().trim();
        if (pasted.isEmpty()) {
            error("Paste a session token first");
            return;
        }

        final TokenParser.Parsed parsed;
        try {
            parsed = TokenParser.parse(pasted);
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
            return;
        }

        busy = true;
        info("Checking the token");
        Async.run(new Runnable() {
            @Override
            public void run() {
                try {
                    Account account = !parsed.username.isEmpty() && !parsed.uuid.isEmpty()
                            ? SessionTokenAuth.withoutLookup(parsed.token, parsed.username, parsed.uuid)
                            : SessionTokenAuth.login(parsed.token, SilentAuth.proxies().getDefault());
                    SilentAuth.accounts().add(account);
                    clearField();
                    playAs(account);
                } catch (AuthException e) {
                    busy = false;
                    error(e.getMessage());
                }
            }
        });
    }

    private void clearField() {
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                tokenField.setText("");
                initGui();
            }
        });
    }

    private void playAs(Account account) {
        busy = true;
        info("Switching to " + account.getUsername());
        LoginService.loginAsync(account, new LoginService.Callback() {
            @Override
            public void onResult(boolean success, String text) {
                busy = false;
                result(success, text);
            }
        });
    }

    /** Puts the launcher's own session back. */
    private void playAsSelf() {
        if (!SessionSwapper.hasOriginal()) {
            error("The original session was not captured");
            return;
        }
        SessionSwapper.restoreOriginal();
        SilentAuth.accounts().setActive(null);
        ok("Playing as " + SessionSwapper.originalUsername());
    }

    // ------------------------------------------------------------------ input

    @Override
    public void updateScreen() {
        tokenField.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (handledBack(keyCode)) {
            return;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            list.moveSelection(keyCode == Keyboard.KEY_UP ? -1 : 1);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            if (tokenField.getText().trim().isEmpty()) {
                list.activateSelection();
            } else {
                addPastedToken();
            }
            return;
        }
        tokenField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        tokenField.mouseClicked(mouseX, mouseY, mouseButton);
        list.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        list.handleScroll();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackdrop();

        int left = panelLeft();
        int top = panelTop();
        int listTop = top + 76;
        int listHeight = listHeight();

        drawPanel(left, top, left + PANEL_WIDTH, top + panelHeight());
        drawTitle("SilentAuth", top + 14);

        drawField(tokenField, "Paste a session token");
        Draw.well(left + 8, listTop, left + PANEL_WIDTH - 8, listTop + listHeight,
                FIELD_RADIUS, Theme.FIELD, Theme.FIELD_BORDER);
        list.draw(mouseX, mouseY);

        drawStatusOr(listTop + listHeight + 4, "Click a name to play    the x on a row removes it");
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** The account rows, with the launcher's own session pinned at the top. */
    private final class AccountList extends ListWidget {

        AccountList(int x, int y, int width, int height, int rowHeight) {
            super(x, y, width, height, rowHeight);
        }

        @Override
        int getSize() {
            return shown.size() + 1;
        }

        @Override
        boolean isRemovable(int index) {
            return !isOwnRow(index);
        }

        @Override
        void onActivated(int index) {
            if (busy) {
                return;
            }
            if (isOwnRow(index)) {
                playAsSelf();
                return;
            }
            Account account = accountAt(index);
            if (account != null) {
                playAs(account);
            }
        }

        @Override
        boolean onRemove(int index) {
            Account account = accountAt(index);
            if (account == null) {
                return false;
            }
            SilentAuth.accounts().remove(account);
            clearSelection();
            info("Removed " + account.getUsername());
            relayout();
            return true;
        }

        @Override
        void drawRow(int index, int rowX, int rowY, int rowWidth, boolean hovered) {
            if (isOwnRow(index)) {
                boolean inUse = SilentAuth.accounts().getActive() == null;
                paintRow(rowX, rowY, rowWidth, inUse ? Theme.OK : Theme.IDLE,
                        "Your account", SessionSwapper.originalUsername());
                return;
            }
            Account account = accountAt(index);
            if (account == null) {
                return;
            }

            boolean inUse = SilentAuth.accounts().isActive(account);
            boolean invalid = account.getValidity() == Validity.INVALID;
            int status = inUse ? Theme.OK : invalid ? Theme.DANGER : Theme.IDLE;

            String detail;
            if (invalid && !account.getDetail().isEmpty()) {
                detail = account.getDetail();
            } else {
                ProxyEntry proxy = LoginService.resolveProxy(account);
                detail = account.getType().getLabel() + (proxy == null ? "" : "   " + proxy.describe());
            }
            paintRow(rowX, rowY, rowWidth, status, account.getUsername(), detail);
        }

        @Override
        String emptyText() {
            return "Paste a token above to add an account";
        }
    }
}
