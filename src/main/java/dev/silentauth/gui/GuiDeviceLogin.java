package dev.silentauth.gui;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.Account;
import dev.silentauth.auth.AuthException;
import dev.silentauth.auth.DeviceCode;
import dev.silentauth.auth.MicrosoftAuth;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Async;
import dev.silentauth.util.Log;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GuiDeviceLogin extends GuiScreen {

    private final GuiScreen parent;
    private final ProxyEntry proxy;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    private volatile DeviceCode code;
    private volatile String status = "Asking Microsoft for a code";
    private volatile boolean finished;
    private boolean started;

    public GuiDeviceLogin(GuiScreen parent, ProxyEntry proxy) {
        this.parent = parent;
        this.proxy = proxy;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int left = width / 2 - 100;
        buttonList.add(new GuiButton(1, left, height - 90, 200, 20, "Copy code"));
        buttonList.add(new GuiButton(2, left, height - 66, 200, 20, "Open microsoft.com/link"));
        buttonList.add(new GuiButton(3, left, height - 42, 200, 20, "Cancel"));

        if (!started) {
            started = true;
            begin();
        }
    }

    private void begin() {
        Async.run(new Runnable() {
            @Override
            public void run() {
                try {
                    DeviceCode requested = MicrosoftAuth.requestDeviceCode(proxy);
                    code = requested;
                    status = "Type the code at " + requested.getVerificationUri();
                    Account account = MicrosoftAuth.completeDeviceLogin(requested, proxy, cancelled,
                            new MicrosoftAuth.StatusListener() {
                                @Override
                                public void onStatus(String message) {
                                    if (!message.startsWith("Waiting")) {
                                        status = message;
                                    }
                                }
                            });
                    if (proxy != null) {
                        account.setProxyId(proxy.getId());
                    }
                    SilentAuth.accounts().add(account);
                    finished = true;
                    status = "§aAdded " + account.getUsername();
                } catch (AuthException e) {
                    finished = true;
                    status = "§c" + e.getMessage();
                    Log.warn("Microsoft sign in failed: " + e.getMessage());
                }
            }
        });
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 1:
                if (code != null) {
                    setClipboardString(code.getUserCode());
                    status = "§aCode copied";
                }
                break;
            case 2:
                openLink();
                break;
            case 3:
                cancelled.set(true);
                mc.displayGuiScreen(parent);
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
            status = "§cCould not open a browser, go to " + uri;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancelled.set(true);
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Microsoft sign in", width / 2, 24, 0xFFFFFF);

        DeviceCode current = code;
        if (current != null) {
            drawCenteredString(fontRendererObj, "§f" + current.getUserCode(), width / 2, 60, 0xFFFFFF);
            if (!finished) {
                drawCenteredString(fontRendererObj, "§8expires in " + current.getSecondsLeft() + "s", width / 2,
                        76, 0x888888);
            }
        }
        drawCenteredString(fontRendererObj, status, width / 2, 100, 0xFFFFFF);
        if (proxy != null) {
            drawCenteredString(fontRendererObj, "§8through " + proxy.describe(), width / 2, 116, 0x888888);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        cancelled.set(true);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
