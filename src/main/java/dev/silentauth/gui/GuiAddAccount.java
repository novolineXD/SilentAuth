package dev.silentauth.gui;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.Account;
import dev.silentauth.account.AccountType;
import dev.silentauth.account.TokenParser;
import dev.silentauth.auth.AuthException;
import dev.silentauth.auth.SessionTokenAuth;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Async;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;

public final class GuiAddAccount extends GuiScreen {

    private final GuiScreen parent;

    private AccountType mode = AccountType.SESSION;
    private int proxyIndex = -1;
    private List<ProxyEntry> proxies;

    private GuiTextField tokenField;
    private GuiTextField nameField;
    private GuiTextField uuidField;

    private GuiButton modeButton;
    private GuiButton proxyButton;
    private GuiButton addButton;

    private volatile String status = "";
    private volatile boolean busy;

    public GuiAddAccount(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        proxies = SilentAuth.proxies().all();

        int left = width / 2 - 150;
        tokenField = new GuiTextField(0, fontRendererObj, left, 60, 300, 18);
        tokenField.setMaxStringLength(4096);
        nameField = new GuiTextField(1, fontRendererObj, left, 100, 146, 18);
        nameField.setMaxStringLength(16);
        uuidField = new GuiTextField(2, fontRendererObj, left + 154, 100, 146, 18);
        uuidField.setMaxStringLength(36);

        modeButton = new GuiButton(1, left, 30, 300, 20, "");
        proxyButton = new GuiButton(2, left, 130, 300, 20, "");
        addButton = new GuiButton(3, left, height - 58, 146, 20, "Add");
        buttonList.add(modeButton);
        buttonList.add(proxyButton);
        buttonList.add(addButton);
        buttonList.add(new GuiButton(4, left + 154, height - 58, 146, 20, "Back"));

        updateLabels();
    }

    private void updateLabels() {
        modeButton.displayString = "Type: " + mode.getLabel();
        proxyButton.displayString = "Proxy: " + proxyLabel();
        addButton.displayString = mode == AccountType.MICROSOFT ? "Start sign in" : "Add";
        addButton.enabled = !busy;
    }

    private String proxyLabel() {
        if (proxyIndex < 0) {
            ProxyEntry fallback = SilentAuth.proxies().getDefault();
            return fallback == null ? "none" : "default (" + fallback.describe() + ")";
        }
        if (proxyIndex >= proxies.size()) {
            return "none";
        }
        return proxies.get(proxyIndex).describe();
    }

    private ProxyEntry chosenProxy() {
        if (proxyIndex < 0) {
            return SilentAuth.proxies().getDefault();
        }
        if (proxyIndex >= proxies.size()) {
            return null;
        }
        return proxies.get(proxyIndex);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 1:
                mode = nextMode();
                updateLabels();
                break;
            case 2:
                proxyIndex++;
                if (proxyIndex > proxies.size()) {
                    proxyIndex = -1;
                }
                updateLabels();
                break;
            case 3:
                submit();
                break;
            case 4:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    private AccountType nextMode() {
        if (mode == AccountType.SESSION) {
            return AccountType.MICROSOFT;
        }
        if (mode == AccountType.MICROSOFT) {
            return AccountType.OFFLINE;
        }
        return AccountType.SESSION;
    }

    private void submit() {
        if (busy) {
            return;
        }
        if (mode == AccountType.MICROSOFT) {
            mc.displayGuiScreen(new GuiDeviceLogin(parent, chosenProxy()));
            return;
        }
        if (mode == AccountType.OFFLINE) {
            String name = nameField.getText().trim();
            if (name.isEmpty() || name.length() > 16) {
                status = "\u00a7cEnter a username of 1 to 16 characters";
                return;
            }
            Account account = Account.offline(name);
            SilentAuth.accounts().add(account);
            status = "\u00a7aAdded " + name;
            return;
        }

        String pasted = tokenField.getText().trim();
        if (pasted.isEmpty()) {
            status = "\u00a7cPaste a session token first";
            return;
        }

        final TokenParser.Parsed parsed;
        try {
            parsed = TokenParser.parse(pasted);
        } catch (IllegalArgumentException e) {
            status = "\u00a7c" + e.getMessage();
            return;
        }

        String typedName = nameField.getText().trim();
        String typedUuid = uuidField.getText().trim();
        final String name = typedName.isEmpty() ? parsed.username : typedName;
        final String uuid = typedUuid.isEmpty() ? parsed.uuid : typedUuid;
        final ProxyEntry proxy = chosenProxy();

        busy = true;
        updateLabels();
        status = "\u00a77Checking the token";
        Async.run(new Runnable() {
            @Override
            public void run() {
                try {
                    Account account;
                    if (!name.isEmpty() && !uuid.isEmpty()) {
                        account = SessionTokenAuth.withoutLookup(parsed.token, name, uuid);
                    } else {
                        account = SessionTokenAuth.login(parsed.token, proxy);
                    }
                    if (proxy != null) {
                        account.setProxyId(proxy.getId());
                    }
                    SilentAuth.accounts().add(account);
                    status = "\u00a7aAdded " + account.getUsername();
                } catch (AuthException e) {
                    status = "\u00a7c" + e.getMessage();
                } finally {
                    busy = false;
                }
            }
        });
    }

    @Override
    public void updateScreen() {
        tokenField.updateCursorCounter();
        nameField.updateCursorCounter();
        uuidField.updateCursorCounter();
        addButton.enabled = !busy;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            boolean toName = tokenField.isFocused();
            tokenField.setFocused(!toName && !nameField.isFocused());
            nameField.setFocused(toName);
            uuidField.setFocused(false);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            submit();
            return;
        }
        tokenField.textboxKeyTyped(typedChar, keyCode);
        nameField.textboxKeyTyped(typedChar, keyCode);
        uuidField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        tokenField.mouseClicked(mouseX, mouseY, mouseButton);
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
        uuidField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Add an account", width / 2, 12, 0xFFFFFF);

        int left = width / 2 - 150;
        if (mode == AccountType.SESSION) {
            fontRendererObj.drawString("\u00a77Session token", left, 50, 0xAAAAAA);
            tokenField.drawTextBox();
            fontRendererObj.drawString("\u00a77Username (optional)", left, 90, 0xAAAAAA);
            fontRendererObj.drawString("\u00a77UUID (optional)", left + 154, 90, 0xAAAAAA);
            nameField.drawTextBox();
            uuidField.drawTextBox();
            fontRendererObj.drawString("\u00a78A pasted name, uuid or token: prefix is picked up on its own",
                    left, 122, 0x888888);
            fontRendererObj.drawString("\u00a78Fill both fields to skip the profile lookup", left, 134, 0x888888);
        } else if (mode == AccountType.OFFLINE) {
            fontRendererObj.drawString("\u00a77Username", left, 90, 0xAAAAAA);
            nameField.drawTextBox();
            fontRendererObj.drawString("\u00a78Offline accounts only work on servers in offline mode", left, 122,
                    0x888888);
        } else {
            fontRendererObj.drawString("\u00a77A code is shown on the next screen, type it at", left, 70, 0xAAAAAA);
            fontRendererObj.drawString("\u00a77microsoft.com/link to finish the sign in", left, 82, 0xAAAAAA);
        }

        if (!status.isEmpty()) {
            drawCenteredString(fontRendererObj, busy ? status + " ..." : status, width / 2, height - 80, 0xFFFFFF);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
