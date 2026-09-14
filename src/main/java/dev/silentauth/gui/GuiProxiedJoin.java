package dev.silentauth.gui;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.Account;
import dev.silentauth.account.LoginService;
import dev.silentauth.net.GameProxyRelay;
import dev.silentauth.proxy.ProxyEntry;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.GuiConnecting;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public final class GuiProxiedJoin extends GuiScreen {

    private final GuiScreen parent;

    private GuiTextField addressField;
    private String status = "";

    public GuiProxiedJoin(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        int left = width / 2 - 150;
        addressField = new GuiTextField(0, fontRendererObj, left, 70, 300, 18);
        addressField.setMaxStringLength(128);
        addressField.setFocused(true);

        buttonList.add(new GuiButton(1, left, height - 58, 146, 20, "Connect"));
        buttonList.add(new GuiButton(2, left + 154, height - 58, 146, 20, "Back"));
    }

    private ProxyEntry proxy() {
        Account active = SilentAuth.accounts().getActive();
        return LoginService.resolveProxy(active);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) {
            connect();
        } else if (button.id == 2) {
            mc.displayGuiScreen(parent);
        }
    }

    private void connect() {
        String raw = addressField.getText().trim();
        if (raw.isEmpty()) {
            status = "§cEnter a server address";
            return;
        }
        ProxyEntry entry = proxy();
        if (entry == null) {
            status = "§cNo proxy is set, add one or pick a default";
            return;
        }

        String host = raw;
        int port = 25565;
        int colon = raw.lastIndexOf(':');
        if (colon > 0 && colon < raw.length() - 1) {
            try {
                port = Integer.parseInt(raw.substring(colon + 1).trim());
                host = raw.substring(0, colon).trim();
            } catch (NumberFormatException e) {
                status = "§cThat port is not a number";
                return;
            }
        }

        try {
            GameProxyRelay relay = GameProxyRelay.start(entry, host, port);
            mc.displayGuiScreen(new GuiConnecting(parent, mc, "127.0.0.1", relay.getLocalPort()));
        } catch (IOException e) {
            status = "§cCould not open the relay: " + e.getMessage();
        }
    }

    @Override
    public void updateScreen() {
        addressField.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            connect();
            return;
        }
        addressField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        addressField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Join through a proxy", width / 2, 20, 0xFFFFFF);

        int left = width / 2 - 150;
        fontRendererObj.drawString("§7Server address", left, 60, 0xAAAAAA);
        addressField.drawTextBox();

        ProxyEntry entry = proxy();
        String line = entry == null ? "§cno proxy selected" : "§7through §f" + entry.describe();
        drawCenteredString(fontRendererObj, line, width / 2, 100, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "§8The game connects to a local relay that forwards the traffic",
                width / 2, 116, 0x888888);

        if (!status.isEmpty()) {
            drawCenteredString(fontRendererObj, status, width / 2, height - 80, 0xFFFFFF);
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
