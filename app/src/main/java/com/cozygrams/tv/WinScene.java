package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * The celebration — the reward for finishing a picture together.
 *
 * <p>It is choreographed rather than simply shown. The board dims and the finished
 * picture <em>lifts off the table</em> into a frame of its own; the picture blooms
 * outward from its own centre; then the name, the message, the shared credit and finally
 * the invitation to carry on each arrive on their own beat. Everything has landed by
 * {@link #ARRIVED_MS}, comfortably inside a second and a half.
 *
 * <p>There is no score, no timer, no rank and no comparison between the players. The move
 * count is a memento of a thing made together, never a result.
 *
 * <p><b>Nothing bright is ever alone.</b> This is the first frame of the reward, it is
 * watched in a dark room and it may be watched on an OLED. The card the picture lands on
 * is the largest and brightest surface in the game, so its opacity is tied to how much of
 * the picture exists rather than to a fixed 130&nbsp;ms ramp — see {@link #paperOpacity}.
 * A near-white 620&nbsp;px sheet holding half a heart and no words is a wince, and it used
 * to last four hundred milliseconds.
 *
 * <p><b>Confetti.</b> Two independent guarantees keep it off the copy: the scene declares
 * its reading column to {@link Effects}, which lays the drift into the clear lanes either
 * side of it, and the picture and the plaque are both painted over the particles anyway.
 * Nothing celebratory is ever drawn on a word. The composition is centred, so those two
 * lanes come out the same width by construction rather than by hope.
 *
 * <p><b>Stillness.</b> A win card that repaints for ever is a panel burning a static
 * picture in a dark room at 60&nbsp;fps. The one thing that moves after the beats have
 * landed is the invitation's breath, and it takes {@link #INVITE_BREATHS} of them and then
 * holds. From {@link #STILL_AT_MS} onward this scene draws the same frame for ever, which
 * is what lets {@code Renderer.animating} answer honestly and the loop go to sleep.
 */
public final class WinScene {

    // ---- The beat sheet --------------------------------------------------------------
    //
    // Every constant is milliseconds after ui.winAt. The beats overlap slightly on
    // purpose: each one begins while the previous is still settling, which reads as one
    // continuous moment rather than a queue of animations.

    /** The room goes quiet first, so the board is seen exactly as they left it. */
    static final float DIM_MS = 200f;

    /** Then the picture lifts off the dimmed board and flies into a frame of its own. */
    static final float LIFT_AT = 140f;
    static final float LIFT_MS = 380f;

    /**
     * When the board underneath has finished being lifted from and can stop being drawn.
     *
     * <p>{@code Renderer} reads this. Up to here the board is genuinely part of the shot —
     * the picture is seen to leave the table it was made on — and after it, keeping it on
     * screen only means a 238/255 scrim that lets a whole finished puzzle, its ticks, its
     * crosses and the live rail ghost through the celebration as readable half-words. Once
     * the board is no longer drawn the scrim has nothing left to hide, which is what lets
     * the room stay lit behind the card instead of being crushed flat.
     */
    public static final long BOARD_GONE_MS = (long) (LIFT_AT + LIFT_MS);

    /**
     * The picture blooms outward from its own centre of mass as it travels. It starts a
     * hair before the lift so the card is never a blank sheet, even for one frame.
     */
    static final float REVEAL_AT = 130f;
    /** Spread between the first square and the last; no cell is ever later than this. */
    static final float REVEAL_SPREAD_MS = 400f;
    /** How long one square takes to pop into place once its turn arrives. */
    static final float CELL_POP_MS = 170f;

    /**
     * The plaque that will hold the words slides up, just ahead of the first of them.
     *
     * <p>Early — earlier than the picture finishes. The card and the plaque are the two
     * halves of one composition, and while only one of them exists the screen is a bright
     * rectangle sitting on its own in the dark. It used to arrive at 500&nbsp;ms, two
     * hundred milliseconds after the card had already gone fully opaque.
     */
    static final float PLAQUE_AT = 340f;
    static final float PLAQUE_MS = 280f;

    static final float NAME_AT = 470f;
    static final float NAME_MS = 260f;
    static final float MESSAGE_AT = 620f;
    static final float MESSAGE_MS = 240f;
    static final float CREDIT_AT = 740f;
    static final float CREDIT_MS = 240f;
    static final float JOURNEY_AT = 850f;
    static final float JOURNEY_MS = 240f;
    /** The invitation to carry on — deliberately the last thing to arrive. */
    static final float INVITE_AT = 950f;
    static final float INVITE_MS = 280f;
    static final float HINT_AT = 1120f;
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
     * scene draws can change again.
     *
     * <p>Read by {@code Renderer.animating}, which may stop asking for frames once this has
     * passed and {@link Effects#busy} has gone quiet. A breath ends exactly on a zero
     * crossing of its own sine, so the hold is not a freeze: the value it stops at is the
     * value it was heading for.
     */
    public static final long STILL_AT_MS =
            (long) (INVITE_AT + INVITE_BREATH_MS * INVITE_BREATHS);

    // ---- Composition -----------------------------------------------------------------

    /**
     * How much of the width the picture and the words occupy between them. What is left
     * over is split evenly either side, which is where the confetti rises: two lanes
     * framing the card, not one dense column pinned to a screen edge.
     */
    private static final float COMPOSITION_SPAN = .72f;

    /**
     * How opaque the picture's card is before any of the picture exists. Enough to read as
     * a sheet of paper coming up under the squares; nowhere near enough to be the brightest
     * thing in a dark room.
     */
    private static final float PAPER_FLOOR = .30f;

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
     * <p>Not a magic gate: it is the moment the invitation has become properly legible,
     * a little after {@link #INVITE_AT}. Nothing is ever hidden from a player who presses
     * immediately, and nothing that carries meaning is cut short — the picture has
     * finished assembling by {@link #revealDoneMs} (700 ms on every board the game can
     * deal), the name by 730 ms and the message by 860 ms.
     */
    public static final long INPUT_DELAY_MS = 1100;

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

    /** Small numbers read better as words in a sentence than as digits. */
    private static final String[] NUMBER_WORDS = {
            "no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen", "twenty"
    };

    private final Draw draw;

    /** Reveal offsets for the picture on the table, rebuilt only when the board changes. */
    private Puzzle scheduledFor;
    private float[] schedule;
    /** How long this picture takes to assemble, from {@link #REVEAL_AT} to the last cell. */
    private float assemblyMs;

    /** Where the board sat, so the picture can fly out of it. Cached per surface size. */
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

        // 1. The room goes quiet around the finished board, before anything moves — so
        //    the picture is seen to leave the table it was actually made on.
        // Deep enough that the HUD's own per-player move counts recede completely: this
        // screen credits the two of them jointly and must not be read next to a tally.
        // Heavy while the board is still being drawn beneath, then easing back once it
        // is gone: past that point the only thing under the card is the illustrated room,
        // and burying it is exactly what made the reward feel like a lightbox.
        float settled = Draw.easeInOut(Draw.clamp01(
                (elapsed - BOARD_GONE_MS) / 260f));
        float peak = calm ? 246 : 238;
        float resting = calm ? 208 : 190;
        int scrim = (int) (Draw.lerp(peak, resting, settled)
                * Draw.easeOut(Draw.clamp01(elapsed / DIM_MS)));
        draw.paint().setStyle(Paint.Style.FILL);
        draw.paint().setColor(Color.argb(scrim, 16, 11, 28));
        canvas.drawRect(0, 0, width, height, draw.paint());

        // 2. The composition: the picture on the left, the words on the right, the pair of
        //    them centred. What is left over is one clear lane either side, and they are
        //    the same width because the composition is centred — not because a hand-picked
        //    left edge happened to make them so. It did not: .122 of the width put 702 px
        //    of air on the left and 131 px on the right, and the comment three lines above
        //    claimed they were equal.
        float span = width * COMPOSITION_SPAN;
        float left = compositionLeft(width);
        float frame = Math.min(height * .575f, span * .42f);
        float gap = Theme.scale(40);
        float plaqueLeft = left + frame + gap;
        float plaqueRight = left + span;

        // 3. Confetti, under everything. Two independent guarantees keep it off the copy:
        //    the plaque is painted over it, and Effects is told exactly which column holds
        //    the reading matter — the whole composition, picture included, so the drift
        //    rises in the two lanes framing it rather than half of it landing behind the
        //    picture card. What is left is a celebration rising around the thing they made,
        //    never across it. Calmed motion has no drift at all.
        if (!calm) {
            effects.setReadingColumn(left / width, plaqueRight / width);
            effects.drawParticles(canvas, draw, null, now);
        }

        drawPicture(canvas, width, height, left, left + frame, game, elapsed, calm);
        drawPlaque(canvas, plaqueLeft, plaqueRight, height, game, ui, elapsed, calm);
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
     * allows. It flies out of the board it was solved on and assembles itself outward
     * from its own centre of mass, so it grows rather than wiping in.
     */
    private void drawPicture(Canvas canvas, float width, float height, float heroLeft,
                             float heroRight, GameState game, float elapsed, boolean calm) {
        BoardLayout board = liftLayout(width, height, game.puzzle);
        // Decelerating, so the picture is nearly at its frame while it is still filling
        // in: the card is never a large empty sheet waiting for its contents.
        float lift = Draw.easeOut(Draw.clamp01((elapsed - LIFT_AT) / LIFT_MS));

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
        float radius = Theme.scale(20);

        // A warm halo, as though the finished picture were catching lamplight. It fades
        // in as the last squares land and then holds perfectly still. The last page of
        // the book gets a wider, brighter one — the only time the glow is ever loud.
        float glow = Draw.easeOut(Draw.clamp01(
                (elapsed - (REVEAL_AT + REVEAL_SPREAD_MS)) / 500f));
        boolean grand = isFinalChapter(game);
        int rings = grand ? 7 : 4;
        for (int ring = rings; ring >= 1; ring--) {
            float spread = Theme.scale(grand ? 12 : 9) * ring;
            draw.roundRect(canvas, frameLeft - spread, frameTop - spread,
                    frameRight + spread, frameBottom + spread, radius + spread,
                    Draw.withAlpha(Theme.GOLD, (int) ((grand ? 17 : 13) * glow)));
        }

        draw.shadow(canvas, frameLeft, frameTop, frameRight, frameBottom, radius,
                Theme.scale(9) * lift);

        int size = game.puzzle.size;
        float cell = picSize / size;
        float[] delays = scheduleFor(game.puzzle);

        // Fresh paper comes up *under* the picture, a moment after the first squares, so
        // the card is never a blank sheet — and it stays translucent until it has something
        // on it. The old ramp reached full white at 310 ms, while the picture was not
        // assembled until 700 and there were no words at all until 500: for four hundred
        // milliseconds the brightest surface in the game sat alone on near-black holding
        // half a heart. Now the sheet arrives dim and its opacity follows the picture.
        draw.roundRect(canvas, frameLeft, frameTop, frameRight, frameBottom, radius,
                Draw.withAlpha(Theme.PAPER, paperOpacity(elapsed, assemblyMs)));
        // Colour ramp: the two identity colours meeting across the picture. Asked of
        // Theme so the colour-blind-friendly palette applies here too.
        int from = Theme.playerColor(0);
        int to = Theme.playerColor(1);

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
     * How opaque the picture's card is, 0..1, {@code assemblyMs} after {@link #REVEAL_AT}
     * being the moment the last square of that particular picture lands.
     *
     * <p>Two factors. The first is the sheet arriving under the first squares, which is
     * quick — a card that fades in slowly shows the dimmed board straight through it. The
     * second is what stops it being a wince: the sheet only reaches full white as the
     * picture finishes assembling, so the brightest surface in the game is never the only
     * thing on the screen. Pure, so the choreography can be asserted about rather than
     * eyeballed.
     */
    static float paperOpacity(float elapsed, float assemblyMs) {
        float arrive = Draw.easeOut(Draw.clamp01((elapsed - LIFT_AT - 40) / 130f));
        float assembled = Draw.easeInOut(assemblyMs <= 0 ? 1f
                : Draw.clamp01((elapsed - REVEAL_AT) / assemblyMs));
        return arrive * Draw.lerp(PAPER_FLOOR, 1f, assembled);
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
            float latest = 0;
            for (float delay : schedule) {
                latest = Math.max(latest, delay);
            }
            assemblyMs = latest + CELL_POP_MS;
            scheduledFor = puzzle;
        }
        return schedule;
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

    /**
     * The copy plaque: what it is called, what it meant, who made it, and — last — the
     * invitation to carry on. The plaque is sized to its own content, so there is never a
     * dead band inside it.
     */
    private void drawPlaque(Canvas canvas, float left, float right, float height,
                            GameState game, UiState ui, float elapsed, boolean calm) {
        boolean together = ui.joined[0] && ui.joined[1];
        int solo = ui.joined[1] && !ui.joined[0] ? 1 : 0;
        float padX = Theme.scale(40);
        float padY = Theme.scale(44);
        float column = right - left - padX * 2;
        float centre = (left + right) / 2;

        String eyebrowText = eyebrow(game, together);
        String messageText = message(game, together);
        String journeyText = journeyLine(game);
        String inviteText = invite(game);
        String hintText = "Back returns to the menu";
        String movesText = together
                ? game.totalMoves() + " cozy moves, made together"
                : game.totalMoves() + " cozy moves, start to finish";

        float sEyebrow = fit(eyebrowText, Theme.textSize(15), column, true);
        float sName = fit(game.puzzle.name, Theme.textSize(Theme.HEADING), column, true);
        float sMessage = fit(messageText, Theme.textSize(Theme.SUBHEAD), column, false);
        float sCredit = Theme.textSize(21);
        float sMoves = fit(movesText, Theme.textSize(Theme.CAPTION), column, false);
        float sJourney = journeyText == null ? 0
                : fit(journeyText, Theme.textSize(15), column, false);
        float sInvite = fit(inviteText, Theme.textSize(Theme.BODY), column * .84f, true);
        float sHint = fit(hintText, Theme.textSize(13), column, false);

        float rule = Math.max(1.5f, Theme.scale(2));
        float hearts = game.storyMode ? Theme.scale(26) : 0;
        float pill = sInvite * 2.05f;

        // One running total, then the same rows drawn against it — the plaque can never
        // be taller than what it holds.
        float content = sEyebrow
                + Theme.scale(18) + sName
                + Theme.scale(20) + sMessage
                + Theme.scale(28) + rule
                + Theme.scale(26) + sCredit
                + Theme.scale(12) + sMoves;
        if (game.storyMode) {
            content += Theme.scale(24) + hearts;
            if (journeyText != null) {
                content += Theme.scale(14) + sJourney;
            }
        }
        content += Theme.scale(34) + pill + Theme.scale(16) + sHint;

        float top = height / 2 - (content + padY * 2) / 2;
        float bottom = top + content + padY * 2;
        float appear = beat(elapsed, PLAQUE_AT, PLAQUE_MS);
        if (appear <= 0) {
            return;
        }
        // The surface reaches full opacity in half the time it takes to finish sliding,
        // so the HUD is never legible through it.
        float slide = (1 - appear) * Theme.scale(20);
        draw.panel(canvas, left, top + slide, right, bottom + slide,
                (int) (240 * beat(elapsed, PLAQUE_AT, PLAQUE_MS * .45f)));

        float y = top + padY + slide;

        // Beat: the name lands, with its label above it.
        float name = beat(elapsed, NAME_AT, NAME_MS);
        y += sEyebrow;
        draw.text(canvas, eyebrowText, centre, y - sEyebrow * .16f, sEyebrow,
                Draw.withAlpha(game.storyMode ? Theme.GOLD : Theme.SOFT_TEXT, name),
                Paint.Align.CENTER, true);

        y += Theme.scale(18) + sName;
        float pop = calm ? 1 : Draw.lerp(.86f, 1f, Draw.springy(
                Draw.clamp01((elapsed - NAME_AT) / (NAME_MS * 1.6f))));
        if (name > 0) {
            float pivotY = y - sName * .45f;
            canvas.save();
            canvas.translate(centre, pivotY);
            canvas.scale(pop, pop);
            canvas.translate(-centre, -pivotY);
            draw.text(canvas, game.puzzle.name, centre, y - sName * .10f, sName,
                    Draw.withAlpha(Theme.CREAM, name), Paint.Align.CENTER, true);
            canvas.restore();
        }

        // Beat: the message.
        float said = beat(elapsed, MESSAGE_AT, MESSAGE_MS);
        y += Theme.scale(20) + sMessage;
        draw.text(canvas, messageText, centre,
                y - sMessage * .14f + (1 - said) * Theme.scale(10), sMessage,
                Draw.withAlpha(Theme.CREAM, said * .96f), Paint.Align.CENTER, false);

        y += Theme.scale(28) + rule;
        float ruleWidth = column * .34f;
        draw.roundRect(canvas, centre - ruleWidth / 2, y - rule, centre + ruleWidth / 2, y,
                rule, Draw.withAlpha(Theme.CREAM, said * .22f));

        // Beat: shared credit. Both names, the same size, in their own colours, joined by
        // a heart. No per-player totals — this is never a contest.
        float credit = beat(elapsed, CREDIT_AT, CREDIT_MS);
        y += Theme.scale(26) + sCredit;
        drawCredit(canvas, centre, y - sCredit * .14f + (1 - credit) * Theme.scale(8),
                sCredit, together, solo, credit);

        y += Theme.scale(12) + sMoves;
        draw.text(canvas, movesText, centre,
                y - sMoves * .14f + (1 - credit) * Theme.scale(8), sMoves,
                Draw.withAlpha(Theme.SOFT_TEXT, credit * .9f), Paint.Align.CENTER, false);

        // Beat: the journey, when there is one.
        if (game.storyMode) {
            float journey = beat(elapsed, JOURNEY_AT, JOURNEY_MS);
            y += Theme.scale(24) + hearts;
            drawChapters(canvas, centre, y - hearts / 2, column, game, journey, calm);
            if (journeyText != null) {
                y += Theme.scale(14) + sJourney;
                draw.text(canvas, journeyText, centre, y - sJourney * .14f, sJourney,
                        Draw.withAlpha(Theme.SOFT_TEXT, journey * .9f),
                        Paint.Align.CENTER, false);
            }
        }

        // Beat: the invitation, last, and exactly when the buttons start working.
        float invited = beat(elapsed, INVITE_AT, INVITE_MS);
        y += Theme.scale(34) + pill;
        drawInvite(canvas, centre, y - pill, y, sInvite, inviteText, elapsed, invited,
                calm);

        y += Theme.scale(16) + sHint;
        draw.text(canvas, hintText, centre, y - sHint * .14f, sHint,
                Draw.withAlpha(Theme.SOFT_TEXT, beat(elapsed, HINT_AT, HINT_MS) * .78f),
                Paint.Align.CENTER, false);
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

    /**
     * The story book as a row of hearts: the chapters already filled in, the one that was
     * just finished, and the pages still to come. The newest heart is the only thing that
     * moves, and only once.
     */
    private void drawChapters(Canvas canvas, float centre, float middle, float column,
                              GameState game, float fade, boolean calm) {
        int total = chapterCount();
        int chapter = chapterNumber(game) - 1;
        int done = Math.max(chapter, Math.min(total - 1, game.storyFurthest)) + 1;
        boolean finished = isFinalChapter(game);

        float step = Math.min(Theme.scale(30), column / Math.max(1, total));
        float size = step * .60f;
        float x = centre - step * (total - 1) / 2;

        for (int i = 0; i < total; i++) {
            float cx = x + step * i;
            if (i > chapter && i >= done) {
                draw.circle(canvas, cx, middle, size * .17f,
                        Draw.withAlpha(Theme.GRID, fade));
            } else if (i == chapter) {
                float land = calm ? fade
                        : Draw.easeOutBack(Draw.clamp01(fade * 1.15f));
                draw.heart(canvas, cx, middle, size * Draw.lerp(.3f, 1.22f, land),
                        Draw.withAlpha(Theme.GOLD, fade));
            } else {
                draw.heart(canvas, cx, middle, size,
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
        float width = draw.measure(label, size, true) + Theme.scale(58);
        float radius = (bottom - top) / 2;
        float breath = calm ? 0f : inviteBreath(elapsed);

        float halo = Theme.scale(3) + Theme.scale(7) * breath;
        draw.roundRect(canvas, centre - width / 2 - halo, top - halo,
                centre + width / 2 + halo, bottom + halo, radius + halo,
                Draw.withAlpha(CTA_FACE, fade * (.14f + breath * .12f)));
        draw.roundRect(canvas, centre - width / 2, top, centre + width / 2, bottom, radius,
                Draw.withAlpha(CTA_FACE, fade));
        draw.text(canvas, label, centre, bottom - radius + size * .35f, size,
                Draw.withAlpha(Theme.textOn(CTA_FACE), fade), Paint.Align.CENTER, true);
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

    /** Shrinks a size until the string fits, never below the ten-foot legibility floor. */
    private float fit(String text, float size, float maxWidth, boolean strong) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return size;
        }
        float measured = draw.measure(text, size, strong);
        if (measured <= maxWidth || measured <= 0) {
            return size;
        }
        return Math.max(Theme.scale(Theme.MIN_READABLE_SP), size * maxWidth / measured);
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

    /** The warm line under the name. Finishing the book earns its own sentence. */
    static String message(GameState game, boolean together) {
        if (isFinalChapter(game)) {
            return together ? "Every page, together, to the very end."
                    : "Every page, to the very end.";
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

    /** Where the players are in the story book, or {@code null} outside story mode. */
    static String journeyLine(GameState game) {
        if (!game.storyMode) {
            return null;
        }
        int left = chapterCount() - chapterNumber(game);
        if (left <= 0) {
            return capitalise(word(chapterCount())) + " pictures, cover to cover.";
        }
        if (left == 1) {
            return "One chapter left in the book.";
        }
        if (chapterNumber(game) == 1) {
            return "The book is open — " + word(left) + " chapters to come.";
        }
        return capitalise(word(left)) + " more chapters to come.";
    }

    /** What the next press of A will do, said plainly. */
    static String invite(GameState game) {
        if (!game.storyMode) {
            return "Press A for another picture";
        }
        return isFinalChapter(game) ? "Press A to open the book again"
                : "Press A for the next chapter";
    }

    private static String word(int value) {
        return value >= 0 && value < NUMBER_WORDS.length ? NUMBER_WORDS[value]
                : Integer.toString(value);
    }

    private static String capitalise(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
