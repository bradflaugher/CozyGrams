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
 *   <li>a soft elliptical <b>pool</b> centred on the playfield, built from concentric
 *       ovals, that settles the board and the side panel onto something and fades to
 *       nothing well before the corners;</li>
 *   <li><b>edge bands</b> at the top and bottom, where the title strip and the message
 *       ribbon float directly on the picture with no panel of their own.</li>
 * </ol>
 *
 * <p>The result is roughly the same protection under the header and the ribbon as before,
 * a little less around the board, and less than a third of it in the four corners — which
 * is exactly where both illustrations keep their best material: the lamp and the bookshelf,
 * the curtain and the window, the lantern and the tea table.
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
    private static final int TINT_ROOM = Color.rgb(40, 20, 34);
    private static final int TINT_GARDEN = Color.rgb(12, 16, 44);

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

    public void setScenes(Bitmap roomScene, Bitmap gardenScene) {
        this.room = roomScene;
        this.garden = gardenScene;
    }

    /** Chooses a scene for the current board; the room and garden alternate in pairs. */
    public Bitmap sceneFor(UiState ui, GameState game) {
        if (garden == null) {
            return room;
        }
        boolean nightTime = ui.screen == UiState.GAME && (game.solved / 2) % 2 == 1;
        return nightTime ? garden : room;
    }

    public void draw(Canvas canvas, float width, float height, UiState ui, GameState game) {
        Bitmap picture = sceneFor(ui, game);
        boolean night = picture != null && picture == garden;
        int tint = night ? TINT_GARDEN : TINT_ROOM;

        if (picture != null) {
            bounds.set(0, 0, width, height);
            scene.setColor(Color.WHITE);
            canvas.drawBitmap(picture, null, bounds, scene);
        } else {
            shade.setColor(Color.rgb(30, 24, 48));
            canvas.drawRect(0, 0, width, height, shade);
        }

        boolean playing = ui.screen == UiState.GAME;
        int extra = ui.highContrastOn ? VEIL_CONTRAST : 0;

        fill(canvas, 0, 0, width, height, tint, (playing ? VEIL_PLAYING : VEIL_MENU) + extra);
        drawPool(canvas, width, height, tint,
                (playing ? POOL_PLAYING : POOL_MENU) + extra / 3);
        drawEdgeBands(canvas, width, height, tint, playing, extra);
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
        float cx = width * .50f;
        float cy = height * .52f;
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
}
