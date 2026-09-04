package com.cozygrams.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/**
 * Remembers where the evening left off, and a little about the evenings before it.
 *
 * <p>Three ideas run through this class.
 *
 * <p><b>A save is only trusted if it still describes the picture on the table.</b> The
 * board is stored as one digit per square, which keeps a 20x20 game to 400 bytes, and
 * alongside it we store {@link #SCHEMA} and a fingerprint of the hidden picture. On load
 * the puzzle is rebuilt first and its fingerprint recomputed; only if it matches are the
 * marks put back. A stale file, a changed generator, a truncated string or a size that no
 * longer agrees with the marks all land in the same gentle place: a fresh board.
 *
 * <p><b>Nothing a broken preferences file contains may crash the game.</b> Every read
 * goes through a guarded helper, because {@code getInt} on a key that holds a String
 * throws {@link ClassCastException}, and a value written by a future build could be
 * anything at all.
 *
 * <p><b>Writing is cheap enough to do after every square.</b> Identical saves are
 * dropped, and bursts are coalesced into at most one write per
 * {@value #COALESCE_MS} ms with a guaranteed trailing write, so callers never have to
 * think about how often they call {@link #save}.
 */
public final class SaveStore {

    /**
     * The shape of the saved board. Bump this whenever a stored key changes meaning; the
     * old board is then quietly discarded instead of being misread.
     */
    public static final int SCHEMA = 2;

    /** Writes are coalesced into at most one per this many milliseconds. */
    private static final long COALESCE_MS = 1000;

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private static final String FILE = "save";

    private static final String KEY_SCHEMA = "schema";
    private static final String KEY_SEED = "seed";
    private static final String KEY_SIZE = "size";
    private static final String KEY_SOLVED = "solved";
    private static final String KEY_MOVES_0 = "moves0";
    private static final String KEY_MOVES_1 = "moves1";
    private static final String KEY_STORY_MODE = "storyMode";
    private static final String KEY_STORY_INDEX = "storyIndex";
    private static final String KEY_STORY_FURTHEST = "storyFurthest";
    private static final String KEY_STORY_COMPLETED = "storyCompleted";
    private static final String KEY_MARKS = "marks";
    private static final String KEY_PICTURE = "picture";
    private static final String KEY_NEXT_SIZE = "nextSize";

    private static final String KEY_FINISHED = "finished";
    private static final String KEY_CHAPTERS = "chapters";
    private static final String KEY_SQUARES_0 = "squares0";
    private static final String KEY_SQUARES_1 = "squares1";
    private static final String KEY_LAST_PLAYED = "lastPlayed";
    private static final String KEY_VISITS = "visits";

    private static final String KEY_MUSIC = "music";
    private static final String KEY_SFX = "sfx";
    private static final String KEY_GENTLE = "gentle";
    private static final String KEY_HINTS = "hints";
    private static final String KEY_BIG_TEXT = "bigText";
    private static final String KEY_CONTRAST = "contrast";
    private static final String KEY_DISTINCT = "distinctPlayers";
    private static final String KEY_BOLD_CURSOR = "boldCursor";
    private static final String KEY_CALM_MOTION = "calmMotion";

    // The look the game ships with lives on UiState, so a first run and "put everything
    // back" can never drift apart, and so the drawing code never has to know about
    // storage in order to reset an option.
    private static final boolean DEFAULT_MUSIC = UiState.DEFAULT_MUSIC;
    private static final boolean DEFAULT_SFX = UiState.DEFAULT_SFX;
    private static final boolean DEFAULT_GENTLE = UiState.DEFAULT_GENTLE;
    private static final boolean DEFAULT_HINTS = UiState.DEFAULT_HINTS;
    private static final boolean DEFAULT_BIG_TEXT = UiState.DEFAULT_BIG_TEXT;
    private static final boolean DEFAULT_CONTRAST = UiState.DEFAULT_CONTRAST;

    private final SharedPreferences prefs;
    private final Handler handler;
    private final Journey journey = new Journey();

