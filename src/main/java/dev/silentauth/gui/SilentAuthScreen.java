package dev.silentauth.gui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Shared chrome for the mod's screens: a tinted backdrop, one glass panel, one status line. */
abstract class SilentAuthScreen extends GuiScreen {

    static final float PANEL_RADIUS = 12.0F;
    static final float FIELD_RADIUS = 7.0F;

    protected final GuiScreen parent;

    /** Written from worker threads, read while drawing. */
    private volatile String message = "";
    private volatile int messageColour = Theme.TEXT_DIM;
    protected volatile boolean busy;

    SilentAuthScreen(GuiScreen parent) {
        this.parent = parent;
    }

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
        drawCenteredString(fontRendererObj, busy ? current + "..." : current, width / 2, y, messageColour);
    }

    /** Draws the status where there is one, otherwise a faint standing hint in its place. */
    protected final void drawStatusOr(int y, String hint) {
        if (message.isEmpty()) {
            drawCenteredString(fontRendererObj, hint, width / 2, y, Theme.TEXT_FAINT);
        } else {
            drawStatus(y);
        }
    }

    // ------------------------------------------------------------------ chrome

    protected final void drawBackdrop() {
        Draw.setScale(new ScaledResolution(mc).getScaleFactor());
        drawRect(0, 0, width, height, Theme.BACKDROP);
    }

    protected final void drawPanel(int left, int top, int right, int bottom) {
        Draw.glassPanel(left, top, right, bottom, PANEL_RADIUS);
    }

    /**
     * Draws a text field as a rounded well, plus placeholder text while it is empty so the
     * field says what it wants without needing a separate label above it.
     */
    protected final void drawField(GuiTextField field, String placeholder) {
        int left = field.xPosition - 6;
        int top = field.yPosition - 5;
        int right = left + field.width + 12;
        int bottom = top + field.height + 10;

        Draw.well(left, top, right, bottom, FIELD_RADIUS, Theme.FIELD,
                field.isFocused() ? Theme.FIELD_BORDER_FOCUS : Theme.FIELD_BORDER);

        field.drawTextBox();
        if (field.getText().isEmpty() && placeholder != null) {
            fontRendererObj.drawString(placeholder, field.xPosition, field.yPosition, Theme.TEXT_FAINT);
        }
    }

    protected final void drawTitle(String text, int y) {
        drawCenteredString(fontRendererObj, text, width / 2, y, Theme.MAUVE);
    }

    protected final void back() {
        mc.displayGuiScreen(parent);
    }

    /**
     * Goes back if escape was pressed. Screens with text fields call this before handing the
     * key on, so escape closes the screen rather than reaching a field.
     *
     * @return true when the key was handled and the caller should stop
     */
    protected final boolean handledBack(int keyCode) {
        if (keyCode != Keyboard.KEY_ESCAPE) {
            return false;
        }
        back();
        return true;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        handledBack(keyCode);
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
