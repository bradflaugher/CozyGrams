package com.cozygrams.tv;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import java.util.HashSet;
import java.util.Set;

/**
 * The lean-back surface: routes controller input into the game, keeps the animation
 * clock running, and hands each frame to {@link Renderer}.
 */
public final class CozyGameView extends View {

    /**
     * Bounds on the frame gap handed to {@link UiState#animateCursors}, which eases in real
     * milliseconds and so believes whatever the clock says.
     *
     * <p>The ceiling is what stops a resumed view teleporting: a television that has been
     * asleep for a minute reports sixty thousand milliseconds on its first frame back, and
     * an exponential closes any gap completely long before that. Sixty-four milliseconds is
     * four 60 Hz frames — slow enough to be a real stutter, short enough that the glide
     * still reads as a glide.
     *
     * <p>The floor is for the opposite end. {@link SystemClock#uptimeMillis} counts whole
     * milliseconds, and the input path calls {@code invalidate()} on every key, so two draws
     * can land inside one tick and report a gap of zero — at which point the cursor stops
     * moving for as long as the player keeps pressing. Four milliseconds is a quarter of a
     * 60 Hz frame, so the worst a doubled draw costs is a quarter frame of extra glide.
     */
    static final long MIN_FRAME_MS = 4;
    static final long MAX_FRAME_MS = 64;

    /**
     * What the first frame of a session, and the first after a resume, is charged. The real
     * gap there is however long somebody was away making tea, which is not a frame time; a
     * nominal 60 Hz frame is the only honest guess available before a second frame exists.
     */
    static final long NOMINAL_FRAME_MS = 16;

    /**
     * How long the centre button has to be held before it asks for a hint. This is the
     * only spare gesture on a bare TV remote, which has no Y button, so it is how a
     * remote-only player reaches the same help a gamepad gets from Y.
     *
     * <p>This used to be counted in {@link KeyEvent#getRepeatCount()} — three repeats,
     * which with Android's stock 400 ms timeout and 50 ms delay fires at 500 ms, shorter
     * than the platform's own long-press. Worse, it made the gesture a property of the
     * remote: plenty of Bluetooth-HID and infrared TV remotes send one DOWN and one UP for
     * a held key and no repeats at all, so on those the one hint gesture a remote-only
     * player has could never fire, while the legend went on promising it. It is measured
     * against the clock now, and {@link #onKeyUp} is what makes it work on a remote that
     * never repeats.
     */
    static final long HINT_HOLD_MS = 650;

    private final GameState game;
    private final UiState ui = new UiState();
    private final Effects effects = new Effects();
    private final Renderer renderer = new Renderer();
    private final PlayerRegistry players = new PlayerRegistry();
    private final CozyMusic music = new CozyMusic();
    private final CozySfx sfx = new CozySfx();
    private final SaveStore store;

    /**
     * The screen reader, when one is running. Null on a set top box that has no
     * accessibility service at all, which is most of them.
     */
    private final AccessibilityManager talkback;

    /**
     * When a held direction went down and when it last moved something, per player for the
     * board and once for the menus — there is only one highlight, however many hands are on
     * it. One-element arrays so both surfaces go through the same gate; see
     * {@link #heldStepIsDue}.
     */
    private final long[] menuPressedAt = {0};
    private final long[] menuSteppedAt = {0};
    private final long[] cursorPressedAt = {0, 0};
    private final long[] cursorSteppedAt = {0, 0};

    /** When each player's centre press went down, for the hold-to-hint gesture. */
    private final long[] centreDownAt = {0, 0};

    /** When the previous frame was drawn, or 0 before the first one of a session. */
    private long lastFrameAt;

    /**
     * Every controller in the room that has face buttons of its own.
     *
     * <p>This was a sticky {@code boolean}: once any gamepad had been seen the legend
     * printed A/B/Y for the rest of the evening, including after the only pad in the room
     * had its batteries die and the remote was all that was left. Holding the ids means
     * {@link #onControllerLost} can take one back out. At most a handful of entries.
     */
    private final Set<Integer> padsInTheRoom = new HashSet<>();

    /** A board size chosen on the title screen, applied to the next endless picture. */
    private int nextSize;

    /** Rotates the line-complete messages so the same words never land twice running. */
    private int lineMessage;

    /** The square each player's centre press landed on, so a hold can put it back. */
    private final int[] holdX = {-1, -1};
    private final int[] holdY = {-1, -1};
    private final byte[] holdMark = {Puzzle.UNKNOWN, Puzzle.UNKNOWN};

    /**
     * Whether each player's centre press came from a device with no other buttons.
     *
     * <p>Captured at the press rather than asked at the repeat, because that is where the
     * device id is known to belong to this hold. Without it a gamepad player who simply
     * rested on A for half a second had their fill silently reverted and a hint spent —
     * a gesture nothing on screen advertises, on the one button they use most.
     */
    private final boolean[] holdIsRemote = {false, false};

    /** When each player was last told a square was not in the picture. */
    private final long[] lastGentleNudgeAt = {0, 0};

    /** When the room last celebrated the two of them being in the same place. */
    private long lastTogetherAt;

    /** True while the cursors are on the same square, so the moment fires once. */
    private boolean wereSharingASquare;

    public CozyGameView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        setContentDescription("CozyGrams puzzle board");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        talkback = (AccessibilityManager) context.getSystemService(
                Context.ACCESSIBILITY_SERVICE);

        store = new SaveStore(context);
        game = store.loadGame();
        store.loadSettings(ui);
        nextSize = store.pendingSize();
        // A board restored in its finished state has already been celebrated once, so
        // deal the next picture rather than replaying the win on the first keypress.
        if (game.puzzle.complete()) {
            game.next();
        }
        ui.snapCursors(game);
        forgetTheRoom();

        // Wall-clock, deliberately: the greeting measures the gap since the last evening,
        // and uptime would make every visit look like the first.
        HomeScene.setWelcome(store.welcomeBack(System.currentTimeMillis()));
        // Tonight's baseline for the rail's counter, so "TOGETHER SO FAR" is this evening
        // rather than a lifetime figure that never visibly moves.
        HudScene.setPuzzlesBeforeTonight(game.solved);
        greetAFreshStart();

