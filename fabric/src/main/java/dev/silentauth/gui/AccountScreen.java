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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole mod on one screen: paste a token at the top, click a name to play as it. The first row
 * is always the session the launcher started with, so there is always something to go back to.
 */
public final class AccountScreen extends ScreenBase {

    private static final int ADD = 1;
    private static final int MICROSOFT = 2;
    private static final int OFFLINE = 3;
    private static final int SPOOFER = 4;
    private static final int PROXIES = 5;
    private static final int CLOSE = 6;

    private static final int PANEL_WIDTH = 330;
    private static final int ROW_HEIGHT = 34;
    private static final int MAX_ROWS = 6;
    private static final int CHROME_HEIGHT = 76 + 16 + 84 + 12;

    private TextFieldWidget tokenField;
    private AccountList list;
    private List<Account> shown = new ArrayList<Account>();
    private String carried = "";

    public AccountScreen(Screen parent) {
        super(parent, "SilentAuth");
    }

    @Override
    protected void buildLayout() {
        shown = SilentAuth.accounts().all();

        int left = centreLeft(PANEL_WIDTH);
        int top = panelTop();
        int fieldTop = top + 40;
        int listTop = top + 76;
        int listHeight = listHeight();

        tokenField = field(left + 20, fieldTop, PANEL_WIDTH - 110, 16, "Paste a session token", 4096);
        tokenField.setText(carried);
        addDrawableChild(tokenField);
        setInitialFocus(tokenField);

        addButton(new GlassButton(ADD, left + PANEL_WIDTH - 82, fieldTop - 6, 62, 24, "Add").asPrimary());

        list = new AccountList(left + 8, listTop, PANEL_WIDTH - 16, listHeight, ROW_HEIGHT);

        int buttonsTop = listTop + listHeight + 16;
        int half = (PANEL_WIDTH - 40 - 10) / 2;
        addButton(new GlassButton(OFFLINE, left + 20, buttonsTop, half, 24, "Offline"));
        addButton(new GlassButton(MICROSOFT, left + 30 + half, buttonsTop, half, 24, "Microsoft"));
        addButton(new GlassButton(SPOOFER, left + 20, buttonsTop + 30, half, 24, "Spoofer"));
        addButton(new GlassButton(PROXIES, left + 30 + half, buttonsTop + 30, half, 24, "Proxies"));
        addButton(new GlassButton(CLOSE, left + 20, buttonsTop + 60, PANEL_WIDTH - 40, 24, "Close"));

        SilentAuth.checker().checkAll(shown);
    }

    private void relayout() {
        MinecraftClient.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                carried = tokenField == null ? "" : tokenField.getText();
                clearAndInit();
            }
        });
    }

    // ------------------------------------------------------------------ layout

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
    protected void onButton(int id) {
        switch (id) {
            case ADD:
                addPastedToken();
                break;
            case MICROSOFT:
                client.setScreen(new DeviceLoginScreen(this, SilentAuth.proxies().getDefault()));
                break;
            case OFFLINE:
                client.setScreen(new OfflineScreen(this));
                break;
            case SPOOFER:
                client.setScreen(new SpooferScreen(this));
                break;
            case PROXIES:
                client.setScreen(new ProxyScreen(this));
                break;
            case CLOSE:
                close();
                break;
            default:
                break;
        }
    }

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
                    carried = "";
                    playAs(account);
                } catch (AuthException e) {
                    busy = false;
                    error(e.getMessage());
                }
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
                relayout();
            }
        });
    }

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
    public boolean keyPressed(KeyInput input) {
        if (input.isEscape()) {
            close();
            return true;
        }
        if (input.isUp() || input.isDown()) {
            list.moveSelection(input.isUp() ? -1 : 1);
            return true;
        }
        if (input.isEnter()) {
            if (tokenField.getText().trim().isEmpty()) {
                list.activateSelection();
            } else {
                addPastedToken();
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        return list.mouseClicked(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        list.scroll(vertical);
        return true;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY) {
        int left = centreLeft(PANEL_WIDTH);
        int top = panelTop();
        int listTop = top + 76;
        int listHeight = listHeight();

        drawPanel(left, top, left + PANEL_WIDTH, top + panelHeight());
        drawTitle("SilentAuth", top + 14);

        drawField(tokenField);
        Draw.well(left + 8, listTop, left + PANEL_WIDTH - 8, listTop + listHeight,
                FIELD_RADIUS, Theme.FIELD, Theme.FIELD_BORDER);
        list.draw(mouseX, mouseY);

        drawStatusOr(listTop + listHeight + 4, "Click a name to play    the x on a row removes it");
    }

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
