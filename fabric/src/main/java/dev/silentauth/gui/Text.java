package dev.silentauth.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

/**
 * Text drawing on the current {@link Draw} frame, using the vanilla font at a slightly larger
 * scale so the glass screens read like a website rather than a cramped game menu.
 */
final class Text {

    /** The screens are laid out in these logical pixels; the font is scaled up to match. */
    static final float SCALE = 1.15F;

    private Text() {
    }

    private static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    static int width(String text) {
        return Math.round(font().getWidth(text) * SCALE);
    }

    static int lineHeight() {
        return Math.round(9 * SCALE);
    }

    static void draw(String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        var matrices = Draw.ctx().getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(SCALE, SCALE);
        Draw.ctx().drawText(font(), text, 0, 0, color, true);
        matrices.popMatrix();
    }

    static void drawCentred(String text, int centreX, int y, int color) {
        draw(text, centreX - width(text) / 2, y, color);
    }

    /** Trims to fit {@code maxWidth} logical pixels, adding an ellipsis when it has to cut. */
    static String trim(String text, int maxWidth) {
        if (text == null || text.isEmpty() || width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int room = maxWidth - width(ellipsis);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (width(out.toString() + text.charAt(i)) > room) {
                break;
            }
            out.append(text.charAt(i));
        }
        return out + ellipsis;
    }
}
