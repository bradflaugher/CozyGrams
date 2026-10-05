package com.cozygrams.tv;

/**
 * The first-evening tour: a real 5x5 puzzle, solved together one step at a time.
 *
 * <p>It reads a clue out loud and fills the row it gives away, hands the player one square
 * to fill and one to cross out with whatever is in their hands, finishes the picture, then
 * shows the controls, how a second controller joins as Sky, and where to go next. Every
 * step can be skipped, stepped back from, or left entirely; How to play starts it again.
 *
 * <p>This class is the whole of what the tour <em>is</em> — its steps, its words, its
 * little board and what that board shows at any moment — and none of how it is drawn or
 * driven. {@link TutorialScene} draws it and {@link CozyGameView} presses its buttons, so
 * all of it can be held to account by plain unit tests.
 */
public final class Tutorial {

    public static final int STEP_WELCOME = 0;
    public static final int STEP_READ = 1;
    public static final int STEP_FILL = 2;
    public static final int STEP_CROSS = 3;
    public static final int STEP_SOLVED = 4;
    public static final int STEP_HANDS = 5;
    public static final int STEP_TOGETHER = 6;
    public static final int STEP_NEXT = 7;
    public static final int STEP_COUNT = 8;

    /** The three buttons along the bottom, left to right. */
    public static final int FOCUS_SKIP = 0;
    public static final int FOCUS_BACK = 1;
    public static final int FOCUS_NEXT = 2;

    public static final int SIZE = 5;

    /** The picture: a heart. Its clues read 1 1 / 5 / 5 / 3 / 1 and 2 / 4 / 4 / 4 / 2. */
    static final boolean[][] PICTURE = parse(
            ".#.#.",
            "#####",
            "#####",
            ".###.",
            "..#..");

    /** The row the tour reads aloud, and fills for the player. */
    static final int READ_ROW = 1;
    /** The square the player fills themselves. */
    static final int FILL_ROW = 2;
    static final int FILL_COL = 0;
    /** The square the player crosses out themselves. */
    static final int CROSS_ROW = 4;
    static final int CROSS_COL = 1;

    /** Pause before the tour's own animation starts on a step, and the gap between squares. */
    static final long LEAD_MS = 450;
    static final long STAGGER_MS = 170;

    public int step;
    public int focus = FOCUS_NEXT;
    /** When this step began, and when the player did its one thing; 0 for not yet. */
    public long stepAt;
    public long actedAt;
    /** True once the player has filled or crossed the glowing square on this step. */
    public boolean acted;
    /**
     * The glowing square on the cross step, as a remote's OK has cycled it so far: a
     * remote has no cross button, so it gets there through fill, the way it does in play.
     */
    public byte remoteMark = Puzzle.UNKNOWN;

    /** Starts the tour from the beginning. */
    public void start(long now) {
        go(STEP_WELCOME, now);
    }

    /** Moves to a step, fresh: nothing on it has been done yet. */
    public void go(int next, long now) {
        step = Math.max(0, Math.min(STEP_COUNT - 1, next));
        stepAt = now;
        actedAt = 0;
        acted = false;
        remoteMark = Puzzle.UNKNOWN;
        focus = FOCUS_NEXT;
    }

    /** @return false when this was the last step, and the tour is over */
    public boolean next(long now) {
        if (step >= STEP_COUNT - 1) {
            return false;
        }
        go(step + 1, now);
        return true;
    }

    public void back(long now) {
        if (step > 0) {
            go(step - 1, now);
        }
    }

    public boolean isLast() {
        return step == STEP_COUNT - 1;
    }

    /** True while a step is waiting for the player's own fill or cross. */
    public boolean awaitingAction() {
        return (step == STEP_FILL || step == STEP_CROSS) && !acted;
    }

    /** True when a button can be chosen at all: there is no step before the first one. */
    public boolean enabled(int button) {
        return button != FOCUS_BACK || step > 0;
    }

    /** Moves the focus left or right, past a button that cannot be chosen. */
    public void moveFocus(int direction) {
        int next = focus;
        do {
            next += direction;
        } while (next >= FOCUS_SKIP && next <= FOCUS_NEXT && !enabled(next));
        if (next >= FOCUS_SKIP && next <= FOCUS_NEXT) {
            focus = next;
        }
    }

