package dev.silentauth.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Mouse;

/**
 * A scrollable list of rows. A click anywhere on a row runs the row's action; a click on the
 * remove target at its right edge removes it instead.
 */
abstract class ListWidget extends Gui {

    /** Width of the remove target at the right edge of each row. Generous, so it is easy to hit. */
    static final int REMOVE_WIDTH = 32;

    protected final Minecraft mc = Minecraft.getMinecraft();

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int rowHeight;

    private int scroll;
    private int selected = -1;

    ListWidget(int x, int y, int width, int height, int rowHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rowHeight = rowHeight;
    }

    abstract int getSize();

    abstract void drawRow(int index, int rowX, int rowY, int rowWidth, boolean hovered);

    /**
     * The standard two-line row: a coloured status dot, a title, and a faint subtitle. Status
     * is carried entirely by the dot's colour, so rows never need a word like "ok" or "in use".
     */
    protected final void paintRow(int rowX, int rowY, int rowWidth, int statusColour,
                                  String title, String subtitle) {
        Draw.dot(rowX + 4, rowY + 8, 3.5F, statusColour);
        int textX = rowX + 16;
        int textW = rowWidth - 16;
        mc.fontRendererObj.drawString(mc.fontRendererObj.trimStringToWidth(title, textW), textX, rowY, Theme.TEXT);
        if (subtitle != null && !subtitle.isEmpty()) {
            mc.fontRendererObj.drawString(mc.fontRendererObj.trimStringToWidth(subtitle, textW),
                    textX, rowY + 10, Theme.TEXT_FAINT);
        }
    }

    /** The row was clicked. */
    abstract void onActivated(int index);

    /** The row's remove target was clicked. Return false if the row cannot be removed. */
    boolean onRemove(int index) {
        return false;
    }

    /** Whether this row shows a remove target at all. */
    boolean isRemovable(int index) {
        return true;
    }

    String emptyText() {
        return "Nothing here yet";
    }

    void draw(int mouseX, int mouseY) {
        clampScroll();
        boolean inside = contains(mouseX, mouseY);

        int visible = visibleRows();
        for (int slot = 0; slot < visible; slot++) {
            int index = scroll + slot;
            if (index >= getSize()) {
                break;
            }
            int rowY = y + slot * rowHeight;
            boolean hovered = inside && mouseY >= rowY && mouseY < rowY + rowHeight;

            if (index == selected) {
                Draw.rounded(x + 3, rowY + 1, x + width - 3, rowY + rowHeight - 1, 7.0F, Theme.ROW_SELECTED);
            } else if (hovered) {
                Draw.rounded(x + 3, rowY + 1, x + width - 3, rowY + rowHeight - 1, 7.0F, Theme.ROW_HOVER);
            }

            int usable = width - (isRemovable(index) ? REMOVE_WIDTH : 0);
            drawRow(index, x + 10, rowY + (rowHeight - 16) / 2, usable - 20, hovered);

            if (isRemovable(index)) {
                boolean overRemove = hovered && mouseX >= x + width - REMOVE_WIDTH;
                drawRemove(x + width - REMOVE_WIDTH, rowY, hovered, overRemove);
            }
        }

        if (getSize() == 0) {
            drawCenteredString(mc.fontRendererObj, emptyText(), x + width / 2, y + height / 2 - 4, Theme.TEXT_FAINT);
        }
        drawScrollbar();
    }

    /**
     * A remove button: a rounded well with an x, drawn rather than using a glyph so it lines up
     * at any scale. It sits in a visible pill while its row is hovered and turns red when the
     * pointer is over it, so it reads as something you can click rather than decoration.
     */
    private void drawRemove(int left, int rowY, boolean rowHovered, boolean overRemove) {
        int centreX = left + REMOVE_WIDTH / 2;
        int centreY = rowY + rowHeight / 2;
        // A rounded red button with a glyph. Font glyphs and rounded fills both render reliably,
        // so the delete control is always plainly visible whether or not it is hovered.
        int fill = overRemove ? 0xE6F0607A : rowHovered ? 0x40F38BA8 : 0x24F38BA8;
        Draw.rounded(centreX - 9, centreY - 8, centreX + 9, centreY + 8, 5.0F, fill);
        int ink = overRemove ? 0xFFFFFFFF : Theme.DANGER;
        String x = "x";
        mc.fontRendererObj.drawString(x, centreX - mc.fontRendererObj.getStringWidth(x) / 2, centreY - 4, ink);
    }

    private void drawScrollbar() {
        int size = getSize();
        int visible = visibleRows();
        if (size <= visible) {
            return;
        }
        int trackX = x + width - 4;
        int barHeight = Math.max(16, height * visible / size);
        int maxScroll = size - visible;
        int offset = maxScroll == 0 ? 0 : (height - barHeight) * scroll / maxScroll;
        Draw.rounded(trackX, y + offset + 2, trackX + 3.0F, y + offset + barHeight - 2, 1.5F, Theme.GLASS_BORDER);
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        int slot = (mouseY - y) / rowHeight;
        // The widget can be slightly taller than a whole number of rows; that strip is not a row.
        if (slot < 0 || slot >= visibleRows()) {
            return false;
        }
        int index = scroll + slot;
        if (index < 0 || index >= getSize()) {
            return false;
        }

        selected = index;
        if (isRemovable(index) && mouseX >= x + width - REMOVE_WIDTH) {
            return onRemove(index);
        }
        onActivated(index);
        return true;
    }

    /**
     * Handles one mouse event's worth of wheel movement. Called from handleMouseInput, which
     * Minecraft runs once per mouse event, so this reads the per-event delta the way the
     * vanilla lists do rather than the polled accumulator.
     */
    void handleScroll() {
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        scroll += wheel > 0 ? -2 : 2;
        clampScroll();
    }

    void moveSelection(int delta) {
        int size = getSize();
        if (size == 0) {
            return;
        }
        int next = selected < 0 ? (delta > 0 ? 0 : size - 1) : selected + delta;
        selected = Math.max(0, Math.min(size - 1, next));
        ensureVisible(selected);
    }

    void activateSelection() {
        if (selected >= 0 && selected < getSize()) {
            onActivated(selected);
        }
    }

    private void ensureVisible(int index) {
        if (index < scroll) {
            scroll = index;
        } else if (index >= scroll + visibleRows()) {
            scroll = index - visibleRows() + 1;
        }
        clampScroll();
    }

    private boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    int visibleRows() {
        return Math.max(1, height / rowHeight);
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, getSize() - visibleRows());
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }

    int getSelectedIndex() {
        return selected;
    }

    void clearSelection() {
        this.selected = -1;
    }
}
