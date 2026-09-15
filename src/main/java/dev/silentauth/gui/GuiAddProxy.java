package dev.silentauth.gui;

import dev.silentauth.SilentAuth;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.proxy.ProxyParser;
import dev.silentauth.proxy.ProxyType;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public final class GuiAddProxy extends GuiScreen {

    private final GuiScreen parent;

    private GuiTextField addressField;
    private GuiTextField labelField;
    private GuiButton typeButton;

    private ProxyType type = ProxyType.SOCKS5;
    private volatile String status = "";

    public GuiAddProxy(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        type = SilentAuth.config().getDefaultProxyType();

        int left = width / 2 - 150;
        addressField = new GuiTextField(0, fontRendererObj, left, 56, 300, 18);
        addressField.setMaxStringLength(256);
        addressField.setFocused(true);
        labelField = new GuiTextField(1, fontRendererObj, left, 96, 300, 18);
        labelField.setMaxStringLength(32);

        typeButton = new GuiButton(1, left, 124, 300, 20, "");
        buttonList.add(typeButton);
        buttonList.add(new GuiButton(2, left, height - 58, 146, 20, "Save"));
        buttonList.add(new GuiButton(3, left + 154, height - 58, 146, 20, "Back"));
        updateLabels();
    }

    private void updateLabels() {
        typeButton.displayString = "Type when no scheme is given: " + type.getLabel();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 1:
                type = type.next();
                updateLabels();
                break;
            case 2:
                save();
                break;
            case 3:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    private void save() {
        try {
            ProxyEntry entry = ProxyParser.parse(addressField.getText(), type);
            entry.setLabel(labelField.getText().trim());
            SilentAuth.proxies().add(entry);
            SilentAuth.tester().testAsync(entry, null);
            mc.displayGuiScreen(parent);
        } catch (IllegalArgumentException e) {
            status = "\u00a7c" + e.getMessage();
        }
    }

    @Override
    public void updateScreen() {
        addressField.updateCursorCounter();
        labelField.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            save();
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            boolean toLabel = addressField.isFocused();
            addressField.setFocused(!toLabel);
            labelField.setFocused(toLabel);
            return;
        }
        addressField.textboxKeyTyped(typedChar, keyCode);
        labelField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        addressField.mouseClicked(mouseX, mouseY, mouseButton);
        labelField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Add a proxy", width / 2, 14, 0xFFFFFF);

        int left = width / 2 - 150;
        fontRendererObj.drawString("\u00a77Proxy", left, 46, 0xAAAAAA);
        addressField.drawTextBox();
        fontRendererObj.drawString("\u00a77Label (optional)", left, 86, 0xAAAAAA);
        labelField.drawTextBox();
        fontRendererObj.drawString("\u00a78host:port  host:port:user:pass  socks5://user:pass@host:port", left, 150,
                0x888888);

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