    /** Fills the glowing square, on the step that asks for it. */
    public boolean fill(long now) {
        if (step != STEP_FILL || acted) {
            return false;
        }
        act(now);
        return true;
    }

    /** Crosses out the glowing square, on the step that asks for it. */
    public boolean cross(long now) {
        if (step != STEP_CROSS || acted) {
            return false;
        }
        act(now);
        return true;
    }

    /**
     * A remote's OK on the cross step: fill, then cross, as the button does on the board.
     * The fill is shown for a moment rather than refused, because it is exactly what that
     * button will do in a real puzzle and the tour should not pretend otherwise.
     */
    public boolean cycle(long now) {
        if (step == STEP_FILL) {
            return fill(now);
        }
        if (step != STEP_CROSS || acted) {
            return false;
        }
        if (remoteMark == Puzzle.UNKNOWN) {
            remoteMark = Puzzle.FILLED;
            return true;
        }
        remoteMark = Puzzle.CROSSED;
        act(now);
        return true;
    }

    private void act(long now) {
        acted = true;
        actedAt = now == 0 ? 1 : now;
    }

    // ---- The little board ------------------------------------------------------------

    public static int[] rowClues(int row) {
        return Puzzle.clues(PICTURE[row]);
    }

    public static int[] colClues(int col) {
        boolean[] line = new boolean[SIZE];
        for (int row = 0; row < SIZE; row++) {
            line[row] = PICTURE[row][col];
        }
        return Puzzle.clues(line);
    }

    /** The square that glows on this step, as {row, col}, or null when none does. */
    public int[] target() {
        if (step == STEP_FILL) {
            return new int[]{FILL_ROW, FILL_COL};
        }
        if (step == STEP_CROSS) {
            return new int[]{CROSS_ROW, CROSS_COL};
        }
        return null;
    }

    /** The row the words on this step are about, or -1. */
    public int focusRow() {
        switch (step) {
            case STEP_READ:
                return READ_ROW;
            case STEP_FILL:
                return FILL_ROW;
            case STEP_CROSS:
                return CROSS_ROW;
            default:
                return -1;
        }
    }

    /**
     * What each square shows at {@code now}. Everything earlier steps did is already
     * there; this step's own squares appear one after another, a beat apart, and with
     * calm motion on they are simply there.
     */
    public byte[][] board(long now, boolean calm) {
        byte[][] marks = new byte[SIZE][SIZE];
        for (int s = 0; s <= step; s++) {
            boolean current = s == step;
            long from = s == STEP_FILL || s == STEP_CROSS ? (current ? actedAt : 1) : stepAt;
            if (s == STEP_FILL || s == STEP_CROSS) {
                boolean done = !current || acted;
                if (!done) {
                    continue;
                }
                int[] own = s == STEP_FILL ? new int[]{FILL_ROW, FILL_COL}
                        : new int[]{CROSS_ROW, CROSS_COL};
                marks[own[0]][own[1]] = s == STEP_FILL ? Puzzle.FILLED : Puzzle.CROSSED;
            }
            int[][] reveals = reveals(s);
            for (int i = 0; i < reveals.length; i++) {
                long due = reveals[i][3];
                if (!current || calm || now - from >= due) {
                    marks[reveals[i][0]][reveals[i][1]] = (byte) reveals[i][2];
                }
            }
        }
        if (step == STEP_CROSS && !acted && remoteMark != Puzzle.UNKNOWN) {
            marks[CROSS_ROW][CROSS_COL] = remoteMark;
        }
        return marks;
    }

    /** True while this step still has squares on their way. */
    public boolean animating(long now, boolean calm) {
        if (calm) {
            return false;
        }
        int[][] reveals = reveals(step);
        if (reveals.length == 0) {
            return false;
        }
        long from = step == STEP_FILL || step == STEP_CROSS ? actedAt : stepAt;
        if (from == 0) {
            return false;
        }
        return now - from < reveals[reveals.length - 1][3] + 200;
    }

