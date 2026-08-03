package com.cozygrams.tv;

/**
 * The reasoning a good nonogram player uses, written down.
 *
 * <p>For one line (a row or a column) the clue numbers describe every legal arrangement
 * of filled squares. If a square is filled in <em>all</em> of those arrangements it must
 * be filled; if it is empty in all of them it must be empty. Anything else stays unknown.
 * Applying that to every row and column, over and over until nothing new is learned, is
 * exactly "line solving" - the deduction a player can do without ever guessing.
 *
 * <p>A board that line-solves all the way to a full grid has one and only one solution
 * and never asks the player to guess. That is the promise CozyGrams makes about every
 * board it deals, and this class is how the promise is checked.
 *
 * <p>The per-line work is a tiny automaton walk. The clues are compiled into states
 * ("no blocks placed yet", "three squares into block two", "block two just ended, a gap
 * is owed"), reachable state sets are swept forwards and backwards once each, and a
 * square is forced whenever only one of filled/empty survives both sweeps. That is
 * linear in the length of the line, so a 20x20 board solves in microseconds and the
 * generator can afford to check - and re-check - every board it hands out.
 *
 * <p>Supports lines up to 61 squares, which is far beyond the 20 CozyGrams uses.
 */
public final class NonogramSolver {

    /** Nothing deduced about this square yet. */
    public static final byte UNKNOWN = 0;
    /** Deduced to be part of the picture. */
    public static final byte FILL = 1;
    /** Deduced to be blank. */
    public static final byte EMPTY = 2;

    /** What line solving managed to work out about a board. */
    public static final class Result {
        /** The deduced grid: {@link #UNKNOWN}, {@link #FILL} or {@link #EMPTY} per square. */
        public final byte[][] state;
        /** How many squares logic alone could not pin down. */
        public final int undetermined;
        /**
         * True when some line became impossible. Never happens for clues read off a real
         * picture, but it does happen the moment a solve is seeded with a player's marks
         * and one of those marks is wrong - see {@link #solveFrom}.
         */
        public final boolean contradicted;

        Result(byte[][] state, int undetermined, boolean contradicted) {
            this.state = state;
            this.undetermined = undetermined;
            this.contradicted = contradicted;
        }

        /** True when pure deduction filled in the whole board - the fairness guarantee. */
        public boolean solved() {
            return undetermined == 0 && !contradicted;
        }
    }

    private final int size;
    private final byte[][] state;
    private final int[][] rowClues;
    private final int[][] colClues;
    private final int[] rowClueCount;
    private final int[] colClueCount;
    private final boolean[] queued;
    private final int[] queue;

    // Scratch space reused by every line solve so a repair loop allocates nothing.
    private final byte[] line;
    private final int[] onEmpty;
    private final int[] onFill;
    private final int[] doneState;
    private final int[] gapState;
    private final int[] blockState;
    private final long[] forward;
    private final long[] backward;

    public NonogramSolver(int size) {
        if (size < 1 || size > 61) {
            throw new IllegalArgumentException("size must be 1..61");
        }
        this.size = size;
        this.state = new byte[size][size];
        this.rowClues = new int[size][(size + 1) / 2];
        this.colClues = new int[size][(size + 1) / 2];
        this.rowClueCount = new int[size];
        this.colClueCount = new int[size];
        this.queued = new boolean[size * 2];
        this.queue = new int[size * 2];
        this.line = new byte[size];
        this.onEmpty = new int[size + 3];
        this.onFill = new int[size + 3];
        this.doneState = new int[(size + 3) / 2];
        this.gapState = new int[(size + 3) / 2];
        this.blockState = new int[(size + 3) / 2];
        this.forward = new long[size + 1];
        this.backward = new long[size + 1];
    }

    /** Convenience for tests and one-off checks. */
    public static boolean uniquelyLineSolvable(boolean[][] picture) {
        return new NonogramSolver(picture.length).solve(picture).solved();
    }

    /**
     * Line-solves the board described by the clues of {@code picture} and reports how far
     * pure deduction got. The picture itself is only used to read the clues off; the
     * solver never peeks at it while deducing.
     */
    public Result solve(boolean[][] picture) {
        return solveFrom(picture, null);
    }

