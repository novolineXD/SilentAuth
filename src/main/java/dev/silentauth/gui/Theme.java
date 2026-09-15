package dev.silentauth.gui;

/**
 * A mauve glass palette. Colours are ARGB, and most of the surfaces are deliberately
 * translucent so panels read as tinted glass sitting over the game rather than as slabs.
 *
 * <p>State is never carried by colour alone: green, red and grey each come with a word, so
 * the rows still read without colour vision.</p>
 */
final class Theme {

    private Theme() {
    }

    /** Dims and cools the game behind the glass. */
    static final int BACKDROP = 0xD1100B18;

    // --- glass surfaces -------------------------------------------------

    /** Panels fill top to bottom, which is what gives the sheen along the top edge. */
    static final int GLASS_TOP = 0x7A4A3C6B;
    static final int GLASS_BOTTOM = 0x66281F3D;
    static final int GLASS_BORDER = 0x5CC5A6F0;
    static final int SHADOW = 0x14000000;

    static final int ROW_HOVER = 0x1FC5A6F0;
    static final int ROW_SELECTED = 0x33C5A6F0;

    // --- ink ------------------------------------------------------------

    static final int MAUVE = 0xFFCBA6F7;
    static final int MAUVE_SOFT = 0xFFB69AE0;
    static final int TEXT = 0xFFEDE6F7;
    static final int TEXT_DIM = 0xFFB9B0CC;
    static final int TEXT_FAINT = 0xFF877E9C;

    /** In use - a vivid green so the active account or proxy is unmistakable. */
    static final int OK = 0xFF5BE58A;
    /** Invalid or dead. */
    static final int DANGER = 0xFFF35A6E;
    /** Good but not in use - a clear neutral grey, distinct from the purple glass behind it. */
    static final int IDLE = 0xFF9A9AB0;

    // --- controls -------------------------------------------------------

    static final int FIELD = 0x4D140F1F;
    static final int FIELD_BORDER = 0x38C5A6F0;
    static final int FIELD_BORDER_FOCUS = 0xA3CBA6F7;

    static final int BUTTON_TOP = 0x3DFFFFFF;
    static final int BUTTON_BOTTOM = 0x1FFFFFFF;
    static final int BUTTON_HOVER_TOP = 0x5CFFFFFF;
    static final int BUTTON_HOVER_BOTTOM = 0x33FFFFFF;
    static final int BUTTON_BORDER = 0x3DFFFFFF;
    static final int BUTTON_PRIMARY_TOP = 0x5CCBA6F7;
    static final int BUTTON_PRIMARY_BOTTOM = 0x2ECBA6F7;
    static final int BUTTON_PRIMARY_HOVER_TOP = 0x8FCBA6F7;
    static final int BUTTON_PRIMARY_HOVER_BOTTOM = 0x4DCBA6F7;
    static final int BUTTON_DISABLED = 0x14FFFFFF;
}