    /**
     * The squares a step brings in by itself, as {row, col, mark, delay}: the row the clue
     * gives away, the rest of a row the player started, and at the end the whole picture.
     */
    static int[][] reveals(int step) {
        switch (step) {
            case STEP_READ: {
                int[][] out = new int[SIZE][];
                for (int col = 0; col < SIZE; col++) {
                    out[col] = new int[]{READ_ROW, col, Puzzle.FILLED,
                            (int) (LEAD_MS + col * STAGGER_MS)};
                }
                return out;
            }
            case STEP_FILL: {
                int[][] out = new int[SIZE - 1][];
                for (int col = 1; col < SIZE; col++) {
                    out[col - 1] = new int[]{FILL_ROW, col, Puzzle.FILLED,
                            (int) (250 + (col - 1) * STAGGER_MS)};
                }
                return out;
            }
            case STEP_CROSS:
                return new int[][]{
                        {CROSS_ROW, 0, Puzzle.CROSSED, 250},
                        {CROSS_ROW, 3, Puzzle.CROSSED, (int) (250 + STAGGER_MS)},
                        {CROSS_ROW, 4, Puzzle.CROSSED, (int) (250 + STAGGER_MS * 2)}
                };
            case STEP_SOLVED: {
                int[][] out = new int[11][];
                int n = 0;
                int[][] fills = {{0, 1}, {0, 3}, {3, 1}, {3, 2}, {3, 3}, {4, 2}};
                for (int[] f : fills) {
                    out[n] = new int[]{f[0], f[1], Puzzle.FILLED,
                            (int) (LEAD_MS + n * STAGGER_MS)};
                    n++;
                }
                int[][] crosses = {{0, 0}, {0, 2}, {0, 4}, {3, 0}, {3, 4}};
                for (int[] c : crosses) {
                    out[n] = new int[]{c[0], c[1], Puzzle.CROSSED,
                            (int) (LEAD_MS + 6 * STAGGER_MS + 200 + (n - 6) * 90)};
                    n++;
                }
                return out;
            }
            default:
                return new int[0][];
        }
    }

    /** True when a line of the board shown matches its clue exactly: it earns a tick. */
    static boolean rowDone(byte[][] marks, int row) {
        for (int col = 0; col < SIZE; col++) {
            if ((marks[row][col] == Puzzle.FILLED) != PICTURE[row][col]) {
                return false;
            }
        }
        return true;
    }

    static boolean colDone(byte[][] marks, int col) {
        for (int row = 0; row < SIZE; row++) {
            if ((marks[row][col] == Puzzle.FILLED) != PICTURE[row][col]) {
                return false;
            }
        }
        return true;
    }

    // ---- The words ---------------------------------------------------------------------

    /** Which hands are in the room, as the words should name them. */
    static final int HANDS_PAD = 0;
    static final int HANDS_REMOTE = 1;
    static final int HANDS_TOUCH = 2;
    /** Nothing pressed yet on a television: a pad or a remote, so both are named. */
    static final int HANDS_UNKNOWN = 3;

    public String title() {
        switch (step) {
            case STEP_WELCOME:
                return "Welcome to CozyGrams";
            case STEP_READ:
                return "Reading a clue";
            case STEP_FILL:
                return acted ? "Lovely" : "Your turn: fill a square";
            case STEP_CROSS:
                return acted ? "Just right" : "Cross out what stays empty";
            case STEP_SOLVED:
                return "That's the picture";
            case STEP_HANDS:
                return "Your controls";
            case STEP_TOGETHER:
                return "Playing together";
            default:
                return "Where to next";
        }
    }

    public String body(int hands) {
        switch (step) {
            case STEP_WELCOME:
                return "A little picture is hiding in this grid. The numbers around the "
                        + "edge say where. This short tour solves one with you.";
            case STEP_READ:
                return "Each number is a run of filled squares, in order. This row says 5 "
                        + "and is five squares wide, so every square in it is filled.";
            case STEP_FILL:
                return acted ? "The row says 5 too, so the rest of it follows."
                        : "The next row also says 5, so its squares are sure.";
            case STEP_CROSS:
                return acted ? "Crosses are notes to yourself: this square is empty."
                        : "The bottom row is a single 1, and only the middle column wants "
                        + "it. The squares beside it stay empty.";
            case STEP_SOLVED:
                return "When every row and column matches its clues, the picture is "
                        + "finished: a heart. Finished lines get a tick as you go.";
            case STEP_HANDS:
                return "";
            case STEP_TOGETHER:
                return hands == HANDS_TOUCH
                        ? "Your finger is Rose. Turn on Two players in the Cozy Corner, and "
                        + "a paired controller joins as Sky with her own cursor."
                        : "Hand someone a second controller. When they press any button "
                        + "they join as Sky, with their own cursor, color and letter.";
            default:
                return "Open the Story Book for 24 handmade chapters, starting with a "
                        + "little 5×5. Sound, comfort and How to play live in the Cozy "
                        + "Corner.";
        }
    }

