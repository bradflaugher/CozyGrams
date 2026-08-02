package com.cozygrams.tv;

import android.graphics.Typeface;

/**
 * The single source of truth for CozyGrams' colours, type scale and 10-foot spacing.
 *
 * <p>Every size here is expressed in "design pixels" measured against a 720p reference
 * screen and scaled up by {@link #scale(float)}.
 *
 * <h2>How the type scale was chosen</h2>
 *
 * <p>The room this game is played in is the constraint, so the sizes were worked out from
 * it rather than guessed. A 55" 16:9 panel is 26.96" tall; at 1080p that is 40.1 pixels
 * per inch, and from a sofa ten feet (120") away one pixel subtends
 * {@code atan(1 / 40.1 / 120)} ≈ 0.72 arcminutes.
 *
 * <p>ISO 9241-303 puts comfortable sustained reading at a cap height of 20–22 arcminutes,
 * with 16 arcminutes the practical minimum; a familiar glyph stays <em>identifiable</em>
 * down to about 5 arcminutes, which is a much lower bar. Android TV's own Leanback
 * guidance says body text is 18sp, and a 1080p television reports xhdpi, so that is 36
 * real pixels. All three agree, so the scale is pinned to them:
 *
 * <ul>
 *   <li>{@link #MIN_PROSE_SP} = 24 design px = <b>36 px at 1080p</b>. Sans-serif cap
 *       height is about .70 em, so 25.2 px, so <b>18.0 arcminutes</b>. Anything made of
 *       words is drawn at least this large — {@link #textSize(float)} enforces it.</li>
 *   <li>{@link #MIN_READABLE_SP} = 18 design px = 27 px at 1080p = 13.5 arcminutes. This
 *       is <em>not</em> a prose size and never was; it is the floor for single, isolated,
 *       high-contrast glyphs whose size is fixed by geometry rather than by taste — the
 *       clue digits, which at 20x20 must fit two numerals inside a 35 px square. 13.5
 *       arcminutes is 2.7x the identification threshold, which is all a numeral needs.
 *       Raising it any further would make 20x20 clues collide with their own columns.</li>
 * </ul>
 *
 * <p>The named steps then climb from the prose floor: 24 / 27 / 32 / 42 / 58 design px,
 * which is 18 / 20 / 24 / 32 / 44 arcminutes at 1080p from ten feet.
 *
 * <h2>Contrast</h2>
 *
 * <p>{@link #contrastRatio(int, int)} is real WCAG maths, not a vibe, and
 * {@link #readableOn(int, int, double)} will nudge an identity colour until it clears a
 * stated ratio. The pairs the game actually draws are asserted in {@code ThemeTest}.
 */
public final class Theme {

    // ---- Ink and paper -------------------------------------------------------------

    /** Deep plum used for text on light surfaces and for the grid's heavy rules. */
    public static final int INK = rgb(38, 32, 56);
    /** Warm off-white used for primary text on dark panels. */
    public static final int CREAM = rgb(255, 245, 227);
    /** The board's paper colour: slightly warmer than white so it never glares. */
    public static final int PAPER = rgb(255, 251, 242);
    /**
     * Secondary copy that should recede without disappearing. Warmed from its old cool
     * lilac toward the lamplight in the illustrations; it also gained a little luminance
     * on the way, so it now clears 10:1 on a panel instead of 9.9:1.
     */
    public static final int SOFT_TEXT = rgb(226, 212, 218);
    /** Mid-tone used for unsolved clue numbers and disabled affordances <em>on paper</em>. */
    public static final int GRID = rgb(94, 82, 116);

    /**
     * The colour a frosted panel settles to once it is opaque — the background every
     * light text colour in the game is measured against. Slightly warmer than the old
     * cool plum so the panels sit in the same lamplight as the art behind them.
     */
    public static final int PANEL = rgb(44, 33, 53);

    // ---- Player identity -----------------------------------------------------------

    /** Rose (player one) — the warm side of the palette. */
    public static final int PINK = rgb(255, 122, 158);
    public static final int PINK_DARK = rgb(186, 66, 108);
    public static final int PINK_LIGHT = rgb(255, 186, 205);

    /** Sky (player two) — the cool side of the palette. */
    public static final int BLUE = rgb(112, 202, 224);
    public static final int BLUE_DARK = rgb(38, 122, 152);
    public static final int BLUE_LIGHT = rgb(186, 233, 244);

    /** Candle gold, reserved for hints and celebration sparkle. */
    public static final int GOLD = rgb(255, 209, 128);

    // ---- The board's own ink ---------------------------------------------------------

