package com.cozygrams.tv;

/**
 * Everything the game itself knows: which puzzle is on the table, where each player's
 * cursor sits, and how the session has progressed. Deliberately free of Android types
 * so the rules stay unit-testable.
 *
 * <h2>Who placed what</h2>
 *
 * <p>{@link Puzzle#marks} is a plain byte grid, so for two people sharing one board the
 * game could not tell Rose's square from Sky's. That was not a cosmetic gap. Pressing the
 * same mark twice clears it — the undo that makes a mistake harmless — and with no
 * ownership the same press also silently erased whatever the <em>other</em> player had
 * just put down, with the clear sound and the clear pulse, as if it had been your own.
 * {@link #resetTable} starts the two cursors on adjacent squares, so that collision is the
 * default opening position.
 *
 * <p>{@link #placedBy} and {@link #placedAt} answer both questions with one byte and one
 * long per square: whose it is, and how recently. {@link #wouldUndoPartner} is the fairness
 * rule built on them, and {@link #lineWasShared} is the happy one — the row the two of you
 * closed together, which is the most common genuinely shared event in a co-op nonogram and
 * which the game previously credited to whoever happened to place the last square.
 *
 * <p>Neither array is persisted. After a resume every square reads as nobody's, the grace
 * period cannot fire and no line counts as shared — which is correct, because by then
 * nobody at the table remembers who placed what either.
 */
public final class GameState {

    /** The next endless board is derived by stepping the seed by a large prime. */
    private static final long SEED_STEP = 104729L;

    public static final int MIN_SIZE = 5;
    public static final int MAX_SIZE = 20;

    /** {@link #placedBy} for a square nobody has claimed. */
    public static final byte NOBODY = 0;

    public Puzzle puzzle;
    public long seed;
    public int size;
    /** How many puzzles have been completed this session, across both modes. */
    public int solved;
    public boolean storyMode;
    public int storyIndex;
    /** Highest story chapter reached, so the book remembers the journey. */
    public int storyFurthest;
    /** One bit per chapter actually completed; visiting a page is not finishing it. */
    public long storyCompleted;

    public final int[] cursorX = {0, 1};
    public final int[] cursorY = {0, 0};
    public final int[] moves = {0, 0};

    /**
     * Who placed the mark on each square: {@link #NOBODY}, 1 for Rose, 2 for Sky.
     *
     * <p>Player + 1 rather than the player index so that a freshly cleared grid of zeroes
     * means "nobody", which is what {@code new byte[size][size]} already gives us.
     */
    public byte[][] placedBy;

    /** When each square was marked, on the caller's clock. Zero when nobody has. */
    public long[][] placedAt;

    /** When each player last placed or cleared a mark, and what kind it was. */
    public final long[] lastMarkAt = {0, 0};
    /** {@link Puzzle#FILLED}, {@link Puzzle#CROSSED}, {@link Puzzle#UNKNOWN} for a clear. */
    public final byte[] lastMarkKind = {Puzzle.UNKNOWN, Puzzle.UNKNOWN};

    /** Lines on this board that both players had a hand in. Never a per-player count. */
    public int sharedLines;

    public GameState(long seed, int size) {
        this.seed = seed;
        this.size = size;
        this.puzzle = PuzzleGenerator.generate(seed, size);
        resetTable();
    }

    // ---- Player actions ------------------------------------------------------------

    /** Moves a cursor by one square, wrapping around the edges of the board. */
    public void move(int player, int dx, int dy) {
        cursorX[player] = Math.floorMod(cursorX[player] + dx, size);
        cursorY[player] = Math.floorMod(cursorY[player] + dy, size);
    }

    /**
     * Applies a mark at the player's cursor. Pressing the same mark again clears the
     * square, so a mistake is always one button away from being undone.
     *
     * @param now the caller's clock, recorded so {@link #wouldUndoPartner} can tell a
     *            partner's fresh square from one that has been sitting there all evening
     */
    public void mark(int player, byte mark, long now) {
        int x = cursorX[player];
        int y = cursorY[player];
        boolean clearing = puzzle.marks[y][x] == mark;
        puzzle.marks[y][x] = clearing ? Puzzle.UNKNOWN : mark;
        claim(player, x, y, clearing ? Puzzle.UNKNOWN : mark, now);
        moves[player]++;
    }