    /** The line that says what to press, or an empty string when nothing is asked. */
    public String prompt(int hands) {
        if (step == STEP_FILL && !acted) {
            switch (hands) {
                case HANDS_TOUCH:
                    return "Tap the glowing square";
                case HANDS_REMOTE:
                    return "Press OK to fill the glowing square";
                case HANDS_UNKNOWN:
                    return "Press A or OK to fill the glowing square";
                default:
                    return "Press A to fill the glowing square";
            }
        }
        if (step == STEP_CROSS && !acted) {
            switch (hands) {
                case HANDS_TOUCH:
                    return "Hold the glowing square to cross it out";
                case HANDS_REMOTE:
                    return "Press OK twice: fill, then cross";
                case HANDS_UNKNOWN:
                    return "Press B to cross it out, or OK twice on a remote";
                default:
                    return "Press B or X to cross it out";
            }
        }
        if (step == STEP_WELCOME) {
            return hands == HANDS_TOUCH ? "Skip the tour any time"
                    : "Skip the tour any time with Back";
        }
        if (step == STEP_TOGETHER) {
            return "You share one picture, and all the credit";
        }
        return "";
    }

    /** The controls, a line each, in the names of whatever is in the room. */
    public static String[] controls(int hands) {
        switch (hands) {
            case HANDS_TOUCH:
                return new String[]{
                        "Tap FILL or CROSS to choose what a tap does",
                        "Drag along a row or column to mark a line",
                        "Hold a square for the other mark",
                        "HINT lights up a square · MENU opens the Cozy Corner"};
            case HANDS_REMOTE:
                return new String[]{
                        "The D-pad moves your cursor; hold it to keep going",
                        "OK fills, then crosses, then clears",
                        "Hold OK to light up one square",
                        "Menu opens the Cozy Corner · Back steps out"};
            case HANDS_UNKNOWN:
                return new String[]{
                        "D-pad or left stick moves; hold to keep going",
                        "A fills · B or X crosses out (on a remote, OK cycles)",
                        "Y, or holding OK, lights up one square",
                        "Menu opens the Cozy Corner · Back steps out"};
            default:
                return new String[]{
                        "D-pad or left stick moves; hold to keep going",
                        "A fills · B or X crosses out",
                        "Y lights up one square when you are stuck",
                        "Menu or Start opens the Cozy Corner · Back steps out"};
        }
    }

    public String nextLabel() {
        return isLast() ? "Let's begin" : "Next";
    }

    public static String buttonName(int button, boolean last) {
        switch (button) {
            case FOCUS_SKIP:
                return "Skip tour";
            case FOCUS_BACK:
                return "Back";
            default:
                return last ? "Let's begin" : "Next";
        }
    }

    /** The whole step as a screen reader should hear it. */
    public String spoken(int hands) {
        StringBuilder words = new StringBuilder("Tour, step ").append(step + 1)
                .append(" of ").append(STEP_COUNT).append(". ").append(title()).append(". ");
        String body = body(hands);
        if (!body.isEmpty()) {
            words.append(body).append(' ');
        }
        if (step == STEP_HANDS) {
            for (String line : controls(hands)) {
                words.append(line).append(". ");
            }
        }
        String prompt = prompt(hands);
        if (!prompt.isEmpty()) {
            words.append(prompt).append(". ");
        }
        words.append(buttonName(focus, isLast())).append(" button.");
        return words.toString();
    }

    private static boolean[][] parse(String... rows) {
        boolean[][] out = new boolean[rows.length][];
        for (int r = 0; r < rows.length; r++) {
            out[r] = new boolean[rows[r].length()];
            for (int c = 0; c < rows[r].length(); c++) {
                out[r][c] = rows[r].charAt(c) == '#';
            }
        }
        return out;
    }
}