    /**
     * The same line solve, but starting from what the players have already worked out
     * instead of from an empty grid. {@code known} holds {@link #UNKNOWN}, {@link #FILL}
     * or {@link #EMPTY} per square, or is null to start from nothing.
     *
     * <p>This is what lets a hint offer the square <em>logic</em> would settle next rather
     * than whichever square happens to be nearest the cursor. A full 20x20 seeded solve
     * costs about a tenth of a millisecond, so the game can afford to ask.
     *
     * <p>Seeded deduction is only as good as the seed. If a mark is wrong the line it is
     * on may become impossible, and the result comes back {@link Result#contradicted}
     * rather than wrong; callers fall back to solving from scratch.
     */
    public Result solveFrom(boolean[][] picture, byte[][] known) {
        readClues(picture);
        for (int y = 0; y < size; y++) {
            if (known == null) {
                java.util.Arrays.fill(state[y], UNKNOWN);
            } else {
                System.arraycopy(known[y], 0, state[y], 0, size);
            }
        }

        boolean contradicted = false;
        int head = 0;
        int tail = 0;
        int pending = 0;
        for (int i = 0; i < size * 2; i++) {
            queue[tail] = i;
            tail = (tail + 1) % queue.length;
            queued[i] = true;
            pending++;
        }

        while (pending > 0) {
            int id = queue[head];
            head = (head + 1) % queue.length;
            pending--;
            queued[id] = false;

            boolean isRow = id < size;
            int index = isRow ? id : id - size;
            readLine(isRow, index);
            int[] clues = isRow ? rowClues[index] : colClues[index];
            int count = isRow ? rowClueCount[index] : colClueCount[index];
            if (!narrow(clues, count)) {
                // Cannot happen for clues taken from a real picture and an empty grid, but
                // a seeded solve reaches it as soon as one of the seeded marks is wrong.
                contradicted = true;
                break;
            }
            for (int at = 0; at < size; at++) {
                byte was = isRow ? state[index][at] : state[at][index];
                if (was == line[at] || line[at] == UNKNOWN) {
                    continue;
                }
                if (isRow) {
                    state[index][at] = line[at];
                } else {
                    state[at][index] = line[at];
                }
                int other = isRow ? size + at : at;
                if (!queued[other]) {
                    queued[other] = true;
                    queue[tail] = other;
                    tail = (tail + 1) % queue.length;
                    pending++;
                }
            }
        }

        int unknown = 0;
        byte[][] copy = new byte[size][size];
        for (int y = 0; y < size; y++) {
            System.arraycopy(state[y], 0, copy[y], 0, size);
            for (int x = 0; x < size; x++) {
                if (state[y][x] == UNKNOWN) {
                    unknown++;
                }
            }
        }
        return new Result(copy, unknown, contradicted);
    }

    // ---- Hinting ----------------------------------------------------------------------

    /**
     * The square pure logic would settle next, given what the players have marked so far:
     * {@code {x, y, state}} where state is {@link #FILL} or {@link #EMPTY}, or null when
     * there is nothing left to find.
     *
     * <p>Two things make this a better hint than "the nearest square that is not filled
     * in yet". It only ever offers a square the clues actually force <em>from where the
     * pair are now</em>, so the hint teaches the deduction rather than handing over an
     * answer nobody could have reached. And it can say "this one is empty" - roughly half
     * of all nonogram deductions are crosses, and a hint that can only ever fill squares
     * has to skip them.
     *
     * <p>Among the squares it could offer it picks the one in the row or column with the
     * fewest unknowns left, so a hint tends to be the square that finishes a line.
     *
     * <p>If the pair have made a mistake, the seeded solve contradicts itself or deduces
     * something the picture disagrees with; either way this falls back to solving from
     * scratch, which can still always find something while the board is unfinished.
     */
    public int[] nextDeduction(boolean[][] picture, byte[][] marks) {
        int[] found = bestOf(solveFrom(picture, marks), picture, marks);
        return found != null ? found : bestOf(solveFrom(picture, null), picture, marks);
    }

