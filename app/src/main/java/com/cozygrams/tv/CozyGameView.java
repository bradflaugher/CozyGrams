package com.cozygrams.tv;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * The lean-back surface: routes controller input into the game, keeps the animation
 * clock running, and hands each frame to {@link Renderer}.
 */
public final class CozyGameView extends View {

    /** How quickly a drawn cursor catches up with its logical square, per frame. */
    private static final float CURSOR_EASE = .34f;

    /**
     * A held centre button asks for a hint once it has been down this long. This is the
     * only spare gesture on a bare TV remote, which has no Y button, so it is how a
     * remote-only player reaches the same help a gamepad gets from Y.
     */
    private static final int HINT_HOLD_REPEATS = 3;

    /** Menus step no faster than this while a direction is held. */
    private static final long MENU_REPEAT_MS = 130;

    private final GameState game;
    private final UiState ui = new UiState();
    private final Effects effects = new Effects();
    private final Renderer renderer = new Renderer();
    private final PlayerRegistry players = new PlayerRegistry();
    private final CozyMusic music = new CozyMusic();
    private final CozySfx sfx = new CozySfx();
    private final SaveStore store;

    /** When the menu last stepped, so a held direction does not race past the rows. */
    private long lastMenuStepAt;

    /** True once any controller in the room has face buttons of its own. */
    private boolean anyGamepad;

    /** A board size chosen while a picture was in progress, applied to the next one. */
    private int nextSize;

    /** Rotates the line-complete messages so the same words never land twice running. */
    private int lineMessage;

    /** The square each player's centre press landed on, so a hold can put it back. */
    private final int[] holdX = {-1, -1};
    private final int[] holdY = {-1, -1};
    private final byte[] holdMark = {Puzzle.UNKNOWN, Puzzle.UNKNOWN};

    public CozyGameView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();

        store = new SaveStore(context);
        game = store.loadGame();
        store.loadSettings(ui);
        // A board restored in its finished state has already been celebrated once, so
        // deal the next picture rather than replaying the win on the first keypress.
        if (game.puzzle.complete()) {
            game.next();
        }
        ui.snapCursors(game);

        // Wall-clock, deliberately: the greeting measures the gap since the last evening,
        // and uptime would make every visit look like the first.
        HomeScene.setWelcome(store.welcomeBack(System.currentTimeMillis()));

