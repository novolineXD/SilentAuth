package dev.silentauth.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;

import java.util.ArrayList;
import java.util.List;

/** Shared chrome for the mod's screens: a tinted backdrop, one glass panel, one status line. */
abstract class ScreenBase extends Screen {

    static final int PANEL_RADIUS = 12;
    static final int FIELD_RADIUS = 7;

    protected final Screen parent;
    protected final List<GlassButton> buttons = new ArrayList<GlassButton>();

    /** Written from worker threads, read while drawing. */
    private volatile String message = "";
    private volatile int messageColour = Theme.TEXT_DIM;
    protected volatile boolean busy;

    ScreenBase(Screen parent, String title) {
        super(net.minecraft.text.Text.literal(title));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buttons.clear();
        buildLayout();
    }

    /** Builds the buttons, fields and list for the current size. Called on open and on resize. */
    protected abstract void buildLayout();

    /** Draws the panel and its contents. The backdrop, buttons and children are drawn around it. */
    protected abstract void renderContent(DrawContext context, int mouseX, int mouseY);

    protected final GlassButton addButton(GlassButton button) {
        buttons.add(button);
        return button;
    }

    /** The click handler, dispatched by button id. */
    protected abstract void onButton(int id);

    // ------------------------------------------------------------------ status

    protected final void ok(String text) {
        message = text;
        messageColour = Theme.OK;
    }

    protected final void info(String text) {
        message = text;
        messageColour = Theme.TEXT_DIM;
    }

    protected final void error(String text) {
        message = text == null || text.isEmpty() ? "That did not work" : text;
        messageColour = Theme.DANGER;
    }

    protected final void result(boolean success, String text) {
        if (success) {
            ok(text);
        } else {
            error(text);
        }
    }

    protected final void drawStatus(int y) {
        String current = message;
        if (current.isEmpty()) {
            return;
        }
        Text.drawCentred(busy ? current + "..." : current, width / 2, y, messageColour);
    }

    protected final void drawStatusOr(int y, String hint) {
        if (message.isEmpty()) {
            Text.drawCentred(hint, width / 2, y, Theme.TEXT_FAINT);
        } else {
            drawStatus(y);
        }
    }

    // ------------------------------------------------------------------ chrome

    protected final void drawPanel(int left, int top, int right, int bottom) {
        Draw.glassPanel(left, top, right, bottom, PANEL_RADIUS);
    }

    protected final void drawTitle(String text, int y) {
        Text.drawCentred(text, width / 2, y, Theme.MAUVE);
    }

    /** A rounded well behind a text field, so the vanilla field reads as part of the glass. */
    protected final void drawField(TextFieldWidget field) {
        int left = field.getX() - 6;
        int top = field.getY() - 5;
        int right = field.getX() + field.getWidth() + 6;
        int bottom = field.getY() + field.getHeight() + 5;
        Draw.well(left, top, right, bottom, FIELD_RADIUS, Theme.FIELD,
                field.isFocused() ? Theme.FIELD_BORDER_FOCUS : Theme.FIELD_BORDER);
    }

    protected final TextFieldWidget field(int x, int y, int w, int h, String placeholder, int maxLength) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, w, h, net.minecraft.text.Text.empty());
        field.setDrawsBackground(false);
        field.setMaxLength(maxLength);
        field.setEditableColor(Theme.TEXT);
        if (placeholder != null) {
            field.setPlaceholder(net.minecraft.text.Text.literal(placeholder));
        }
        return field;
    }

    // ------------------------------------------------------------------ input plumbing

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        Draw.begin(context);
        context.fill(0, 0, width, height, Theme.BACKDROP);
        renderContent(context, mouseX, mouseY);
        for (GlassButton button : buttons) {
            button.draw(mouseX, mouseY);
        }
        // Draws the text-field children (and anything else added) on top of their wells.
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (click.button() == 0) {
            for (GlassButton button : buttons) {
                if (button.isOver(click.x(), click.y())) {
                    onButton(button.id);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.isEscape()) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    protected final int centreLeft(int panelWidth) {
        return width / 2 - panelWidth / 2;
    }
}
