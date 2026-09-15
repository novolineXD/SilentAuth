package dev.silentauth.gui;

import dev.silentauth.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * A smooth, anti-aliased UI font.
 *
 * <p>The vanilla bitmap font is what makes a screen read as "Minecraft". This draws a
 * sans-serif atlas that is baked at build time (see the generator in the scratchpad) into a
 * texture and a metrics file, so at runtime there is no Java2D and thus no dependency on the
 * font manager natives that Minecraft's launch arguments hide. The atlas is sampled with
 * linear filtering, so the glyphs stay smooth at any gui scale.</p>
 */
final class GlassFont {

    private static final char FIRST = 32;
    private static final char LAST = 126;

    /** Gui-space height of a line of text. */
    static final float LINE = 9.0F;

    private static final ResourceLocation ATLAS = new ResourceLocation("silentauth", "textures/font.png");

    private static GlassFont instance;

    private boolean ready;
    private int cellW;
    private int cellH;
    private int cols;
    private int pad;
    private int texW;
    private int texH;
    private final int[] widths = new int[128];
    private float scale;

    static GlassFont get() {
        if (instance == null) {
            instance = new GlassFont();
        }
        return instance;
    }

    private GlassFont() {
        load();
    }

    private void load() {
        InputStream in = GlassFont.class.getResourceAsStream("/assets/silentauth/font.dat");
        if (in == null) {
            Log.warn("Font metrics missing, falling back to the vanilla font");
            return;
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            try {
                String[] head = reader.readLine().trim().split("\\s+");
                cellW = Integer.parseInt(head[0]);
                cellH = Integer.parseInt(head[1]);
                cols = Integer.parseInt(head[2]);
                pad = Integer.parseInt(head[3]);
                String[] w = reader.readLine().trim().split("\\s+");
                for (int c = FIRST; c <= LAST; c++) {
                    widths[c] = Integer.parseInt(w[c - FIRST]);
                }
            } finally {
                reader.close();
            }
            int rows = (LAST - FIRST + cols) / cols;
            texW = cols * cellW;
            texH = rows * cellH;
            scale = LINE / cellH;
            ready = true;
        } catch (Exception e) {
            Log.warn("Could not read the font metrics, falling back to the vanilla font");
        }
    }

    boolean isReady() {
        return ready;
    }

    /**
     * Binds the atlas once so Minecraft loads it before the first glyph is drawn. Without this
     * the very first frame samples the magenta "missing texture" for an instant. Must run on
     * the client thread.
     */
    void preload() {
        if (ready) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(ATLAS);
        }
    }

    private int advancePx(char c) {
        if (c < FIRST || c > LAST) {
            c = ' ';
        }
        return widths[c];
    }

    float width(String text) {
        float w = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            w += advancePx(text.charAt(i)) * scale;
        }
        return w;
    }

    float charWidth(char c) {
        return advancePx(c) * scale;
    }

    /** The longest prefix (or suffix, if reverse) of the text that fits within maxWidth. No ellipsis. */
    String fit(String text, float maxWidth, boolean reverse) {
        if (text == null || text.isEmpty() || width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        StringBuilder sb = new StringBuilder();
        float w = 0.0F;
        if (reverse) {
            for (int i = text.length() - 1; i >= 0; i--) {
                float cw = charWidth(text.charAt(i));
                if (w + cw > maxWidth) {
                    break;
                }
                sb.insert(0, text.charAt(i));
                w += cw;
            }
        } else {
            for (int i = 0; i < text.length(); i++) {
                float cw = charWidth(text.charAt(i));
                if (w + cw > maxWidth) {
                    break;
                }
                sb.append(text.charAt(i));
                w += cw;
            }
        }
        return sb.toString();
    }

    /** Draws the string with its top-left at (x, y) in the wanted ARGB colour. Returns the end x. */
    float draw(String text, float x, float y, int argb) {
        float a = (argb >>> 24) / 255.0F;
        if (a == 0.0F) {
            a = 1.0F;
        }
        float r = (argb >> 16 & 0xFF) / 255.0F;
        float g = (argb >> 8 & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;

        // Everything goes through the Tessellator, exactly like Draw, so Minecraft's cached GL
        // state stays consistent. Raw immediate-mode glBegin here would desync that cache and
        // leave the tessellator-drawn panels invisible.
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(r, g, b, a);
        Minecraft.getMinecraft().getTextureManager().bindTexture(ATLAS);
        // Smooth sampling; Minecraft binds gui textures as nearest by default.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        float penX = x;
        float h = cellH * scale;
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < FIRST || c > LAST) {
                c = ' ';
            }
            int idx = c - FIRST;
            int cx = (idx % cols) * cellW + pad;
            int cy = (idx / cols) * cellH;
            int gw = widths[c];
            float w = gw * scale;
            float u0 = (float) cx / texW;
            float v0 = (float) cy / texH;
            float u1 = (float) (cx + gw) / texW;
            float v1 = (float) (cy + cellH) / texH;
            wr.pos(penX, y + h, 0.0D).tex(u0, v1).endVertex();
            wr.pos(penX + w, y + h, 0.0D).tex(u1, v1).endVertex();
            wr.pos(penX + w, y, 0.0D).tex(u1, v0).endVertex();
            wr.pos(penX, y, 0.0D).tex(u0, v0).endVertex();
            penX += w;
        }
        Tessellator.getInstance().draw();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return penX;
    }

    /** Trims with an ellipsis so long values never overrun their row. */
    String trim(String text, float maxWidth) {
        if (width(text) <= maxWidth) {
            return text;
        }
        float dots = width("...");
        StringBuilder sb = new StringBuilder();
        float w = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            float cw = advancePx(text.charAt(i)) * scale;
            if (w + cw + dots > maxWidth) {
                break;
            }
            sb.append(text.charAt(i));
            w += cw;
        }
        return sb.append("...").toString();
    }
}
