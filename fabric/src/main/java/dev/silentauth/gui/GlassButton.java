package dev.silentauth.gui;

/**
 * A glass button drawn by hand rather than as a vanilla widget, so it matches the panel and can be
 * laid out by the screens. The screen owns a list of these, draws them and routes clicks by id.
 */
final class GlassButton {

    final int id;
    int x;
    int y;
    int width;
    int height;
    String label;
    private boolean primary;
    boolean enabled = true;

    GlassButton(int id, int x, int y, int width, int height, String label) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
    }

    GlassButton asPrimary() {
        this.primary = true;
        return this;
    }

    boolean isOver(double mouseX, double mouseY) {
        return enabled && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    void draw(int mouseX, int mouseY) {
        boolean hover = isOver(mouseX, mouseY);
        int top;
        int bottom;
        if (!enabled) {
            top = Theme.BUTTON_DISABLED;
            bottom = Theme.BUTTON_DISABLED;
        } else if (primary) {
            top = hover ? Theme.BUTTON_PRIMARY_HOVER_TOP : Theme.BUTTON_PRIMARY_TOP;
            bottom = hover ? Theme.BUTTON_PRIMARY_HOVER_BOTTOM : Theme.BUTTON_PRIMARY_BOTTOM;
        } else {
            top = hover ? Theme.BUTTON_HOVER_TOP : Theme.BUTTON_TOP;
            bottom = hover ? Theme.BUTTON_HOVER_BOTTOM : Theme.BUTTON_BOTTOM;
        }

        int radius = 7;
        Draw.rounded(x, y, x + width, y + height, radius, Theme.BUTTON_BORDER);
        Draw.roundedGradient(x + 1, y + 1, x + width - 1, y + height - 1, radius - 1, top, bottom);

        int ink = !enabled ? Theme.TEXT_FAINT : primary ? Theme.TEXT : Theme.TEXT_DIM;
        Text.drawCentred(label, x + width / 2, y + (height - Text.lineHeight()) / 2 + 1, ink);
    }
}
