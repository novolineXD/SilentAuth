package dev.silentauth.gui;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.Account;
import dev.silentauth.account.AccountImporter;
import dev.silentauth.account.LoginService;
import dev.silentauth.account.SessionSwapper;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.util.Async;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class GuiAccountManager extends GuiScreen {

    private final GuiScreen parent;

    private GuiTextField searchField;
    private ListWidget list;
    private List<Account> shown = new ArrayList<Account>();
    private String lastQuery = "";
    private volatile String status = "";
    private volatile boolean busy;

    public GuiAccountManager(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        searchField = new GuiTextField(0, fontRendererObj, width / 2 - 150, 24, 300, 16);
        searchField.setMaxStringLength(48);
        searchField.setText(lastQuery);

        final int listTop = 48;
        final int listHeight = Math.max(44, height - 156);
        list = new ListWidget(width / 2 - 150, listTop, 300, listHeight, 22) {
            @Override
            public int getSize() {
                return shown.size();
            }

            @Override
            public void drawRow(int index, int rowX, int rowY, int rowWidth, boolean hovered, boolean isSelected) {
                Account account = shown.get(index);
                boolean active = SilentAuth.accounts().isActive(account);
                String name = (active ? "§a" : "§f") + account.getUsername();
                fontRendererObj.drawString(name, rowX, rowY, 0xFFFFFF);

                String detail = "§7" + account.getType().getLabel();
                ProxyEntry proxy = LoginService.resolveProxy(account);
                if (proxy != null) {
                    detail += " §8| §7" + proxy.describe();
                }
                fontRendererObj.drawString(detail, rowX, rowY + 10, 0xAAAAAA);

                String state = account.getStatus();
                if (!state.isEmpty()) {
                    String trimmed = fontRendererObj.trimStringToWidth(state, 110);
                    int color = state.equals("ok") ? 0x55FF55 : 0xFFAA55;
                    fontRendererObj.drawString(trimmed,
                            rowX + rowWidth - fontRendererObj.getStringWidth(trimmed) - 6, rowY + 5, color);
                }
            }
        };

        int left = width / 2 - 154;
        int rowOne = height - 86;
        int rowTwo = height - 62;
        int rowThree = height - 38;
        buttonList.add(new GuiButton(1, left, rowOne, 100, 20, "Log in"));
        buttonList.add(new GuiButton(2, left + 104, rowOne, 100, 20, "Add account"));
        buttonList.add(new GuiButton(3, left + 208, rowOne, 100, 20, "Remove"));
        buttonList.add(new GuiButton(4, left, rowTwo, 100, 20, "Check token"));
        buttonList.add(new GuiButton(5, left + 104, rowTwo, 100, 20, "Import file"));
        buttonList.add(new GuiButton(6, left + 208, rowTwo, 100, 20, "Proxies"));
        buttonList.add(new GuiButton(7, left, rowThree, 152, 20, "Set proxy"));
        buttonList.add(new GuiButton(8, left + 156, rowThree, 152, 20, "Done"));

        refreshList();
    }

    private void refreshList() {
        shown = SilentAuth.accounts().search(searchField == null ? "" : searchField.getText());
    }

    private Account selected() {
        int index = list.getSelectedIndex();
        if (index < 0 || index >= shown.size()) {
            return null;
        }
        return shown.get(index);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 1:
                loginSelected();
                break;
            case 2:
                mc.displayGuiScreen(new GuiAddAccount(this));
                break;
            case 3:
                removeSelected();
                break;
            case 4:
                checkSelected();
                break;
            case 5:
                importFile();
                break;
            case 6:
                mc.displayGuiScreen(new GuiProxyManager(this));
                break;
            case 7:
                cycleProxy();
                break;
            case 8:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    private void loginSelected() {
        Account account = selected();
        if (account == null) {
            status = "§cPick an account first";
            return;
        }
        busy = true;
        status = "§7Switching to " + account.getUsername();
        LoginService.loginAsync(account, new LoginService.Callback() {
            @Override
            public void onResult(boolean success, String message) {
                busy = false;
                status = (success ? "§a" : "§c") + message;
            }
        });
    }

    private void checkSelected() {
        Account account = selected();
        if (account == null) {
            status = "§cPick an account first";
            return;
        }
        busy = true;
        status = "§7Checking " + account.getUsername();
        LoginService.validateAsync(account, new LoginService.Callback() {
            @Override
            public void onResult(boolean success, String message) {
                busy = false;
                status = (success ? "§a" : "§c") + message;
            }
        });
    }

    private void cycleProxy() {
        Account account = selected();
        if (account == null) {
            status = "§cPick an account first";
            return;
        }
        List<ProxyEntry> available = SilentAuth.proxies().all();
        if (available.isEmpty()) {
            status = "§cNo proxies stored yet";
            return;
        }

        int index = -1;
        for (int i = 0; i < available.size(); i++) {
            if (available.get(i).getId().equals(account.getProxyId())) {
                index = i;
                break;
            }
        }
        index++;

        if (index >= available.size()) {
            account.setProxyId("");
            ProxyEntry fallback = SilentAuth.proxies().getDefault();
            status = "§7" + account.getUsername() + " follows the default ("
                    + (fallback == null ? "none" : fallback.describe()) + ")";
        } else {
            ProxyEntry chosen = available.get(index);
            account.setProxyId(chosen.getId());
            status = "§a" + account.getUsername() + " uses " + chosen.describe();
        }
        SilentAuth.accounts().save();
    }

    private void importFile() {
        if (busy) {
            return;
        }
        final File file = new File(SilentAuth.get().getDirectory(), "accounts.txt");
        if (!file.isFile()) {
            status = "§cPut one token per line in " + file.getName();
            return;
        }
        busy = true;
        status = "§7Reading " + file.getName();
        Async.run(new Runnable() {
            @Override
            public void run() {
                try {
                    List<String> lines = AccountImporter.readLines(file);
                    if (lines.isEmpty()) {
                        status = "§c" + file.getName() + " is empty";
                        return;
                    }
                    AccountImporter.Result result = AccountImporter.importAll(lines,
                            LoginService.resolveAuthProxy(null), new AccountImporter.Progress() {
                                @Override
                                public void onProgress(int done, int total, String line) {
                                    status = "§7Checking token " + (done + 1) + " of " + total;
                                }
                            });
                    refreshList();
                    status = "§aImported " + result.added
                            + (result.failed > 0 ? " §c(" + result.failed + " skipped)" : "");
                } catch (IOException e) {
                    status = "§cCould not read " + file.getName();
                } finally {
                    busy = false;
                }
            }
        });
    }

    private void removeSelected() {
        Account account = selected();
        if (account == null) {
            status = "§cPick an account first";
            return;
        }
        SilentAuth.accounts().remove(account);
        list.clearSelection();
        refreshList();
        status = "§7Removed " + account.getUsername();
    }

    @Override
    public void updateScreen() {
        searchField.updateCursorCounter();
        if (!searchField.getText().equals(lastQuery)) {
            lastQuery = searchField.getText();
            list.clearSelection();
            refreshList();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN && searchField.isFocused()) {
            return;
        }
        searchField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.mouseClicked(mouseX, mouseY, mouseButton);
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
        drawCenteredString(fontRendererObj, "SilentAuth", width / 2, 10, 0xFFFFFF);
        searchField.drawTextBox();
        if (searchField.getText().isEmpty() && !searchField.isFocused()) {
            fontRendererObj.drawString("§8Search", width / 2 - 145, 28, 0x888888);
        }
        list.draw(mouseX, mouseY);

        String footer = "§7Session: §f" + SessionSwapper.currentUsername();
        ProxyEntry activeProxy = LoginService.resolveProxy(SilentAuth.accounts().getActive());
        if (activeProxy != null) {
            footer += " §8| §7" + activeProxy.describe();
        }
        drawCenteredString(fontRendererObj, footer, width / 2, height - 106, 0xFFFFFF);
        if (!status.isEmpty()) {
            drawCenteredString(fontRendererObj, busy ? status + " ..." : status, width / 2, height - 96, 0xFFFFFF);
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
