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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;

import java.util.concurrent.atomic.AtomicBoolean;

/** Shows the code to type at microsoft.com/link, then plays as that account. */
public final class DeviceLoginScreen extends ScreenBase {

    private static final int COPY = 1;
    private static final int OPEN = 2;
    private static final int CLOSE = 3;
    private static final int PANEL_WIDTH = 330;

    private final ProxyEntry proxy;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    private volatile DeviceCode code;
    private volatile boolean finished;
    private boolean started;
    private GlassButton closeButton;

    public DeviceLoginScreen(Screen parent, ProxyEntry proxy) {
        super(parent, "Microsoft sign in");
        this.proxy = proxy;
    }

    @Override
    protected void buildLayout() {
        int left = centreLeft(PANEL_WIDTH);
        int top = panelTop();

        addButton(new GlassButton(OPEN, left + 20, top + 98, PANEL_WIDTH - 40, 24,
                "Open microsoft.com/link").asPrimary());
        addButton(new GlassButton(COPY, left + 20, top + 128, PANEL_WIDTH - 40, 24, "Copy the code"));
        closeButton = addButton(new GlassButton(CLOSE, left + 20, top + 158, PANEL_WIDTH - 40, 24,
                finished ? "Done" : "Cancel"));

        // buildLayout runs again on every resize; the sign in must only start once.
        if (!started) {
            started = true;
            info("Asking Microsoft for a code");
            begin();
        }
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
                    MinecraftClient.getInstance().execute(new Runnable() {
                        @Override
                        public void run() {
                            close();
                        }
                    });
                } else {
                    markDone();
                }
            }
        });
    }

    private void markDone() {
        if (closeButton != null) {
            closeButton.label = "Done";
        }
    }

    @Override
    protected void onButton(int id) {
        switch (id) {
            case COPY:
                DeviceCode current = code;
                if (current == null) {
                    error("There is no code yet");
                    break;
                }
                MinecraftClient.getInstance().keyboard.setClipboard(current.getUserCode());
                ok("Code copied");
                break;
            case OPEN:
                openLink();
                break;
            case CLOSE:
                close();
                break;
            default:
                break;
        }
    }

    private void openLink() {
        String uri = code == null ? "https://microsoft.com/link" : code.getVerificationUri();
        try {
            Util.getOperatingSystem().open(uri);
        } catch (Throwable t) {
            error("Could not open a browser, go to " + uri);
        }
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY) {
        int left = centreLeft(PANEL_WIDTH);
        int top = panelTop();
        int bottom = top + 196;

        drawPanel(left, top, left + PANEL_WIDTH, bottom);
        drawTitle("Microsoft sign in", top + 14);

        DeviceCode current = code;
        Draw.well(left + 20, top + 38, left + PANEL_WIDTH - 20, top + 70,
                FIELD_RADIUS, Theme.FIELD, Theme.FIELD_BORDER);
        if (current == null) {
            Text.drawCentred("...", width / 2, top + 50, Theme.TEXT_FAINT);
        } else {
            Text.drawCentred(current.getUserCode(), width / 2, top + 50, Theme.MAUVE);
            if (!finished) {
                Text.drawCentred("expires in " + current.getSecondsLeft() + "s",
                        width / 2, top + 78, Theme.TEXT_FAINT);
            }
        }

        drawStatus(bottom + 8);
        if (proxy != null) {
            Text.drawCentred("through " + proxy.describe(), width / 2, bottom + 22, Theme.TEXT_FAINT);
        }
    }

    @Override
    public void close() {
        cancelled.set(true);
        super.close();
    }
}
