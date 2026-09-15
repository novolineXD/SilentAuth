package dev.silentauth.gui;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.Account;
import dev.silentauth.account.LoginService;
import dev.silentauth.proxy.ProxyEntry;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class GuiProxyManager extends GuiScreen {

    private final GuiScreen parent;

    private ListWidget list;
    private List<ProxyEntry> shown = new ArrayList<ProxyEntry>();
    private volatile String status = "";

    public GuiProxyManager(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        refreshList();

        int listHeight = Math.max(44, height - 140);
        list = new ListWidget(width / 2 - 150, 40, 300, listHeight, 22) {
            @Override
            public int getSize() {
                return shown.size();
            }

            @Override
            public void drawRow(int index, int rowX, int rowY, int rowWidth, boolean hovered, boolean isSelected) {
                ProxyEntry entry = shown.get(index);
                boolean isDefault = SilentAuth.proxies().isDefault(entry);
                String name = (isDefault ? "\u00a7a" : "\u00a7f") + entry.displayName();
                fontRendererObj.drawString(name, rowX, rowY, 0xFFFFFF);
                fontRendererObj.drawString("\u00a77" + entry.getType().getLabel()
                        + (entry.hasCredentials() ? " \u00a78| \u00a77auth" : ""), rowX, rowY + 10, 0xAAAAAA);

                String state = entry.statusText();
                int color = entry.getLatencyMs() >= 0 ? 0x55FF55
                        : entry.getLatencyMs() == ProxyEntry.UNTESTED ? 0xAAAAAA : 0xFF5555;
                fontRendererObj.drawString(state, rowX + rowWidth - fontRendererObj.getStringWidth(state) - 6,
                        rowY + 5, color);
            }
        };

        int left = width / 2 - 154;
        int rowOne = height - 92;
        int rowTwo = height - 68;
        int rowThree = height - 44;
        buttonList.add(new GuiButton(1, left, rowOne, 100, 20, "Add"));
        buttonList.add(new GuiButton(2, left + 104, rowOne, 100, 20, "Remove"));
        buttonList.add(new GuiButton(3, left + 208, rowOne, 100, 20, "Test"));
        buttonList.add(new GuiButton(4, left, rowTwo, 100, 20, "Test all"));
        buttonList.add(new GuiButton(5, left + 104, rowTwo, 100, 20, "Set default"));
        buttonList.add(new GuiButton(6, left + 208, rowTwo, 100, 20, "Bind to account"));
        buttonList.add(new GuiButton(7, left, rowThree, 308, 20, "Back"));
    }

    private void refreshList() {
        shown = SilentAuth.proxies().all();
    }

    private ProxyEntry selected() {
        int index = list.getSelectedIndex();
        if (index < 0 || index >= shown.size()) {
            return null;
        }
        return shown.get(index);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        ProxyEntry entry = selected();
        switch (button.id) {
            case 1:
                mc.displayGuiScreen(new GuiAddProxy(this));
                break;
            case 2:
                if (entry == null) {
                    status = "\u00a7cPick a proxy first";
                    break;
                }
                SilentAuth.proxies().remove(entry);
                list.clearSelection();
                refreshList();
                status = "\u00a77Removed " + entry.describe();
                break;
            case 3:
                if (entry == null) {
                    status = "\u00a7cPick a proxy first";
                    break;
                }
                status = "\u00a77Testing " + entry.describe();
                SilentAuth.tester().testAsync(entry, null);
                break;
            case 4:
                status = "\u00a77Testing " + shown.size() + " proxies";
                SilentAuth.tester().testAll(shown, null);
                break;
            case 5:
                if (entry == null) {
                    status = "\u00a7cPick a proxy first";
                    break;
                }
                SilentAuth.proxies().setDefault(entry);
                LoginService.applyCurrentProxy();
                status = "\u00a7a" + entry.describe() + " is now the default";
                break;
            case 6:
                bindToAccount(entry);
                break;
            case 7:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    private void bindToAccount(ProxyEntry entry) {
        Account active = SilentAuth.accounts().getActive();
        if (active == null) {
            status = "\u00a7cLog in to an account first";
            return;
        }
        active.setProxyId(entry == null ? "" : entry.getId());
        SilentAuth.accounts().save();
        LoginService.applyCurrentProxy();
        status = "\u00a7a" + active.getUsername() + " now uses "
                + (entry == null ? "no proxy" : entry.describe());
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        list.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        list.handleScroll();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Proxies", width / 2, 14, 0xFFFFFF);
        list.draw(mouseX, mouseY);
        if (!status.isEmpty()) {
            drawCenteredString(fontRendererObj, status, width / 2, height - 108, 0xFFFFFF);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
