package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * The game's juice layer: short-lived square pulses and free-flying particles.
 *
 * <p><b>The feedback vocabulary.</b> Every action has its own shape, so two people playing
 * at once can tell from the corner of an eye <em>what</em> just happened without reading
 * anything. A ring for everything would be cheap and illegible.
 *
 * <ul>
 *   <li>{@link Pulse#FILL} — <em>soft settle</em>. A crown ring blooming outward off the
 *       cursor plate, over an edgeless warmth spreading just past the tile's own edge,
 *       190&nbsp;ms. The tile's own arrival is drawn by the renderer from
 *       {@link #squareProgress}.</li>
 *   <li>{@link Pulse#CROSS} — <em>quick scratch</em>. Four diagonal ticks flick outward
 *       past the corners and are gone in 240&nbsp;ms. No ring: a cross is the one action
 *       whose crown is only implied, which is what keeps it from reading as a fill.</li>
 *   <li>{@link Pulse#CLEAR} — <em>quiet undo</em>. A thin outline collapses inward to
 *       nothing, 250&nbsp;ms.</li>
 *   <li>{@link Pulse#HINT} — <em>warm shimmer</em>. A candlelit crown with four slowly
 *       turning glints radiating out from behind the cursor, 620&nbsp;ms.</li>
 *   <li>{@link Pulse#ERROR} — <em>gentle sigh</em>. A crown that swells while it
 *       <em>sinks</em>, over a soft bloom doing the same, 540&nbsp;ms. The sink is the
 *       whole tell: it is what stops a gentle-mode correction looking like the cross it
 *       leaves behind. Never a flash, never a shake.</li>
 *   <li>{@link Pulse#LINE} — <em>travelling wave</em>. A band of candlelight in the gutter
 *       around each square, riding down the line one square behind the last. The caller
 *       emits one per square with a staggered future start time, which is what turns the
 *       crest into a wave.</li>
 * </ul>
 *
 * <p>Each of these peaks inside ~110&nbsp;ms, so an action is confirmed while the thumb is
 * still on the button, and each is gone before it can pile up into visual soup.
 *
 * <p><b>Two passes, because one was not enough.</b> This layer used to be drawn entirely
 * <em>underneath</em> the board, which is right for warmth and wrong for confirmation.
 * Every action fires on the square the acting player's cursor is standing on, and that
 * square is covered at that instant by an opaque cream plate of
 * {@link BoardRenderer#plateHalf half} 0.55 of a cell at 5x5 and 0.70 at 20x20; every
 * pulse's radius was smaller than the plate or barely larger. The confirmation was
 * therefore always drawn under the thing it was confirming — measurably, not arguably:
 * 40&nbsp;ms after a fill the only pixels that had changed anywhere on screen were inside
 * the tile itself.
 *
 * <ul>
 *   <li>{@link #drawPulses} and {@link #drawParticles} are the <em>under</em> pass: the
 *       warmth a player sees a beat later, once the cursor has moved on. Drawn below the
 *       marks and the plates.</li>
 *   <li>{@link #drawPulseCrowns} is the <em>over</em> pass: the ring outside the plate,
 *       drawn above it. Its geometry is expressed as a multiple of the plate's own half
 *       width rather than as a fraction of a cell, so it starts exactly where the occluder
 *       ends at every board size and cannot drift back underneath.</li>
 * </ul>
 *
 * <p>A renderer that draws only the first pass gets the warmth and none of the
 * confirmation. See {@link #drawPulseCrowns} for where the call belongs.
 *
 * <p><b>Rules the layer enforces on itself,</b> rather than leaving them to the caller:
 *
 * <ul>
 *   <li><b>Nothing leaves the paper card.</b> Pulse radii are trimmed to the room left
 *       between the square and the card's edge, and a burst fades out before its own
 *       silhouette can cross onto the wallpaper or the HUD. See {@link #fit} and
 *       {@link #cardFade}.</li>
 *   <li><b>Nothing travels far.</b> A spray thrown by a square is held inside
 *       {@link #BURST_REACH_CELLS} cells of it and drawn no larger than
 *       {@link #BURST_SIZE_CELLS} of a cell, so it can never cover the neighbourhood a
 *       player is reading or match a cursor badge for size.</li>
 *   <li><b>Nothing borrows an identity.</b> A spray thrown by a square is pulled most of
 *       the way to one warm {@link #CANDLE candlelight}, so it is neither Rose's pink nor
 *       Sky's blue and cannot be mistaken for either cursor. The win drift keeps its own
 *       colours: there are no cursors on the win card to confuse it with.</li>
 *   <li><b>Ink that lands on paper is measured, not chosen.</b> See
 *       {@link Theme#FEEDBACK_MIN_RATIO}; every alpha below carries the ratio it was tuned
 *       to, because the previous set was tuned by eye on a monitor at arm's length and
 *       arrived between 1.05:1 and 1.37:1 — visible there, invisible from a sofa.</li>
 *   <li><b>Two of the six pulses belong to the board, not to a player.</b> A hint and a
 *       completed line are things the <em>board</em> does, so they are drawn in the board's
 *       own {@link Theme#CANDLE candlelight} whatever colour the caller asks for. That is
 *       also what retired the long-standing bug where both were emitted in
 *       {@link Theme#GOLD}, which is a light-on-dark accent and tops out at 1.38:1 on cream
 *       paper — no alpha whatsoever could have made either of them visible.</li>
 * </ul>
 *
 * <p><b>Cost.</b> Everything lives in preallocated primitive arrays; nothing here allocates
 * after construction and nothing reads a wall clock or an unseeded random. Particle
 * positions are a closed-form function of their launch time, so there is no per-frame
 * integration state to keep and a frame can be drawn for any instant — which is what makes
 * the still-frame preview harness able to reproduce a moment exactly.
 *
 * <p><b>Comfort.</b> Everything here honours {@link Comfort#calmMotion}: a third of the
 * particles, slower and smaller ones, shorter and shallower pulses, and no re-popping of
 * squares that have already been placed. The one thing calm motion does <em>not</em> do is
 * take the confirmation away — the crown is drawn at full depth however calm the board is,
 * because a player who asked for less movement did not ask to stop being told what they
 * just did. Nothing in this class ever loops forever.
 */
public final class Effects {

    /** What a square pulse means, which decides its shape, colour ramp and length. */
    public enum Pulse {
        FILL, CROSS, CLEAR, HINT, ERROR, LINE
    }

    // ---- Capacity ---------------------------------------------------------------------
    //
    // Worst case for pulses: on a 20x20 board a single mark can finish a row *and* a
    // column, which emits 1 action pulse + 40 staggered LINE pulses. The stagger means the
    // last of them is still on screen 19*32 + 280 = 888 ms after the move — the crest got
    // shorter and the stagger it wants got longer, so the tail grew from 762 ms — and two
    // players doing that at the same time, twice inside a second, is ~164 live pulses. 192
    // covers it, and when the buffer really is full the eviction below drops the least
    // important effect rather than whatever happens to be next in the ring — a decorative
    // LINE crest is never allowed to evict a player's own fill confirmation.
    //
    // Worst case for particles: the win emits 34 rise() calls of 2 = 68 hearts and petals
    // staggered 18 ms apart and living up to ~1.7 s, so all of them coexist. On top of that
    // a fill sprays dots, a hint sparks and a completed line two motes per square — a row
    // and a column at 20x20 is 80 of those, living 620 ms. At four presses a second each,
    // two players add about 40 more inside a dot's 520 ms life. 384 still leaves a margin
    // and costs ~20 KB of arrays.
    private static final int MAX_PULSES = 192;
    private static final int MAX_PARTICLES = 384;

    // ---- Timing (milliseconds) --------------------------------------------------------

    /**
     * A fill has to be confirmed inside 120 ms or the button stops feeling connected to the
     * board. With the ring gone the whole confirmation is the tile's own pop, so the curve
     * that drives it is short enough to have overshot and be settling back by then.
     */
    private static final long FILL_MS = 190;
    private static final long CROSS_MS = 240;
    private static final long CLEAR_MS = 250;

    /**
     * A hint is the one moment in the game that is allowed to be unhurried, so it takes
     * the slowest rung of {@link Theme}'s shared duration ladder — "take this in" — rather
     * than a number of its own. It was 560 ms, which is the same gesture in a hurry.
     */
    private static final long HINT_MS = (long) Theme.MOTION_WARM_MS;

    /** The sigh is the other gesture allowed to be slow; 480 ms was over before it read. */
    private static final long ERROR_MS = 540;

    /**
     * How long one square of a completed line stays lit.
     *
     * <p>Shorter than it looks, and shortened deliberately from 420 ms. The crest is
     * visible (above .35 of its peak) for the first 59% of this span, so the number of
     * squares lit at once is {@code .59 * LINE_MS / stagger} — at 420 ms and the emitter's
     * 18 ms stagger that was 13 squares, which on a 10x10 board is the whole row switching
     * on at once. The class documentation has always claimed this pulse is "one thing
     * travelling"; the arithmetic said otherwise.
     *
     * <p>280 ms gives a lit band of 3.6 squares at the 46 ms stagger the emitter should be
     * using on a 10x10 board, and 3.6 squares reads as a wave. The emitter still owns the
     * stagger — this constant alone cannot fix a wave that is emitted too fast.
     */
    private static final long LINE_MS = 280;

    /** Fraction of {@link #LINE_MS} the crest spends rising. The rest is its tail. */
    private static final float CREST_RISE = .15f;

    /**
     * Calm motion keeps every shape, just briefer, shallower, slower and sparser.
     *
     * <p>There is no life multiplier any more. Calm used to thin a spray to a quarter,
     * shrink what survived to .85, slow it to .6 <em>and</em> cut its life to .8 — four
     * simultaneous cuts to something already drawn at half ink, which together left a
     * player who asked for calm motion with no visible feedback at all. Calm means gentle,
     * not absent: it now thins to a third and slows further, and what is left lives its
     * full span so there is time to see it.
     */
    private static final float CALM_PULSE_TIME = .7f;
    private static final float CALM_PULSE_DEPTH = .55f;
    private static final float CALM_SPEED = .45f;
    private static final float CALM_SIZE = .85f;

    /**
     * Where in {@link #squareProgress}'s curve a line crest starts a square from.
     *
     * <p>A crest runs over squares that are <em>already</em> placed, so it is mapped into
     * the tail of the curve rather than the whole of it: {@code easeOutBack(.55)} onward is
     * a swell of about four percent and a settle, which is a ripple through the row instead
     * of forty tiles collapsing to half size and springing back.
     */
    private static final float LINE_RIPPLE_FLOOR = .55f;

    /**
     * And where calm motion starts every other kind from. Enough of the pop survives to say
     * "that press landed" — the confirmation is the one thing calm motion never takes away —
     * without the square leaving its own footprint.
     */
    private static final float CALM_POP_FLOOR = .72f;

    // ---- Living underneath the board -----------------------------------------------------

    /**
     * How much of its ink a shape keeps when its whole visible extent is <em>inside</em>
     * the half-cell footprint a tile, a cross or a cursor plate covers. That ink really is
     * buried and should stay quiet, or it reads as a second mark showing through the first.
     *
     * <p>Only one shape is in that position — the hint's bright core.
     */
    private static final float UNDER_MARK_ALPHA = .55f;

    /**
     * And how much a shape keeps that reaches past the footprint, where nothing is going to
     * cover it. All of it.
     *
     * <p>This was one constant, .5, applied to everything, and it was the single most
     * expensive line in the class. A settle bloom runs from .30 to .52 of a cell and a mark
     * is .445, so <em>every pixel of it that anybody can ever see</em> is outside the
     * footprint — and all of them were being halved to protect pixels that were already
     * covered. The same was true of the sigh, the undo outline and the cross flick.
     * Composited over paper they measured 1.175:1, 1.196:1 and 1.311:1.
     */
    private static final float OPEN_ALPHA = 1f;

    /**
     * How far a particle has to travel from the square that threw it before it counts as
     * being in the open. Half a cell is the mark's own half-width, so the ramp is exactly
     * "as far as the tile reaches".
     */
    private static final float OPEN_FROM_CELLS = .55f;

    /**
     * How far the ink of an under-mark shape is walked toward {@link Theme#INK}.
     *
     * <p>These shapes land on cream paper, and the two identity colours are chosen to be
     * seen against a dark tile and against each other, not against paper: Rose's pink is
     * 2.38:1 there and Sky's cyan 1.81:1, which is the whole reason a wash of either was
     * unreadable at any alpha a soft bloom can afford. A .30 pull takes them to 3.96:1 and
     * 3.14:1 while leaving the hue plainly rose and plainly sky — (190,95,127) and
     * (90,151,174). It is also the more faithful picture: a card pressed onto paper
     * <em>darkens</em> the paper around it.
     */
    private static final float PAPER_INK_PULL = .30f;

    // ---- Measured feedback alphas ----------------------------------------------------
    //
    // Every peak alpha the layer uses, in one place, because they are one decision. Each
    // was chosen by compositing the ink it is drawn with over Theme.PAPER — and, where the
    // shape also crosses a placed square, over Theme.TILE — and measuring the real WCAG
    // ratio against Theme.FEEDBACK_MIN_RATIO. The set they replace was chosen by eye at a
    // desk and landed between 1.05:1 and 1.37:1, which is visible from a chair and gone
    // from a sofa.
    //
    // Package-private, and only so that EffectsTest can measure them. Nothing else reads
    // them; the point is that the next person to retune one has a test that tells them what
    // they have just cost.

    static final int SETTLE_INK_ALPHA = 150;
    static final int SCRATCH_INK_ALPHA = 235;
    static final int UNDO_INK_ALPHA = 190;
    static final int SHIMMER_HALO_ALPHA = 150;
    static final int SHIMMER_CORE_ALPHA = 230;
    static final int SIGH_INK_ALPHA = 150;
    static final int CREST_BAND_ALPHA = 185;
    static final int CREST_EDGE_ALPHA = 235;
    static final int CROWN_FILL_ALPHA = 190;
    static final int CROWN_CROSS_ALPHA = 215;
    static final int CROWN_UNDO_ALPHA = 165;
    static final int CROWN_HINT_ALPHA = 180;
    static final int CROWN_GLINT_ALPHA = 225;
    static final int CROWN_SIGH_ALPHA = 170;

    /**
     * No spray a square throws may travel further than this many cells from it. Tightened
     * from 1.5: a cluster that stays near the square it belongs to reads as one event, and
     * the same ink spread over a wider circle is a fainter one.
     */
    private static final float BURST_REACH_CELLS = 1.15f;

    /**
     * Nor be drawn wider than this fraction of a cell. A cursor badge is about .30 of a
     * cell across at 10x10 and never smaller than 33 px; .21 keeps a particle to two thirds
     * of that, which is the invariant this number exists for.
     *
     * <p>It was .16, which on a 20x20 board is 5.6 px — a dot four arcminutes across from
     * ten feet. Note that this is a ceiling, not the size: {@link #particleSize} decides
     * that, and on the boards where a spray was invisible it was the per-shape size that
     * was binding, not this cap.
     */
    private static final float BURST_SIZE_CELLS = .21f;

    /**
     * The board's own gold, as {@link Theme#CANDLE}. The definition moved to {@link Theme}
     * once the wave, the hint and the message ribbon all wanted it; the name is kept here
     * because "the colour a square's spray is pulled to" is a fact about this class.
     */
    public static final int CANDLE = Theme.CANDLE;

    /** How far a board burst is pulled toward {@link #CANDLE}; the rest is the caller's. */
    private static final float CANDLE_PULL = .72f;

    // ---- The crown ------------------------------------------------------------------------
    //
    // The over pass. Every number here is a multiple of BoardRenderer.plateHalf(cell) — the
    // half width of the cream plate the acting player's cursor is standing on — rather than
    // a fraction of a cell. That is the whole point: measured at 1080p the plate is 0.550
    // of a cell at 5x5, 0.601 at 10x10, 0.654 at 15x15 and 0.702 at 20x20 — it grows by a
    // quarter of a cell across the board sizes the game ships — so a crown expressed in
    // cells is under the plate on one board and clear of it on another, which is exactly
    // how the old radii drifted underneath. Expressed against the occluder it starts one
    // plate-edge outside it everywhere, forever, and the ring it opens is a steady .24 to
    // .30 of a cell wide at every size.

    /** Where a crown starts: just clear of the plate, never on it. */
    static final float CROWN_IN = 1.02f;

    /** How far a crown's colour is lifted toward cream. See {@link #crownInk}. */
    static final float CROWN_INK_LIFT = .24f;

    /**
     * The keyline under every crown, and the fraction of the crown's own alpha it takes.
     *
     * <p>{@code CursorRenderer.drawBorder} already had to solve this exact problem: Sky's
     * cyan is 1.8:1 on cream and Rose's pink 2.4:1, which is fine as identity and useless
     * as an edge. A dark line under a light one belongs to neither player and reads on both
     * of the board's surfaces, so a crown needs one colour rather than two. The same
     * (22, 15, 38) is used here so the two shapes look like one interface.
     */
    static final int CROWN_KEYLINE = 0xff160F26;
    static final float CROWN_KEYLINE_ALPHA = .68f;

    /**
     * And the keyline candlelight gets: none.
     *
     * <p>{@link Theme#CANDLE} is the one ink in the palette already above the floor on both
     * of the board's surfaces — 2.37:1 on paper, 4.18:1 on a tile — so a dark line under it
     * buys nothing and costs a great deal. Rendered, a keylined candle crown reads as a
     * khaki reticle drawn on the paper rather than as warmth landing on it, which is
     * exactly the wrong feeling for the one moment the game offers help.
     */
    private static final float CROWN_NO_KEYLINE = 0f;

    /**
     * How much wider the keyline is drawn than the colour laid over it.
     *
     * <p>1.6, not 2. Both read, but at 2 the dark rim is as wide as the colour between it
     * and the crown stops saying whose it is: recorded, a Rose fill on the rose guide band
     * came out as a grey ripple. At 1.6 the rim is 30% of the stroke on each side, which is
     * the same proportion {@code CursorRenderer.drawBorder} uses for the same reason.
     */
    private static final float CROWN_KEYLINE_WIDEN = 1.6f;

    // ---- The win drift ------------------------------------------------------------------
    //
    // These live here rather than at the call site because two places have to agree about
    // them — the emitter in CozyGameView and the test that asserts the celebration is over
    // when the win card claims to be still. When they were written out twice they drifted
    // apart, and the screen went on moving for seconds after everything else had settled.

    /** How many pairs of petals and hearts the win throws up. */
    public static final int WIN_DRIFT_EMISSIONS = 34;
    /** Gap between one pair leaving and the next, in milliseconds. */
    public static final long WIN_DRIFT_STAGGER_MS = 18;
    /**
     * Nominal life of a drifting particle, scattered by {@link #rise} to 0.85–1.15 of
     * this.
     *
     * <p>Measured rather than guessed: with the 18 ms stagger the last pair leaves at
     * 594 ms, so the drift is thinning to 26 of 68 by 1.5 s and the final particle dies
     * around 1.9 s — just after the win card itself goes still at about 1.4 s, and well
     * inside the two seconds its own documentation promises. At 1200 ms it was 40 of 68
     * still in the air at 1.5 s, which read as a screen that would not settle.
     */
    public static final long WIN_DRIFT_LIFE_MS = 1100;

    // ---- The landing tile ------------------------------------------------------------------

    /**
     * The size a freshly marked tile is drawn at the instant it lands, as a fraction of its
     * settled size. See {@link #squareProgress} for the mapping the renderer applies.
     */
    public static final float SETTLE_SCALE = .55f;

    /**
     * How far a freshly marked tile is lifted toward {@link Theme#CREAM} at the instant it
     * lands, 0..1. This is the other half of the confirmation that used to be a ring.
     */
    public static final float SETTLE_LIFT = .55f;

    // ---- Particle shapes ---------------------------------------------------------------

    public static final int SHAPE_DOT = 0;
    public static final int SHAPE_HEART = 1;
    public static final int SHAPE_SPARK = 2;
    public static final int SHAPE_PETAL = 3;
    /** Warmth lifting off finished work: a slow rising mote, for a completed line. */
    public static final int SHAPE_MOTE = 4;

    private static final int SHAPE_COUNT = 5;

    /**
     * Per-shape physics, so nothing moves like anything else. Gravity is in design pixels
     * per second squared (negative lifts), drag is a linear damping coefficient in 1/s, the
     * size is a half-width in design pixels and the life is the default span of a burst.
     *
     * <ul>
     *   <li>Dots fall quickly and are damped hard — a spray that settles back into the
     *       square it came from.</li>
     *   <li>Hearts are buoyant: gravity lifts them and drag is almost absent, so they keep
     *       rising and leave the screen instead of piling up at the bottom.</li>
     *   <li>Sparks are fast and very short: heavy drag stops them almost where they land,
     *       which reads as a flash rather than a throw.</li>
     *   <li>Petals fall slowly and flutter — see the lateral sway and edge-on squash in
     *       {@link #drawParticles}.</li>
     *   <li>Motes lift gently and are damped in the middle: they leave the square and stop,
     *       which is what makes a finished line look warmed rather than celebrated.</li>
     * </ul>
     *
     * <p>The two board sizes went up — a dot from 4.2 to 6.0 and a spark from 5.5 to 7.0.
     * The cap in {@link #BURST_SIZE_CELLS} was widely believed to be what kept a spray
     * small; measured, it never bound at 10x10 at all, where a dot came out 3.4–5.0 px
     * across against a cap of 8.8. These are the numbers that were actually deciding it.
     */
    private static final float[] SHAPE_GRAVITY = {620f, -46f, 60f, 150f, -90f};
    private static final float[] SHAPE_DRAG = {2.4f, .5f, 4.2f, 1.5f, 1.8f};
    private static final float[] SHAPE_SIZE = {6f, 9.5f, 7f, 8f, 5.5f};
    private static final long[] SHAPE_LIFE = {520L, 1150L, 520L, 1500L, 620L};
    private static final int[] SHAPE_ALPHA = {225, 195, 255, 175, 210};

    /**
     * Celebration drift overrides gravity with one gentle lift for every shape, because
     * "rise" has to mean rise: a confetti dot under its own 620 px/s² would be back off the
     * bottom of the screen before anyone saw it. Drag still differs per shape, so hearts
     * climb furthest, petals follow and dots hang low — which reads as depth.
     */
    private static final float CELEBRATION_GRAVITY = -34f;

    // ---- Square pulses ------------------------------------------------------------------

    private final Pulse[] pulseKind = new Pulse[MAX_PULSES];
    private final int[] pulseX = new int[MAX_PULSES];
    private final int[] pulseY = new int[MAX_PULSES];
    private final int[] pulseColor = new int[MAX_PULSES];
    private final long[] pulseStart = new long[MAX_PULSES];
    private final long[] pulseLength = new long[MAX_PULSES];
    private int pulseCursor;

    // ---- Particles ------------------------------------------------------------------------

    /** Launch position in pixels — or, for {@link #FLAG_LANE} particles, a 0..1 lane. */
    private final float[] partX = new float[MAX_PARTICLES];
    private final float[] partY = new float[MAX_PARTICLES];
    private final float[] partVx = new float[MAX_PARTICLES];
    private final float[] partVy = new float[MAX_PARTICLES];
    private final float[] partSize = new float[MAX_PARTICLES];
    private final float[] partSpin = new float[MAX_PARTICLES];
    /** Phase offset for flutter and sway, so no two particles wobble in step. */
    private final float[] partPhase = new float[MAX_PARTICLES];
    private final int[] partColor = new int[MAX_PARTICLES];
    private final byte[] partShape = new byte[MAX_PARTICLES];
    private final byte[] partFlags = new byte[MAX_PARTICLES];
    private final long[] partStart = new long[MAX_PARTICLES];
    private final long[] partLife = new long[MAX_PARTICLES];
    private int partCursor;

    /** The particle's x is a 0..1 lane resolved against the reading column at draw time. */
    private static final byte FLAG_LANE = 1;

    /**
     * Which lane the next celebration particle takes. Stepping a golden-ratio sequence
     * spreads successive particles evenly instead of clustering them the way independent
     * random draws do — that clustering is exactly what used to pile the win hearts into
     * one corner. Reset by {@link #clear()} so every puzzle starts from the same place.
     */
    private int lane;

    /** How many celebration particles have been offered, for calm-motion thinning. */
    private int riseOffered;

    // ---- Reading area ---------------------------------------------------------------------

    /**
     * The horizontal band, as a fraction of the viewport, where copy is drawn. Celebration
     * drift is placed in the gutters either side of it, so hearts frame the win card rather
     * than swimming through "121 cozy moves, made together".
     *
     * <p>The default leaves the outer fifth of the screen either side, which is the open
     * air the win card is composed around and is clear of copy in every layout the win
     * screen has had. A scene that knows better should say so with
     * {@link #setReadingColumn} before it draws.
     */
    private float readLeft = .19f;
    private float readRight = .81f;

    /**
     * Whether a reading column is being kept clear at all. A scene whose copy is drawn
     * <em>after</em> the drift, on an opaque panel, does not need one: a heart passing
     * behind the thing the players made reads as depth, and draw order already protects the
     * words. {@link #clearReadingColumn} is how such a scene says so, and it is the
     * difference between a celebration that has the screen and one squeezed into two
     * gutters. See {@link #laneToScreen}.
     */
    private boolean readingColumn = true;

    /**
     * Width of the surface last drawn into, so a lane can be resolved into a pixel column.
     * Lanes are resolved at draw time rather than at emission precisely so that this never
     * has to be known in advance — the default only matters for a celebration emitted
     * before the very first frame.
     */
    private float viewWidth = 1920f;

    // ---- The paper card ----------------------------------------------------------------------

    /**
     * The card board effects are confined to, taken from the last {@link BoardLayout} this
     * instance was handed. It is remembered rather than passed to every call because the
     * two entry points that need it — {@link #drawPulses} and {@link #drawParticles} — are
     * made by two different renderers, and a scene that has no board simply never sets it.
     */
    private float cardLeft;
    private float cardTop;
    private float cardRight;
    private float cardBottom;
    private float cardCell;
    private boolean cardKnown;

    // ---- Shared geometry -------------------------------------------------------------------

    /**
     * Unit outlines built once and stamped through the canvas transform, so drawing a heart
     * or a petal costs one transform and one path fill and never rebuilds geometry.
     *
     * <p>A heart made of two circles and a triangle loses both its notch and its point once
     * it is only twenty pixels across; these are real curves, with a concave cusp at the top
     * and a sharp tip at the bottom, so the silhouette survives at any size.
     */
    private final Path unitHeart = new Path();
    private final Path unitPetal = new Path();
    private final Path unitGlint = new Path();

    /** The end of the latest live effect, so {@link #busy} is a single comparison. */
    private long busyUntil = Long.MIN_VALUE;

    public Effects() {
        buildUnitHeart(unitHeart);
        buildUnitPetal(unitPetal);
        buildUnitGlint(unitGlint);
    }

    // ---- Emitting ---------------------------------------------------------------------------

    /** Emits a pulse with the natural length for its kind. */
    public void pulse(Pulse kind, int x, int y, int color, long now) {
        pulse(kind, x, y, color, now, naturalLength(kind));
    }

    /**
     * Emits a pulse of an explicit length. {@code now} may be in the future — that is how
     * a completed line staggers one crest per square into a travelling wave.
     */
    public void pulse(Pulse kind, int x, int y, int color, long now, long durationMs) {
        long length = Math.max(60L, (long) (durationMs
                * (Comfort.get().calmMotion ? CALM_PULSE_TIME : 1f)));
        int slot = allocPulse(rank(kind), now);
        if (slot < 0) {
            return;
        }
        pulseKind[slot] = kind;
        pulseX[slot] = x;
        pulseY[slot] = y;
        pulseColor[slot] = color;
        pulseStart[slot] = now;
        pulseLength[slot] = length;
        keepBusyUntil(now + length);
    }

    /**
     * Sprays particles out of a point on screen. {@code spread} is the initial speed in
     * pixels per second; how the spray then moves is decided by the shape, not by one
     * shared gravity — see {@link #SHAPE_GRAVITY}.
     *
     * <p>The spray is reproducible: everything random about it comes from a seed derived
     * from the arguments alone ({@link #seedOf}), so the same call always produces the same
     * spray no matter what else has been emitted before it.
     *
     * <p>{@code color} is a request rather than an instruction. A spray drawn on the board
     * is pulled most of the way to {@link #CANDLE}, because a saturated pink one landing
     * two cells from Rose's badge is read as more of Rose rather than as feedback. Only the
     * warmth of the caller's colour survives.
     */
    public void burst(float cx, float cy, int count, float spread, int color, int shape,
                      long now) {
        int kind = safeShape(shape);
        boolean calm = Comfort.get().calmMotion;
        int seed = seedOf(now, cx, cy, count, kind);
        float speedScale = calm ? CALM_SPEED : 1f;
        long life = SHAPE_LIFE[kind];

        for (int i = 0; i < count; i++) {
            // One in three rather than one in four. At a quarter a 7-dot fill became two
            // dots and a 10-spark hint three, which together with the old half-strength ink
            // was indistinguishable from no feedback at all.
            if (calm && i % 3 != 0) {
                continue;
            }
            // Even angular spacing plus a bounded jitter: a ring that is regular enough to
            // read as one event but never looks machine-stamped.
            float angle = (float) (Math.PI * 2 * i / Math.max(1, count))
                    + (noise(seed, i, 0) - .5f) * 1.1f;
            float speed = spread * (.55f + noise(seed, i, 1) * .5f) * speedScale;
            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            float vx = dx * speed;
            float vy = dy * speed;
            if (kind == SHAPE_HEART) {
                // Hearts are buoyant, so a heart burst is a fountain rather than a ring.
                vy = vy * .72f - speed * .45f;
            }
            // Launched from the square itself, with no step off the centre. The step used
            // to be a fraction of the *spread*, which is a speed: a fast burst therefore
            // started a cell and a half out before it had moved at all, and spent most of
            // its travel allowance standing still. Drag alone separates the spray, and the
            // reach it separates over is the one thing that is now bounded.
            emit(cx, cy, vx, vy,
                    particleSize(kind, spread, noise(seed, i, 2)), color, kind,
                    now, (long) (life * (.8f + noise(seed, i, 3) * .45f)), (byte) 0,
                    noise(seed, i, 4));
        }
    }

    /**
     * The celebration drift: particles that rise past the win card and off the top.
     *
     * <p>Horizontal placement is <em>not</em> taken from {@code cx}. Celebration particles
     * are laid into the gutters either side of the reading column (see
     * {@link #setReadingColumn}), evenly, one golden-ratio step apart, so the drift frames
     * the card instead of drifting through the copy on it. {@code cx} still seeds the
     * spray, so different emitters still differ.
     */
    public void rise(float cx, float cy, int count, float spread, int color, int shape,
                     long now, long lifeMs) {
        int kind = safeShape(shape);
        boolean calm = Comfort.get().calmMotion;
        int seed = seedOf(now, cx, cy, count, kind);
        float speedScale = calm ? CALM_SPEED : 1f;

        for (int i = 0; i < count; i++) {
            // Calm motion thins the drift globally rather than per call: the caller emits
            // 34 pairs, so a per-call rule would round every pair up to one and thin
            // nothing at all.
            boolean keep = !calm || riseOffered % 3 == 0;
            riseOffered++;
            if (!keep) {
                continue;
            }
            float laneValue = nextLane();
            float speed = spread * (.85f + noise(seed, i, 0) * .5f) * speedScale;
            float drift = (noise(seed, i, 1) - .5f) * spread * .18f * speedScale;
            // The launch height is scattered off the caller's own scale rather than off the
            // viewport, so an emission never depends on whether a frame has been drawn yet.
            emit(laneValue, cy + (noise(seed, i, 2) - .5f) * spread * .4f, drift, -speed,
                    particleSize(kind, spread, noise(seed, i, 3)), color, kind, now,
                    // Deliberately not shortened by calm motion: the celebration is a
                    // fixed moment, and cutting it short would leave the card sitting
                    // there with nothing happening. Calm makes it sparser and slower,
                    // not briefer.
                    (long) (lifeMs * (.85f + noise(seed, i, 4) * .3f)),
                    FLAG_LANE, noise(seed, i, 5));
        }
    }

    private void emit(float x, float y, float vx, float vy, float size, int color, int shape,
                      long now, long lifeMs, byte flags, float phase) {
        long life = Math.max(80L, lifeMs);
        int slot = allocParticle(shapeRank(shape), now);
        if (slot < 0) {
            return;
        }
        partX[slot] = x;
        partY[slot] = y;
        partVx[slot] = vx;
        partVy[slot] = vy;
        partSize[slot] = size;
        partSpin[slot] = (phase - .5f) * (shape == SHAPE_PETAL ? 260f : 90f);
        partPhase[slot] = phase * 6.2832f;
        partColor[slot] = color;
        partShape[slot] = (byte) shape;
        partFlags[slot] = flags;
        partStart[slot] = now;
        partLife[slot] = life;
        keepBusyUntil(now + life);
    }

    /**
     * A particle's half-width. It is capped at a comfortable on-screen size rather than
     * scaled straight off the launch speed, which is what used to turn a fast celebration
     * burst into hundred-pixel blobs; small boards still get small particles because the
     * cap is taken together with a fraction of the spread.
     *
     * <p>That fraction is .08 rather than .06 for one measured reason: a board burst is
     * emitted at {@code cell * 2.4}, so on a 20x20 board the spread term was 5.0 px and it,
     * not the per-shape ceiling, was deciding the size of every dot on the smallest cells
     * in the game. At .08 the ceiling binds on every board from 20x20 up, which is what
     * makes a spray the same apparent size wherever it is thrown. The celebration is
     * emitted at 324 px/s and was never anywhere near this term.
     */
    private float particleSize(int shape, float spread, float jitter) {
        float ceiling = Theme.scale(SHAPE_SIZE[shape]);
        float fromSpread = Math.abs(spread) * .08f;
        float size = Math.min(ceiling, fromSpread) * (.82f + jitter * .36f);
        if (Comfort.get().calmMotion) {
            size *= CALM_SIZE;
        }
        return Math.max(1.2f, size);
    }

    /** Where the next celebration particle sits across the screen, as a 0..1 lane. */
    private float nextLane() {
        lane++;
        // 0.618… steps never repeat and never clump: successive particles land as far from
        // each other as the sequence allows.
        float value = lane * .6180339887f;
        return value - (float) Math.floor(value);
    }

    /** Drops every live effect, e.g. when a new puzzle is dealt. */
    public void clear() {
        for (int i = 0; i < MAX_PULSES; i++) {
            pulseLength[i] = 0;
            pulseKind[i] = null;
        }
        for (int i = 0; i < MAX_PARTICLES; i++) {
            partLife[i] = 0;
        }
        pulseCursor = 0;
        partCursor = 0;
        lane = 0;
        riseOffered = 0;
        busyUntil = Long.MIN_VALUE;
        cardKnown = false;
    }

    // ---- Staying on the card ------------------------------------------------------------

    /** Remembers the paper card, so everything the board throws can be held inside it. */
    private void noteBoard(BoardLayout board) {
        cardLeft = board.cardLeft();
        cardTop = board.cardTop();
        cardRight = board.cardRight();
        cardBottom = board.cardBottom();
        cardCell = board.cell;
        cardKnown = true;
    }

    /**
     * A radius trimmed so the shape drawn with it cannot reach off the paper card. Squares
     * on the outside rank are half a cell from the board's edge and the card adds only a
     * little padding beyond that, so an unbounded bloom on a 5x5 board lands on the
     * wallpaper — which is where a piece of board feedback stops being board feedback.
     */
    private float fit(float cx, float cy, float radius) {
        if (!cardKnown) {
            return radius;
        }
        float room = Math.min(Math.min(cx - cardLeft, cardRight - cx),
                Math.min(cy - cardTop, cardBottom - cy));
        return Math.min(radius, Math.max(0, room));
    }

    /**
     * How much of a particle survives at this position, 1 well inside the card and 0 by the
     * time its own silhouette would touch the edge.
     *
     * <p>A soft edge rather than a hard cull, and worked out per particle rather than with
     * a canvas clip: the clip stack is one more piece of state for every scene to get
     * right, and a particle that simply vanishes at a boundary reads as a glitch.
     */
    private float cardFade(float x, float y, float size) {
        float inset = Math.min(Math.min(x - cardLeft, cardRight - x),
                Math.min(y - cardTop, cardBottom - y));
        return Draw.clamp01((inset - size) / Math.max(1f, size * 2f));
    }

    /** The board's own colour for a spray: the caller's warmth over one candlelight. */
    private static int boardTint(int color) {
        return Draw.blend(color, CANDLE, CANDLE_PULL);
    }

    /**
     * The caller's colour walked deep enough to be seen as a wash on cream paper, keeping
     * its hue so it still says whose it was. See {@link #PAPER_INK_PULL}.
     */
    static int paperInk(int color) {
        return Draw.blend(color, Theme.INK, PAPER_INK_PULL);
    }

    /**
     * And the opposite walk, for a crown.
     *
     * <p>A crown is keylined, and the keyline is 2.9–4.4:1 on paper, so the colour on top
     * of it never has to carry the paper case — it only has to carry the <em>tile</em>
     * case, where the surface is dark and the light end of the palette is what reads. The
     * identity colours are already light enough (2.4–4.1:1 on a tile). The one that is not
     * is the deep rose a gentle-mode correction is emitted in, which measures 1.41:1 there;
     * a .24 lift takes it to 1.91:1 and, as it happens, makes the sigh dustier and softer,
     * which is the right direction for the one shape in the game that says "not that one".
     */
    static int crownInk(int color) {
        return Draw.blend(color, Theme.CREAM, CROWN_INK_LIFT);
    }

    /**
     * Reserves the horizontal band, in fractions of the viewport width, where a scene is
     * about to draw copy. Celebration drift keeps out of it. The default is the win card's
     * own column; a scene with a different layout should say so before it draws.
     */
    public void setReadingColumn(float leftFraction, float rightFraction) {
        float left = Draw.clamp01(Math.min(leftFraction, rightFraction));
        float right = Draw.clamp01(Math.max(leftFraction, rightFraction));
        readLeft = left;
        readRight = Math.max(right, left + .05f);
        readingColumn = true;
    }

    /**
     * Gives the drift the whole safe width, for a scene that draws its copy on an opaque
     * panel after the particles.
     *
     * <p>Worth having as its own call rather than as {@code setReadingColumn(.5f, .5f)}:
     * the reserved band is clamped to a minimum width, so there is no pair of fractions
     * that means "none".
     */
    public void clearReadingColumn() {
        readingColumn = false;
    }

    // ---- Slot allocation ---------------------------------------------------------------------

    /**
     * How much an effect matters when the buffer is full. A player's own confirmation
     * outranks a decorative line crest, so a twenty-square wave can never wipe out the fill
     * that started it.
     */
    private static int rank(Pulse kind) {
        switch (kind) {
            case ERROR:
                return 3;
            case FILL:
            case CROSS:
            case CLEAR:
            case HINT:
                return 2;
            default:
                return 0;
        }
    }

    /** Direct action feedback outranks celebration confetti for the same reason. */
    private static int shapeRank(int shape) {
        return shape == SHAPE_HEART || shape == SHAPE_PETAL ? 1 : 2;
    }

    /**
     * Finds a slot for a new pulse: the first expired one, or failing that the least
     * important live one. Returns -1 when everything live matters more than the newcomer,
     * in which case the newcomer is the thing that gets dropped.
     */
    private int allocPulse(int priority, long now) {
        int victim = -1;
        int victimRank = Integer.MAX_VALUE;
        long victimEnd = Long.MAX_VALUE;
        for (int probe = 0; probe < MAX_PULSES; probe++) {
            int i = pulseCursor + probe;
            if (i >= MAX_PULSES) {
                i -= MAX_PULSES;
            }
            if (pulseLength[i] <= 0 || now - pulseStart[i] >= pulseLength[i]) {
                pulseCursor = nextSlot(i, MAX_PULSES);
                return i;
            }
            int liveRank = rank(pulseKind[i]);
            long end = pulseStart[i] + pulseLength[i];
            if (liveRank < victimRank || (liveRank == victimRank && end < victimEnd)) {
                victim = i;
                victimRank = liveRank;
                victimEnd = end;
            }
        }
        if (victim < 0 || victimRank > priority) {
            return -1;
        }
        pulseCursor = nextSlot(victim, MAX_PULSES);
        return victim;
    }

    private int allocParticle(int priority, long now) {
        int victim = -1;
        int victimRank = Integer.MAX_VALUE;
        long victimEnd = Long.MAX_VALUE;
        for (int probe = 0; probe < MAX_PARTICLES; probe++) {
            int i = partCursor + probe;
            if (i >= MAX_PARTICLES) {
                i -= MAX_PARTICLES;
            }
            if (partLife[i] <= 0 || now - partStart[i] >= partLife[i]) {
                partCursor = nextSlot(i, MAX_PARTICLES);
                return i;
            }
            int liveRank = shapeRank(partShape[i]);
            long end = partStart[i] + partLife[i];
            if (liveRank < victimRank || (liveRank == victimRank && end < victimEnd)) {
                victim = i;
                victimRank = liveRank;
                victimEnd = end;
            }
        }
        if (victim < 0 || victimRank > priority) {
            return -1;
        }
        partCursor = nextSlot(victim, MAX_PARTICLES);
        return victim;
    }

    /**
     * Where a ring buffer's cursor goes after it has just handed out {@code index}.
     *
     * <p>Written out four times as {@code index + 1 >= size ? 0 : index + 1} — twice for
     * pulses and twice for particles, once on the expired-slot path and once on the
     * eviction path. A modulo would read better still, but this is the form the buffers
     * have always used and it costs no division; what it lacked was a name.
     */
    private static int nextSlot(int index, int size) {
        return index + 1 >= size ? 0 : index + 1;
    }

    private void keepBusyUntil(long end) {
        if (end > busyUntil) {
            busyUntil = end;
        }
    }

    // ---- Deterministic noise --------------------------------------------------------------

    /**
     * The seed a spray emitted with these arguments will use. Two identical calls always
     * produce identical particles, whatever else happened in between — the old shared
     * rolling cursor made a burst depend on the order of every emission before it, which
     * looked fine and was impossible to test.
     */
    public static int seedOf(long now, float cx, float cy, int count, int shape) {
        int value = (int) now * 0x27D4EB2D;
        value ^= Float.floatToIntBits(cx) * 31;
        value ^= Float.floatToIntBits(cy) * 131;
        value ^= count * 7919;
        value ^= shape * 104729;
        return mix(value);
    }

    /** Cheap deterministic pseudo-noise in 0..1, pure in its three inputs. */
    private static float noise(int seed, int index, int stream) {
        return (mix(seed + index * 0x9E3779B9 + stream * 0x85EBCA6B) >>> 8) * (1f / (1 << 24));
    }

    /** An integer finaliser with good avalanche — a hash, not a sequence. */
    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }

    // ---- Querying ------------------------------------------------------------------------

    /**
     * True while anything is still animating, so the view knows to keep drawing. An effect
     * whose start time is still in the future counts: the line-clear wave is a queue of
     * pulses that have not begun yet, and the frame loop has to stay awake for it.
     */
    public boolean busy(long now) {
        return now < busyUntil;
    }

    /**
     * How far through its landing a freshly marked square is, 0..1, or -1 when the square
     * is not animating.
     *
     * <p><b>This number is now the whole confirmation.</b> It used to be one cue among
     * several, the loudest of which was a ring stroked into the .46–.68 annulus around the
     * square — which is precisely where the cursor's ring, its cream band and its finder
     * frame live <em>permanently</em>, in the same cream-plus-player-colour treatment. The
     * one cue that has to be read inside 120 ms was therefore drawn in the busiest place on
     * the board. The ring is gone; what a player sees instead is the tile itself arriving,
     * and this is the curve that drives it. The renderer is expected to read it as exactly
     * two things:
     *
     * <pre>
     *   scale      = SETTLE_SCALE + (1 - SETTLE_SCALE) * easeOutBack(p)
     *   brightness = blend(tile colour, CREAM, SETTLE_LIFT * (1 - p))
     * </pre>
     *
     * <p>Both resolve to the square's ordinary appearance at {@code p == 1}, so nothing has
     * to be unwound; and {@code easeOutBack} has overshot and is settling back by 120 ms,
     * which is what makes the press feel connected to the board.
     *
     * <p>The number is deliberately not the raw progress for every kind. A line-clear crest
     * runs over squares that are <em>already</em> placed, so it is mapped into the tail of
     * the curve — the tile swells about four percent and settles, a ripple through the row
     * rather than forty tiles collapsing to half size and springing back. Calm motion
     * compresses every kind the same way and opts out of the line ripple altogether.
     */
    /**
     * How brightly the line-complete light is falling on one square, 0 to 1.
     *
     * <p>{@link #drawCrest} puts the wave in the gutter <em>around</em> each square, because
     * the picture two people are making is not a surface this layer gets to paint amber on.
     * That is still right, and it left the beat almost invisible where it matters: a finished
     * line is mostly placed tiles, the gutter between them is covered by their neighbours,
     * and measured on a completed row the only candlelight that reached the eye was the sliver
     * at the two ends. So what a player actually noticed when a line landed was the ribbon
     * text at the bottom of the screen — a notification, twenty times a board, for the beat
     * that is supposed to be the game's rhythm.
     *
     * <p>This is the other half of it, and it is not a wash over the picture: the tile keeps
     * its own colour and is lifted toward candlelight, the way a surface does when a light
     * passes over it. {@code BoardRenderer.drawFilled} asks {@link Theme#washAlpha} how far
     * to carry it, so the lift clears {@link Theme#FEEDBACK_MIN_RATIO} against the mulberry
     * plum and against the near-black slate that replaces it under extra contrast.
     *
     * <p>Rides {@link #crest} — the same fast-rise, slow-fall curve the gutter band uses — so
     * the two are one moment travelling down the line rather than two effects that happen to
     * agree. Calm motion keeps it: this is a change of light, not of position, and somebody
     * who asked for less movement did not ask for less feedback.
     */
    public float lineLift(int x, int y, long now) {
        float best = 0;
        for (int i = 0; i < MAX_PULSES; i++) {
            if (pulseKind[i] != Pulse.LINE || pulseLength[i] <= 0
                    || pulseX[i] != x || pulseY[i] != y) {
                continue;
            }
            long age = now - pulseStart[i];
            if (age < 0 || age >= pulseLength[i]) {
                continue;
            }
            best = Math.max(best, crest(age / (float) pulseLength[i]));
        }
        return best;
    }

    public float squareProgress(int x, int y, long now) {
        boolean calm = Comfort.get().calmMotion;
        long best = Long.MIN_VALUE;
        float progress = -1;
        for (int i = 0; i < MAX_PULSES; i++) {
            if (pulseLength[i] <= 0 || pulseX[i] != x || pulseY[i] != y) {
                continue;
            }
            long age = now - pulseStart[i];
            if (age < 0 || age >= pulseLength[i]) {
                continue;
            }
            if (pulseKind[i] == Pulse.LINE && calm) {
                continue;
            }
            // The newest pulse on a square is the one describing what just happened to it.
            if (pulseStart[i] < best) {
                continue;
            }
            best = pulseStart[i];
            float raw = age / (float) pulseLength[i];
            float floor = progressFloor(pulseKind[i], calm);
            progress = floor + raw * (1 - floor);
        }
        return progress;
    }

    /**
     * Where in the curve a pulse of this kind starts its square from — the two-line rule the
     * paragraph above spends a paragraph explaining, said in code. It was a nested ternary
     * carrying three unnamed numbers.
     *
     * <p>A line crest never reaches here under calm motion; {@link #squareProgress} skips
     * those pulses outright, so the {@code LINE} branch is the full-motion answer only.
     */
    private static float progressFloor(Pulse kind, boolean calm) {
        if (kind == Pulse.LINE) {
            return LINE_RIPPLE_FLOOR;
        }
        return calm ? CALM_POP_FLOOR : 0f;
    }

    /** How many pulses are live at {@code now}, counting ones yet to start. */
    public int livePulses(long now) {
        int count = 0;
        for (int i = 0; i < MAX_PULSES; i++) {
            if (pulseLength[i] > 0 && now - pulseStart[i] < pulseLength[i]) {
                count++;
            }
        }
        return count;
    }

    /** How many particles are live at {@code now}, counting ones yet to start. */
    public int liveParticles(long now) {
        int count = 0;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (partLife[i] > 0 && now - partStart[i] < partLife[i]) {
                count++;
            }
        }
        return count;
    }

    /**
     * A fingerprint of every live effect at {@code now}. Two runs that produce the same
     * digest draw the same frame, which is how the harness's byte-identical guarantee is
     * checked without rendering anything.
     */
    public long stateDigest(long now) {
        long digest = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < MAX_PULSES; i++) {
            if (pulseLength[i] <= 0 || now - pulseStart[i] >= pulseLength[i]) {
                continue;
            }
            digest = fold(digest, pulseKind[i].ordinal());
            digest = fold(digest, pulseX[i]);
            digest = fold(digest, pulseY[i]);
            digest = fold(digest, pulseColor[i]);
            digest = fold(digest, now - pulseStart[i]);
            digest = fold(digest, pulseLength[i]);
        }
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (partLife[i] <= 0 || now - partStart[i] >= partLife[i]) {
                continue;
            }
            digest = fold(digest, Float.floatToIntBits(partX[i]));
            digest = fold(digest, Float.floatToIntBits(partY[i]));
            digest = fold(digest, Float.floatToIntBits(partVx[i]));
            digest = fold(digest, Float.floatToIntBits(partVy[i]));
            digest = fold(digest, Float.floatToIntBits(partSize[i]));
            digest = fold(digest, Float.floatToIntBits(partPhase[i]));
            digest = fold(digest, partShape[i]);
            digest = fold(digest, partFlags[i]);
            digest = fold(digest, partColor[i]);
            digest = fold(digest, now - partStart[i]);
            digest = fold(digest, partLife[i]);
        }
        return digest;
    }

    private static long fold(long digest, long value) {
        long result = digest ^ value;
        result *= 0xFF51AFD7ED558CCDL;
        return result ^ (result >>> 31);
    }

    // ---- Drawing: square pulses ------------------------------------------------------------

    /**
     * Draws the feedback that expands out of recently marked squares.
     *
     * <p>This is the <em>under</em> pass, and it belongs beneath the fills, the crosses and
     * both cursors: drawn on top it buries the two things a player is actually looking at,
     * at exactly the moment they are about to move. What a player sees here is the warmth
     * left behind once the cursor moves on; the confirmation itself is
     * {@link #drawPulseCrowns}, which has to be called separately and above.
     *
     * <p>There was a {@code drawBoardEffects} convenience beside this that called it and
     * {@link #drawParticles} in one go. Nothing ever called it — {@code BoardRenderer} draws
     * the two passes several statements apart, with the crosshair guides between them —
     * so it has gone rather than sitting here suggesting an order the game does not use.
     */
    public void drawPulses(Canvas canvas, Draw draw, BoardLayout board, long now) {
        noteBoard(board);
        boolean calm = Comfort.get().calmMotion;
        float depth = calm ? CALM_PULSE_DEPTH : 1f;
        for (int i = 0; i < MAX_PULSES; i++) {
            if (pulseLength[i] <= 0) {
                continue;
            }
            long age = now - pulseStart[i];
            if (age < 0 || age >= pulseLength[i]) {
                continue;
            }
            float progress = age / (float) pulseLength[i];
            float cx = board.centreX(pulseX[i]);
            float cy = board.centreY(pulseY[i]);
            switch (pulseKind[i]) {
                case FILL:
                    drawSettle(canvas, draw, cx, cy, board.cell, progress, pulseColor[i],
                            depth);
                    break;
                case CROSS:
                    drawScratch(canvas, draw, cx, cy, board.cell, progress, pulseColor[i],
                            depth);
                    break;
                case CLEAR:
                    drawUndo(canvas, draw, cx, cy, board.cell, progress, pulseColor[i],
                            depth);
                    break;
                case HINT:
                    drawShimmer(canvas, draw, cx, cy, board.cell, progress, depth, calm);
                    break;
                case ERROR:
                    drawSigh(canvas, draw, cx, cy, board.cell, progress, pulseColor[i],
                            depth);
                    break;
                default:
                    drawCrest(canvas, draw, cx, cy, board.cell, progress);
                    break;
            }
        }
    }

    // ---- Drawing: the crown pass -----------------------------------------------------------

    /**
     * The over pass: the ring of each action pulse that lies outside the cursor plate,
     * drawn above it.
     *
     * <p><b>Where this call goes.</b> In {@code BoardRenderer.draw}, between
     * {@code drawCursorPlates} and {@code drawClues}. That places a crown above the plate,
     * above both marks and above the grid, and still below the player-colour border, the
     * badge and the halo, which {@code CursorRenderer} draws in a later top-level pass — so
     * a crown can never cover the one cue that says where a player is.
     *
     * <p>Nothing here draws inside the plate, so the picture and the marks are untouched:
     * every shape starts at {@link #CROWN_IN} of the plate's own half width and moves
     * outward from there. And nothing here lasts: the longest crown is a hint's, at 620 ms.
     *
     * <p>{@link Pulse#LINE} is deliberately absent. A completed line runs over squares that
     * mostly have no cursor on them, so its wave has no occluder to climb over and stays in
     * the under pass where it belongs — see {@link #drawCrest}.
     */
    public void drawPulseCrowns(Canvas canvas, Draw draw, BoardLayout board, long now) {
        noteBoard(board);
        float cell = board.cell;
        float plate = BoardRenderer.plateHalf(cell);
        for (int i = 0; i < MAX_PULSES; i++) {
            if (pulseLength[i] <= 0 || pulseKind[i] == Pulse.LINE) {
                continue;
            }
            long age = now - pulseStart[i];
            if (age < 0 || age >= pulseLength[i]) {
                continue;
            }
            float progress = age / (float) pulseLength[i];
            float cx = board.centreX(pulseX[i]);
            float cy = board.centreY(pulseY[i]);
            switch (pulseKind[i]) {
                case CROSS:
                    drawCrossFlick(canvas, draw, cx, cy, cell, plate, progress,
                            pulseColor[i]);
                    break;
                case CLEAR:
                    drawUndoCrown(canvas, draw, cx, cy, cell, plate, progress,
                            pulseColor[i]);
                    break;
                case HINT:
                    drawHintCrown(canvas, draw, cx, cy, cell, plate, progress);
                    break;
                case ERROR:
                    drawSighCrown(canvas, draw, cx, cy, cell, plate, progress,
                            pulseColor[i]);
                    break;
                default:
                    drawFillCrown(canvas, draw, cx, cy, cell, plate, progress,
                            pulseColor[i]);
                    break;
            }
        }
    }

    /**
     * FILL — one ring blooming off the plate and gone. The whole confirmation a player can
     * actually see while their thumb is still on the button.
     *
     * <p>0.613 to 0.871 of a cell at 10x10 and 0.716 to 1.018 at 20x20 — it grows by the
     * same fraction of the plate everywhere rather than by the same fraction of a cell,
     * which is why it stays clear of the plate on a board where the plate is 0.70 of a cell
     * wide. Even there it stops just inside the 1.05-cell ceiling this layer holds every
     * shape to.
     */
    private void drawFillCrown(Canvas canvas, Draw draw, float cx, float cy, float cell,
                               float plate, float progress, int color) {
        float ease = Draw.easeOut(progress);
        float width = crownWidth(cell) * (1 - progress * .55f);
        crown(canvas, draw, cx, cy, Draw.lerp(plate * CROWN_IN, plate * 1.45f, ease), width,
                crownInk(color), crownAlpha(CROWN_FILL_ALPHA, progress),
                CROWN_KEYLINE_ALPHA);
    }

    /**
     * CROSS — four ticks flicking out past the corners, and no ring at all.
     *
     * <p>Withholding the ring is the point. A cross and a fill are the two things a player
     * does most, a ring for both would be one shape doing two jobs, and the diagonals
     * already say "cross" in the same language the drawn mark does. The colour is then free
     * to say only whose it was.
     */
    private void drawCrossFlick(Canvas canvas, Draw draw, float cx, float cy, float cell,
                                float plate, float progress, int color) {
        float ease = Draw.easeOut(progress);
        float width = crownWidth(cell) * (1 - progress * .55f);
        float reach = fitStroke(cx, cy, Draw.lerp(plate * 1.05f, plate * 1.60f, ease), width);
        float inner = Math.min(plate * CROWN_IN, reach);
        if (reach <= inner) {
            return;
        }
        int alpha = crownAlpha(CROWN_CROSS_ALPHA, progress);
        strokeFlicks(canvas, draw, cx, cy, inner, reach, width * CROWN_KEYLINE_WIDEN,
                Draw.withAlpha(CROWN_KEYLINE, (int) (alpha * CROWN_KEYLINE_ALPHA)));
        strokeFlicks(canvas, draw, cx, cy, inner, reach, width,
                Draw.withAlpha(crownInk(color), alpha));
    }

    /** CLEAR — the ring runs the other way, collapsing back onto the plate. */
    private void drawUndoCrown(Canvas canvas, Draw draw, float cx, float cy, float cell,
                               float plate, float progress, int color) {
        float ease = Draw.easeInOut(progress);
        float width = crownWidth(cell) * .8f * (1 - progress * .55f);
        crown(canvas, draw, cx, cy, Draw.lerp(plate * 1.40f, plate * CROWN_IN, ease), width,
                crownInk(color), crownAlpha(CROWN_UNDO_ALPHA, progress),
                CROWN_KEYLINE_ALPHA);
    }

    /**
     * HINT — a candlelit crown with four glints radiating out from behind the cursor.
     *
     * <p>The glints used to be held deliberately <em>inside</em> the square, reaching .26
     * to .62 of a cell, so that they could not "read as scratches across the row". Every
     * one of them was therefore under the plate, because a hint moves the cursor onto the
     * square it reveals: the one moment the game says "a little starlight showed the way"
     * showed no starlight. They now start where the plate ends and travel outward, which is
     * the same gesture turned inside out.
     */
    private void drawHintCrown(Canvas canvas, Draw draw, float cx, float cy, float cell,
                               float plate, float progress) {
        float ease = Draw.easeOut(progress);
        float fade = 1 - progress;
        float width = crownWidth(cell) * (1 - progress * .55f);
        float half = Draw.lerp(plate * CROWN_IN, plate * 1.65f, ease);
        int alpha = (int) (CROWN_HINT_ALPHA * Math.pow(fade, 1.3f));
        // A ring and, just outside it, a much wider and much fainter one. Recorded as a
        // single stroke this read as a drawn tan outline rather than as light — the
        // difference between a ring and a glow is entirely in the second, soft edge, and
        // one extra stroke is a cheap way to buy it.
        crown(canvas, draw, cx, cy, half + width, width * 2.2f, Theme.CANDLE,
                (int) (alpha * .34f), CROWN_NO_KEYLINE);
        crown(canvas, draw, cx, cy, half, width, Theme.CANDLE, alpha, CROWN_NO_KEYLINE);

        float glintWidth = Math.max(1.6f, cell * .055f) * fade;
        float reach = fitStroke(cx, cy, Draw.lerp(plate * 1.15f, plate * 1.70f, ease),
                glintWidth);
        float inner = Math.min(plate * 1.08f, reach);
        if (reach <= inner) {
            return;
        }
        // A fifth of a radian over the whole pulse. At .35 the glints visibly rotated,
        // which is a machine turning; this is closer to light shifting.
        float turn = progress * .22f;
        strokeGlints(canvas, draw, cx, cy, inner, reach, turn, glintWidth,
                Draw.withAlpha(Theme.CANDLE, crownAlpha(CROWN_GLINT_ALPHA, progress)));
    }

    /**
     * ERROR — the same ring as a fill, sinking as it goes.
     *
     * <p>This is the whole answer to "the game corrected me" and "I ruled this out" looking
     * identical. Gentle mode writes a cross on the square either way, and one frame later
     * the board is in the same state; what differs is a ring that <em>falls</em> while it
     * opens, over three times as long as a fill's, which is a sigh and reads as one.
     */
    private void drawSighCrown(Canvas canvas, Draw draw, float cx, float cy, float cell,
                               float plate, float progress, int color) {
        float ease = Draw.easeOut(progress);
        float width = crownWidth(cell) * (1 - progress * .55f);
        crown(canvas, draw, cx, cy + cell * .20f * ease,
                Draw.lerp(plate * CROWN_IN, plate * 1.50f, ease), width, crownInk(color),
                crownAlpha(CROWN_SIGH_ALPHA, progress), CROWN_KEYLINE_ALPHA);
    }

    /**
     * One crown: a dark keyline with the colour laid over it.
     *
     * <p>Two strokes rather than one measured colour, for the reason
     * {@code CursorRenderer.drawBorder} gives: a crown crosses cream paper and plum tiles
     * in the same 200 ms, and no single ink is above {@link Theme#FEEDBACK_MIN_RATIO} on
     * both. Rose's pink is 2.38:1 on paper and 4.16:1 on a tile; Sky's cyan is 1.81:1 and
     * 5.46:1. The keyline is 2.9–4.4:1 on paper across the alphas it is used at and belongs
     * to neither player, so both crowns get the same definition and the colour is left to
     * say whose.
     */
    private void crown(Canvas canvas, Draw draw, float cx, float cy, float half, float width,
                       int color, int alpha, float keyline) {
        float fitted = fitStroke(cx, cy, half, width);
        if (alpha < 4 || fitted <= 0 || width <= 0) {
            return;
        }
        if (keyline > 0) {
            tileStroke(canvas, draw, cx, cy, fitted, width * CROWN_KEYLINE_WIDEN,
                    Draw.withAlpha(CROWN_KEYLINE, (int) (alpha * keyline)));
        }
        tileStroke(canvas, draw, cx, cy, fitted, width, Draw.withAlpha(color, alpha));
    }

    /** Two arms, four ticks: the corners of the mark, flicking outward. */
    private void strokeFlicks(Canvas canvas, Draw draw, float cx, float cy, float inner,
                              float reach, float width, int color) {
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        for (int arm = 0; arm < 2; arm++) {
            float dx = arm == 0 ? .7071f : -.7071f;
            canvas.drawLine(cx - dx * inner, cy - .7071f * inner, cx - dx * reach,
                    cy - .7071f * reach, paint);
            canvas.drawLine(cx + dx * inner, cy + .7071f * inner, cx + dx * reach,
                    cy + .7071f * reach, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Four glints on the axes, turning slowly. */
    private void strokeGlints(Canvas canvas, Draw draw, float cx, float cy, float inner,
                              float reach, float turn, float width, int color) {
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        for (int glint = 0; glint < 4; glint++) {
            double angle = turn + glint * Math.PI / 2;
            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            canvas.drawLine(cx + dx * inner, cy + dy * inner, cx + dx * reach,
                    cy + dy * reach, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * A crown's stroke width: thick enough to be a shape rather than a hairline at ten
     * feet, and never below 2.5 px however small the cells get.
     */
    private static float crownWidth(float cell) {
        return Math.max(2.5f, cell * .085f);
    }

    /**
     * A crown's alpha over its life. The 1.6 power keeps it near full strength through the
     * first third — the part a player is looking at — and then lets go quickly, so the
     * shape is bright while it means something and never lingers as a smudge.
     *
     * <p>Deliberately not scaled by {@link #CALM_PULSE_DEPTH}. Calm motion already makes
     * this pulse 30% shorter, and the crown is the only cue calm motion has left; at depth
     * .55 it would be back under the perceptual floor, which is not what "calm" was asked
     * for.
     */
    private static int crownAlpha(int peak, float progress) {
        return (int) (peak * Math.pow(1 - progress, 1.6f));
    }

    /**
     * A radius trimmed so that a <em>stroke</em> of this width drawn along it still cannot
     * reach off the paper card. {@link #fit} trims a fill's edge; a stroke straddles its
     * path, so half its width has to be handed over as well or a crown on the outside rank
     * spills onto the wallpaper.
     */
    private float fitStroke(float cx, float cy, float half, float width) {
        return fit(cx, cy, half + width * .5f) - width * .5f;
    }

    /**
     * FILL — the tile pressing itself onto the paper.
     *
     * <p>No ring <em>here</em>. A ring in the .46–.68 annulus is a stroke laid over the
     * cursor's own ring, cream band and finder frame, which sit there permanently and in
     * the same cream-plus-player-colour treatment: the one cue that has to land inside
     * 120 ms would be the one drawn into the most cluttered band on the board. The
     * confirmation went two ways instead — into the tile, via {@link #squareProgress}, and
     * outward past the plate, via {@link #drawFillCrown}. What is left here is a soft
     * edgeless warmth spreading barely past the tile's own edge, the way a piece of card
     * pressed onto paper darkens the paper around it.
     *
     * <p>Three things changed together and only make sense together. It starts at .40 of a
     * cell rather than .30, because a mark is .445 and the old bloom was brightest at the
     * instant it was entirely underneath one and only escaped the tile once it had almost
     * faded out — its alpha and its visibility were anti-correlated. It fades on a linear
     * tail rather than a squared one, for the same reason. And its ink is walked toward
     * {@link Theme#INK}. It first clears the tile about a fifth of the way through, so what
     * anybody actually sees is .8 of the peak alpha: composited over paper that measures
     * 1.81:1 for Rose and 1.63:1 for Sky, against 1.175:1 before.
     */
    private void drawSettle(Canvas canvas, Draw draw, float cx, float cy, float cell,
                            float progress, int color, float depth) {
        float ease = Draw.easeOut(progress);
        float fade = 1 - progress;
        float radius = cell * (.40f + ease * .22f * depth);
        draw.circle(canvas, cx, cy, fit(cx, cy, radius),
                Draw.withAlpha(paperInk(color),
                        (int) (SETTLE_INK_ALPHA * depth * OPEN_ALPHA * fade)));
    }

    /**
     * CROSS — four ticks flicking outward along the diagonals and gone.
     *
     * <p>The ticks reach .72 of a cell, which is well outside the .5-cell footprint any
     * mark occupies, so they take {@link #OPEN_ALPHA}: there is nothing there to bury them.
     * Only the innermost part of the flick is under the drawn X.
     */
    private void drawScratch(Canvas canvas, Draw draw, float cx, float cy, float cell,
                             float progress, int color, float depth) {
        float ease = Draw.easeOut(progress);
        float fade = 1 - progress;
        float reach = fit(cx, cy, cell * (.30f + ease * .42f * depth));
        // Starts where the drawn X ends, so the flick reads as the mark scratching
        // outward rather than as a second, softer cross behind the first. Never past the
        // trimmed reach, or a flick on the outside rank would run inward.
        float inner = Math.min(cell * .28f, reach);
        strokeFlicks(canvas, draw, cx, cy, inner, reach,
                Math.max(1.5f, cell * .085f * (1 - progress * .5f)),
                Draw.withAlpha(color, (int) (SCRATCH_INK_ALPHA * fade * depth * OPEN_ALPHA)));
    }

    /** CLEAR — a thin outline collapsing inward, the mark being taken back. */
    private void drawUndo(Canvas canvas, Draw draw, float cx, float cy, float cell,
                          float progress, int color, float depth) {
        float ease = Draw.easeInOut(progress);
        float fade = 1 - progress;
        float half = fit(cx, cy, cell * (.56f - ease * .34f * depth));
        tileStroke(canvas, draw, cx, cy, half, Math.max(1.2f, cell * .055f * fade),
                Draw.withAlpha(paperInk(color),
                        (int) (UNDO_INK_ALPHA * fade * depth * OPEN_ALPHA)));
    }

    /**
     * HINT — candlelit warmth with four slowly turning glints, left behind under the mark.
     *
     * <p>Drawn in {@link Theme#CANDLE} whatever the caller asked for. The caller asks for
     * {@link Theme#GOLD}, which is the right gold on the dark hint chip and is 1.38:1 on
     * cream paper at full opacity — there is no alpha at which a gold halo on this board
     * could have been seen, so the halo was raised from 72 to 150 and the hue was changed
     * at the same time. The core is lifted toward cream by .35 rather than .55: further
     * than that and the brightest point of the hint is paler than the paper it sits on.
     *
     * <p>Neither of these clears {@link Theme#FEEDBACK_MIN_RATIO}, and neither is meant to:
     * the halo is a wide soft glow at 1.47:1 and the core is buried under the tile the hint
     * has just placed. What a player reads is the crown and the four glints in
     * {@link #drawHintCrown}, at 1.80:1 and 2.13:1 on paper. This is the warmth left
     * behind when the cursor moves off.
     */
    private void drawShimmer(Canvas canvas, Draw draw, float cx, float cy, float cell,
                             float progress, float depth, boolean calm) {
        float ease = Draw.easeOut(progress);
        float fade = 1 - progress;
        draw.circle(canvas, cx, cy, fit(cx, cy, cell * (.30f + ease * .48f * depth)),
                Draw.withAlpha(Theme.CANDLE,
                        (int) (SHIMMER_HALO_ALPHA * depth * fade * fade * OPEN_ALPHA)));
        draw.circle(canvas, cx, cy, cell * .17f * (1 - ease * .5f),
                Draw.withAlpha(Draw.blend(Theme.CANDLE, Theme.CREAM, .35f),
                        (int) (SHIMMER_CORE_ALPHA * fade * UNDER_MARK_ALPHA)));
        if (calm) {
            return;
        }
        float reach = fit(cx, cy, cell * (.26f + ease * .36f));
        float inner = Math.min(cell * .20f, reach);
        strokeGlints(canvas, draw, cx, cy, inner, reach, progress * .22f,
                Math.max(1.2f, cell * .05f * fade),
                Draw.withAlpha(Theme.CANDLE_DEEP, (int) (CROWN_GLINT_ALPHA * fade * OPEN_ALPHA)));
    }

    /**
     * ERROR — a soft bloom that swells, sinks a little and breathes out. No flash.
     *
     * <p>The sink went from .16 of a cell to .20 and the ink is walked to the dark end of
     * the palette; where it first clears the tile it now measures 2.00:1 on paper, against
     * 1.196:1. Both changes serve the same end: gentle mode writes exactly the cross a
     * deliberate cross writes, so the only thing that can tell a player the game corrected
     * them is the way this bloom — and the crown over it — moves.
     */
    private void drawSigh(Canvas canvas, Draw draw, float cx, float cy, float cell,
                          float progress, int color, float depth) {
        float ease = Draw.easeOut(progress);
        float fade = 1 - progress;
        float sink = cell * .20f * ease * depth;
        float radius = fit(cx, cy + sink, cell * (.30f + ease * .52f * depth));
        draw.circle(canvas, cx, cy + sink, radius,
                Draw.withAlpha(paperInk(color),
                        (int) (SIGH_INK_ALPHA * depth * fade * fade * OPEN_ALPHA)));
        draw.circle(canvas, cx, cy + sink, radius * .62f,
                Draw.withAlpha(paperInk(color),
                        (int) (SIGH_INK_ALPHA * .72f * depth * fade * OPEN_ALPHA)));
    }

    /**
     * LINE — a band of candlelight in the gutter around each square, riding down the line.
     *
     * <p>The one pulse that keeps all its ink, and the one that had almost none to keep.
     * Its energy used to be a fill and a border at .52 of a cell — but the tile it runs
     * under is drawn at .445, so on a filled square the only part of it that was ever
     * visible was a .075-cell seam: 4.1 px at 10x10 and 2.6 px at 20x20. The alpha-120
     * fill inside was entirely wasted, and since the game auto-crosses the rest of a
     * finished line, roughly half of every completed line was crossed or empty paper, where
     * the gold-and-cream border measured 1.187:1 and showed nothing at all.
     *
     * <p>The energy moved outward into the annulus between .48 and .78 of a cell. That
     * annulus is the gutter: it is the same paper on a filled square, a crossed one and an
     * untouched one, so the wave reads identically over all three. It is drawn as a
     * <em>rounded-rect</em> band rather than a ring on purpose — a square band tiles with
     * its neighbours' bands along the line instead of overlapping them (they meet at .48 of
     * a cell and share only .04), so twenty crests cannot pile into one dark stripe.
     * Measured, {@link Theme#CANDLE} at 185 is 1.84:1 on paper and 2.95:1 on a tile, the
     * only accent in the palette that clears the floor on both.
     *
     * <p>What it cannot do is climb over a neighbour's tile: the band is still in the under
     * pass, so on a square whose neighbour along the line is a placed tile the outer third
     * of the band is covered. Recorded, a fully filled row reads as a warm sweep running
     * along the gutter beneath it. The alternative — putting the wave in
     * {@link #drawPulseCrowns} — would draw amber over the outer .1 of every tile in the
     * finished line, and the picture two people are making is not a surface this layer gets
     * to draw on.
     */
    private void drawCrest(Canvas canvas, Draw draw, float cx, float cy, float cell,
                           float progress) {
        float crest = crest(progress);
        float bandWidth = cell * .30f;
        float band = fit(cx, cy, cell * .78f) - bandWidth * .5f;
        if (band > 0) {
            tileStroke(canvas, draw, cx, cy, band, bandWidth,
                    Draw.withAlpha(Theme.CANDLE, (int) (CREST_BAND_ALPHA * crest)));
        }
        // And the bright inner edge, which is what the wave looks like on a plum tile: at
        // alpha 235 it is 5.76:1 there, the one shape in the old crest anybody could see.
        float half = fit(cx, cy, cell * .52f);
        tileStroke(canvas, draw, cx, cy, half,
                Math.max(1.6f, cell * .075f * crest),
                Draw.withAlpha(Draw.blend(Theme.CANDLE, Theme.CREAM, .5f),
                        (int) (CREST_EDGE_ALPHA * crest * crest)));
    }

    /**
     * The crest curve: fast rise, slow fall, so the bright moment is narrow and a row of
     * overlapping crests reads as one thing travelling rather than as a whole line
     * switching on.
     *
     * <p>Pulled out of {@link #drawCrest} and named because it is the number that decides
     * how wide the travelling band is, and that had been wrong for the entire life of the
     * feature without anybody being able to check it: the width of the wave, in squares, is
     * {@code (span where this exceeds .35) * LINE_MS / stagger}, and
     * {@code EffectsTest.theLineWaveIsABandNotAWholeRow} now measures exactly that.
     */
    static float crest(float progress) {
        if (progress < CREST_RISE) {
            return progress / CREST_RISE;
        }
        return (float) Math.pow(1 - (progress - CREST_RISE) / (1 - CREST_RISE), 1.4f);
    }

    private void tileStroke(Canvas canvas, Draw draw, float cx, float cy, float half,
                            float width, int color) {
        draw.roundRectStroke(canvas, cx - half, cy - half, cx + half, cy + half, half * .32f,
                width, color);
    }

    // ---- Drawing: particles ------------------------------------------------------------------

    /**
     * Draws every live particle against a named board.
     *
     * <p>Pass {@code null} from a scene that owns the whole screen — the menu, the win
     * card — where there is no card for a particle to stay inside and no cursor for it to
     * be mistaken for. Everything is then drawn in the colour and at the strength it was
     * emitted with. The celebration drift is never confined either way: it is laid into the
     * lanes beside the reading column and belongs to the whole screen.
     *
     * <p>Positions are a closed-form function of launch time with per-shape gravity and
     * linear drag, so nothing has to be stepped frame by frame and any instant can be
     * drawn directly.
     *
     * <p>There was a two-argument form beside this that meant "confine to whatever card I
     * last drew pulses for", which is a fact about this object's history rather than about
     * the frame being drawn: {@link #clear()} is the only thing that forgets a card, so the
     * title screen — which has no card at all — silently trimmed particles to the board of
     * the puzzle the player had just left. Every caller now says which board it is on, or
     * says {@code null}.
     */
    public void drawParticles(Canvas canvas, Draw draw, BoardLayout board, long now) {
        if (board != null) {
            noteBoard(board);
        }
        drawParticles(canvas, draw, board != null, now);
    }

    private void drawParticles(Canvas canvas, Draw draw, boolean confineToCard, long now) {
        if (canvas.getWidth() > 0) {
            viewWidth = canvas.getWidth();
        }
        boolean onCard = confineToCard && cardKnown;
        float reachCap = cardCell * BURST_REACH_CELLS;
        float sizeCap = cardCell * BURST_SIZE_CELLS;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (partLife[i] <= 0) {
                continue;
            }
            long age = now - partStart[i];
            if (age < 0 || age >= partLife[i]) {
                continue;
            }
            int shape = partShape[i];
            float seconds = age / 1000f;
            float life = age / (float) partLife[i];

            boolean celebration = (partFlags[i] & FLAG_LANE) != 0;
            // Board chrome: a spray thrown by a square, drawn under the marks and the
            // cursors, and therefore held to the card, to just over a cell of travel and to
            // one candlelight that is nobody's identity colour.
            boolean chrome = onCard && !celebration;
            float drag = SHAPE_DRAG[shape];
            float damp = (float) ((1 - Math.exp(-drag * seconds)) / drag);
            float gravity = Theme.scale(celebration ? CELEBRATION_GRAVITY
                    : SHAPE_GRAVITY[shape]);
            float originX = celebration ? laneToScreen(partX[i]) : partX[i];
            float x = originX + partVx[i] * damp;
            float y = partY[i] + partVy[i] * damp + .5f * gravity * seconds * seconds;

            float size = partSize[i];
            float strength = 1f;
            if (chrome) {
                size = Math.min(size, sizeCap);
                // Held to a radius rather than culled at one: drag already makes the flight
                // asymptotic, so trimming the asymptote only shortens the tail.
                float dx = x - originX;
                float dy = y - partY[i];
                float travel = (float) Math.sqrt(dx * dx + dy * dy);
                if (travel > reachCap) {
                    float shrink = reachCap / travel;
                    x = originX + dx * shrink;
                    y = partY[i] + dy * shrink;
                }
                // Quiet while it is still over the square that threw it, where a tile or a
                // cursor plate is about to cover it; full strength once it has cleared the
                // mark's own footprint, where nothing is going to. Ramped over that half
                // cell rather than switched at it, because a particle that brightens as it
                // steps over a threshold reads as a glitch.
                strength = Draw.lerp(UNDER_MARK_ALPHA, OPEN_ALPHA,
                        Draw.clamp01(Math.min(travel, reachCap)
                                / Math.max(1f, cardCell * OPEN_FROM_CELLS)))
                        * cardFade(x, y, size);
                if (strength <= 0) {
                    continue;
                }
            }

            float envelope = envelope(life);
            int color = Draw.withAlpha(chrome ? boardTint(partColor[i]) : partColor[i],
                    (int) (SHAPE_ALPHA[shape] * envelope * strength));
            if (color >>> 24 < 4) {
                continue;
            }

            switch (shape) {
                case SHAPE_HEART: {
                    // Buoyant and unhurried: a slow sway and a matching tilt, so hearts
                    // read as floating rather than being thrown.
                    float sway = (float) Math.sin(partPhase[i] + seconds * 1.5f);
                    drawUnit(canvas, draw, unitHeart, x + sway * Theme.scale(9f), y,
                            size * (.8f + envelope * .25f), 1f, sway * 9f, color);
                    break;
                }
                case SHAPE_PETAL: {
                    // Flutter: the petal drifts sideways and turns edge-on, which is the
                    // squash on the x axis. It never quite vanishes, hence the floor.
                    float phase = partPhase[i] + seconds * 4.4f;
                    float turn = Math.abs((float) Math.cos(phase));
                    drawUnit(canvas, draw, unitPetal,
                            x + (float) Math.sin(partPhase[i] + seconds * 3.1f)
                                    * Theme.scale(14f), y,
                            size, Math.max(.22f, turn),
                            partPhase[i] * 57.2958f + partSpin[i] * seconds, color);
                    break;
                }
                case SHAPE_SPARK:
                    drawSpark(canvas, draw, x, y, size * (.55f + envelope * .75f),
                            partPhase[i] * 57.2958f + partSpin[i] * seconds, color);
                    break;
                default:
                    draw.circle(canvas, x, y, size * (.5f + envelope * .6f), color);
                    break;
            }
        }
    }

    /**
     * Fade in fast enough that nothing pops on, hold, then leave on a soft quadratic tail.
     *
     * <p>The knee sits late on purpose. A celebration that is over quickly has to be
     * <em>present</em> while it lasts: with the knee at .55 a shortened drift spends nearly
     * half its life dissolving, which reads as a thinning haze rather than as confetti, and
     * the only way to get the room back is to make every particle live longer — which is
     * how a two-second moment turned into four and a half.
     */
    private static float envelope(float life) {
        float in = Math.min(1f, life * 14f);
        float out = 1 - Draw.clamp01((life - .72f) / .28f);
        return in * out * out;
    }

    /**
     * Resolves a 0..1 lane into a pixel column: the gutters beside the reading area, or the
     * whole safe width when there is no reading area.
     *
     * <p>The margin used to be charged twice. A gutter runs from the safe edge to the
     * reading column, and the reading column already is the band being kept clear — so
     * pulling its edge in by another margin's worth left the win screen's own numbers
     * (readLeft .14, readRight .86) describing lanes of [96, 172.8] and [1747.2, 1824]:
     * 153 px of a 1920 px screen, both hard against the overscan boundary, with all 68
     * celebration particles inside them. The same numbers now give 268.8 px and 268.8 px of
     * gutter, which is where a celebration can actually be seen.
     */
    private float laneToScreen(float slot) {
        return laneColumn(slot, viewWidth);
    }

    /** {@link #laneToScreen} against an explicit width, so a test can measure the lanes. */
    float laneColumn(float slot, float width) {
        // The TV-safe margin, so nothing celebratory is ever eaten by an overscanning panel.
        float margin = width * Theme.SAFE_AREA;
        if (!readingColumn) {
            return Draw.lerp(margin, width - margin, Draw.clamp01(slot));
        }
        float left = width * readLeft;
        float right = width * readRight;
        float side = slot * 2f;
        if (side < 1f) {
            return Draw.lerp(margin, Math.max(margin, left), side);
        }
        return Draw.lerp(Math.min(width - margin, right), width - margin, side - 1f);
    }

    /**
     * Stamps a unit outline at a position, size, squash and rotation. The outline is built
     * once, so a heart costs one transform and one fill however many are on screen.
     */
    private void drawUnit(Canvas canvas, Draw draw, Path unit, float x, float y, float size,
                          float squash, float degrees, int color) {
        float wide = size * squash;
        if (wide < .35f || size < .35f) {
            return;
        }
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.save();
        canvas.translate(x, y);
        if (degrees != 0) {
            canvas.rotate(degrees);
        }
        canvas.scale(wide, size);
        canvas.drawPath(unit, paint);
        canvas.restore();
    }

    /**
     * A four-point glint with a bright core: fast, small, over almost before it starts.
     *
     * <p>It used to be two round-capped lines of constant width at a random rotation — and
     * on a nonogram board, at the 9 px it ships at, that is a pale straw-coloured X. An X
     * on this board already means "definitely empty", so the hint's own spray was drawing a
     * soft gold copy of the game's most load-bearing mark, ten times over, across the
     * neighbourhood the player was about to read. A filled star with concave sides cannot
     * be confused with a stroked cross at any size, and the taper is what makes it read as
     * light rather than as ink.
     */
    private void drawSpark(Canvas canvas, Draw draw, float x, float y, float size,
                           float spin, int color) {
        drawUnit(canvas, draw, unitGlint, x, y, size * 1.5f, 1f, spin, color);
        draw.circle(canvas, x, y, size * .34f,
                Draw.withAlpha(Draw.blend(color, Theme.CREAM, .6f), color >>> 24));
    }

    // ---- Unit outlines --------------------------------------------------------------------

    /**
     * A heart one unit wide either side of centre, with a real concave notch at the top and
     * a real point at the bottom — the two features that a lobes-and-triangle heart loses
     * first as it gets smaller.
     */
    private static void buildUnitHeart(Path path) {
        path.reset();
        path.moveTo(0f, .92f);
        path.cubicTo(-.62f, .36f, -1f, .02f, -1f, -.34f);
        path.cubicTo(-1f, -.72f, -.66f, -.92f, -.38f, -.92f);
        path.cubicTo(-.16f, -.92f, -.03f, -.76f, 0f, -.60f);
        path.cubicTo(.03f, -.76f, .16f, -.92f, .38f, -.92f);
        path.cubicTo(.66f, -.92f, 1f, -.72f, 1f, -.34f);
        path.cubicTo(1f, .02f, .62f, .36f, 0f, .92f);
        path.close();
    }

    /** A petal: a leaf with a point at each end, so its rotation reads at any size. */
    private static void buildUnitPetal(Path path) {
        path.reset();
        path.moveTo(0f, -1f);
        path.cubicTo(.72f, -.55f, .9f, .35f, 0f, 1f);
        path.cubicTo(-.9f, .35f, -.72f, -.55f, 0f, -1f);
        path.close();
    }

    /**
     * A four-point star whose sides curve inward, so the arms taper to a point instead of
     * meeting at a corner. The waist is at .12 of the unit — narrow enough that the shape
     * still reads as light at ten pixels across, wide enough that it does not disappear
     * into its own bright core.
     */
    private static void buildUnitGlint(Path path) {
        path.reset();
        path.moveTo(0f, -1f);
        path.cubicTo(.12f, -.28f, .28f, -.12f, 1f, 0f);
        path.cubicTo(.28f, .12f, .12f, .28f, 0f, 1f);
        path.cubicTo(-.12f, .28f, -.28f, .12f, -1f, 0f);
        path.cubicTo(-.28f, -.12f, -.12f, -.28f, 0f, -1f);
        path.close();
    }

    // ---- Small helpers ----------------------------------------------------------------------

    private static long naturalLength(Pulse kind) {
        switch (kind) {
            case FILL:
                return FILL_MS;
            case CROSS:
                return CROSS_MS;
            case CLEAR:
                return CLEAR_MS;
            case HINT:
                return HINT_MS;
            case ERROR:
                return ERROR_MS;
            default:
                return LINE_MS;
        }
    }

    private static int safeShape(int shape) {
        return shape < 0 || shape >= SHAPE_COUNT ? SHAPE_DOT : shape;
    }

    /**
     * The shape the win drift throws at a given index: heart, petal, dot, repeating.
     *
     * <p>The sibling of {@link #confettiColor(int)}, and it should always have been here.
     * The same nested ternary was written out in {@code CozyGameView.showWinCelebration},
     * verbatim again in the preview harness so the screenshots would match, and a third time
     * as a private helper in {@code EffectsTest} so the particle counts would — three copies
     * of one rotation, any of which could go on describing the old game after a change.
     *
     * <p>{@code floorMod} rather than {@code %} only matters for a negative index, and every
     * caller counts up from zero, so the emitted sequence is exactly what it was.
     */
    public static int confettiShape(int index) {
        switch (Math.floorMod(index, 3)) {
            case 0:
                return SHAPE_HEART;
            case 1:
                return SHAPE_PETAL;
            default:
                return SHAPE_DOT;
        }
    }

    /** Convenience palette for celebratory bursts. */
    public static int confettiColor(int index) {
        switch (Math.floorMod(index, 5)) {
            case 0:
                return Theme.PINK;
            case 1:
                return Theme.BLUE;
            case 2:
                return Theme.GOLD;
            case 3:
                return Theme.PINK_LIGHT;
            default:
                return Theme.CREAM;
        }
    }
}