    /**
     * The same, for a caller with no clock of its own — the unit tests and the screenshot
     * harness. The square is still attributed, but with no timestamp {@link
     * #wouldUndoPartner} can never fire for it, which is the safe direction: the grace
     * period is a courtesy that fails open rather than a lock that fails shut.
     */
    public void mark(int player, byte mark) {
        mark(player, mark, 0);
    }

    /**
     * Puts a square back the way it was, for the hint that is being held down and then let
     * go of. The move count comes back with it, so the invariant that a move is a thing
     * the player did lives here rather than in two files.
     *
     * <p>{@code CozyGameView.holdForHint} used to write {@code
     * game.puzzle.marks[holdY[who]][holdX[who]]} and decrement {@code game.moves[who]} by
     * hand. That left {@link #placedBy} still naming the player as the owner of a square
     * whose mark had just been taken away, which is exactly the stale entry
     * {@link #wouldUndoPartner} would then have read.
     */
    public void undoMark(int player, int x, int y, byte previous) {
        puzzle.marks[y][x] = previous;
        placedBy[y][x] = NOBODY;
        placedAt[y][x] = 0;
        moves[player] = Math.max(0, moves[player] - 1);
    }

    /**
     * Crosses a square out on the player's behalf — gentle mode's soft correction, which
     * lands on a square the player did not aim at.
     *
     * <p>{@code CozyGameView.markWrongSquare} used to write {@code
     * game.puzzle.marks[y][x] = Puzzle.CROSSED} and increment {@code game.moves[who]} by
     * hand, so gentle mode's crosses were the one kind of mark nobody owned; same reason
     * as {@link #undoMark}.
     */
    public void crossOut(int player, int x, int y, long now) {
        puzzle.marks[y][x] = Puzzle.CROSSED;
        claim(player, x, y, Puzzle.CROSSED, now);
        moves[player]++;
    }

    /** Records who a square now belongs to. A cleared square belongs to nobody again. */
    private void claim(int player, int x, int y, byte mark, long now) {
        boolean cleared = mark == Puzzle.UNKNOWN;
        placedBy[y][x] = cleared ? NOBODY : (byte) (player + 1);
        placedAt[y][x] = cleared ? 0 : now;
        lastMarkAt[player] = now;
        lastMarkKind[player] = mark;
    }

    /** The mark currently under a player's cursor. */
    public byte markUnder(int player) {
        return puzzle.marks[cursorY[player]][cursorX[player]];
    }

    /**
     * True when this player's fill would rub out a square their partner put down moments
     * ago, rather than one of their own.
     *
     * <p>The window is {@link Theme#PARTNER_GRACE_MS}, not forever: after a minute and a
     * half a filled square is simply part of the picture and either player must be able to
     * tidy it. Inside the window the caller is expected to keep the square, say something
     * warm and leave the two of them agreeing rather than undoing each other.
     */
    public boolean wouldUndoPartner(int player, long now) {
        int x = cursorX[player];
        int y = cursorY[player];
        byte owner = placedBy[y][x];
        return markUnder(player) == Puzzle.FILLED
                && owner != NOBODY
                && owner != (byte) (player + 1)
                && placedAt[y][x] != 0
                && now - placedAt[y][x] <= Theme.PARTNER_GRACE_MS;
    }

    /**
     * True when both players' hands are in the completed line through {@code (x, y)}.
     *
     * <p>Asked of the filled squares only: a cross is a deduction about where the picture
     * is not, and crossing out someone else's row is not the same as building it with
     * them.
     */
    public boolean lineWasShared(int x, int y, boolean row) {
        boolean rose = false;
        boolean sky = false;
        for (int step = 0; step < size; step++) {
            int cx = row ? step : x;
            int cy = row ? y : step;
            if (puzzle.marks[cy][cx] != Puzzle.FILLED) {
                continue;
            }
            rose |= placedBy[cy][cx] == 1;
            sky |= placedBy[cy][cx] == 2;
        }
        return rose && sky;
    }

    /** True when both cursors are on the same square. */
    public boolean sharingASquare() {
        return cursorX[0] == cursorX[1] && cursorY[0] == cursorY[1];
    }

    /**
     * True when the two cursors are touching — the same square or one of the eight around
     * it.
     *
     * <p>Measured on the logical squares rather than on the eased drawing positions
     * {@code CursorRenderer} uses for the heart and the tether. The two answers differ for
     * about 120 ms after a step, and that is the right difference: a sound belongs to the
     * moment you arrive, a glow to the pixels that are actually adjacent.
     */
    public boolean sideBySide() {
        return Math.abs(cursorX[0] - cursorX[1]) <= 1
                && Math.abs(cursorY[0] - cursorY[1]) <= 1;
    }

