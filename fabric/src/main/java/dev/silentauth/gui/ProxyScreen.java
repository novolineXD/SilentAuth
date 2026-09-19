package dev.silentauth.gui;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.LoginService;
import dev.silentauth.net.IpCheck;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.proxy.ProxyParser;
import dev.silentauth.util.Async;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Proxies on one screen. Paste at the top, click a row to use it for everything, click the x to
 * drop it. Every proxy tests itself when the screen opens.
 */
public final class ProxyScreen extends ScreenBase {

    private static final int ADD = 1;
    private static final int BACK = 2;

    private static final int PANEL_WIDTH = 330;
    private static final int ROW_HEIGHT = 34;
    private static final int MAX_ROWS = 6;
    private static final int CHROME_HEIGHT = 76 + 16 + 18 + 24 + 14;

    private TextFieldWidget addressField;
    private ProxyList list;
    private List<ProxyEntry> shown = new ArrayList<ProxyEntry>();

    public ProxyScreen(Screen parent) {
        super(parent, "Proxies");
    }

    @Override
    protected void buildLayout() {
        shown = SilentAuth.proxies().all();

        int left = centreLeft(PANEL_WIDTH);
        int top = panelTop();
        int fieldTop = top + 40;
        int listTop = top + 76;
        int listHeight = listHeight();

        addressField = field(left + 20, fieldTop, PANEL_WIDTH - 110, 16,
                "Paste a proxy  -  ip:port  or  ip:port:user:pass", 4096);
        addDrawableChild(addressField);
        setInitialFocus(addressField);

        addButton(new GlassButton(ADD, left + PANEL_WIDTH - 82, fieldTop - 6, 62, 24, "Add").asPrimary());

        list = new ProxyList(left + 8, listTop, PANEL_WIDTH - 16, listHeight, ROW_HEIGHT);

        addButton(new GlassButton(BACK, left + 20, listTop + listHeight + 34, PANEL_WIDTH - 40, 24, "Back"));

        SilentAuth.tester().testStale(shown);
    }

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

    private void relayout() {
        MinecraftClient.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                clearAndInit();
            }
        });
    }

    /** Fetches the IP this proxy shows to the outside world and compares it with the direct one. */
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
    protected void onButton(int id) {
        if (id == ADD) {
            add();
        } else if (id == BACK) {
            close();
        }
    }

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
        if (rejected == 0) {
            ok(added.size() == 1 ? "Added " + added.get(0).describe() : "Added " + added.size() + " proxies");
        } else {
            ok("Added " + added.size() + ", skipped " + rejected + " that did not parse");
        }
        clearAndInit();
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
            if (addressField.getText().trim().isEmpty()) {
                list.activateSelection();
            } else {
                add();
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
        drawTitle("Proxies", top + 14);

        drawField(addressField);
        Draw.well(left + 8, listTop, left + PANEL_WIDTH - 8, listTop + listHeight,
                FIELD_RADIUS, Theme.FIELD, Theme.FIELD_BORDER);
        list.draw(mouseX, mouseY);

        Text.draw("like  154.9.12.80:8080      154.9.12.80:8080:myuser:mypass",
                left + 20, listTop + listHeight + 6, Theme.TEXT_FAINT);
        drawStatusOr(listTop + listHeight + 20, "Click a proxy to use it    the x on a row removes it");
    }

    private final class ProxyList extends ListWidget {

        ProxyList(int x, int y, int width, int height, int rowHeight) {
            super(x, y, width, height, rowHeight);
        }

        @Override
        int getSize() {
            return shown.size();
        }

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