    /** Move counts already folded into the journey, so a save never double-counts. */
    private final int[] countedMoves = {0, 0};

    private String lastWritten = "";
    private long lastWriteAt;
    private boolean writeScheduled;
    private boolean startedFresh;

    /**
     * A board size chosen while a picture was in progress, waiting for the next one.
     *
     * <p>It lives here rather than only in the view because it is a choice the player made
     * and would notice losing: stepping BOARD SIZE to 15x15 halfway through a picture and
     * then turning the television off used to throw the choice away silently, while the
     * board it was waiting for came back intact. Zero means "no pending size", which is
     * also what an absent key reads as — so this needs no {@link #SCHEMA} bump.
     */
    private int pendingSize;

    public SaveStore(Context context) {
        this.prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Looper main = Looper.getMainLooper();
        this.handler = main == null ? null : new Handler(main);
    }

    // ---- Loading ---------------------------------------------------------------------

    /**
     * Builds the game from whatever was saved. Anything we cannot vouch for is replaced
     * by a fresh board rather than being forced onto the wrong picture.
     */
    public GameState loadGame() {
        boolean trusted = readInt(KEY_SCHEMA, 0) == SCHEMA;

        long seed = readLong(KEY_SEED, System.currentTimeMillis());
        int size = clampSize(readInt(KEY_SIZE, GameState.MIN_SIZE));
        int chapters = PuzzleLibrary.count();
        int storyIndex = clampStoryIndex(readInt(KEY_STORY_INDEX, 0), chapters);

        GameState game = new GameState(seed, size);
        if (readBoolean(KEY_STORY_MODE, false)) {
            game.startStory(storyIndex);
        }
        game.solved = atLeastZero(readInt(KEY_SOLVED, 0));
        // Endless play still remembers the chapter, so the story book resumes in place.
        game.storyIndex = storyIndex;
        game.storyFurthest = Math.max(game.storyIndex,
                clampStoryIndex(readInt(KEY_STORY_FURTHEST, game.storyIndex), chapters));
        game.storyCompleted = readLong(KEY_STORY_COMPLETED,
                GameState.completedPrefix(game.storyFurthest));

        startedFresh = !trusted || !restoreBoard(game);
        // Kept in story mode too, deliberately: a size banked while a chapter was open is
        // the pair asking to leave the book after it, and dropping it here would make the
        // book a one-way door again for anyone who turned the television off first.
        pendingSize = trusted ? clampPendingSize(readInt(KEY_NEXT_SIZE, 0)) : 0;
        loadJourney();
        return game;
    }

    /**
     * A pending size, or 0. Anything outside the board sizes the game deals is dropped
     * rather than clamped: a value we cannot vouch for is not a choice the player made,
     * and the honest answer to "which size did they pick" is then "none".
     */
    static int clampPendingSize(int size) {
        return size < GameState.MIN_SIZE || size > GameState.MAX_SIZE ? 0 : size;
    }

    /** The size the next endless picture should use, or 0 when none was chosen. */
    public int pendingSize() {
        return pendingSize;
    }

    /**
     * Records a size chosen for the next picture. It is part of {@link #digest}, ahead of
     * the newline, so choosing one is written straight away rather than coalesced with the
     * squares.
     */
    public void setPendingSize(int size) {
        pendingSize = clampPendingSize(size);
    }

    /**
     * Puts the saved marks back, but only when the fingerprint proves they belong to this
     * exact picture. Returns false when the board had to be left empty.
     */
    private boolean restoreBoard(GameState game) {
        if (!fingerprint(game.puzzle).equals(readString(KEY_PICTURE, ""))) {
            return false;
        }
        byte[][] marks = decodeMarks(readString(KEY_MARKS, ""), game.size);
        if (marks == null) {
            return false;
        }
        for (int y = 0; y < game.size; y++) {
            System.arraycopy(marks[y], 0, game.puzzle.marks[y], 0, game.size);
        }
        game.moves[0] = atLeastZero(readInt(KEY_MOVES_0, 0));
        game.moves[1] = atLeastZero(readInt(KEY_MOVES_1, 0));
        countedMoves[0] = game.moves[0];
        countedMoves[1] = game.moves[1];
        return true;
    }

