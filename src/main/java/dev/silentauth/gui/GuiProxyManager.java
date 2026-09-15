package dev.silentauth.gui;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.LoginService;
import dev.silentauth.net.IpCheck;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.proxy.ProxyParser;
import dev.silentauth.util.Async;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Proxies on one screen. Paste at the top, click a row to use it for everything, click the x
 * to drop it. Every proxy tests itself when the screen opens.
 */
public final class GuiProxyManager extends SilentAuthScreen {

    private static final int ADD = 1;
    private static final int BACK = 2;

    private static final int PANEL_WIDTH = 330;
    private static final int ROW_HEIGHT = 28;
    private static final int MAX_ROWS = 6;
    /** Title, field, format hint, status band, one button row and the bottom padding. */
    private static final int CHROME_HEIGHT = 76 + 14 + 16 + 22 + 14;

    private GuiTextField addressField;
    private ListWidget list;
    private List<ProxyEntry> shown = new ArrayList<ProxyEntry>();

    public GuiProxyManager(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        refreshList();

        int left = panelLeft();
        int top = panelTop();
        int fieldTop = top + 40;
        int listTop = top + 76;

        addressField = new GuiTextField(0, fontRendererObj, left + 20, fieldTop, PANEL_WIDTH - 110, 12);
        addressField.setMaxStringLength(4096);
        addressField.setEnableBackgroundDrawing(false);
        addressField.setTextColor(Theme.TEXT);
        addressField.setFocused(true);

        buttonList.add(new GlassButton(ADD, left + PANEL_WIDTH - 82, fieldTop - 6, 62, 24, "Add").asPrimary());

        list = new ProxyList(left + 8, listTop, PANEL_WIDTH - 16, listHeight(), ROW_HEIGHT);

        buttonList.add(new GlassButton(BACK, left + 20, listTop + listHeight() + 34, PANEL_WIDTH - 40, 22, "Back"));

        SilentAuth.tester().testStale(shown);
    }

    private int panelLeft() {
        return width / 2 - PANEL_WIDTH / 2;
    }

    /** How many rows fit, so the panel never runs off a short screen at a large gui scale. */
    private int listHeight() {
        int room = (height - 20 - CHROME_HEIGHT) / ROW_HEIGHT;
        int rows = Math.min(Math.min(MAX_ROWS, Math.max(1, room)), Math.max(1, shown.size()));
        return rows * ROW_HEIGHT;
    }

    private int panelHeight() {
        return CHROME_HEIGHT + listHeight();
    }

    private int panelTop() {
        return Math.max(6, (height - panelHeight()) / 2);
    }

