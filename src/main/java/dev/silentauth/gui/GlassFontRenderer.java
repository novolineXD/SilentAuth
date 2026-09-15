package dev.silentauth.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

/**
 * A {@link FontRenderer} that draws with {@link GlassFont}, so a vanilla {@link
 * net.minecraft.client.gui.GuiTextField} handed this instance renders its text and positions
 * its cursor in the smooth UI font instead of the pixel font. Falls back to the vanilla
 * behaviour if the atlas could not be loaded.
 */
final class GlassFontRenderer extends FontRenderer {

    private static GlassFontRenderer instance;

    static GlassFontRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getMinecraft();
            instance = new GlassFontRenderer(mc);
        }
        return instance;
    }

    private GlassFontRenderer(Minecraft mc) {
        super(mc.gameSettings, new ResourceLocation("textures/font/ascii.png"), mc.getTextureManager(), false);
    }

    private static boolean ready() {
        return GlassFont.get().isReady();
    }

    /** Colour codes never appear in the fields this draws (IPs, tokens), but strip them defensively. */
    private static String clean(String text) {
        if (text == null || text.indexOf('\u00a7') < 0) {
            return text == null ? "" : text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public int drawString(String text, float x, float y, int colour, boolean shadow) {
        if (!ready()) {
            return super.drawString(text, x, y, colour, shadow);
        }
        return (int) Math.ceil(GlassFont.get().draw(clean(text), x, y, colour));
    }

    @Override
    public int getStringWidth(String text) {
        if (!ready()) {
            return super.getStringWidth(text);
        }
        return (int) Math.ceil(GlassFont.get().width(clean(text)));
    }

    @Override
    public int getCharWidth(char character) {
        if (!ready()) {
            return super.getCharWidth(character);
        }
        return (int) Math.ceil(GlassFont.get().charWidth(character));
    }

    @Override
    public String trimStringToWidth(String text, int width) {
        if (!ready()) {
            return super.trimStringToWidth(text, width);
        }
        return GlassFont.get().fit(clean(text), width, false);
    }

    @Override
    public String trimStringToWidth(String text, int width, boolean reverse) {
        if (!ready()) {
            return super.trimStringToWidth(text, width, reverse);
        }
        return GlassFont.get().fit(clean(text), width, reverse);
    }
}