    private void loadJourney() {
        journey.puzzlesFinished = atLeastZero(readInt(KEY_FINISHED, 0));
        journey.chaptersFinished = atLeastZero(readInt(KEY_CHAPTERS, 0));
        journey.squares[0] = atLeastZero(readInt(KEY_SQUARES_0, 0));
        journey.squares[1] = atLeastZero(readInt(KEY_SQUARES_1, 0));
        journey.previousVisitAt = readLong(KEY_LAST_PLAYED, 0);
        journey.lastPlayedAt = journey.previousVisitAt;
        journey.visits = atLeastZero(readInt(KEY_VISITS, 0)) + 1;
    }

    public void loadSettings(UiState ui) {
        ui.musicOn = readBoolean(KEY_MUSIC, DEFAULT_MUSIC);
        ui.sfxOn = readBoolean(KEY_SFX, DEFAULT_SFX);
        ui.gentleCheck = readBoolean(KEY_GENTLE, DEFAULT_GENTLE);
        ui.hintsOn = readBoolean(KEY_HINTS, DEFAULT_HINTS);
        ui.bigTextOn = readBoolean(KEY_BIG_TEXT, DEFAULT_BIG_TEXT);
        ui.highContrastOn = readBoolean(KEY_CONTRAST, DEFAULT_CONTRAST);

        Comfort comfort = Comfort.get();
        comfort.distinctPlayers = readBoolean(KEY_DISTINCT, false);
        comfort.boldCursor = readBoolean(KEY_BOLD_CURSOR, false);
        comfort.calmMotion = readBoolean(KEY_CALM_MOTION, false);
    }

    /**
     * Puts every option back to the look the game ships with. Kept as a delegate so the
     * save layer stays the one place callers have to know about, while the reset itself
     * lives with the state it resets.
     */
    public static void restoreDefaults(UiState ui) {
        ui.restoreDefaults();
    }

    /**
     * True when no saved board was put back — a first evening, or a save we could not
     * vouch for. Worth a kind word on screen rather than a silent empty grid.
     */
    public boolean startedFresh() {
        return startedFresh;
    }

    /** Everything we remember about the evenings so far. */
    public Journey journey() {
        return journey;
    }

    /** A warm line for the title screen, e.g. "Back after 3 days — 12 pictures together". */
    public String welcomeBack(long now) {
        return welcomeBack(journey, now);
    }

    // ---- Saving ----------------------------------------------------------------------

    /**
     * Remembers the current state. Safe to call after every single square: an unchanged
     * state writes nothing, and a burst of changes is folded into one write.
     */
    public void save(GameState game, UiState ui) {
        store(game, ui, false);
    }

    /**
     * Writes straight away, skipping the coalescing window. Call this when the game is
     * about to lose focus, so a pending write can never be left behind.
     */
    public void flush(GameState game, UiState ui) {
        store(game, ui, true);
    }

    private void store(GameState game, UiState ui, boolean immediate) {
        accrueJourney(game);
        String digest = digest(game, ui, journey, pendingSize);
        if (digest.equals(lastWritten)) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean onlySquares = beforeTheBoard(digest).equals(beforeTheBoard(lastWritten));
        if (!immediate && onlySquares && lastWriteAt != 0
                && now - lastWriteAt < COALESCE_MS) {
            scheduleTrailingWrite(game, ui, COALESCE_MS - (now - lastWriteAt));
            return;
        }
        write(game, ui, digest, now);
    }

    /**
     * Makes sure a coalesced change still reaches disk. Only one write is ever pending,
     * and it always writes the newest state rather than the one that was dropped.
     */
    private void scheduleTrailingWrite(GameState game, UiState ui, long delay) {
        if (handler == null) {
            write(game, ui, digest(game, ui, journey, pendingSize),
                    System.currentTimeMillis());
            return;
        }
        if (writeScheduled) {
            return;
        }
        writeScheduled = true;
        handler.postDelayed(() -> {
            writeScheduled = false;
            store(game, ui, true);
        }, Math.max(1, delay));
    }

