package dev.silentauth.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * Facade over {@link GlassFont} so screens draw the smooth UI font in gui-space ints, falling
 * back to the vanilla font if the atlas could not be loaded.
 */
final class Text {

    private Text() {
    }

    private static FontRenderer vanilla() {
        return Minecraft.getMinecraft().fontRendererObj;
    }

    static int draw(String text, int x, int y, int colour) {
        GlassFont font = GlassFont.get();
        if (!font.isReady()) {
            return vanilla().drawString(text, x, y, colour);
        }
        return (int) Math.ceil(font.draw(text, x, y, colour));
    }

    static void drawCentred(String text, int centreX, int y, int colour) {
        draw(text, centreX - width(text) / 2, y, colour);
    }

    static int width(String text) {
        GlassFont font = GlassFont.get();
        if (!font.isReady()) {
            return vanilla().getStringWidth(text);
        }
        return (int) Math.ceil(font.width(text));
    }

    static String trim(String text, int maxWidth) {
        GlassFont font = GlassFont.get();
        if (!font.isReady()) {
            return vanilla().trimStringToWidth(text, maxWidth);
        }
        return font.trim(text, maxWidth);
    }
}
