package dev.silentauth.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** A rounded pill of tinted glass, drawn rather than blitted from the widget texture. */
class GlassButton extends GuiButton {

    private static final float RADIUS = 8.0F;

    /** Drawn in mauve, for the one action a screen is really for. */
    private boolean primary;

    GlassButton(int id, int x, int y, int width, int height, String label) {
        super(id, x, y, width, height, label);
    }

    GlassButton asPrimary() {
        this.primary = true;
        return this;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        hovered = mouseX >= xPosition && mouseY >= yPosition
                && mouseX < xPosition + width && mouseY < yPosition + height;

        float right = xPosition + width;
        float bottom = yPosition + height;

        int top;
        int base;
        int ink;
        if (!enabled) {
            top = Theme.BUTTON_DISABLED;
            base = Theme.BUTTON_DISABLED;
            ink = Theme.TEXT_FAINT;
        } else if (primary) {
            top = hovered ? Theme.BUTTON_PRIMARY_HOVER_TOP : Theme.BUTTON_PRIMARY_TOP;
            base = hovered ? Theme.BUTTON_PRIMARY_HOVER_BOTTOM : Theme.BUTTON_PRIMARY_BOTTOM;
            ink = hovered ? Theme.TEXT : Theme.MAUVE;
        } else {
            top = hovered ? Theme.BUTTON_HOVER_TOP : Theme.BUTTON_TOP;
            base = hovered ? Theme.BUTTON_HOVER_BOTTOM : Theme.BUTTON_BOTTOM;
            ink = hovered ? Theme.TEXT : Theme.TEXT_DIM;
        }

        Draw.rounded(xPosition, yPosition, right, bottom, RADIUS,
                primary && enabled ? Theme.FIELD_BORDER_FOCUS : Theme.BUTTON_BORDER);
        Draw.roundedGradient(xPosition + 1.0F, yPosition + 1.0F, right - 1.0F, bottom - 1.0F,
                RADIUS - 1.0F, top, base);

        mc.fontRendererObj.drawString(displayString,
                xPosition + (width - mc.fontRendererObj.getStringWidth(displayString)) / 2,
                yPosition + (height - 8) / 2,
                ink);
    }
}