    /**
     * The colour of a placed square — and, just as importantly, <em>not</em> a player's
     * colour.
     *
     * <p>A filled tile used to be {@code blend(PINK_DARK, PINK, .62)}, which put Rose's
     * identity colour on up to four hundred squares at once. Her cursor core then sat at
     * 1.30:1 against the field it had to be found on, the same hue at the same value, while
     * Sky's cyan was hue-opposite and popped instantly. Unequal findability in a two-player
     * game is disqualifying, so the fix is not a heavier casing on the cursor: it is to
     * stop sharing the palette. Saturated pink now exists on the board <em>only</em> as
     * Rose.
     *
     * <p>What replaced it is a deep mulberry plum — warm, lamplit, the colour of a berry
     * ink rather than of a highlighter. It is 9.9:1 on paper and 8.5:1 on a crossed square,
     * so filled / crossed / untouched still separate by value alone, and both identity
     * colours clear the graphical-object floor against it (Rose 4.2:1, Sky 5.5:1).
     */
    public static final int TILE = rgb(88, 54, 78);

    /** The shaded lower body of a tile, and the wash used for its per-square variation. */
    public static final int TILE_DEEP = rgb(58, 34, 56);

    /**
     * The filled tile with extra contrast on. This is a change of <em>kind</em>, not of
     * amount: the plum desaturates all the way to a cool slate and drops another 3:1
     * against paper, so "filled" stops being a warm hue and becomes a near-black block.
     * Turning the setting on used to move a tile by about five percent of luminance, which
     * is nothing at ten feet.
     */
    public static final int TILE_BOLD = rgb(44, 46, 62);

    /** Extra contrast's shaded tile body. */
    public static final int TILE_BOLD_DEEP = rgb(26, 28, 42);

    /** The tile colour for the current contrast setting. */
    public static int tile(boolean highContrast) {
        return highContrast ? TILE_BOLD : TILE;
    }

    /** The shaded tile body for the current contrast setting. */
    public static int tileDeep(boolean highContrast) {
        return highContrast ? TILE_BOLD_DEEP : TILE_DEEP;
    }

    // ---- Type scale (design pixels at 720p) -----------------------------------------

    public static final float TITLE = 58f;
    public static final float HEADING = 42f;
    public static final float SUBHEAD = 32f;
    public static final float BODY = 27f;
    public static final float CAPTION = 24f;

    /**
     * The floor for anything made of words. See the class notes: 36 px at 1080p, a cap
     * height of 18 arcminutes from ten feet, matching Android TV's 18sp body minimum.
     */
    public static final float MIN_PROSE_SP = 24f;

    /**
     * The floor for a single isolated glyph whose size is set by geometry — in practice
     * the clue digits, which have to fit inside one square at 20x20. 27 px at 1080p.
     * Words never come down here; {@link #textSize(float)} stops at
     * {@link #MIN_PROSE_SP}.
     */
    public static final float MIN_READABLE_SP = 18f;

    // ---- Layout --------------------------------------------------------------------

    /**
     * Fraction of each edge kept clear so overscanning TVs never clip the interface.
     *
     * <p>Leanback asks for 5% of the height and 5% of the width — 27dp and 48dp on a 1080p
     * set. This was .045 for a while, which is inside the margin an overscanning panel is
     * allowed to eat. {@link BoardLayout} spends it on both axes, so raising it takes the
     * board slightly smaller rather than pushing anything off screen.
     */
    public static final float SAFE_AREA = .05f;

    /** Corner radius for the large frosted panels, in design pixels. */
    public static final float PANEL_RADIUS = 30f;

    private static Typeface boldFace;
    private static Typeface regularFace;
    private static float screenHeight = 720f;

    private Theme() {
    }

