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
    /** What the footer says on a touchscreen, where rows are tapped rather than chosen. */
    static String touchFooter(int row) {
        switch (row) {
            case ITEM_STORY:
                return "Tap ‹ › for chapters, the row to open";
            case ITEM_SIZE:
                return "Tap ‹ › for sizes, the row to start";
            default:
                return "Tap a row to choose";
        }
    }

    private final float[] rowRect = new float[4];

    /**
     * Every piece of text the landing page drew last frame, as {left, top, right, bottom}
     * runs of four, so a one-time tip can be set down beside its row without covering any
     * of it. The rows are in here as whole pills.
     */
    private final float[] textRects = new float[4 * 24];
    /** Which {@code Tips.YIELD_*} group each rectangle's words are in, or 0. */
    private final int[] textGroups = new int[24];
    private int textRectCount;

    /** The text drawn last frame, four floats a rectangle; see {@link #textRectCount}. */
    public float[] textRects() {
        return textRects;
    }

    public int textRectCount() {
        return textRectCount;
    }

    public int[] textGroups() {
        return textGroups;
    }

    private void keepClear(float left, float top, float right, float bottom) {
        keepClear(left, top, right, bottom, 0);
    }

    private void keepClear(float left, float top, float right, float bottom, int group) {
        if (textRectCount >= textGroups.length) {
            return;
        }
        textGroups[textRectCount] = group;
        int at = textRectCount * 4;
        textRects[at] = left;
        textRects[at + 1] = top;
        textRects[at + 2] = right;
        textRects[at + 3] = bottom;
        textRectCount++;
    }

    /** Records a line of text drawn at {@code baseline} with the given alignment. */
    private void keepClear(String text, float x, float baseline, float size,
                           Paint.Align align, boolean strong, int group) {
        float textWidth = draw.measure(text, size, strong);
        float left = align == Paint.Align.LEFT ? x
                : align == Paint.Align.RIGHT ? x - textWidth : x - textWidth / 2;
        keepClear(left, baseline - size * (CAP_HEIGHT + .12f), left + textWidth,
                baseline + size * (DESCENT + .06f), group);
    }

    /**
     * {@code color} as strongly as the words in {@code group} are drawn this frame: a tip
     * that needed their room fades them while it is up.
     */
    private static int yielded(int color, UiState ui, int group, long now) {
        float strength = ui.tips.wordsAlpha(group, now);
        return strength >= 1 ? color
                : Draw.withAlpha(color, (int) ((color >>> 24) * strength));
    }

    /** A row's rectangle as last drawn, for a tip to point at; null before it is drawn. */
    public float[] rowRect(int item) {
        if (!rowsDrawn || item < 0 || item >= ITEM_COUNT) {
            return null;
        }
        rowRect[0] = rowsLeft;
        rowRect[1] = rowCentre[item] - rowPitch / 2;
        rowRect[2] = rowsRight;
        rowRect[3] = rowCentre[item] + rowPitch / 2;
        return rowRect;
    }

    /** The row under a point, or -1. Only rows that have actually been drawn answer. */
    public int itemAt(float x, float y) {
        if (!rowsDrawn || x < rowsLeft || x > rowsRight) {
            return -1;
        }
        for (int item = 0; item < ITEM_COUNT; item++) {
            // The whole pitch, not just the painted pill: the air between two rows belongs
            // to one of them, so a thumb that lands between them still chooses something.
            if (Math.abs(y - rowCentre[item]) <= rowPitch / 2) {
                return item;
            }
        }
        return -1;
    }

    /**
     * Which way a tap on a stepper's value turns it: -1 for the ‹ half, 1 for the › half,
     * 0 when the tap was elsewhere on the row (which opens it instead). The target is
     * padded out to the row's full height and a thumb's width either side, because the
     * chevrons themselves are only as big as a letter.
     */
    public int stepAt(int item, float x) {
        if (!rowsDrawn || !valueIsControl(item)) {
            return 0;
        }
        float pad = Theme.scale(28);
        if (x < valueLeft[item] - pad || x > valueRight[item] + pad) {
            return 0;
        }
        return x < (valueLeft[item] + valueRight[item]) / 2 ? -1 : 1;
    }

    public static String confirmName() {
        if (HudScene.touch()) {
            return "TAP";
        }
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
            return Math.max(width * Theme.MENU_PANEL_LEFT, Theme.safeLeft(width));
        }

        public float right(float width) {
            return Math.min(width * Theme.MENU_PANEL_RIGHT, Theme.safeRight(width));
        }

        /**
         * The panel's top: the overscan line of the 16:9 stage, not of the window. On a
         * television the two are the same line; on a portrait window the stage is the band
         * {@link Theme#stageTop} letterboxes, because rows sized as a seventh of the whole
         * height each grew into pills a sixth of the screen tall. And never above the
         * window's own inset, whichever of the two is lower.
         */
        public float top(float height) {
            return Math.max(Theme.stageTop(height) + Theme.unitHeight() * Theme.SAFE_AREA,
                    Theme.safeTop(height));
        }

        public float bottom(float height) {
            return Math.min(Theme.stageBottom(height) - Theme.unitHeight() * Theme.SAFE_AREA,
                    Theme.safeBottom(height));
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
                    : (float) Math.abs(Math.sin(now / 900.0));
        }
    }

    // ---- Layout ----------------------------------------------------------------------




    /**
     * The widest a stacked landing card grows, in design pixels: about one and a half of
     * a television's cards. The right-hand copy of a row is pinned to its right edge, and
     * past this the gap between a row's name and its value is wider than either.
     */
    private static final float STACKED_CARD_WIDTH = 820f;


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

    /**
     * Baseline to baseline for stacked lines of prose, as a fraction of the size. The
     * greeting used to step by cap height plus descent, .88 em, so a descender on one line
     * touched the capitals on the next.
     */
    private static final float LINE_PITCH = 1.22f;

    /** The most lines the greeting may take. */
    private static final int GREETING_LINES = 3;

    private final Draw draw;
    private final MenuFrame frame;

    /** Where everything goes this frame, worked out in {@link #layOut}. */
    private final float[] rowCentre = new float[ITEM_COUNT];
    /** Where the rows and the steppers' values were last drawn, for a finger to find. */
    private float rowsLeft;
    private float rowsRight;
    private final float[] valueLeft = new float[ITEM_COUNT];
    private final float[] valueRight = new float[ITEM_COUNT];
    private boolean rowsDrawn;
    private final String[] greetingLines = new String[GREETING_LINES];
    private float rowHalf;
    private float rowPitch;

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
        textRectCount = 0;
        // The fractions are of the 16:9 stage rather than of the window, so a square or a
        // tall window letterboxes this screen instead of stretching both cards down it,
        // and each edge also clears whatever the window's own bars and cutout cover.
        float stage = Theme.unitHeight();
        float stageTop = Theme.stageTop(height);
        float top = Math.max(stageTop + stage * .105f, Theme.safeTop(height));
        float bottom = Math.min(stageTop + stage * .895f, Theme.safeBottom(height));
        float left = Math.max(width * .075f, Theme.safeLeft(width));
        float right = Math.min(width * .925f, Theme.safeRight(width));
        float gap = Theme.scale(22);
        int panelAlpha = ui.highContrastOn ? 246 : 228;
        int selected = Math.floorMod(ui.menu, ITEM_COUNT);

        if (Theme.tall(width, height)) {
            drawStackedLanding(canvas, width, height, left, right, gap, game, ui, selected,
                    panelAlpha, now);
            return;
        }

        float centre = (left + right) * .5f;
        float brandLeft = left;
        float brandRight = centre - gap / 2;
        float menuLeft = centre + gap / 2;
        float menuRight = right;

        draw.panel(canvas, brandLeft, top, brandRight, bottom, panelAlpha);
        draw.panel(canvas, menuLeft, top, menuRight, bottom, panelAlpha);

        drawLandingBrand(canvas, brandLeft, top, brandRight, bottom, game, ui, selected,
                now);
        drawLandingMenu(canvas, menuLeft, top, menuRight, bottom, game, ui, selected, now);
    }

    /**
     * The two cards of the landing page, one above the other, for a window taller than it
     * is wide.
     *
     * <p>Side by side on a portrait tablet the cards were each half of 85% of the width
     * and 79% of the height — two tall slots a fifth as wide as they were high, with the
     * wordmark running out of the left one and every row's copy out of the right. Stacked,
     * each card is exactly as tall as it is on a television, so everything inside it lands
     * where it was designed to; the pair is centred in the safe height, and each is as
     * wide as the safe width allows up to {@link #STACKED_CARD_WIDTH}, past which rows
     * stop reading as rows.
     */
    private void drawStackedLanding(Canvas canvas, float width, float height, float left,
                                    float right, float gap, GameState game, UiState ui,
                                    int selected, int panelAlpha, long now) {
        float cardHeight = Theme.unitHeight() * .79f;
        float safeTop = Theme.safeTop(height);
        float safeBottom = Theme.safeBottom(height);
        float total = cardHeight * 2 + gap;
        float top = Math.max(safeTop, (safeTop + safeBottom - total) / 2);
        float centre = (left + right) / 2;
        float half = Math.min(right - left, Theme.scale(STACKED_CARD_WIDTH)) / 2;
        float cardLeft = centre - half;
        float cardRight = centre + half;
        float brandBottom = top + cardHeight;
        float menuTop = brandBottom + gap;
        float menuBottom = Math.min(safeBottom, menuTop + cardHeight);

        draw.panel(canvas, cardLeft, top, cardRight, brandBottom, panelAlpha);
        draw.panel(canvas, cardLeft, menuTop, cardRight, menuBottom, panelAlpha);

        drawLandingBrand(canvas, cardLeft, top, cardRight, brandBottom, game, ui, selected,
                now);
        drawLandingMenu(canvas, cardLeft, menuTop, cardRight, menuBottom, game, ui,
                selected, now);
    }

    private void drawLandingBrand(Canvas canvas, float left, float top, float right,
                                  float bottom, GameState game, UiState ui, int selected,
                                  long now) {
        float cx = (left + right) / 2;
        float pad = Theme.scale(34);
        float lane = right - left - pad * 2;

        float beat = Comfort.get().calmMotion ? .5f
                : (float) Math.abs(Math.sin(now / 1700.0));
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
        keepClear(left + pad, titleY - titleSize * (CAP_HEIGHT + .12f), right - pad,
                titleY + titleSize * DESCENT);

        float ruleY = titleY + titleSize * DESCENT + Theme.scale(12);
        draw.roundRect(canvas, cx - Theme.scale(54), ruleY, cx + Theme.scale(54),
                ruleY + Theme.scale(3), Theme.scale(2), Draw.withAlpha(Theme.PINK, 190));

        float greetingSize = landingText(Theme.CAPTION);
        String greeting = welcome.isEmpty() ? "Puzzles are better together" : welcome;
        int greetingCount = wrapGreeting(greeting, greetingSize, lane);
        float greetingY = ruleY + Theme.scale(18) + greetingSize * CAP_HEIGHT;
        float greetingPitch = greetingSize * LINE_PITCH;
        for (int i = 0; i < greetingCount; i++) {
            draw.text(canvas, greetingLines[i], cx, greetingY, greetingSize,
                    welcome.isEmpty() ? Comfort.skyColor() : Theme.GOLD,
                    Paint.Align.CENTER, false);
            keepClear(greetingLines[i], cx, greetingY, greetingSize, Paint.Align.CENTER,
                    false, 0);
            greetingY += greetingPitch;
        }
        greetingY -= greetingPitch - greetingSize * (CAP_HEIGHT + DESCENT);

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
        int detail = Tips.YIELD_DETAIL;
        String feature = landingFeature(game, selected);
        float featureSize = draw.fit(feature, landingText(Theme.HEADING), detailLane,
                true, landingText(Theme.SUBHEAD));
        float metaWanted = landingText(Theme.CAPTION);
        // The card is as tall as the greeting above it leaves. Its name and its chapter
        // must both be inside it; with a three-line greeting and LARGER TEXT they were
        // not — "Chapter 24 of 24" sat half off the card's bottom edge — so the eyebrow,
        // which only labels the name under it, is what gives way.
        float cardBottom = bottom - Theme.scale(105);
        float inner = Theme.scale(18) + featureSize * CAP_HEIGHT + featureSize * DESCENT
                + Theme.scale(18) + metaWanted * (CAP_HEIGHT + DESCENT) + Theme.scale(22);
        boolean eyebrowFits = detailTop + Theme.scale(30) + eyebrowSize * CAP_HEIGHT + inner
                <= cardBottom;
        float featureY;
        if (eyebrowFits) {
            float eyebrowY = detailTop + Theme.scale(30) + eyebrowSize * CAP_HEIGHT;
            draw.text(canvas, landingEyebrow(selected), textLeft, eyebrowY, eyebrowSize,
                    yielded(Theme.GOLD, ui, detail, now), Paint.Align.LEFT, true);
            keepClear(landingEyebrow(selected), textLeft, eyebrowY, eyebrowSize,
                    Paint.Align.LEFT, true, detail);
            featureY = eyebrowY + Theme.scale(18) + featureSize * CAP_HEIGHT;
        } else {
            featureY = detailTop + Theme.scale(26) + featureSize * CAP_HEIGHT;
        }
        draw.text(canvas, feature, textLeft, featureY, featureSize,
                yielded(Theme.CREAM, ui, detail, now), Paint.Align.LEFT, true);
        keepClear(feature, textLeft, featureY, featureSize, Paint.Align.LEFT, true, detail);

        float metaSize = landingText(Theme.CAPTION);
        float metaY = featureY + featureSize * DESCENT + Theme.scale(18)
                + metaSize * CAP_HEIGHT;
        metaSize = draw.fitText(canvas, landingMeta(game, selected), textLeft, metaY,
                metaSize, landingText(Theme.MIN_PROSE_SP), detailLane,
                yielded(Theme.secondaryText(ui.highContrastOn), ui, detail, now),
                Paint.Align.LEFT, false);
        keepClear(landingMeta(game, selected), textLeft, metaY, metaSize, Paint.Align.LEFT,
                false, detail);

        float bodySize = landingText(Theme.BODY);
        float bodyY = metaY + metaSize * DESCENT + Theme.scale(26) + bodySize * CAP_HEIGHT;
        // The card's last line is the one that only decorates, so it is the one left out —
        // with LARGER TEXT, or when a long greeting has moved the card down — rather than
        // running out of the bottom of the card.
        if (Theme.textScale() <= 1.2f
                && bodyY + bodySize * DESCENT <= bottom - Theme.scale(105) - Theme.scale(18)) {
            float bodyLane = detailLane - Theme.scale(24);
            float bodyFit = draw.fit(landingDescription(selected), bodySize, bodyLane, false,
                    landingText(Theme.CAPTION));
            draw.text(canvas, landingDescription(selected), textLeft, bodyY, bodyFit,
                    yielded(Theme.CREAM, ui, detail, now), Paint.Align.LEFT, false);
            keepClear(landingDescription(selected), textLeft, bodyY, bodyFit,
                    Paint.Align.LEFT, false, detail);
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
        int header = Tips.YIELD_HEADER;
        draw.text(canvas, "Choose how to settle in", rowLeft, headingY, heading,
                yielded(Theme.CREAM, ui, header, now), Paint.Align.LEFT, true);
        keepClear("Choose how to settle in", rowLeft, headingY, heading, Paint.Align.LEFT,
                true, header);
        float caption = landingText(Theme.CAPTION);
        String invitation = "A story, a puzzle, or a cozier room";
        float invitationY = headingY + Theme.scale(18) + caption * CAP_HEIGHT;
        float invitationSize = draw.fit(invitation, caption, rowRight - rowLeft, false,
                landingText(Theme.MIN_PROSE_SP));
        draw.text(canvas, invitation, rowLeft, invitationY, invitationSize,
                yielded(Theme.secondaryText(ui.highContrastOn), ui, header, now),
                Paint.Align.LEFT, false);
        keepClear(invitation, rowLeft, invitationY, invitationSize, Paint.Align.LEFT, false,
                header);

        float rowsTop = top + Theme.scale(150);
        float footerRoom = Theme.scale(92);
        rowPitch = (bottom - footerRoom - rowsTop) / ITEM_COUNT;
        rowHalf = Math.min(Theme.scale(52), rowPitch * .39f);
        for (int item = 0; item < ITEM_COUNT; item++) {
            rowCentre[item] = rowsTop + rowPitch * (item + .5f);
        }
        rowsLeft = rowLeft;
        rowsRight = rowRight;
        rowsDrawn = true;
        follow(selected, rowCentre[selected], now);

        for (int item = 0; item < ITEM_COUNT; item++) {
            drawLandingRowSurface(canvas, rowLeft, rowRight, rowCentre[item], rowHalf,
                    item, selected, ui.highContrastOn, now);
        }
        for (int item = 0; item < ITEM_COUNT; item++) {
            drawLandingRowCopy(canvas, rowLeft, rowRight, rowCentre[item], game, item,
                    selected, ui.highContrastOn);
            keepClear(rowLeft, rowCentre[item] - rowHalf, rowRight, rowCentre[item] + rowHalf);
        }

        float footerY = bottom - Theme.scale(39);
        // The control hint may step aside for a tip, like the header: in the stacked layout
        // the space under Cozy Corner is the only place that tip can point from, and the
        // tip is itself the instruction. The rows themselves never yield.
        int hint = Tips.YIELD_HEADER;
        drawLandingFooter(canvas, rowLeft, rowRight, footerY, selected, ui.highContrastOn,
                ui.tips.wordsAlpha(hint, now));
        float footerHalf = landingText(Theme.CAPTION) * .62f;
        keepClear(rowLeft, footerY - footerHalf, rowRight, footerY + footerHalf, hint);
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
        float end = right - Theme.scale(24);
        float gap = Theme.scale(16);

        // What sits on the right — a stepper's value, or OPEN on the focused corner row —
        // is measured first, so the two lines beside it know how much room they have.
        boolean settings = item == ITEM_SETTINGS;
        String action = settings ? null : values(game)[item];
        float actionSize = settings ? 0 : draw.fit(action, landingText(Theme.BODY),
                (right - left) * .42f, false, landingText(Theme.CAPTION));
        float accessory = settings ? (focused ? confirmWidth("OPEN") : 0)
                : draw.measure(action, actionSize, false);

        float labelWanted = landingText(Theme.SUBHEAD);
        float labelY = centreY - Theme.scale(13) + draw.capCentreOffset(labelWanted);
        float detailSize = landingText(Theme.CAPTION);
        String detail = rowDetail(game, item);
        float detailY = centreY + Theme.scale(20) + draw.capCentreOffset(detailSize);

        // Centred in the row it reads best, and it stays there while the detail line fits
        // beside it. When it does not — "Comfort, sound, and how to play" ran under the
        // focused row's OPEN — it moves up beside the row's name, which is short, and the
        // detail gets the whole width.
        float accessoryY = centreY;
        float detailLane = end - x;
        float labelLane = (right - left) * .55f;
        if (accessory > 0) {
            float beside = end - accessory - gap - x;
            if (draw.measure(detail, detailSize, false) <= beside) {
                detailLane = beside;
                labelLane = Math.min(labelLane, beside);
            } else {
                accessoryY = labelY - draw.capCentreOffset(labelWanted);
                labelLane = beside;
            }
        }
        draw.fitText(canvas, items(game)[item], x, labelY, labelWanted,
                landingText(Theme.BODY), labelLane, primary, Paint.Align.LEFT, true);
        draw.fitText(canvas, detail, x, detailY, detailSize,
                landingText(Theme.MIN_PROSE_SP), detailLane, secondary, Paint.Align.LEFT,
                false);

        if (settings) {
            if (focused) {
                drawLandingConfirm(canvas, end, accessoryY, "OPEN", primary);
            }
            return;
        }
        valueRight[item] = end;
        valueLeft[item] = end - accessory;
        draw.text(canvas, action, end, accessoryY + draw.capCentreOffset(actionSize),
                actionSize, primary, Paint.Align.RIGHT, false);
    }

    /** How wide {@link #drawLandingConfirm} draws {@code label} and its button. */
    private float confirmWidth(String label) {
        float size = landingText(Theme.BODY);
        float labelWidth = draw.measure(label, size, true);
        if (HudScene.touch()) {
            return labelWidth;
        }
        return labelWidth + Theme.scale(9) + draw.keycapWidth(confirmName(), size * 1.05f);
    }

    private void drawLandingConfirm(Canvas canvas, float right, float centreY,
                                    String label, int textColor) {
        float size = landingText(Theme.BODY);
        if (HudScene.touch()) {
            // A finger has no A button; the row itself is what it presses.
            draw.text(canvas, label, right, centreY + draw.capCentreOffset(size), size,
                    textColor, Paint.Align.RIGHT, true);
            return;
        }
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
                                   int selected, boolean highContrast, float strength) {
        if (strength <= 0) {
            return;
        }
        int row = Math.floorMod(selected, ITEM_COUNT);
        if (HudScene.touch()) {
            String line = touchFooter(row);
            float size = draw.fit(line, landingText(Theme.CAPTION), right - left, false,
                    landingText(Theme.MIN_PROSE_SP));
            draw.text(canvas, line, (left + right) / 2, centreY + draw.capCentreOffset(size),
                    size, faded(Theme.secondaryText(highContrast), strength), Paint.Align.CENTER,
                    false);
            return;
        }
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

        int text = faded(Theme.secondaryText(highContrast), strength);
        float x = (left + right - total) / 2;
        draw.dpadKeycap(canvas, x, centreY, chipHeight, faded(Theme.SOFT_TEXT, strength));
        x += chipHeight + gap;
        draw.text(canvas, lead, x, centreY + draw.capCentreOffset(size), size, text,
                Paint.Align.LEFT, false);
        x += draw.measure(lead, size, false) + gap * 2;
        int chipColor = faded(HudScene.remoteOnly() ? Theme.SOFT_TEXT : Theme.BUTTON_A,
                strength);
        draw.keycap(canvas, x, centreY, chipHeight, key, chipColor);
        x += chipWidth + gap;
        draw.text(canvas, action, x, centreY + draw.capCentreOffset(size), size, text,
                Paint.Align.LEFT, false);
    }

    private static int faded(int color, float strength) {
        return strength >= 1 ? color
                : Draw.withAlpha(color, (int) ((color >>> 24) * strength));
    }

    private void drawPresence(Canvas canvas, float left, float right, float centreY,
                              UiState ui) {
        boolean together = ui.skyPlaying();
        String line = presenceLine(ui);
        int accent = together || !ui.twoPlayers ? Theme.PINK : Comfort.skyColor();
        float size = draw.fit(line, landingText(Theme.CAPTION), right - left
                - Theme.scale(34), together, landingText(Theme.MIN_PROSE_SP));
        float half = size * .95f;
        keepClear(left, centreY - half, right, centreY + half);
        draw.roundRect(canvas, left, centreY - half, right, centreY + half, half,
                Draw.withAlpha(accent, together ? 48 : 28));
        draw.roundRectStroke(canvas, left, centreY - half, right, centreY + half, half,
                Theme.keyline(), Draw.withAlpha(accent, 135));
        draw.text(canvas, line, (left + right) / 2,
                centreY + draw.capCentreOffset(size), size,
                together ? Theme.PINK_LIGHT : Theme.CREAM, Paint.Align.CENTER, together);
    }

    /**
     * Who is at the table. Short on purpose: the chip is one line inside the left card, and
     * "Sky can join any time — press a button" ran 31 px past both ends of it with LARGER
     * TEXT on, at a size the prose floor would not let it shrink below.
     */
    static String presenceLine(UiState ui) {
        if (ui.skyPlaying()) {
            return "Rose and Sky are ready  ♥";
        }
        if (!ui.twoPlayers) {
            return "A quiet puzzle, just for you";
        }
        return HudScene.touch() ? "Sky can join on a controller"
                : "Sky can join with any button";
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
            case ITEM_SIZE: return "Endless · always a new picture";
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
            default: return "Comfort, sound, and how to play";
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
     * Breaks the greeting into lines that each fit {@code maxWidth}, into
     * {@link #greetingLines}, and says how many it used.
     *
     * <p>The em dash is tried first, because it is not punctuation the game chose at random:
     * {@code SaveStore} builds the greeting as "how long you were away — what you have made
     * together", so breaking there gives two whole thoughts instead of a line ending in
     * "seat" and the next one opening with a dash.
     *
     * <p>Otherwise the words are wrapped as evenly as the lane allows. This used to stop
     * after two lines and drop the rest: "It's been a while — the room kept your seat warm
     * — 12 pictures finished together" came out as two lines ending at "pictures", which is
     * not a greeting but half of one. Every word is kept now; the longest greeting
     * {@code SaveStore} can produce needs three lines at LARGER TEXT, and the detail card
     * below moves down to make room.
     */
    private int wrapGreeting(String line, float size, float maxWidth) {
        java.util.Arrays.fill(greetingLines, "");
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
        String[] lines = draw.wrap(line, size, maxWidth, false);
        // Even them out: as narrow as the lane can go without needing another line.
        float lane = maxWidth;
        while (lane > maxWidth * .5f) {
            String[] tighter = draw.wrap(line, size, lane * .95f, false);
            if (tighter.length != lines.length) {
                break;
            }
            lines = tighter;
            lane *= .95f;
        }
        int used = Math.min(lines.length, greetingLines.length);
        System.arraycopy(lines, 0, greetingLines, 0, used);
        if (lines.length > used) {
            // Never drop words: the last line keeps the rest, and the audit says so if it
            // then runs out of the card.
            StringBuilder rest = new StringBuilder(greetingLines[used - 1]);
            for (int i = used; i < lines.length; i++) {
                rest.append(' ').append(lines[i]);
            }
            greetingLines[used - 1] = rest.toString();
        }
        return used;
    }

    // ---- The wordmark ----------------------------------------------------------------


    // ---- The rows --------------------------------------------------------------------



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




    // ---- The footer ------------------------------------------------------------------


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