    /** Rebuilds the layout after the number of rows changed. */
    private void relayout() {
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                initGui();
            }
        });
    }

    private void refreshList() {
        shown = SilentAuth.proxies().all();
    }

    /**
     * Fetches the IP this proxy actually shows to the outside world and compares it with the
     * direct one, so it is obvious right here - before joining anything - whether the proxy is
     * really hiding the player or not.
     */
    private void checkExitIp(final ProxyEntry entry) {
        Async.run(new Runnable() {
            @Override
            public void run() {
                String through;
                try {
                    through = IpCheck.exitIp(entry);
                } catch (IOException e) {
                    error(entry.describe() + " did not answer, it is dead");
                    return;
                }
                String direct;
                try {
                    direct = IpCheck.exitIp(null);
                } catch (IOException e) {
                    direct = "";
                }
                if (!direct.isEmpty() && through.equals(direct)) {
                    error("This proxy shows your real IP (" + through + "). It will not hide you");
                } else {
                    ok("In use - servers will see " + through);
                }
            }
        });
    }

    // ------------------------------------------------------------------ actions

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ADD) {
            add();
        } else if (button.id == BACK) {
            back();
        }
    }

    /** Takes one proxy or a whole pasted list, separated by spaces or commas. */
    private void add() {
        String text = addressField.getText().trim();
        if (text.isEmpty()) {
            error("Paste a proxy first");
            return;
        }

        List<ProxyEntry> added = new ArrayList<ProxyEntry>();
        int rejected = 0;
        for (String piece : text.split("[,\\s]+")) {
            if (piece.isEmpty()) {
                continue;
            }
            try {
                added.add(SilentAuth.proxies()
                        .add(ProxyParser.parse(piece, SilentAuth.config().getDefaultProxyType())));
            } catch (IllegalArgumentException e) {
                rejected++;
            }
        }

        if (added.isEmpty()) {
            error(rejected == 1 ? "That does not look like a proxy" : "None of those looked like proxies");
            return;
        }
        for (ProxyEntry entry : added) {
            SilentAuth.tester().testAsync(entry);
        }
        addressField.setText("");
        // The list grew, so the panel has to be laid out again.
        initGui();
        if (rejected == 0) {
            ok(added.size() == 1 ? "Added " + added.get(0).describe() : "Added " + added.size() + " proxies");
        } else {
            ok("Added " + added.size() + ", skipped " + rejected + " that did not parse");
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public void updateScreen() {
        addressField.updateCursorCounter();
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
            if (addressField.getText().trim().isEmpty()) {
                list.activateSelection();
            } else {
                add();
            }
            return;
        }
        addressField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        addressField.mouseClicked(mouseX, mouseY, mouseButton);
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
        drawTitle("Proxies", top + 14);

        drawField(addressField, "Paste a proxy, or several at once");
        Draw.well(left + 8, listTop, left + PANEL_WIDTH - 8, listTop + listHeight,
                FIELD_RADIUS, Theme.FIELD, Theme.FIELD_BORDER);
        list.draw(mouseX, mouseY);

        fontRendererObj.drawString("host:port    host:port:user:pass    user:pass@host:port",
                left + 20, listTop + listHeight + 6, Theme.TEXT_FAINT);
        drawStatusOr(listTop + listHeight + 20, "Click a proxy to use it    the x on a row removes it");
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private final class ProxyList extends ListWidget {

        ProxyList(int x, int y, int width, int height, int rowHeight) {
            super(x, y, width, height, rowHeight);
        }

        @Override
        int getSize() {
            return shown.size();
        }

        /** Clicking the proxy in use turns it off again, so one control covers both ways. */
        @Override
        void onActivated(int index) {
            if (index < 0 || index >= shown.size()) {
                return;
            }
            ProxyEntry entry = shown.get(index);
            if (SilentAuth.proxies().isDefault(entry)) {
                SilentAuth.proxies().setDefault(null);
                info("Going out directly again");
            } else {
                SilentAuth.proxies().setDefault(entry);
                info("Checking what IP this proxy shows");
                checkExitIp(entry);
            }
            LoginService.applyCurrentProxy();
        }

        @Override
        boolean onRemove(int index) {
            if (index < 0 || index >= shown.size()) {
                return false;
            }
            ProxyEntry entry = shown.get(index);
            SilentAuth.proxies().remove(entry);
            clearSelection();
            LoginService.applyCurrentProxy();
            info("Removed " + entry.describe());
            relayout();
            return true;
        }

        @Override
        void drawRow(int index, int rowX, int rowY, int rowWidth, boolean hovered) {
            if (index >= shown.size()) {
                return;
            }
            ProxyEntry entry = shown.get(index);
            boolean inUse = SilentAuth.proxies().isDefault(entry);
            boolean dead = entry.getLatencyMs() == ProxyEntry.UNREACHABLE;
            int status = inUse ? Theme.OK : dead ? Theme.DANGER : Theme.IDLE;

            StringBuilder detail = new StringBuilder(entry.getType().getLabel());
            if (entry.hasCredentials()) {
                detail.append("   auth");
            }
            if (entry.isReachable()) {
                detail.append("   ").append(entry.getLatencyMs()).append(" ms");
            }
            paintRow(rowX, rowY, rowWidth, status, entry.displayName(), detail.toString());
        }

        @Override
        String emptyText() {
            return "Paste a proxy above to add one";
        }
    }
}
