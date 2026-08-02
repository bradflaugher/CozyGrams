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
 *   <li>{@link Pulse#FILL} — <em>soft settle</em>. An edgeless warmth spreading just past
 *       the tile's own edge, 190&nbsp;ms. The confirmation itself is not drawn here at all;
 *       it is the tile's own scale and brightness, driven by {@link #squareProgress}.</li>
 *   <li>{@link Pulse#CROSS} — <em>quick scratch</em>. Two diagonal ticks flick outward past
 *       the corners and are gone in 240&nbsp;ms.</li>
 *   <li>{@link Pulse#CLEAR} — <em>quiet undo</em>. A thin outline collapses inward to
 *       nothing, 250&nbsp;ms.</li>
 *   <li>{@link Pulse#HINT} — <em>warm shimmer</em>. A candle-gold glow with four slowly
 *       turning glints, 560&nbsp;ms.</li>
 *   <li>{@link Pulse#ERROR} — <em>gentle sigh</em>. A soft edgeless bloom that swells,
 *       sinks a little and breathes out, 480&nbsp;ms. Never a flash, never a shake.</li>
 *   <li>{@link Pulse#LINE} — <em>travelling wave</em>. A crest that lights the tile and
 *       falls away. The caller emits one per square with a staggered future start time,
 *       which is what turns the crest into a wave running down the line.</li>
 * </ul>
 *
 * <p>Each of these peaks inside ~110&nbsp;ms, so an action is confirmed while the thumb is
 * still on the button, and each is gone before it can pile up into visual soup.
 *
 * <p><b>Where the layer sits.</b> Everything on this page is <em>underneath</em> the
 * board: beneath the fills, beneath the crosses and beneath both cursors. Three rules
 * follow from that and are enforced here rather than left to the caller:
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
 * </ul>
 *
 * <p><b>Cost.</b> Everything lives in preallocated primitive arrays; nothing here allocates
 * after construction and nothing reads a wall clock or an unseeded random. Particle
 * positions are a closed-form function of their launch time, so there is no per-frame
 * integration state to keep and a frame can be drawn for any instant — which is what makes
 * the still-frame preview harness able to reproduce a moment exactly.
 *
 * <p><b>Comfort.</b> Everything here honours {@link Comfort#calmMotion}: far fewer
 * particles, slower and smaller ones, shorter and shallower pulses, and no re-popping of
 * squares that have already been placed. Nothing in this class ever loops forever.
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
    // last of them is still on screen 19*18 + 420 = 762 ms after the move, so two players
    // doing that at the same time, twice inside a second, is ~164 live pulses. 192 covers
    // it with headroom, and when the buffer really is full the eviction below drops the
    // least important effect rather than whatever happens to be next in the ring — a
    // decorative LINE crest is never allowed to evict a player's own fill confirmation.
    //
    // Worst case for particles: the win emits 34 rise() calls of 2 = 68 hearts and petals
    // staggered 18 ms apart and living up to ~1.7 s, so all of them coexist. On top of that
    // a fill sprays 7 dots and a hint 10 sparks; at four presses a second each, two players
    // add about 40 more inside a dot's 520 ms life. 384 leaves a fourfold margin and costs
    // ~20 KB of arrays.
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
    private static final long HINT_MS = 560;
    private static final long ERROR_MS = 480;
    private static final long LINE_MS = 420;

    /** Calm motion keeps every shape, just briefer and shallower. */
    private static final float CALM_PULSE_TIME = .7f;
    private static final float CALM_PULSE_DEPTH = .55f;
    private static final float CALM_SPEED = .6f;
    private static final float CALM_SIZE = .85f;
    private static final float CALM_LIFE = .8f;

    // ---- Living underneath the board -----------------------------------------------------

    /**
     * How much of its ink an effect keeps now that it is drawn beneath the marks and the
     * cursors. Half. The layer is a warmth under the board, not a thing competing with the
     * board — and at full strength a spray reads as another mark rather than as light.
     */
    private static final float LAYER_ALPHA = .5f;

    /** No spray a square throws may travel further than this many cells from it. */
    private static final float BURST_REACH_CELLS = 1.5f;

    /**
     * Nor be drawn wider than this fraction of a cell. A cursor badge is about .30 of a
     * cell across at 10x10 and never smaller than 33 px; holding a particle to a third of
     * that is what stops six of them reading as six more badges.
     */
    private static final float BURST_SIZE_CELLS = .16f;

    /**
     * One warm amber — the light a candle throws on paper. Every spray a square makes is
     * pulled most of the way to it, so a burst belongs to the board rather than to either
     * player. Deliberately deeper than {@link Theme#GOLD}, which is chosen to glow on a
     * dark panel and simply disappears on cream paper.
     */
    public static final int CANDLE = 0xffD69A4A;

    /** How far a board burst is pulled toward {@link #CANDLE}; the rest is the caller's. */
    private static final float CANDLE_PULL = .72f;

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

    private static final int SHAPE_COUNT = 4;

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
     * </ul>
     */
    private static final float[] SHAPE_GRAVITY = {620f, -46f, 60f, 150f};
    private static final float[] SHAPE_DRAG = {2.4f, .5f, 4.2f, 1.5f};
    private static final float[] SHAPE_SIZE = {4.2f, 9.5f, 5.5f, 8f};
    private static final long[] SHAPE_LIFE = {520L, 1150L, 520L, 1500L};
    private static final int[] SHAPE_ALPHA = {225, 195, 240, 175};

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

    /** The end of the latest live effect, so {@link #busy} is a single comparison. */
    private long busyUntil = Long.MIN_VALUE;

    public Effects() {
        buildUnitHeart(unitHeart);
        buildUnitPetal(unitPetal);
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
        long life = (long) (SHAPE_LIFE[kind] * (calm ? CALM_LIFE : 1f));

        for (int i = 0; i < count; i++) {
            if (calm && (i & 3) != 0) {
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
     */
    private float particleSize(int shape, float spread, float jitter) {
        float ceiling = Theme.scale(SHAPE_SIZE[shape]);
        float fromSpread = Math.abs(spread) * .06f;
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
     * Reserves the horizontal band, in fractions of the viewport width, where a scene is
     * about to draw copy. Celebration drift keeps out of it. The default is the win card's
     * own column; a scene with a different layout should say so before it draws.
     */
    public void setReadingColumn(float leftFraction, float rightFraction) {
        float left = Draw.clamp01(Math.min(leftFraction, rightFraction));
        float right = Draw.clamp01(Math.max(leftFraction, rightFraction));
        readLeft = left;
        readRight = Math.max(right, left + .05f);
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
        return shape == SHAPE_DOT || shape == SHAPE_SPARK ? 2 : 1;
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
                pulseCursor = i + 1 >= MAX_PULSES ? 0 : i + 1;
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
        pulseCursor = victim + 1 >= MAX_PULSES ? 0 : victim + 1;
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
                partCursor = i + 1 >= MAX_PARTICLES ? 0 : i + 1;
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
        partCursor = victim + 1 >= MAX_PARTICLES ? 0 : victim + 1;
        return victim;
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
            float floor = pulseKind[i] == Pulse.LINE ? .55f : (calm ? .72f : 0f);
            progress = floor + raw * (1 - floor);
        }
        return progress;
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
     * The whole board effects layer — the square pulses and everything the squares have
     * thrown — confined to the paper card.
     *
     * <p>This belongs <em>beneath</em> the fills, the crosses and both cursors. Drawn on
     * top, the layer buries the two things a player is actually looking at, and it does so
     * at exactly the moment they are about to move.
     */
    public void drawBoardEffects(Canvas canvas, Draw draw, BoardLayout board, long now) {
        drawPulses(canvas, draw, board, now);
        drawParticles(canvas, draw, board, now);
    }

    /** Draws the feedback that expands out of recently marked squares. */
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
                    drawShimmer(canvas, draw, cx, cy, board.cell, progress, pulseColor[i],
                            depth, calm);
                    break;
                case ERROR:
                    drawSigh(canvas, draw, cx, cy, board.cell, progress, pulseColor[i],
                            depth);
                    break;
                default:
                    drawCrest(canvas, draw, cx, cy, board.cell, progress, pulseColor[i],
                            depth, calm);
                    break;
            }
        }
    }

    /**
     * FILL — the tile pressing itself onto the paper.
     *
     * <p>There is no ring. A ring here is a stroke in the .46–.68 annulus, which is where
     * the cursor's ring, its cream band and its finder frame sit permanently and in the
     * same cream-plus-player-colour treatment: the one cue that has to land inside 120 ms
     * would be the one drawn into the most cluttered band on the board. The confirmation
     * moved into the tile — see {@link #squareProgress} — and all that is left here is a
     * soft edgeless warmth that spreads barely past the tile's own edge, the way a piece of
     * card pressed onto paper darkens the paper around it.
     */
    private void drawSettle(Canvas canvas, Draw draw, float cx, float cy, float cell,
                            float progress, int color, float depth) {
        float ease = Draw.easeOut(progress);
        float fade = 1 - progress;
        float radius = cell * (.30f + ease * .22f * depth);
        draw.circle(canvas, cx, cy, fit(cx, cy, radius),
                Draw.withAlpha(color, (int) (96 * depth * LAYER_ALPHA * fade * fade)));
    }

    /** CROSS — two ticks flicking outward along the diagonals and gone. */
    private void drawScratch(Canvas canvas, Draw draw, float cx, float cy, float cell,
                             float progress, int color, float depth) {
        float ease = Draw.easeOut(progress);
        float fade = 1 - progress;
        float reach = fit(cx, cy, cell * (.30f + ease * .42f * depth));
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(1.5f, cell * .085f * (1 - progress * .5f)));
        paint.setColor(Draw.withAlpha(color, (int) (235 * fade * depth * LAYER_ALPHA)));
        // Starts where the drawn X ends, so the flick reads as the mark scratching
        // outward rather than as a second, softer cross behind the first. Never past the
        // trimmed reach, or a flick on the outside rank would run inward.
        float inner = Math.min(cell * .28f, reach);
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

    /** CLEAR — a thin outline collapsing inward, the mark being taken back. */
    private void drawUndo(Canvas canvas, Draw draw, float cx, float cy, float cell,
                          float progress, int color, float depth) {
        float ease = Draw.easeInOut(progress);
        float fade = 1 - progress;
        float half = fit(cx, cy, cell * (.56f - ease * .34f * depth));
        tileStroke(canvas, draw, cx, cy, half, Math.max(1.2f, cell * .055f * fade),
                Draw.withAlpha(color, (int) (170 * fade * depth * LAYER_ALPHA)));
    }

    /** HINT — candle-gold warmth with four slowly turning glints. */
    private void drawShimmer(Canvas canvas, Draw draw, float cx, float cy, float cell,
                             float progress, int color, float depth, boolean calm) {
        float ease = Draw.easeOut(progress);
        float fade = 1 - progress;
        draw.circle(canvas, cx, cy, fit(cx, cy, cell * (.30f + ease * .48f * depth)),
                Draw.withAlpha(color, (int) (72 * depth * fade * fade * LAYER_ALPHA)));
        draw.circle(canvas, cx, cy, cell * .17f * (1 - ease * .5f),
                Draw.withAlpha(Draw.blend(color, Theme.CREAM, .55f),
                        (int) (230 * fade * LAYER_ALPHA)));
        if (calm) {
            return;
        }
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(1.2f, cell * .05f * fade));
        paint.setColor(Draw.withAlpha(color, (int) (215 * fade * LAYER_ALPHA)));
        // Kept inside the square: glints that reach into the neighbours stop reading as
        // one warm point of light and start reading as scratches across the row.
        float reach = fit(cx, cy, cell * (.26f + ease * .36f));
        float inner = Math.min(cell * .20f, reach);
        float turn = progress * .35f;
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

    /** ERROR — a soft bloom that swells, sinks a little and breathes out. No flash. */
    private void drawSigh(Canvas canvas, Draw draw, float cx, float cy, float cell,
                          float progress, int color, float depth) {
        float ease = Draw.easeOut(progress);
        float fade = 1 - progress;
        float sink = cell * .16f * ease * depth;
        float radius = fit(cx, cy + sink, cell * (.30f + ease * .52f * depth));
        draw.circle(canvas, cx, cy + sink, radius,
                Draw.withAlpha(color, (int) (66 * depth * fade * fade * LAYER_ALPHA)));
        draw.circle(canvas, cx, cy + sink, radius * .62f,
                Draw.withAlpha(color, (int) (54 * depth * fade * LAYER_ALPHA)));
    }

    /**
     * LINE — a crest that lights the tile and falls away, one square behind the last.
     *
     * <p>The one pulse that keeps all its ink. Every other shape here fires on the square a
     * player is looking at, beside a cursor, and can afford to be half strength underneath
     * it; the crest runs across a line that is by definition <em>finished</em>, so every
     * square it crosses is already an opaque tile or a crossed one and nothing drawn inside
     * the tile's own footprint can be seen at all. What a player reads is the warm border
     * around each square and the ring expanding past it, so those are what carry the wave —
     * and halving them as well as burying them leaves nothing there to read.
     */
    private void drawCrest(Canvas canvas, Draw draw, float cx, float cy, float cell,
                           float progress, int color, float depth, boolean calm) {
        // Fast rise, slow fall: the bright moment is narrow, so a row of overlapping
        // crests reads as one thing travelling rather than a whole line switching on.
        float crest = progress < .22f ? progress / .22f
                : (float) Math.pow(1 - (progress - .22f) / .78f, 1.4f);
        float fade = 1 - progress;
        // Deliberately a shade wider than a mark. A crest the size of the tile it runs
        // under is a one-pixel fringe at 20x20 and nothing at all; at .52 it is a warm
        // border around each square in turn, which is what carries the wave down a line
        // whose every square is by then an opaque tile.
        float half = fit(cx, cy, cell * .52f);
        tile(canvas, draw, cx, cy, half,
                Draw.withAlpha(color, (int) (120 * depth * crest)));
        tileStroke(canvas, draw, cx, cy, half,
                Math.max(1.6f, cell * .075f * crest),
                Draw.withAlpha(Draw.blend(color, Theme.CREAM, .5f),
                        (int) (235 * crest * crest)));
        if (calm) {
            return;
        }
        draw.circleStroke(canvas, cx, cy,
                fit(cx, cy, cell * (.42f + Draw.easeOut(progress) * .34f)),
                Math.max(1f, cell * .04f * fade),
                Draw.withAlpha(color, (int) (150 * fade * fade)));
    }

    private void tile(Canvas canvas, Draw draw, float cx, float cy, float half, int color) {
        draw.roundRect(canvas, cx - half, cy - half, cx + half, cy + half, half * .32f,
                color);
    }

    private void tileStroke(Canvas canvas, Draw draw, float cx, float cy, float half,
                            float width, int color) {
        draw.roundRectStroke(canvas, cx - half, cy - half, cx + half, cy + half, half * .32f,
                width, color);
    }

    // ---- Drawing: particles ------------------------------------------------------------------

    /**
     * Draws every live particle, confining anything a square threw to the paper card of
     * the board this instance last drew pulses for.
     *
     * <p>Positions are a closed-form function of launch time with per-shape gravity and
     * linear drag, so nothing has to be stepped frame by frame and any instant can be
     * drawn directly.
     */
    public void drawParticles(Canvas canvas, Draw draw, long now) {
        drawParticles(canvas, draw, cardKnown, now);
    }

    /**
     * Draws every live particle against a named board.
     *
     * <p>Pass {@code null} from a scene that owns the whole screen — the menu, the win
     * card — where there is no card for a particle to stay inside and no cursor for it to
     * be mistaken for. Everything is then drawn in the colour and at the strength it was
     * emitted with. The celebration drift is never confined either way: it is laid into the
     * lanes beside the reading column and belongs to the whole screen.
     */
    public void drawParticles(Canvas canvas, Draw draw, BoardLayout board, long now) {
        if (board != null) {
            noteBoard(board);
        }
        drawParticles(canvas, draw, board != null, now);
    }

    private void drawParticles(Canvas canvas, Draw draw, boolean confine, long now) {
        if (canvas.getWidth() > 0) {
            viewWidth = canvas.getWidth();
        }
        boolean onCard = confine && cardKnown;
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
            // cursors, and therefore held to the card, to a cell and a half of travel, to
            // half its ink and to one candlelight that is nobody's identity colour.
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
                strength = LAYER_ALPHA * cardFade(x, y, size);
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

    /** Resolves a 0..1 lane into a pixel column in the gutters beside the reading area. */
    private float laneToScreen(float slot) {
        // The TV-safe margin, so nothing celebratory is ever eaten by an overscanning panel.
        float margin = viewWidth * Theme.SAFE_AREA;
        float left = viewWidth * readLeft;
        float right = viewWidth * readRight;
        float side = slot * 2f;
        if (side < 1f) {
            return Draw.lerp(margin, Math.max(margin, left - margin), side);
        }
        return Draw.lerp(Math.min(viewWidth - margin, right + margin), viewWidth - margin,
                side - 1f);
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

    /** A four-point glint with a bright core: fast, small, over almost before it starts. */
    private void drawSpark(Canvas canvas, Draw draw, float x, float y, float size,
                           float spin, int color) {
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(1f, size * .30f));
        paint.setColor(color);
        // Unequal arms: a symmetric cross reads as a plus sign, a long axis and a short
        // one reads as light catching something.
        double angle = Math.toRadians(spin);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float longReach = size * 1.9f;
        float shortReach = size * .8f;
        canvas.drawLine(x - cos * longReach, y - sin * longReach, x + cos * longReach,
                y + sin * longReach, paint);
        canvas.drawLine(x + sin * shortReach, y - cos * shortReach,
                x - sin * shortReach, y + cos * shortReach, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
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
