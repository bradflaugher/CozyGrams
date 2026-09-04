package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * The title screen: the invitation to sit down and play.
 *
 * <h2>Laid out from measurements, not from height fractions</h2>
 *
 * <p>Every position on this screen used to be a hand-tuned fraction of the height — nine of
 * them — and every string was drawn from an edge with no idea how wide it was. That is not
 * a matter of taste: in ordinary story mode the label "START THE STORY AGAIN" and the value
 * "you're on chapter 10" overlapped by 145 px of glyphs, both illegible; the
 * returning-player greeting ran 500 px outside the panel; and turning LARGER TEXT on broke
 * two more rows. Meanwhile the panel filled the whole safe rect with the control hint's
 * descender 7 px from its inner edge, so the composition was crowded along the bottom and
 * empty across the top.
 *
 * <p>So the screen is laid out the way a page is: a header measured downward from the top
 * margin, a footer measured upward from the bottom one, and the rows given what is left.
 * Nothing is placed by eye, and every label and value is fitted to the room it actually has
 * — see {@link #drawRow}.
 *
 * <h2>Two type steps apart</h2>
 *
 * <p>A row's label and its value are adjacent levels of one hierarchy, so they are set two
 * steps apart rather than one: {@link Theme#SUBHEAD} over {@link Theme#CAPTION}, 48 px over
 * 36 px at 1080p, a ratio of 1.33. {@code BODY} over {@code CAPTION} — what this screen
 * used — is 40.5 over 36, cap heights two arcminutes apart, which nobody resolves from a
 * sofa; the hierarchy was resting entirely on bold-versus-regular and on colour. See the
 * note in {@link Theme}.
 *
 * <p>One row is deliberately not at that size. START THE STORY AGAIN is the way out, not a
 * fifth thing to do, so it is set at {@code CAPTION} in the quiet colour with a group break
 * above it — the same air the cozy corner puts between its groups. It still takes the same
 * pill when it is focused, because it is still a row.
 */
public final class HomeScene {

    public static final int ITEM_STORY = 0;
    public static final int ITEM_SIZE = 1;
    public static final int ITEM_SETTINGS = 2;
    public static final int ITEM_COUNT = 3;

    /**
     * The returning-player greeting, e.g. "Back after 3 days — 12 pictures finished
     * together". {@link SaveStore} knows the answer and this scene never sees a
     * {@code SaveStore}, so the line is handed in from outside exactly once, the way
     * {@link Comfort} hands the comfort options to the drawing code.
     *
     * <p>{@code CozyGameView} sets it from {@code store.welcomeBack(...)} once the save is
     * loaded. It has to be wall-clock time rather than the view's {@code now()}:
     * {@code welcomeBack} subtracts the stored {@code previousVisitAt}, which
     * {@code SaveStore} writes from {@code System.currentTimeMillis()}, so an uptime clock
     * would produce a negative gap and the line would read "Welcome back" for ever.
     *
     * <p>When it is set it takes the tagline's place rather than being stacked under it.
     * They are the same kind of line in the same voice, and printing both put two pieces of
     * small print under the title — the second of which, at 81 characters, was the widest
     * thing on the screen by 500 px.
     */
    private static String welcome = "";

    /**
     * How long a "press again to be sure" stays armed. Long enough to read the question,
     * short enough that it cannot be answered by accident minutes later.
     */
    private static final long CONFIRM_WINDOW_MS = 6000;

    /** When the destructive row was armed, or 0 when it is not armed. */
    private static long restartArmedAt;

    /**
     * Which player asked the question, or -1 when nobody in particular did.
     *
     * <p>There are two controllers in the room and both are being pressed, so "press again
     * to be sure" has to mean the <em>same</em> pair of hands. Otherwise Rose arms the row,
     * Sky presses the button they were already pressing, and an evening's work is gone
     * without either of them having agreed to it.
     */
    private static int restartArmedBy = -1;

    /** The size the next endless picture will use, when it differs from the current one. */
    private static int pendingSize;

    /**
     * The chapter the story-book stepper is showing, or {@code -1} to follow
     * {@link GameState#storyIndex}. Zero is a real chapter, so it cannot be the unset
     * sentinel the way {@link #pendingSize} uses 0.
     */
    private static int pendingChapter = -1;

    /** True while START THE STORY AGAIN is waiting for a second, deliberate press. */
    public static boolean restartArmed() {
        return restartArmedAt != 0;
    }

    /** Arms the question without recording who asked it: anyone may then answer. */
    public static void armRestart(long now) {
        armRestart(now, -1);
    }

    public static void armRestart(long now, int player) {
        restartArmedAt = now == 0 ? 1 : now;
        restartArmedBy = player;
    }

    public static void disarmRestart() {
        restartArmedAt = 0;
        restartArmedBy = -1;
    }

    /** The player the question is waiting on, or -1 when anyone may answer it. */
    public static int restartArmedBy() {
        return restartArmedBy;
    }

    /**
     * True when this player's press may answer the question — either because they asked it
     * or because nobody in particular did. Ask this before acting on a second press.
     */
    public static boolean mayConfirmRestart(int player) {
        return restartArmedBy < 0 || restartArmedBy == player;
    }

    /** Lets the arming lapse once the question has been on screen long enough. */
    static void expireRestart(long now) {
        if (restartArmedAt != 0 && now - restartArmedAt > CONFIRM_WINDOW_MS) {
            disarmRestart();
        }
    }

    /** Records a board size chosen while a picture was in progress. 0 clears it. */
    public static void setPendingSize(int size) {
        pendingSize = size;
    }

    /** The size the stepper is showing, or 0 when it is following the board. */
    public static int pendingSize() {
        return pendingSize;
    }

    /**
     * The chapter the story-book stepper is showing, or {@code -1} to follow the board.
     */
    public static void setPendingChapter(int chapter) {
        pendingChapter = chapter < 0 ? -1 : chapter;
    }

    public static int pendingChapter() {
        return pendingChapter;
    }

    /**
     * The furthest chapter the stepper will offer: every chapter, from the jump.
     *
     * <p>The book is short and sitting on the sofa. Locking later evenings until earlier
     * ones are finished made the row look broken — left and right did nothing on chapter
     * one — so the whole contents are on the table.
     */
    public static int furthestPlayable(GameState game) {
        return Math.max(0, PuzzleLibrary.count() - 1);
    }

    /** The chapter the story-book row is currently offering. */
    public static int chapterToShow(GameState game) {
        int unlocked = furthestPlayable(game);
        int from = pendingChapter >= 0 ? pendingChapter : game.storyIndex;
        return Math.max(0, Math.min(unlocked, from));
    }

    /**
     * Steps the story-book picker by {@code delta} chapters, wrapping inside the
     * unlocked range, and remembers the choice.
     */
    public static int browseChapter(GameState game, int delta) {
        int unlocked = furthestPlayable(game);
        int next = Math.floorMod(chapterToShow(game) + delta, unlocked + 1);
        pendingChapter = next;
        return next;
    }

    /**
     * The size a fresh endless picture would use: whatever the pair last chose, or the
     * board in front of them snapped onto the ladder endless play actually deals.
     *
     * <p>The chapters are drawn at 5, 7, 10, 12, 15 and 20 squares while endless steps in
     * fives, so a stepper seeded straight from a 12x12 chapter would walk 17, 22 — sizes
     * the row has never offered. The rule lives here, next to the row that prints it, so
     * that what the row says and what the button does cannot disagree; {@code CozyGameView}
     * carries a private copy of it, which is one copy too many.
     */
    public static int endlessSize(GameState game) {
        int from = pendingSize > 0 ? pendingSize : game.size;
        int steps = Math.round((from - GameState.MIN_SIZE) / 5f);
        int rungs = (GameState.MAX_SIZE - GameState.MIN_SIZE) / 5;
        return GameState.MIN_SIZE + Math.max(0, Math.min(rungs, steps)) * 5;
    }

    /** The name of the confirm button on whatever is actually in the player's hands. */
    public static String confirmName() {
        return HudScene.remoteOnly() ? "OK" : "A";
    }

    /** Sets the returning-player line. See the notes on the field for the call site. */
    public static void setWelcome(String line) {
        welcome = line == null ? "" : line.trim();
    }

    /** The returning-player line, or an empty string when nobody has set one. */
    public static String welcome() {
        return welcome;
    }

    /**
     * Labels for each row, in menu order.
     *
     * <p>The size row is named after what it does from where the player is standing. In
     * endless play it changes the board. In story mode the only thing a board size can
     * apply to is a fresh endless picture, so the row <em>is</em> the way out of the book —
     * which the story had no other exit from, in a menu that offered none.
     *
     * <p>"When" belongs in the label too. The chosen size takes effect on the <em>next</em>
     * picture rather than the one on the table, and that used to be said by appending the
     * word "next" to the value: "‹ 20 × 20 › next", three ideas in one run, where the eye
     * cannot tell whether the last word is a button, a hint or part of the number. Every
     * other row on this menu is a clean label/value pair, so the timing moves to the side
     * that is made of words and the value goes back to being a stepper and nothing else.
     */
    public static String[] items(GameState game) {
        return new String[]{
                "Story Book",
                "Endless",
                "Cozy Corner"
        };
    }

    /**
     * Secondary text shown to the right of each row.
     *
     * <p>Kept short on purpose. A value sets at {@link Theme#CAPTION}, which
     * {@link Theme#MIN_PROSE_SP} pins to the 18-arcminute prose floor, so it is the one
     * thing on a row that <em>cannot</em> be made smaller to fit — and "you're on chapter
     * 10" is 327 px of a 746 px lane beside a 462 px label. The measuring guard in
     * {@link #drawRow} is the backstop, not the plan.
     */
    public static String[] values(GameState game) {
        int chapter = chapterToShow(game);
        int next = endlessSize(game);
        return new String[]{
                "‹  " + (chapter + 1) + " / " + PuzzleLibrary.count() + "  ›",
                "‹  " + next + " × " + next + "  ›",
                "Make the room yours"
        };
    }

    /**
     * True when a row's value is the control rather than a remark about it.
     *
     * <p>The size row's value is the stepper, and its chevrons are the only thing on the
     * screen saying that a row can be adjusted in place, so it is set at the label's size
     * in the label's colour and the fitting guard may never drop it. Every other value is
     * commentary, and commentary is what gives way when a row runs out of room.
     */
    private static boolean valueIsControl(int item) {
        return item == ITEM_SIZE || item == ITEM_STORY;
    }

    // ---- The frame the two menus share ----------------------------------------------

    /**
     * The frame the title screen and the cozy corner are both drawn in.
     *
     * <p>They are one flow and were two designs. The panel was {@code .27–.73} here and
     * {@code .255–.745} there, so opening the settings made the frame jump 57 px wider — on
     * a television, mid-transition, that reads as the screen having been rebuilt rather than
     * opened. The rows were inset differently, and the resting row surface was a warm rose
     * card here and, there, the flat cream wash this file's own comment had already rejected
     * as "the colour of a filing cabinet".
     *
     * <p><b>Half adopted.</b> The title screen is drawn entirely through this; the cozy
     * corner has taken the shared colours and the shared type step but not the shared
     * geometry, so the panel still steps 38 px wider when it opens. Two substitutions in
     * {@code SettingsScene} close it, and neither changes that screen by more than a couple
     * of pixels:
     *
     * <ul>
     *   <li>{@code draw.panel(canvas, width * .255f, …, width * .745f, …, bold ? 246 : 235)}
     *       becomes {@code frame.panel(canvas, width, height, bold)};</li>
     *   <li>{@code left = width * .285f} / {@code right = width * .715f} become
     *       {@link #rowLeft(float)} and {@link #rowRight(float)} — 826 px of row becoming
     *       824.</li>
     * </ul>
     *
     * <p>The third has landed: that screen's label size was {@code Theme.textSize(17)} and
     * is now {@code Theme.textSize(Theme.CAPTION)}, which is the same number — 17 is under
     * the prose floor and was already being clamped — said out loud.
     *
     * <p>The row <em>surface</em> is the one thing both screens still spell out for
     * themselves, and neither is wrong to: {@link Draw#menuRow} bakes in {@link Theme#PINK},
     * and a row that is asking a question has to be able to turn {@link Theme#CAUTION}. An
     * accent parameter on {@code menuRow} would let both delete their copy — this screen
     * repaints the fill over the shared pill, the cozy corner draws its own.
     */
    public static final class MenuFrame {

        /**
         * Padding between a row's edge and the text inside it, in design pixels. With
         * {@link Theme#MENU_INSET} outside it that leaves a 746 px lane inside the 902 px
         * panel at 1080p, which is the width every string on both menus is measured
         * against.
         */
        private static final float ROW_PAD = 26f;

        /** A row's half-height, as a fraction of the label size it has to hold. */
        private static final float ROW_HALF_EM = .86f;

        /** How opaque the panel is, and how opaque it becomes with extra contrast on. */
        private static final int PANEL_ALPHA = 230;
        private static final int PANEL_ALPHA_BOLD = 244;

        private final Draw draw;

        public MenuFrame(Draw draw) {
            this.draw = draw;
        }

        public float left(float width) {
            return width * Theme.MENU_PANEL_LEFT;
        }

        public float right(float width) {
            return width * Theme.MENU_PANEL_RIGHT;
        }

        public float top(float height) {
            return height * Theme.SAFE_AREA;
        }

        public float bottom(float height) {
            return height * (1 - Theme.SAFE_AREA);
        }

        /** How far a row is inset from the panel's own edge. */
        public float inset() {
            return Theme.scale(Theme.MENU_INSET);
        }

        public float rowLeft(float width) {
            return left(width) + inset();
        }

        public float rowRight(float width) {
            return right(width) - inset();
        }

        public float rowPad() {
            return Theme.scale(ROW_PAD);
        }

        /** Half the height a row needs in order to hold {@code labelSize} comfortably. */
        public float rowHalf(float labelSize) {
            return labelSize * ROW_HALF_EM;
        }

        public void panel(Canvas canvas, float width, float height, boolean highContrast) {
            draw.panel(canvas, left(width), top(height), right(width), bottom(height),
                    highContrast ? PANEL_ALPHA_BOLD : PANEL_ALPHA);
        }

        /**
         * One row's surface, centred on {@code centreY}. {@code beat} is the caller's
         * pulse, 0 to 1 — hand it a constant when {@link Comfort#calmMotion} is on.
         */
        public void row(Canvas canvas, float left, float centreY, float right, float half,
                        boolean focused, float beat) {
            draw.menuRow(canvas, left, centreY - half, right, centreY + half, half * .7f,
                    focused, beat);
        }

        /** The focus pulse both menus breathe at, steady when movement is unwelcome. */
        public static float beat(long now) {
            return Comfort.get().calmMotion ? .55f
                    : (float) Math.abs(Math.sin(now / 900f));
        }
    }

    // ---- Layout ----------------------------------------------------------------------

    /** A group break is worth this much of a row, exactly as in the cozy corner. */
    private static final float GROUP_GAP = .34f;

    /**
     * The margin above the heart and below the last line, in design pixels.
     *
     * <p>Smaller than {@link Theme#MENU_INSET}, and for a reason: the sides of a panel are
     * inset far enough to clear its 30 px corner radius, while the top and bottom edges are
     * straight where this screen's content is — everything in the header and the footer is
     * centred. 24 px at 1080p is a margin you can see without spending the room the rows
     * need.
     */
    private static final float MARGIN_Y = 16f;

    /** Air between the header, the row band and the footer, in design pixels. */
    private static final float BAND_GAP = 20f;

    /** The heart above the wordmark, in design pixels. */
    private static final float HEART_SIZE = 42f;

    /** How much of an em of extra space the wordmark's letters are given. */
    private static final float TITLE_TRACKING = .055f;

    /**
     * Sans-serif cap height as a fraction of the text size, and how far below its own
     * baseline a line of text reaches before the next one may start.
     *
     * <p>The layout stacks blocks, so it needs to know how tall a line is rather than where
     * its baseline goes. .70 is the figure the type scale in {@link Theme} is derived from,
     * so the two agree by construction.
     */
    private static final float CAP_HEIGHT = .70f;
    private static final float DESCENT = .18f;

    private final Draw draw;
    private final MenuFrame frame;

    /** Where everything goes this frame, worked out in {@link #layOut}. */
    private final float[] rowCentre = new float[ITEM_COUNT];
    private final String[] greetingLines = new String[2];
    private float rowHalf;
    private float rowPitch;
    private float heartCentre;
    private float titleBaseline;
    private float ruleTop;
    private float lineBaseline;
    private float chipCentre;
    private float hintBaseline;

    /**
     * Where the focus pill actually is, as opposed to which row is selected.
     *
     * <p>The focus used to be drawn straight from {@code i == ui.menu}, a boolean, so
     * across six steps of the D-pad the pill teleported six times — on the first screen of
     * the game, the one frame everybody sees. It now has a position of its own that chases
     * the selected row, and an arrival to land from.
     *
     * <p>The state lives here rather than on {@link UiState} because it is a fact about
     * what has been drawn rather than about what the game is — the same reason
     * {@code Renderer} keeps its own {@code lastPuzzle}. {@code Renderer} builds one
     * {@code HomeScene} and keeps it, so these survive between frames.
     */
    private float pillCentre;
    private float pillHalf;
    private int pillRow = -1;
    private long pillArrivedAt;
    private long lastFrameAt;

    public HomeScene(Draw draw) {
        this.draw = draw;
        this.frame = new MenuFrame(draw);
    }

    /** The frame this screen is drawn in, for the other menu that shares it. */
    public MenuFrame frame() {
        return frame;
    }

    public void draw(Canvas canvas, float width, float height, GameState game, UiState ui,
                     Effects effects, long now) {
        drawLanding(canvas, width, height, game, ui, now);
        // Explicitly boardless. The two-argument form means "confine to whatever card I
        // last saw", and this scene has no card: after a game has been drawn and Back
        // pressed, a particle still in the air would have been trimmed and candle-tinted
        // against a paper card that is no longer on screen.
        effects.drawParticles(canvas, draw, (BoardLayout) null, now);
    }

    // ---- The landing page ------------------------------------------------------------

    /**
     * A two-card landing page: identity and invitation on the left, choices on the right.
     * The old home screen put a logo, three equal rows, presence and instructions into one
     * tall rectangle. Everything was aligned, but nothing was composed. Separating the
     * emotional promise from the controls makes the first frame read like a finished game
     * rather than a settings form laid over a lovely painting.
     */
    private void drawLanding(Canvas canvas, float width, float height, GameState game,
                             UiState ui, long now) {
        float safeX = width * .075f;
        float top = height * .105f;
        float bottom = height * .895f;
        float gap = Theme.scale(22);
        float centre = width * .5f;
        float brandLeft = safeX;
        float brandRight = centre - gap / 2;
        float menuLeft = centre + gap / 2;
        float menuRight = width - safeX;
        int panelAlpha = ui.highContrastOn ? 246 : 228;

        draw.panel(canvas, brandLeft, top, brandRight, bottom, panelAlpha);
        draw.panel(canvas, menuLeft, top, menuRight, bottom, panelAlpha);

        int selected = Math.floorMod(ui.menu, ITEM_COUNT);
        drawLandingBrand(canvas, brandLeft, top, brandRight, bottom, game, ui, selected,
                now);
        drawLandingMenu(canvas, menuLeft, top, menuRight, bottom, game, ui, selected, now);
    }

    private void drawLandingBrand(Canvas canvas, float left, float top, float right,
                                  float bottom, GameState game, UiState ui, int selected,
                                  long now) {
        float cx = (left + right) / 2;
        float pad = Theme.scale(34);
        float lane = right - left - pad * 2;

        float beat = Comfort.get().calmMotion ? .5f
                : (float) Math.abs(Math.sin(now / 1700f));
        float heartRest = Theme.scale(38);
        float heart = heartRest * (.95f + beat * .10f);
        float heartY = top + Theme.scale(48);
        draw.heart(canvas, cx, heartY, heart, Theme.PINK);
        draw.heart(canvas, cx - heart * .07f, heartY - heart * .07f, heart * .80f,
                Draw.withAlpha(Draw.blend(Theme.PINK, Theme.CREAM, .35f), 115));

        float titleSize = draw.fit("COZYGRAMS", landingText(Theme.TITLE), lane, true,
                landingText(Theme.HEADING));
        // The heart may breathe; the page must not. Keep every text baseline anchored to
        // its resting size so the decorative pulse never makes the whole card bob.
        float titleY = heartY + heartRest * .65f + Theme.scale(18)
                + titleSize * CAP_HEIGHT;
        draw.tracked(canvas, "COZYGRAMS", cx, titleY, titleSize, Theme.CREAM,
                TITLE_TRACKING, true);

        float ruleY = titleY + titleSize * DESCENT + Theme.scale(12);
        draw.roundRect(canvas, cx - Theme.scale(54), ruleY, cx + Theme.scale(54),
                ruleY + Theme.scale(3), Theme.scale(2), Draw.withAlpha(Theme.PINK, 190));

        float greetingSize = landingText(Theme.CAPTION);
        String greeting = welcome.isEmpty() ? "Puzzles are better together" : welcome;
        int greetingCount = wrapGreeting(greeting, greetingSize, lane);
        float greetingY = ruleY + Theme.scale(18) + greetingSize * CAP_HEIGHT;
        for (int i = 0; i < greetingCount; i++) {
            draw.text(canvas, greetingLines[i], cx, greetingY, greetingSize,
                    welcome.isEmpty() ? Comfort.skyColor() : Theme.GOLD,
                    Paint.Align.CENTER, false);
            greetingY += greetingSize * (CAP_HEIGHT + DESCENT);
        }

        float detailTop = Math.max(top + Theme.scale(250), greetingY + Theme.scale(34));
        draw.roundRect(canvas, left + pad, detailTop, right - pad,
                bottom - Theme.scale(105), Theme.scale(Theme.RADIUS_CARD),
                Draw.withAlpha(Theme.NIGHT, ui.highContrastOn ? 205 : 145));
        draw.roundRectStroke(canvas, left + pad, detailTop, right - pad,
                bottom - Theme.scale(105), Theme.scale(Theme.RADIUS_CARD), Theme.keyline(),
                Draw.withAlpha(Theme.GOLD, 95));

        float textLeft = left + pad + Theme.scale(28);
        float detailLane = right - pad - Theme.scale(28) - textLeft;
        float eyebrowSize = landingText(Theme.CAPTION);
        float eyebrowY = detailTop + Theme.scale(30) + eyebrowSize * CAP_HEIGHT;
        draw.text(canvas, landingEyebrow(selected), textLeft, eyebrowY, eyebrowSize,
                Theme.GOLD, Paint.Align.LEFT, true);

        String feature = landingFeature(game, selected);
        float featureSize = draw.fit(feature, landingText(Theme.HEADING), detailLane,
                true, landingText(Theme.SUBHEAD));
        float featureY = eyebrowY + Theme.scale(18) + featureSize * CAP_HEIGHT;
        draw.text(canvas, feature, textLeft, featureY, featureSize, Theme.CREAM,
                Paint.Align.LEFT, true);

        float metaSize = landingText(Theme.CAPTION);
        float metaY = featureY + featureSize * DESCENT + Theme.scale(18)
                + metaSize * CAP_HEIGHT;
        draw.text(canvas, landingMeta(game, selected), textLeft, metaY, metaSize,
                Theme.secondaryText(ui.highContrastOn), Paint.Align.LEFT, false);

        if (Theme.textScale() <= 1.2f) {
            float bodySize = landingText(Theme.BODY);
            float bodyY = metaY + metaSize * DESCENT + Theme.scale(26)
                    + bodySize * CAP_HEIGHT;
            float bodyLane = detailLane - Theme.scale(24);
            draw.text(canvas, landingDescription(selected), textLeft, bodyY,
                    draw.fit(landingDescription(selected), bodySize, bodyLane, false,
                            landingText(Theme.CAPTION)), Theme.CREAM, Paint.Align.LEFT, false);
        }

        drawPresence(canvas, left + pad, right - pad, bottom - Theme.scale(53), ui);
    }

    private void drawLandingMenu(Canvas canvas, float left, float top, float right,
                                 float bottom, GameState game, UiState ui, int selected,
                                 long now) {
        float pad = Theme.scale(32);
        float rowLeft = left + pad;
        float rowRight = right - pad;
        float heading = landingText(Theme.SUBHEAD);
        float headingY = top + Theme.scale(42) + heading * CAP_HEIGHT;
        draw.text(canvas, "Choose how to settle in", rowLeft, headingY, heading, Theme.CREAM,
                Paint.Align.LEFT, true);
        float caption = landingText(Theme.CAPTION);
        String invitation = "A story, a puzzle, or a cozier room";
        draw.text(canvas, invitation, rowLeft,
                headingY + Theme.scale(18) + caption * CAP_HEIGHT,
                draw.fit(invitation, caption, rowRight - rowLeft, false,
                        landingText(Theme.MIN_PROSE_SP)),
                Theme.secondaryText(ui.highContrastOn), Paint.Align.LEFT, false);

        float rowsTop = top + Theme.scale(150);
        float footerRoom = Theme.scale(92);
        rowPitch = (bottom - footerRoom - rowsTop) / ITEM_COUNT;
        rowHalf = Math.min(Theme.scale(52), rowPitch * .39f);
        for (int item = 0; item < ITEM_COUNT; item++) {
            rowCentre[item] = rowsTop + rowPitch * (item + .5f);
        }
        follow(selected, rowCentre[selected], now);

        for (int item = 0; item < ITEM_COUNT; item++) {
            drawLandingRowSurface(canvas, rowLeft, rowRight, rowCentre[item], rowHalf,
                    item, selected, ui.highContrastOn, now);
        }
        for (int item = 0; item < ITEM_COUNT; item++) {
            drawLandingRowCopy(canvas, rowLeft, rowRight, rowCentre[item], game, item,
                    selected, ui.highContrastOn);
        }

        drawLandingFooter(canvas, rowLeft, rowRight, bottom - Theme.scale(39), selected,
                ui.highContrastOn);
    }

    private void drawLandingRowSurface(Canvas canvas, float left, float right, float centreY,
                                       float half, int item, int selected,
                                       boolean highContrast, long now) {
        float radius = Theme.scale(Theme.RADIUS_CARD);
        if (item != selected) {
            draw.roundRect(canvas, left, centreY - half, right, centreY + half, radius,
                    Draw.withAlpha(Theme.ROW_REST, highContrast ? 92 : 56));
            draw.roundRectStroke(canvas, left, centreY - half, right, centreY + half,
                    radius, Theme.keyline(), Draw.withAlpha(Theme.CREAM, 50));
            return;
        }
        float landed = Draw.easeOut(landing(now));
        float glow = Theme.scale(7) * (.65f + .35f * landed);
        draw.glow(canvas, left, centreY - half, right, centreY + half, radius,
                glow, Theme.GOLD, 34);
        draw.roundRect(canvas, left, centreY - half, right, centreY + half, radius,
                Theme.GOLD);
        draw.roundRectStroke(canvas, left, centreY - half, right, centreY + half, radius,
                Theme.hairline(), Draw.withAlpha(Theme.CREAM, 220));
    }

    private void drawLandingRowCopy(Canvas canvas, float left, float right, float centreY,
                                    GameState game, int item, int selected,
                                    boolean highContrast) {
        boolean focused = item == selected;
        int primary = focused ? Theme.INK : Theme.CREAM;
        int secondary = focused ? Draw.withAlpha(Theme.INK, 205)
                : Theme.secondaryText(highContrast);
        float x = left + Theme.scale(26);
        float labelSize = draw.fit(items(game)[item], landingText(Theme.SUBHEAD),
                (right - left) * .55f, true, landingText(Theme.BODY));
        float labelY = centreY - Theme.scale(13) + draw.capCentreOffset(labelSize);
        draw.text(canvas, items(game)[item], x, labelY, labelSize, primary,
                Paint.Align.LEFT, true);

        float detailSize = landingText(Theme.CAPTION);
        String detail = rowDetail(game, item);
        float detailY = centreY + Theme.scale(20) + draw.capCentreOffset(detailSize);
        draw.text(canvas, detail, x, detailY, detailSize, secondary,
                Paint.Align.LEFT, false);

        if (item == ITEM_SETTINGS) {
            if (focused) {
                drawLandingConfirm(canvas, right - Theme.scale(24), centreY, "OPEN",
                        primary);
            }
            return;
        }
        String action = values(game)[item];
        float actionSize = draw.fit(action, landingText(Theme.BODY),
                (right - left) * .42f, false, landingText(Theme.CAPTION));
        draw.text(canvas, action, right - Theme.scale(24),
                centreY + draw.capCentreOffset(actionSize), actionSize, primary,
                Paint.Align.RIGHT, false);
    }

    private void drawLandingConfirm(Canvas canvas, float right, float centreY,
                                    String label, int textColor) {
        float size = landingText(Theme.BODY);
        float labelWidth = draw.measure(label, size, true);
        float gap = Theme.scale(9);
        float chipHeight = size * 1.05f;
        String key = confirmName();
        float chipWidth = draw.keycapWidth(key, chipHeight);
        float chipRight = right - labelWidth - gap;
        int chipColor = HudScene.remoteOnly() ? Theme.INK : Theme.BUTTON_A;

        draw.keycap(canvas, chipRight - chipWidth, centreY, chipHeight, key, chipColor);
        draw.text(canvas, label, right, centreY + draw.capCentreOffset(size), size,
                textColor, Paint.Align.RIGHT, true);
    }

    private void drawLandingFooter(Canvas canvas, float left, float right, float centreY,
                                   int selected, boolean highContrast) {
        int row = Math.floorMod(selected, ITEM_COUNT);
        String lead = row == ITEM_STORY ? "Pick a chapter"
                : row == ITEM_SIZE ? "Pick a size" : "Move";
        String action = row == ITEM_STORY ? "Open"
                : row == ITEM_SIZE ? "Start" : "Choose";
        String key = confirmName();
        float size = landingText(Theme.CAPTION);
        float gap = Theme.scale(10);
        float chipHeight;
        float chipWidth;
        float total;
        do {
            chipHeight = size * 1.08f;
            chipWidth = draw.keycapWidth(key, chipHeight);
            total = chipHeight + gap + draw.measure(lead, size, false)
                    + gap * 2 + chipWidth + gap + draw.measure(action, size, false);
            if (total <= right - left || size <= Theme.scale(20)) break;
            size = Math.max(Theme.scale(20), size * (right - left) / total);
        } while (true);

        int text = Theme.secondaryText(highContrast);
        float x = (left + right - total) / 2;
        draw.dpadKeycap(canvas, x, centreY, chipHeight, Theme.SOFT_TEXT);
        x += chipHeight + gap;
        draw.text(canvas, lead, x, centreY + draw.capCentreOffset(size), size, text,
                Paint.Align.LEFT, false);
        x += draw.measure(lead, size, false) + gap * 2;
        int chipColor = HudScene.remoteOnly() ? Theme.SOFT_TEXT : Theme.BUTTON_A;
        draw.keycap(canvas, x, centreY, chipHeight, key, chipColor);
        x += chipWidth + gap;
        draw.text(canvas, action, x, centreY + draw.capCentreOffset(size), size, text,
                Paint.Align.LEFT, false);
    }

    private void drawPresence(Canvas canvas, float left, float right, float centreY,
                              UiState ui) {
        boolean together = ui.joined[1];
        String line = together ? "Rose and Sky are ready  ♥"
                : "Sky can join any time — press a button";
        int accent = together ? Theme.PINK : Comfort.skyColor();
        float size = draw.fit(line, landingText(Theme.CAPTION), right - left
                - Theme.scale(34), together, landingText(Theme.MIN_PROSE_SP));
        float half = size * .95f;
        draw.roundRect(canvas, left, centreY - half, right, centreY + half, half,
                Draw.withAlpha(accent, together ? 48 : 28));
        draw.roundRectStroke(canvas, left, centreY - half, right, centreY + half, half,
                Theme.keyline(), Draw.withAlpha(accent, 135));
        draw.text(canvas, line, (left + right) / 2,
                centreY + draw.capCentreOffset(size), size,
                together ? Theme.PINK_LIGHT : Theme.CREAM, Paint.Align.CENTER, together);
    }

    static String landingEyebrow(int item) {
        switch (Math.floorMod(item, ITEM_COUNT)) {
            case ITEM_STORY: return "TONIGHT'S STORY";
            case ITEM_SIZE: return "A FRESH CANVAS";
            default: return "MAKE IT YOURS";
        }
    }

    static String landingFeature(GameState game, int item) {
        switch (Math.floorMod(item, ITEM_COUNT)) {
            case ITEM_STORY: return PuzzleLibrary.name(chapterToShow(game));
            case ITEM_SIZE:
                int size = endlessSize(game);
                return size + " × " + size + " picture";
            default: return "Cozy Corner";
        }
    }

    static String landingMeta(GameState game, int item) {
        switch (Math.floorMod(item, ITEM_COUNT)) {
            case ITEM_STORY:
                return "Chapter " + (chapterToShow(game) + 1) + " of "
                        + PuzzleLibrary.count();
            case ITEM_SIZE: return "Endless · a new picture every time";
            default: return "Sound · hints · colors · comfort";
        }
    }

    static String landingDescription(int item) {
        switch (Math.floorMod(item, ITEM_COUNT)) {
            case ITEM_STORY: return "Open the book and settle in.";
            case ITEM_SIZE: return "Pick a size, then make it yours.";
            default: return "Tune the room until it feels just right.";
        }
    }

    static String rowDetail(GameState game, int item) {
        switch (Math.floorMod(item, ITEM_COUNT)) {
            case ITEM_STORY: return PuzzleLibrary.name(chapterToShow(game));
            case ITEM_SIZE: return "A new cozy picture";
            default: return "Sound, hints, and comfort";
        }
    }

    /**
     * The landing page has fixed-height, two-line choices. Let the comfort option make
     * them appreciably larger, but stop before 150% text turns three tidy cards into six
     * colliding lines. Long copy still goes through {@link Draw#fit} as a second guard.
     */
    private static float landingText(float designPixels) {
        return Theme.scale(Math.max(Theme.MIN_PROSE_SP, designPixels))
                * Math.min(Theme.textScale(), 1.2f);
    }

    /**
     * Where everything goes: the header measured down from the top margin, the footer up
     * from the bottom one, and the rows sharing what is left.
     *
     * <p>Worked out per frame rather than stored, because it depends on the type scale and
     * LARGER TEXT can change that between one frame and the next. It writes into fields
     * rather than returning an object, so a menu redrawing at 60 Hz allocates nothing.
     */
    private void layOut(float width, float height) {
        float top = frame.top(height) + Theme.scale(MARGIN_Y);
        float bottom = frame.bottom(height) - Theme.scale(MARGIN_Y);
        float lane = frame.rowRight(width) - frame.rowLeft(width);

        float headerBottom = layOutHeader(top, lane);
        float footerTop = layOutFooter(bottom);

        float band = (footerTop - Theme.scale(BAND_GAP))
                - (headerBottom + Theme.scale(BAND_GAP));
        rowPitch = band / (ITEM_COUNT + GROUP_GAP);
        rowHalf = Math.min(frame.rowHalf(Theme.textSize(Theme.SUBHEAD)), rowPitch * .42f);

        float y = headerBottom + Theme.scale(BAND_GAP);
        for (int item = 0; item < ITEM_COUNT; item++) {
            rowCentre[item] = y + rowPitch * .5f;
            y += rowPitch;
            if (item == ITEM_SETTINGS - 1) {
                y += rowPitch * GROUP_GAP;
            }
        }
    }

    /**
     * The header block, laid out downward, and the y its last line reaches.
     *
     * <p>The greeting is wrapped to two lines when it needs them. It is the one string on
     * this screen the game does not choose: {@code SaveStore} can hand over 81 characters,
     * which is 1318 px at the prose floor against a lane of 824, and a floor is a floor —
     * so it cannot be answered by setting the type smaller.
     */
    private float layOutHeader(float top, float lane) {
        float heart = Theme.scale(HEART_SIZE);
        heartCentre = top + heart * .5f;

        float titleSize = Theme.textSize(Theme.TITLE);
        titleBaseline = top + heart + Theme.scale(14) + titleSize * CAP_HEIGHT;
        ruleTop = titleBaseline + titleSize * DESCENT + Theme.scale(12);

        float lineSize = Theme.textSize(Theme.CAPTION);
        lineBaseline = ruleTop + Theme.scale(3) + Theme.scale(16) + lineSize * CAP_HEIGHT;
        int lines = wrapGreeting(welcome.isEmpty() ? "Puzzles are better together" : welcome,
                lineSize, lane);
        return lineBaseline + lineSize * DESCENT
                + (lines - 1) * lineSize * (CAP_HEIGHT + DESCENT);
    }

    /**
     * The footer, laid out upward from the bottom margin: the who-is-here chip, and the
     * control hint under it.
     *
     * @return the top of the block, so the rows know where they have to stop.
     */
    private float layOutFooter(float bottom) {
        float size = Theme.textSize(Theme.CAPTION);
        hintBaseline = bottom - size * DESCENT;
        float chipHalf = size * .95f;
        chipCentre = hintBaseline - size * CAP_HEIGHT - Theme.scale(18) - chipHalf;
        return chipCentre - chipHalf;
    }

    /**
     * Breaks a line into at most two that each fit {@code maxWidth}, into
     * {@link #greetingLines}, and says how many it used.
     *
     * <p>The em dash is tried first, because it is not punctuation the game chose at random:
     * {@code SaveStore} builds the greeting as "how long you were away — what you have made
     * together", so breaking there gives two whole thoughts instead of a line ending in
     * "seat" and the next one opening with a dash.
     *
     * <p>Two lines is the whole budget: a third would push the menu down, and every greeting
     * {@code SaveStore} can produce fits in two at both text scales. A word wider than the
     * panel goes out whole rather than being drawn as half a word — there is nothing to
     * break, and a language this game has not met yet should overhang rather than disappear.
     */
    private int wrapGreeting(String line, float size, float maxWidth) {
        greetingLines[0] = "";
        greetingLines[1] = "";
        if (line.isEmpty()) {
            return 0;
        }
        if (draw.measure(line, size, false) <= maxWidth) {
            greetingLines[0] = line;
            return 1;
        }
        int dash = line.lastIndexOf(" — ");
        if (dash > 0 && draw.measure(line.substring(0, dash), size, false) <= maxWidth
                && draw.measure(line.substring(dash + 3), size, false) <= maxWidth) {
            greetingLines[0] = line.substring(0, dash);
            greetingLines[1] = line.substring(dash + 3);
            return 2;
        }
        int used = 0;
        int from = 0;
        while (from < line.length() && used < greetingLines.length) {
            int fit = -1;
            int next = line.indexOf(' ', from);
            while (next >= 0 && draw.measure(line.substring(from, next), size, false)
                    <= maxWidth) {
                fit = next;
                next = line.indexOf(' ', next + 1);
            }
            if (next < 0 && draw.measure(line.substring(from), size, false) <= maxWidth) {
                fit = line.length();
            }
            if (fit < 0) {
                fit = next < 0 ? line.length() : next;
            }
            greetingLines[used++] = line.substring(from, fit);
            from = fit + 1;
        }
        return used;
    }

    // ---- The wordmark ----------------------------------------------------------------

    /**
     * The heart, the wordmark, the rule under it, and the one warm line.
     *
     * <p>This was the least designed element in the game: the system font at default
     * tracking with a flat drop shadow, over a single-colour heart. It is still the system
     * font — no typeface ships with the app and none is going to — but it is letterspaced,
     * it sits in a wash of candlelight instead of on a shadow, and the heart has a highlight
     * on the side the room's lamp is on, so it reads as felt rather than as a glyph.
     */
    private void drawWordmark(Canvas canvas, float width, long now) {
        float cx = width / 2;

        // The heartbeat is the one idle movement on this screen, so it is also the first
        // thing calm motion should switch off.
        float beat = Comfort.get().calmMotion ? .5f
                : (float) Math.abs(Math.sin(now / 1700f));
        float size = Theme.scale(HEART_SIZE) * (.94f + beat * .12f);
        draw.heart(canvas, cx, heartCentre, size, Theme.PINK);
        draw.heart(canvas, cx - size * .07f, heartCentre - size * .07f, size * .80f,
                Draw.withAlpha(Draw.blend(Theme.PINK, Theme.CREAM, .35f), 115));

        float titleSize = Theme.textSize(Theme.TITLE);
        float titleWidth = draw.measure("COZYGRAMS", titleSize, true) * (1 + TITLE_TRACKING);
        // Lamplight behind the letters rather than a shadow under them, spread over enough
        // rings that it has no edge of its own. Kept to 14 alpha at its peak: past about 16
        // a warm glow around type stops reading as a lamp and starts reading as neon, which
        // is the opposite of cozy.
        float capHeight = titleSize * CAP_HEIGHT;
        draw.glow(canvas, cx - titleWidth / 2, titleBaseline - capHeight,
                cx + titleWidth / 2, titleBaseline, capHeight * .5f, Theme.scale(30),
                Theme.GOLD, 14);
        draw.tracked(canvas, "COZYGRAMS", cx, titleBaseline, titleSize, Theme.CREAM,
                TITLE_TRACKING, true);

        float ruleWidth = Theme.scale(64);
        draw.roundRect(canvas, cx - ruleWidth, ruleTop, cx + ruleWidth,
                ruleTop + Theme.scale(3), Theme.scale(2),
                Draw.withAlpha(Theme.PINK, 190));

        float lineSize = Theme.textSize(Theme.CAPTION);
        float y = lineBaseline;
        for (String line : greetingLines) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            draw.text(canvas, line, cx, y, lineSize,
                    welcome.isEmpty() ? Theme.BLUE : Theme.GOLD, Paint.Align.CENTER, false);
            y += lineSize * (CAP_HEIGHT + DESCENT);
        }
    }

    // ---- The rows --------------------------------------------------------------------

    /**
     * The five rows: every surface first, then the travelling focus pill over them, then
     * every label and value on top of that.
     *
     * <p>Three passes rather than one, because the pill is between two rows for most of its
     * journey. Drawn row by row it would slide <em>under</em> the row it is arriving at;
     * drawn after the text it would bury it.
     */
    private void drawRows(Canvas canvas, float width, String[] labels, String[] values,
                          UiState ui, long now) {
        float left = frame.rowLeft(width);
        float right = frame.rowRight(width);
        for (int item = 0; item < ITEM_COUNT; item++) {
            frame.row(canvas, left, rowCentre[item], right, rowHalf, false, 0);
        }
        drawFocus(canvas, left, right, ui, now);
        for (int item = 0; item < ITEM_COUNT; item++) {
            drawRow(canvas, item, labels[item], values[item], left + frame.rowPad(),
                    right - frame.rowPad(), covered(item), ui.highContrastOn, now);
        }
    }

    /**
     * The focus pill, at wherever it has actually got to.
     *
     * <p>It stretches along its travel and settles with a squash, which is the landing the
     * cursors on the board are already given ({@link Theme#CURSOR_LAND_MS}). Those two are
     * the only things in the game that move because a person moved them, so they move
     * alike.
     */
    private void drawFocus(Canvas canvas, float left, float right, UiState ui, long now) {
        int row = Math.floorMod(ui.menu, ITEM_COUNT);
        follow(row, rowCentre[row], now);

        float travel = Math.min(1f,
                Math.abs(rowCentre[row] - pillCentre) / Math.max(1f, rowPitch));
        float landed = Draw.easeOut(landing(now));
        // A little stretch along the travel and no more: the pill is nearly as tall as the
        // gap between two rows, so anything more than this and a moving highlight lies
        // across two labels at once instead of passing between them.
        float stretch = travel * .12f;
        float squash = (1 - landed) * .10f;
        float half = pillHalf * (1 + stretch - squash);
        float bulge = (squash - stretch * .5f) * pillHalf;

        // A brighter halo the instant it arrives, decaying into the resting breath.
        float beat = Math.max(MenuFrame.beat(now), 1 - landed);
        frame.row(canvas, left - bulge, pillCentre, right + bulge, half, true, beat);
    }

    /**
     * Moves the pill toward the selected row over real milliseconds.
     *
     * <p>An exponential in time rather than a fraction per frame, for the reason
     * {@link Theme#MOTION_TAU_MS} gives: a fraction per frame makes the speed a property of
     * the panel the game happens to be running on. A first frame, a changed row height and a
     * long gap between frames all snap, because none of them is a person moving the focus.
     *
     * <p>Calmer Animation snaps too, and that is not only politeness:
     * {@code Renderer.animating} stops asking for frames on a still menu, so a glide would
     * freeze half way between two rows.
     */
    private void follow(int row, float target, long now) {
        long since = now - lastFrameAt;
        lastFrameAt = now;
        boolean jump = pillRow < 0 || pillHalf != rowHalf || Comfort.get().calmMotion
                || since <= 0 || since > 200;
        if (pillRow != row) {
            pillRow = row;
            pillArrivedAt = 0;
        }
        pillHalf = rowHalf;
        if (jump) {
            pillCentre = target;
            settle(now);
            return;
        }
        pillCentre = Draw.lerp(pillCentre, target,
                Draw.approachRate(since, Theme.MOTION_TAU_MS));
        if (pillArrivedAt == 0 && Math.abs(target - pillCentre) < Theme.scale(1)) {
            pillArrivedAt = now;
        }
    }

    /** Marks the pill as having arrived long enough ago to have finished landing. */
    private void settle(long now) {
        pillArrivedAt = now - (long) Theme.CURSOR_LAND_MS - 1;
    }

    /**
     * How far through its arrival the pill is: 0 the moment it lands, 1 once it has
     * settled — and 1 while it is still travelling, which is the only time it has no
     * arrival to be part of the way through.
     */
    private float landing(long now) {
        if (pillArrivedAt == 0) {
            return 1;
        }
        return Draw.clamp01((now - pillArrivedAt) / Theme.CURSOR_LAND_MS);
    }

    /**
     * How much of a row the pill is standing on, 0 to 1 — what decides whether its label is
     * cream on plum or ink on rose.
     *
     * <p>Ramped late and steeply on purpose. A label half way between those two colours is
     * a mid-grey on a rose pill, 1.35:1, which is the one combination on this screen that
     * cannot be read — and the pill spends the middle of its journey lying across two rows
     * at once, so a gentle ramp puts <em>both</em> labels in that grey. Nothing changes
     * until the pill is nearly home, and then it changes over about 25 ms.
     */
    private float covered(int item) {
        float overlap = 1 - Math.abs(rowCentre[item] - pillCentre) / Math.max(1f, rowPitch);
        return Draw.clamp01((overlap - .62f) * 6f);
    }

    /**
     * One row's label and value, each at the largest size that fits beside the other.
     *
     * <p>They were drawn from opposite edges at fixed sizes, which is why an ordinary story
     * mode frame overlapped two strings by 145 px. The rule now:
     *
     * <ol>
     *   <li>the value may take whatever the label does not need at its smallest;</li>
     *   <li>the label takes what is left of the lane, down to the prose floor;</li>
     *   <li>if it still does not fit, the value goes — the label is the control and the
     *       value is a remark about it. Unless the value <em>is</em> the control, in which
     *       case both stay, both at the floor.</li>
     * </ol>
     *
     * <p>Step 2 is what usually gives: {@code CAPTION} is already pinned to
     * {@link Theme#MIN_PROSE_SP} so a value can rarely shrink at all, while a label starts
     * two steps above the floor and has a quarter of its size to spend.
     */
    private void drawRow(Canvas canvas, int item, String label, String value, float left,
                         float right, float coverage, boolean highContrast, long now) {
        float lane = right - left;
        float gap = Theme.scale(28);
        float floor = Theme.textSize(Theme.MIN_PROSE_SP);

        boolean control = valueIsControl(item);
        float labelWanted = Theme.textSize(Theme.SUBHEAD);
        float valueWanted = control ? labelWanted : Theme.textSize(Theme.CAPTION);

        float valueSize = valueWanted;
        float valueWidth = 0;
        if (!value.isEmpty()) {
            valueSize = draw.fit(value, valueWanted,
                    lane - gap - draw.measure(label, floor, true), false, floor);
            valueWidth = draw.measure(value, valueSize, false);
        }
        float labelSize = draw.fit(label, labelWanted,
                lane - (value.isEmpty() ? 0 : gap + valueWidth), true, floor);
        boolean showValue = !value.isEmpty() && (control
                || draw.measure(label, labelSize, true) + gap + valueWidth <= lane);

        int ink = restingInk(item, highContrast, now);
        draw.text(canvas, label, left, rowCentre[item] + draw.capCentreOffset(labelSize),
                labelSize, Draw.blend(ink, Theme.INK, coverage), Paint.Align.LEFT, true);
        if (showValue) {
            int resting = control ? ink : Theme.secondaryText(highContrast);
            draw.text(canvas, value, right,
                    rowCentre[item] + draw.capCentreOffset(valueSize), valueSize,
                    Draw.blend(resting, Theme.INK, coverage), Paint.Align.RIGHT, false);
        }
    }

    /**
     * The colour a row rests in before the pill reaches it.
     *
     * <p>The way out is quiet, and while it is asking a question it is
     * {@link Theme#CAUTION} — a warm clay inside the lamplit palette rather than an error
     * red, and not either player's colour, which is what an armed confirmation used to
     * borrow.
     */
    private int restingInk(int item, boolean highContrast, long now) {
        return Theme.CREAM;
    }

    // ---- The footer ------------------------------------------------------------------

    /**
     * Who is at the table, and how to move.
     *
     * <p>These used to be two dim lines of the same size sitting 16 px apart, and read as
     * small print twice. The first is a chip with its own colour and the second is the only
     * quiet thing on the screen; more to the point they now have air between them and a
     * 24 px margin under them, instead of the second one's descender ending 7 px from the
     * panel's inner edge.
     *
     * <p>They cannot be one line: side by side the widest pair measures 1078 px against an
     * 824 px lane, and neither of them is prose that may be set any smaller.
     */
    private void drawFooter(Canvas canvas, float width, UiState ui) {
        boolean together = ui.joined[1];
        String hint = together ? "Rose and Sky are both here"
                : "Sky can join any time — press a button";
        int accent = together ? Theme.PINK : Comfort.skyColor();

        float size = Theme.textSize(Theme.CAPTION);
        float half = size * .95f;
        float textWidth = draw.measure(hint, size, together);
        float heart = together ? size * 1.5f : 0;
        float chipWidth = textWidth + heart + Theme.scale(48);
        float left = width / 2 - chipWidth / 2;
        float right = width / 2 + chipWidth / 2;

        draw.roundRect(canvas, left, chipCentre - half, right, chipCentre + half, half,
                Draw.withAlpha(accent, together ? 52 : 30));
        draw.roundRectStroke(canvas, left, chipCentre - half, right, chipCentre + half,
                half, Theme.keyline(), Draw.withAlpha(accent, 130));

        float textLeft = left + Theme.scale(24);
        draw.text(canvas, hint, textLeft, chipCentre + draw.capCentreOffset(size), size,
                together ? Theme.PINK_LIGHT : Theme.CREAM, Paint.Align.LEFT, together);
        if (together) {
            draw.heart(canvas, textLeft + textWidth + heart * .6f, chipCentre, size * .95f,
                    Theme.PINK);
        }

        // While the question is armed, this line is the answer to it: the row states the
        // cost in the room it has, and how to say yes belongs where a player already looks
        // to find out what the buttons do.
        String line = footerHint(ui.menu);
        draw.text(canvas, line, width / 2, hintBaseline, size,
                Theme.secondaryText(ui.highContrastOn), Paint.Align.CENTER, false);
    }

    /**
     * How to use the highlighted row. The size and story rows are steppers whose centre
     * button starts something, and a generic "A to choose" left both of them looking like
     * they had already been chosen.
     */
    static String footerHint(int menu) {
        int row = Math.floorMod(menu, ITEM_COUNT);
        if (row == ITEM_STORY) {
            return "Left and right to pick a chapter   ·   " + confirmName() + " to open it";
        }
        if (row == ITEM_SIZE) {
            return "Left and right to pick a size   ·   " + confirmName() + " to start it";
        }
        return "D-pad to move   ·   " + confirmName() + " to choose";
    }

    static String compactFooterHint(int menu) {
        int row = Math.floorMod(menu, ITEM_COUNT);
        if (row == ITEM_STORY) {
            return "← → Pick chapter   ·   " + confirmName() + " Open";
        }
        if (row == ITEM_SIZE) {
            return "← → Pick size   ·   " + confirmName() + " Start";
        }
        return "D-pad Move   ·   " + confirmName() + " Choose";
    }
}