    private void write(GameState game, UiState ui, String digest, long now) {
        journey.lastPlayedAt = now;
        Comfort comfort = Comfort.get();
        prefs.edit()
                .putInt(KEY_SCHEMA, SCHEMA)
                .putLong(KEY_SEED, game.seed)
                .putInt(KEY_SIZE, game.size)
                .putInt(KEY_SOLVED, game.solved)
                .putInt(KEY_MOVES_0, game.moves[0])
                .putInt(KEY_MOVES_1, game.moves[1])
                .putBoolean(KEY_STORY_MODE, game.storyMode)
                .putInt(KEY_STORY_INDEX, game.storyIndex)
                .putInt(KEY_STORY_FURTHEST, game.storyFurthest)
                .putLong(KEY_STORY_COMPLETED, game.storyCompleted)
                .putString(KEY_MARKS, encodeMarks(game.puzzle.marks))
                .putString(KEY_PICTURE, fingerprint(game.puzzle))
                .putInt(KEY_NEXT_SIZE, pendingSize)
                .putInt(KEY_FINISHED, journey.puzzlesFinished)
                .putInt(KEY_CHAPTERS, journey.chaptersFinished)
                .putInt(KEY_SQUARES_0, journey.squares[0])
                .putInt(KEY_SQUARES_1, journey.squares[1])
                .putLong(KEY_LAST_PLAYED, journey.lastPlayedAt)
                .putInt(KEY_VISITS, journey.visits)
                .putBoolean(KEY_MUSIC, ui.musicOn)
                .putBoolean(KEY_SFX, ui.sfxOn)
                .putBoolean(KEY_GENTLE, ui.gentleCheck)
                .putBoolean(KEY_HINTS, ui.hintsOn)
                .putBoolean(KEY_BIG_TEXT, ui.bigTextOn)
                .putBoolean(KEY_CONTRAST, ui.highContrastOn)
                .putBoolean(KEY_DISTINCT, comfort.distinctPlayers)
                .putBoolean(KEY_BOLD_CURSOR, comfort.boldCursor)
                .putBoolean(KEY_CALM_MOTION, comfort.calmMotion)
                .apply();
        lastWritten = digest;
        lastWriteAt = now;
    }

    /**
     * Folds whatever has happened since the last save into the lifetime counters. Per
     * board move counts reset to zero when a new picture is dealt, so a drop in the count
     * is read as "a fresh board", not as negative progress.
     */
    private void accrueJourney(GameState game) {
        for (int player = 0; player < 2; player++) {
            journey.squares[player] += deltaMoves(countedMoves[player], game.moves[player]);
            countedMoves[player] = game.moves[player];
        }
        journey.puzzlesFinished = Math.max(journey.puzzlesFinished, game.solved);
        journey.chaptersFinished = Math.max(journey.chaptersFinished,
                game.storyCompleteCount());
    }

    // ---- Pure helpers ----------------------------------------------------------------
    // Everything below is plain data in, plain data out, so it can be tested on a normal
    // JVM where SharedPreferences does nothing at all.

