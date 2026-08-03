package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * The celebration — the reward for finishing a picture together.
 *
 * <p>It is choreographed rather than simply shown, in four beats. The room holds
 * <em>perfectly still</em> on the board they just finished; the puzzle then
 * <em>resolves into the picture where it stands</em>, its clue digits, ticks, crosses and
 * grid rules dissolving into the paper they were written on while the squares recolour;
 * the picture <em>lifts off the table</em> into a frame of its own; and then the name, the
 * message, the shared credit and finally the invitation to carry on each arrive on their
 * own beat. Everything has landed by {@link #ARRIVED_MS}.
 *
 * <p>There is no score, no timer, no rank and no comparison between the players. The move
 * count is a memento of a thing made together, never a result.
 *
 * <p><b>The room is never switched off.</b> This used to open with a fade to 93% black:
 * the scrim reached 238/255 in 200&nbsp;ms while the picture had travelled 15% of the way
 * off the table, so the last square the pair placed, its fill pulse and the finished board
 * were all extinguished inside ten frames. The measured frame was mean RGB (41,35,50) with
 * a 95th percentile of 127 — against (100,92,101)/246 for gameplay — and it read as a
 * crash. The scrim now waits at {@link #SCRIM_HOLD} (21%) while the pair look at what they
 * made, deepens only to {@link Theme#SCRIM_PEAK} as the puzzle turns into the picture, and
 * eases back to {@link Theme#SCRIM_REST} once the picture is home. Nobody has to lose the
 * room to be given the picture.
 *
 * <p><b>Confetti.</b> The drift has the whole safe width — {@link Effects#clearReadingColumn}
 * — because the two surfaces on this screen are opaque and are painted over it. Reserving
 * the composition as a reading column squeezed all sixty-eight particles into two 77&nbsp;px
 * lanes hard against the overscan boundary, which reads as debris in the corners rather
 * than as warmth around the card. A heart passing behind the thing they made reads as
 * depth; draw order is what protects the words.
 *
 * <p><b>Stillness.</b> A win card that repaints for ever is a panel burning a static
 * picture in a dark room at 60&nbsp;fps. The one thing that moves after the beats have
 * landed is the invitation's breath, and it takes {@link #INVITE_BREATHS} of them and then
 * holds. From {@link #stillAtMs()} onward this scene draws the same frame for ever, which
 * is what lets {@code Renderer.animating} answer honestly and the loop go to sleep.
 */
public final class WinScene {

    // ---- The beat sheet --------------------------------------------------------------
    //
    // Every constant is milliseconds after ui.winAt. The beats overlap slightly on
    // purpose: each one begins while the previous is still settling, which reads as one
    // continuous moment rather than a queue of animations.

    /**
     * The room holds still on the finished board for this long before anything moves.
     *
     * <p>There was no hold at all — the scrim started ramping on frame one — so nobody ever
     * saw the puzzle they had just finished. This is the beat that costs the least and buys
     * the most: two people look at their own completed board, in the room they solved it
     * in, with the fill pulse from the last square still playing out.
     */
    static final float HOLD_MS = 220f;

    /**
     * The squares begin recolouring into the picture, in place, on the board.
     *
     * <p>A hair before {@link #RESOLVE_AT} so the sheet never comes up over a board that
     * has not started changing: the first cells are already blooming when the paper starts
     * to cover the workings.
     */
    static final float REVEAL_AT = 220f;
    /** Spread between the first square and the last; no cell is ever later than this. */
    static final float REVEAL_SPREAD_MS = 400f;
    /** How long one square takes to pop into place once its turn arrives. */
    static final float CELL_POP_MS = 170f;

    /**
     * The workings dissolve into the sheet they were written on: the clue digits, the row
     * and column ticks, the crosses and the grid rules all go under fresh paper while the
     * squares recolour where they stand.
     *
     * <p>This is one draw, not a new pass. Until {@link #LIFT_AT} the picture's card
     * <em>is</em> the board's card — same rectangle, same {@link Theme#RADIUS_CARD} — so
     * raising {@link #paperOpacity} over the board is what erases it, and the picture cells
     * are already being drawn on top at board scale. The transition used to be "the screen
     * goes black and a grey square grows out of it", which is why nobody ever felt the
     * puzzle <em>become</em> the picture.
     */
    static final float RESOLVE_AT = 260f;
    static final float RESOLVE_MS = 320f;

    /** Then the picture lifts off the resolved board and flies into a frame of its own. */
    static final float LIFT_AT = 580f;
    static final float LIFT_MS = 420f;

    /**
     * When the board underneath has been completely painted over and can stop being drawn.
     *
     * <p>{@code Renderer} reads this. It is the instant the resolve finishes, not the
     * instant the lift does: by {@link #LIFT_AT} the sheet is fully opaque over the board's
     * own card and occupies exactly that card's rectangle, so the hand-over is invisible
     * and there is provably nothing of the puzzle left on screen to remove. It used to sit
     * at the end of the lift, which meant the board, its ticks, its crosses and the live
     * rail were all still being drawn <em>behind a card that was only 30% opaque and moving
     * away from them</em> — the clue digits ghosted straight through the picture for the
     * whole flight.
     */
    public static final long BOARD_GONE_MS = (long) LIFT_AT;

    /** When the picture has arrived in its frame and the room may come back up. */
    static final float PICTURE_HOME_MS = LIFT_AT + LIFT_MS;
    /** How long the room takes to ease back once the picture is home. */
    private static final float SETTLE_MS = 300f;

    /**
     * The plaque that will hold the words slides up, just ahead of the first of them.
     *
     * <p>Early — earlier than the picture settles. The card and the plaque are the two
     * halves of one composition, and while only one of them exists the screen is a bright
     * rectangle sitting on its own. It is only eighty milliseconds ahead of the name
     * because the panel now unrolls with its content (see {@link #drawPlaque}); when it
     * arrived at full height it stood as a 744x696 empty box for four hundred milliseconds
     * and read as a loading placeholder.
     */
    static final float PLAQUE_AT = 800f;
    static final float PLAQUE_MS = 280f;

    static final float NAME_AT = 880f;
    static final float NAME_MS = 260f;
    static final float MESSAGE_AT = 1020f;
    static final float MESSAGE_MS = 240f;
    static final float CREDIT_AT = 1140f;
    static final float CREDIT_MS = 240f;
    static final float JOURNEY_AT = 1250f;
    static final float JOURNEY_MS = 240f;
    /** The invitation to carry on — deliberately the last thing to arrive. */
    static final float INVITE_AT = 1350f;
    static final float INVITE_MS = 280f;
    static final float HINT_AT = 1500f;
    static final float HINT_MS = 240f;

    /** The instant the last beat has finished arriving. Nothing enters after this. */
    static final float ARRIVED_MS = HINT_AT + HINT_MS;

    // ---- Stillness -------------------------------------------------------------------

    /**
     * One full breath of the invitation, in milliseconds — about twenty-five a minute,
     * which is a resting rate rather than an attention-seeking blink.
     */
    static final float INVITE_BREATH_MS = 2400f;

    /** How many breaths the invitation takes before it holds perfectly still. */
    static final int INVITE_BREATHS = 3;

    /**
     * The first instant, in milliseconds after {@code ui.winAt}, at which nothing this
     * scene draws can change again <em>with the game's full motion on</em>.
     *
     * <p>A breath ends exactly on a zero crossing of its own sine, so the hold is not a
     * freeze: the value it stops at is the value it was heading for. Callers should ask
     * {@link #stillAtMs()} rather than reading this, because calmed motion has no breath
     * to wait for.
     */
    public static final long STILL_AT_MS =
            (long) (INVITE_AT + INVITE_BREATH_MS * INVITE_BREATHS);

    /**
     * How long {@code Renderer.animating} must keep asking for frames after a win.
     *
     * <p>Nobody read {@link #STILL_AT_MS}. {@code Renderer} answered
     * {@code !calmMotion || effects.busy(now)} for the win card, which is wrong in both
     * directions: with Calmer Animation on it went false as soon as the last pulse died
     * (~760 ms), so the view stopped repainting <em>mid-entrance</em> and the credit line
     * and the invitation never arrived at all; with it off it was true for ever, so a
     * finished puzzle repainted a static 576&nbsp;px near-white card at 60&nbsp;fps all
     * evening — the exact burn-in this class claims to have solved.
     *
     * <p>Under calm there is no breath, no name pop, no pill spring and no drift, so the
     * last thing that can change is the final beat landing; the extra frame of slack is so
     * the loop's last paint is genuinely the terminal one rather than one short of it.
     */
    public static long stillAtMs() {
        return Comfort.get().calmMotion ? (long) ARRIVED_MS + 60 : STILL_AT_MS;
    }

    /**
     * How long the win card still has something moving on it: the choreography, and then the
     * drift, until the room hushes.
     *
     * <p>{@link #stillAtMs()} is when the <em>beats</em> are over, and it used to be when the
     * card stopped entirely — the last second of the celebration was three pixel-identical
     * frames. {@link #drawMotes} keeps one slow thing alive past it, and this is what tells
     * {@code Renderer.animating} to keep asking for frames while it does.
     *
     * <p>It ends at {@link Theme#IDLE_HUSH_FULL_MS}, which is the point the whole game agrees
     * somebody has actually walked away: past it the card is genuinely static and the
     * renderer can stop. Calmer Animation gets the old answer unchanged, because there is
     * nothing left to drive.
     */
    public static long ambientUntilMs() {
        return Comfort.get().calmMotion ? stillAtMs()
                : Math.max(stillAtMs(), Theme.IDLE_HUSH_FULL_MS);
    }

    // ---- The page turn ----------------------------------------------------------------

    /**
     * How long the wash between one picture and the next lasts.
     *
     * <p>Chapter to chapter used to be a single-frame snap: {@code nextPuzzle} cleared the
     * win card and the very next frame drew a fresh full-brightness grid. For an
     * eighteen-page book whose win card is choreographed over a second and a half, the
     * transition between its pages was the one moment in the whole flow with no motion in
     * it at all.
     */
    public static final long HANDOVER_MS = 340;

    // ---- Composition -----------------------------------------------------------------

    /**
     * How much of the width the picture and the words occupy between them.
     *
     * <p>It was .72, which the drift then had to squeeze around. The drift no longer lives
     * in the leftovers (see {@link Effects#clearReadingColumn}), so the composition can
     * take the room it needs; what is left either side is air, and it is equal because the
     * composition is centred. .78 still leaves 211&nbsp;px clear at 1920, which is more
     * than twice {@link Theme#SAFE_AREA}.
     */
    private static final float COMPOSITION_SPAN = .78f;

    /**
     * How large the picture's frame is allowed to be, against the screen height and against
     * the composition's own width.
     *
     * <p>Measured at HEAD: the paper card was 576x576 = 332k px² while the plaque was
     * 744x696 = 518k px² — the words held 1.6 times the area of the thing two people had
     * spent ten minutes making, and 1.9 times it in story mode. The width term binds on
     * every 16:9 screen, so raising the span and the fraction together is what actually
     * moves it: the card is now 666x666 = 444k px², up 32% in area, and the plaque column
     * comes out at 651&nbsp;px, which is the widest the copy can be squeezed to and still
     * clear {@link Theme#MIN_PROSE_SP}.
     *
     * <p>That last clause is the real constraint and it is worth stating, because it is
     * what stops the picture growing further. At 1920x1080 the longest invitation, "Press A
     * to open the book again", measures 538&nbsp;px bold at the 36&nbsp;px prose floor, and
     * its pill needs another {@link Theme#PILL_PAD_X}: 625&nbsp;px of the 651 available.
     * Twenty-six pixels of headroom is what the picture has left to take.
     */
    private static final float HERO_OF_HEIGHT = .62f;
    private static final float HERO_OF_SPAN = .445f;

    /**
     * How dark the room goes while it is holding on the finished board.
     *
     * <p>21%. Deep enough to say "this moment is over" and to sit the picture forward of
     * the illustrated room, nowhere near enough to take the board away. The two deeper
     * values this eases into live in {@link Theme#SCRIM_PEAK} and {@link Theme#SCRIM_REST}.
     */
    private static final int SCRIM_HOLD = 54;

    /**
     * The face of the one button on this screen. Candle gold: the game's own accent, and
     * pointedly neither Rose's nor Sky's, because the invitation is the game asking rather
     * than either player. Its contrast against {@link Theme#PANEL}, and that of
     * {@link Theme#textOn} against it, are both asserted in {@code WinSceneTest}.
     */
    static final int CTA_FACE = Theme.GOLD;

    /**
     * How long before any button will move on. Read by {@code CozyGameView}.
     *
     * <p>Not a magic gate: it is the moment the invitation has finished arriving, which is
     * {@link #INVITE_AT} plus {@link #INVITE_MS}. Nothing that carries meaning is cut short
     * — the picture has finished assembling by {@link #revealDoneMs} (790 ms at worst on
     * every board the game can deal), the name by 1140 ms and the message by 1260 — and a
     * player who presses before it simply runs the rest of the celebration out immediately.
     * It moved 1100 -> 1630 with the hold and the resolve; the only thing still arriving
     * after it is the "Back returns to the menu" hint.
     */
    public static final long INPUT_DELAY_MS = (long) (INVITE_AT + INVITE_MS);

    // ---- Copy ------------------------------------------------------------------------

    /** Shown when both players are at the table. Warm, specific, never a compliment
     * about winning. */
    private static final String[] MESSAGES = {
            "You make a lovely team.",
            "Piece by piece, you got there.",
            "Another one for the wall.",
            "Nobody rushed. Look what happened.",
            "That one came out beautifully.",
            "Small squares, one whole picture.",
            "A good way to spend an evening.",
            "Look what was hiding in there."
    };

    /** The same warmth for one player, without pretending there were two. */
    private static final String[] SOLO_MESSAGES = {
            "Quietly done, and done well.",
            "Piece by piece, you got there.",
            "Another one for the wall.",
            "You never rushed. Look what happened.",
            "That one came out beautifully.",
            "Small squares, one whole picture.",
            "A good way to spend an evening.",
            "Look what was hiding in there."
    };

    /**
     * Small numbers read better as words in a sentence than as digits.
     *
     * <p>This was a flat table that stopped at "twenty", which was one clear of the
     * longest thing the game ever counted — until the Story Book grew from eighteen
     * chapters to twenty-four and the opening line came out as "The book is open — 23
     * chapters to come." One numeral in the middle of a sentence is enough to make the
     * whole line read like a status bar, and it was the very first thing the book says.
     *
     * <p>So the table stops at nineteen, where English stops inventing words, and
     * {@link #word(int)} composes everything above it from {@link #TENS_WORDS}. The book
     * can now grow to ninety-nine chapters before the voice breaks again, which is longer
     * than anyone will author.
     */
    private static final String[] NUMBER_WORDS = {
            "no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen"
    };

    /** Indexed by tens, so {@code TENS_WORDS[2]} is the "twenty" in "twenty-four". */
    private static final String[] TENS_WORDS = {
            "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty",
            "ninety"
    };

    private final Draw draw;

    /** The plaque's layout, rebuilt in place each frame so drawing allocates nothing. */
    private final Plaque plaque = new Plaque();

    /** Reveal offsets for the picture on the table, rebuilt only when the board changes. */
    private Puzzle scheduledFor;
    private float[] schedule;

    /** Where the board sat, so the picture can be resolved and lifted out of it. */
    private BoardLayout liftFrom;
    private Puzzle liftFor;
    private float liftWidth;
    private float liftHeight;

    public WinScene(Draw draw) {
        this.draw = draw;
    }

    // ---- The scene -------------------------------------------------------------------

    public void draw(Canvas canvas, float width, float height, GameState game, UiState ui,
                     Effects effects, long now) {
        boolean calm = Comfort.get().calmMotion;
        float elapsed = now - ui.winAt;
        BoardLayout board = liftLayout(width, height, game.puzzle);
        float lift = lift(elapsed);

        drawRoom(canvas, width, height, elapsed);

        // The celebration, under everything. It is given the whole safe width rather than
        // the gutters either side of the composition: both surfaces above it are opaque, so
        // draw order already keeps every particle off every word, and a heart passing
        // behind the picture reads as depth. Confined to the gutters it was two 77 px
        // slivers hard against the overscan boundary — debris in the corners of the screen
        // at the one moment the room is supposed to feel full.
        effects.clearReadingColumn();
        effects.drawParticles(canvas, draw, null, now);

        float span = width * COMPOSITION_SPAN;
        float left = compositionLeft(width);
        float frame = Math.min(height * HERO_OF_HEIGHT, span * HERO_OF_SPAN);
        float gap = Theme.scale(40);

        drawPicture(canvas, height, left, left + frame, board, game, elapsed, lift, calm);
        drawPlaque(canvas, left + frame + gap, left + span, height, game, ui, elapsed,
                calm);
    }

    /**
     * The room quietening around the finished board, and coming back up afterwards.
     *
     * <p>Keyed to the resolve rather than to a timer of its own. The dim is there to hide
     * a board that is still being drawn; while the board is the thing being looked at
     * there is nothing to hide, and once the picture is home the illustrated room is the
     * game's biggest emotional asset and should not be dimmest exactly when the pair feel
     * best.
     */
    private void drawRoom(Canvas canvas, float width, float height, float elapsed) {
        float hold = Draw.easeOut(Draw.clamp01(elapsed / HOLD_MS));
        float resolve = Draw.easeInOut(Draw.clamp01((elapsed - RESOLVE_AT) / RESOLVE_MS));
        float settled = Draw.easeInOut(Draw.clamp01(
                (elapsed - PICTURE_HOME_MS) / SETTLE_MS));
        float dim = Draw.lerp(SCRIM_HOLD * hold,
                Draw.lerp(Theme.SCRIM_PEAK, Theme.SCRIM_REST, settled), resolve);

        draw.paint().setStyle(Paint.Style.FILL);
        draw.paint().setColor(Draw.withAlpha(Theme.NIGHT, (int) dim));
        canvas.drawRect(0, 0, width, height, draw.paint());
    }

    /** How far the picture has travelled from the table to its frame, 0..1. */
    static float lift(float elapsed) {
        return Draw.easeInOut(Draw.clamp01((elapsed - LIFT_AT) / LIFT_MS));
    }

    /** Left edge of the whole composition — picture and words — at this width. */
    static float compositionLeft(float width) {
        return width * (1 - COMPOSITION_SPAN) / 2;
    }

    /** Right edge of the same. Symmetric about the centre, which is the whole point. */
    static float compositionRight(float width) {
        return width - compositionLeft(width);
    }

    // ---- The picture -----------------------------------------------------------------

    /**
     * The star of the screen: what the two of them actually made, as large as the layout
     * allows.
     *
     * <p>One draw for both halves of the transition. Until {@link #LIFT_AT} every
     * coordinate below is the board's own — the card is the board's card, the squares are
     * the board's squares — so raising the paper over it resolves the puzzle into the
     * picture where it stands. After it the same rectangle is interpolated into the hero
     * frame, so the thing that flies is the finished picture rather than a stand-in for it.
     */
    private void drawPicture(Canvas canvas, float height, float heroLeft, float heroRight,
                             BoardLayout board, GameState game, float elapsed, float lift,
                             boolean calm) {
        float pad = Theme.scale(22);
        float heroTop = height / 2 - (heroRight - heroLeft) / 2;
        float picLeft = Draw.lerp(board.left, heroLeft + pad, lift);
        float picTop = Draw.lerp(board.top, heroTop + pad, lift);
        float picSize = Draw.lerp(board.right - board.left,
                heroRight - heroLeft - pad * 2, lift);

        float frameLeft = Draw.lerp(board.cardLeft(), heroLeft, lift);
        float frameTop = Draw.lerp(board.cardTop(), heroTop, lift);
        float frameRight = Draw.lerp(board.cardRight(), heroRight, lift);
        float frameBottom = Draw.lerp(board.cardBottom(),
                heroTop + (heroRight - heroLeft), lift);
        float radius = Theme.scale(Theme.RADIUS_CARD);

        // A warm halo, as though the finished picture were catching lamplight. It fades in
        // as the last squares land and then holds perfectly still. The last page of the
        // book gets a wider, brighter one — the only time the glow is ever loud.
        //
        // Four rounded rects at alpha 13 (seven at 17 for the last chapter) is what this
        // was, and it measured as six hard edges thirteen luminance levels apart at an
        // 18 px pitch: a target pattern of nested boxes around the one picture the whole
        // game is building toward. Draw.glow spends the same brightness across as many
        // rings as Theme.GLOW_STEP_ALPHA_MAX demands and cannot band by construction.
        float glow = Draw.easeOut(Draw.clamp01(
                (elapsed - (REVEAL_AT + REVEAL_SPREAD_MS)) / 500f));
        boolean grand = isFinalChapter(game);
        draw.glow(canvas, frameLeft, frameTop, frameRight, frameBottom, radius,
                Theme.scale(grand ? 90 : 52), Theme.GOLD, (grand ? 110 : 62) * glow);

        // Only once it is off the table. On it, the board's own card carries the shadow,
        // and Draw.shadow at zero depth is three fills of the card rect at alpha 26 — a
        // 28% dark wash over the finished board for the whole of the hold.
        if (lift > 0) {
            draw.shadow(canvas, frameLeft, frameTop, frameRight, frameBottom, radius,
                    Theme.scale(9) * lift);
        }

        // The mount, which arrives with the lift: a dark border a little proud of the paper,
        // with one warm keyline inside it. The picture used to fly straight out of the board
        // and hang on the wallpaper as bare cream with a halo behind it, so what the eye got
        // was a card, not a keepsake — and the plaque beside it says "Four pictures on the
        // wall", which is a promise about framing. A mount is also what makes the halo read
        // as light falling on something rather than as a glow the paper is emitting.
        float mount = Theme.scale(11) * lift;
        if (mount > 0) {
            draw.roundRect(canvas, frameLeft - mount, frameTop - mount, frameRight + mount,
                    frameBottom + mount, radius + mount * .6f,
                    Draw.withAlpha(Theme.NIGHT, (int) (238 * lift)));
            draw.roundRectStroke(canvas, frameLeft - mount * .34f, frameTop - mount * .34f,
                    frameRight + mount * .34f, frameBottom + mount * .34f,
                    radius + mount * .2f, Math.max(1f, Theme.scale(1.4f)),
                    Draw.withAlpha(Theme.CANDLE_DEEP, (int) (150 * lift)));
        }

        draw.roundRect(canvas, frameLeft, frameTop, frameRight, frameBottom, radius,
                Draw.withAlpha(Theme.PAPER, paperOpacity(elapsed)));

        drawPictureCells(canvas, picLeft, picTop, picSize, game.puzzle, elapsed, calm);

        drawMotes(canvas, frameLeft - mount, frameTop - mount, frameRight + mount,
                frameBottom + mount, elapsed, lift);
    }

    /** How many motes drift around a finished picture. */
    private static final int MOTES = 7;
    /** How long one mote takes to rise the height of the frame, in milliseconds. */
    private static final float MOTE_RISE_MS = 11_000f;
    /** How long the drift takes to settle out once the room has begun to hush. */
    private static final float MOTE_FADE_MS = 6_000f;

    /**
     * A few motes of dust turning in the lamplight around the finished picture.
     *
     * <p>The card used to arrive at {@link #stillAtMs()} and then be a photograph: every
     * confetti particle was gone by 2.6 s and the frames after it were pixel-identical, so
     * the last second of the celebration was a still image of a result screen. One slow thing
     * still moving is the whole difference between a result and a keepsake, and dust in a
     * lamp beam is the quietest one available — it carries no information, asks for nothing,
     * and is exactly what a warm room has in it at nine in the evening.
     *
     * <p>It does not run for ever, which is what the old stillness was protecting: the drift
     * eases out over {@link #MOTE_FADE_MS} before {@link Theme#IDLE_HUSH_FULL_MS}, the same
     * clock the music fades on and the cursors settle to, so a card somebody has walked away
     * from ends up genuinely static and {@code Renderer.animating} can stop asking for
     * frames. Calmer Animation opts out entirely — see {@link #ambientUntilMs()}.
     *
     * <p>Positions come from the mote's index rather than from stored state, so this
     * allocates nothing and draws the same frame for the same millisecond.
     */
    private void drawMotes(Canvas canvas, float left, float top, float right, float bottom,
                           float elapsed, float lift) {
        if (Comfort.get().calmMotion || lift <= 0) {
            return;
        }
        float settle = Draw.clamp01(
                (Theme.IDLE_HUSH_FULL_MS - elapsed) / MOTE_FADE_MS);
        if (settle <= 0) {
            return;
        }
        float width = right - left;
        float height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }
        float reach = Theme.scale(26);
        for (int i = 0; i < MOTES; i++) {
            // A different phase, speed and lane per mote, so seven of them never line up.
            float speed = .72f + (i % 3) * .19f;
            float phase = (elapsed / (MOTE_RISE_MS / speed) + i * .137f) % 1f;
            float lane = (i * .1379f + .06f) % 1f;
            float x = left - reach + (width + reach * 2) * lane
                    + (float) Math.sin(elapsed / 2600f + i) * Theme.scale(9);
            float y = bottom + reach - (height + reach * 2) * phase;
            // Brightest in the middle of the rise, so a mote is never born or snuffed out
            // in front of the player.
            float alpha = (float) Math.sin(phase * Math.PI) * lift * settle;
            float size = Theme.scale(2.4f + (i % 3) * .8f);
            draw.circle(canvas, x, y, size,
                    Draw.withAlpha(Theme.GOLD, (int) (96 * alpha)));
        }
    }

    /**
     * The picture itself: one rounded square per filled cell, each arriving on its own beat
     * from {@link #revealDelays}.
     *
     * <p>The colour ramp is the two identity colours meeting across the diagonal — asked of
     * {@link Theme#playerColor(int)} so the colour-blind-friendly palette applies here too,
     * and the same ramp the shared progress bar uses, so the finished picture is visibly the
     * thing that bar was filling.
     */
    private void drawPictureCells(Canvas canvas, float picLeft, float picTop, float picSize,
                                  Puzzle puzzle, float elapsed, boolean calm) {
        int from = Theme.playerColor(0);
        int to = Theme.playerColor(1);
        int size = puzzle.size;
        float cell = picSize / size;
        float[] delays = scheduleFor(puzzle);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float delay = delays[y * size + x];
                if (delay < 0) {
                    continue;
                }
                float age = Draw.clamp01((elapsed - REVEAL_AT - delay) / CELL_POP_MS);
                if (age <= 0) {
                    continue;
                }
                float grow = calm ? Draw.easeOut(age) : Draw.easeOutBack(age);
                float inset = Math.max(.6f, cell * .07f) + cell * (1 - grow) * .5f;
                int color = Draw.blend(from, to,
                        (x + y) / (float) Math.max(1, (size - 1) * 2) * .85f);
                draw.roundRect(canvas, picLeft + x * cell + inset,
                        picTop + y * cell + inset,
                        picLeft + (x + 1) * cell - inset,
                        picTop + (y + 1) * cell - inset,
                        Math.max(1f, cell * .17f), Draw.withAlpha(color, age));
            }
        }
    }

    /**
     * How opaque the picture's card is, 0..1.
     *
     * <p>This used to be a fixed ramp multiplied by how much of the picture existed, with a
     * floor of .30, because the card arrived over near-black with nothing on it and a
     * near-white 620&nbsp;px sheet holding half a heart is a wince. That guard is now
     * structural rather than arithmetic: the sheet only ever comes up over the board's own
     * card, in a room dimmed to 21%, with the picture already blooming on it since
     * {@link #REVEAL_AT}. There is no instant at which it can be bright and alone, so it
     * can simply be paper.
     *
     * <p>It reaching exactly 1 at {@link #LIFT_AT} is the load-bearing part: that is what
     * lets {@code Renderer} stop drawing the puzzle there in the certainty that every clue
     * digit, tick and cross is already under paper. Pure, so the choreography can be
     * asserted about rather than eyeballed.
     */
    static float paperOpacity(float elapsed) {
        return Draw.easeOut(Draw.clamp01((elapsed - RESOLVE_AT) / RESOLVE_MS));
    }

    /** The board's geometry, rebuilt only when the surface or the board changes. */
    private BoardLayout liftLayout(float width, float height, Puzzle puzzle) {
        if (liftFrom == null || liftFor != puzzle || liftWidth != width
                || liftHeight != height) {
            liftFrom = new BoardLayout(width, height, puzzle, true);
            liftFor = puzzle;
            liftWidth = width;
            liftHeight = height;
        }
        return liftFrom;
    }

    private float[] scheduleFor(Puzzle puzzle) {
        if (schedule == null || scheduledFor != puzzle) {
            schedule = revealDelays(puzzle);
            scheduledFor = puzzle;
        }
        return schedule;
    }

    // ---- The page turn ----------------------------------------------------------------

    /**
     * The wash between one picture and the next: the page going over, rather than the hard
     * cut a chapter change used to be.
     *
     * <p>{@code since} is milliseconds since the board on the table changed. Deliberately
     * a plain fade from {@link Theme#NIGHT} rather than a titled card — the toast the
     * handover raises already names the chapter, and two things announcing the same page
     * is a transition drawing attention to itself.
     */
    public void drawHandover(Canvas canvas, float width, float height, float since) {
        float gone = handover(since);
        if (gone >= 1) {
            return;
        }
        draw.paint().setStyle(Paint.Style.FILL);
        draw.paint().setColor(Draw.withAlpha(Theme.NIGHT, (int) (210 * (1 - gone))));
        canvas.drawRect(0, 0, width, height, draw.paint());
    }

    /** How far through the page turn we are, 0..1; 1 means there is nothing to draw. */
    static float handover(float since) {
        if (since < 0) {
            return 1f;
        }
        return Draw.easeOut(Draw.clamp01(since / HANDOVER_MS));
    }

    // ---- The reveal schedule ---------------------------------------------------------

    /**
     * When each square of the picture starts to appear, in milliseconds after
     * {@link #REVEAL_AT}. Squares that are not part of the picture get {@code -1}.
     *
     * <p>The order is a bloom outward from the picture's own centre of mass, jittered so
     * the wavefront is a soft ring rather than a perfect circle. Only the squares that are
     * actually drawn take part, so the picture starts appearing the instant the reveal
     * begins — an empty top row can never leave the card blank — and the schedule is
     * normalised so the first square is always at exactly 0 and the last at no more than
     * {@link #REVEAL_SPREAD_MS}.
     */
    static float[] revealDelays(Puzzle puzzle) {
        int size = puzzle.size;
        float[] delays = new float[size * size];
        float sumX = 0;
        float sumY = 0;
        int count = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                delays[y * size + x] = -1;
                if (puzzle.solution[y][x]) {
                    sumX += x;
                    sumY += y;
                    count++;
                }
            }
        }
        if (count == 0) {
            return delays;
        }
        float centreX = sumX / count;
        float centreY = sumY / count;

        float furthest = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (puzzle.solution[y][x]) {
                    furthest = Math.max(furthest, distance(x, y, centreX, centreY));
                }
            }
        }

        float earliest = Float.MAX_VALUE;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!puzzle.solution[y][x]) {
                    continue;
                }
                float reach = furthest <= 0 ? 0
                        : distance(x, y, centreX, centreY) / furthest;
                float when = REVEAL_SPREAD_MS
                        * Draw.clamp01(reach * .80f + jitter(x, y) * .20f);
                delays[y * size + x] = when;
                earliest = Math.min(earliest, when);
            }
        }
        for (int i = 0; i < delays.length; i++) {
            if (delays[i] >= 0) {
                delays[i] = Math.max(0, delays[i] - earliest);
            }
        }
        return delays;
    }

    /** The instant the last square of a picture has finished landing. */
    static float revealDoneMs(Puzzle puzzle) {
        float latest = 0;
        for (float delay : revealDelays(puzzle)) {
            latest = Math.max(latest, delay);
        }
        return REVEAL_AT + latest + CELL_POP_MS;
    }

    private static float distance(int x, int y, float cx, float cy) {
        float dx = x - cx;
        float dy = y - cy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Deterministic per-square wobble in 0..1, so every replay looks identical. */
    private static float jitter(int x, int y) {
        int hash = x * 0x27d4eb2d ^ (y + 17) * 0x165667b1;
        hash ^= hash >>> 15;
        hash *= 0x2c1b3c6d;
        hash ^= hash >>> 13;
        return ((hash >>> 8) & 0xffff) / 65535f;
    }

    // ---- The words -------------------------------------------------------------------

    /** The rows the plaque can hold, in the order they always appear. */
    private static final int ROW_EYEBROW = 0;
    private static final int ROW_NAME = 1;
    private static final int ROW_MESSAGE = 2;
    private static final int ROW_RULE = 3;
    private static final int ROW_CREDIT = 4;
    private static final int ROW_MOVES = 5;
    private static final int ROW_CHAPTERS = 6;
    private static final int ROW_JOURNEY = 7;
    private static final int ROW_INVITE = 8;
    private static final int ROW_HINT = 9;
    private static final int MAX_ROWS = 10;

    /**
     * One frame's worth of plaque: the strings, the sizes they fit at, and the row table.
     *
     * <p>The row table is what makes the panel able to unroll. Layout used to be a single
     * 131-line method that summed a height across two conditional branches and then drew
     * seven beats while mutating a running {@code y} twenty-one times, so the sum and the
     * drawing had to be kept in lockstep by hand and neither could answer "how far down has
     * the content actually got by now". Written down once as {@code gap + height + beat} per
     * row, the same table is summed for the full height, walked against the clock for the
     * unrolled height, and stepped down while drawing.
     *
     * <p>Held as a field and refilled in place, so a frame of the win card allocates
     * nothing beyond the strings it was already building.
     */
    private static final class Plaque {
        final int[] kind = new int[MAX_ROWS];
        /** Space above this row. */
        final float[] gap = new float[MAX_ROWS];
        /** The row's own height. */
        final float[] high = new float[MAX_ROWS];
        /** When the row's beat starts, and how long it takes to arrive. */
        final float[] at = new float[MAX_ROWS];
        final float[] over = new float[MAX_ROWS];
        int rows;

        String eyebrow;
        String message;
        String[] messageLines;
        String moves;
        String journey;
        String invite;
        String hint;

        float sEyebrow;
        float sName;
        float sMessage;
        float sCredit;
        float sMoves;
        float sJourney;
        float sInvite;
        float sHint;
        float rule;
        float pill;
        float chapters;

        float centre;
        float column;
        float padY;
        boolean together;
        int solo;

        void reset() {
            rows = 0;
        }

        void add(int rowKind, float above, float height, float beatAt, float beatMs) {
            kind[rows] = rowKind;
            gap[rows] = above;
            high[rows] = height;
            at[rows] = beatAt;
            over[rows] = beatMs;
            rows++;
        }

        /** The panel's full height, which is what the composition is centred on. */
        float content() {
            float total = 0;
            for (int row = 0; row < rows; row++) {
                total += gap[row] + high[row];
            }
            return total;
        }

        /**
         * How far down the content has unrolled by now, as an offset from the first row's
         * top. Each row eases the edge from where the previous one ended to where it ends
         * itself, so the panel grows with the words rather than in steps.
         */
        float unrolled(float elapsed) {
            float edge = 0;
            float y = 0;
            for (int row = 0; row < rows; row++) {
                y += gap[row] + high[row];
                edge = Draw.lerp(edge, y, beat(elapsed, at[row], over[row]));
            }
            return edge;
        }
    }

    /**
     * The copy plaque: what it is called, what it meant, who made it, and — last — the
     * invitation to carry on.
     *
     * <p>The panel is sized to its finished content, so nothing reflows, but its bottom
     * edge follows what has actually arrived: it unrolls downward from a fixed top as the
     * name, the message, the credit and the invitation land, and is exactly full when the
     * hint does. Drawn at full height from the start it stood as a 744x696 empty dark
     * rectangle for four hundred milliseconds and then held 500&nbsp;px — 72% — of dead
     * panel under two lines of text, which reads as a loading placeholder rather than as
     * choreography.
     */
    private void drawPlaque(Canvas canvas, float left, float right, float height,
                            GameState game, UiState ui, float elapsed, boolean calm) {
        Plaque p = measurePlaque(left, right, game, ui);
        float content = p.content();
        float top = height / 2 - (content + p.padY * 2) / 2;

        float appear = beat(elapsed, PLAQUE_AT, PLAQUE_MS);
        if (appear <= 0) {
            return;
        }
        // The surface reaches full opacity in half the time it takes to finish sliding, so
        // nothing behind it is ever legible through it. Fully opaque rather than 240/255:
        // this is the topmost surface in the game, there is nothing behind it worth seeing,
        // and at 240 the celebration drift showed through the panel as dark heart-shaped
        // silhouettes sitting inside the words.
        float slide = (1 - appear) * Theme.scale(20);
        float edge = Math.max(top + p.padY * 2, top + p.padY + p.unrolled(elapsed));
        draw.panel(canvas, left, top + slide, right, edge + slide,
                (int) (255 * beat(elapsed, PLAQUE_AT, PLAQUE_MS * .45f)));

        float y = top + p.padY + slide;
        for (int row = 0; row < p.rows; row++) {
            y += p.gap[row] + p.high[row];
            drawRow(canvas, p, p.kind[row], y, game, elapsed, calm);
        }
    }

    /** Chooses the copy, fits it to the column and lays the rows out. */
    private Plaque measurePlaque(float left, float right, GameState game, UiState ui) {
        Plaque p = plaque;
        p.reset();
        p.centre = (left + right) / 2;
        p.padY = Theme.scale(44);
        p.column = right - left - Theme.scale(40) * 2;
        p.together = ui.joined[0] && ui.joined[1];
        p.solo = ui.joined[1] && !ui.joined[0] ? 1 : 0;

        p.eyebrow = eyebrow(game, p.together);
        p.message = message(game, p.together);
        p.journey = journeyLine(game);
        p.invite = invite(game);
        p.hint = "Back returns to the menu";
        p.moves = p.together
                ? game.totalMoves() + " cozy moves, made together"
                : game.totalMoves() + " cozy moves, start to finish";

        p.sEyebrow = fit(p.eyebrow, Theme.textSize(15), p.column, true);
        p.sName = fit(game.puzzle.name, Theme.textSize(Theme.TITLE), p.column, true);
        p.messageLines = wrapMessage(p.message, Theme.textSize(Theme.SUBHEAD), p.column);
        p.sMessage = fit(widestOf(p.messageLines, Theme.textSize(Theme.SUBHEAD)),
                Theme.textSize(Theme.SUBHEAD), p.column, false);
        p.sCredit = Theme.textSize(21);
        p.sMoves = fit(p.moves, Theme.textSize(Theme.CAPTION), p.column, false);
        p.sJourney = p.journey == null ? 0
                : fit(p.journey, Theme.textSize(15), p.column, false);
        // Against the room the label actually has, which is the column less the pill's own
        // padding. It used to be against .84 of the column, a fraction with nothing behind
        // it that happened to leave the longest invitation at 34.9 px — under the prose
        // floor, on the one thing on this screen a player can act on.
        p.sInvite = fit(p.invite, Theme.textSize(Theme.BODY),
                p.column - Theme.scale(Theme.PILL_PAD_X), true);
        p.sHint = fit(p.hint, Theme.textSize(13), p.column, false);

        p.rule = Theme.hairline();
        p.pill = p.sInvite * Theme.PILL_HEIGHT_EM;
        p.chapters = game.storyMode ? chapterBlockHeight(p.column) : 0;

        // The label arrives with the panel rather than with the name, so the plaque never
        // unrolls as an empty bar: at its floor height it is already holding a word.
        p.add(ROW_EYEBROW, 0, p.sEyebrow, PLAQUE_AT, PLAQUE_MS);
        p.add(ROW_NAME, Theme.scale(18), p.sName, NAME_AT, NAME_MS);
        p.add(ROW_MESSAGE, Theme.scale(20), messageHeight(p), MESSAGE_AT, MESSAGE_MS);
        p.add(ROW_RULE, Theme.scale(28), p.rule, MESSAGE_AT, MESSAGE_MS);
        p.add(ROW_CREDIT, Theme.scale(26), p.sCredit, CREDIT_AT, CREDIT_MS);
        p.add(ROW_MOVES, Theme.scale(12), p.sMoves, CREDIT_AT, CREDIT_MS);
        if (game.storyMode) {
            p.add(ROW_CHAPTERS, Theme.scale(24), p.chapters, JOURNEY_AT, JOURNEY_MS);
        }
        if (p.journey != null) {
            p.add(ROW_JOURNEY, Theme.scale(game.storyMode ? 14 : 24), p.sJourney,
                    JOURNEY_AT, JOURNEY_MS);
        }
        p.add(ROW_INVITE, Theme.scale(34), p.pill, INVITE_AT, INVITE_MS);
        p.add(ROW_HINT, Theme.scale(16), p.sHint, HINT_AT, HINT_MS);
        return p;
    }

    /** One row of the plaque, drawn against the baseline the table walked it to. */
    private void drawRow(Canvas canvas, Plaque p, int kind, float y, GameState game,
                         float elapsed, boolean calm) {
        float named = beat(elapsed, NAME_AT, NAME_MS);
        float said = beat(elapsed, MESSAGE_AT, MESSAGE_MS);
        float credited = beat(elapsed, CREDIT_AT, CREDIT_MS);
        float travelled = beat(elapsed, JOURNEY_AT, JOURNEY_MS);

        switch (kind) {
            case ROW_EYEBROW:
                draw.text(canvas, p.eyebrow, p.centre, y - p.sEyebrow * .16f, p.sEyebrow,
                        Draw.withAlpha(game.storyMode ? Theme.GOLD : Theme.SOFT_TEXT,
                                beat(elapsed, PLAQUE_AT, PLAQUE_MS)),
                        Paint.Align.CENTER, true);
                break;
            case ROW_NAME:
                drawName(canvas, p, y, game, elapsed, named, calm);
                break;
            case ROW_MESSAGE: {
                // Drawn upward from the last line, so a chapter's two-line sentence puts its
                // final line exactly where a one-line message sits and the rule under it
                // never moves between an endless win and a story one.
                int last = p.messageLines.length - 1;
                for (int i = 0; i <= last; i++) {
                    float lift = (last - i) * p.sMessage * MESSAGE_LINE_EM;
                    draw.text(canvas, p.messageLines[i], p.centre,
                            y - lift - p.sMessage * .14f + (1 - said) * Theme.scale(10),
                            p.sMessage, Draw.withAlpha(Theme.CREAM, said * .96f),
                            Paint.Align.CENTER, false);
                }
                break;
            }
            case ROW_RULE: {
                float ruleWidth = p.column * .34f;
                draw.roundRect(canvas, p.centre - ruleWidth / 2, y - p.rule,
                        p.centre + ruleWidth / 2, y, p.rule,
                        Draw.withAlpha(Theme.CREAM, said * .22f));
                break;
            }
            case ROW_CREDIT:
                // Both names, the same size, in their own colours, joined by a heart. No
                // per-player totals — this is never a contest.
                drawCredit(canvas, p.centre,
                        y - p.sCredit * .14f + (1 - credited) * Theme.scale(8), p.sCredit,
                        p.together, p.solo, credited);
                break;
            case ROW_MOVES:
                draw.text(canvas, p.moves, p.centre,
                        y - p.sMoves * .14f + (1 - credited) * Theme.scale(8), p.sMoves,
                        Draw.withAlpha(Theme.SOFT_TEXT, credited * .9f),
                        Paint.Align.CENTER, false);
                break;
            case ROW_CHAPTERS:
                drawChapters(canvas, p.centre, y - p.chapters / 2, p.column, game,
                        travelled, calm);
                break;
            case ROW_JOURNEY:
                draw.text(canvas, p.journey, p.centre, y - p.sJourney * .14f, p.sJourney,
                        Draw.withAlpha(Theme.SOFT_TEXT, travelled * .9f),
                        Paint.Align.CENTER, false);
                break;
            case ROW_INVITE:
                drawInvite(canvas, p.centre, y - p.pill, y, p.sInvite, p.invite, elapsed,
                        beat(elapsed, INVITE_AT, INVITE_MS), calm);
                break;
            case ROW_HINT:
            default:
                draw.text(canvas, p.hint, p.centre, y - p.sHint * .14f, p.sHint,
                        Draw.withAlpha(Theme.SOFT_TEXT,
                                beat(elapsed, HINT_AT, HINT_MS) * .78f),
                        Paint.Align.CENTER, false);
                break;
        }
    }

    /** The one word the pair actually came for, at the largest size on the plaque. */
    private void drawName(Canvas canvas, Plaque p, float y, GameState game, float elapsed,
                          float fade, boolean calm) {
        if (fade <= 0) {
            return;
        }
        float pop = calm ? 1 : Draw.lerp(.86f, 1f, Draw.springy(
                Draw.clamp01((elapsed - NAME_AT) / (NAME_MS * 1.6f))));
        float pivotY = y - p.sName * .45f;
        canvas.save();
        canvas.translate(p.centre, pivotY);
        canvas.scale(pop, pop);
        canvas.translate(-p.centre, -pivotY);
        draw.text(canvas, game.puzzle.name, p.centre, y - p.sName * .10f, p.sName,
                Draw.withAlpha(Theme.CREAM, fade), Paint.Align.CENTER, true);
        canvas.restore();
    }

    /** "Rose ♥ Sky", or one name alone, in the players' own colours. */
    private void drawCredit(Canvas canvas, float centre, float baseline, float size,
                            boolean together, int solo, float fade) {
        if (!together) {
            draw.text(canvas, Theme.playerName(solo), centre, baseline, size,
                    Draw.withAlpha(Theme.playerColor(solo), fade), Paint.Align.CENTER,
                    true);
            return;
        }
        String rose = Theme.playerName(0);
        String sky = Theme.playerName(1);
        float heart = size * .78f;
        float space = size * .55f;
        float roseWidth = draw.measure(rose, size, true);
        float skyWidth = draw.measure(sky, size, true);
        float total = roseWidth + space + heart + space + skyWidth;
        float x = centre - total / 2;

        draw.text(canvas, rose, x, baseline, size,
                Draw.withAlpha(Theme.playerColor(0), fade), Paint.Align.LEFT, true);
        x += roseWidth + space;
        draw.heart(canvas, x + heart / 2, baseline - size * .30f, heart,
                Draw.withAlpha(Theme.GOLD, fade * .92f));
        x += heart + space;
        draw.text(canvas, sky, x, baseline, size,
                Draw.withAlpha(Theme.playerColor(1), fade), Paint.Align.LEFT, true);
    }

    // ---- The story book row -----------------------------------------------------------

    /** How much of the pitch a filled page takes up, and how much a page still to come. */
    private static final float PIP_MARK = .60f;
    private static final float PIP_UNVISITED = .78f;

    /**
     * How close together chapter marks are allowed to sit, in design pixels — derived, so
     * that the smallest mark in the row always clears {@link Theme#MIN_PIP_PX}.
     *
     * <p>Twenty-four chapters across a 651&nbsp;px column is a 27&nbsp;px pitch, which made
     * each mark 16&nbsp;px and an unvisited one — drawn as a dot at {@code .17} of that —
     * 5.5&nbsp;px across: about four arcminutes from ten feet, which is under the threshold
     * at which a mark is identifiable at all. The row read as "some hearts" with no sense of
     * how much book was left. Below this pitch it wraps instead of shrinking, so the book
     * can grow without the row collapsing into a dotted line.
     */
    private static final float PIP_PITCH_MIN = Theme.MIN_PIP_PX / (PIP_MARK * PIP_UNVISITED);
    /** And the pitch at which more air stops helping and the row starts to straggle. */
    private static final float PIP_PITCH_MAX = 34f;
    /** Two lines read as a book; four read as a progress bar. */
    private static final int MAX_CHAPTER_LINES = 3;

    /** How many lines the chapter row needs at this column width. */
    static int chapterLines(float column) {
        int lines = 1;
        while (lines < MAX_CHAPTER_LINES
                && column / perLine(lines) < Theme.scale(PIP_PITCH_MIN)) {
            lines++;
        }
        return lines;
    }

    private static int perLine(int lines) {
        return (int) Math.ceil(chapterCount() / (double) Math.max(1, lines));
    }

    /** The pitch between two chapter marks at this column width. */
    static float chapterStep(float column) {
        return Math.min(Theme.scale(PIP_PITCH_MAX),
                column / perLine(chapterLines(column)));
    }

    /** How tall the whole chapter block is, which the plaque's row table needs. */
    static float chapterBlockHeight(float column) {
        int lines = chapterLines(column);
        return lines * chapterStep(column) * .72f + (lines - 1) * Theme.scale(8);
    }

    /**
     * The story book as rows of hearts: the chapters already filled in, the one that was
     * just finished, and the pages still to come. The newest heart is the only thing that
     * moves, and only once.
     *
     * <p>The pages still to come are dim hearts rather than dots — the same silhouette
     * family at twice the size, so the row reads as "twenty-four pages, ten filled" at a
     * glance instead of as a hyphen followed by some punctuation.
     */
    private void drawChapters(Canvas canvas, float centre, float middle, float column,
                              GameState game, float fade, boolean calm) {
        int total = chapterCount();
        int chapter = chapterNumber(game) - 1;
        int done = Math.max(chapter, Math.min(total - 1, game.storyFurthest)) + 1;
        boolean finished = isFinalChapter(game);

        int lines = chapterLines(column);
        int perLine = perLine(lines);
        float step = chapterStep(column);
        float lineHeight = step * .72f;
        float lineGap = Theme.scale(8);
        float size = step * PIP_MARK;
        float first = middle - (lines - 1) * (lineHeight + lineGap) / 2;

        for (int i = 0; i < total; i++) {
            int line = i / perLine;
            int inLine = Math.min(perLine, total - line * perLine);
            float cx = centre - step * (inLine - 1) / 2 + step * (i % perLine);
            float cy = first + line * (lineHeight + lineGap);

            if (i > chapter && i >= done) {
                draw.heart(canvas, cx, cy, size * PIP_UNVISITED,
                        Draw.withAlpha(Theme.GRID, fade * .55f));
            } else if (i == chapter) {
                float land = calm ? fade : Draw.easeOutBack(Draw.clamp01(fade * 1.15f));
                float newest = size * Draw.lerp(.3f, 1.45f, land);
                draw.glow(canvas, cx - newest / 2, cy - newest / 2, cx + newest / 2,
                        cy + newest / 2, newest / 2, Theme.scale(9), Theme.GOLD,
                        34 * fade);
                draw.heart(canvas, cx, cy, newest, Draw.withAlpha(Theme.GOLD, fade));
            } else {
                draw.heart(canvas, cx, cy, size,
                        Draw.withAlpha(finished ? Theme.GOLD : Theme.PINK, fade * .75f));
            }
        }
    }

    /**
     * The invitation to carry on: the one thing on this screen a player can act on, so it
     * is drawn as a real button rather than as a hint.
     *
     * <p>It used to be a translucent wash of {@link Theme#BLUE} inside a hairline of the
     * same, about 2:1 against the plaque it sat on — the quietest container on a screen
     * whose loudest element was a decorative picture name. A filled candle-gold pill with
     * ink on it clears 10:1 both ways: the pill against the plaque, and the label against
     * the pill. Gold rather than either player's colour, because this is the game asking,
     * not Rose or Sky.
     *
     * <p><b>It arrives at full strength and moves instead of fading.</b> A filled button at
     * partial alpha is never read as "arriving", only as "unavailable": fifty milliseconds
     * before input opened, the pill was a washed khaki with grey lettering — the exact
     * appearance convention reserves for a disabled control. The colour is up inside the
     * first third of the entrance and the rest of it is a spring on scale, which is the
     * treatment the picture's name already gets.
     *
     * <p>The breath is a halo <em>around</em> the pill, never the pill's own fill, so the
     * thing that has to be readable is at full strength in every frame including the first
     * and the last. And it is counted: {@link #INVITE_BREATHS} of them and then it holds,
     * because a pulse with no terminating condition on an otherwise static screen is a
     * panel kept awake all evening for nothing.
     */
    private void drawInvite(Canvas canvas, float centre, float top, float bottom,
                            float size, String label, float elapsed, float fade,
                            boolean calm) {
        if (fade <= 0) {
            return;
        }
        float show = Draw.clamp01(fade * 1.8f);
        float pop = calm ? 1f : Draw.lerp(.90f, 1f, Draw.springy(
                Draw.clamp01((elapsed - INVITE_AT) / (INVITE_MS * 1.6f))));
        float width = draw.measure(label, size, true) + Theme.scale(Theme.PILL_PAD_X);
        float radius = (bottom - top) / 2;
        float breath = calm ? 0f : inviteBreath(elapsed);
        float pivotY = (top + bottom) / 2;

        canvas.save();
        canvas.translate(centre, pivotY);
        canvas.scale(pop, pop);
        canvas.translate(-centre, -pivotY);
        // A halo with room to fall off in, rather than one hard gold ring three pixels out.
        draw.glow(canvas, centre - width / 2, top, centre + width / 2, bottom, radius,
                Theme.scale(10) + Theme.scale(14) * breath, CTA_FACE,
                (28 + 26 * breath) * show);
        draw.roundRect(canvas, centre - width / 2, top, centre + width / 2, bottom, radius,
                Draw.withAlpha(CTA_FACE, show));
        draw.text(canvas, label, centre, bottom - radius + size * .35f, size,
                Draw.withAlpha(Theme.textOn(CTA_FACE), show), Paint.Align.CENTER, true);
        canvas.restore();
    }

    /**
     * The invitation's breath, 0..1, bounded to {@link #INVITE_BREATHS} cycles.
     *
     * <p>Each cycle is a full half-period of {@code |sin|}, so the last one ends exactly on
     * a zero crossing: the hold is the value the breath was already arriving at, not a
     * freeze part way up. Pure, so {@link #STILL_AT_MS} can be asserted rather than trusted.
     */
    static float inviteBreath(float elapsed) {
        float breaths = (elapsed - INVITE_AT) / INVITE_BREATH_MS;
        if (breaths <= 0 || breaths >= INVITE_BREATHS) {
            return 0f;
        }
        return (float) Math.abs(Math.sin(breaths * Math.PI));
    }

    // ---- Copy selection --------------------------------------------------------------

    /** How far through its own entrance a beat is, 0..1. */
    private static float beat(float elapsed, float at, float duration) {
        return Draw.easeOut(Draw.clamp01((elapsed - at) / duration));
    }

    /**
     * Shrinks a size until the string fits, never below {@link Theme#MIN_PROSE_SP}.
     *
     * <p>The floor used to be {@link Theme#MIN_READABLE_SP} — 18 design px, 27 px at 1080p,
     * 13.5 arcminutes — for all seven strings on the plaque. {@code Theme} says in as many
     * words that that size is <em>not</em> a prose size and exists only for isolated clue
     * digits whose size is fixed by the board's geometry. Nothing on this plaque is a clue
     * digit. A line that cannot fit at the prose floor is a line that needs shortening, and
     * every string here is copy this class owns.
     */
    /**
     * The most lines the warm line under the name may turn onto, and the leading between
     * them.
     *
     * <p>One was enough while every message came from the eight-word rotation. A chapter's
     * own line is roughly twice as long — "She has claimed the warm end of the sofa. That is
     * simply how it is now." against "Nobody rushed. Look what happened." — and {@link #fit}
     * only shrinks to the prose floor before giving up, so at 1080p that sentence measured
     * 1148 px in an 843 px column and hung off both sides of the card onto the wallpaper. A
     * sentence is allowed to turn; it is not allowed to shrink under the ten-foot floor, and
     * it is certainly not allowed to leave the card.
     */
    private static final int MESSAGE_MAX_LINES = 2;
    private static final float MESSAGE_LINE_EM = 1.18f;

    /**
     * Splits a message into at most {@link #MESSAGE_MAX_LINES} balanced lines.
     *
     * <p>Balanced rather than greedy. The card is centred, so a greedy fill — pack the first
     * line, drop the remainder onto the second — leaves a long line over a short one, which
     * on a centred plaque reads as a mistake rather than as a stanza. Choosing the break that
     * minimises the wider of the two halves is one pass over the spaces and comes out even.
     */
    private String[] wrapMessage(String text, float size, float room) {
        if (text == null || text.isEmpty() || room <= 0
                || draw.measure(text, size, false) <= room) {
            return new String[]{text == null ? "" : text};
        }
        int best = -1;
        float bestWidest = Float.MAX_VALUE;
        for (int at = text.indexOf(' '); at >= 0; at = text.indexOf(' ', at + 1)) {
            float widest = Math.max(draw.measure(text.substring(0, at), size, false),
                    draw.measure(text.substring(at + 1), size, false));
            if (widest < bestWidest) {
                bestWidest = widest;
                best = at;
            }
        }
        if (best < 0) {
            return new String[]{text};
        }
        return new String[]{text.substring(0, best), text.substring(best + 1)};
    }

    /** The widest of a wrapped message's lines, for {@link #fit} to size against. */
    private String widestOf(String[] lines, float size) {
        String widest = lines[0];
        for (String line : lines) {
            if (draw.measure(line, size, false) > draw.measure(widest, size, false)) {
                widest = line;
            }
        }
        return widest;
    }

    /** How tall the message row is once its lines are counted. */
    private float messageHeight(Plaque p) {
        return p.sMessage
                * (1 + (p.messageLines.length - 1) * MESSAGE_LINE_EM);
    }

    private float fit(String text, float size, float maxWidth, boolean strong) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return size;
        }
        float measured = draw.measure(text, size, strong);
        if (measured <= maxWidth || measured <= 0) {
            return size;
        }
        return Math.max(Theme.scale(Theme.MIN_PROSE_SP), size * maxWidth / measured);
    }

    /** How many chapters the book holds; never zero, so the arithmetic is always safe. */
    private static int chapterCount() {
        return Math.max(1, PuzzleLibrary.count());
    }

    /** Which chapter was just finished, counting from one. */
    private static int chapterNumber(GameState game) {
        return Math.floorMod(game.storyIndex, chapterCount()) + 1;
    }

    /** True on the win that closes the story book — the one real milestone in the game. */
    static boolean isFinalChapter(GameState game) {
        return game.storyMode && chapterNumber(game) >= chapterCount();
    }

    /** The small label above the picture's name. */
    static String eyebrow(GameState game, boolean together) {
        if (!game.storyMode) {
            return together ? "TOGETHER YOU MADE" : "YOU MADE";
        }
        if (isFinalChapter(game)) {
            return "THE LAST CHAPTER";
        }
        if (chapterNumber(game) == 1) {
            return "THE FIRST CHAPTER";
        }
        return "CHAPTER " + chapterNumber(game) + " OF " + chapterCount();
    }

    /**
     * The warm line under the name. Finishing the book earns its own sentence.
     *
     * <p>A chapter says its own line rather than drawing from the rotation.
     * {@link PuzzleLibrary#LINES} holds twenty-four of them — "One more chapter, and then
     * really and truly lights out." — and {@code PuzzleLibrary}'s own Javadoc claimed they
     * were "read out on the board card". They were not: nothing outside the tests called
     * {@link PuzzleLibrary#line}, so the warmest writing in the product had never once
     * reached a television. This is where it belongs. "Bedtime Story" over "One more
     * chapter, and then really and truly lights out." is the pairing that makes two people
     * start chapter twenty-two; "Bedtime Story" over "Another one for the wall." is not.
     *
     * <p>The rotation stays for endless play, which has no authored line to say, and the
     * last chapter keeps its own ending — a book that has just closed should not be talking
     * about the picture.
     */
    static String message(GameState game, boolean together) {
        if (isFinalChapter(game)) {
            return together ? "Every page, together, to the very end."
                    : "Every page, to the very end.";
        }
        if (game.storyMode) {
            String line = PuzzleLibrary.line(game.storyIndex);
            if (line != null && !line.isEmpty()) {
                return line;
            }
        }
        return message(game.solved, together);
    }

    /**
     * The message for a given number of finished puzzles. Stable — the same count always
     * gives the same words — and defined for every {@code int}, negatives included.
     */
    static String message(int solved, boolean together) {
        String[] pool = together ? MESSAGES : SOLO_MESSAGES;
        return pool[Math.floorMod(solved, pool.length)];
    }

    static int messageCount() {
        return MESSAGES.length;
    }

    /**
     * Where the players are: in the book if they are reading one, and on the wall if they
     * are not.
     *
     * <p>Endless used to get nothing here at all, so the mode people spend most of their
     * evenings in had the barest card of the two: straight from "121 cozy moves, made
     * together" to the button. It has a memento available for free —
     * {@code GameState.solved} is persisted by {@code SaveStore} and is the same figure the
     * HUD prints as "ENDLESS&nbsp;#4" — so the card can close by saying how many pictures
     * are on the wall, in the same words the rail used two seconds earlier. That turns
     * endless from a treadmill into a collection without a byte of new state.
     */
    static String journeyLine(GameState game) {
        if (!game.storyMode) {
            return wallLine(game.solved + 1);
        }
        int left = chapterCount() - chapterNumber(game);
        if (left <= 0) {
            return capitalise(word(chapterCount())) + " pictures, cover to cover.";
        }
        if (left == 1) {
            return "One chapter left in the book.";
        }
        if (chapterNumber(game) == 1) {
            return "The book is open — " + word(left) + " more.";
        }
        return capitalise(word(left)) + " more chapters to come.";
    }

    /**
     * The endless memento, counting the picture that has just been finished.
     *
     * <p>"now" used to be on the end of this line. At the 36&nbsp;px prose floor "Seventy-
     * seven pictures on the wall now." measures 642&nbsp;px against a 651&nbsp;px column —
     * nine pixels of headroom on a string whose length depends on a number that grows for
     * ever. Without it the same worst case is 566&nbsp;px.
     */
    private static String wallLine(int pictures) {
        if (pictures <= 0) {
            return null;
        }
        if (pictures == 1) {
            return "The first picture on the wall.";
        }
        return capitalise(word(pictures)) + " pictures on the wall.";
    }

    /** What the next press of A will do, said plainly. */
    static String invite(GameState game) {
        if (!game.storyMode) {
            return "Press A for another picture";
        }
        return isFinalChapter(game) ? "Press A to open the book again"
                : "Press A for the next chapter";
    }

    /**
     * The number as the game would say it out loud, or as digits if it is bigger than the
     * game has any business saying out loud. Shared with {@link HudScene}, which spells the
     * same counts on the rail and must not disagree with the win card two seconds later, and
     * with {@code CozyGameView}, which announces each chapter as it opens.
     *
     * <p>Public rather than package-private so the preview harness builds its chapter ribbon
     * the way the game builds it. That shot used to carry a hand-typed "Chapter ten — Sleepy
     * Cat" beside a build that emitted "Chapter 10", which is a screenshot flattering the
     * thing it is meant to be evidence about.
     */
    public static String word(int value) {
        if (value < 0 || value >= 100) {
            return Integer.toString(value);
        }
        if (value < NUMBER_WORDS.length) {
            return NUMBER_WORDS[value];
        }
        String tens = TENS_WORDS[value / 10];
        int units = value % 10;
        return units == 0 ? tens : tens + "-" + NUMBER_WORDS[units];
    }

    /**
     * A number word at the head of a sentence. Package-private alongside {@link #word(int)}
     * and for the same reason: the rail says the same counts about the same book, and two
     * copies of a rule that has to agree are one rule waiting to disagree.
     */
    static String capitalise(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
