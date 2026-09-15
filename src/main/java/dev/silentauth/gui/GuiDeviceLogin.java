package dev.silentauth.gui;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.Account;
import dev.silentauth.account.LoginService;
import dev.silentauth.auth.AuthException;
import dev.silentauth.auth.DeviceCode;
import dev.silentauth.auth.MicrosoftAuth;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Async;
import dev.silentauth.util.Log;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.Sys;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shows the code to type at microsoft.com/link, then plays as that account. */
public final class GuiDeviceLogin extends SilentAuthScreen {

    private static final int COPY = 1;
    private static final int OPEN = 2;
    private static final int CLOSE = 3;
    private static final int PANEL_WIDTH = 330;

    private final ProxyEntry proxy;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    private volatile DeviceCode code;
    private volatile boolean finished;
    private boolean started;
    private GuiButton closeButton;

    public GuiDeviceLogin(GuiScreen parent, ProxyEntry proxy) {
        super(parent);
        this.proxy = proxy;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int left = panelLeft();
        int top = panelTop();

        buttonList.add(new GlassButton(OPEN, left + 20, top + 98, PANEL_WIDTH - 40, 22,
                "Open microsoft.com/link").asPrimary());
        buttonList.add(new GlassButton(COPY, left + 20, top + 126, PANEL_WIDTH - 40, 22, "Copy the code"));
        closeButton = new GlassButton(CLOSE, left + 20, top + 154, PANEL_WIDTH - 40, 22,
                finished ? "Done" : "Cancel");
        buttonList.add(closeButton);

        // initGui runs again on every resize; the sign in must only start once.
        if (!started) {
            started = true;
            info("Asking Microsoft for a code");
            begin();
        }
    }

    private int panelLeft() {
        return width / 2 - PANEL_WIDTH / 2;
    }

    private int panelTop() {
        return Math.max(16, height / 2 - 100);
    }

    private void begin() {
        Async.run(new Runnable() {
            @Override
            public void run() {
                try {
                    DeviceCode requested = MicrosoftAuth.requestDeviceCode(proxy);
                    code = requested;
                    info("Type the code at " + requested.getVerificationUri());
                    Account account = MicrosoftAuth.completeDeviceLogin(requested, proxy, cancelled,
                            new MicrosoftAuth.StatusListener() {
                                @Override
                                public void onStatus(String text) {
                                    // The polling tick is noise; the real steps are worth showing.
                                    if (!text.startsWith("Waiting")) {
                                        info(text);
                                    }
                                }
                            });
                    SilentAuth.accounts().add(account);
                    finished = true;
                    playAs(account);
                } catch (AuthException e) {
                    Log.warn("Microsoft sign in failed: " + e.getMessage());
                    finished = true;
                    error(e.getMessage());
                    markDone();
                }
            }
        });
    }

    private void playAs(Account account) {
        LoginService.loginAsync(account, new LoginService.Callback() {
            @Override
            public void onResult(boolean success, String text) {
                result(success, text);
                if (success) {
                    back();
                } else {
                    markDone();
                }
            }
        });
    }

    private void markDone() {
        if (closeButton != null) {
            closeButton.displayString = "Done";
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case COPY:
                DeviceCode current = code;
                if (current == null) {
                    error("There is no code yet");
                    break;
                }
                setClipboardString(current.getUserCode());
                ok("Code copied");
                break;
            case OPEN:
                openLink();
                break;
            case CLOSE:
                back();
                break;
            default:
                break;
        }
    }

    private void openLink() {
        String uri = code == null ? "https://microsoft.com/link" : code.getVerificationUri();
        try {
            Sys.openURL(uri);
        } catch (Throwable t) {
            error("Could not open a browser, go to " + uri);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackdrop();

        int left = panelLeft();
        int top = panelTop();
        int bottom = top + 190;

        drawPanel(left, top, left + PANEL_WIDTH, bottom);
        drawTitle("Microsoft sign in", top + 14);

        DeviceCode current = code;
        Draw.well(left + 20, top + 38, left + PANEL_WIDTH - 20, top + 68,
                FIELD_RADIUS, Theme.FIELD, Theme.FIELD_BORDER);
        if (current == null) {
            Text.drawCentred("...", width / 2, top + 49, Theme.TEXT_FAINT);
        } else {
            Text.drawCentred(current.getUserCode(), width / 2, top + 49, Theme.MAUVE);
            if (!finished) {
                Text.drawCentred("expires in " + current.getSecondsLeft() + "s",
                        width / 2, top + 76, Theme.TEXT_FAINT);
            }
        }

        drawStatus(bottom + 8);
        if (proxy != null) {
            Text.drawCentred("through " + proxy.describe(), width / 2, bottom + 20,
                    Theme.TEXT_FAINT);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        cancelled.set(true);
    }
}