    /** One digit per square, row by row. Anything unexpected is written out as blank. */
    static String encodeMarks(byte[][] marks) {
        if (marks == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(marks.length * marks.length);
        for (byte[] row : marks) {
            for (byte mark : row) {
                boolean known = mark >= Puzzle.UNKNOWN && mark <= Puzzle.CROSSED;
                out.append((char) ('0' + (known ? mark : Puzzle.UNKNOWN)));
            }
        }
        return out.toString();
    }

    /**
     * Turns a saved board back into marks, or returns null when the text cannot possibly
     * describe a board of this size: wrong length, an impossible size, or any character
     * that is not one of the three marks.
     */
    static byte[][] decodeMarks(String text, int size) {
        if (text == null || size < GameState.MIN_SIZE || size > GameState.MAX_SIZE) {
            return null;
        }
        if (text.length() != size * size) {
            return null;
        }
        byte[][] marks = new byte[size][size];
        for (int i = 0; i < text.length(); i++) {
            int value = text.charAt(i) - '0';
            if (value < Puzzle.UNKNOWN || value > Puzzle.CROSSED) {
                return null;
            }
            marks[i / size][i % size] = (byte) value;
        }
        return marks;
    }

    static int clampSize(int size) {
        return Math.max(GameState.MIN_SIZE, Math.min(GameState.MAX_SIZE, size));
    }

    /** Wraps a chapter number into the book, matching what {@code startStory} does. */
    static int clampStoryIndex(int index, int chapters) {
        return chapters <= 0 ? 0 : Math.floorMod(index, chapters);
    }

    static int atLeastZero(int value) {
        return Math.max(0, value);
    }

    /**
     * How many squares were marked since the last save. A count that has gone backwards
     * means a new board was dealt, so the new count is the whole of the progress.
     */
    static int deltaMoves(int previous, int current) {
        if (current < 0) {
            return 0;
        }
        return current >= previous ? current - previous : current;
    }

    /**
     * A short signature of the hidden picture. If a future build generates a different
     * board for the same seed, or the story art is edited, the signature changes and the
     * old marks are dropped instead of being painted onto the wrong picture.
     */
    static String fingerprint(Puzzle puzzle) {
        return puzzle == null ? ""
                : fingerprint(puzzle.size, puzzle.name, puzzle.solution);
    }

    static String fingerprint(int size, String name, boolean[][] solution) {
        long hash = 0xcbf29ce484222325L;
        hash = fold(hash, size);
        if (name != null) {
            for (int i = 0; i < name.length(); i++) {
                hash = fold(hash, name.charAt(i));
            }
        }
        if (solution != null) {
            for (boolean[] row : solution) {
                if (row == null) {
                    continue;
                }
                for (boolean on : row) {
                    hash = fold(hash, on ? 1 : 0);
                }
            }
        }
        return Long.toHexString(hash);
    }

    /** FNV-1a over the four bytes of a value; small, stable and dependency free. */
    private static long fold(long hash, int value) {
        long result = hash;
        for (int shift = 0; shift < 32; shift += 8) {
            result = (result ^ ((value >>> shift) & 0xff)) * 0x100000001b3L;
        }
        return result;
    }

    /**
     * Everything a write would put on disk, as one string: first the parts that only move
     * when something notable happens, then a newline, then the parts that change with
     * every single square.
     *
     * <p>Comparing the whole string against the previous write is how an unchanged save
     * costs nothing. Comparing only {@link #beforeTheBoard} is how a burst of marking is
     * told apart from a setting being changed, so the burst can be coalesced while
     * anything the player would notice losing is written straight away. The clock is
     * deliberately left out, so "nothing happened" stays equal to itself.
     *
     * @param pendingSize the size waiting for the next picture, 0 for none. It sits ahead
     *                    of the newline because choosing one is a decision, not a square.
     */
    static String digest(GameState game, UiState ui, Journey journey, int pendingSize) {
        Comfort comfort = Comfort.get();
        StringBuilder out = new StringBuilder(600);
        out.append(SCHEMA).append('|')
                .append(game.seed).append('|')
                .append(game.size).append('|')
                .append(pendingSize).append('|')
                .append(game.solved).append('|')
                .append(game.storyMode ? 1 : 0).append('|')
                .append(game.storyIndex).append('|')
                .append(game.storyFurthest).append('|')
                .append(game.storyCompleted).append('|')
                .append(journey.puzzlesFinished).append('|')
                .append(journey.chaptersFinished).append('|')
                .append(journey.visits).append('|')
                .append(ui.musicOn ? 1 : 0)
                .append(ui.sfxOn ? 1 : 0)
                .append(ui.gentleCheck ? 1 : 0)
                .append(ui.hintsOn ? 1 : 0)
                .append(ui.bigTextOn ? 1 : 0)
                .append(ui.highContrastOn ? 1 : 0)
                .append(comfort.distinctPlayers ? 1 : 0)
                .append(comfort.boldCursor ? 1 : 0)
                .append(comfort.calmMotion ? 1 : 0)
                .append('\n')
                .append(game.moves[0]).append('|')
                .append(game.moves[1]).append('|')
                .append(journey.squares[0]).append('|')
                .append(journey.squares[1]).append('|')
                .append(encodeMarks(game.puzzle.marks));
        return out.toString();
    }

    /** The half of a digest that a single square never touches. */
    static String beforeTheBoard(String digest) {
        int split = digest.indexOf('\n');
        return split < 0 ? digest : digest.substring(0, split);
    }

    // ---- The journey -----------------------------------------------------------------

    /** What the game remembers about the evenings behind it. */
    public static final class Journey {
        /** Pictures finished across every session, in both modes. */
        public int puzzlesFinished;
        /** How far through the story book the pair have travelled. */
        public int chaptersFinished;
        /** Squares each of Rose and Sky has marked, ever. */
        public final int[] squares = {0, 0};
        /** How many times the game has been opened, including this one. */
        public int visits;
        /** When this session last wrote something down. */
        public long lastPlayedAt;
        /** When the previous session left off, for "it's been a while". */
        public long previousVisitAt;
    }

    /**
     * A greeting for the title screen. Warm, never a statistic for its own sake: it says
     * how long it has been and what the pair have made together.
     */
    static String welcomeBack(Journey journey, long now) {
        if (journey == null || journey.visits <= 1 || journey.previousVisitAt <= 0) {
            return "Welcome — let's find a picture together";
        }
        return timeAway(now - journey.previousVisitAt) + tally(journey);
    }

    static String timeAway(long millis) {
        long days = millis / DAY_MS;
        if (millis < 0 || days <= 0) {
            return "Welcome back";
        }
        if (days == 1) {
            return "Back the very next evening";
        }
        if (days < 7) {
            return "Back after " + days + " days";
        }
        if (days < 60) {
            long weeks = days / 7;
            return weeks == 1 ? "Back after a week away" : "Back after " + weeks + " weeks";
        }
        return "It's been a while — the room kept your seat warm";
    }

    static String tally(Journey journey) {
        int finished = journey.puzzlesFinished;
        if (finished <= 0) {
            return "";
        }
        return " — " + (finished == 1 ? "one picture" : finished + " pictures")
                + " finished together";
    }

    /** How the two players have shared the work, for the title screen or the win card. */
    static String shareLine(Journey journey) {
        int rose = journey.squares[0];
        int sky = journey.squares[1];
        if (rose + sky <= 0) {
            return "";
        }
        if (sky == 0) {
            return "Rose has placed " + rose + " squares so far";
        }
        if (rose == 0) {
            return "Sky has placed " + sky + " squares so far";
        }
        return "Rose " + rose + "  ·  Sky " + sky + " — squares placed together";
    }

    // ---- Guarded reads ---------------------------------------------------------------

    /**
     * A preferences file can hold a value of any type under any key — because an older
     * build wrote it, because a future build will, or because the file was edited by
     * hand. {@code getInt} on a key holding a String throws {@link ClassCastException},
     * which would take the whole game down before the first frame. Every read comes
     * through here instead and quietly falls back; the next write puts the key right.
     */
    interface Read<T> {
        T get();
    }

    static <T> T guarded(Read<T> read, T fallback) {
        try {
            T value = read.get();
            return value == null ? fallback : value;
        } catch (ClassCastException wrongType) {
            return fallback;
        }
    }

    private int readInt(String key, int fallback) {
        return guarded(() -> prefs.getInt(key, fallback), fallback);
    }

    private long readLong(String key, long fallback) {
        return guarded(() -> prefs.getLong(key, fallback), fallback);
    }

    private boolean readBoolean(String key, boolean fallback) {
        return guarded(() -> prefs.getBoolean(key, fallback), fallback);
    }

    private String readString(String key, String fallback) {
        return guarded(() -> prefs.getString(key, fallback), fallback);
    }
}