    /**
     * Reveals the next picture square, searching outward from the player's cursor so the
     * help always appears somewhere they were already looking. Returns false when the
     * picture is already fully found.
     */
    public boolean hint(int player, long now) {
        int cells = size * size;
        int start = cursorY[player] * size + cursorX[player];
        for (int step = 0; step < cells; step++) {
            int at = (start + step) % cells;
            int y = at / size;
            int x = at % size;
            if (puzzle.solution[y][x] && puzzle.marks[y][x] != Puzzle.FILLED) {
                puzzle.marks[y][x] = Puzzle.FILLED;
                claim(player, x, y, Puzzle.FILLED, now);
                cursorX[player] = x;
                cursorY[player] = y;
                moves[player]++;
                return true;
            }
        }
        return false;
    }

    /** The same, for a caller with no clock. See {@link #mark(int, byte)}. */
    public boolean hint(int player) {
        return hint(player, 0);
    }

    // ---- Mode changes --------------------------------------------------------------

    public void startStory(int index) {
        storyMode = true;
        storyIndex = Math.floorMod(index, PuzzleLibrary.count());
        storyFurthest = Math.max(storyFurthest, storyIndex);
        puzzle = PuzzleLibrary.get(storyIndex);
        size = puzzle.size;
        resetTable();
    }

    public void startEndless(long newSeed, int newSize) {
        storyMode = false;
        seed = newSeed;
        size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, newSize));
        puzzle = PuzzleGenerator.generate(seed, size);
        resetTable();
    }

    /** Advances to the next board in whichever mode is running. */
    public void next() {
        completeCurrentStoryChapter();
        solved++;
        if (storyMode) {
            startStory(storyIndex + 1);
        } else {
            startEndless(seed + SEED_STEP, size);
        }
    }

    /** Records the current chapter as complete. Safe to call again when leaving its win card. */
    public void completeCurrentStoryChapter() {
        if (storyMode && storyIndex >= 0 && storyIndex < Long.SIZE) {
            storyCompleted |= 1L << storyIndex;
        }
    }

    public boolean storyChapterComplete(int chapter) {
        return chapter >= 0 && chapter < Long.SIZE
                && (storyCompleted & (1L << chapter)) != 0;
    }

    public int storyCompleteCount() {
        long valid = PuzzleLibrary.count() >= Long.SIZE ? -1L
                : (1L << PuzzleLibrary.count()) - 1;
        return Long.bitCount(storyCompleted & valid);
    }

    public boolean storyBookComplete() {
        return storyCompleteCount() >= PuzzleLibrary.count();
    }

    /** Migration for saves from before completion had its own field. */
    public static long completedPrefix(int chapters) {
        int count = Math.max(0, Math.min(Long.SIZE, chapters));
        return count == Long.SIZE ? -1L : (1L << count) - 1;
    }

    /**
     * Puts both cursors back at the top-left, clears the per-board counts and hands out a
     * fresh, correctly sized pair of attribution grids.
     *
     * <p>Every path that can change {@link #size} ends here — the constructor,
     * {@link #startStory} and {@link #startEndless} — which is what keeps
     * {@link #placedBy} the same shape as {@link Puzzle#marks}.
     */
    private void resetTable() {
        cursorX[0] = 0;
        cursorY[0] = 0;
        cursorX[1] = Math.min(1, size - 1);
        cursorY[1] = 0;
        moves[0] = 0;
        moves[1] = 0;
        lastMarkAt[0] = 0;
        lastMarkAt[1] = 0;
        lastMarkKind[0] = Puzzle.UNKNOWN;
        lastMarkKind[1] = Puzzle.UNKNOWN;
        sharedLines = 0;
        placedBy = new byte[size][size];
        placedAt = new long[size][size];
    }

    /** Combined move count, shown on the win card as a shared achievement. */
    public int totalMoves() {
        return moves[0] + moves[1];
    }

    /** Fraction of the hidden picture that has been found, in the range 0..1. */
    public float pictureProgress() {
        int total = puzzle.pictureCount();
        return total == 0 ? 1f : puzzle.foundCount() / (float) total;
    }
}
