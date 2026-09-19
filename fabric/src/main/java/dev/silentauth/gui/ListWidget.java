package dev.silentauth.gui;

/**
 * A scrollable list of rows drawn by the owning screen. A click anywhere on a row runs the row's
 * action; a click on the remove target at its right edge removes it instead. Status is carried by a
 * coloured dot rather than a word, so rows stay compact.
 */
abstract class ListWidget {

    /** Width of the remove target at the right edge of each row. Generous, so it is easy to hit. */
    static final int REMOVE_WIDTH = 32;

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

    /** The standard two-line row: a coloured status dot, a title, and a faint subtitle. */
    protected final void paintRow(int rowX, int rowY, int rowWidth, int statusColour,
                                  String title, String subtitle) {
        int cy = rowY + Text.lineHeight() / 2;
        Draw.dot(rowX + 3, cy, 4, statusColour);
        int textX = rowX + 18;
        int textW = rowWidth - 18;
        Text.draw(Text.trim(title, textW), textX, rowY, Theme.TEXT);
        if (subtitle != null && !subtitle.isEmpty()) {
            Text.draw(Text.trim(subtitle, textW), textX, rowY + Text.lineHeight() + 3, Theme.TEXT_FAINT);
        }
    }

    abstract void onActivated(int index);

    boolean onRemove(int index) {
        return false;
    }

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
                Draw.rounded(x + 3, rowY + 1, x + width - 3, rowY + rowHeight - 1, 7, Theme.ROW_SELECTED);
            } else if (hovered) {
                Draw.rounded(x + 3, rowY + 1, x + width - 3, rowY + rowHeight - 1, 7, Theme.ROW_HOVER);
            }

            int usable = width - (isRemovable(index) ? REMOVE_WIDTH : 0);
            drawRow(index, x + 10, rowY + (rowHeight - 2 * Text.lineHeight() - 3) / 2, usable - 20, hovered);

            if (isRemovable(index)) {
                boolean overRemove = hovered && mouseX >= x + width - REMOVE_WIDTH;
                drawRemove(x + width - REMOVE_WIDTH, rowY, hovered, overRemove);
            }
        }

        if (getSize() == 0) {
            Text.drawCentred(emptyText(), x + width / 2, y + height / 2 - Text.lineHeight() / 2, Theme.TEXT_FAINT);
        }
        drawScrollbar();
    }

    /** A rounded red button with an x, plainly visible whether or not its row is hovered. */
    private void drawRemove(int left, int rowY, boolean rowHovered, boolean overRemove) {
        int centreX = left + REMOVE_WIDTH / 2;
        int centreY = rowY + rowHeight / 2;
        int fill = overRemove ? 0xE6F0607A : rowHovered ? 0x40F38BA8 : 0x24F38BA8;
        Draw.rounded(centreX - 9, centreY - 8, centreX + 9, centreY + 8, 5, fill);
        int ink = overRemove ? 0xFFFFFFFF : Theme.DANGER;
        Text.drawCentred("x", centreX, centreY - Text.lineHeight() / 2, ink);
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
        Draw.rounded(trackX, y + offset + 2, trackX + 3, y + offset + barHeight - 2, 1, Theme.GLASS_BORDER);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        int slot = (int) (mouseY - y) / rowHeight;
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

    void scroll(double amount) {
        if (amount == 0) {
            return;
        }
        this.scroll += amount > 0 ? -2 : 2;
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

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    int visibleRows() {
        return Math.max(1, height / rowHeight);
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, getSize() - visibleRows());
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }

    void clearSelection() {
        this.selected = -1;
    }
}
