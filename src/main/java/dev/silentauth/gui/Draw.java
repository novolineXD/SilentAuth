package dev.silentauth.gui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Smooth, anti-aliased rounded shapes.
 *
 * <p>Minecraft's own {@code drawRect} takes integers, so anything curved built from it steps
 * a whole gui pixel at a time and looks like a staircase. These build the outline as floats
 * and hand it to the tessellator instead, then trace the edge a second time with a band that
 * fades to nothing across a single screen pixel. That band is the anti-aliasing: it needs no
 * multisampling, no shaders and no render targets, so it behaves the same on every driver.</p>
 */
final class Draw {

    /** Screen pixels per gui pixel, so the soft edge is always one real pixel wide. */
    private static float scale = 2.0F;

    private static final int MAX_SEGMENTS = 20;

    private Draw() {
    }

    /** Called once a frame by the screen, so shapes know how wide a real pixel is. */
    static void setScale(int scaleFactor) {
        scale = Math.max(1, scaleFactor);
    }

    private static float feather() {
        return 1.2F / scale;
    }

    // ------------------------------------------------------------------ shapes

    static void rounded(float left, float top, float right, float bottom, float radius, int colour) {
        roundedGradient(left, top, right, bottom, radius, colour, colour);
    }

    /**
     * A rounded rectangle whose fill runs from one colour at the top to another at the
     * bottom. The gradient is clipped by the shape itself, so corners stay clean.
     */
    static void roundedGradient(float left, float top, float right, float bottom, float radius,
                                int topColour, int bottomColour) {
        float width = right - left;
        float height = bottom - top;
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        float r = Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F));

        int corners = segments(r);
        int count = corners * 4;
        float[] px = new float[count];
        float[] py = new float[count];
        float[] nx = new float[count];
        float[] ny = new float[count];
        outline(left, top, right, bottom, r, corners, px, py, nx, ny);

        begin();
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();

        // Solid core.
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        vertex(wr, (left + right) / 2.0F, (top + bottom) / 2.0F, mix(topColour, bottomColour, 0.5F), 1.0F);
        for (int i = 0; i <= count; i++) {
            int index = i % count;
            float blend = (py[index] - top) / height;
            vertex(wr, px[index], py[index], mix(topColour, bottomColour, blend), 1.0F);
        }
        Tessellator.getInstance().draw();

        // Feathered edge: full alpha on the outline, nothing a pixel further out.
        float feather = feather();
        wr.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= count; i++) {
            int index = i % count;
            int colour = mix(topColour, bottomColour, (py[index] - top) / height);
            vertex(wr, px[index], py[index], colour, 1.0F);
            vertex(wr, px[index] + nx[index] * feather, py[index] + ny[index] * feather, colour, 0.0F);
        }
        Tessellator.getInstance().draw();

        end();
    }

    /**
     * A straight line with soft edges, for the small marks that would otherwise be a visibly
     * jagged stack of one pixel squares.
     */
    static void line(float x1, float y1, float x2, float y2, float thickness, int colour) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0F) {
            return;
        }
        float normalX = -dy / length;
        float normalY = dx / length;

        float half = thickness / 2.0F;
        float feather = feather();
        float[] offsets = { -half - feather, -half, half, half + feather };
        float[] alphas = { 0.0F, 1.0F, 1.0F, 0.0F };

        begin();
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < offsets.length; i++) {
            vertex(wr, x1 + normalX * offsets[i], y1 + normalY * offsets[i], colour, alphas[i]);
            vertex(wr, x2 + normalX * offsets[i], y2 + normalY * offsets[i], colour, alphas[i]);
        }
        Tessellator.getInstance().draw();
        end();
    }

    /** A soft filled circle, for status dots. */
    static void dot(float cx, float cy, float radius, int colour) {
        rounded(cx - radius, cy - radius, cx + radius, cy + radius, radius, colour);
    }

    /** A rounded well: a soft edge in one colour with a fill sitting inside it. */
    static void well(float left, float top, float right, float bottom, float radius, int fill, int border) {
        rounded(left, top, right, bottom, radius, border);
        rounded(left + 1.0F, top + 1.0F, right - 1.0F, bottom - 1.0F, radius - 1.0F, fill);
    }

    /**
     * A panel of tinted glass: a soft shadow spread underneath, a light edge, and a fill that
     * catches the light along its top edge.
     */
    static void glassPanel(float left, float top, float right, float bottom, float radius) {
        for (int i = 4; i >= 1; i--) {
            rounded(left - i, top - i + 1, right + i, bottom + i + 1, radius + i, Theme.SHADOW);
        }
        rounded(left, top, right, bottom, radius, Theme.GLASS_BORDER);
        roundedGradient(left + 1.0F, top + 1.0F, right - 1.0F, bottom - 1.0F, radius - 1.0F,
                Theme.GLASS_TOP, Theme.GLASS_BOTTOM);
    }

    // ------------------------------------------------------------------ geometry

    /** More segments on a bigger corner, so the curve stays smooth without wasting vertices. */
    private static int segments(float radius) {
        return Math.max(3, Math.min(MAX_SEGMENTS, (int) (radius * scale * 0.7F) + 3));
    }

    /**
     * Walks the four corner arcs clockwise, recording each point and the direction pointing
     * straight out of the shape there. A zero radius collapses an arc to its corner point,
     * which keeps square shapes working through the same path.
     */
    private static void outline(float left, float top, float right, float bottom, float radius,
                                int perCorner, float[] px, float[] py, float[] nx, float[] ny) {
        float[] centreX = { left + radius, right - radius, right - radius, left + radius };
        float[] centreY = { top + radius, top + radius, bottom - radius, bottom - radius };
        // Screen space has y running down, so these sweep top-left, top-right, then the bottom.
        float[] startAngle = { (float) Math.PI, -(float) Math.PI / 2.0F, 0.0F, (float) Math.PI / 2.0F };

        int index = 0;
        for (int corner = 0; corner < 4; corner++) {
            for (int step = 0; step < perCorner; step++) {
                float angle = startAngle[corner] + (float) Math.PI / 2.0F * step / (perCorner - 1);
                float dx = (float) Math.cos(angle);
                float dy = (float) Math.sin(angle);
                px[index] = centreX[corner] + dx * radius;
                py[index] = centreY[corner] + dy * radius;
                nx[index] = dx;
                ny[index] = dy;
                index++;
            }
        }
    }

    // ------------------------------------------------------------------ plumbing

    private static int mix(int from, int to, float amount) {
        if (from == to) {
            return from;
        }
        float t = Math.max(0.0F, Math.min(1.0F, amount));
        int a = lerp(from >>> 24, to >>> 24, t);
        int r = lerp(from >> 16 & 0xFF, to >> 16 & 0xFF, t);
        int g = lerp(from >> 8 & 0xFF, to >> 8 & 0xFF, t);
        int b = lerp(from & 0xFF, to & 0xFF, t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int lerp(int from, int to, float t) {
        return from + (int) ((to - from) * t);
    }

    private static void vertex(WorldRenderer wr, float x, float y, int colour, float alphaScale) {
        wr.pos(x, y, 0.0D).color(
                (colour >> 16 & 0xFF) / 255.0F,
                (colour >> 8 & 0xFF) / 255.0F,
                (colour & 0xFF) / 255.0F,
                (colour >>> 24) / 255.0F * alphaScale).endVertex();
    }

    private static void begin() {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    private static void end() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }
}