        renderer.setScenes(
                BitmapFactory.decodeResource(getResources(), R.drawable.cozy_room),
                BitmapFactory.decodeResource(getResources(), R.drawable.moon_garden));
        sfx.setEnabled(ui.sfxOn);
    }

    // ---- Lifecycle -----------------------------------------------------------------

    public void resume() {
        music.setEnabled(ui.musicOn);
        sfx.setEnabled(ui.sfxOn);
        invalidate();
    }

    public void pause() {
        music.stop();
        store.flush(game, ui);
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

    // ---- Input routing -------------------------------------------------------------

    @Override
    public boolean onKeyDown(int key, KeyEvent event) {
        boolean repeat = event.getRepeatCount() > 0;
        int who = registerDevice(event.getDeviceId());
        ui.lastActive[who] = now();

        boolean handled;
        switch (ui.screen) {
            case UiState.HOME:
                handled = handleHomeKey(key, event, repeat);
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

    /** Assigns a controller to a player slot and celebrates the first time it joins. */
    private int registerDevice(int deviceId) {
        int who = players.playerFor(deviceId);
        // The printed legend has to match what is actually in people's hands. It only
        // switches to remote wording when *every* controller is a remote: if there is a
        // gamepad anywhere in the room the button names are true for it, and the remote
        // player's centre key still fills, so nothing on screen is ever a lie.
        anyGamepad |= !players.needsCycleInput(deviceId);
        HudScene.setRemoteOnly(players.deviceCount() > 0 && !anyGamepad);
        if (players.justJoined() == who && !ui.joined[who]) {
            ui.joined[who] = true;
            ui.showToast(Theme.playerName(who) + " joined the puzzle  ♥",
                    Theme.playerColor(who), now());
            sfx.play(CozySfx.Sound.JOIN);
        } else if (players.justShared()) {
            // A third controller doubles up rather than taking a seat. Saying so out loud
            // beats leaving someone wondering why their buttons move somebody else.
            ui.showToast("Playing as " + Theme.playerName(who) + " too",
                    Theme.playerColor(who), now());
        }
        return who;
    }

    // ---- Home screen ---------------------------------------------------------------

    private boolean handleHomeKey(int key, KeyEvent event, boolean repeat) {
        if (PlayerRegistry.isBack(key)) {
            store.save(game, ui);
            return super.onKeyDown(key, event);
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            stepMenu(-1, HomeScene.ITEM_COUNT);
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            stepMenu(1, HomeScene.ITEM_COUNT);
        } else if (ui.menu == HomeScene.ITEM_SIZE
                && (key == KeyEvent.KEYCODE_DPAD_LEFT
                || key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            changeBoardSize(key == KeyEvent.KEYCODE_DPAD_LEFT ? -5 : 5);
        } else if (PlayerRegistry.isConfirm(key) && !repeat) {
            chooseHomeItem();
        } else {
            return true;
        }
        return true;
    }

    /**
     * Moves the menu highlight, ignoring repeats that arrive faster than a person can
     * read. A held direction on a long list would otherwise shoot straight past the row
     * someone was aiming for.
     */
    private void stepMenu(int direction, int itemCount) {
        // Moving away from the question is an answer of "no".
        HomeScene.disarmRestart();
        long moment = now();
        if (moment - lastMenuStepAt < MENU_REPEAT_MS) {
            return;
        }
        lastMenuStepAt = moment;
        ui.menu = Math.floorMod(ui.menu + direction, itemCount);
        sfx.play(CozySfx.Sound.MOVE);
    }

    /**
     * Steps the endless board size.
     *
     * <p>A row with chevrons on it promises a setting, not a restart — so a board with
     * work on it is never thrown away here. The new size is remembered and takes effect
     * on the next picture, and the row says so. Only an untouched board is redealt
     * immediately, because there is nothing to lose and seeing the change is the point.
     */
    private void changeBoardSize(int amount) {
        int size = game.storyMode ? GameState.MIN_SIZE : pendingSize();
        size += amount;
        if (size > GameState.MAX_SIZE) {
            size = GameState.MIN_SIZE;
        }
        if (size < GameState.MIN_SIZE) {
            size = GameState.MAX_SIZE;
        }
        sfx.play(CozySfx.Sound.SELECT);

        if (game.storyMode || game.totalMoves() > 0) {
            nextSize = size;
            HomeScene.setPendingSize(size);
            ui.showToast(size + " × " + size + " — ready for the next picture",
                    Theme.BLUE, now());
            store.save(game, ui);
            return;
        }
        nextSize = 0;
        HomeScene.setPendingSize(0);
        game.startEndless(System.currentTimeMillis(), size);
        ui.snapCursors(game);
        effects.clear();
        ui.showToast(size + " × " + size + " — a fresh cozy canvas", Theme.BLUE, now());
        store.save(game, ui);
    }

    /** The size the next endless picture will use: a pending choice, or the current one. */
    private int pendingSize() {
        return nextSize > 0 ? nextSize : game.size;
    }

    private void chooseHomeItem() {
        sfx.play(CozySfx.Sound.SELECT);
        switch (ui.menu) {
            case HomeScene.ITEM_CONTINUE:
                enterGame();
                break;
            case HomeScene.ITEM_STORY:
                game.startStory(game.storyIndex);
                enterGame();
                break;
            case HomeScene.ITEM_SIZE:
                changeBoardSize(5);
                break;
            case HomeScene.ITEM_SETTINGS:
                openSettings();
                break;
            case HomeScene.ITEM_RESTART:
                confirmRestart();
                break;
            default:
                break;
        }
    }

    /**
     * Starting the story over throws away everything a pair has made, and the row sits
     * one press below the settings row — an overshoot must not be able to do it. The
     * first press asks; only a second press within a few seconds goes through.
     */
    private void confirmRestart() {
        if (HomeScene.restartArmed()) {
            HomeScene.disarmRestart();
            game.solved = 0;
            game.startStory(0);
            enterGame();
            return;
        }
        HomeScene.armRestart(now());
        ui.showToast("Start the story over from chapter one? Press A again to be sure",
                Theme.PINK_LIGHT, now());
    }

    private void enterGame() {
        HomeScene.disarmRestart();
        ui.screen = UiState.GAME;
        ui.snapCursors(game);
        ui.showToast(game.storyMode
                ? "Chapter " + (game.storyIndex + 1) + " — " + game.puzzle.name
                : game.puzzle.name + " is waiting", Theme.CREAM, now());
        store.save(game, ui);
    }

    // ---- Cozy corner ---------------------------------------------------------------

    private boolean handleSettingsKey(int key, KeyEvent event, boolean repeat) {
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            stepMenu(-1, SettingsScene.ITEM_COUNT);
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            stepMenu(1, SettingsScene.ITEM_COUNT);
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
        if (!SettingsScene.toggle(ui, ui.menu)) {
            leaveSettings();
            return;
        }
        music.setEnabled(ui.musicOn);
        sfx.setEnabled(ui.sfxOn);
        sfx.play(CozySfx.Sound.SELECT);
        store.save(game, ui);
    }

    /** Opens the cozy corner, remembering where to return to. */
    private void openSettings() {
        ui.screenBeforeSettings = ui.screen;
        ui.menuBeforeSettings = ui.menu;
        ui.screen = UiState.SETTINGS;
        ui.menu = 0;
    }

    /** Returns to wherever the cozy corner was opened from. */
    private void leaveSettings() {
        ui.screen = ui.screenBeforeSettings;
        ui.menu = ui.menuBeforeSettings;
        store.save(game, ui);
    }

    // ---- Playing -------------------------------------------------------------------

    private boolean handleGameKey(int key, KeyEvent event, int who, boolean repeat) {
        if (PlayerRegistry.isMenu(key)) {
            openSettings();
            return true;
        }
        if (PlayerRegistry.isBack(key)) {
            ui.screen = UiState.HOME;
            ui.menu = 0;
            store.save(game, ui);
            return true;
        }
        if (ui.won) {
            // The picture is the whole payoff. Only a deliberate press moves on — a D-pad
            // nudge from the other cushion must never wipe what the pair just made.
            if (!repeat && PlayerRegistry.isConfirm(key)) {
                if (now() - ui.winAt < WinScene.INPUT_DELAY_MS) {
                    // Somebody is impatient. Rather than swallowing the press and looking
                    // broken, run the rest of the celebration out immediately.
                    ui.winAt = now() - WinScene.INPUT_DELAY_MS;
                } else {
                    nextPuzzle();
                }
            }
            return true;
        }

        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                moveCursor(who, -1, 0);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                moveCursor(who, 1, 0);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                moveCursor(who, 0, -1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                moveCursor(who, 0, 1);
                break;
            default:
                if (repeat) {
                    if (PlayerRegistry.isConfirm(key)
                            && event.getRepeatCount() >= HINT_HOLD_REPEATS) {
                        holdForHint(who);
                    }
                    return true;
                }
                if (PlayerRegistry.isConfirm(key)) {
                    rememberSquareBeforeFill(who);
                    if (players.needsCycleInput(event.getDeviceId())) {
                        cycleSquare(who);
                    } else {
                        fillSquare(who);
                    }
                } else if (PlayerRegistry.isCross(key)) {
                    crossSquare(who);
                } else if (PlayerRegistry.isHint(key)) {
                    useHint(who);
                } else {
                    return super.onKeyDown(key, event);
                }
                break;
        }
        checkForWin();
        return true;
    }

    private void moveCursor(int who, int dx, int dy) {
        game.move(who, dx, dy);
        ui.cursorMovedAt[who] = now();
        forgetHeldSquare(who);
        sfx.play(CozySfx.Sound.MOVE);
    }

    /** Notes what a square looked like before a centre press, in case a hold undoes it. */
    private void rememberSquareBeforeFill(int who) {
        holdX[who] = game.cursorX[who];
        holdY[who] = game.cursorY[who];
        holdMark[who] = game.puzzle.marks[holdY[who]][holdX[who]];
    }

    private void forgetHeldSquare(int who) {
        holdX[who] = -1;
        holdY[who] = -1;
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
        game.puzzle.marks[holdY[who]][holdX[who]] = holdMark[who];
        game.moves[who] = Math.max(0, game.moves[who] - 1);
        forgetHeldSquare(who);
        useHint(who);
        checkForWin();
    }

    private void fillSquare(int who) {
        int x = game.cursorX[who];
        int y = game.cursorY[who];

        if (ui.gentleCheck && !game.puzzle.solution[y][x]
                && game.puzzle.marks[y][x] != Puzzle.FILLED) {
            markWrongSquare(who, x, y);
            return;
        }

        boolean clearing = game.puzzle.marks[y][x] == Puzzle.FILLED;
        game.mark(who, Puzzle.FILLED);
        effects.pulse(clearing ? Effects.Pulse.CLEAR : Effects.Pulse.FILL, x, y,
                Theme.playerColor(who), now());
        sfx.play(clearing ? CozySfx.Sound.CLEAR : CozySfx.Sound.FILL);
        if (!clearing) {
            burstAtCell(x, y, 7, Theme.playerColorLight(who), Effects.SHAPE_DOT);
            celebrateCompletedLines(who, x, y);
        }
        afterBoardChanged();
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
                game.mark(who, Puzzle.CROSSED);
                effects.pulse(Effects.Pulse.CLEAR, x, y, Theme.playerColor(who), now());
                sfx.play(CozySfx.Sound.CLEAR);
                afterBoardChanged();
                break;
        }
    }

    /** Gentle mode: cross the square instead of filling it, and say so kindly. */
    private void markWrongSquare(int who, int x, int y) {
        game.puzzle.marks[y][x] = Puzzle.CROSSED;
        game.moves[who]++;
        // Deliberately quiet: a soft pulse and a soft sound, aimed at the person who did
        // it. A full-width ribbon would announce one player's mis-tap to the whole room,
        // which is the opposite of what a setting called "gentle" should feel like.
        effects.pulse(Effects.Pulse.ERROR, x, y, Theme.PINK_DARK, now());
        sfx.play(CozySfx.Sound.ERROR);
        afterBoardChanged();
    }

    private void crossSquare(int who) {
        int x = game.cursorX[who];
        int y = game.cursorY[who];
        boolean clearing = game.puzzle.marks[y][x] == Puzzle.CROSSED;
        game.mark(who, Puzzle.CROSSED);
        effects.pulse(clearing ? Effects.Pulse.CLEAR : Effects.Pulse.CROSS, x, y,
                Theme.BLUE, now());
        sfx.play(clearing ? CozySfx.Sound.CLEAR : CozySfx.Sound.CROSS);
        afterBoardChanged();
    }

    private void useHint(int who) {
        if (!ui.hintsOn) {
            ui.showToast("Hints are resting — see the cozy corner",
                    Theme.SOFT_TEXT, now());
            sfx.play(CozySfx.Sound.ERROR);
            return;
        }
        if (!game.hint(who)) {
            ui.showToast("The whole picture is already here  ✦", Theme.GOLD, now());
            return;
        }
        int x = game.cursorX[who];
        int y = game.cursorY[who];
        ui.cursorMovedAt[who] = now();
        effects.pulse(Effects.Pulse.HINT, x, y, Theme.GOLD, now());
        burstAtCell(x, y, 10, Theme.GOLD, Effects.SHAPE_SPARK);
        ui.showToast("A little starlight showed the way  ✦", Theme.GOLD, now());
        sfx.play(CozySfx.Sound.HINT);
        celebrateCompletedLines(who, x, y);
        afterBoardChanged();
    }

    /**
     * Called after any change to the board. The score thickens as the picture fills in,
     * and the position goes to disk — {@link SaveStore} coalesces the writes, so calling
     * this on every square costs almost nothing and means a puzzle is never lost.
     */
    private void afterBoardChanged() {
        music.setIntensity(game.pictureProgress());
        store.save(game, ui);
    }

    /** Auto-crosses any line the move just finished, with a matching flourish. */
    private void celebrateCompletedLines(int who, int x, int y) {
        boolean rowDone = game.puzzle.rowSolved(y);
        boolean colDone = game.puzzle.colSolved(x);
        int crossed = game.puzzle.autoCrossCompletedLines(x, y);
        if (crossed == 0 && !rowDone && !colDone) {
            return;
        }
        long when = now();
        if (rowDone) {
            for (int column = 0; column < game.size; column++) {
                effects.pulse(Effects.Pulse.LINE, column, y, Theme.GOLD,
                        when + column * 18L);
            }
        }
        if (colDone) {
            for (int row = 0; row < game.size; row++) {
                effects.pulse(Effects.Pulse.LINE, x, row, Theme.GOLD,
                        when + row * 18L);
            }
        }
        ui.showToast(lineCompleteMessage(who, rowDone), Theme.GOLD, when);
        sfx.play(CozySfx.Sound.LINE);
    }

    /**
     * A completed line can happen forty times on one board, so the same sentence forty
     * times would stop meaning anything. Six variants, stepped rather than random, so the
     * words never repeat back to back.
     */
    private String lineCompleteMessage(int who, boolean rowDone) {
        String line = rowDone ? "row" : "column";
        String name = Theme.playerName(who);
        switch (lineMessage++ % 6) {
            case 0:
                return "Lovely — that " + line + " is complete  ✦";
            case 1:
                return "Nice one, " + name + " — " + line + " done  ✦";
            case 2:
                return "That " + line + " can rest now  ✦";
            case 3:
                return "Another " + line + " tucked in  ✦";
            case 4:
                return name + " closed a " + line + "  ✦";
            default:
                return "One more " + line + " finished  ✦";
        }
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
        ui.winAt = now();
        sfx.play(CozySfx.Sound.WIN);
        music.celebrate();
        showWinCelebration();
        store.save(game, ui);
    }

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
            float x = width * (.16f + (i * 37 % 100) / 100f * .68f);
            effects.rise(x, height * .96f, 2, height * .30f,
                    Effects.confettiColor(i), i % 3 == 0 ? Effects.SHAPE_HEART
                            : (i % 3 == 1 ? Effects.SHAPE_PETAL : Effects.SHAPE_DOT),
                    when + i * Effects.WIN_DRIFT_STAGGER_MS, Effects.WIN_DRIFT_LIFE_MS);
        }
    }

    private void nextPuzzle() {
        ui.won = false;
        effects.clear();
        if (!game.storyMode && nextSize > 0 && nextSize != game.size) {
            int solved = game.solved + 1;
            game.startEndless(game.seed + 1, nextSize);
            game.solved = solved;
            nextSize = 0;
            HomeScene.setPendingSize(0);
        } else {
            game.next();
        }
        ui.snapCursors(game);
        ui.showToast(game.storyMode
                        ? "Chapter " + (game.storyIndex + 1) + " — " + game.puzzle.name
                        : "A fresh picture is waiting…", Theme.CREAM, now());
        sfx.play(CozySfx.Sound.SELECT);
        store.save(game, ui);
    }

    // ---- Analog sticks -------------------------------------------------------------

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        int[] step = players.stickStep(event, now());
        if (step == null) {
            return super.onGenericMotionEvent(event);
        }
        int who = registerDevice(event.getDeviceId());
        ui.lastActive[who] = now();

        if (ui.screen == UiState.GAME && !ui.won) {
            moveCursor(who, step[0], step[1]);
        } else if (step[1] != 0) {
            stepMenu(step[1], ui.screen == UiState.SETTINGS
                    ? SettingsScene.ITEM_COUNT : HomeScene.ITEM_COUNT);
        }
        invalidate();
        return true;
    }

    // ---- Drawing -------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = now();
        ui.animateCursors(game, CURSOR_EASE);
        renderer.draw(canvas, getWidth(), getHeight(), game, ui, effects, now);
        if (renderer.animating(game, ui, effects, now)) {
            postInvalidateOnAnimation();
        }
    }
}
