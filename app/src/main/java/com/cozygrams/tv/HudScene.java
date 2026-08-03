package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Everything around the board while playing: the side rail and the message ribbon.
 *
 * <h2>Nothing here is a scoreboard</h2>
 *
 * <p>The rail used to print {@code ROSE 174 moves} above {@code SKY 158 moves}, adjacent,
 * in each player's own colour, for the whole evening. Two numbers side by side are a
 * league table however warmly they are lettered — and this one was worse than useless,
 * because {@code moves} counts button presses: fumbling raises it, so the <em>less</em>
 * certain player "won". The seats now carry a dot, a name and, when nobody is sitting in
 * one, the invitation to join. The only number left in the rail is the pair's shared
 * progress through the picture, drawn once, as one bar, in a colour that runs from Rose's
 * warm end to Sky's cool one. There is nothing left to compare.
 *
 * <h2>Where the pixels went</h2>
 *
 * <p>The board is the game; everything in this class is furniture around it, and the
 * furniture had grown. Three changes give the squares their pixels back:
 *
 * <ul>
 *   <li><b>There is no title strip.</b> It was two lines, then one, and one line of type
 *       across the top still cost 66 px at 1080p — charged to the only axis the board is
 *       ever short of. Everything it said now lives at the head of the rail
 *       ({@link #drawWhereWeAre}), which is on the axis with several hundred pixels going
 *       spare, and the wordmark is gone from play altogether: {@code HomeScene} sets it in
 *       58 px letters thirty seconds earlier and nobody needs telling twice which game
 *       they are in. Measured on a busy 20x20 board at 1920x1080, that is 29 px squares
 *       becoming 32 px ones.</li>
 *   <li><b>The rail is a fixed width.</b> See {@link BoardLayout#railWidth}. It no longer
 *       swallows whatever the height-capped board leaves behind, and it no longer widens
 *       when someone turns larger text on.</li>
 *   <li><b>The progress readout is a bar, not a caption.</b> It moved out of the strip and
 *       into the rail with everything else.</li>
 * </ul>
 *
 * <h2>And what the rail's own height went on</h2>
 *
 * <p>The rail costs the board nothing — it is 360 px of a 1920 px screen and the grid is
 * height-limited at every board size, so 341 to 372 px of backdrop sits between the paper
 * card and the rail whatever it does. Its height was the problem. Four rows of button
 * names took 228 px of a 617 px rail to say the same four things all evening, while both
 * seats together had 171 and the one shared thing on screen had 90; and the closing block
 * that was meant to fill the rest missed its threshold by 2.03 px and had never been
 * drawn at all. So the legend retires into a strip of chips once the pair have finished
 * three pictures ({@link #LEGEND_LEARNED_AFTER}), and the closing block asks for what it
 * is about to draw ({@link #tailBlockHeight}) and sits at the foot of the panel, so the
 * rail runs to the safe line with a note at the bottom of it instead of stopping two
 * hundred pixels short.
 *
 * <p>Both files have to agree about that furniture, so every measurement the layout needs
 * is declared here once and read by {@link BoardLayout}: {@link #ribbonInset},
 * {@link #ribbonHeight} and {@link #RIBBON_MAX_LINES}. With the strip gone that list is
 * one item shorter, and {@link BoardLayout#headerHeight()} no longer has to know anything
 * about type at all.
 */
public final class HudScene {

    private static final long TOAST_MS = 4200;
    private static final long TOAST_FADE_MS = 420;

    // ---- The furniture BoardLayout has to reserve room for ---------------------------
    //
    // Every pixel here is a pixel the board does not get, and at 20x20 the board is
    // height-limited while horizontal space goes spare — so these are the highest-leverage
    // numbers in the whole layout. They live here, once, because two files silently
    // disagreeing about them is how the ribbon ended up sitting on the last row of the
    // board in a previous version.

    /**
     * Height of a one-line ribbon, as a multiple of its own text size.
     *
     * <p>1.8 em put 19.8 px of clear pill above and below a 25.2 px cap at 1080p — more air
     * inside the pill than the cap is tall, on a reserve the board pays for all evening
     * whether or not there is a message. 1.55 leaves 15.3 px each side, which is still more
     * padding than the {@code PILL_PAD_X} the win card's button uses vertically, and hands
     * 10.8 px of the scarce axis back to the squares.
     */
    static final float RIBBON_HEIGHT_EM = 1.55f;
    /** What each wrapped line after the first adds, as a multiple of the text size. */
    static final float RIBBON_LINE_EM = 1.08f;
    /**
     * The ribbon is one line, and the board is never charged for a second.
     *
     * <p>Reserving a two-line worst case cost every board 38.9 px of height at 1080p — at
     * 20x20 that is the difference between 31.6 px cells and 29.7 px ones — for a case
     * gameplay messages never reach. The board is the thing being read, so it keeps the
     * pixels and the ribbon lives inside one line: it widens to {@link #ribbonLane} first,
     * 1307 px at 1920x1080 against a widest real message of 690 px, and shrinks to the
     * prose floor after that.
     *
     * <p>This paragraph used to say "{@code HudSceneTest} holds every message the game can
     * show to one line at the narrowest card". There was no such test, {@link #wrap} could
     * not wrap, and real messages hung off both ends of the pill. There is one now —
     * {@code everyMessageTheGameCanShowFitsItsPill} — and it measures the strings the game
     * actually writes rather than trusting this sentence.
     */
    static final int RIBBON_MAX_LINES = 1;

    /**
     * How far the ribbon floats up from the bottom edge, as a multiple of the safe area.
     *
     * <p>It used to be {@code .55}, which put the pill's bottom edge 2.6% from the bottom
     * of a 1080p screen. Leanback asks for 5%, and a panel overscanning 3% simply ate the
     * message. At 1 the pill's bottom sits exactly <em>on</em> the safe line, which is the
     * lowest it is allowed to be; {@link #RIBBON_SAFE} is what guarantees that line is at
     * least 5% however {@link Theme#SAFE_AREA} is set.
     */
    static final float RIBBON_LIFT = 1f;

    /** The smallest inset the ribbon will accept, whatever the theme's safe area says. */
    static final float RIBBON_SAFE = .05f;

    /**
     * Air between the bottom of the paper card and the top of the ribbon.
     *
     * <p>Both edges are already generously padded — the card carries its own 12 design px
     * below the last row and the pill 10 more above its cap — so this only has to keep the
     * two shapes from touching, which 8 design px does at every resolution the game ships.
     */
    static final float RIBBON_GAP = 8f;

    /** Inset from the rail's edges to its content. */
    private static final float PANEL_PAD = 14f;
    /** Air under the picture's name, before the rule that closes the head block. */
    private static final float HEAD_GAP = 16f;
    /** The most lines the picture's name is allowed to turn onto in the rail. */
    private static final int NAME_MAX_LINES = 2;
    /** Height of one seat card. */
    private static final float CARD_HEIGHT = 52f;
    /** Gap between the two seat cards. */
    private static final float CARD_GAP = 10f;
    /** Gap between the seats and the shared progress bar. */
    private static final float PROGRESS_GAP = 14f;
    /** Height of the shared progress bar itself. */
    private static final float PROGRESS_BAR = 16f;
    /** Gap between the progress bar and the first legend row. */
    private static final float LEGEND_GAP = 20f;
    /** Smallest pitch a row of story pips may use before it wraps onto another row. */
    private static final float PIP_MIN_PITCH = 18f;
    /** Largest pitch a row of story pips ever uses. */
    private static final float PIP_MAX_PITCH = 26f;
    /** Air between two chips on the collapsed legend strip. */
    private static final float LEGEND_CHIP_GAP = 10f;

    /**
     * How many pictures the pair finish before the legend stops spelling itself out.
     *
     * <p>Four rows of button names took 228 px of a 617 px rail — 37% of it — to say the
     * same four things for the whole evening, while both seats together had 171 px and the
     * one genuinely shared thing on screen, the progress bar, had 90. Three pictures is
     * long enough to have pressed every button several times and short enough that nobody
     * is still reading it.
     *
     * <p>The chips themselves stay. What goes is the sentence beside each one, and with it
     * the room the closing block needed.
     */
    private static final int LEGEND_LEARNED_AFTER = 3;

    /** How long a newcomer gets the words back for. */
    private static final long LEGEND_TEACH_MS = 15_000;

    /** One full breath of an empty seat's ring, out and back. */
    private static final float SEAT_BREATH_MS = 2400f;

    /**
     * One full cycle of a joined seat's beating dot: {@code 1500 * π}, because the dot is
     * drawn from {@code abs(sin(now / 1500))} and that has half the period of the sine.
     * Stated so the phase can be taken modulo something exact — see {@link #drawSeat}.
     */
    private static final long SEAT_PULSE_MS = 4712;

    /** How long the empty seat's ring widens for when someone stirs its stick. */
    private static final long SEAT_STIR_MS = 300;

    /** How long a player's last mark shows on their own card before it fades away. */
    private static final long SEAT_GLYPH_MS = 2500;

    /** The most lines the closing block's sentence is allowed to turn onto. */
    private static final int TAIL_MAX_LINES = 3;

    /**
     * How far a small-caps eyebrow may be shrunk to fit its lane, as a fraction of its
     * asked-for size.
     *
     * <p>Below {@link Theme#MIN_PROSE_SP} is normally forbidden and this is the one
     * exception: "PLAYING TOGETHER" and "THE STORY BOOK" are decoration around a name
     * rather than something anybody reads a word at a time, and they are already set at
     * 82% of caption. In practice the floor is a backstop and not the answer — the worst
     * case measured, "PLAYING TOGETHER" with Larger Text on, comes to 30.3 px against a
     * floor of 26.7. The alternative, which is what shipped, is 351.9 px of heading in a
     * 311.3 px lane: the final R sitting on the wallpaper outside the card.
     */
    private static final float EYEBROW_FLOOR = .78f;

    /**
     * True when every controller that has joined is a bare TV remote — a D-pad, a centre
     * key, Back, and nothing else.
     *
     * <p>Such a controller cannot reach {@code PlayerRegistry.isCross} at all, so the
     * centre key cycles empty → filled → crossed instead of only toggling a fill, and
     * hints move to a held centre press. A legend that still says "A / B / Y" is then
     * three lies in a row, which is worse than no legend.
     *
     * <p>Set by {@code CozyGameView.registerDevice} from
     * {@link PlayerRegistry#needsCycleInput}. The rule is deliberately "every controller",
     * not "this controller". If there is
     * a gamepad anywhere in the room the printed legend is true for it, and the remote
     * user's centre key still fills — a superset of what the legend claims — so nothing
     * on screen is wrong. With no gamepad at all, all three button names are wrong and
     * the legend has to change. Until something sets it the gamepad legend shows, which
     * is the familiar case and the safe default.
     */
    private static boolean remoteOnly;

    /**
     * How many pictures had already been finished when the app was opened, so the rail
     * can say what <em>this evening</em> holds.
     *
     * <p>{@code GameState.solved} calls itself a session count and is not one:
     * {@code SaveStore} persists it under {@code KEY_SOLVED} and puts it back on the next
     * launch. A pair forty pictures deep over three months therefore sat down to
     * "TONIGHT — 40 pictures done", which is simply false, and false in the direction that
     * makes an evening feel like a performance review.
     *
     * <p>Set by {@code CozyGameView}'s constructor, from {@code game.solved} as it comes off
     * disk and after the finished-board fix-up, so the baseline is the count the pair sat
     * down with. {@link #tailLabel} then says "TONIGHT" and {@link #picturesToShow} counts
     * only this evening's pictures.
     *
     * <p>Until something sets it the rail does not pretend: the block says "TOGETHER SO FAR"
     * and shows the lifetime figure, which is at least true. That is the state the preview
     * harness and the unit tests see, and it is why {@link #knowsTonight()} exists — nothing
     * on screen is ever a lie in either state.
     */
    private static int puzzlesBeforeTonight = -1;

    /**
     * When somebody last sat down, so the legend can spell itself out again for them.
     *
     * <p>Zero, not {@code -1}: the clock the rail is drawn against is
     * {@code SystemClock.uptimeMillis}, which is never zero on a running television, and a
     * sentinel that reads as "fifteen seconds ago" on a freshly booted box would show the
     * long legend to a pair who did not ask for it.
     */
    private static long lastJoinAt;

    /** When each empty seat was last stirred by a stick that could not join yet. */
    private static final long[] seatStirredAt = {0, 0};

    private final Draw draw;

    public HudScene(Draw draw) {
        this.draw = draw;
    }

    /**
     * Says somebody has just taken a seat. Called from {@code CozyGameView.registerDevice}
     * when {@code players.justJoined() >= 0}, so a newcomer always gets the button names
     * back for {@value #LEGEND_TEACH_MS} ms however many pictures the room has finished.
     */
    public static void setJoinedAt(long now) {
        lastJoinAt = now;
    }

    /**
     * Says a stick moved on a controller that has not joined yet — see
     * {@link PlayerRegistry#justStirred()}. The empty seat's ring widens once and settles:
     * nothing has happened, but the room noticed.
     */
    public static void setSeatStirredAt(int player, long now) {
        if (player >= 0 && player < seatStirredAt.length) {
            seatStirredAt[player] = now;
        }
    }

    /** Puts the room's memory back, for tests that share one static rail between them. */
    static void forgetTheRoom() {
        lastJoinAt = 0;
        seatStirredAt[0] = 0;
        seatStirredAt[1] = 0;
        puzzlesBeforeTonight = -1;
        remoteOnly = false;
    }

    /** Tells the legend what the players are holding. See the notes on the field. */
    public static void setRemoteOnly(boolean everyControllerIsARemote) {
        remoteOnly = everyControllerIsARemote;
    }

    public static boolean remoteOnly() {
        return remoteOnly;
    }

    /** Tonight's baseline for the pictures counter. See the notes on the field. */
    public static void setPuzzlesBeforeTonight(int finishedAtLaunch) {
        puzzlesBeforeTonight = Math.max(0, finishedAtLaunch);
    }

    /** True once someone has told us where this evening started. */
    public static boolean knowsTonight() {
        return puzzlesBeforeTonight >= 0;
    }

    /** Pictures finished this evening, or the lifetime figure while nobody has said. */
    public static int picturesToShow(GameState game) {
        int solved = Math.max(0, game.solved);
        return knowsTonight() ? Math.max(0, solved - puzzlesBeforeTonight) : solved;
    }

    /** What is printed in each legend chip, in order. */
    public static String[] legendButtons() {
        return remoteOnly
                ? new String[]{"OK", "HOLD", "MENU", "BACK"}
                : new String[]{"A", "B", "Y", "☰"};
    }

    /**
     * What each legend chip does, in the same order.
     *
     * <p>Terser than it was, because the rail is now a fixed 240 design pixels rather than
     * however much the board failed to use — and a legend is a reference card, not prose.
     * Every label here fits its lane at the default type size on the narrowest screen the
     * game ships on, so none of them is ever shrunk or clipped.
     */
    public static String[] legendLabels(UiState ui) {
        String hint = ui.hintsOn ? "Reveal one" : "Hints resting";
        return remoteOnly
                ? new String[]{"Fill, cross, clear", hint, "Cozy corner", "Back home"}
                : new String[]{"Fill a square", "Cross it out", hint, "Cozy corner"};
    }

    /** Which chips are lit: a resting hint button is drawn as resting. */
    private static boolean[] legendActive(UiState ui) {
        return remoteOnly
                ? new boolean[]{true, ui.hintsOn, true, true}
                : new boolean[]{true, true, ui.hintsOn, true};
    }

    /** The colour of each chip, in order. Gold is always the hint. */
    private static int[] legendColors() {
        return remoteOnly
                ? new int[]{Theme.PINK, Theme.GOLD, Theme.SOFT_TEXT, Theme.SOFT_TEXT}
                : new int[]{Theme.PINK, Theme.BLUE, Theme.GOLD, Theme.SOFT_TEXT};
    }

    // ---- The ribbon's geometry, shared with BoardLayout -------------------------------

    /**
     * How far the ribbon's bottom edge sits above the bottom of the screen.
     *
     * <p>Expressed as {@code max(SAFE_AREA, RIBBON_SAFE)} rather than {@code SAFE_AREA}
     * alone so the pill clears Leanback's 5% overscan band whichever way the theme's safe
     * area is set. Everything else about the ribbon hangs off this number.
     */
    static float ribbonInset(float screenHeight) {
        return screenHeight * Math.max(Theme.SAFE_AREA, RIBBON_SAFE) * RIBBON_LIFT;
    }

    /**
     * Height of a ribbon of {@code lines} lines.
     *
     * <p>Measured with {@link Theme#scale} rather than {@link Theme#textSize}, which is the
     * same furniture-versus-type distinction {@link BoardLayout#railWidth} makes and for a
     * sharper reason: this number is subtracted from the board's height on every frame, so
     * putting the LARGER TEXT multiplier in it lets an accessibility setting resize the
     * squares under two people's hands. Measured at 1920x1080 on the busiest 20x20 board, a
     * scaled reserve took the ribbon from 55.8 px to 72.5 and the clue digits from 26.86 px
     * to 26.63 — the setting named for legibility making the puzzle's own numbers smaller,
     * which is the exact fault {@link Theme#TEXT_SCALE_BIG} was rewritten to end.
     *
     * <p>The words inside still scale; they are fitted to this box by
     * {@link #ribbonTextSize}.
     */
    static float ribbonHeight(int lines) {
        float size = Theme.scale(Theme.CAPTION);
        return size * (RIBBON_HEIGHT_EM + Math.max(0, lines - 1) * RIBBON_LINE_EM);
    }

    /**
     * The size everything this class writes asks for before it is fitted to a lane.
     *
     * <p>Caption rather than body, for both the rail and the ribbon. The ribbon's height is
     * subtracted from the board on every frame, message or no message, and the rail is a
     * fixed 240 design pixels of column; neither can afford a step up. 24 design pixels is
     * 36 px at 1080p — 18 arcminutes from ten feet, Leanback's stated body minimum, and the
     * floor {@link Theme#textSize} would clamp anything smaller up to anyway.
     *
     * <p>{@link #ribbonTextSize()} and {@link #railTextSize()} were character-identical
     * bodies under two names, one of which took a {@code screenHeight} it never read.
     */
    private static float captionSize() {
        return Theme.textSize(Theme.CAPTION);
    }

    /**
     * The ribbon's type size: the caption size, fitted to the pill the board reserved.
     *
     * <p>{@link #ribbonHeight} is furniture and does not grow with LARGER TEXT, so the words
     * cannot grow past what that box holds without the pill clipping its own cap. A cap is
     * .70 em and the pill is {@link #RIBBON_HEIGHT_EM} em of the unscaled caption, so the
     * fit leaves at least .42 em of clear pill split above and below the line at every
     * setting. In practice the caption is already on the ten-foot prose floor — 36 px at
     * 1080p, Leanback's stated body minimum — so this only ever declines to make an
     * already-legible transient larger at the board's expense; the rail beside it, which is
     * type rather than furniture, grows with the setting as it should.
     */
    private static float ribbonTextSize() {
        return Math.min(captionSize(), ribbonHeight(1) / RIBBON_HEIGHT_EM);
    }

    // ---- The frame -------------------------------------------------------------------

    public void draw(Canvas canvas, float width, float height, BoardLayout board,
                     GameState game, UiState ui, long now) {
        if (board.hasRoomForPanel()) {
            drawSidePanel(canvas, height, board, game, ui, now);
        }
        // The win card is about to cover everything with a scrim and say the picture is
        // finished. A ribbon underneath it would be a second, quieter voice saying
        // something else at the same moment.
        if (!ui.won) {
            drawToast(canvas, width, height, board, ui, now);
        }
    }

    // ---- Where we are ----------------------------------------------------------------

    /**
     * The eyebrow over the picture's name: which deck, how far in, and how big the board is.
     *
     * <p>Set with single spaces rather than the triple ones the old title strip used. That
     * spacing was there to hold a 1728 px line apart; in a 318 px lane it is what pushed
     * {@link #eyebrowSize} down onto its floor for no gain.
     */
    static String whereWeAre(GameState game) {
        String size = " · " + game.size + " × " + game.size;
        return game.storyMode
                ? "STORY " + (game.storyIndex + 1) + " / " + PuzzleLibrary.count() + size
                : "ENDLESS #" + (game.solved + 1) + size;
    }

    /**
     * The head of the rail: where we are, and the name of the picture being made.
     *
     * <p>This is the title strip, moved. It used to run across the top of the screen, which
     * cost the board 66 px of the one axis it is short of — see
     * {@link BoardLayout#headerHeight()}. Here it costs nothing at all: the rail is on the
     * axis with several hundred spare pixels, and the height it takes is height the rail
     * was leaving empty between the legend and its own closing block.
     *
     * <p>The name is the largest type in the rail on purpose. It is the answer to "what are
     * we making", it is the only proper noun on screen while a puzzle is open, and in the
     * Story Book it is the chapter's title. A narrow column is where a title is expected to
     * turn rather than shrink, so it turns first — up to {@link #NAME_MAX_LINES} lines —
     * and only shrinks if two lines still will not hold it.
     *
     * @return the y the next block starts at.
     */
    private float drawWhereWeAre(Canvas canvas, float left, float top, float right,
                                 GameState game, UiState ui, Wrapped name) {
        float lane = right - left;
        float y = top;

        String eyebrow = whereWeAre(game);
        float label = labelSize();
        draw.text(canvas, eyebrow, left, y + label * 1.28f, eyebrowSize(eyebrow, lane),
                Theme.secondaryText(ui.highContrastOn), Paint.Align.LEFT, true);
        y += label * 1.9f;

        for (String line : name.lines) {
            draw.text(canvas, line, left, y + name.size, name.size, Theme.CREAM,
                    Paint.Align.LEFT, true);
            y += name.size * 1.16f;
        }

        y += Theme.scale(HEAD_GAP);
        drawRailRule(canvas, left, y, right);
        return y + railRule() + Theme.scale(HEAD_GAP);
    }

    /**
     * The heading over the two seats, which has to be true of the room it is in.
     *
     * <p>It read "PLAYING TOGETHER" always. Playing alone, that is a heading over an empty
     * chair for the whole evening — and the game knows better everywhere else: the win card
     * ships a separate pool of solo lines with a comment saying "the same warmth for one
     * player, without pretending there were two". The card refuses to pretend for the four
     * seconds it is up; the rail was pretending for the hour.
     *
     * <p>What replaces it is not an apology. "WHO'S HERE" is true of one player and of two,
     * it still reads as a heading over a pair of seats, and the open seat underneath keeps
     * its ring and its "press any button" exactly as they were — the invitation was always
     * the warm part. Once both seats are filled the original heading comes back, because by
     * then it is a fact rather than a hope.
     */
    static String seatsEyebrow(UiState ui) {
        return ui.joined[0] && ui.joined[1] ? "PLAYING TOGETHER" : "WHO'S HERE";
    }

    /**
     * How tall {@link #drawWhereWeAre} comes out for a given wrapped name.
     *
     * <p>Term for term what that method advances by, for the same reason
     * {@link #panelHeight} matches {@link #drawSidePanel}: the two are read together or not
     * at all, and a block that measures itself differently from the way it draws itself is
     * how the rail's last legend row came to be clipped once already.
     */
    private float headBlockHeight(Wrapped name) {
        return labelSize() * 1.9f
                + name.lines.length * name.size * 1.16f
                + Theme.scale(HEAD_GAP) * 2
                + railRule();
    }

    /**
     * The picture's name, wrapped and sized for the rail's lane.
     *
     * @param lines the most lines it may turn onto — {@link #NAME_MAX_LINES} normally, and
     *              one when the rail is too short to hold two. See {@link #drawSidePanel}.
     */
    Wrapped nameBlock(float screenHeight, float lane, GameState game, int lines) {
        return wrapToFit(game.puzzle.name, lane, Theme.textSize(Theme.SUBHEAD),
                BoardLayout.dp(Theme.MIN_PROSE_SP, screenHeight), lines);
    }

    /**
     * The weight of a rule inside the rail. The head block and the closing block each draw
     * one and each reserve room for one, and all four spelled the same floor out by hand.
     */
    private static float railRule() {
        return Math.max(1f, Theme.scale(1.4f));
    }

    /** One of those rules, laid across the rail's lane. */
    private void drawRailRule(Canvas canvas, float left, float y, float right) {
        draw.roundRect(canvas, left, y, right, y + railRule(), Theme.scale(1),
                Draw.withAlpha(Theme.CREAM, 40));
    }

    /**
     * A gentle, non-competitive progress line — never a timer, never a score.
     *
     * <p>The terminal state used to read "Almost there…", printed over a picture that was
     * already finished. It now says nothing at all once {@link Puzzle#complete()} is true:
     * the picture itself is the message, and the win card is a beat away.
     *
     * <p>The wording is short because the rail it is drawn in is 240 design pixels wide —
     * "Just one stray square left" would need a rail half again as wide, which is exactly
     * the trade this layout refuses to make.
     */
    static String progressLabel(GameState game) {
        if (game.puzzle.complete()) {
            return "";
        }
        int found = game.puzzle.foundCount();
        int total = game.puzzle.pictureCount();
        if (found == 0) {
            return "Take your time";
        }
        if (found >= total) {
            return "One stray square";
        }
        return found + " of " + total + " squares";
    }

    // ---- The rail --------------------------------------------------------------------

    /**
     * Height the rail needs for the parts it always draws: the head block, the section
     * label, both seats, the shared progress bar and every legend row, plus the padding
     * around them.
     *
     * <p>This is the only place that geometry is decided. {@link #drawSidePanel} consumes
     * it in the same order, so a row can never be added to one without the other noticing.
     * The legend's row count is passed in rather than looked up, because it is now the one
     * thing that yields when the rail is short — see {@link #legendRowsThatFit}.
     *
     * <p>Every term here is either a design-pixel constant or a type size, and a type size
     * already knows the screen through {@link Theme#scale}, so this takes no
     * {@code screenHeight} of its own. It used to, and threaded it through
     * {@code inviteHeight} and {@code progressHeight} into a {@code railText(screenHeight)}
     * that ignored it — five signatures carrying one argument to nowhere. The dp floor that
     * genuinely does need the real screen lives in {@link #fitRail} and {@link #nameBlock},
     * and both still take it.
     */
    private float panelHeight(GameState game, UiState ui, Wrapped name, int legendRows) {
        return Theme.scale(PANEL_PAD)                       // top inset
                + headBlockHeight(name)                     // where we are, and its name
                + labelSize() * 1.9f                        // "PLAYING TOGETHER"
                + Theme.scale(CARD_HEIGHT) * 2 + Theme.scale(CARD_GAP)
                + openSeats(ui) * inviteHeight()
                + Theme.scale(PROGRESS_GAP) + progressHeight()
                + Theme.scale(LEGEND_GAP)
                // Counted, not assumed: the remote-only legend need not be four rows
                // forever, and a hard-coded count is exactly what clipped the last one.
                + legendStep() * legendRows                 // rows are centred in a step
                + Theme.scale(PANEL_PAD);                   // bottom inset
    }

    /**
     * How many legend rows the pair have asked for: one for every button while they are
     * still learning them, and a single strip of chips once they are not. See
     * {@link #LEGEND_LEARNED_AFTER}.
     *
     * <p>What they get is {@link #legendRowsThatFit}, which is this or fewer.
     */
    static int legendRows(GameState game, long now) {
        boolean learning = game.solved < LEGEND_LEARNED_AFTER;
        boolean somebodyJustSatDown = lastJoinAt > 0 && now - lastJoinAt < LEGEND_TEACH_MS;
        return learning || somebodyJustSatDown ? legendButtons().length : 1;
    }

    /**
     * The spelled-out legend, but only while the rail can hold it.
     *
     * <p>The rail's height is the card's height, and the card is now taller than it was, so
     * on the smallest canvas the game ships on there is less rail than there used to be:
     * at 1280x720 with Larger Text, two open seats and the picture's name in the head
     * block, the four spelled-out rows ask for 698 px of a 597 px rail. They are the right
     * thing to drop. The chips keep their colours, their letters and their order, so the
     * reference is still on screen — what goes is the sentence beside each one, which is
     * exactly what {@link #LEGEND_LEARNED_AFTER} drops a few pictures later anyway.
     *
     * <p>Everything above the legend is either the picture's identity or a player's, and
     * cutting any of it to keep four sentences would be the wrong trade. This is also the
     * bound that stops the panel being drawn taller than {@link BoardLayout#panelBottom()},
     * which nothing used to check: {@code drawSidePanel} simply drew to {@code top +
     * needed} and would have run its last row through the overscan band.
     */
    private int legendRowsThatFit(GameState game, UiState ui, Wrapped name, int wanted,
                                  float available) {
        for (int rows = wanted; rows > 1; rows--) {
            if (panelHeight(game, ui, name, rows) <= available) {
                return rows;
            }
        }
        return 1;
    }

    private static int openSeats(UiState ui) {
        int open = 0;
        for (boolean joined : ui.joined) {
            if (!joined) {
                open++;
            }
        }
        return open;
    }

    /** The invitation under an empty seat: one line of type plus a little air. */
    private static float inviteHeight() {
        return railTextSize() * 1.34f;
    }

    /** The count and the bar under it. */
    private static float progressHeight() {
        return railTextSize() * 1.24f + Theme.scale(PROGRESS_BAR);
    }

    /** Pitch of the legend, from whichever of its chip and its label is taller. */
    private static float legendStep() {
        return Math.max(Theme.scale(38), Theme.textSize(Theme.CAPTION) * 1.55f);
    }

    private static float labelSize() {
        return Theme.textSize(Theme.CAPTION) * .82f;
    }

    /** The type size the rail's copy asks for, before anything is fitted to its lane. */
    private static float railTextSize() {
        return captionSize();
    }

    /**
     * The size a piece of the rail's copy is actually drawn at.
     *
     * <p>The rail's width is pinned to the screen rather than to the type scale — that is
     * the whole point of {@link BoardLayout#railWidth}, and it is what stops "larger text"
     * from taking pixels away from the grid. Something has to absorb the difference, and
     * it is this: a line grows with larger text only as far as its lane can hold it. The
     * floor is the size the same line would be with the setting <em>off</em>, so turning
     * larger text on can never make anything in the rail smaller than it already was.
     */
    private float fitRail(String text, float room, float screenHeight) {
        return fit(text, room, railTextSize(),
                BoardLayout.dp(Theme.MIN_PROSE_SP, screenHeight), false);
    }

    /**
     * The size a small-caps eyebrow is drawn at: its asked-for size, or as much less as its
     * lane demands. See {@link #EYEBROW_FLOOR}.
     *
     * <p>These were the only two strings in the rail that went to {@code draw.text}
     * unmeasured, which is exactly why they were the only two that ran off the card.
     */
    private float eyebrowSize(String text, float lane) {
        return fit(text, lane, labelSize(), labelSize() * EYEBROW_FLOOR, true);
    }

    private void drawSidePanel(Canvas canvas, float screenHeight, BoardLayout board,
                               GameState game, UiState ui, long now) {
        float left = board.panelLeft;
        float right = board.panelRight;
        float top = board.panelTop();
        float pad = Theme.scale(PANEL_PAD);
        float lane = (right - pad) - (left + pad);
        float available = board.panelBottom() - top;
        Wrapped name = nameBlock(screenHeight, lane, game, NAME_MAX_LINES);
        int legendRows = legendRowsThatFit(game, ui, name, legendRows(game, now),
                available);
        if (panelHeight(game, ui, name, legendRows) > available) {
            // Last resort, and the only one left: a two-line name is 129 px of a 1080p
            // rail and one line at the prose floor is 44. It is reached in exactly one
            // place — a 5x5 board with Larger Text and both seats still open, where the
            // card is at its cell ceiling and the rail is therefore at its shortest, and
            // it was over by 14 px at 720p, 20 at 1080p and 40 at 4K. Everything above the
            // name is either a player's identity or the shared bar; the name is the only
            // thing here that can yield without something going missing.
            name = nameBlock(screenHeight, lane, game, 1);
        }
        float needed = panelHeight(game, ui, name, legendRows);

        // The closing block is measured rather than guessed at. It used to open only when
        // there were 225 spare pixels, a number that missed by 2.03 px at 1920x1080 — at
        // every board size, in every mode — so the whole tail, the pip strip included, had
        // never once been on a screen. It now asks for what it is actually about to draw.
        Wrapped closing = closingLine(screenHeight, lane, game);
        float spare = available - needed;
        float pips = pipStripHeight(lane, game);
        boolean showPips = spare >= pad + tailBlockHeight(closing, pips);
        boolean showTail = showPips || spare >= pad + tailBlockHeight(closing, 0);
        float bottom = top + Math.min(available, showTail ? available : needed);

        draw.panel(canvas, left, top, right, bottom, ui.highContrastOn ? 246 : 228);

        float y = drawWhereWeAre(canvas, left + pad, top + pad, right - pad, game, ui,
                name);

        float label = labelSize();
        String seatsEyebrow = seatsEyebrow(ui);
        draw.text(canvas, seatsEyebrow, left + pad, y + label * 1.28f,
                eyebrowSize(seatsEyebrow, lane),
                Theme.secondaryText(ui.highContrastOn), Paint.Align.LEFT, true);
        y += label * 1.9f;

        y = drawSeats(canvas, screenHeight, left + pad, y, right - pad, game, ui, now);

        y += Theme.scale(PROGRESS_GAP);
        drawProgress(canvas, screenHeight, left + pad, y, right - pad, game, ui);
        y += progressHeight() + Theme.scale(LEGEND_GAP);

        drawLegend(canvas, screenHeight, left + pad, y, right - pad, ui, legendRows);
        y += legendStep() * legendRows;

        if (showTail) {
            // Anchored to the foot of the rail rather than stacked under the legend. The
            // spare height belongs to neither block, and pushing it between them turns it
            // into air below a closing note instead of a panel that stops early — the two
            // big surfaces on screen finally share a bottom edge.
            float tailTop = bottom - pad - tailBlockHeight(closing, showPips ? pips : 0);
            drawTail(canvas, left + pad, tailTop, right - pad, game, ui, closing, showPips);
        }
    }

    /**
     * Both seats, and the invitation under whichever of them is empty.
     *
     * <p>The one place the rail's height depends on who is in the room: an open seat costs
     * an extra {@link #inviteHeight()}, which is why {@link #panelHeight} counts
     * {@link #openSeats} rather than assuming two cards. The two have to advance by the same
     * terms or the legend below ends up clipped, so they are read together.
     *
     * @return the y the next block starts at, exactly as {@link #drawWhereWeAre} does.
     */
    private float drawSeats(Canvas canvas, float screenHeight, float left, float y,
                            float right, GameState game, UiState ui, long now) {
        for (int player = 0; player < 2; player++) {
            drawSeat(canvas, left, y, right, player, game, ui, now);
            y += Theme.scale(CARD_HEIGHT);
            if (!ui.joined[player]) {
                float textLeft = left + Theme.scale(22);
                draw.text(canvas, "press any button", textLeft, y + railTextSize(),
                        fitRail("press any button", right - textLeft, screenHeight),
                        Theme.SOFT_TEXT, Paint.Align.LEFT, false);
                y += inviteHeight();
            }
            if (player == 0) {
                y += Theme.scale(CARD_GAP);
            }
        }
        return y;
    }

    /**
     * One seat.
     *
     * <p>A dot, a name, and — when nobody is sitting there — the invitation, on its own
     * line underneath. No number: see the notes on the class. Everything is measured
     * against the colour the card actually ends up, not against the panel behind it and
     * not against an assumption that a player colour is light. With
     * {@link Comfort#distinctPlayers} on, Sky is a deep teal whose whole point is that it
     * is <em>darker</em> than Rose; drawn raw on a dark panel her card had a 2:1 outline,
     * a 2:1 name and a dot that all but vanished. {@link Theme#readableOn} keeps the hue
     * and lifts only as far as it must.
     *
     * <h3>An empty seat is an invitation, not a disabled control</h3>
     *
     * <p>The open card's name used to be blended 62% back into its own fill, which took a
     * carefully computed 4.5:1 down to 2.98:1 for Rose and 3.58:1 for Sky — both under AA,
     * and both <em>below the caption underneath them</em>, so the least readable words in
     * the whole panel were the ones a person who is not playing yet has to read. The dim is
     * gone. Open and joined are told apart by a hollow ring against a filled dot, by the
     * missing outline, by a card that is 9% of the colour instead of 23% — and by the ring
     * breathing, which is the one thing on the rail that says "there is room here" rather
     * than "this is switched off".
     */
    private void drawSeat(Canvas canvas, float left, float top, float right, int player,
                          GameState game, UiState ui, long now) {
        int color = Theme.playerColor(player);
        boolean joined = ui.joined[player];
        float height = Theme.scale(CARD_HEIGHT);
        float radius = Theme.scale(Theme.RADIUS_CHIP);

        // Composited rather than translucent, so the colour choices below are exact.
        int fill = Draw.blend(Theme.PANEL, color,
                joined ? Theme.SEAT_FILL_JOINED : Theme.SEAT_FILL_OPEN);
        int accent = Theme.readableOn(color, fill, 3);

        draw.roundRect(canvas, left, top, right, top + height, radius, fill);
        if (joined) {
            draw.roundRectStroke(canvas, left, top, right, top + height, radius,
                    Theme.hairline(), accent);
        }

        float dotX = left + Theme.scale(22);
        float centreY = top + height / 2;
        if (joined && !Comfort.get().calmMotion) {
            // Phase taken modulo the cycle before it reaches a float. The clock is
            // SystemClock.uptimeMillis, and a television left on for a week hands over
            // 6e8 — where a float's ulp is 0.016 and the dot's beat quantises into steps
            // coarser than a frame, so a slow pulse starts to tick instead of breathe.
            float beat = (float) Math.abs(Math.sin(now % SEAT_PULSE_MS / 1500f + player));
            draw.circle(canvas, dotX, centreY, Theme.scale(11) * (.92f + beat * .12f),
                    accent);
        } else if (joined) {
            draw.circle(canvas, dotX, centreY, Theme.scale(11), accent);
        } else {
            draw.circleStroke(canvas, dotX, centreY, Theme.scale(10),
                    openRingWidth(player, now), accent);
        }

        float nameSize = Theme.textSize(Theme.CAPTION);
        draw.text(canvas, Theme.playerName(player).toUpperCase(java.util.Locale.US),
                left + Theme.scale(42), centreY + draw.capCentreOffset(nameSize),
                nameSize, Theme.readableOn(color, fill, 4.5), Paint.Align.LEFT, true);

        if (joined) {
            drawSeatActivity(canvas, right, centreY, player, game, accent, now);
        }
    }

    /**
     * How thick an empty seat's ring is drawn: a slow breath between {@code scale(2)} and
     * {@code scale(3.2)}, plus a brief widening when somebody pushes a stick on a
     * controller that has not joined yet.
     *
     * <p>Held at its resting width under {@link Comfort#calmMotion} — the stir still lands,
     * because that one is a direct answer to something a person just did rather than
     * ambient movement, and swallowing it would leave them with no answer at all.
     */
    private float openRingWidth(int player, long now) {
        float resting = Theme.hairline();
        float stir = 0;
        long since = now - seatStirredAt[player];
        if (seatStirredAt[player] > 0 && since >= 0 && since < SEAT_STIR_MS) {
            stir = Theme.scale(2.4f) * (1 - Draw.easeOut(since / (float) SEAT_STIR_MS));
        }
        if (Comfort.get().calmMotion) {
            return resting + stir;
        }
        // Modulo before the float, for the reason spelled out on the beating dot.
        float phase = now % (long) SEAT_BREATH_MS / SEAT_BREATH_MS;
        float breath = (float) (1 + Math.sin(phase * Math.PI * 2)) / 2;
        return resting + (Math.max(resting, Theme.scale(3.2f)) - resting) * breath + stir;
    }

    /**
     * What that player just did, at the far end of their own card.
     *
     * <p>Each seat is 78 px tall and the full 318 px lane wide, and over half of it was
     * blank colour to the right of a four-letter name that never changed. This fills it
     * with the one thing worth knowing about a partner you are not looking at: the mark
     * they last made, in the shape the board draws it, fading to nothing over
     * {@value #SEAT_GLYPH_MS} ms.
     *
     * <p>Deliberately never a count and never a total. A glyph that stays becomes a status
     * light, a status light becomes a scoreboard, and the whole rail exists because this
     * game does not have one. The fade to nothing is what keeps it a glance rather than a
     * tally.
     */
    private void drawSeatActivity(Canvas canvas, float right, float centreY, int player,
                                  GameState game, int accent, long now) {
        long at = game.lastMarkAt[player];
        long since = now - at;
        if (at <= 0 || since < 0 || since >= SEAT_GLYPH_MS) {
            return;
        }
        float fade = Comfort.get().calmMotion ? 1
                : 1 - Draw.easeOut(since / (float) SEAT_GLYPH_MS);
        int ink = Draw.withAlpha(accent, (int) (255 * fade));
        float size = Theme.scale(22);
        float cx = right - Theme.scale(24);
        float half = size / 2;

        byte kind = game.lastMarkKind[player];
        if (kind == Puzzle.FILLED) {
            draw.roundRect(canvas, cx - half, centreY - half, cx + half, centreY + half,
                    Theme.scale(4), ink);
        } else if (kind == Puzzle.CROSSED) {
            draw.text(canvas, "✕", cx, centreY + draw.capCentreOffset(size * 1.15f),
                    size * 1.15f, ink, Paint.Align.CENTER, true);
        } else {
            // A square put back: the same shape, hollow, so undoing reads as the opposite
            // of doing rather than as a different kind of event.
            draw.roundRectStroke(canvas, cx - half, centreY - half, cx + half,
                    centreY + half, Theme.scale(4), Theme.hairline(), ink);
        }
    }

    /**
     * The one number in the rail: how much of the picture the two of them have found.
     *
     * <p>Drawn once, as a single bar, because a single bar cannot be read as a comparison.
     * The fill runs from Rose's warm end to Sky's cool one — the same ramp the win card
     * assembles the finished picture in — so it reads as one thing the pair are making
     * rather than two contributions being weighed.
     *
     * <p>The empty half is {@link Theme#TRACK}. It was {@code blend(PANEL, CREAM, .16f)},
     * which measures 1.61:1 on the panel it sits on — under the 3:1 floor for a graphical
     * object, and a shape you can only find once you already know it is there. At 0% the
     * bar read as a decorative rule rather than as an empty measure, and at 55% there was
     * no way to see where the measure ended, so "35 of 64 squares" had nothing to point at.
     * A hairline inset stroke keeps the full span findable even where the fill covers the
     * track.
     *
     * <p><b>And at 0% it is not drawn at all.</b> Raising the track to 3:1 fixed whether the
     * empty capsule could be <em>found</em> and not whether it was worth finding. A fresh
     * board pairs it with "Take your time", and a mood line over an empty capsule states a
     * measure of nothing twice: the label already says no square has been placed, and the
     * bar can only agree with it. Rendered, {@code 21-game-page-turn} showed the pair — a
     * grey pill sitting under a soft sentence, reading as a component still loading rather
     * than as a puzzle waiting to be started. The bar now appears with the first square,
     * which is also the first moment it has anything to say; the row keeps its height
     * either way (see {@link #progressHeight()}), so nothing below it moves when it arrives.
     */
    private void drawProgress(Canvas canvas, float screenHeight, float left, float top,
                              float right, GameState game, UiState ui) {
        float size = railTextSize();
        String line = ui.won ? "" : progressLabel(game);
        if (!line.isEmpty()) {
            draw.text(canvas, line, left, top + size,
                    fitRail(line, right - left, screenHeight),
                    ui.highContrastOn ? Theme.CREAM : Theme.SOFT_TEXT, Paint.Align.LEFT,
                    false);
        }
        if (!showsProgressBar(game)) {
            return;
        }

        float barTop = top + size * 1.24f;
        float barBottom = barTop + Theme.scale(PROGRESS_BAR);
        float radius = (barBottom - barTop) / 2;
        draw.roundRect(canvas, left, barTop, right, barBottom, radius, Theme.TRACK);
        drawProgressFill(canvas, left, barTop, right, barBottom, radius,
                Draw.clamp01(game.pictureProgress()));
        draw.roundRectStroke(canvas, left, barTop, right, barBottom, radius,
                Theme.keyline(), Draw.withAlpha(Theme.CREAM, 60));
    }

    /**
     * Whether the rail has a measure to draw, as opposed to an empty one.
     *
     * <p>Pulled out of {@link #drawProgress} so the "nothing placed yet" case can be checked
     * without a canvas: the rail's drawing code takes a {@link Canvas} and the test suite has
     * no recorder for one, so a rule that lives only inside a paint call is a rule no test
     * can reach. It is the same reason {@link #progressLabel} is a static returning a String
     * rather than a call to {@code draw.text}.
     */
    static boolean showsProgressBar(GameState game) {
        return Draw.clamp01(game.pictureProgress()) > 0;
    }

    /** The Rose-to-Sky ramp, from the left-hand end to wherever the pair have reached. */
    private void drawProgressFill(Canvas canvas, float left, float barTop, float right,
                                  float barBottom, float radius, float progress) {
        float end = left + Math.max(radius * 2, (right - left) * progress);
        int warm = Theme.playerColor(0);
        int cool = Theme.playerColor(1);
        draw.roundRect(canvas, left, barTop, end, barBottom, radius,
                Theme.togetherColor());
        // The ramp itself, laid inside the rounded ends so the pill keeps its shape.
        float inner = end - radius;
        for (float x = left + radius; x < inner; x += radius) {
            float step = Math.min(radius, inner - x);
            draw.roundRect(canvas, x, barTop, x + step, barBottom, 0,
                    Draw.blend(warm, cool, (x - left) / Math.max(1f, right - left)));
        }
    }

    /**
     * The legend, in one of its two states. See {@link #legendRows}.
     *
     * <p>Collapsed, it is the same four chips in the same four colours on one line with no
     * sentences — a reminder of which buttons are in play rather than a lesson in what they
     * do. The hint chip still goes dark when hints are resting, because that is the one
     * thing in the legend that changes and the one thing worth still saying.
     */
    private void drawLegend(Canvas canvas, float screenHeight, float left, float top,
                            float right, UiState ui, int rows) {
        String[] buttons = legendButtons();
        boolean[] active = legendActive(ui);
        int[] colors = legendColors();
        float step = legendStep();
        if (rows == 1) {
            drawLegendStrip(canvas, left, top + step * .5f, right, buttons, colors,
                    active);
            return;
        }
        String[] labels = legendLabels(ui);
        for (int row = 0; row < buttons.length; row++) {
            drawLegendRow(canvas, screenHeight, left, top + step * (row + .5f), right,
                    buttons[row], labels[row], colors[row], active[row]);
        }
    }

    /**
     * Every chip on one line, shrunk together if the row would otherwise run off the rail.
     *
     * <p>At the default type size the four gamepad chips take 197 px of the 318 px lane and
     * the four remote ones 243 px, so the scale is 1 in the ordinary case. With Larger Text
     * the remote row asks for 362 px and comes back to 311 — the chips get smaller, which
     * is the correct answer for a strip whose whole job is to be recognised rather than
     * read.
     */
    private void drawLegendStrip(Canvas canvas, float left, float centreY, float right,
                                 String[] buttons, int[] colors, boolean[] active) {
        float height = railTextSize() * 1.26f;
        float gap = Theme.scale(LEGEND_CHIP_GAP);
        float total = gap * (buttons.length - 1);
        for (String button : buttons) {
            total += chipWidth(button, height);
        }
        float room = right - left;
        if (total > room && total > 0) {
            height *= room / total;
            gap *= room / total;
        }

        float x = left;
        for (int chip = 0; chip < buttons.length; chip++) {
            float width = chipWidth(buttons[chip], height);
            drawChip(canvas, x, centreY, width, height, buttons[chip], colors[chip],
                    active[chip]);
            x += width + gap;
        }
    }

    /** A chip is a pill sized to its own text, and a single character comes out round. */
    private float chipWidth(String button, float height) {
        return Math.max(height, draw.measure(button, glyphSize(button, height), true)
                + height * .50f);
    }

    private float glyphSize(String button, float height) {
        return height / 1.26f * (button.length() > 1 ? .52f : .78f);
    }

    /** The pill and the button's name on it, centred on {@code centreY}. */
    private void drawChip(Canvas canvas, float left, float centreY, float width,
                          float height, String button, int color, boolean active) {
        int chip = active ? color : Draw.blend(Theme.PANEL, color, .34f);
        float glyph = glyphSize(button, height);
        draw.roundRect(canvas, left, centreY - height / 2, left + width,
                centreY + height / 2, height / 2, chip);
        draw.text(canvas, button, left + width / 2,
                centreY + draw.capCentreOffset(glyph), glyph, Theme.textOn(chip),
                Paint.Align.CENTER, true);
    }

    /**
     * One legend row: a chip with the button's name on it, then what the button does.
     *
     * <p>The chip is a pill sized to its text rather than a fixed circle, because a bare
     * remote's keys are called OK, HOLD, MENU and BACK, not A, B and Y — a one-character
     * circle cannot say any of them. A single character still comes out round.
     *
     * <p>The chip is composited rather than translucent so its final colour is known, and
     * the glyph on it is then <em>asked for</em> rather than assumed to be ink. A resting
     * chip is genuinely dark, and plum ink on it was invisible; that was already true of
     * the hint chip with hints off, before any player colour got involved.
     *
     * <p>The label's floor is 92% of the ordinary prose floor rather than the floor itself.
     * With Larger Text and a bare remote, "Fill, cross, clear" measures 250.0 px at the
     * floor against a 235.0 px lane — because the chip grows with the type too, taking
     * 58.9 px of the 311.3 px the rail has. Fifteen pixels over is a clipped word. At 92%
     * of the floor it measures 230.0 and fits. This is the one place in the rail where a
     * whole sentence shares its line with a four-letter chip, so it is the one place that
     * needs the sliver.
     */
    private void drawLegendRow(Canvas canvas, float screenHeight, float left,
                               float centreY, float right, String button, String label,
                               int color, boolean active) {
        float labelSize = railTextSize();
        float height = labelSize * 1.26f;
        float width = chipWidth(button, height);
        drawChip(canvas, left, centreY, width, height, button, color, active);

        // A dimmed-but-solid colour rather than translucent GRID: GRID is a paper tone
        // and only reaches 2.1:1 on a panel, which is not a "resting" affordance so much
        // as an invisible one.
        int text = active ? Theme.CREAM : Draw.blend(Theme.PANEL, Theme.CREAM, .55f);
        float textLeft = left + width + Theme.scale(10);
        float floor = BoardLayout.dp(Theme.MIN_PROSE_SP, screenHeight) * .92f;
        draw.text(canvas, label, textLeft, centreY + draw.capCentreOffset(labelSize),
                fit(label, right - textLeft, labelSize, floor, false), text,
                Paint.Align.LEFT, false);
    }

    // ---- The tail: what the rail says when there is room to say it --------------------

    /** The heading over the tail block. See {@link #puzzlesBeforeTonight}. */
    static String tailLabel(GameState game) {
        if (game.storyMode) {
            return "THE STORY BOOK";
        }
        return knowsTonight() ? "TONIGHT" : "TOGETHER SO FAR";
    }

    /**
     * The line under that heading.
     *
     * <p>In the story book it says how much book is left, which is the thing a couple most
     * want to feel and the one thing the old block could not show. Outside it, it counts
     * pictures — this evening's, once somebody has told us when the evening started.
     */
    static String tailLine(GameState game) {
        if (game.storyMode) {
            int left = PuzzleLibrary.count() - (game.storyIndex + 1);
            if (left <= 0) {
                return "The last chapter";
            }
            if (left == 1) {
                return "One chapter still to come";
            }
            return capitalise(word(left)) + " chapters still to come";
        }
        int made = picturesToShow(game);
        if (made <= 0) {
            return knowsTonight() ? "The evening is young" : "The first one is waiting";
        }
        return capitalise(word(made)) + (made == 1 ? " picture done" : " pictures done");
    }

    /**
     * The rail spelled its own copy of the number words, and the copy went stale in the
     * same breath the win card's did: when the Story Book grew to twenty-four chapters,
     * chapter one's rail read "23 chapters still to come". Two tables that have to agree
     * are one table, so this defers to {@link WinScene#word(int)} — the rail and the win
     * card say the same number about the same book four seconds apart, and a player who
     * saw them disagree would be right to think one of them was lying.
     */
    private static String word(int value) {
        return WinScene.word(value);
    }

    /** And the sentence case that goes with it, from the same place. */
    private static String capitalise(String value) {
        return WinScene.capitalise(value);
    }

    /** The closing sentence, wrapped and sized for the rail's lane. */
    private Wrapped closingLine(float screenHeight, float lane, GameState game) {
        return wrapToFit(tailLine(game), lane, railTextSize(),
                BoardLayout.dp(Theme.MIN_PROSE_SP, screenHeight), TAIL_MAX_LINES);
    }

    /**
     * How tall the closing block is: the rule above it, its heading, the whole of its
     * sentence, and the pip strip when there is room for one.
     *
     * <p>The pips are asked for separately because they are the one part that can be left
     * out without anything reading as broken — whereas a heading with half a sentence under
     * it reads as broken immediately. So the block opens for its words and takes the pips
     * if they fit, which is what lets it appear with Larger Text on, where the same words
     * are 30% taller and the strip no longer fits under them.
     */
    private float tailBlockHeight(Wrapped closing, float pipStrip) {
        return railRule()                                   // the rule
                + Theme.scale(18)                           // air under it
                + labelSize() * 1.9f                        // the heading
                + closing.lines.length * closing.size * 1.24f
                + pipStrip;
    }

    /** The air before the pips and the rows of them, or zero when none are worth drawing. */
    private float pipStripHeight(float lane, GameState game) {
        int rows = pipRows(lane, pipCount(game));
        return rows == 0 ? 0 : Theme.scale(10) + rows * pipPitch(lane, pipCount(game), rows);
    }

    /**
     * A quiet closing note in whatever space a tall board leaves beside it: where the pair
     * are up to, and a strip of pips for the rest.
     *
     * <p>In the story book every chapter gets a pip, all {@link PuzzleLibrary#count()} of
     * them, wrapping onto further rows rather than shrinking below the point of being seen
     * — the same complete row {@code WinScene.drawChapters} draws, because "chapter 10 of
     * 24" is only a fact if the fourteen that are left are on screen too. The old strip
     * capped at ten and filled up for good a third of the way through the book.
     *
     * <p>This block had never been drawn. {@code showTail} asked for 225 spare pixels and
     * the rail had 223.7 — it missed by 2.03 px at 1920x1080, identically at 5x5, 10x10,
     * 15x15 and 20x20, because the board is height-limited at all of them so the rail's
     * spare height never moves. The rail therefore stopped at y≈803 with the paper card
     * running to y≈943 and the safe line at 1026, and the two big surfaces of the screen
     * did not share a bottom edge. The threshold is now measured from what is about to be
     * drawn (see {@link #tailBlockHeight}), and the legend collapsing (see
     * {@link #LEGEND_LEARNED_AFTER}) is what pays for it.
     */
    private void drawTail(Canvas canvas, float left, float top, float right,
                          GameState game, UiState ui, Wrapped closing, boolean withPips) {
        drawRailRule(canvas, left, top, right);

        float y = top + Theme.scale(18);
        float label = labelSize();
        draw.text(canvas, tailLabel(game), left, y + label * 1.28f,
                eyebrowSize(tailLabel(game), right - left),
                Theme.secondaryText(ui.highContrastOn), Paint.Align.LEFT, true);
        y += label * 1.9f;

        // A narrow column is exactly where a sentence is expected to turn rather than
        // shrink, so it turns first and only shrinks if three lines still will not do it.
        for (String line : closing.lines) {
            draw.text(canvas, line, left, y + closing.size, closing.size, Theme.CREAM,
                    Paint.Align.LEFT, false);
            y += closing.size * 1.24f;
        }
        if (withPips) {
            drawPips(canvas, left, y + Theme.scale(10), right - left, game);
        }
    }

    /** How many pips the strip carries: every chapter, or the evening's own pictures. */
    private static int pipCount(GameState game) {
        return game.storyMode ? Math.max(1, PuzzleLibrary.count())
                // Always a strip of at least five outside the book, so an empty evening
                // reads as room to fill rather than as one lonely outline.
                : Math.min(10, Math.max(5, picturesToShow(game)));
    }

    /** The chapters, or the evening: one pip each, hearts for the ones that are done. */
    private void drawPips(Canvas canvas, float left, float top, float lane,
                          GameState game) {
        int total = pipCount(game);
        int made = game.storyMode ? Math.min(total, game.storyFurthest + 1)
                : picturesToShow(game);
        int current = game.storyMode ? game.storyIndex : -1;

        int rows = pipRows(lane, total);
        if (rows == 0) {
            return;
        }
        int perRow = (total + rows - 1) / rows;
        float pitch = pipPitch(lane, total, rows);
        for (int pip = 0; pip < total; pip++) {
            float cx = left + pitch * (pip % perRow + .5f);
            float cy = top + pitch * (pip / perRow + .5f);
            if (pip == current) {
                draw.heart(canvas, cx, cy, pitch * .62f, Theme.GOLD);
            } else if (pip < made) {
                draw.heart(canvas, cx, cy, pitch * .58f, Theme.PINK);
            } else {
                draw.circle(canvas, cx, cy, pitch * .11f,
                        Draw.withAlpha(Theme.CREAM, 90));
            }
        }
    }

    /**
     * How many rows the strip takes: the fewest the lane allows at
     * {@link #PIP_MIN_PITCH}, splitting evenly rather than filling one row and orphaning
     * the rest. Zero when even that would put the pips under {@link Theme#MIN_PIP_PX},
     * where a pip stops being a shape and becomes a speck — and a strip of specks says
     * less than no strip at all.
     */
    private int pipRows(float lane, int total) {
        int most = Math.max(1, (int) (lane / Theme.scale(PIP_MIN_PITCH)));
        int rows = (total + most - 1) / most;
        return pipPitch(lane, total, rows) >= Theme.scale(Theme.MIN_PIP_PX) ? rows : 0;
    }

    private float pipPitch(float lane, int total, int rows) {
        return Math.min(Theme.scale(PIP_MAX_PITCH), lane / ((total + rows - 1) / rows));
    }

    // ---- Message ribbon --------------------------------------------------------------

    /**
     * How wide the pill is allowed to grow: symmetric about the paper card's centre, clear
     * of the safe edge on one side and of the rail on the other. 1307 px at 1920x1080, at
     * every board size.
     *
     * <p>It used to be the card's own width — 694 px at 5x5 — which is where the overflow
     * came from. The card is where the ribbon is <em>centred</em>, because that is the
     * column the eye is in; it is not a wall the ribbon has to live behind. The pill floats
     * over the backdrop below the card, so the only real walls are the safe area and the
     * rail, and the clamp against {@link BoardLayout#panelLeft} is what keeps it clear of
     * the rail now that the rail runs all the way to the safe line.
     */
    static float ribbonLane(float screenWidth, BoardLayout board) {
        float centre = (board.cardLeft() + board.cardRight()) / 2;
        float toSafeEdge = centre - screenWidth * Theme.SAFE_AREA;
        float toRail = board.panelLeft - Theme.scale(24) - centre;
        return 2 * Math.max(0, Math.min(toSafeEdge, toRail));
    }

    /**
     * The ribbon.
     *
     * <p>Three things were wrong with it and all three were geometry. It sat 2.6% from the
     * bottom of the screen, inside the band a TV is allowed to overscan, so on some panels
     * the message simply was not there. It was centred on the <em>viewport</em> while the
     * board it comments on is not — 166 px adrift on a 1080p screen. And its width was
     * whatever its text happened to measure, from 277 px to 1138 px, the latter 1.7× the
     * grid it belongs to.
     *
     * <p>A fourth was worse than all of them, and this class's own Javadoc asserted the
     * opposite: "HudSceneTest holds every message the game can show to one line at the
     * narrowest card". No such test existed, and the text was not held to anything at all —
     * see {@link #wrap}. "Press a button on each controller to join", the invitation that
     * makes this a two-player game, measures 690 px against a 607 px pill at 5x5, and the
     * words at each end sat on the bare wallpaper with nothing behind them.
     *
     * <p>Now: bottom edge on the safe line (see {@link #ribbonInset}), centred on the paper
     * card, as wide as {@link #ribbonLane} allows, and shrunk to the prose floor if a
     * message still will not fit. {@link BoardLayout#footerHeight} reserves
     * {@link #RIBBON_MAX_LINES} lines and the pill never draws more, so the board's height
     * does not move when a long message arrives.
     */
    private void drawToast(Canvas canvas, float screenWidth, float screenHeight,
                           BoardLayout board, UiState ui, long now) {
        if (ui.toast == null || ui.toast.isEmpty()) {
            return;
        }
        long age = ui.toastAt == 0 ? 0 : now - ui.toastAt;
        if (ui.toastAt != 0 && age > TOAST_MS) {
            return;
        }
        float appear = Draw.easeOut(Math.min(1, age / 260f));
        float fade = age > TOAST_MS - TOAST_FADE_MS
                ? 1 - (age - (TOAST_MS - TOAST_FADE_MS)) / (float) TOAST_FADE_MS : 1;
        fade = Draw.clamp01(fade);

        float centre = (board.cardLeft() + board.cardRight()) / 2;
        float pad = Theme.scale(26);
        float widest = ribbonLane(screenWidth, board);
        Wrapped message = wrapToFit(ui.toast, widest - pad * 2, ribbonTextSize(),
                BoardLayout.dp(Theme.MIN_PROSE_SP, screenHeight), RIBBON_MAX_LINES);
        String[] lines = message.lines;
        float size = message.size;

        // The pill hugs a short message and stops at the lane, full stop. It used to be
        // allowed past the lane rather than let a word out of the pill, on the grounds that
        // a sentence lying on the wallpaper is worse than a few pixels of overscan. Both are
        // avoidable: {@link #ribbonLane} is already measured against the safe edge and the
        // rail, so clamping here keeps the rounded cap inside the 5% band on every panel,
        // and anything that still will not fit is tightened between the glyphs below rather
        // than pushed out of the ends. Measured at 1920x1080 the pill's left cap sat at
        // x=68 against a safe edge of 96, with 5 px of amber ink outside it.
        float text = widestLine(lines, size);
        float half = Math.min(widest, text + pad * 2) / 2;
        float bottom = screenHeight - ribbonInset(screenHeight)
                - (1 - appear) * Theme.scale(14);
        float top = bottom - ribbonHeight(lines.length);
        float radius = Math.min((bottom - top) / 2, half);

        draw.roundRect(canvas, centre - half, top, centre + half, bottom, radius,
                Draw.withAlpha(Theme.PANEL, (int) (228 * fade)));
        draw.roundRectStroke(canvas, centre - half, top, centre + half, bottom, radius,
                Math.max(1.2f, Theme.scale(1.6f)),
                Draw.withAlpha(ui.toastColor, (int) (125 * fade)));

        int ink = Draw.withAlpha(Theme.readableOn(ui.toastColor, Theme.PANEL, 4.5),
                (int) (255 * fade));
        float pitch = size * RIBBON_LINE_EM;
        float first = (top + bottom) / 2 + draw.capCentreOffset(size)
                - pitch * (lines.length - 1) / 2;
        float room = half * 2 - pad;
        for (int line = 0; line < lines.length; line++) {
            float tracking = trackingToFit(lines[line], size, room);
            if (tracking < 0) {
                draw.tracked(canvas, lines[line], centre, first + pitch * line, size, ink,
                        tracking, true);
            } else {
                draw.text(canvas, lines[line], centre, first + pitch * line, size, ink,
                        Paint.Align.CENTER, true);
            }
        }
    }

    /**
     * How much to tighten a line's tracking so it fits the pill, as a fraction of the type
     * size; zero when it already fits.
     *
     * <p>The last resort, and deliberately a small one. {@link #wrapToFit} has already tried
     * turning the message onto another line and shrinking the type to the prose floor, so by
     * the time anything reaches here it is a sentence longer than the game writes — the
     * widest real message is 690 px against a 1229 px lane at 1080p. Tightening the space
     * between glyphs is what the clue digits do in the same situation and for the same
     * reason: height is what carries legibility across a room, width is what causes
     * collisions, so the thing to give up is width.
     *
     * <p>Floored at {@link #TRACK_FLOOR}, because tracking taken far enough runs the words
     * together, and a pill of touching letters is not an improvement on a pill that is
     * slightly too small for its sentence.
     */
    private float trackingToFit(String line, float size, float room) {
        int gaps = line.length() - 1;
        if (gaps < 1 || room <= 0 || size <= 0) {
            return 0;
        }
        float over = draw.measure(line, size, true) - room;
        return over <= 0 ? 0 : Math.max(TRACK_FLOOR, -over / (gaps * size));
    }

    /** Tightest the ribbon's tracking is ever squeezed. See {@link #trackingToFit}. */
    private static final float TRACK_FLOOR = -.05f;

    /** Lines of a message, and the type size they were laid out at and must be drawn at. */
    static final class Wrapped {
        final String[] lines;
        final float size;

        Wrapped(String[] lines, float size) {
            this.lines = lines;
            this.size = size;
        }
    }

    /**
     * Lays a message out to fit a lane: turns it onto as many lines as it is allowed, and
     * then shrinks the type if the widest of them is still too wide.
     *
     * <p>This exists because {@link #wrap} alone cannot promise a fit and its callers were
     * behaving as though it could. Going through here, they cannot: the size comes back
     * with the lines, so a caller has to draw at the size the layout was measured at.
     *
     * <p>The second pass is not cosmetic. Shrinking moves where the breaks fall, so laying
     * out again at the smaller size is the difference between a line that fits and a line
     * that was measured at one size and drawn at another. One extra pass is enough for
     * every string in the game: the shrink is proportional, so it cannot overshoot.
     */
    private Wrapped wrapToFit(String message, float room, float preferred, float floor,
                              int maxLines) {
        String[] lines = wrap(message, preferred, room, maxLines);
        float widest = widestLine(lines, preferred);
        if (widest <= room || widest <= 0 || room <= 0) {
            return new Wrapped(lines, preferred);
        }
        float size = Math.max(floor, preferred * room / widest);
        return new Wrapped(wrap(message, size, room, maxLines), size);
    }

    private float widestLine(String[] lines, float size) {
        float widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, draw.measure(line, size, true));
        }
        return widest;
    }

    /**
     * Breaks a message at its spaces into at most {@code maxLines} lines.
     *
     * <p>Every line fits {@code room} except the last, which keeps whatever is left over —
     * a bounded box has to put the remainder somewhere, and a chopped word reads as a bug
     * where a slightly wide one reads as a long word. {@link #wrapToFit} is what turns that
     * remainder back into a fit, and it is how both callers reach this method.
     *
     * <p>The condition was {@code count == lines.length - 1}, which reads as "the last line
     * takes the rest" and is true — but the ribbon asks for one line, so the last line was
     * also the first, the test was {@code 0 == 0} on every single word, and the function
     * appended unconditionally. It could not split at all, at any width. That is why real
     * messages hung roughly 265 px off each end of the pill while a comment three methods
     * up claimed a test held them to one line.
     */
    private String[] wrap(String message, float size, float room, int maxLines) {
        String[] words = message.trim().split("\\s+");
        String[] lines = new String[Math.max(1, maxLines)];
        int count = 0;
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() == 0) {
                line.append(word);
                continue;
            }
            String candidate = line + " " + word;
            boolean lastLine = count == lines.length - 1;
            if (draw.measure(candidate, size, true) <= room || lastLine) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines[count++] = line.toString();
                line.setLength(0);
                line.append(word);
            }
        }
        lines[count++] = line.toString();
        String[] trimmed = new String[count];
        System.arraycopy(lines, 0, trimmed, 0, count);
        return trimmed;
    }

    /** True while the ribbon is still on screen, so the view keeps animating. */
    public boolean toastVisible(UiState ui, long now) {
        return ui.toast != null && !ui.toast.isEmpty()
                && (ui.toastAt == 0 || now - ui.toastAt <= TOAST_MS);
    }

    // ---- Fitting ---------------------------------------------------------------------

    /** The largest size at or below {@code preferred} that fits, never below the floor. */
    private float fit(String text, float room, float preferred, float floor,
                      boolean strong) {
        if (text.isEmpty() || room <= 0) {
            return preferred;
        }
        float measured = draw.measure(text, preferred, strong);
        if (measured <= room || measured <= 0) {
            return preferred;
        }
        return Math.max(floor, preferred * room / measured);
    }
}