        renderer.setScenes(
                BitmapFactory.decodeResource(getResources(), R.drawable.cozy_room),
                BitmapFactory.decodeResource(getResources(), R.drawable.moon_garden));
        sfx.setEnabled(ui.sfxOn);
    }

    /**
     * Puts every static the scenes keep back where a fresh view expects them.
     *
     * <p>{@code HomeScene.pendingSize}, {@code HomeScene.restartArmedAt},
     * {@code HudScene.remoteOnly} and the cozy corner's armed row all outlive the view that
     * set them: pressing Back to the launcher and coming straight back builds a new
     * {@code CozyGameView} inside the same process, and the title screen would still print
     * "15 x 15  next" for a size this view has never heard of.
     */
    private void forgetTheRoom() {
        HomeScene.disarmRestart();
        HomeScene.setPendingSize(nextSize);
        HomeScene.setPendingChapter(-1);
        SettingsScene.disarmStoryRestart();
        HudScene.setRemoteOnly(false);
        SettingsScene.disarmDefaults();
        SettingsScene.setTidyingPlayer(-1);
    }

    /**
     * A word for a pair whose picture could not be put back — a schema bump, a regenerated
     * board. Only for someone who has been here before; a genuine first evening is not
     * missing anything.
     */
    private void greetAFreshStart() {
        if (store.startedFresh() && store.journey().visits > 1) {
            ui.showToast("We couldn't find last night's picture — here's a fresh one",
                    Theme.SOFT_TEXT, now());
        }
    }

    // ---- Lifecycle -----------------------------------------------------------------

    public void resume() {
        music.setEnabled(ui.musicOn);
        sfx.setEnabled(ui.sfxOn);
        // Forget the frame we drew before the interruption; the gap since then is however
        // long somebody was away making tea, and it is not a frame time. The same goes for
        // any centre button that was down when we were interrupted: whatever it was doing,
        // it is not still doing it.
        lastFrameAt = 0;
        centreDownAt[0] = 0;
        centreDownAt[1] = 0;
        forgetHeldSquare(0);
        forgetHeldSquare(1);
        invalidate();
    }

    public void pause() {
        music.stop();
        // The mixer's thread outlives the pause, and a chime arriving over whatever
        // interrupted us is exactly what audio focus asked us not to do.
        sfx.setEnabled(false);
        store.flush(game, ui);
    }

    /** Steps out of the way of something short, without stopping. See {@link CozyMusic#duck}. */
    public void duck(boolean quieter) {
        music.duck(quieter);
    }

    /**
     * A controller the platform says has gone — a flat battery, a pad switched off, a
     * dongle unplugged. The seat it was sitting in becomes an open chair again rather than
     * a cursor that will never move.
     */
    public void onControllerLost(int deviceId) {
        int freed = players.releaseDevice(deviceId);
        padsInTheRoom.remove(deviceId);
        HudScene.setRemoteOnly(players.deviceCount() > 0 && padsInTheRoom.isEmpty());
        for (int player = 0; player < ui.joined.length; player++) {
            ui.joined[player] = players.seatOccupied(player);
        }
        if (freed >= 0) {
            ui.showToast(Theme.playerName(freed) + "'s controller is resting  ♥",
                    Theme.playerColor(freed), now());
            announce(Theme.playerName(freed) + "'s controller is resting");
        }
        music.setPresence(players.playerCount() >= 2 ? 2 : 1);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        music.stop();
        sfx.release();
        super.onDetachedFromWindow();
    }

    private long now() {
        return SystemClock.uptimeMillis();
    }

    // ---- Speaking ------------------------------------------------------------------

    /**
     * Says something out loud, when something is listening.
     *
     * <p>The whole interface is one canvas with no node tree behind it, so before this a
     * TalkBack user heard nothing at all — not the menu, not the board, not even that the
     * cozy corner exists. Everything the game shows now has a sentence to go with it. It
     * costs nothing when no service is running, which is the ordinary case: the manager is
     * asked whether it is enabled before a string is even handed over.
     */
    private void announce(String what) {
        if (!speaking() || what == null || what.isEmpty()) {
            return;
        }
        announceForAccessibility(what);
    }

    /**
     * True when a screen reader is running. Asked before a sentence is built, not after:
     * {@link #describeMenu} walks two label arrays and {@link #describeCursor} builds a
     * string per cursor step, and neither is worth doing for nobody.
     */
    private boolean speaking() {
        return talkback != null && talkback.isEnabled();
    }

    /**
     * Where a player is and what is under them, as a sentence.
     *
     * <p>Static and pure so the wording can be held to account by a test — nothing else
     * about this class can be, because it needs a live {@code Context}.
     */
    static String describeCursor(GameState game, int player) {
        return Theme.playerName(player)
                + ", row " + (game.cursorY[player] + 1)
                + ", column " + (game.cursorX[player] + 1)
                + ", " + markWord(game.markUnder(player));
    }

    /** What a square is, in a word. */
    static String markWord(byte mark) {
        switch (mark) {
            case Puzzle.FILLED:
                return "filled";
            case Puzzle.CROSSED:
                return "crossed out";
            default:
                return "empty";
        }
    }

    /** The highlighted row of whichever menu is open, and its state. */
    private String describeMenu() {
        if (ui.screen == UiState.SETTINGS) {
            int row = Math.floorMod(ui.menu, SettingsScene.ITEM_COUNT);
            String label = SettingsScene.friendlyName(ui, row);
            return SettingsScene.hasSwitch(row)
                    ? label + ", " + (SettingsScene.states(ui)[row] ? "on" : "off")
                    : label;
        }
        int row = Math.floorMod(ui.menu, HomeScene.ITEM_COUNT);
        return HomeScene.items(game)[row] + ", " + HomeScene.values(game)[row];
    }

    /** Shows a message and reads it out, so the two can never say different things. */
    private void tell(String message, int color) {
        ui.showToast(message, color, now());
        announce(message);
    }

    /**
     * How the book announces where it has got to.
     *
     * <p>The chapter number is spelled through {@link WinScene#word}, which is the same rule
     * the win card and the rail already keep, and for the reason that file states: "one
     * numeral in the middle of a sentence is enough to make the whole line read like a status
     * bar, and it was the very first thing the book says". Both places that opened a chapter
     * concatenated the raw integer instead, so the ribbon read "Chapter 11 — Garden Rose"
     * four inches from a rail reading "Thirteen chapters still to come" — one book, counted
     * two ways, on screen at the same instant.
     */
    private String chapterLine() {
        return "Chapter " + WinScene.word(game.storyIndex + 1) + " — " + game.puzzle.name;
    }

    // ---- Input routing -------------------------------------------------------------

    @Override
    public boolean onKeyDown(int key, KeyEvent event) {
        boolean repeat = event.getRepeatCount() > 0;
        boolean joining = players.slotOf(event.getDeviceId()) < 0;
        int who = registerDevice(event.getDeviceId());
        ui.lastActive[who] = now();

        // “Press a button to join” should do exactly one thing. Letting that same press
        // fall through could start a chapter, flip a setting, or mark a square before the
        // new player had even seen which seat they claimed.
        if (joining) {
            invalidate();
            return true;
        }

        boolean handled;
        switch (ui.screen) {
            case UiState.HOME:
                handled = handleHomeKey(key, event, who, repeat);
                break;
            case UiState.SETTINGS:
                handled = handleSettingsKey(key, event, repeat);
                break;
            default:
                handled = handleGameKey(key, event, who, repeat);
                break;
        }
        invalidate();
        return handled;
    }

    /**
     * The other half of the held centre button. A remote that emits no auto-repeat at all
     * — which many Bluetooth-HID and infrared remotes do not — reaches the hint gesture
     * here and nowhere else.
     */
    @Override
    public boolean onKeyUp(int key, KeyEvent event) {
        int who = players.slotOf(event.getDeviceId());
        if (who < 0 || !PlayerRegistry.isConfirm(key)) {
            return super.onKeyUp(key, event);
        }
        boolean held = ui.screen == UiState.GAME && !ui.won && holdWasLongEnough(who);
        centreDownAt[who] = 0;
        if (!held) {
            forgetHeldSquare(who);
            return super.onKeyUp(key, event);
        }
        holdForHint(who);
        invalidate();
        return true;
    }

    /** True when this player's centre press has been down long enough to mean "help". */
    private boolean holdWasLongEnough(int who) {
        return holdIsRemote[who] && holdX[who] >= 0 && centreDownAt[who] > 0
                && now() - centreDownAt[who] >= HINT_HOLD_MS;
    }

    /** Assigns a controller to a player slot and celebrates the first time it joins. */
    private int registerDevice(int deviceId) {
        int who = players.playerFor(deviceId);
        // The printed legend has to match what is actually in people's hands. It only
        // switches to remote wording when *every* controller is a remote: if there is a
        // gamepad anywhere in the room the button names are true for it, and the remote
        // player's centre key still fills, so nothing on screen is ever a lie.
        if (players.needsCycleInput(deviceId)) {
            padsInTheRoom.remove(deviceId);
        } else {
            padsInTheRoom.add(deviceId);
        }
        HudScene.setRemoteOnly(players.deviceCount() > 0 && padsInTheRoom.isEmpty());
        music.setPresence(players.playerCount() >= 2 ? 2 : 1);
        if (players.justJoined() == who && !ui.joined[who]) {
            ui.joined[who] = true;
            HudScene.setJoinedAt(now());
            tell(Theme.playerName(who) + " joined the puzzle  ♥", Theme.playerColor(who));
            sfx.play(CozySfx.Sound.JOIN);
        } else if (players.justShared()) {
            // A third controller doubles up rather than taking a seat. Saying so out loud
            // beats leaving someone wondering why their buttons move somebody else.
            tell("Playing as " + Theme.playerName(who) + " too", Theme.playerColor(who));
        }
        return who;
    }

    // ---- Held directions -----------------------------------------------------------

    /**
     * Whether a held direction may step again, against the shared cadence in
     * {@link Theme#REPEAT_FIRST_MS}.
     *
     * <p>One gesture used to behave three ways. The board had no gate at all and moved on
     * every key event — about twenty cells a second at Android's 50 ms repeat delay, which
     * is 3.3x the analog stick and fast enough that {@code UiState.approach} gives up
     * gliding and teleports the cursor instead. Menus used 130 ms. The stick had a careful
     * 340/165. Now the pause before a hold starts repeating is the same everywhere and only
     * the repeat itself differs, because travelling across a board and choosing a menu row
     * are genuinely different jobs.
     */
    private boolean heldStepIsDue(long[] pressedAt, long[] steppedAt, int slot, long now,
                                  long repeatMs) {
        long due = now - pressedAt[slot] < Theme.REPEAT_FIRST_MS
                ? Theme.REPEAT_FIRST_MS : repeatMs;
        if (now - steppedAt[slot] < due) {
            return false;
        }
        steppedAt[slot] = now;
        return true;
    }

    // ---- Home screen ---------------------------------------------------------------

    private boolean handleHomeKey(int key, KeyEvent event, int who, boolean repeat) {
        if (PlayerRegistry.isBack(key)) {
            store.save(game, ui);
            return super.onKeyDown(key, event);
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            stepMenu(-1, HomeScene.ITEM_COUNT, repeat);
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            stepMenu(1, HomeScene.ITEM_COUNT, repeat);
        } else if ((ui.menu == HomeScene.ITEM_SIZE || ui.menu == HomeScene.ITEM_STORY)
                && (key == KeyEvent.KEYCODE_DPAD_LEFT
                || key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            // Hat-switch motion already stepped this pad; the matching D-pad key would
            // double the move and skip a chapter or a size.
            if (players.stickSteppedRecently(event.getDeviceId(), now())) {
                return true;
            }
            if (menuStepIsAllowed(repeat)) {
                int dir = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (ui.menu == HomeScene.ITEM_SIZE) {
                    nudgeBoardSize(dir * 5);
                } else {
                    browseStoryChapter(dir);
                }
            }
        } else if (PlayerRegistry.isConfirm(key) && !repeat) {
            chooseHomeItem(who);
        } else {
            return true;
        }
        return true;
    }

    /**
     * Moves the menu highlight, ignoring repeats that arrive faster than a person can
     * read. A held direction on a long list would otherwise shoot straight past the row
     * someone was aiming for.
     *
     * <p>Only <em>repeats</em> are throttled. The gate used to apply to every press from a
     * single shared field, so two deliberate taps a tenth of a second apart lost the second
     * one, and Rose holding a direction swallowed Sky's separate press on the same menu.
     */
    private void stepMenu(int direction, int itemCount, boolean repeat) {
        // Moving away from the question is an answer of "no".
        HomeScene.disarmRestart();
        SettingsScene.disarmDefaults();
        if (!menuStepIsAllowed(repeat)) {
            return;
        }
        ui.menu = Math.floorMod(ui.menu + direction, itemCount);
        sfx.play(CozySfx.Sound.MOVE);
        if (speaking()) {
            announce(describeMenu());
        }
    }

    /** The menu's cadence gate. A tap always passes; a held direction waits its turn. */
    private boolean menuStepIsAllowed(boolean repeat) {
        long moment = now();
        if (repeat) {
            return heldStepIsDue(menuPressedAt, menuSteppedAt, 0, moment,
                    Theme.MENU_REPEAT_MS);
        }
        menuPressedAt[0] = moment;
        menuSteppedAt[0] = moment;
        return true;
    }

    /**
     * Steps the endless-size picker. Left and right only change the number in the row;
     * {@link #startEndlessAtSelectedSize} is what actually deals the canvas. Mixing those
     * two jobs is how a 20×20 could sit in the stepper and never start.
     */
    private void nudgeBoardSize(int amount) {
        int size = endlessSizeNow() + amount;
        if (size > GameState.MAX_SIZE) {
            size = GameState.MIN_SIZE;
        }
        if (size < GameState.MIN_SIZE) {
            size = GameState.MAX_SIZE;
        }
        sfx.play(CozySfx.Sound.SELECT);
        nextSize = size;
        HomeScene.setPendingSize(size);
        store.setPendingSize(size);
        store.save(game, ui);
    }

    /** Opens the chapter the story-book stepper is showing. */
    private void browseStoryChapter(int delta) {
        HomeScene.disarmRestart();
        HomeScene.browseChapter(game, delta);
        sfx.play(CozySfx.Sound.SELECT);
        if (speaking()) {
            announce(describeMenu());
        }
    }

    /**
     * Deals the size the stepper is showing, now, and goes to the board.
     *
     * <p>This is the way out of the story book and the way onto a 20×20. Continue resumes
     * whatever is already on the table, so it cannot be the button that starts a size
     * sitting in the next-picture row.
     */
    private void startEndlessAtSelectedSize() {
        int size = endlessSizeNow();
        if (!game.storyMode && game.size == size && !game.puzzle.complete()) {
            enterGame();
            return;
        }
        dealEndless(size, System.currentTimeMillis(),
                size + " × " + size + " — a fresh cozy canvas");
        enterGame();
    }

    /**
     * Forgets a banked board size, in the view, the title screen's row and the save file.
     */
    private void clearPendingSize() {
        nextSize = 0;
        HomeScene.setPendingSize(0);
        store.setPendingSize(0);
    }

    /** Deals a fresh endless board at once, and says what changed. */
    private void dealEndless(int size, long seed, String message) {
        clearPendingSize();
        clearWin();
        game.startEndless(seed, size);
        ui.snapCursors(game);
        tell(message, Theme.BLUE);
        store.save(game, ui);
    }

    /**
     * Where the stepper starts from: a pending choice, or the board on the table snapped
     * onto the ladder endless play actually deals.
     *
     * <p>The chapters are drawn at 5, 7, 10, 12, 15 and 20 squares while endless steps in
     * fives, so a stepper seeded from a 7x7 chapter would otherwise walk 12, 17 — sizes the
     * row has never offered. It used to be seeded from {@link GameState#MIN_SIZE} in story
     * mode, which is why stepping it always started again from the smallest board.
     */
    private int endlessSizeNow() {
        int from = nextSize > 0 ? nextSize : game.size;
        int steps = Math.round((from - GameState.MIN_SIZE) / 5f);
        return GameState.MIN_SIZE + Math.max(0, Math.min(3, steps)) * 5;
    }

    private void chooseHomeItem(int who) {
        sfx.play(CozySfx.Sound.SELECT);
        switch (ui.menu) {
            case HomeScene.ITEM_STORY:
                openStoryBook();
                break;
            case HomeScene.ITEM_SIZE:
                startEndlessAtSelectedSize();
                break;
            case HomeScene.ITEM_SETTINGS:
                openSettings(who);
                break;
            default:
                break;
        }
    }

    /**
     * Opens the book at the chapter the pair are on.
     *
     * <p>The row is a chapter stepper. It used to call {@code startStory} unconditionally, and
     * {@code PuzzleLibrary.get} builds a brand new {@link Puzzle} every time — so opening
     * the book halfway through chapter four threw chapter four away. Re-entering the
     * chapter you are already on now keeps every mark on it.
     */
    private void openStoryBook() {
        int chapter = HomeScene.chapterToShow(game);
        if (!game.storyMode || game.storyIndex != chapter || game.puzzle.complete()) {
            game.startStory(chapter);
        }
        HomeScene.setPendingChapter(-1);
        enterGame();
    }

    private void enterGame() {
        HomeScene.disarmRestart();
        clearWin();
        ui.screen = UiState.GAME;
        ui.snapCursors(game);
        tell(game.storyMode
                ? chapterLine()
                : game.puzzle.name + " is waiting", Theme.CREAM);
        store.save(game, ui);
    }

    /**
     * Forgets a celebration.
     *
     * <p>{@code ui.won} used to be cleared in exactly one place — {@link #nextPuzzle} — so
     * pressing Back on the win card (which the card itself invites) left it set. The next
     * board dealt then had the win card painted straight over it: opening the story book
     * showed the finished solution of the chapter you had not started yet, and the press
     * that dismissed it skipped the chapter as well. Every path that puts a different
     * picture on the table comes through here now.
     */
    private void clearWin() {
        ui.won = false;
        ui.winAt = 0;
        effects.clear();
    }

    // ---- Cozy corner ---------------------------------------------------------------

    private boolean handleSettingsKey(int key, KeyEvent event, boolean repeat) {
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            stepMenu(-1, SettingsScene.ITEM_COUNT, repeat);
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            stepMenu(1, SettingsScene.ITEM_COUNT, repeat);
        } else if (PlayerRegistry.isConfirm(key) && !repeat) {
            chooseSetting();
        } else if (PlayerRegistry.isBack(key) || PlayerRegistry.isCross(key)
                || PlayerRegistry.isMenu(key)) {
            leaveSettings();
        }
        return true;
    }

    /**
     * Acts on the highlighted row. {@link SettingsScene#toggle} owns what each row means,
     * so the row list and this handler can never drift apart; all that is left here is
     * telling the audio engines about a change they cannot see for themselves.
     */
    private void chooseSetting() {
        int row = Math.floorMod(ui.menu, SettingsScene.ITEM_COUNT);
        if (!SettingsScene.toggle(ui, row, now())) {
            leaveSettings();
            return;
        }
        if (SettingsScene.consumeStoryRestart()) {
            game.solved = 0;
            game.storyFurthest = 0;
            game.storyCompleted = 0;
            game.startStory(0);
            HomeScene.setPendingChapter(0);
            leaveSettings();
            enterGame();
            return;
        }
        music.setEnabled(ui.musicOn);
        // The chime rises for something turned on and falls for something turned off, so
        // the answer is audible even for a row whose effect is on another screen.
        boolean switchedOn = SettingsScene.hasSwitch(row) && SettingsScene.states(ui)[row];
        if (row == SettingsScene.ITEM_SFX && !ui.sfxOn) {
            // Say goodbye while the mixer is still listening, then make the room quiet.
            sfx.select(CozySfx.ROOM, false);
            sfx.setEnabled(false);
        } else {
            sfx.setEnabled(ui.sfxOn);
            sfx.select(CozySfx.ROOM, switchedOn);
        }
        announce(SettingsScene.bottomLine(row, now()));
        store.save(game, ui);
    }

    /** Opens the cozy corner, remembering where to return to and who reached for it. */
    private void openSettings(int who) {
        ui.screenBeforeSettings = ui.screen;
        ui.menuBeforeSettings = ui.menu;
        ui.screen = UiState.SETTINGS;
        ui.menu = 0;
        SettingsScene.setTidyingPlayer(who);
        SettingsScene.disarmDefaults();
        SettingsScene.disarmStoryRestart();
        if (speaking()) {
            announce("Settings. " + describeMenu());
        }
    }

    /** Returns to wherever settings was opened from. */
    private void leaveSettings() {
        ui.screen = ui.screenBeforeSettings;
        ui.menu = ui.menuBeforeSettings;
        SettingsScene.disarmDefaults();
        SettingsScene.disarmStoryRestart();
        SettingsScene.setTidyingPlayer(-1);
        if (speaking()) {
            announce(ui.screen == UiState.GAME ? "Back to the puzzle" : describeMenu());
        }
        store.save(game, ui);
    }

    // ---- Playing -------------------------------------------------------------------

    private boolean handleGameKey(int key, KeyEvent event, int who, boolean repeat) {
        if (PlayerRegistry.isMenu(key)) {
            openSettings(who);
            return true;
        }
        if (ui.won) {
            return handleWinKey(key, repeat);
        }
        if (PlayerRegistry.isBack(key)) {
            ui.screen = UiState.HOME;
            ui.menu = 0;
            if (speaking()) {
                announce(describeMenu());
            }
            store.save(game, ui);
            return true;
        }

        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                // Some pads emit both analog motion and D-pad keys for one stick push.
                // The analog path already stepped; taking the key too skips a square.
                if (players.analogSteppedRecently(event.getDeviceId(), now())) {
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                    stepCursor(who, -1, 0, repeat);
                } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    stepCursor(who, 1, 0, repeat);
                } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    stepCursor(who, 0, -1, repeat);
                } else {
                    stepCursor(who, 0, 1, repeat);
                }
                break;
            default:
                if (repeat) {
                    holdOnActionKey(key, who);
                    return true;
                }
                if (!handleActionKey(key, event, who)) {
                    return super.onKeyDown(key, event);
                }
                break;
        }
        checkForWin();
        return true;
    }

    /**
     * A held action key. Only one gesture lives here: a remote that <em>does</em> auto-repeat
     * gets its hint the moment the hold is earned, rather than waiting for the key to come
     * back up. {@link #onKeyUp} is the other half, for the remotes that never repeat.
     */
    private void holdOnActionKey(int key, int who) {
        if (PlayerRegistry.isConfirm(key) && holdWasLongEnough(who)) {
            holdForHint(who);
        }
    }

    /**
     * The buttons that change the board: fill, cross and hint.
     *
     * <p>Split out of {@link #handleGameKey}'s {@code default} arm, which had grown into a
     * second router nested inside the first one — four levels of indentation, three
     * different kinds of exit, and the one thing they all had in common (whether
     * {@link #checkForWin()} runs afterwards) decided by whether a branch said
     * {@code break} or {@code return}.
     *
     * @return false when this is not a button this screen owns, so the caller can hand it
     *         back to the platform.
     */
    private boolean handleActionKey(int key, KeyEvent event, int who) {
        if (PlayerRegistry.isConfirm(key)) {
            beginCentrePress(who, event.getDeviceId());
            if (players.needsCycleInput(event.getDeviceId())) {
                cycleSquare(who);
            } else {
                fillSquare(who);
            }
            return true;
        }
        if (PlayerRegistry.isCross(key)) {
            crossSquare(who);
            return true;
        }
        if (PlayerRegistry.isHint(key)) {
            useHint(who);
            return true;
        }
        return false;
    }

    /**
     * The win card's keys.
     *
     * <p>The picture is the whole payoff. Only a deliberate press moves on — a D-pad nudge
     * from the other cushion must never wipe what the pair just made — and Back, which the
     * card offers in as many words, acknowledges the picture exactly as the centre button
     * does before going home. Anything else that left {@code ui.won} standing showed the
     * next board with a stale win card over it.
     */
    private boolean handleWinKey(int key, boolean repeat) {
        if (repeat) {
            return true;
        }
        boolean settled = now() - ui.winAt >= WinScene.INPUT_DELAY_MS;
        if (PlayerRegistry.isConfirm(key)) {
            if (settled) {
                nextPuzzle();
            } else {
                // Somebody is impatient. Rather than swallowing the press and looking
                // broken, run the rest of the celebration out immediately. ARRIVED_MS
                // rather than INPUT_DELAY_MS: they are 110 ms apart and the difference is
                // the hint line still sliding in under a card that is otherwise complete.
                ui.winAt = now() - (long) WinScene.ARRIVED_MS;
            }
            return true;
        }
        if (!PlayerRegistry.isBack(key)) {
            return true;
        }
        // Leaving acknowledges the picture too. Anything else leaves ui.won standing, and
        // the next board is dealt underneath a win card showing a solution nobody has
        // found yet — the press that dismisses it then skips that chapter as well.
        // Back is an acknowledgement, not a way to cancel a solved picture. Advancing
        // here at every point in the entrance keeps a quick Back and a patient Back
        // identical: both bank the finished picture before returning home.
        nextPuzzle();
        ui.screen = UiState.HOME;
        ui.menu = 0;
        if (speaking()) {
            announce(describeMenu());
        }
        store.save(game, ui);
        return true;
    }

    /** Notes a centre press: what the square was, when, and what it was pressed on. */
    private void beginCentrePress(int who, int deviceId) {
        holdX[who] = game.cursorX[who];
        holdY[who] = game.cursorY[who];
        holdMark[who] = game.puzzle.marks[holdY[who]][holdX[who]];
        centreDownAt[who] = now();
        holdIsRemote[who] = players.needsCycleInput(deviceId);
    }

    private void forgetHeldSquare(int who) {
        holdX[who] = -1;
        holdY[who] = -1;
        holdIsRemote[who] = false;
    }

    /**
     * A bare TV remote has no Y button, so holding the centre key is how a remote-only
     * player asks for the help a gamepad gets from Y. The fill that the press itself made
     * is put back first, so a hold reads as purely "show me one", not "fill and show me".
     */
    private void holdForHint(int who) {
        if (holdX[who] < 0) {
            return;
        }
        game.undoMark(who, holdX[who], holdY[who], holdMark[who]);
        forgetHeldSquare(who);
        centreDownAt[who] = 0;
        useHint(who);
        checkForWin();
    }

    /** Moves a cursor, at no more than the shared held-direction cadence. */
    private void stepCursor(int who, int dx, int dy, boolean repeat) {
        long moment = now();
        if (repeat) {
            if (!heldStepIsDue(cursorPressedAt, cursorSteppedAt, who, moment,
                    Theme.BOARD_REPEAT_MS)) {
                return;
            }
        } else {
            cursorPressedAt[who] = moment;
            cursorSteppedAt[who] = moment;
        }
        moveCursor(who, dx, dy);
    }

    private void moveCursor(int who, int dx, int dy) {
        int fromX = game.cursorX[who];
        int fromY = game.cursorY[who];
        game.move(who, dx, dy);
        // The board wraps, so a step that lands on the far side did not travel at all —
        // on a 20x20 grid that is otherwise completely invisible, and it gets its own tick.
        boolean wrapped = Math.abs(game.cursorX[who] - fromX) > 1
                || Math.abs(game.cursorY[who] - fromY) > 1;
        ui.cursorMovedAt[who] = now();
        forgetHeldSquare(who);
        sfx.move(who, dx, dy, wrapped);
        noticeTheOtherCushion(who);
        if (speaking()) {
            announce(describeCursor(game, who));
        }
    }

    /**
     * The two of them arriving on the same square. Rate limited by
     * {@link Theme#TOGETHER_COOLDOWN_MS}, because a moment that can happen twice in ten
     * seconds is not a moment, and only on the rising edge, so sitting there together does
     * not chime on every step.
     */
    private void noticeTheOtherCushion(int who) {
        boolean sharing = ui.joined[0] && ui.joined[1] && game.sharingASquare();
        if (sharing && !wereSharingASquare
                && now() - lastTogetherAt > Theme.TOGETHER_COOLDOWN_MS) {
            lastTogetherAt = now();
            tell("Right beside each other  ♥", Theme.togetherColor());
            sfx.play(CozySfx.Sound.JOIN, who);
        }
        wereSharingASquare = sharing;
    }

    private void fillSquare(int who) {
        int x = game.cursorX[who];
        int y = game.cursorY[who];

        if (wouldBeAGentleMistake(x, y)) {
            markWrongSquare(who, x, y);
            return;
        }
        if (game.wouldUndoPartner(who, now())) {
            agreeWithThePartner(who, x, y);
            return;
        }

        boolean clearing = game.puzzle.marks[y][x] == Puzzle.FILLED;
        game.mark(who, Puzzle.FILLED, now());
        effects.pulse(clearing ? Effects.Pulse.CLEAR : Effects.Pulse.FILL, x, y,
                Theme.playerColor(who), now());
        sfx.play(clearing ? CozySfx.Sound.CLEAR : CozySfx.Sound.FILL, who);
        if (!clearing) {
            // Five, dark rather than light: the pale tint measured 1.92:1 on paper, which
            // is a spray you have to be told about. The darker identity reaches 2.57:1.
            burstAtCell(x, y, 5, Theme.playerColorDark(who), Effects.SHAPE_DOT);
            celebrateCompletedLines(who, x, y);
        }
        afterBoardChanged();
    }

    /**
     * True when gentle mode should answer this press with a kind correction rather than a
     * fill: the setting is on, this square is not part of the picture, and the press would
     * be <em>adding</em> a fill rather than taking one back.
     *
     * <p>The last clause is the one worth naming. Without it gentle mode would refuse to let
     * a player rub out their own wrong square, which is the opposite of gentle.
     */
    private boolean wouldBeAGentleMistake(int x, int y) {
        return ui.gentleCheck
                && !game.puzzle.solution[y][x]
                && game.puzzle.marks[y][x] != Puzzle.FILLED;
    }

    /**
     * Two people reaching for the same square inside {@link Theme#PARTNER_GRACE_MS}.
     *
     * <p>A fill toggles, so the second player's press would rub out the first player's
     * square — which is exactly the wrong answer to the two of them agreeing. The square
     * stays, and the room says so once in a while rather than every time.
     */
    private void agreeWithThePartner(int who, int x, int y) {
        effects.pulse(Effects.Pulse.FILL, x, y, Theme.togetherColor(), now());
        sfx.play(CozySfx.Sound.FILL, who);
        if (now() - lastTogetherAt > Theme.TOGETHER_COOLDOWN_MS) {
            lastTogetherAt = now();
            tell("Great minds  ♥", Theme.togetherColor());
        }
    }

    /**
     * The centre key on a device with no other buttons: empty → filled → crossed → empty.
     *
     * <p>A bare TV remote has a D-pad, a centre key and Back, so there is nowhere to put
     * a separate cross button. Cycling is the standard single-button nonogram gesture and
     * it costs a gamepad player nothing, because a gamepad never takes this path.
     */
    private void cycleSquare(int who) {
        int x = game.cursorX[who];
        int y = game.cursorY[who];
        switch (game.puzzle.marks[y][x]) {
            case Puzzle.UNKNOWN:
                fillSquare(who);
                break;
            case Puzzle.FILLED:
                // mark() toggles, so ask for CROSSED and it replaces the fill outright.
                crossSquare(who);
                break;
            default:
                game.mark(who, Puzzle.CROSSED, now());
                effects.pulse(Effects.Pulse.CLEAR, x, y, Theme.playerColor(who), now());
                sfx.play(CozySfx.Sound.CLEAR, who);
                afterBoardChanged();
                break;
        }
    }

    /** Gentle mode: cross the square instead of filling it, and say so kindly. */
    private void markWrongSquare(int who, int x, int y) {
        game.crossOut(who, x, y, now());
        // Deliberately quiet: a soft pulse and a soft sound, in the colour of whoever did
        // it — it used to be Rose's dark pink for both players, so Sky's mistake wore
        // Rose's identity. A full-width ribbon would announce one player's mis-tap to the
        // whole room, which is the opposite of what a setting called "gentle" should feel
        // like, so the words come at most once every few seconds per player.
        effects.pulse(Effects.Pulse.ERROR, x, y, Theme.playerColorDark(who), now());
        sfx.play(CozySfx.Sound.ERROR, who);
        if (now() - lastGentleNudgeAt[who] > GENTLE_NUDGE_GAP_MS) {
            lastGentleNudgeAt[who] = now();
            // Warm, and true. It used to read "Not that one — try its neighbour", which is a
            // specific hint the game has not earned: this path knows only that the square is
            // empty in the finished picture, never where a filled one is, so following the
            // sentence would sometimes send a player somewhere worse. Everything else in this
            // game says only things it knows.
            tell("Not that one — no harm done", Theme.SOFT_TEXT);
        }
        afterBoardChanged();
    }

    /** How often gentle mode is willing to put its correction into words, per player. */
    private static final long GENTLE_NUDGE_GAP_MS = 1500;

    private void crossSquare(int who) {
        int x = game.cursorX[who];
        int y = game.cursorY[who];
        boolean clearing = game.puzzle.marks[y][x] == Puzzle.CROSSED;
        game.mark(who, Puzzle.CROSSED, now());
        // The player's own colour, not a hard-coded blue: Rose's crosses used to come out
        // in Sky's colour, and neither of them followed Sky's colour-blind-friendly teal.
        effects.pulse(clearing ? Effects.Pulse.CLEAR : Effects.Pulse.CROSS, x, y,
                Theme.playerColor(who), now());
        sfx.play(clearing ? CozySfx.Sound.CLEAR : CozySfx.Sound.CROSS, who);
        if (!clearing) {
            // Correcting a mistaken fill to a cross can finish a line just as surely as
            // placing its last filled square. Give that route the same sweep and the same
            // automatic crosses instead of making remote play feel second-class.
            celebrateCompletedLines(who, x, y);
        }
        afterBoardChanged();
    }

    private void useHint(int who) {
        if (!ui.hintsOn) {
            tell("Hints are resting — see the cozy corner", Theme.SOFT_TEXT);
            // A nudge, not an error: nothing went wrong, the help is simply switched off.
            sfx.play(CozySfx.Sound.NUDGE, who);
            return;
        }
        if (!game.hint(who, now())) {
            tell("The whole picture is already here  ✦", Theme.CANDLE);
            return;
        }
        int x = game.cursorX[who];
        int y = game.cursorY[who];
        ui.cursorMovedAt[who] = now();
        effects.pulse(Effects.Pulse.HINT, x, y, Theme.CANDLE, now());
        // Six, in candlelight. Gold measures 1.38:1 on paper at full opacity, so ten gold
        // sparks over a board were ten sparks nobody could see.
        burstAtCell(x, y, 6, Theme.CANDLE, Effects.SHAPE_SPARK);
        tell("A little starlight showed the way  ✦", Theme.CANDLE);
        sfx.play(CozySfx.Sound.HINT, who);
        celebrateCompletedLines(who, x, y);
        afterBoardChanged();
    }

    /**
     * Called after any change to the board. The score thickens as the picture fills in,
     * the arrangement is told somebody is still here, and the position goes to disk —
     * {@link SaveStore} coalesces the writes, so calling this on every square costs almost
     * nothing and means a puzzle is never lost.
     */
    private void afterBoardChanged() {
        music.setIntensity(game.pictureProgress());
        music.nudge();
        store.save(game, ui);
    }

    /** Auto-crosses any line the move just finished, with a matching flourish. */
    private void celebrateCompletedLines(int who, int x, int y) {
        boolean rowDone = game.puzzle.rowSolved(y);
        boolean colDone = game.puzzle.colSolved(x);
        boolean shared = (rowDone && game.lineWasShared(x, y, true))
                || (colDone && game.lineWasShared(x, y, false));
        int crossed = game.puzzle.autoCrossCompletedLines(x, y);
        if (crossed == 0 && !rowDone && !colDone) {
            return;
        }
        long when = now();
        long stagger = lineStaggerMs(game.size);
        if (rowDone) {
            sweepLine(true, x, y, when, stagger);
        }
        if (colDone) {
            sweepLine(false, x, y, when, stagger);
        }
        if (shared) {
            game.sharedLines++;
        }
        tell(lineCompleteMessage(who, rowDone, shared), Theme.CANDLE);
        sfx.play(CozySfx.Sound.LINE);
        music.sparkle();
    }

    /**
     * The warmth travelling along a finished line — the crest, and a mote lifting off each
     * square behind it.
     */
    private void sweepLine(boolean row, int x, int y, long when, long stagger) {
        BoardLayout board = renderer.board();
        for (int step = 0; step < game.size; step++) {
            int cx = row ? step : x;
            int cy = row ? y : step;
            long at = when + step * stagger;
            effects.pulse(Effects.Pulse.LINE, cx, cy, Theme.CANDLE, at);
            if (board != null) {
                effects.burst(board.centreX(cx), board.centreY(cy), 2, board.cell * 1.1f,
                        Theme.CANDLE, Effects.SHAPE_MOTE, at);
            }
        }
    }

    /**
     * The gap between one square of a finished line lighting up and the next.
     *
     * <p>It was a flat 18 ms, which on a 20-wide board lights eight squares at once — a
     * flash rather than a sweep. Scaled to the board and clamped, a line takes about
     * two thirds of a second whatever its length: 46 ms at 5 squares, 32 at 20.
     */
    static long lineStaggerMs(int size) {
        return Math.max(26L, Math.min(46L, size <= 0 ? 46L : 640L / size));
    }

    /**
     * The warm words for a finished line, in the order they are handed out.
     *
     * <p>{@code {line}} is "row" or "column" and {@code {name}} is whoever closed it. A
     * table rather than a switch because the rotation's length was written down twice — once
     * as the case labels and once as a {@code % 6} three lines above them — so adding a
     * seventh warm variant meant editing two places and the second one was easy to miss.
     * {@code WinScene.MESSAGES} already had this shape.
     */
    private static final String[] LINE_DONE = {
            "Lovely — that {line} is complete  ✦",
            "Nice one, {name} — {line} done  ✦",
            "That {line} can rest now  ✦",
            "Another {line} tucked in  ✦",
            "{name} closed a {line}  ✦",
            "One more {line} finished  ✦"
    };

    /**
     * A completed line can happen forty times on one board, so the same sentence forty
     * times would stop meaning anything. Six variants, stepped rather than random, so the
     * words never repeat back to back — and a line both of them had a hand in is credited
     * to both, rather than to whoever happened to place the last square.
     *
     * <p>Two {@code String.replace} calls per completed line is nothing beside the 20x20
     * redraw that follows it, and a line finishes a few times a minute at worst.
     */
    private String lineCompleteMessage(int who, boolean rowDone, boolean shared) {
        String line = rowDone ? "row" : "column";
        if (shared) {
            return "You finished that " + line + " together  ♥";
        }
        return LINE_DONE[Math.floorMod(lineMessage++, LINE_DONE.length)]
                .replace("{line}", line)
                .replace("{name}", Theme.playerName(who));
    }

    private void burstAtCell(int x, int y, int count, int color, int shape) {
        BoardLayout board = renderer.board();
        if (board == null) {
            return;
        }
        effects.burst(board.centreX(x), board.centreY(y), count, board.cell * 2.4f,
                color, shape, now());
    }

    private void checkForWin() {
        if (ui.won || !game.puzzle.complete()) {
            return;
        }
        ui.won = true;
        game.completeCurrentStoryChapter();
        ui.winAt = now();
        sfx.play(CozySfx.Sound.WIN);
        music.celebrate();
        showWinCelebration();
        announce(game.puzzle.name + " is finished. Press "
                + HomeScene.confirmName() + " for the next picture, or Back for the menu.");
        store.save(game, ui);
    }

    /**
     * The confetti.
     *
     * <p>Every number here is {@link Effects}', deliberately: {@code EffectsTest} carries a
     * {@code celebration()} helper documented as emitting "exactly as this method does",
     * and the two only stay honest while this is a straight reading of the constants. Two
     * earlier audits asked for a wider launch and a longer emission window — the first
     * makes every particle bigger as well as faster (spread scales both), and the second
     * puts the last death past the 2.4 s that {@code theCelebrationIsOverWhenTheCardSaysItIs}
     * holds the screen to. Both belong to a pass that owns the test and the harness that
     * would let somebody actually look at the result.
     */
    private void showWinCelebration() {
        float width = getWidth();
        float height = getHeight();
        // Calm motion draws no particles, so emitting them would only keep the view
        // redrawing for two and a half seconds with nothing to show for it.
        if (width <= 0 || height <= 0 || Comfort.get().calmMotion) {
            return;
        }
        long when = now();
        for (int i = 0; i < Effects.WIN_DRIFT_EMISSIONS; i++) {
            // Not a position, despite reading like one: Effects.rise lays the drift out
            // across the safe width itself and uses cx only as a seed. Changing this
            // expression moves every particle in 11-win-settled.png.
            float seedX = width * (.16f + (i * 37 % 100) / 100f * .68f);
            effects.rise(seedX, height * .96f, 2, height * .30f,
                    Effects.confettiColor(i), Effects.confettiShape(i),
                    when + i * Effects.WIN_DRIFT_STAGGER_MS, Effects.WIN_DRIFT_LIFE_MS);
        }
    }

    private void nextPuzzle() {
        clearWin();
        if (takesTheBankedSize()) {
            dealBankedSize();
        } else {
            game.next();
        }
        ui.snapCursors(game);
        tell(game.storyMode
                        ? chapterLine()
                        : "A fresh picture is waiting…", Theme.CREAM);
        sfx.play(CozySfx.Sound.SELECT);
        store.save(game, ui);
    }

    /**
     * Whether the next picture should be the banked size rather than simply the next board.
     *
     * <p>A size sitting in the stepper must never steal the next story chapter. That is
     * how finishing First Heart dealt a 20×20: the pair had browsed the size row, the
     * win card's Back called {@link #nextPuzzle}, and the banked canvas jumped the book.
     * Leaving the story is a menu action now — Play Endless — so a finished chapter
     * always turns the page.
     */
    static boolean takesTheBankedSize(boolean storyMode, int bankedSize, int boardSize) {
        if (bankedSize <= 0 || storyMode) {
            return false;
        }
        return bankedSize != boardSize;
    }

    private boolean takesTheBankedSize() {
        return takesTheBankedSize(game.storyMode, nextSize, game.size);
    }

    /**
     * Deals the banked size, keeping the session count.
     *
     * <p>{@code startEndless} goes through {@code resetTable} and resets everything about
     * the board, which is right — but {@code solved} is a count of evenings' work rather
     * than a fact about the board, so it is carried over by hand. It is incremented first
     * because the picture just finished still counts even though the next one is a different
     * size.
     */
    private void dealBankedSize() {
        int solved = game.solved + 1;
        game.startEndless(game.seed + SIZE_CHANGE_SEED_STEP, nextSize);
        game.solved = solved;
        clearPendingSize();
    }

    /**
     * How far the seed moves when a board-size change deals the next picture.
     *
     * <p>Deliberately not {@code GameState.SEED_STEP}, and it was a bare {@code + 1} with
     * nothing saying so. {@code PuzzleGenerator} reads the subject as
     * {@code seed % 24}, so this one advances to the very next subject in the deck while
     * {@code SEED_STEP}'s 104729 advances seventeen of them. Both are deterministic and
     * neither repeats a subject; what this keeps is the order players have already been
     * dealt, so asking for a bigger board hands over the next picture rather than a jump.
     */
    private static final long SIZE_CHANGE_SEED_STEP = 1;

    // ---- Analog sticks -------------------------------------------------------------

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        int[] step = players.stickStep(event, now());
        if (step == null) {
            if (players.justStirred()) {
                // Somebody pushed a stick on a controller that has not joined yet. Nothing
                // moved, but the empty seat can show that the room noticed.
                HudScene.setSeatStirredAt(openSeat(), now());
                invalidate();
            }
            return super.onGenericMotionEvent(event);
        }
        int who = registerDevice(event.getDeviceId());
        ui.lastActive[who] = now();

        if (ui.screen == UiState.GAME && !ui.won) {
            moveCursor(who, step[0], step[1]);
        } else if (ui.screen == UiState.HOME) {
            // Xbox D-pads report as hat axes, not as KEYCODE_DPAD_LEFT/RIGHT. Vertical
            // still moves the highlight; horizontal is the story/size stepper — and used
            // to be swallowed, which is why the chevrons never moved.
            if (step[1] != 0) {
                stepMenu(step[1], HomeScene.ITEM_COUNT, false);
            } else if (step[0] != 0) {
                if (ui.menu == HomeScene.ITEM_SIZE) {
                    nudgeBoardSize(step[0] * 5);
                } else if (ui.menu == HomeScene.ITEM_STORY) {
                    browseStoryChapter(step[0]);
                }
            }
        } else if (step[1] != 0 && ui.screen == UiState.SETTINGS) {
            stepMenu(step[1], SettingsScene.ITEM_COUNT, false);
        }
        invalidate();
        return true;
    }

    /** Whichever chair nobody is sitting in, or Sky's when both are taken. */
    private int openSeat() {
        return players.seatOccupied(PlayerRegistry.ROSE) ? PlayerRegistry.SKY
                : PlayerRegistry.ROSE;
    }

    // ---- Drawing -------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = now();
        applyTextScale();
        ui.animateCursors(game, frameGap(lastFrameAt, now));
        lastFrameAt = now;
        renderer.draw(canvas, getWidth(), getHeight(), game, ui, effects, now);
        if (renderer.animating(game, ui, effects, now)) {
            postInvalidateOnAnimation();
        }
    }

    /**
     * Honours Android TV's own Display &amp; Sound text size.
     *
     * <p>Nothing read {@code Configuration.fontScale} before, so a player who had already
     * told the whole television they need bigger words got no change at all here. It is
     * clamped: the system's value can be anything a manufacturer likes, and
     * {@link Theme#setTextScale} only reaches {@link Theme#textSize(float)} so the board's
     * geometry is untouched either way.
     *
     * <p>LARGER TEXT <em>is</em> folded in now, in {@link UiState#textScale()}, because
     * {@code Renderer} no longer applies it by lying to {@code Theme.setScreenHeight}. The
     * two settings used to fight: the system scale reached type only, while the switch
     * reached every padding, stroke and clue gutter as well, so turning it on took 4.8% off
     * the board. They compose here instead, and {@code Renderer} hands the product to
     * {@link Theme#setTextScale} once a frame.
     */
    private void applyTextScale() {
        float system = 1f;
        if (getResources() != null && getResources().getConfiguration() != null) {
            system = getResources().getConfiguration().fontScale;
        }
        ui.systemTextScale = Math.max(1f, Math.min(1.3f, system));
    }

    /**
     * How much real time to advance the glide by, given when the previous frame was drawn
     * ({@code 0} if there has not been one). Static and arithmetic-only so the clamp the
     * {@link UiState#animateCursors} contract asks the caller for can actually be proved
     * without a live view.
     */
    static long frameGap(long previous, long now) {
        if (previous <= 0) {
            return NOMINAL_FRAME_MS;
        }
        return Math.max(MIN_FRAME_MS, Math.min(MAX_FRAME_MS, now - previous));
    }
}
