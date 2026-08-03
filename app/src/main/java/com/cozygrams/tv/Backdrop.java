package com.cozygrams.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * The illustrated room behind everything else, and the shade that keeps the interface
 * readable on top of it.
 *
 * <h2>Shade where the interface is, not everywhere</h2>
 *
 * <p>The two illustrations — a lamp-lit living room and a moonlit rose garden — used to
 * be flattened under one 150-alpha sheet across the whole screen while playing, which
 * left them reading as texture rather than as places. They are the reason the game feels
 * like an evening in, so the shade is now spent only where something has to be read:
 *
 * <ol>
 *   <li>a light overall <b>veil</b>, tinted to the scene's own key light, so nothing in
 *       the art can out-shine the paper board;</li>
 *   <li>a soft elliptical <b>pool</b> under the playfield, built from concentric ovals,
 *       that settles the board and the side panel onto something and fades to nothing
 *       well before the corners;</li>
 *   <li><b>edge bands</b> at the top and bottom, where the title strip and the message
 *       ribbon float directly on the picture with no panel of their own.</li>
 * </ol>
 *
 * <p>The result is roughly the same protection under the header and the ribbon as before,
 * a little less around the board, and less than a third of it in the four corners — which
 * is exactly where both illustrations keep their best material: the lamp and the bookshelf,
 * the curtain and the window, the lantern and the tea table.
 *
 * <h2>And one place light is added</h2>
 *
 * <p>Everything above <em>takes</em> light away. Both illustrations are painted with an
 * obvious key light — the room's lamp at normalised (0.947, 0.221), the garden's moon at
 * (0.776, 0.190), measured as the brightest 32-px block of each decoded asset — and until
 * now nothing in the game acknowledged either one, so the picture sat there inert. A
 * shallow bloom on the key point, and a drift slower than anything else on screen, are the
 * difference between a painting and a room with someone in it.
 *
 * <h2>The clock</h2>
 *
 * <p>{@link #draw(Canvas, float, float, UiState, GameState, long)} is the real entry point.
 * The five-argument overload exists because {@code Renderer} does not pass its clock yet;
 * without one the backdrop is drawn exactly as it always was — no drift, no breath, and a
 * scene change that snaps instead of crossing. Nothing here reads a wall clock of its own:
 * the screenshot harness drives a fixed clock and every frame it writes has to be
 * reproducible.
 */
public final class Backdrop {

    /** Total shade over the whole picture while playing, before the pool and bands. */
    private static final int VEIL_PLAYING = 50;
    /** Menus sit on a large opaque panel, so the art only needs settling, not hiding. */
    private static final int VEIL_MENU = 30;
    /** Extra shade everywhere when the player has asked for more contrast. */
    private static final int VEIL_CONTRAST = 60;

    /** Peak alpha added in the middle of the elliptical pool. */
    private static final int POOL_PLAYING = 40;
    private static final int POOL_MENU = 22;
    /** How many ovals the pool is built from. More is smoother and costs almost nothing. */
    private static final int POOL_RINGS = 18;

    /** Peak alpha at the very top and bottom edges while playing. */
    private static final int BAND_TOP = 76;
    private static final int BAND_BOTTOM = 62;
    private static final int BAND_MENU = 30;
    /** How far in from the edge a band reaches, as a fraction of the screen height. */
    private static final float BAND_TOP_DEPTH = .215f;
    private static final float BAND_BOTTOM_DEPTH = .175f;
    private static final int BAND_STEPS = 12;

    /**
     * The shade's own colour, chosen per scene. Neutral grey would mute both pictures
     * equally; deepening each one toward its own key light keeps the room amber and the
     * garden blue instead of dragging them both toward the same murk.
     */
    private static final int TINT_ROOM = rgb(40, 20, 34);
    private static final int TINT_GARDEN = rgb(12, 16, 44);

    /**
     * The deep counterparts of the two tints, for a dim that covers the whole screen
     * rather than settling part of it.
     *
     * <p>The win celebration used one scene-blind near-black for both, so the one screen
     * that is meant to be the warmest moment in the game was the only one with no key
     * light: the roses went grey-brown and the card mid-flight read as a UI sheet rather
     * than as paper. Relative luminance .0057 (room) and .0036 (garden) against the .0043
     * of the colour they replace, so the ghost suppression a heavy scrim exists for is
     * unchanged either way.
     */
    private static final int NIGHT_ROOM = rgb(26, 13, 22);
    private static final int NIGHT_GARDEN = rgb(8, 10, 30);

    /** The colour each scene's key light throws, for the bloom around it. */
    private static final int GLOW_ROOM = rgb(255, 196, 128);
    private static final int GLOW_GARDEN = rgb(158, 196, 255);

    /** Where that key light is, as a fraction of the picture. */
    private static final float KEY_ROOM_X = .947f;
    private static final float KEY_ROOM_Y = .221f;
    private static final float KEY_GARDEN_X = .776f;
    private static final float KEY_GARDEN_Y = .190f;

    /**
     * How bright the bloom on the key point is allowed to get, in alpha at its centre.
     *
     * <p>A menu floats on a large opaque panel, so the art behind it can carry a real
     * highlight; the board is paper and nothing in the room may out-shine it, so while
     * playing the bloom is halved. Spread over {@link #BLOOM_RINGS} ovals it is two levels
     * a ring at most, which is the same no-banding rule {@link Draw#glow} keeps.
     */
    private static final int BLOOM_MENU = 28;
    private static final int BLOOM_PLAYING = 14;
    private static final int BLOOM_RINGS = 14;
    /** The bloom's reach, as a fraction of the screen, from widest ring to tightest. */
    private static final float BLOOM_FAR = .30f;
    private static final float BLOOM_NEAR = .06f;
    /** How much the bloom breathes, and how long one breath takes. */
    private static final float BLOOM_BREATH = .12f;
    private static final float BLOOM_BREATH_MS = 5200f;

    /**
     * How far the picture drifts, as a fraction of the screen, and how long each axis
     * takes to come back.
     *
     * <p>A completely motionless painting behind a menu is the clearest tell there is
     * between a hobby app and a shipped living-room title. This is ±9 px horizontally on an
     * 18-second cycle and ±5 px vertically on a 26-second one at 1080p — an order of
     * magnitude slower than anything else on screen, and slower than the eye tracks. The
     * destination rect is overscanned by the same fraction first, so the drift never
     * uncovers an edge; it costs 1.6% of the art, and the room's lantern at normalised
     * x = .047 keeps 75 px of clearance at the extreme.
     */
    private static final float DRIFT = .008f;
    private static final float DRIFT_X_MS = 9000f;
    private static final float DRIFT_Y_MS = 13000f;

    /**
     * How long the room takes to become the garden.
     *
     * <p>The scene used to change between one frame and the next, on a two-picture cadence,
     * and only while a board was on screen — so the menus were always the room and the swap
     * itself was a cut nobody could account for. It now follows the picture you are on, one
     * evening indoors and the next in the garden, and it crosses over the {@code 340 ms}
     * page turn {@code WinScene.drawHandover} is already drawing.
     */
    private static final float FADE_MS = 700f;

    /** How quickly the pool slides to a new playfield: 250 ms to within 5% of it. */
    private static final float FOCUS_TAU_MS = 85f;

    /** The clock a caller passes when it has none. See the class notes. */
    private static final long NO_CLOCK = 0;

    private final RectF bounds = new RectF();

    /**
     * The illustration's own paint, deliberately separate from {@link #shade}.
     *
     * <p>{@code Canvas.drawBitmap} honours the paint's alpha. When one paint was shared,
     * the scrim colour set at the end of a frame was still on it at the start of the
     * next, so every frame after the first drew the backdrop at ~59% alpha over whatever
     * was already in the buffer — invisible in the screenshot harness, where every PNG is
     * frame one, and a slow accumulating haze on a live view. Two paints, no ordering to
     * remember.
     */
    private final Paint scene = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint shade = new Paint(Paint.DITHER_FLAG);

    private Bitmap room;
    private Bitmap garden;

    /** The scene drawn last frame, the one before it, and when they changed places. */
    private Bitmap showing;
    private Bitmap leaving;
    private long sceneChangedAt;

    /** Where the pool is centred, as a fraction of the screen, and its target. */
    private float poolX = .50f;
    private float poolY = .52f;
    private long lastFrameAt;

    /** The board the pool was last measured against; see {@link #focusOn}. */
    private BoardLayout measured;
    private Puzzle measuredPuzzle;
    private float measuredWidth;
    private float measuredHeight;

    /**
     * The deep tint of whatever was drawn this frame.
     *
     * <p>Static so that a full-screen dim can ask for it without being handed a
     * {@code Backdrop} it has no other use for — the same shape as
     * {@code HudScene.remoteOnly()}. There is one backdrop and one screen, so "the scene
     * being dimmed" is a fact about the frame rather than about an instance.
     */
    private static int nightTint = NIGHT_ROOM;

    public void setScenes(Bitmap roomScene, Bitmap gardenScene) {
        this.room = roomScene;
        this.garden = gardenScene;
    }

    /**
     * The colour a full-screen dim should be drawn in: the deep counterpart of the scene
     * that was drawn this frame, crossed over with the previous one while they change.
     */
    public static int nightTint() {
        return nightTint;
    }

    /**
     * Which scene the current picture belongs to. The evening alternates one picture
     * indoors and the next in the garden, and the menus follow the room you are in rather
     * than always showing the lamp.
     */
    public Bitmap sceneFor(GameState game) {
        if (garden == null) {
            return room;
        }
        return game.solved % 2 == 1 ? garden : room;
    }

    /** The five-argument form kept for callers with no clock. See the class notes. */
    public void draw(Canvas canvas, float width, float height, UiState ui, GameState game) {
        draw(canvas, width, height, ui, game, NO_CLOCK);
    }

    public void draw(Canvas canvas, float width, float height, UiState ui, GameState game,
                     long now) {
        boolean playing = ui.screen == UiState.GAME;
        Bitmap picture = sceneFor(game);
        float crossed = noteScene(picture, now);
        focusOn(width, height, playing, game, now);
        int tint = tintFor(picture, crossed);
        nightTint = nightFor(picture, crossed);

        drawScene(canvas, width, height, picture, crossed, now);

        int extra = ui.highContrastOn ? VEIL_CONTRAST : 0;
        fill(canvas, 0, 0, width, height, tint, (playing ? VEIL_PLAYING : VEIL_MENU) + extra);
        drawKeyLight(canvas, width, height, picture, crossed, playing, now);
        drawPool(canvas, width, height, tint,
                (playing ? POOL_PLAYING : POOL_MENU) + extra / 3);
        drawEdgeBands(canvas, width, height, tint, playing, extra);
    }

    /**
     * The illustration itself: the outgoing scene, then the incoming one over it, both
     * drifting together so the cross-fade is one room becoming another rather than two
     * pictures sliding past each other.
     */
    private void drawScene(Canvas canvas, float width, float height, Bitmap picture,
                           float crossed, long now) {
        if (picture == null) {
            shade.setColor(rgb(30, 24, 48));
            canvas.drawRect(0, 0, width, height, shade);
            return;
        }
        drift(width, height, now);
        if (crossed < 1 && leaving != null) {
            scene.setColor(Color.WHITE);
            canvas.drawBitmap(leaving, null, bounds, scene);
        }
        scene.setColor(Draw.withAlpha(Color.WHITE, Math.round(255 * crossed)));
        canvas.drawBitmap(picture, null, bounds, scene);
    }

    /**
     * The destination rect for the picture: overscanned by {@link #DRIFT} and offset by two
     * slow sines that never agree, so the room never returns to quite the same place.
     * Calmer Animation gets the plain overscanned rect, which is genuinely still.
     */
    private void drift(float width, float height, long now) {
        float reachX = width * DRIFT;
        float reachY = height * DRIFT;
        float dx = 0;
        float dy = 0;
        if (now != NO_CLOCK && !Comfort.get().calmMotion) {
            dx = reachX * .6f * (float) Math.sin(now / DRIFT_X_MS);
            dy = reachY * .4f * (float) Math.sin(now / DRIFT_Y_MS);
        }
        bounds.set(-reachX + dx, -reachY + dy, width + reachX + dx, height + reachY + dy);
    }

    /**
     * Notices a scene change and reports how far the new one has arrived, 0 to 1.
     *
     * <p>Without a clock, and with Calmer Animation on, the answer is 1 straight away: a
     * fade needs frames, and {@code Renderer.animating} stops asking for them on a menu the
     * moment the player has said they want the room to hold still.
     */
    private float noteScene(Bitmap picture, long now) {
        if (showing != picture) {
            leaving = showing;
            showing = picture;
            sceneChangedAt = now;
        }
        if (leaving == null) {
            return 1;
        }
        if (now == NO_CLOCK || sceneChangedAt == NO_CLOCK || Comfort.get().calmMotion) {
            leaving = null;
            return 1;
        }
        float crossed = Draw.easeInOut(Draw.clamp01((now - sceneChangedAt) / FADE_MS));
        if (crossed >= 1) {
            leaving = null;
        }
        return crossed;
    }

    private int tintFor(Bitmap picture, float crossed) {
        int arriving = picture != null && picture == garden ? TINT_GARDEN : TINT_ROOM;
        if (crossed >= 1 || leaving == null) {
            return arriving;
        }
        return Draw.blend(leaving == garden ? TINT_GARDEN : TINT_ROOM, arriving, crossed);
    }

    private int nightFor(Bitmap picture, float crossed) {
        int arriving = picture != null && picture == garden ? NIGHT_GARDEN : NIGHT_ROOM;
        if (crossed >= 1 || leaving == null) {
            return arriving;
        }
        return Draw.blend(leaving == garden ? NIGHT_GARDEN : NIGHT_ROOM, arriving, crossed);
    }

    /**
     * A shallow bloom on the scene's own key light: fourteen concentric ovals, each
     * carrying two levels at most, breathing on a five-second sine.
     *
     * <p>Drawn after the veil, so it lifts the lamp back out of the shade rather than
     * competing with it, and capped well under the veil's own strength so that adding light
     * can never cost a text contrast the palette has already been measured against.
     */
    private void drawKeyLight(Canvas canvas, float width, float height, Bitmap picture,
                              float crossed, boolean playing, long now) {
        if (picture == null) {
            return;
        }
        if (crossed < 1 && leaving != null) {
            bloom(canvas, width, height, leaving, playing, 1 - crossed, now);
        }
        bloom(canvas, width, height, picture, playing, crossed, now);
    }

    private void bloom(Canvas canvas, float width, float height, Bitmap picture,
                       boolean playing, float strength, long now) {
        boolean night = picture == garden;
        float cx = width * (night ? KEY_GARDEN_X : KEY_ROOM_X);
        float cy = height * (night ? KEY_GARDEN_Y : KEY_ROOM_Y);
        float peak = (playing ? BLOOM_PLAYING : BLOOM_MENU) * strength;
        if (now != NO_CLOCK && !Comfort.get().calmMotion) {
            peak *= 1 + BLOOM_BREATH * (float) Math.sin(now / BLOOM_BREATH_MS);
        }
        if (peak < 1) {
            return;
        }
        int color = night ? GLOW_GARDEN : GLOW_ROOM;
        for (int ring = 0; ring < BLOOM_RINGS; ring++) {
            int step = Draw.glowRingAlpha(ring, BLOOM_RINGS, peak);
            if (step <= 0) {
                continue;
            }
            float t = ring / (float) (BLOOM_RINGS - 1);
            float rx = Draw.lerp(width * BLOOM_FAR, width * BLOOM_NEAR, t);
            float ry = Draw.lerp(height * BLOOM_FAR, height * BLOOM_NEAR, t);
            bounds.set(cx - rx, cy - ry, cx + rx, cy + ry);
            shade.setColor(Draw.withAlpha(color, step));
            canvas.drawOval(bounds, shade);
        }
    }

    /**
     * Slides the pool under whatever the playfield is now.
     *
     * <p>The pool was fixed at the middle of the screen while the class notes called it
     * "centred on the playfield": measured on a 10x10 board at 1920x1080 the paper card
     * spans x 350–1150, a centre of .391, so the pool was settling a strip of empty room
     * 210 px to the right of the thing it was for. The playfield's own geometry is the
     * answer to where it is, so the pool asks {@link BoardLayout} — once per picture, not
     * once per frame: the answer only changes when the board does, and building one costs a
     * 24-pass bisection.
     *
     * <p>Menus keep the middle of the screen, which is where their panel is.
     */
    private void focusOn(float width, float height, boolean playing, GameState game,
                         long now) {
        float targetX = .50f;
        float targetY = .52f;
        if (playing) {
            BoardLayout board = measure(width, height, game.puzzle);
            targetX = (board.cardLeft() + board.cardRight()) / 2 / width;
            targetY = (board.cardTop() + board.cardBottom()) / 2 / height;
        }
        float amount = 1;
        if (now != NO_CLOCK && lastFrameAt != NO_CLOCK && now > lastFrameAt) {
            amount = Draw.approachRate(Math.min(now - lastFrameAt, 100), FOCUS_TAU_MS);
        }
        lastFrameAt = now;
        poolX = Draw.lerp(poolX, targetX, amount);
        poolY = Draw.lerp(poolY, targetY, amount);
    }

    private BoardLayout measure(float width, float height, Puzzle puzzle) {
        if (measured == null || measuredPuzzle != puzzle || measuredWidth != width
                || measuredHeight != height) {
            measured = new BoardLayout(width, height, puzzle, true);
            measuredPuzzle = puzzle;
            measuredWidth = width;
            measuredHeight = height;
        }
        return measured;
    }

    /**
     * A soft dark pool under the playfield, built from concentric ovals so it needs no
     * shader — the stubs the screenshot harness runs on have none, and neither does this.
     *
     * <p>Each ring adds a couple of alpha, so the middle reaches roughly {@code peak} and
     * the outermost ring is all but invisible. The outer ellipse is sized to fall short of
     * the corners: {@code (.5/.62)² + (.52/.70)² > 1}, so the four corners of the picture
     * carry the veil alone.
     */
    private void drawPool(Canvas canvas, float width, float height, int tint, int peak) {
        if (peak <= 0) {
            return;
        }
        float cx = width * poolX;
        float cy = height * poolY;
        int step = Math.max(1, Math.round(peak / (float) POOL_RINGS));
        for (int ring = 0; ring < POOL_RINGS; ring++) {
            float t = ring / (float) (POOL_RINGS - 1);
            float rx = Draw.lerp(width * .62f, width * .28f, t);
            float ry = Draw.lerp(height * .70f, height * .26f, t);
            bounds.set(cx - rx, cy - ry, cx + rx, cy + ry);
            shade.setColor(Draw.withAlpha(tint, step));
            canvas.drawOval(bounds, shade);
        }
    }

    /**
     * Graded bands along the top and bottom edges. The title strip and the message ribbon
     * are the only copy in the game with no panel under them, so this is the shade that
     * pays for them; it falls off quadratically and is gone by a fifth of the way in.
     */
    private void drawEdgeBands(Canvas canvas, float width, float height, int tint,
                               boolean playing, int extra) {
        int top = (playing ? BAND_TOP : BAND_MENU) + extra / 2;
        int bottom = (playing ? BAND_BOTTOM : BAND_MENU) + extra / 2;
        float topDepth = height * BAND_TOP_DEPTH;
        float bottomDepth = height * BAND_BOTTOM_DEPTH;

        for (int i = 0; i < BAND_STEPS; i++) {
            float near = i / (float) BAND_STEPS;
            float far = (i + 1) / (float) BAND_STEPS;
            // Quadratic falloff, sampled at the middle of each slice. The slices sit
            // side by side rather than on top of one another, so each one carries its
            // own share of the peak outright.
            float weight = 1 - (near + far) / 2;
            weight *= weight;

            fill(canvas, 0, near * topDepth, width, far * topDepth, tint,
                    Math.round(top * weight));
            fill(canvas, 0, height - far * bottomDepth, width,
                    height - near * bottomDepth, tint, Math.round(bottom * weight));
        }
    }

    private void fill(Canvas canvas, float left, float top, float right, float bottom,
                      int tint, int alpha) {
        if (alpha <= 0) {
            return;
        }
        shade.setStyle(Paint.Style.FILL);
        shade.setColor(Draw.withAlpha(tint, alpha));
        canvas.drawRect(left, top, right, bottom, shade);
    }

    /**
     * Opaque ARGB, worked out here rather than through {@code android.graphics.Color}, for
     * the reason {@link Theme} states: the unit tests run with {@code returnDefaultValues},
     * where every {@code Color} static answers zero, so a tint built by {@code Color.rgb}
     * is black in every test that can see it — including anything asserting about
     * {@link #nightTint()}.
     */
    private static int rgb(int red, int green, int blue) {
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }
}
