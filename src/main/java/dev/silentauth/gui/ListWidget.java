package dev.silentauth.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Mouse;

public abstract class ListWidget extends Gui {

    protected final Minecraft mc = Minecraft.getMinecraft();

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int rowHeight;

    private int scroll;
    private int selected = -1;

    public ListWidget(int x, int y, int width, int height, int rowHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rowHeight = rowHeight;
    }

    public abstract int getSize();

    public abstract void drawRow(int index, int rowX, int rowY, int rowWidth, boolean hovered, boolean isSelected);

    public void draw(int mouseX, int mouseY) {
        drawRect(x, y, x + width, y + height, 0x77000000);
        clampScroll();

        int visible = visibleRows();
        for (int slot = 0; slot < visible; slot++) {
            int index = scroll + slot;
            if (index >= getSize()) {
                break;
            }
            int rowY = y + slot * rowHeight;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + rowHeight;
            boolean isSelected = index == selected;
            if (isSelected) {
                drawRect(x, rowY, x + width, rowY + rowHeight - 1, 0x803C6EFF);
            } else if (hovered) {
                drawRect(x, rowY, x + width, rowY + rowHeight - 1, 0x33FFFFFF);
            }
            drawRow(index, x + 5, rowY + 4, width - 10, hovered, isSelected);
        }

        drawScrollbar();
    }

    private void drawScrollbar() {
        int size = getSize();
        int visible = visibleRows();
        if (size <= visible) {
            return;
        }
        int trackX = x + width - 3;
        drawRect(trackX, y, trackX + 3, y + height, 0x33FFFFFF);
        int barHeight = Math.max(12, height * visible / size);
        int maxScroll = size - visible;
        int offset = maxScroll == 0 ? 0 : (height - barHeight) * scroll / maxScroll;
        drawRect(trackX, y + offset, trackX + 3, y + offset + barHeight, 0xAAFFFFFF);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        int slot = (mouseY - y) / rowHeight;
        int index = scroll + slot;
        if (index < 0 || index >= getSize()) {
            return false;
        }
        selected = index;
        onSelected(index);
        return true;
    }

    public void handleScroll() {
        int wheel = Mouse.getDWheel();
        if (wheel == 0) {
            return;
        }
        scroll += wheel > 0 ? -1 : 1;
        clampScroll();
    }

    protected void onSelected(int index) {
        return;
    }

    public int visibleRows() {
        return Math.max(1, height / rowHeight);
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, getSize() - visibleRows());
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    public int getSelectedIndex() {
        return selected;
    }

    public void setSelectedIndex(int index) {
        this.selected = index;
    }

    public void clearSelection() {
        this.selected = -1;
    }

    public int getRowHeight() {
        return rowHeight;
    }
}
