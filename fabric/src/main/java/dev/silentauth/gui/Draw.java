package dev.silentauth.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * Small drawing helpers for the glass look, built on {@link DrawContext#fill}. Rounded shapes are
 * approximated by insetting the corner rows with a circle equation, which reads as soft glass at
 * any gui scale without needing a texture or a custom pipeline.
 *
 * <p>{@link #begin} stows the frame's context so the screens and the list widget can draw without
 * threading a context argument through every call.</p>
 */
final class Draw {

    private static DrawContext ctx;

    private Draw() {
    }

    static void begin(DrawContext context) {
        ctx = context;
    }

    static DrawContext ctx() {
        return ctx;
    }

    static void rect(int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1, y1, x2, y2, color);
    }

    /** A filled rectangle with rounded corners. */
    static void rounded(int x1, int y1, int x2, int y2, int radius, int color) {
        int r = clampRadius(radius, x1, y1, x2, y2);
        if (r <= 0) {
            ctx.fill(x1, y1, x2, y2, color);
            return;
        }
        ctx.fill(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            ctx.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            ctx.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }

    /** A rounded rectangle that fades from {@code top} to {@code bottom}, giving the glass sheen. */
    static void roundedGradient(int x1, int y1, int x2, int y2, int radius, int top, int bottom) {
        int r = clampRadius(radius, x1, y1, x2, y2);
        int height = Math.max(1, y2 - y1);
        for (int y = y1; y < y2; y++) {
            float t = (float) (y - y1) / height;
            int color = lerpColor(top, bottom, t);
            int i = Math.min(y - y1, y2 - 1 - y);
            int inset = i < r ? cornerInset(r, i) : 0;
            ctx.fill(x1 + inset, y, x2 - inset, y + 1, color);
        }
    }

    /** A rounded fill with a one pixel rounded border laid under it, so the edge reads as soft. */
    static void bordered(int x1, int y1, int x2, int y2, int radius, int fill, int border) {
        rounded(x1, y1, x2, y2, radius, border);
        rounded(x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, radius - 1), fill);
    }

    /** A sunken well for a field or a list, the same shape as {@link #bordered} but darker. */
    static void well(int x1, int y1, int x2, int y2, int radius, int fill, int border) {
        bordered(x1, y1, x2, y2, radius, fill, border);
    }

    /** The mod's panel: a tinted glass sheet with a soft mauve rim. */
    static void glassPanel(int x1, int y1, int x2, int y2, int radius) {
        rounded(x1 - 1, y1 - 1, x2 + 1, y2 + 1, radius + 1, Theme.SHADOW);
        rounded(x1, y1, x2, y2, radius, Theme.GLASS_BORDER);
        roundedGradient(x1 + 1, y1 + 1, x2 - 1, y2 - 1, radius - 1, Theme.GLASS_TOP, Theme.GLASS_BOTTOM);
    }

    /** A small round status marker. */
    static void dot(int cx, int cy, int radius, int color) {
        rounded(cx - radius, cy - radius, cx + radius, cy + radius, radius, color);
    }

    // ------------------------------------------------------------------ internals

    private static int clampRadius(int radius, int x1, int y1, int x2, int y2) {
        return Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
    }

    /** How far in the fill starts on corner row {@code i}, from a quarter-circle of the radius. */
    private static int cornerInset(int r, int i) {
        double dy = r - i - 0.5;
        double dx = r - Math.sqrt(Math.max(0.0, r * (double) r - dy * dy));
        return (int) Math.round(dx);
    }

    private static int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int oa = (int) (aa + (ba - aa) * t);
        int or = (int) (ar + (br - ar) * t);
        int og = (int) (ag + (bg - ag) * t);
        int ob = (int) (ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }
}
