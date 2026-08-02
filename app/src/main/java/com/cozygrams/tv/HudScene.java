package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Everything around the board while playing: the title line, the side rail and the
 * message ribbon.
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
 * furniture had grown. Two changes give the squares their pixels back:
 *
 * <ul>
 *   <li><b>The title strip is one line, not two.</b> A second line of type across the top
 *       cost {@code CAPTION * 1.72} of board height at every board size. The mode now sits
 *       beside the wordmark on the same baseline and the progress readout moved into the
 *       rail, where it is a bar rather than a whisper.</li>
 *   <li><b>The rail is a fixed width.</b> See {@link BoardLayout#railWidth}. It no longer
 *       swallows whatever the height-capped board leaves behind, and it no longer widens
 *       when someone turns larger text on.</li>
 * </ul>
 *
 * <p>Both files have to agree about that furniture, so every measurement the layout needs
 * is declared here once and read by {@link BoardLayout}: {@link #HEADER_GAP},
 * {@link #ribbonInset}, {@link #ribbonHeight} and {@link #RIBBON_MAX_LINES}.
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

    /** Height of a one-line ribbon, as a multiple of its own text size. */
    static final float RIBBON_HEIGHT_EM = 1.8f;
    /** What each wrapped line after the first adds, as a multiple of the text size. */
    static final float RIBBON_LINE_EM = 1.08f;
    /** The ribbon wraps rather than widening, but never past this many lines. */
    /**
     * The ribbon is one line, and the board is never charged for a second.
     *
     * <p>Reserving a two-line worst case cost every board a whole line of height for a
     * case that gameplay messages never actually reach — and the board is the thing being
     * read. Messages are written to fit; the pill widens to the card before it would ever
     * need to wrap, and {@code HudSceneTest} holds every message the game can show to
     * one line at the narrowest card the layout produces.
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

    /** Air between the bottom of the paper card and the top of the ribbon. */
    static final float RIBBON_GAP = 12f;

    /** Air between the title line and the top of the paper card. */
    static final float HEADER_GAP = 12f;

    /** Inset from the rail's edges to its content. */
    private static final float PANEL_PAD = 14f;
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
    /** Smallest tail block worth opening up below the legend. */
    private static final float TAIL_MIN = 150f;
    /** Smallest pitch a row of story pips may use before it wraps onto another row. */
    private static final float PIP_MIN_PITCH = 18f;
    /** Largest pitch a row of story pips ever uses. */
    private static final float PIP_MAX_PITCH = 26f;

    /**
     * True when every controller that has joined is a bare TV remote — a D-pad, a centre
     * key, Back, and nothing else.
     *
     * <p>Such a controller cannot reach {@code PlayerRegistry.isCross} at all, so the
     * centre key cycles empty → filled → crossed instead of only toggling a fill, and
     * hints move to a held centre press. A legend that still says "A / B / Y" is then
     * three lies in a row, which is worse than no legend.
     *
     * <p><b>To wire it up</b>, in {@code CozyGameView}: one field and two lines inside
     * {@code registerDevice(int deviceId)}, using only public API that already exists.
     *
     * <pre>
     *   private boolean anyGamepad;
     *   ...
     *   anyGamepad |= !players.needsCycleInput(deviceId);
     *   HudScene.setRemoteOnly(players.deviceCount() &gt; 0 &amp;&amp; !anyGamepad);
     * </pre>
     *
     * <p>The rule is deliberately "every controller", not "this controller". If there is
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
     * <p><b>To wire it up</b>, in {@code CozyGameView}'s constructor, immediately after the
     * {@code if (game.puzzle.complete()) game.next();} fix-up — one line:
     *
     * <pre>
     *   HudScene.setPuzzlesBeforeTonight(game.solved);
     * </pre>
     *
     * <p>Until something sets it the rail does not pretend: {@link #tailLabel} says
     * "TOGETHER SO FAR" and shows the lifetime figure, which is at least true. Once the
     * baseline is handed in, the same block becomes "TONIGHT" and counts only this
     * evening's pictures. Either way nothing on screen is a lie, which is why the wiring
     * can land whenever it likes.
     */
    private static int puzzlesBeforeTonight = -1;

    private final Draw draw;

    public HudScene(Draw draw) {
        this.draw = draw;
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

    /** Height of a ribbon of {@code lines} lines. */
    static float ribbonHeight(int lines) {
        float size = ribbonTextSize();
        return size * (RIBBON_HEIGHT_EM + Math.max(0, lines - 1) * RIBBON_LINE_EM);
    }

    /**
     * The ribbon's type size. Caption rather than body: the pill is reserved for at its
     * two-line worst case, and those two lines are subtracted from the board's height on
     * every frame, message or no message. 24 design pixels is 36 px at 1080p — 18
     * arcminutes from ten feet, Leanback's stated body minimum, and the floor
     * {@link Theme#textSize} would clamp anything smaller up to anyway.
     */
    private static float ribbonTextSize() {
        return Theme.textSize(Theme.CAPTION);
    }

    // ---- The frame -------------------------------------------------------------------

    public void draw(Canvas canvas, float width, float height, BoardLayout board,
                     GameState game, UiState ui, long now) {
        drawHeader(canvas, width, height, game, ui);
        if (board.hasRoomForPanel()) {
            drawSidePanel(canvas, height, board, game, ui, now);
        }
        // The win card is about to cover everything with a scrim and say the picture is
        // finished. A ribbon underneath it would be a second, quieter voice saying
        // something else at the same moment.
        if (!ui.won) {
            drawToast(canvas, height, board, ui, now);
        }
    }

    // ---- Title line ------------------------------------------------------------------

    /**
     * One line: the wordmark and where we are on the left, the picture's name on the right.
     *
     * <p>The reserve {@link BoardLayout#headerHeight()} keeps for this is exactly
     * {@code textSize(SUBHEAD) + HEADER_GAP} plus the card's own padding — the baseline is
     * the bottom of the type reserve and descenders hang into the gap, which is what the
     * gap is for.
     */
    private void drawHeader(Canvas canvas, float width, float height, GameState game,
                            UiState ui) {
        float x = width * Theme.SAFE_AREA;
        float right = width - width * Theme.SAFE_AREA;
        float baseline = height * Theme.SAFE_AREA + Theme.textSize(Theme.SUBHEAD);
        float titleSize = Theme.textSize(Theme.SUBHEAD);
        int quiet = Theme.secondaryText(ui.highContrastOn);

        draw.shadowedText(canvas, "COZYGRAMS", x, baseline, titleSize, Theme.PINK,
                Paint.Align.LEFT, true);

        String name = game.puzzle.name;
        float nameLeft = right - draw.measure(name, titleSize, true);
        draw.shadowedText(canvas, name, right, baseline, titleSize, Theme.CREAM,
                Paint.Align.RIGHT, true);

        // The mode rides the same baseline as the wordmark. It is the one thing on this
        // line that can grow without bound, so it is the one thing that yields.
        float modeLeft = x + draw.measure("COZYGRAMS", titleSize, true) + Theme.scale(22);
        String mode = game.storyMode
                ? "STORY  " + (game.storyIndex + 1) + " / " + PuzzleLibrary.count()
                        + "   ·   " + game.size + " × " + game.size
                : "ENDLESS  #" + (game.solved + 1) + "   ·   " + game.size + " × "
                        + game.size;
        float room = nameLeft - Theme.scale(40) - modeLeft;
        if (room > Theme.scale(60)) {
            draw.shadowedText(canvas, mode, modeLeft, baseline,
                    fit(mode, room, Theme.textSize(Theme.CAPTION), false), quiet,
                    Paint.Align.LEFT, false);
        }
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
     * Height the rail needs for the parts it always draws: the section label, both seats,
     * the shared progress bar and every legend row, plus the padding around them.
     *
     * <p>This is the only place that geometry is decided. {@link #drawSidePanel} consumes
     * it in the same order, so a row can never be added to one without the other noticing.
     */
    private float panelHeight(float screenHeight, UiState ui) {
        return Theme.scale(PANEL_PAD)                       // top inset
                + labelSize() * 1.9f                        // "PLAYING TOGETHER"
                + Theme.scale(CARD_HEIGHT) * 2 + Theme.scale(CARD_GAP)
                + openSeats(ui) * inviteHeight(screenHeight)
                + Theme.scale(PROGRESS_GAP) + progressHeight(screenHeight)
                + Theme.scale(LEGEND_GAP)
                // Counted, not assumed: the remote-only legend need not be four rows
                // forever, and a hard-coded count is exactly what clipped the last one.
                + legendStep() * legendButtons().length     // rows are centred in a step
                + Theme.scale(PANEL_PAD);                   // bottom inset
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
    private float inviteHeight(float screenHeight) {
        return railText(screenHeight) * 1.34f;
    }

    /** The count and the bar under it. */
    private float progressHeight(float screenHeight) {
        return railText(screenHeight) * 1.24f + Theme.scale(PROGRESS_BAR);
    }

    /** Pitch of the legend, from whichever of its chip and its label is taller. */
    private float legendStep() {
        return Math.max(Theme.scale(38), Theme.textSize(Theme.CAPTION) * 1.55f);
    }

    private float labelSize() {
        return Theme.textSize(Theme.CAPTION) * .82f;
    }

    /** The type size the rail's copy asks for, before anything is fitted to its lane. */
    private float railText(float screenHeight) {
        return Theme.textSize(Theme.CAPTION);
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
        return fit(text, room, railText(screenHeight),
                BoardLayout.dp(Theme.MIN_PROSE_SP, screenHeight), false);
    }

    private void drawSidePanel(Canvas canvas, float screenHeight, BoardLayout board,
                               GameState game, UiState ui, long now) {
        float left = board.panelLeft;
        float right = board.panelRight;
        float top = board.panelTop();
        float needed = panelHeight(screenHeight, ui);
        float available = board.panelBottom() - top;

        // The rule, in one line: the rail is exactly as tall as what it draws. It is never
        // shorter than the seats, the bar and the legend need — that is what used to clip
        // the last row — and it only grows to the bottom of the safe area when the extra
        // height has something in it, so it is never left half empty either. It may run
        // below the paper card because the ribbon no longer crosses it: the ribbon is
        // centred on the card and capped to it, so the two can never meet.
        boolean showTail = available >= needed + Theme.scale(TAIL_MIN);
        float bottom = top + (showTail ? available : needed);

        draw.panel(canvas, left, top, right, bottom, ui.highContrastOn ? 246 : 228);

        float pad = Theme.scale(PANEL_PAD);
        float y = top + pad;

        float label = labelSize();
        draw.text(canvas, "PLAYING TOGETHER", left + pad, y + label * 1.28f, label,
                Theme.secondaryText(ui.highContrastOn), Paint.Align.LEFT, true);
        y += label * 1.9f;

        for (int player = 0; player < 2; player++) {
            drawSeat(canvas, left + pad, y, right - pad, player, ui, now);
            y += Theme.scale(CARD_HEIGHT);
            if (!ui.joined[player]) {
                float textLeft = left + pad + Theme.scale(22);
                draw.text(canvas, "press any button", textLeft, y + railText(screenHeight),
                        fitRail("press any button", right - pad - textLeft, screenHeight),
                        Theme.SOFT_TEXT, Paint.Align.LEFT, false);
                y += inviteHeight(screenHeight);
            }
            if (player == 0) {
                y += Theme.scale(CARD_GAP);
            }
        }

        y += Theme.scale(PROGRESS_GAP);
        drawProgress(canvas, screenHeight, left + pad, y, right - pad, game, ui);
        y += progressHeight(screenHeight) + Theme.scale(LEGEND_GAP);

        drawLegend(canvas, screenHeight, left + pad, y, right - pad, ui);
        y += legendStep() * legendButtons().length;

        if (showTail) {
            drawTail(canvas, screenHeight, left + pad, y + pad, right - pad, bottom - pad,
                    game, ui);
        }
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
     */
    private void drawSeat(Canvas canvas, float left, float top, float right, int player,
                          UiState ui, long now) {
        int color = Theme.playerColor(player);
        boolean joined = ui.joined[player];
        float height = Theme.scale(CARD_HEIGHT);
        float radius = Theme.scale(14);

        // Composited rather than translucent, so the colour choices below are exact.
        int fill = Draw.blend(Theme.PANEL, color, joined ? .23f : .09f);
        int accent = Theme.readableOn(color, fill, 3);

        draw.roundRect(canvas, left, top, right, top + height, radius, fill);
        if (joined) {
            draw.roundRectStroke(canvas, left, top, right, top + height, radius,
                    Math.max(1.5f, Theme.scale(2)), accent);
        }

        float dotX = left + Theme.scale(22);
        float centreY = top + height / 2;
        if (joined && !Comfort.get().calmMotion) {
            float beat = (float) Math.abs(Math.sin(now / 1500f + player));
            draw.circle(canvas, dotX, centreY, Theme.scale(11) * (.92f + beat * .12f),
                    accent);
        } else if (joined) {
            draw.circle(canvas, dotX, centreY, Theme.scale(11), accent);
        } else {
            draw.circleStroke(canvas, dotX, centreY, Theme.scale(10),
                    Math.max(1.5f, Theme.scale(2)), Draw.blend(fill, accent, .55f));
        }

        float nameSize = Theme.textSize(Theme.CAPTION);
        int nameColor = Theme.readableOn(color, fill, 4.5);
        if (!joined) {
            nameColor = Draw.blend(fill, nameColor, .62f);
        }
        draw.text(canvas, Theme.playerName(player).toUpperCase(java.util.Locale.US),
                left + Theme.scale(42), centreY + draw.capCentreOffset(nameSize),
                nameSize, nameColor, Paint.Align.LEFT, true);
    }

    /**
     * The one number in the rail: how much of the picture the two of them have found.
     *
     * <p>Drawn once, as a single bar, because a single bar cannot be read as a comparison.
     * The fill runs from Rose's warm end to Sky's cool one — the same ramp the win card
     * assembles the finished picture in — so it reads as one thing the pair are making
     * rather than two contributions being weighed.
     */
    private void drawProgress(Canvas canvas, float screenHeight, float left, float top,
                              float right, GameState game, UiState ui) {
        float size = railText(screenHeight);
        String line = ui.won ? "" : progressLabel(game);
        if (!line.isEmpty()) {
            draw.text(canvas, line, left, top + size,
                    fitRail(line, right - left, screenHeight),
                    ui.highContrastOn ? Theme.CREAM : Theme.SOFT_TEXT, Paint.Align.LEFT,
                    false);
        }

        float barTop = top + size * 1.24f;
        float barBottom = barTop + Theme.scale(PROGRESS_BAR);
        float radius = (barBottom - barTop) / 2;
        draw.roundRect(canvas, left, barTop, right, barBottom, radius,
                Draw.blend(Theme.PANEL, Theme.CREAM, .16f));

        float progress = Draw.clamp01(game.pictureProgress());
        if (progress <= 0) {
            return;
        }
        float end = left + Math.max(radius * 2, (right - left) * progress);
        int warm = Theme.playerColor(0);
        int cool = Theme.playerColor(1);
        draw.roundRect(canvas, left, barTop, end, barBottom, radius,
                Draw.blend(warm, cool, .5f));
        // The ramp itself, laid inside the rounded ends so the pill keeps its shape.
        float inner = end - radius;
        for (float x = left + radius; x < inner; x += radius) {
            float step = Math.min(radius, inner - x);
            draw.roundRect(canvas, x, barTop, x + step, barBottom, 0,
                    Draw.blend(warm, cool, (x - left) / Math.max(1f, right - left)));
        }
    }

    private void drawLegend(Canvas canvas, float screenHeight, float left, float top,
                            float right, UiState ui) {
        String[] buttons = legendButtons();
        String[] labels = legendLabels(ui);
        boolean[] active = legendActive(ui);
        int[] colors = legendColors();
        float step = legendStep();
        for (int row = 0; row < buttons.length; row++) {
            drawLegendRow(canvas, screenHeight, left, top + step * (row + .5f), right,
                    buttons[row], labels[row], colors[row], active[row]);
        }
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
     */
    private void drawLegendRow(Canvas canvas, float screenHeight, float left,
                               float centreY, float right, String button, String label,
                               int color, boolean active) {
        float labelSize = railText(screenHeight);
        float height = labelSize * 1.26f;
        float glyphSize = labelSize * (button.length() > 1 ? .52f : .78f);

        int chip = active ? color : Draw.blend(Theme.PANEL, color, .34f);
        float width = Math.max(height,
                draw.measure(button, glyphSize, true) + height * .50f);
        draw.roundRect(canvas, left, centreY - height / 2, left + width,
                centreY + height / 2, height / 2, chip);
        draw.text(canvas, button, left + width / 2,
                centreY + draw.capCentreOffset(glyphSize), glyphSize, Theme.textOn(chip),
                Paint.Align.CENTER, true);

        // A dimmed-but-solid colour rather than translucent GRID: GRID is a paper tone
        // and only reaches 2.1:1 on a panel, which is not a "resting" affordance so much
        // as an invisible one.
        int text = active ? Theme.CREAM : Draw.blend(Theme.PANEL, Theme.CREAM, .55f);
        float textLeft = left + width + Theme.scale(10);
        draw.text(canvas, label, textLeft, centreY + draw.capCentreOffset(labelSize),
                fitRail(label, right - textLeft, screenHeight), text, Paint.Align.LEFT,
                false);
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

    private static final String[] NUMBER_WORDS = {
            "no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen", "twenty"
    };

    private static String word(int value) {
        return value >= 0 && value < NUMBER_WORDS.length ? NUMBER_WORDS[value]
                : Integer.toString(value);
    }

    private static String capitalise(String value) {
        return value.isEmpty() ? value
                : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * A quiet closing note in whatever space a tall board leaves beside it: where the pair
     * are up to, and a row of pips for the rest.
     *
     * <p>In the story book every chapter gets a pip, all eighteen of them, wrapping onto a
     * second row rather than shrinking below the point of being seen — the same complete
     * row {@code WinScene.drawChapters} draws, because "chapter 10 of 18" is only a fact
     * if the eight that are left are on screen too. The old strip capped at ten and filled
     * up for good half way through the book.
     */
    private void drawTail(Canvas canvas, float screenHeight, float left, float top,
                          float right, float bottom, GameState game, UiState ui) {
        draw.roundRect(canvas, left, top, right, top + Math.max(1f, Theme.scale(1.4f)),
                Theme.scale(1), Draw.withAlpha(Theme.CREAM, 40));

        float y = top + Theme.scale(18);
        float label = labelSize();
        draw.text(canvas, tailLabel(game), left, y + label * 1.28f, label,
                Theme.secondaryText(ui.highContrastOn), Paint.Align.LEFT, true);
        y += label * 1.9f;

        // Two lines are plenty for "Eight chapters still to come", and a narrow column is
        // exactly where a sentence is expected to turn rather than shrink.
        float size = railText(screenHeight);
        for (String line : wrap(tailLine(game), size, right - left, 2)) {
            draw.text(canvas, line, left, y + size, size, Theme.CREAM, Paint.Align.LEFT,
                    false);
            y += size * 1.24f;
        }
        y += Theme.scale(10);

        int total = game.storyMode ? Math.max(1, PuzzleLibrary.count())
                // Always a strip of at least five outside the book, so an empty evening
                // reads as room to fill rather than as one lonely outline.
                : Math.min(10, Math.max(5, picturesToShow(game)));
        int made = game.storyMode ? Math.min(total, game.storyFurthest + 1)
                : picturesToShow(game);
        int current = game.storyMode ? game.storyIndex : -1;

        int perRow = pipsPerRow(right - left, total);
        float pitch = Math.min(Theme.scale(PIP_MAX_PITCH), (right - left) / perRow);
        for (int i = 0; i < total; i++) {
            float cx = left + pitch * (i % perRow + .5f);
            float cy = y + pitch * (i / perRow + .5f);
            if (cy + pitch * .5f > bottom) {
                break;
            }
            if (i == current) {
                draw.heart(canvas, cx, cy, pitch * .62f, Theme.GOLD);
            } else if (i < made) {
                draw.heart(canvas, cx, cy, pitch * .58f, Theme.PINK);
            } else {
                draw.circle(canvas, cx, cy, pitch * .11f,
                        Draw.withAlpha(Theme.CREAM, 90));
            }
        }
    }

    /** How many pips fit one row before they stop being worth drawing. */
    private int pipsPerRow(float room, int total) {
        int most = Math.max(1, (int) (room / Theme.scale(PIP_MIN_PITCH)));
        if (total <= most) {
            return total;
        }
        // Split evenly rather than filling one row and orphaning the rest.
        int rows = (total + most - 1) / most;
        return (total + rows - 1) / rows;
    }

    // ---- Message ribbon --------------------------------------------------------------

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
     * <p>Now: bottom edge on the safe line (see {@link #ribbonInset}), centred on the paper
     * card, never wider than that card, wrapping to a second line instead of growing.
     * {@link BoardLayout#footerHeight} reserves the two-line worst case, so the board's
     * height never changes when a long message arrives.
     */
    private void drawToast(Canvas canvas, float screenHeight, BoardLayout board,
                           UiState ui, long now) {
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
        float widest = board.cardRight() - board.cardLeft();
        float size = ribbonTextSize();
        String[] lines = wrap(ui.toast, size, widest - pad * 2, RIBBON_MAX_LINES);

        float text = 0;
        for (String line : lines) {
            text = Math.max(text, draw.measure(line, size, true));
        }
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
        for (int line = 0; line < lines.length; line++) {
            draw.text(canvas, lines[line], centre, first + pitch * line, size, ink,
                    Paint.Align.CENTER, true);
        }
    }

    /**
     * Breaks a message into at most {@code maxLines} lines that each fit {@code room}. A
     * word longer than the whole line is left to overhang rather than being chopped
     * mid-word — no message the game writes is anywhere near that long, and a clipped word
     * reads as a bug where a slightly wide one reads as a long word.
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
            if (draw.measure(candidate, size, true) <= room
                    || count == lines.length - 1) {
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

    private float fit(String text, float room, float preferred, boolean strong) {
        return fit(text, room, preferred, preferred * .7f, strong);
    }

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