    private int[] bestOf(Result result, boolean[][] picture, byte[][] marks) {
        if (result.contradicted) {
            return null;
        }
        int[] rowLeft = new int[size];
        int[] colLeft = new int[size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (marks == null || marks[y][x] == UNKNOWN) {
                    rowLeft[y]++;
                    colLeft[x]++;
                }
            }
        }
        int bestX = -1;
        int bestY = -1;
        byte bestState = UNKNOWN;
        int bestScore = Integer.MAX_VALUE;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (marks != null && marks[y][x] != UNKNOWN) {
                    continue;
                }
                byte deduced = result.state[y][x];
                if (deduced == UNKNOWN) {
                    continue;
                }
                if ((deduced == FILL) != picture[y][x]) {
                    return null;                 // a mark lied; this whole solve is suspect
                }
                int score = Math.min(rowLeft[y], colLeft[x]);
                if (score < bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                    bestState = deduced;
                }
            }
        }
        return bestX < 0 ? null : new int[] {bestX, bestY, bestState};
    }

    // ---- Clue bookkeeping ------------------------------------------------------------

    private void readClues(boolean[][] picture) {
        for (int index = 0; index < size; index++) {
            int runs = 0;
            int run = 0;
            for (int x = 0; x < size; x++) {
                if (picture[index][x]) {
                    run++;
                } else if (run > 0) {
                    rowClues[index][runs++] = run;
                    run = 0;
                }
            }
            if (run > 0) {
                rowClues[index][runs++] = run;
            }
            rowClueCount[index] = runs;

            runs = 0;
            run = 0;
            for (int y = 0; y < size; y++) {
                if (picture[y][index]) {
                    run++;
                } else if (run > 0) {
                    colClues[index][runs++] = run;
                    run = 0;
                }
            }
            if (run > 0) {
                colClues[index][runs++] = run;
            }
            colClueCount[index] = runs;
        }
    }

    private void readLine(boolean isRow, int index) {
        if (isRow) {
            System.arraycopy(state[index], 0, line, 0, size);
        } else {
            for (int y = 0; y < size; y++) {
                line[y] = state[y][index];
            }
        }
    }

    // ---- One line ---------------------------------------------------------------------

    /**
     * Marks every square of {@link #line} that is forced by {@code clues}. Returns false
     * only if the line has become impossible, which a well-formed board never does.
     */
    private boolean narrow(int[] clues, int count) {
        int states = compile(clues, count);
        long accept = 1L << doneState[count];
        if (count > 0) {
            accept |= 1L << gapState[count];
        }

        forward[0] = 1L;
        for (int at = 0; at < size; at++) {
            long from = forward[at];
            long to = 0L;
            boolean mayBeEmpty = line[at] != FILL;
            boolean mayBeFilled = line[at] != EMPTY;
            while (from != 0) {
                int s = Long.numberOfTrailingZeros(from);
                from &= from - 1;
                if (mayBeEmpty && onEmpty[s] >= 0) {
                    to |= 1L << onEmpty[s];
                }
                if (mayBeFilled && onFill[s] >= 0) {
                    to |= 1L << onFill[s];
                }
            }
            forward[at + 1] = to;
            if (to == 0) {
                return false;
            }
        }
        if ((forward[size] & accept) == 0) {
            return false;
        }

        backward[size] = accept;
        for (int at = size - 1; at >= 0; at--) {
            long next = backward[at + 1];
            long here = 0L;
            boolean mayBeEmpty = line[at] != FILL;
            boolean mayBeFilled = line[at] != EMPTY;
            for (int s = 0; s < states; s++) {
                if ((mayBeEmpty && onEmpty[s] >= 0 && (next >> onEmpty[s] & 1) != 0)
                        || (mayBeFilled && onFill[s] >= 0 && (next >> onFill[s] & 1) != 0)) {
                    here |= 1L << s;
                }
            }
            backward[at] = here;
        }

        for (int at = 0; at < size; at++) {
            if (line[at] != UNKNOWN) {
                continue;
            }
            long live = forward[at] & backward[at];
            long next = backward[at + 1];
            boolean canBeEmpty = false;
            boolean canBeFilled = false;
            long from = live;
            while (from != 0 && !(canBeEmpty && canBeFilled)) {
                int s = Long.numberOfTrailingZeros(from);
                from &= from - 1;
                if (onEmpty[s] >= 0 && (next >> onEmpty[s] & 1) != 0) {
                    canBeEmpty = true;
                }
                if (onFill[s] >= 0 && (next >> onFill[s] & 1) != 0) {
                    canBeFilled = true;
                }
            }
            if (!canBeEmpty && !canBeFilled) {
                return false;
            }
            if (canBeEmpty != canBeFilled) {
                line[at] = canBeFilled ? FILL : EMPTY;
            }
        }
        return true;
    }

    /**
     * Turns clue numbers into the little automaton described in the class comment and
     * returns how many states it has. States are numbered so they fit in one long.
     */
    private int compile(int[] clues, int count) {
        int next = 0;
        for (int j = 0; j <= count; j++) {
            doneState[j] = next++;
        }
        for (int j = 1; j <= count; j++) {
            gapState[j] = next++;
        }
        for (int j = 1; j <= count; j++) {
            blockState[j] = next;
            next += clues[j - 1] - 1;
        }
        for (int s = 0; s < next; s++) {
            onEmpty[s] = -1;
            onFill[s] = -1;
        }
        for (int j = 0; j <= count; j++) {
            int s = doneState[j];
            onEmpty[s] = s;
            if (j < count) {
                onFill[s] = clues[j] == 1 ? gapState[j + 1] : blockState[j + 1];
            }
        }
        for (int j = 1; j <= count; j++) {
            onEmpty[gapState[j]] = doneState[j];
            for (int r = 1; r < clues[j - 1]; r++) {
                int s = blockState[j] + (r - 1);
                onFill[s] = r + 1 == clues[j - 1] ? gapState[j] : s + 1;
            }
        }
        return next;
    }
}
