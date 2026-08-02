package com.cozygrams.tv;

/**
 * Everything the game itself knows: which puzzle is on the table, where each player's
 * cursor sits, and how the session has progressed. Deliberately free of Android types
 * so the rules stay unit-testable.
 */
public final class GameState {

    /** The next endless board is derived by stepping the seed by a large prime. */
    private static final long SEED_STEP = 104729L;

    public static final int MIN_SIZE = 5;
    public static final int MAX_SIZE = 20;

    public Puzzle puzzle;
    public long seed;
    public int size;
    /** How many puzzles have been completed this session, across both modes. */
    public int solved;
    public boolean storyMode;
    public int storyIndex;
    /** Highest story chapter reached, so the book remembers the journey. */
    public int storyFurthest;

    public final int[] cursorX = {0, 1};
    public final int[] cursorY = {0, 0};
    public final int[] moves = {0, 0};

    public GameState(long seed, int size) {
        this.seed = seed;
        this.size = size;
        this.puzzle = PuzzleGenerator.generate(seed, size);
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
     */
    public void mark(int player, byte mark) {
        int x = cursorX[player];
        int y = cursorY[player];
        puzzle.marks[y][x] = puzzle.marks[y][x] == mark ? Puzzle.UNKNOWN : mark;
        moves[player]++;
    }

    /** The mark currently under a player's cursor. */
    public byte markUnder(int player) {
        return puzzle.marks[cursorY[player]][cursorX[player]];
    }

    /**
     * Reveals the next picture square, searching outward from the player's cursor so the
     * help always appears somewhere they were already looking. Returns false when the
     * picture is already fully found.
     */
    public boolean hint(int player) {
        int cells = size * size;
        int start = cursorY[player] * size + cursorX[player];
        for (int step = 0; step < cells; step++) {
            int at = (start + step) % cells;
            int y = at / size;
            int x = at % size;
            if (puzzle.solution[y][x] && puzzle.marks[y][x] != Puzzle.FILLED) {
                puzzle.marks[y][x] = Puzzle.FILLED;
                cursorX[player] = x;
                cursorY[player] = y;
                moves[player]++;
                return true;
            }
        }
        return false;
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
        solved++;
        if (storyMode) {
            startStory(storyIndex + 1);
        } else {
            startEndless(seed + SEED_STEP, size);
        }
    }

    /** Puts both cursors back at the top-left and clears the per-board move counts. */
    private void resetTable() {
        cursorX[0] = 0;
        cursorY[0] = 0;
        cursorX[1] = Math.min(1, size - 1);
        cursorY[1] = 0;
        moves[0] = 0;
        moves[1] = 0;
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
