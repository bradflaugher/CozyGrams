package com.cozygrams.tv;

import android.graphics.Typeface;

/**
 * The single source of truth for CozyGrams' colours, type scale, 10-foot spacing and the
 * rate at which the whole interface moves.
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
 * <p><b>Adjacent steps are legibility neighbours, not hierarchy levels.</b> {@link #BODY}
 * over {@link #CAPTION} is 40.5 px over 36 px at 1080p — cap heights of 20.2 and 18.0
 * arcminutes, two arcminutes apart, which nobody resolves from a sofa. A label and its
 * value set one step apart are the same size in the room, so the hierarchy falls entirely
 * to weight and colour. When two levels sit side by side, skip a step: {@link #SUBHEAD}
 * over {@code CAPTION} is 1.33x, which reads instantly.
 *
 * <h2>Light and dark</h2>
 *
 * <p>Four ideas, and everything else is one of them at a different depth:
 *
 * <ul>
 *   <li><b>Paper</b> — {@link #PAPER}, {@link #PAPER_SHADE}, {@link #CLUE_BAND_ALT}. The
 *       board and the things printed on it.</li>
 *   <li><b>Plum</b> — {@link #PANEL}, {@link #PANEL_LIFT}, {@link #INK}, {@link #TILE}.
 *       The furniture and the game's own ink.</li>
 *   <li><b>Candlelight</b> — {@link #GOLD}, {@link #CANDLE}, {@link #CANDLE_DEEP}. One
 *       warm light at three depths, because one gold cannot be seen on both a dark panel
 *       and cream paper (see {@link #CANDLE}).</li>
 *   <li><b>Night</b> — {@link #SHADOW_INK}, {@link #NIGHT}, {@link #PLATE_INK}. Three
 *       near-blacks that used to be five, each with a stated job.</li>
 * </ul>
 *
 * <h2>Declared here, and now drawn</h2>
 *
 * <p>This section used to be a list of tokens that were declared and had no consumer, with
 * the warning that a palette nobody draws with is not a palette but a wish. It has been
 * re-derived by grepping every {@code Theme.} reference in the package, and every entry on
 * that list has since been adopted bar one: the paper and rule colours, the cross tint, the
 * clue band, all three night depths, the pip floor, the cursor rail, the meter track, the
 * toggle and caution colours, both radii, the pill metrics, the menu frame and the seat
 * fills are each read from here by the file that draws them. Two tokens the old list never
 * mentioned turned out to have no consumer at all — {@code CHIP_PAD_X} and
 * {@code CHIP_HEIGHT_EM} — and have been deleted rather than listed.
 *
 * <p>Two are still literals somewhere else, and both are named rather than quietly left:
 *
 * <ul>
 *   <li>{@link #TEXT_SCALE_BIG} — {@code Renderer.draw} still applies LARGER TEXT by telling
 *       {@link #setScreenHeight(float)} the screen is 16% taller, which is the exact fault
 *       that constant's own Javadoc describes. {@link #setTextScale(float)} <em>is</em>
 *       driven now, by {@code CozyGameView.applyTextScale} from the television's own
 *       {@code Configuration.fontScale}; the two settings still do not compose, and
 *       {@code CozyGameView.applyTextScale} carries the one line that would make them.</li>
 *   <li>{@link #MOTION_QUICK_MS} and {@link #MOTION_SETTLE_MS} — {@link #MOTION_WARM_MS} is
 *       read by {@code Effects} and {@code CursorRenderer}; its two shorter siblings are
 *       still spelled as their own millisecond literals in {@code Effects} and
 *       {@code WinScene}. Those are beat sheets rather than stray numbers, so folding them
 *       in is a re-timing job and not a rename.</li>
 * </ul>
 *
 * <p>{@link #MAX_CELL} is a third case and a different one: it has a consumer, but it is not
 * the ceiling it claims to be. See {@code BoardLayout.CELL_MAX}, which binds first at every
 * resolution the game ships on.
 *
 * <h2>Contrast</h2>
 *
 * <p>{@link #contrastRatio(int, int)} is real WCAG maths, not a vibe, and
 * {@link #readableOn(int, int, double)} will nudge an identity colour until it clears a
 * stated ratio. The pairs the game actually draws are asserted in {@code ThemeTest}.
 * Three ratios are named rather than chosen per call site — {@link #FEEDBACK_MIN_RATIO},
 * {@link #CROSS_RATIO} and {@link #CROSS_RATIO_BOLD} — because they are perceptual floors
 * for the board rather than typography rules, and a floor stated once cannot drift.
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
     *
     * <p>This is now the <em>bottom</em> of a panel; see {@link #PANEL_LIFT}.
     */
    public static final int PANEL = rgb(44, 33, 53);

    /**
     * How much lamplight {@link Draw#panel} washes across the top of a panel, in alpha.
     *
     * <p>A panel used to be flat: measured on a 1920x1080 title screen the interior went
     * from (46,34,53) at the top to (49,36,53) at the bottom — three levels across 970 px,
     * which is nothing, while the perimeter stroke was <em>brightest along the bottom
     * edge</em>. The result read as a slab pasted onto a painting rather than as a surface
     * with a lamp above it.
     *
     * <p>12 is not a taste decision, it is the last value that keeps a promise the palette
     * already makes. Two of them bite at almost the same place: Sky's {@link #BLUE} on an
     * opaque lit top runs 7.24:1 at alpha 12 and 7.04:1 at 14, and {@link #SOFT_TEXT} at
     * the lit top over the brightest lamplight in either illustration runs 7.04:1 at 12 and
     * <b>6.94:1 at 13</b> — under the 7:1 this palette holds its primary voice to. So the
     * wash stops at 12: a lift of nine levels, three times the variation the flat panel
     * had, and one step short of costing something.
     */
    public static final int PANEL_LIFT_ALPHA = 12;

    /**
     * The lit top of a panel: {@link #PANEL} under {@link #PANEL_LIFT_ALPHA} of
     * {@link #CREAM}, which is what {@link Draw#panel} washes there. Derived rather than
     * written down so the token and the pixels cannot drift apart — the wash arrives as a
     * dozen one-level composites, so a sampled pixel lands within a level of this.
     */
    public static final int PANEL_LIFT = Draw.blend(PANEL, CREAM, PANEL_LIFT_ALPHA / 255f);

    // ---- The night, at three depths -------------------------------------------------

    /**
     * The colour every soft shadow is cast in — {@link Draw#shadow} and the drop under a
     * cursor plate. The darkest of the three because a shadow is the absence of the room,
     * not a surface of its own.
     */
    public static final int SHADOW_INK = rgb(10, 6, 18);

    /**
     * The full-screen dim: the win celebration's scrim, and any future handover wash.
     *
     * <p>There were four near-blacks in the game — (8,5,16) behind text, (10,6,18) under
     * panels, (16,11,28) over the whole screen at the win — with no shared name and no
     * stated difference. Two of them differed by two levels, which nobody can see and
     * everybody has to maintain.
     */
    public static final int NIGHT = rgb(16, 11, 28);

    /**
     * The dark keyline under every cursor plate and player border. It was spelled out
     * numerically in three places, which is unfortunate for the one colour that makes both
     * cursors equally findable on cream paper: 17:1 against {@link #PAPER}, so the plate's
     * edge is found by value alone whichever player owns it.
     */
    public static final int PLATE_INK = rgb(30, 20, 46);

    /**
     * The dark the illustrated backdrop composites to where the interface floats directly
     * on it with no panel underneath — the cursor's margin tabs, the message ribbon.
     *
     * <p>Sampled behind the tab band on both shipped scenes: (53,62,74) in the garden and
     * (66,49,62) in the room. This is the darker, safer of the two, so
     * {@link #readableOn(int, int, double)} has an honest target instead of guessing at a
     * painting.
     */
    public static final int BACKDROP_REF = rgb(52, 42, 60);

    // ---- Player identity -----------------------------------------------------------

    /** Rose (player one) — the warm side of the palette. */
    public static final int PINK = rgb(255, 122, 158);
    public static final int PINK_DARK = rgb(186, 66, 108);
    public static final int PINK_LIGHT = rgb(255, 186, 205);

    /** Sky (player two) — the cool side of the palette. */
    public static final int BLUE = rgb(112, 202, 224);
    public static final int BLUE_DARK = rgb(38, 122, 152);
    public static final int BLUE_LIGHT = rgb(186, 233, 244);

    // ---- One candle, three depths ---------------------------------------------------

    /**
     * Candle gold as seen <em>on a dark panel</em>: 10.7:1 there, and the right colour for
     * the win card's call to action and the hint chip.
     *
     * <p>It is 1.38:1 on {@link #PAPER} at full opacity, so it cannot be used on the
     * board at all — no alpha exists that would make it visible. That is not a defect in
     * the colour, it is what a light-on-dark accent is; the board's gold is
     * {@link #CANDLE}.
     */
    public static final int GOLD = rgb(255, 209, 128);

    /**
     * The board's gold: the light a candle throws <em>on paper</em>.
     *
     * <p>This lived as a private constant in {@link Effects} while the line-complete wave,
     * the hint and the message ribbon each wanted it, so three files were about to own a
     * copy. It is the only accent in the palette that clears the perceptual floor on both
     * of the board's surfaces at once — 2.37:1 on {@link #PAPER}, 4.18:1 on {@link #TILE}
     * — which is the whole reason feedback drawn in {@link #GOLD} was invisible.
     */
    public static final int CANDLE = rgb(214, 154, 74);

    /**
     * The same candle walked dark enough to be drawn as a <em>line</em> on paper: 3.77:1
     * against {@link #PAPER}, against {@link #CANDLE}'s 2.37:1.
     *
     * <p>The difference matters because a stroke is thin. A fill at 2.37:1 registers
     * across a whole square; a two-pixel sweep has to be well clear of the floor before it
     * is seen from a sofa at all, and {@link #CANDLE} only reaches 1.94:1 once it is
     * washed rather than laid down solid.
     *
     * <p>The critic's audit proposed a fourth gold at rgb(206,140,46) for the same reason.
     * Measured, it is 2.74:1 on paper — 0.37 above {@code CANDLE} and 1.03 below this —
     * so it sits between two colours that already exist and buys nothing except a third
     * way to be wrong. Two depths, one hue family.
     */
    public static final int CANDLE_DEEP = rgb(176, 116, 48);

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
     *
     * <p>There is deliberately no bold counterpart to {@link #TILE_DEEP}. Extra contrast
     * draws every filled square as one flat block — no gloss and no per-square grain — so a
     * shaded body would have nothing to shade; the token existed, was never read by
     * anything, and has been removed rather than left as a colour nobody can see.
     */
    public static final int TILE_BOLD = rgb(44, 46, 62);

    /**
     * The slate a crossed square's wash and its X are both drawn from. It was an inline
     * {@code Color.argb} literal, so the one square state that is <em>only</em> a tint had
     * nothing a test could hold it to.
     */
    public static final int CROSS_TINT = rgb(88, 80, 122);

    /**
     * The five-square checker that helps the eye count along a long row: the paper's own
     * hue taken one step toward candlelight, 1.07:1 against {@link #PAPER}.
     *
     * <p>It replaces {@code blend(PAPER, GRID, .055)}, which was the same strength
     * (1.087:1) but cooled the warmest surface in the game toward lilac. Warm paper with
     * warmer paper on it is the same idea drawn correctly.
     */
    public static final int PAPER_SHADE = rgb(250, 243, 226);

    /**
     * The field behind every other clue lane, 1.21:1 against {@link #PAPER}.
     *
     * <p>At 20x20 neighbouring two-digit column clues have about 4 px of white between
     * them — 3.2 arcminutes at ten feet, which is under the gap the eye needs to split two
     * digit streams. The board cannot spare more paper, so the separation has to come from
     * the ground rather than from the gap. Deliberately louder than {@link #PAPER_SHADE}
     * so the two never read as the same mark.
     */
    public static final int CLUE_BAND_ALT = rgb(238, 229, 210);

    /** The light rule between two squares: 4.3:1 on paper, well over the checker. */
    public static final int RULE_MINOR = rgb(126, 114, 150);

    /** The heavy rule every five squares: 11.2:1 on paper, so the fives are countable. */
    public static final int RULE_MAJOR = rgb(62, 52, 84);

    /** The tile colour for the current contrast setting. */
    public static int tile(boolean highContrast) {
        return highContrast ? TILE_BOLD : TILE;
    }

    // ---- Surfaces and controls -------------------------------------------------------

    /**
     * The empty half of any meter — the shared progress bar, and whatever meter comes
     * next. 3.07:1 on a panel; the bar's own track was 1.61:1, which is a shape you can
     * only find once you already know it is there.
     */
    public static final int TRACK = Draw.blend(PANEL, CREAM, .36f);

    /**
     * The OFF side of a cozy-corner switch. ON is {@link #PINK}; the two used to differ by
     * 1.34:1, so from a sofa the only thing carrying nine settings was the knob's position.
     * This puts them 4.6:1 apart, which survives distance and colour blindness alike.
     */
    public static final int TOGGLE_OFF = rgb(64, 55, 70);

    /**
     * A warm clay-amber for a question that cannot be taken back — "start the story
     * again", "put everything back".
     *
     * <p>Both of those borrowed {@link #PINK_LIGHT}, which is Rose's identity colour: a
     * destructive confirmation should not be wearing a player. Clay is inside the lamplit
     * palette rather than an error red, reads 6.7:1 on a panel, and takes {@link #INK} at
     * 6.8:1 for the word printed on it.
     */
    public static final int CAUTION = rgb(232, 152, 118);

    /**
     * The resting surface of a menu row, as an alpha over whatever panel it sits on.
     *
     * <p>The title screen already had this and said why: a flat wash of cream over plum
     * comes out the colour of a filing cabinet, while a rose-tinted fill under a warmer
     * hairline reads as a card sitting in lamplight. The cozy corner used the filing
     * cabinet. Composited over {@link #PANEL} this is (80,59,79), a 1.51:1 separation —
     * enough to see the row, not enough to compete with the focused one.
     */
    public static final int ROW_REST = Draw.withAlpha(PINK_LIGHT, 44);

    /** The hairline around a resting menu row. */
    public static final int ROW_REST_STROKE = Draw.withAlpha(CREAM, 54);

    /** Familiar Xbox face-button colours, reserved for literal controller prompts. */
    public static final int BUTTON_A = rgb(16, 124, 16);
    // Red and blue are one value-step deeper than the plastic reference hues so cream
    // letters still clear AA contrast from a sofa.
    public static final int BUTTON_B = rgb(205, 25, 45);
    public static final int BUTTON_X = rgb(0, 105, 185);
    public static final int BUTTON_Y = rgb(255, 185, 0);

    /** Lamplight along a panel's top edge and its two upper corners. */
    public static final int PANEL_EDGE_LIGHT = Draw.withAlpha(CREAM, 150);

    /** The shade along a panel's bottom edge, where the light does not reach. */
    public static final int PANEL_EDGE_DARK = Draw.withAlpha(rgb(12, 7, 20), 96);

    /**
     * The all-round hairline that keeps a panel's outline crisp. It was 150 the whole way
     * round, which made the bottom edge the brightest line on the title screen; at 72 the
     * top-edge highlight is the thing that catches the eye and the outline merely exists.
     */
    public static final int PANEL_EDGE_QUIET = Draw.withAlpha(CREAM, 72);

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

    /**
     * What LARGER TEXT multiplies type by.
     *
     * <p>This was 1.16, and it was applied by lying to {@link #setScreenHeight(float)} —
     * which scales <em>everything</em>, so asking for bigger words also grew every padding
     * and stroke, and the board card had to shrink to fit: measured, 797 px down to 759 px.
     * Larger text made the puzzle smaller, which is the opposite of the request.
     *
     * <p>Now that the multiplier reaches only {@link #textSize(float)} it can be what the
     * setting is actually for: 1.30 puts the prose floor at 46.8 px at 1080p, a cap height
     * of 23.4 arcminutes from ten feet, just above the 20–22' band ISO 9241-303 calls
     * comfortable — which is where a setting named "larger" belongs.
     *
     * <p>{@code Renderer.draw} finally applies it this way rather than by inflating the
     * screen height, so the paragraph above is now true of the shipped build. What it cost
     * while it was not: measured at 1920x1080 on the same puzzle, turning LARGER TEXT on
     * moved the 15x15 cell from 43 px to 42 and its clue ink from 31.8 px to 31.1, while
     * every word in the side rail grew. The one setting named for legibility was making the
     * puzzle's own numbers smaller.
     */
    public static final float TEXT_SCALE_BIG = 1.30f;

    /**
     * How much of {@link #textScale()} reaches the board's clue digits — see
     * {@link #clueScale()}.
     *
     * <p>Not all of it, and not none of it. A clue digit is not prose: its size is set by
     * the square it has to sit in, and the column gutter it stacks into is charged to the
     * board's <em>scarce</em> axis, so every point of clue type costs the grid four points
     * of height at 20x20. Passing the full 1.30 through would shrink the squares to pay for
     * the numbers on them.
     *
     * <p>Half of it is what the geometry can absorb. Measured at 1920x1080 with LARGER TEXT
     * on, clue ink goes 47.4 px to 54.5 at 10x10 and 48.0 px to 55.2 at 5x5, while 15x15 and
     * 20x20 — where a two-digit clue is already as wide as its own column and cannot grow
     * without colliding — hold exactly steady instead of shrinking. Never smaller with the
     * setting on than with it off is the promise; larger wherever the squares can hold it.
     */
    public static final float CLUE_TEXT_SHARE = .5f;

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

    /**
     * The corner ramp, in design pixels: panel 30, card 22, chip 14 — each about .7 of the
     * one above. A pill is not on the ramp; a pill's radius is half its own height, which
     * is what makes it a pill.
     *
     * <p>These were three unrelated literals in three files, so the paper card and the
     * co-op rail sat side by side on the same screen with different corners.
     */
    public static final float PANEL_RADIUS = 30f;
    /** The paper board card and the win card's picture frame. */
    public static final float RADIUS_CARD = 22f;
    /** Seat cards, the message ribbon, anything the size of a line of text. */
    public static final float RADIUS_CHIP = 14f;

    /**
     * The two stroke weights the interface is drawn with, in design pixels, and the floors
     * they may never fall below.
     *
     * <p>A hairline is a <em>seen</em> line — a panel's edge, a focus ring, the rule under
     * a heading. A keyline is a line that only has to <em>separate</em> two surfaces, like
     * the dark edge of a cursor plate. The idiom {@code Math.max(1.5f, Theme.scale(2))}
     * appeared five times and {@code Math.max(1f, Theme.scale(1))} twice; both floors are
     * 10-foot rules and are stated once here, in {@link #hairline()} and {@link #keyline()},
     * which is what every one of those sites calls now.
     *
     * <p>Two weights nearby are deliberately <em>not</em> these: the focus ring on a menu row
     * is {@code scale(2.4)} because it is the loudest line on the screen, and the rule inside
     * the co-op rail is {@code scale(1.4)} because it has to be quieter than a panel edge.
     * A named weight is worth having only while everything wearing the name is the same
     * thing.
     */
    public static final float HAIRLINE = 2f;
    public static final float KEYLINE = 1f;

    /**
     * The outer bound on a board square, in design pixels — 156 px at 1080p.
     *
     * <p>Without a cap a 5x5 board spends the whole card on twenty-five squares of 125 px
     * and reads as a spreadsheet. A small puzzle should look like a jewel sitting on paper,
     * not like a big puzzle with the detail removed.
     *
     * <p><b>It is not the cap that binds.</b> This said "the largest a board square is ever
     * drawn" and was not: 104 design pixels resolves to 156 px at 1080p, and no board the
     * game deals has ever asked for a square that large, so the ceiling never once fired.
     * {@code BoardLayout.CELL_MAX} is the same rule at the number it was written for — 70
     * design pixels — and {@code BoardLayout.cellCeiling} takes whichever of the two is
     * tighter, which on every screen the game ships on is that one. This stays because {@code ThemeTest} pins the
     * 156 px figure and because a second, looser bound costs nothing; it is documented as
     * the outer bound so nobody reads it as the answer.
     */
    public static final float MAX_CELL = 104f;

    /**
     * The smallest decorative mark the interface is allowed to draw, in design pixels —
     * 18 px at 1080p, 13 arcminutes from ten feet.
     *
     * <p>The type scale has a floor for words and a floor for glyphs and nothing at all
     * for marks that are neither, which is how the win card's unvisited chapter dots ended
     * up 7 px across — about 5 arcminutes, the threshold at which a <em>familiar</em> shape
     * is merely identifiable. A dot has no familiar shape to fall back on.
     */
    public static final float MIN_PIP_PX = 12f;

    /**
     * Thickness of the crosshair rail the cursor draws along the cell boundaries, in
     * design pixels — 3.3 px at 1080p, 2.4 arcminutes, the width at which a saturated line
     * is reliably detected from ten feet.
     *
     * <p>The rail exists because the crosshair <em>band</em> is punched full of holes by
     * filled tiles exactly when the board is nearly solved, which is exactly when the guide
     * is needed most. A line on the boundary is the part no square can erase.
     */
    public static final float CURSOR_RAIL = 2.2f;

    /**
     * The pill: "press this". Padding is in design pixels; height is in ems of the text
     * inside, so the container grows with LARGER TEXT instead of clipping it. Read by
     * {@code WinScene}, which draws the only pill in the game.
     *
     * <p>This was declared as a pair with a {@code CHIP_PAD_X} / {@code CHIP_HEIGHT_EM}
     * beside it, so that "filled pill = press this, outlined chip = just so you know" would
     * be a rule rather than an accident. The chip half was never read: the legend's chips in
     * {@code HudScene} size themselves to the glyph on them, because a chip there has to
     * come out round for a single character and long enough for the word HOLD, which is a
     * different rule from a fixed padding. Two dead constants stating a rule nothing follows
     * are worse than no rule, so they are gone.
     */
    public static final float PILL_PAD_X = 58f;
    public static final float PILL_HEIGHT_EM = 2.05f;

    /**
     * The menu frame, shared by the title screen and the cozy corner because they are one
     * flow. They were .27–.73 and .255–.745, so the panel jumped 57 px wider when you
     * opened settings — on a television, in the middle of a transition, that reads as the
     * screen having been rebuilt rather than opened.
     */
    public static final float MENU_PANEL_LEFT = .265f;
    public static final float MENU_PANEL_RIGHT = .735f;
    /** How far a menu row is inset from its panel, in design pixels. */
    public static final float MENU_INSET = 26f;

    /** How much of a player's colour a joined seat card is filled with. */
    public static final float SEAT_FILL_JOINED = .23f;
    /** And an empty one, which has to look like an invitation rather than a fault. */
    public static final float SEAT_FILL_OPEN = .09f;

    /**
     * The falloff budget for any stacked glow: at least this many rings, none of them
     * carrying more than {@link #GLOW_STEP_ALPHA_MAX}.
     *
     * <p>The win card's halo was 4 rings at alpha 13 (7 at 17 on the last chapter), which
     * is a step of about 13/255 at every ring boundary — countable contour lines around
     * the one picture the whole game is building toward. A ring that composites at 4/255
     * cannot produce a visible edge on any panel, which is the entire rule.
     * {@link Draw#glow} enforces both halves of it.
     */
    public static final int GLOW_STEPS = 16;
    public static final int GLOW_STEP_ALPHA_MAX = 4;

    /**
     * How dark the win celebration is allowed to get, at its peak and once it has settled.
     *
     * <p>These were 238 and 190. At 238 the screen keeps 7% of the picture: the board, the
     * last square and the fill that just landed are all extinguished before the paper has
     * moved a sixth of the way off the table, and the frame reads as a crash rather than as
     * a curtain. At 168 a third of the room survives, which is enough for the eye to follow
     * the picture rising out of it.
     */
    public static final int SCRIM_PEAK = 168;
    public static final int SCRIM_REST = 120;

    // ---- Motion ----------------------------------------------------------------------

    /**
     * The three durations the whole interface breathes at.
     *
     * <p>There were twenty-odd independent millisecond constants — six in {@link Effects},
     * eleven in {@code WinScene}, a 1900 ms breath in {@code CursorRenderer} — none of
     * which agreed and none of which was wrong on its own. These three name what an
     * animation is <em>for</em>:
     *
     * <ul>
     *   <li>{@link #MOTION_QUICK_MS} — "the press was heard". Must be over inside the
     *       120 ms an input has to be confirmed in, plus its settle.</li>
     *   <li>{@link #MOTION_SETTLE_MS} — "a thing landed and stopped".</li>
     *   <li>{@link #MOTION_WARM_MS} — "take this in": a hint arriving, a line completing.
     *       Long enough to be a moment, short enough not to be a wait.</li>
     * </ul>
     */
    public static final float MOTION_QUICK_MS = 220f;
    public static final float MOTION_SETTLE_MS = 300f;
    public static final float MOTION_WARM_MS = 620f;

    /**
     * The time constant for the cursor's glide, in milliseconds.
     *
     * <p>The glide used to close a fixed fraction of the remaining distance <em>per
     * frame</em>, which makes the cursor's speed a property of the display: identical code
     * moves at half speed on a 30 Hz panel and races on a 120 Hz one. An exponential in
     * milliseconds — {@code 1 - exp(-dt / MOTION_TAU_MS)} — is the same motion everywhere.
     * 42 ms reproduces the 60 Hz feel the game shipped with exactly (90% closed in 97 ms).
     */
    public static final float MOTION_TAU_MS = 42f;

    /**
     * How long a cursor's arrival squash and its margin-tab flash take to settle.
     *
     * <p>Long enough to read as a landing from ten feet; short enough that a held
     * direction at {@link #BOARD_REPEAT_MS} overlaps the previous landing rather than
     * cutting it off, so a held sweep is one continuous motion instead of a stutter.
     */
    public static final float CURSOR_LAND_MS = 150f;

    /**
     * One cadence for every held direction in the game: the pause before a hold starts
     * repeating, the ordinary repeat, and the repeat for a surface where holding is the
     * normal way to travel.
     *
     * <p>The same gesture used to behave three ways — the board had no gate at all and
     * moved once per frame, menus used 130 ms, and the analog stick had a careful 340/165.
     * Holding right on the board crossed a 20-wide grid in a third of a second and fired
     * twenty move sounds; holding right in a menu was sedate.
     *
     * <p>At 300 then 85 a one-second hold advances ten cells, which is a sweep you can
     * stop where you meant to.
     */
    public static final long REPEAT_FIRST_MS = 300;
    public static final long REPEAT_MS = 150;
    public static final long REPEAT_FAST_MS = 85;

    /** A menu list: every step is a decision, so it repeats at the ordinary rate. */
    public static final long MENU_REPEAT_MS = REPEAT_MS;
    /** The board: holding a direction is how you travel, so it repeats at the fast rate. */
    public static final long BOARD_REPEAT_MS = REPEAT_FAST_MS;

    /**
     * When the room starts to hush, and when the hush is complete.
     *
     * <p>The music's fade and any visual idling — a resting cursor settling, the breath
     * coming out of the interface — run off this one clock so the evening dims and quietens
     * together rather than on two unrelated timers. 25 s is longer than any pause for
     * thought; 55 s means someone has actually walked away.
     */
    public static final long IDLE_HUSH_START_MS = 25_000;
    public static final long IDLE_HUSH_FULL_MS = 55_000;

    /**
     * How long a square stays the partner's before the other player's fill may toggle it
     * off. A fairness rule rather than a drawing constant, which is why it belongs beside
     * the other durations instead of inside the input handler.
     */
    public static final long PARTNER_GRACE_MS = 1500;

    /**
     * The floor between two shared-moment celebrations. A moment that can happen twice in
     * ten seconds is not a moment; every future together-beat shares this rate limit
     * rather than inventing its own.
     */
    public static final long TOGETHER_COOLDOWN_MS = 45_000;

    // ---- What the generator promises the layout ---------------------------------------

    /**
     * The most clue groups the generator will ever put in one row, and one column.
     *
     * <p>{@link BoardLayout} used to size its gutters against the worst case someone had
     * happened to observe. Stating the bound here makes it a contract in two directions:
     * the layout reserves exactly this much, and {@code PuzzleGeneratorTest} can fail the
     * day a new subject exceeds it rather than the day a clue runs off the paper.
     *
     * <p>Measured across all 4,608 deck boards and every story chapter, the worst case
     * today is 6 rows and 4 columns. The column bound is set one above what is used
     * because columns are the under-worked axis, and that is where extra picture detail can
     * go without the board card growing.
     */
    public static final int MAX_ROW_CLUE_GROUPS = 6;
    public static final int MAX_COL_CLUE_GROUPS = 5;

    // ---- Perceptual floors for the board ----------------------------------------------

    /**
     * The contrast a mark drawn on the board has to reach against the surface under it
     * before anyone on a sofa can see it happen.
     *
     * <p>Every action pulse in the game was measured between 1.05:1 and 1.37:1 against
     * what it was drawn on — technically present, perceptually absent, which is why the
     * board felt unresponsive despite a great deal of code running. 1.6:1 is the floor;
     * feedback that is meant to be <em>celebrated</em> rather than merely noticed should
     * aim for 2.2:1, which is what {@link #CANDLE} on {@link #PAPER} reaches.
     *
     * <p>Pass it to {@link #washAlpha(int, int, double, int)} rather than picking an alpha:
     * an alpha can only be right for one hue on one background.
     */
    public static final double FEEDBACK_MIN_RATIO = 1.6;

    /**
     * How strongly a crossed square is tinted, as a ratio against {@link #PAPER}.
     *
     * <p>The wash was a fixed alpha of 26, which measures 1.17:1 — statistically the same
     * as the 1.07:1 five-square checker printed underneath it. "Filled, crossed and
     * untouched separate by value" was therefore true of filled and of nothing else.
     * Asking for a ratio keeps the cross clear of the checker whatever either colour
     * becomes.
     */
    public static final double CROSS_RATIO = 1.30;

    /** And with extra contrast on, where a cross should be a decision, not a hint. */
    public static final double CROSS_RATIO_BOLD = 1.75;

    private static Typeface boldFace;
    private static Typeface regularFace;
    private static float screenHeight = 720f;
    private static float textScale = 1f;

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

    /**
     * Sets the LARGER TEXT multiplier. It reaches {@link #textSize(float)} and nothing
     * else — see {@link #TEXT_SCALE_BIG} for what went wrong when it reached everything —
     * and is clamped to [1, 1.5] because the system's own {@code Configuration.fontScale}
     * can be anything at all and a board with 50% larger clue gutters has no room left for
     * a board.
     */
    public static void setTextScale(float scale) {
        textScale = Math.max(1f, Math.min(1.5f, scale));
    }

    /** The current LARGER TEXT multiplier; 1 unless someone has asked for bigger words. */
    public static float textScale() {
        return textScale;
    }

    /**
     * The multiplier the board's clue digits get: {@link #CLUE_TEXT_SHARE} of the one words
     * get.
     *
     * <p>It is applied to the two bounds on a clue that are about <em>taste</em> — the
     * ceiling {@code BoardLayout.CLUE_MAX} and the comfort floor {@link #MIN_READABLE_SP} —
     * and to how much of its own row a digit may fill, but deliberately not to the width a
     * digit has inside its column. That last one is not a preference, it is the point at
     * which two numbers touch, and no setting should be able to ask for illegible.
     */
    public static float clueScale() {
        return 1f + (textScale - 1f) * CLUE_TEXT_SHARE;
    }

    /** Converts a design pixel size measured at 720p into a size for the live screen. */
    public static float scale(float designPixels) {
        return designPixels * screenHeight / 720f;
    }

    /**
     * Scales a text size, never letting words fall below the ten-foot prose floor and
     * applying {@link #textScale()} on top. The floor is scaled too, so LARGER TEXT raises
     * the smallest thing on screen rather than only the things that were already large.
     */
    public static float textSize(float designPixels) {
        return textScale * Math.max(scale(MIN_PROSE_SP), scale(designPixels));
    }

    /** A line meant to be seen: a panel edge, a focus ring, the rule under a heading. */
    public static float hairline() {
        return Math.max(1.5f, scale(HAIRLINE));
    }

    /** A line meant only to separate two surfaces, like a cursor plate's dark edge. */
    public static float keyline() {
        return Math.max(1f, scale(KEYLINE));
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

    /**
     * The playback rate a player's sounds are pitched at — the audible half of the same
     * identity {@link #playerColor(int)} carries, kept beside it so the two cannot drift
     * apart.
     *
     * <p>Rose and Sky were byte-identical, so with two people playing there was no way to
     * hear whose square had just landed. A perfect fifth (2^(7/12), 701.955 cents) is the
     * interval to move by: the score is F major pentatonic, so Rose's F and Sky's C are
     * both in the scale and two simultaneous presses are consonant rather than a clash —
     * which matters, because on a good evening they happen constantly.
     */
    public static float playerPitch(int player) {
        return player == 0 ? 1f : 1.49831f;
    }

    /**
     * The colour of something both players did: Rose and Sky met in the middle.
     *
     * <p>This exact blend was already being recomputed by hand in the tether glow and in
     * the shared progress bar, and the shared-line and shared-square cues have to agree
     * with both. Asked of {@link Comfort} through {@link #playerColor(int)}, so it follows
     * the colour-blind-friendly palette too.
     */
    public static int togetherColor() {
        return Draw.blend(playerColor(0), playerColor(1), .5f);
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