    /**
     * Opaque ARGB, worked out here rather than through {@code android.graphics.Color}.
     * {@link Comfort} does the same and for the same reason: the unit tests run with
     * {@code returnDefaultValues}, where every {@code Color} static answers zero, and a
     * palette that is all zeroes in a test cannot be asserted about at all.
     */
    private static int rgb(int red, int green, int blue) {
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    /** Records the current surface height so {@link #scale(float)} can resolve sizes. */
    public static void setScreenHeight(float height) {
        screenHeight = Math.max(1f, height);
    }

    public static float screenHeight() {
        return screenHeight;
    }

    /** Converts a design pixel size measured at 720p into a size for the live screen. */
    public static float scale(float designPixels) {
        return designPixels * screenHeight / 720f;
    }

    /** Scales a text size but never lets words fall below the ten-foot prose floor. */
    public static float textSize(float designPixels) {
        return Math.max(scale(MIN_PROSE_SP), scale(designPixels));
    }

    public static Typeface bold() {
        if (boldFace == null) {
            boldFace = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        }
        return boldFace;
    }

    public static Typeface regular() {
        if (regularFace == null) {
            regularFace = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        }
        return regularFace;
    }

    /**
     * The identity colour for a player index, used by cursors, cards and particles.
     *
     * <p>Sky's colour is asked of {@link Comfort} rather than named here, so the
     * colour-blind-friendly identity applies everywhere at once instead of only in the
     * places that remembered to check the setting.
     */
    public static int playerColor(int player) {
        return player == 0 ? PINK : Comfort.skyColor();
    }

    public static int playerColorDark(int player) {
        return player == 0 ? PINK_DARK : Comfort.skyColorDark();
    }

    public static int playerColorLight(int player) {
        return player == 0 ? PINK_LIGHT : Comfort.skyColorLight();
    }

    public static String playerName(int player) {
        return player == 0 ? "Rose" : "Sky";
    }

    // ---- Comfort-aware colour choices -----------------------------------------------

    /**
     * The colour for copy that should recede. With extra contrast on it stops receding,
     * because someone who asked for more contrast did not ask for a subtle hierarchy.
     */
    public static int secondaryText(boolean highContrast) {
        return highContrast ? CREAM : SOFT_TEXT;
    }

    // ---- Contrast maths --------------------------------------------------------------

    /**
     * WCAG 2.1 relative luminance of an opaque colour, in [0, 1]. Alpha is ignored: a
     * translucent colour has no luminance of its own, only the one it ends up with.
     */
    public static double relativeLuminance(int color) {
        return .2126 * channel((color >> 16) & 0xff)
                + .7152 * channel((color >> 8) & 0xff)
                + .0722 * channel(color & 0xff);
    }

    private static double channel(int value) {
        double v = value / 255.0;
        return v <= .03928 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4);
    }

    /** WCAG contrast ratio between two opaque colours, from 1:1 to 21:1. */
    public static double contrastRatio(int first, int second) {
        double a = relativeLuminance(first);
        double b = relativeLuminance(second);
        return a > b ? (a + .05) / (b + .05) : (b + .05) / (a + .05);
    }

    /**
     * {@link #INK} or {@link #CREAM}, whichever will actually be read on top of
     * {@code background}. For a glyph <em>on</em> a coloured fill — a button chip, a
     * cursor badge, a focused row's label.
     *
     * <p>Every one of those places used to assume the fill was light, which held only
     * because the palette happened to be light. It stops holding the moment
     * {@link Comfort#distinctPlayers} is on: Sky becomes a deep teal, and plum ink on
     * deep teal is 2:1 — a letter nobody can make out from ten feet, which would quietly
     * undo the cues that owe the least to colour. Ask, do not assume.
     */
    public static int textOn(int background) {
        return contrastRatio(INK, background) >= contrastRatio(CREAM, background)
                ? INK : CREAM;
    }

    /**
     * The alpha, 0..255, at which a wash of {@code color} laid over {@code background}
     * first reaches {@code minRatio} against that background — the thinnest tint that is
     * still above the perceptual threshold.
     *
     * <p>This exists so that the cursor's crosshair bands are <em>equal</em> rather than
     * merely present. A fixed alpha cannot be: Rose's pink is much darker than Sky's cyan,
     * so the same 40/255 that gave Rose a barely-there 1.09:1 gave Sky less still. Asking
     * for a ratio instead of a number makes the two bands the same strength by
     * construction, whatever the identity colours become — including Sky's deep teal under
     * {@link Comfort#distinctPlayers}, which needs less than half the alpha.
     *
     * <p>Capped at {@code ceiling} so a colour too close to the background can never turn
     * the wash opaque and erase what it is washing over.
     */
    public static int washAlpha(int color, int background, double minRatio, int ceiling) {
        int cap = Math.max(1, Math.min(255, ceiling));
        for (int alpha = 4; alpha < cap; alpha += 2) {
            if (contrastRatio(Draw.blend(background, color, alpha / 255f), background)
                    >= minRatio) {
                return alpha;
            }
        }
        return cap;
    }

    /**
     * {@code color} if it already reads against {@code background}, otherwise the same
     * hue walked toward the light or dark end of the palette until it clears
     * {@code minRatio}.
     *
     * <p>This exists because the identity colours are chosen to look like Rose and Sky,
     * not to be text. Sky's colour-blind-friendly teal is a deep, saturated colour: it is
     * exactly right as a cursor ring and only 2:1 as a name on a dark panel. Rather than
     * every caller remembering that, they ask for a colour that reads.
     */
    public static int readableOn(int color, int background, double minRatio) {
        if (contrastRatio(color, background) >= minRatio) {
            return color;
        }
        int target = relativeLuminance(background) < .18 ? CREAM : INK;
        for (int step = 1; step <= 16; step++) {
            int lifted = Draw.blend(color, target, step / 16f);
            if (contrastRatio(lifted, background) >= minRatio) {
                return lifted;
            }
        }
        return target;
    }
}
