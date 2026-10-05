package com.cozygrams.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
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

    /**
     * Software hold-to-repeat, one lane per player on the board and one for the menus.
     * Lives here rather than on the platform because many pads never send a key-repeat
     * or a second stick event while the direction stays down.
     */
    private final HoldRepeat[] cursorHolds = {new HoldRepeat(), new HoldRepeat()};
    private final HoldRepeat menuHold = new HoldRepeat();
    private final Runnable cursorHoldTick = this::tickCursorHolds;
    private final Runnable menuHoldTick = this::tickMenuHold;

    /** When each player's centre press went down, for the hold-to-hint gesture. */
    private final long[] centreDownAt = {0, 0};

    /**
     * True while {@link #hush} has silenced the game for something outside it — a call,
     * headphones out, audio focus lost — until {@link #resume} brings the sound back.
     */
    private boolean soundResting;

    /** When the previous frame was drawn, or 0 before the first one of a session. */
    private long lastFrameAt;

    /**
     * Whether anybody is still playing, and so how hard the view should work for them. See
     * {@link IdleWatch} for why this exists at all.
     */
    private final IdleWatch idle = new IdleWatch(SystemClock.uptimeMillis());

    /**
     * The one pending slow frame, while settled. A single named runnable rather than
     * {@code postInvalidateDelayed}, because every press also invalidates: each of those
     * frames would otherwise schedule a delayed frame of its own on top of the one already
     * waiting, and a quiet screen poked a few times would end up redrawing several chains
     * at once — the opposite of settling.
     */
    private final Runnable settledFrame = this::invalidate;

    /** Lets the screen sleep once the room has been quiet for long enough. */
    private final Runnable dozeCheck = this::checkForDoze;

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
        // A phone or tablet starts in touch mode, so its first screen already talks about
        // tapping; a television never does. After that, whichever was used last wins.
        PackageManager pm = context.getPackageManager();
        boolean handheld = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        HudScene.setTouch(handheld);
        // A television is the sofa, and starts with a seat for Sky. A phone or a tablet is
        // one person holding one screen, and starts as a one-player game; either can be
        // changed in the cozy corner. Said before the save is read, so the save's own
        // answer wins and a first run falls back to the one that suits the hardware.
        ui.defaultTwoPlayers = !handheld;
        ui.twoPlayers = ui.defaultTwoPlayers;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        talkback = (AccessibilityManager) context.getSystemService(
                Context.ACCESSIBILITY_SERVICE);

        store = new SaveStore(context);
        game = store.loadGame();
        store.loadSettings(ui);
        players.setSolo(!ui.twoPlayers);
        nextSize = store.pendingSize();
        // A board restored in its finished state has already been celebrated once, so
        // deal the next picture rather than replaying the win on the first keypress.
        if (game.puzzle.complete()) {
            // The same choice the win card's own exit makes, so a size picked before the
            // app was closed on a finished picture is the size of the next one.
            if (takesTheBankedSize()) {
                dealBankedSize();
            } else {
                game.next();
            }
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
        // Asked once, up front: whether there is a share sheet and a browser to hand off
        // to. A television usually has neither, and the cozy corner says so in words.
        ui.canShare = resolves(shareIntent());
        ui.canBrowse = resolves(feedbackIntent());
        // No store app and no browser means nothing to rate in, so the row is left out.
        SettingsScene.setRateShown(resolves(rateIntent()) || resolves(ratePageIntent()));
        ui.tips.seen = store.tipsSeen();
        if (store.welcomeOwed()) {
            ui.tutorial = true;
            ui.tour.start(now());
            // Posted, because a view that is not attached yet has nobody to speak to.
            post(() -> announce(ui.tour.spoken(TutorialScene.hands())));
        }

        renderer.setScenes(
                BitmapFactory.decodeResource(getResources(), R.drawable.cozy_room),
                BitmapFactory.decodeResource(getResources(), R.drawable.moon_garden));
        sfx.setEnabled(ui.sfxOn);

        // The thumb pad's 48dp floor needs to know what a dp is here, and the safe
        // rectangle needs to know what the window's bars and cutout cover. Both arrive
        // with the insets, which the system re-sends whenever the window changes shape.
        HudScene.setDensity(getResources().getDisplayMetrics().density);
        setOnApplyWindowInsetsListener((view, insets) -> {
            applyInsets(insets);
            return insets;
        });
    }

    /**
     * Puts the tour and every one-time tip away for good without showing them — for an
     * automated run that launches the app to take a picture of the game rather than of them.
     */
    public void skipWelcome() {
        ui.tutorial = false;
        store.welcomeSeen();
        ui.tips.seen = Tips.ALL;
        ui.tips.hide(now());
        store.writeTips(Tips.ALL);
        invalidate();
    }

    /**
     * The activity kept itself through a configuration change. A new density moves the
     * 48dp floor under the thumb pad, and the bars may have moved with it, so the density
     * is read again and fresh insets are asked for rather than waited for.
     */
    public void configurationChanged() {
        HudScene.setDensity(getResources().getDisplayMetrics().density);
        requestApplyInsets();
        invalidate();
    }

    /**
     * Hands the window's insets to the renderer as four plain numbers.
     *
     * <p>{@code getSystemWindowInset*} rather than {@code getInsets(Type)}, which is API 30,
     * and without {@code getDisplayCutout()}, which is API 28: this has to run on API 26.
     * They are deprecated, not broken — on API 30 and up they are the visible system bars
     * together with the display cutout, which is exactly the rectangle wanted here, and a
     * window laid out into the short-edge cutout gets its camera reported through them.
     * The stable insets are deliberately not folded in: they report a bar even while it
     * is hidden, and on a television or an immersive phone that would give up a status
     * bar's height of board to a bar nobody can see.
     */
    @SuppressWarnings("deprecation")
    private void applyInsets(WindowInsets insets) {
        renderer.setInsets(insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
        HudScene.setDensity(getResources().getDisplayMetrics().density);
        invalidate();
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
        HudScene.setPadSeen(false);
        HudScene.setKeyboard(false);
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
        resume(true);
    }

    /**
     * Comes back to the game, with the sound only if {@code withSound}: returning during a
     * phone call brings back the picture and leaves the music and effects off, without
     * ever starting a track that would then have to be stopped.
     */
    public void resume(boolean withSound) {
        soundResting = !withSound;
        music.setEnabled(withSound && ui.musicOn);
        sfx.setEnabled(withSound && ui.sfxOn);
        // Forget the frame we drew before the interruption; the gap since then is however
        // long somebody was away making tea, and it is not a frame time. The same goes for
        // any centre button that was down when we were interrupted: whatever it was doing,
        // it is not still doing it.
        lastFrameAt = 0;
        centreDownAt[0] = 0;
        centreDownAt[1] = 0;
        forgetHeldSquare(0);
        forgetHeldSquare(1);
        // Coming back to the game is as good as a keypress: whoever opened it is looking.
        noticeSomebody();
        armDozeCheck();
        invalidate();
    }

    public void pause() {
        hush();
        // A key-up that lands while another window has focus never reaches us; a hold
        // left running would walk the cursor on its own when the game comes back.
        cursorHolds[0].clear();
        cursorHolds[1].clear();
        menuHold.clear();
        // The doze timer is left running: a permanent loss of audio focus pauses the sound
        // with the game still in front of somebody, and that screen still deserves to be
        // allowed to sleep. On a window that is really in the background it is harmless.
        removeCallbacks(settledFrame);
        store.flush(game, ui);
    }

    /**
     * Silences the music and the effects without leaving the game — for a phone call, or
     * for headphones pulled out on a bus, where the picture is still wanted but the sound
     * very much is not. {@link #resume} brings both back.
     */
    public void hush() {
        soundResting = true;
        music.stop();
        // The mixer's thread outlives the pause, and a chime arriving over whatever
        // interrupted us is exactly what audio focus asked us not to do.
        sfx.setEnabled(false);
    }

    /**
     * The headphones came out. The sound stops where it is — the phone's speaker is not
     * where anybody asked for it — and the puzzle carries on, since there is no pause
     * screen to force on anybody and the board has lost nothing. Sound comes back with the
     * next resume, or with the Music or Sounds switch in the cozy corner.
     */
    public void headphonesOut() {
        if (!ui.musicOn && !ui.sfxOn) {
            return;
        }
        hush();
        tell("Headphones out — the sound is resting", Theme.SOFT_TEXT);
        invalidate();
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
        // The cancelled key-up for a direction it was holding can arrive after the device
        // has left the registry, where onKeyUp no longer knows whose it was.
        cursorHolds[0].releaseDevice(deviceId);
        cursorHolds[1].releaseDevice(deviceId);
        menuHold.releaseDevice(deviceId);
        int freed = players.releaseDevice(deviceId);
        padsInTheRoom.remove(deviceId);
        HudScene.setRemoteOnly(players.deviceCount() > 0 && padsInTheRoom.isEmpty());
        HudScene.setPadSeen(!padsInTheRoom.isEmpty());
        // The registry counts the finger too, so a pad going away cannot take Rose's seat
        // from somebody who has been playing her on the glass.
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
        removeCallbacks(cursorHoldTick);
        removeCallbacks(menuHoldTick);
        removeCallbacks(settledFrame);
        removeCallbacks(dozeCheck);
        super.onDetachedFromWindow();
    }

    private long now() {
        return SystemClock.uptimeMillis();
    }

    // ---- Winding down --------------------------------------------------------------

    /**
     * Somebody pressed, touched, scrolled or pushed something. Brings the frame rate
     * straight back, and if the screen had been allowed to sleep, keeps it awake again.
     */
    private void noticeSomebody() {
        if (idle.noticed(now())) {
            keepScreenOn(true);
            armDozeCheck();
            invalidate();
        }
    }

    private void armDozeCheck() {
        removeCallbacks(dozeCheck);
        postDelayed(dozeCheck, idle.untilDoze(now()));
    }

    /**
     * Runs off its own timer rather than off the frames, because with Calmer Animation on a
     * quiet screen draws no frames at all — and that is the screen that most needs to be
     * allowed to sleep.
     */
    private void checkForDoze() {
        long moment = now();
        if (idle.dozing(moment)) {
            keepScreenOn(false);
            return;
        }
        if (idle.idleFor(moment) < IdleWatch.DOZE_AFTER_MS) {
            postDelayed(dozeCheck, idle.untilDoze(moment));
        }
    }

    /**
     * {@code FLAG_KEEP_SCREEN_ON} lives on the activity's window, which is where
     * {@link MainActivity} set it. Taken away after ten quiet minutes so a phone can sleep and
     * a television can reach its screensaver; put back on the next press.
     */
    private void keepScreenOn(boolean on) {
        Context context = getContext();
        if (!(context instanceof Activity)) {
            return;
        }
        Window window = ((Activity) context).getWindow();
        if (window == null) {
            return;
        }
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
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

    /**
     * Where a player is, and the clues that cross there — so somebody who cannot see the
     * gutters still has the whole of what the puzzle is asking at the square they are on.
     * A finished line says so instead of reading its numbers again.
     */
    static String describeSquare(GameState game, int player) {
        int row = game.cursorY[player];
        int column = game.cursorX[player];
        return describeCursor(game, player)
                + ". Row " + clueWords(game.puzzle.rowClues(row), game.puzzle.rowSolved(row))
                + ". Column " + clueWords(game.puzzle.colClues(column),
                game.puzzle.colSolved(column)) + ".";
    }

    /** A line's clues as they would be read out: "clues 3 1", "clue 0", "finished". */
    static String clueWords(int[] clues, boolean solved) {
        if (solved) {
            return "finished";
        }
        StringBuilder words = new StringBuilder(clues.length == 1 ? "clue" : "clues");
        for (int clue : clues) {
            words.append(' ').append(clue);
        }
        return words.toString();
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Any key at all — the volume included — is somebody in the room, and is enough to
        // keep the screen awake and the frame rate up. See IdleWatch.
        noticeSomebody();
        int key = PlayerRegistry.canonical(keyCode);
        if (!PlayerRegistry.isGameKey(key)) {
            // Volume, mute, the media keys, channel, captions, the input switcher: none of
            // them are ours, and none of them may seat a player or flip a legend on the way
            // past. They go back to the platform exactly as they arrived.
            return super.onKeyDown(keyCode, event);
        }
        ui.tips.dismiss(now());
        boolean keyboard = fromAKeyboard(event);
        if (keyboard || fromAController(event)) {
            HudScene.setKeyboard(keyboard);
        }
        // A controller picked up on a phone brings back the controller's legend, and so
        // does a keyboard on a Chromebook or a tablet in its case. The back gesture also
        // arrives as a key, but from neither, so it changes nothing.
        if (HudScene.touch() && (fromAController(event) || keyboard)) {
            HudScene.setTouch(false);
        }
        boolean repeat = event.getRepeatCount() > 0;
        // Back and Menu mean one press, however long they are held. Auto-repeat would hand
        // each repeat to whatever screen the first press opened: a held Back walked from the
        // cozy corner through the puzzle to the title, and a held Menu flicked the corner
        // open and shut. The repeats are swallowed on the title screen too, since a Back
        // held from the puzzle arrives there still repeating; only a fresh Back pressed on
        // the title screen goes to the platform.
        if (repeat && (PlayerRegistry.isMenu(key) || PlayerRegistry.isBack(key))) {
            return true;
        }
        // The back gesture on a phone arrives as a key from no controller at all. It is
        // Rose's Back, straight away: it must not be taken for somebody sitting down, which
        // swallowed the first swipe and would have handed a seat to the navigation bar.
        boolean gesture = PlayerRegistry.isBack(key) && !fromAController(event);
        boolean joining = !gesture && players.slotOf(event.getDeviceId()) < 0;
        int who = gesture ? 0 : registerDevice(event.getDeviceId());
        ui.lastActive[who] = now();

        // The tour takes every press while it is up, and none of them reaches the screen
        // behind it. The press that seats somebody counts too: the tour is the first thing
        // anybody sees, and it would be odd for it to ignore the first button on a sofa.
        if (ui.tutorial) {
            if (!repeat) {
                handleTourKey(key);
            }
            invalidate();
            return true;
        }

        // “Press a button to join” should do exactly one thing. Letting that same press
        // fall through could start a chapter, flip a setting, or mark a square before the
        // new player had even seen which seat they claimed.
        //
        // Except on a keyboard. That is somebody at a Chromebook or a tablet who has been
        // playing all along — with the trackpad, with a finger — and has just started
        // typing, not a second person picking up a pad across the sofa. Eating their first
        // arrow key reads as the game ignoring them, so the press takes the seat and then
        // does what it says.
        //
        // And except on a one-player evening, where there is no seat to choose between
        // and the press can simply be the press it was meant to be.
        if (joining && !keyboard && ui.twoPlayers) {
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
            case UiState.HELP:
                handled = handleHelpKey(key, event, repeat);
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
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        noticeSomebody();
        int key = PlayerRegistry.canonical(keyCode);
        int who = players.slotOf(event.getDeviceId());
        if (who < 0 || !PlayerRegistry.isGameKey(key)) {
            // The other half of a volume press, or of anything else that is not ours, goes
            // back the way onKeyDown sent its first half.
            return super.onKeyUp(keyCode, event);
        }
        if (PlayerRegistry.isDirection(key)) {
            boolean stickDown = players.stickDeflected(event.getDeviceId());
            cursorHolds[who].releaseKey(event.getDeviceId(), key, stickDown);
            menuHold.releaseKey(event.getDeviceId(), key, stickDown);
            return true;
        }
        if (!PlayerRegistry.isConfirm(key)) {
            return super.onKeyUp(keyCode, event);
        }
        boolean held = ui.screen == UiState.GAME && !ui.won && holdWasLongEnough(who);
        centreDownAt[who] = 0;
        if (!held) {
            forgetHeldSquare(who);
            return super.onKeyUp(keyCode, event);
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
        HudScene.setPadSeen(!padsInTheRoom.isEmpty());
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

    // ---- Touch -----------------------------------------------------------------------

    /*
     * A phone is played the way every nonogram on a phone is: choose a pen, then touch the
     * squares. The pen is the FILL | CROSS switch at the top of the rail's thumb pad, and
     * it decides what every tap and drag on the board does, so one thumb can solve a whole
     * picture without a second button or a gesture nobody discovers.
     *
     * Where the squares are big enough to hit reliably (TOUCH_DIRECT_MM and up) a tap uses
     * the pen on the square under the finger, and a drag paints a straight line: the
     * stroke locks to the row or the column it first moves along, and only changes squares
     * that look like the one it started on, so dragging over a half-finished row fills its
     * empties without rubbing out what is already there. Resting on a square uses the
     * other mark — a cross while the pen fills, a fill while it crosses — for the odd
     * square that wants the other one, and a drag that carries on from there paints with
     * it.
     *
     * Smaller squares are aimed at first, like a trackpad: a tap puts Rose's cursor on a
     * square, a slide moves it one square for every square's width of travel (never less
     * than TOUCH_STEP_MM, so even a 20x20 can be walked a square at a time), and a tap on
     * the square the cursor already sits on uses the pen. MARK on the rail uses it too,
     * and holding MARK while the other thumb slides paints every square the cursor passes.
     *
     * Every gesture ends in the same fillSquare / crossSquare / useHint the buttons call,
     * so gentle checking, hints, line sweeps, sounds and TalkBack all behave exactly as
     * they do on a television.
     */

    /** The smallest trackpad step, so tiny squares can still be walked one at a time. */
    private static final float TOUCH_STEP_MM = 4.5f;
    /** Squares at least this big are tapped directly; smaller ones are aimed at first. */
    private static final float TOUCH_DIRECT_MM = 5.5f;
    /** How long a finger rests on a square before that means "the other mark". */
    private static final long TOUCH_LONG_PRESS_MS = 380;

    private int aimPointer = -1;
    private float aimDownX;
    private float aimDownY;
    private float aimLastX;
    private float aimLastY;
    private float aimCarryX;
    private float aimCarryY;
    private boolean aimMoved;
    private boolean aimLongPressed;
    private int aimCellX = -1;
    private int aimCellY = -1;
    /** Whether this finger went down on a board big enough to be touched directly. */
    private boolean aimDirect;
    /**
     * The board as it was drawn when this finger went down. The first touch after a
     * controller redraws the board with touch geometry, so a stroke keeps reading its
     * squares off the layout it started on rather than jumping to the new one mid-drag.
     */
    private BoardLayout aimBoard;

    /** A direct drag's stroke: which mark it lays, and the axis it is locked to. */
    private boolean strokeCross;
    /** 0 until the stroke has left its first square, then 1 along a row or 2 down a column. */
    private int strokeAxis;
    /** What the stroke's first square held before the finger arrived. */
    private byte strokeFrom;

    private int buttonPointer = -1;
    private int buttonHeld = -1;
    /** The finger on the on-screen back button, which acts when it lifts there. */
    private int backPointer = -1;
    /** What MARK found under the cursor, so painting only repeats that change. */
    private byte paintFrom;

    private int menuPointer = -1;
    private float menuDownX;
    private float menuDownY;
    private float menuLastY;
    private boolean menuMoved;

    /** True once a finger has played Rose, which is a seat no controller holds. */
    private boolean fingerIsRose;

    private final Runnable aimLongPress = this::longPressSquare;
    private boolean taughtTouch;

    private float mm(float millimetres) {
        return millimetres * getResources().getDisplayMetrics().xdpi / 25.4f;
    }

    private float touchSlop() {
        return mm(1.6f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        noticeSomebody();
        if (secondaryClick(event)) {
            invalidate();
            return true;
        }
        if (!HudScene.touch()) {
            // The finger lands on the rows a controller's focus was showing, so the list
            // starts from there rather than jumping under it between down and up.
            ui.settingsScroll = SettingsScene.windowStart(ui.menu);
        }
        HudScene.setTouch(true);
        if (ui.tips.dismiss(now())) {
            invalidate();
        }
        if (tourTouch(event)) {
            invalidate();
            return true;
        }
        // The finger on the glass is Rose. A controller that arrives later takes Sky's seat.
        ui.joined[0] = true;
        claimRoseForTouch();
        ui.lastActive[0] = now();
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                touchDown(event.getPointerId(index), event.getX(index), event.getY(index));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    touchMove(event.getPointerId(i), event.getX(i), event.getY(i));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (touchUp(event.getPointerId(index), event.getX(index),
                        event.getY(index))) {
                    // That finger finished a tap: tell accessibility services a click
                    // happened, the way a stock View does. What the tap did is already done.
                    performClick();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseAllTouches();
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    /** True from a finger landing on the tour until every finger has lifted. */
    private boolean tourTouched;
    private boolean tourHeld;
    private final Runnable tourLongPress = this::tourHoldFired;

    /**
     * A finger on the tour: its buttons, and the glowing square. Acted on at the lift, so
     * the rest of the touch cannot fall through onto whatever the tour was covering; a
     * finger held on the glowing square of the cross step crosses it, as a long press does
     * on the board.
     *
     * @return true while this touch belongs to the tour
     */
    private boolean tourTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (!tourTouched) {
            if (!ui.tutorial || action != MotionEvent.ACTION_DOWN) {
                return false;
            }
            tourTouched = true;
            tourHeld = false;
            releaseAllTouches();
            if (ui.tour.step == Tutorial.STEP_CROSS
                    && renderer.tutorial().targetAt(event.getX(), event.getY())) {
                postDelayed(tourLongPress, TOUCH_LONG_PRESS_MS);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            tourTouched = false;
            removeCallbacks(tourLongPress);
            if (action == MotionEvent.ACTION_UP && ui.tutorial && !tourHeld) {
                float x = event.getX();
                float y = event.getY();
                int button = renderer.tutorial().buttonAt(x, y);
                if (button >= 0 && ui.tour.enabled(button)) {
                    ui.tour.focus = button;
                    pressTourButton(button);
                    performClick();
                } else if (ui.tour.step == Tutorial.STEP_FILL
                        && renderer.tutorial().targetAt(x, y)) {
                    tourActed(ui.tour.fill(now()));
                    performClick();
                }
            }
        }
        return true;
    }

    private void tourHoldFired() {
        if (!tourTouched || !ui.tutorial) {
            return;
        }
        tourHeld = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        tourActed(ui.tour.cross(now()));
        invalidate();
    }

    /**
     * Reports the click to accessibility services and autofill, and nothing more.
     *
     * <p>Called only for a finger that finished a tap; a drag, a stroke or a long press is
     * not a click and sends nothing.
     *
     * <p>The tap has already been acted on square by square in {@link #touchUp}, which is
     * the only place that knows what was under the finger, so this must not act on it a
     * second time. It is here because the platform's accessibility events for a click are
     * sent from {@code View.performClick}, and a view that swallows every touch in
     * {@code onTouchEvent} otherwise never sends them.
     */
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private static boolean fromAController(KeyEvent event) {
        int source = event.getSource();
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    /**
     * A real typing keyboard: a Chromebook's own, a tablet's case, a Bluetooth one.
     *
     * <p>{@code SOURCE_KEYBOARD} alone proves nothing. Gamepads carry it, the navigation
     * bar's back gesture carries it, and so do a phone's volume rocker and the on-screen
     * keyboard. What they do not have is a full set of letters on a physical device, so that
     * is what is asked — and never of a controller, which answers first.
     */
    private static boolean fromAKeyboard(KeyEvent event) {
        if (fromAController(event)
                || (event.getSource() & InputDevice.SOURCE_KEYBOARD)
                != InputDevice.SOURCE_KEYBOARD) {
            return false;
        }
        try {
            InputDevice device = event.getDevice();
            return device != null && !device.isVirtual()
                    && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
        } catch (RuntimeException problem) {
            return false;
        }
    }

    /** True while a mouse's right button is down, so the rest of that press is ignored. */
    private boolean secondaryDown;

    /**
     * A mouse's right button crosses out the square under the pointer, the way every
     * nonogram on a desktop has always worked. The left button already reaches the same
     * tap-to-fill a finger does, so the two buttons end up meaning fill and cross, and a
     * trackpad's two-finger click comes along for free.
     *
     * <p>Nowhere but the board has a use for it, so on the menus the press is simply
     * swallowed rather than being taken for a left click on whatever row it landed on.
     *
     * @return true when this event belonged to a right-button press
     */
    private boolean secondaryClick(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            int buttons = event.getButtonState();
            secondaryDown = event.isFromSource(InputDevice.SOURCE_MOUSE)
                    && (buttons & MotionEvent.BUTTON_SECONDARY) != 0
                    && (buttons & MotionEvent.BUTTON_PRIMARY) == 0;
            if (secondaryDown) {
                crossUnderPointer(event.getX(), event.getY());
            }
            return secondaryDown;
        }
        if (!secondaryDown) {
            return false;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            secondaryDown = false;
        }
        return true;
    }

    /**
     * The first time a finger or a mouse plays Rose, tells the registry her seat is taken.
     * A pad that was Rose until then may move over to Sky; it is announced, and any
     * direction it was holding on Rose's cursor is let go, since its key-up will now be
     * looked for on Sky's.
     */
    private void claimRoseForTouch() {
        if (fingerIsRose) {
            return;
        }
        fingerIsRose = true;
        players.setTouchRose(true);
        HoldRepeat roseHold = cursorHolds[PlayerRegistry.ROSE];
        if (roseHold.active && players.slotOf(roseHold.deviceId) == PlayerRegistry.SKY) {
            roseHold.clear();
        }
        if (!ui.joined[PlayerRegistry.SKY] && players.seatOccupied(PlayerRegistry.SKY)) {
            ui.joined[PlayerRegistry.SKY] = true;
            HudScene.setJoinedAt(now());
            tell(Theme.playerName(PlayerRegistry.SKY) + " joined the puzzle  ♥",
                    Theme.playerColor(PlayerRegistry.SKY));
            sfx.play(CozySfx.Sound.JOIN);
        }
        // Rose's seat may have just filled beside a Sky who was already here, so the score
        // follows the registry's count, announced or not.
        music.setPresence(players.playerCount() >= 2 ? 2 : 1);
    }

    private void crossUnderPointer(float x, float y) {
        if (ui.screen != UiState.GAME || ui.won) {
            return;
        }
        ui.joined[0] = true;
        claimRoseForTouch();
        ui.lastActive[0] = now();
        BoardLayout board = boardInPlay();
        int column = cellColumn(board, x);
        int row = cellRow(board, y);
        if (column < 0 || row < 0) {
            return;
        }
        placeCursor(0, column, row);
        crossSquare(0);
        checkForWin();
    }

    /** The first time a finger reaches a puzzle, say how the pen and the board split the work. */
    private void teachTouch() {
        tell(squaresAreDirect()
                        ? "Tap or drag to use the pen · hold for the other mark"
                        : "Tap or slide to aim, tap again to mark · or use MARK",
                Theme.CREAM);
    }

    /**
     * The board last drawn, but only while it is still the size of the one in play. A
     * touch landing between a new picture being dealt and its first frame would otherwise
     * be aimed with the old board's geometry, and index past the edge of a smaller one.
     */
    private BoardLayout boardInPlay() {
        BoardLayout board = renderer.board();
        return board != null && board.size == game.size ? board : null;
    }

    private boolean squaresAreDirect() {
        BoardLayout board = boardInPlay();
        return board != null && board.cell >= mm(TOUCH_DIRECT_MM);
    }

    private void touchDown(int pointer, float x, float y) {
        if (backPointer < 0 && HudScene.backButtonAt(x, y)) {
            backPointer = pointer;
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            HudScene.setTouchHeld(HudScene.TOUCH_BACK);
            return;
        }
        if (ui.screen != UiState.GAME || ui.won) {
            if (menuPointer < 0) {
                menuPointer = pointer;
                menuDownX = x;
                menuDownY = menuLastY = y;
                menuMoved = false;
                if (ui.screen == UiState.SETTINGS) {
                    // Lit while the finger rests on it, the way a phone's list answers a
                    // touch, and let go of the moment the finger starts to scroll.
                    ui.settingsPressed = renderer.settings().itemAt(x, y);
                }
            }
            return;
        }
        int button = HudScene.touchButtonAt(x, y);
        if (button >= 0) {
            if (buttonPointer < 0) {
                buttonPointer = pointer;
                pressTouchButton(button);
            }
            return;
        }
        if (aimPointer >= 0) {
            return;
        }
        if (!taughtTouch) {
            taughtTouch = true;
            teachTouch();
        }
        aimPointer = pointer;
        aimDownX = aimLastX = x;
        aimDownY = aimLastY = y;
        aimCarryX = aimCarryY = 0;
        aimMoved = false;
        aimLongPressed = false;
        aimDirect = squaresAreDirect();
        strokeAxis = 0;
        BoardLayout board = boardInPlay();
        aimBoard = board;
        aimCellX = cellColumn(board, x);
        aimCellY = cellRow(board, y);
        if (aimCellX >= 0 && aimCellY >= 0) {
            strokeFrom = game.puzzle.marks[aimCellY][aimCellX];
            postDelayed(aimLongPress, TOUCH_LONG_PRESS_MS);
        }
    }

    private void touchMove(int pointer, float x, float y) {
        if (pointer == menuPointer) {
            slideMenu(x, y);
            return;
        }
        if (pointer != aimPointer || ui.screen != UiState.GAME || ui.won) {
            return;
        }
        if (!aimMoved && Math.hypot(x - aimDownX, y - aimDownY) < touchSlop()) {
            return;
        }
        if (aimDirect && aimCellX >= 0 && aimCellY >= 0) {
            strokeTo(x, y);
            return;
        }
        if (!aimMoved) {
            aimMoved = true;
            removeCallbacks(aimLongPress);
            // A slide that starts on a square starts from that square, so the cursor is
            // where the thumb went down rather than wherever it happened to be before.
            if (aimCellX >= 0 && aimCellY >= 0 && !aimLongPressed) {
                placeCursor(0, aimCellX, aimCellY);
                paintIfHeld();
            }
        }
        BoardLayout board = aimBoard;
        float step = Math.max(board == null ? 0 : board.cell, mm(TOUCH_STEP_MM));
        aimCarryX += x - aimLastX;
        aimCarryY += y - aimLastY;
        aimLastX = x;
        aimLastY = y;
        while (Math.abs(aimCarryX) >= step || Math.abs(aimCarryY) >= step) {
            int dx = Math.abs(aimCarryX) >= step ? (int) Math.signum(aimCarryX) : 0;
            int dy = Math.abs(aimCarryY) >= step ? (int) Math.signum(aimCarryY) : 0;
            aimCarryX -= dx * step;
            aimCarryY -= dy * step;
            // Trackpad travel stops at the edge rather than wrapping: a thumb that runs
            // off the side of the board should not reappear on the other side of it.
            int nx = Math.max(0, Math.min(game.size - 1, game.cursorX[0] + dx));
            int ny = Math.max(0, Math.min(game.size - 1, game.cursorY[0] + dy));
            if (nx == game.cursorX[0] && ny == game.cursorY[0]) {
                aimCarryX = dx != 0 ? 0 : aimCarryX;
                aimCarryY = dy != 0 ? 0 : aimCarryY;
                continue;
            }
            placeCursor(0, nx, ny);
            paintIfHeld();
        }
    }

    /**
     * Carries a direct drag to the square under the finger.
     *
     * <p>The first time the finger leaves its slop the stroke begins: it takes the pen, or
     * the other mark if the finger had rested long enough to ask for it, and marks the
     * square it started on. The first square it crosses into decides the axis, and after
     * that the finger's position is read along that one line only, so a thumb that wanders
     * a little off a row keeps painting the row. Every square between the cursor and the
     * finger is visited in turn, so a fast flick cannot skip one.
     */
    private void strokeTo(float x, float y) {
        if (!aimMoved) {
            aimMoved = true;
            removeCallbacks(aimLongPress);
            strokeCross = aimLongPressed != ui.crossPen;
            placeCursor(0, aimCellX, aimCellY);
            if (!aimLongPressed) {
                markAtCursor(strokeCross);
            }
        }
        BoardLayout board = aimBoard;
        if (board == null || ui.won) {
            return;
        }
        int column = clampCell(board, (int) Math.floor((x - board.left) / board.cell));
        int row = clampCell(board, (int) Math.floor((y - board.top) / board.cell));
        if (strokeAxis == 0) {
            if (column == aimCellX && row == aimCellY) {
                return;
            }
            strokeAxis = Math.abs(column - aimCellX) >= Math.abs(row - aimCellY) ? 1 : 2;
        }
        if (strokeAxis == 1) {
            row = aimCellY;
        } else {
            column = aimCellX;
        }
        while (!ui.won && (game.cursorX[0] != column || game.cursorY[0] != row)) {
            int nx = game.cursorX[0] + Integer.signum(column - game.cursorX[0]);
            int ny = game.cursorY[0] + Integer.signum(row - game.cursorY[0]);
            placeCursor(0, nx, ny);
            if (game.markUnder(0) == strokeFrom) {
                markAtCursor(strokeCross);
            }
        }
    }

    private static int clampCell(BoardLayout board, int cell) {
        return Math.max(0, Math.min(board.size - 1, cell));
    }

    /** Uses a mark on the square under Rose's cursor, and sees whether that finished it. */
    private void markAtCursor(boolean cross) {
        if (cross) {
            crossSquare(0);
        } else {
            fillSquare(0);
        }
        checkForWin();
    }

    /**
     * A finger leaving the glass.
     *
     * @return true when it finished a tap — a button let go of where it was pressed, a menu
     *         row tapped rather than dragged (and a row, not the space between them), a
     *         square tapped rather than stroked or held —
     *         so {@link #onTouchEvent} reports a click only for something that was one
     */
    private boolean touchUp(int pointer, float x, float y) {
        if (pointer == backPointer) {
            backPointer = -1;
            HudScene.setTouchHeld(-1);
            // Like any button on a phone: sliding off it before letting go changes nothing.
            if (HudScene.backButtonAt(x, y)) {
                goBack();
                return true;
            }
            return false;
        }
        if (pointer == menuPointer) {
            menuPointer = -1;
            ui.settingsPressed = -1;
            return !menuMoved && tapMenu(x, y);
        }
        if (pointer == buttonPointer) {
            // The thumb pad acts as it is pressed, so letting go completes that press.
            buttonPointer = -1;
            buttonHeld = -1;
            HudScene.setTouchHeld(-1);
            return true;
        }
        if (pointer != aimPointer) {
            return false;
        }
        aimPointer = -1;
        removeCallbacks(aimLongPress);
        if (aimMoved || aimLongPressed || aimCellX < 0 || aimCellY < 0
                || ui.screen != UiState.GAME || ui.won) {
            return false;
        }
        boolean onCursor = aimCellX == game.cursorX[0] && aimCellY == game.cursorY[0];
        if (!onCursor) {
            placeCursor(0, aimCellX, aimCellY);
        }
        // Big squares take the pen on the first tap. Small ones are aimed at first, and a
        // second tap on the square the cursor is already sitting on uses it.
        if (onCursor || aimDirect) {
            markAtCursor(ui.crossPen);
        }
        return true;
    }

    private void releaseAllTouches() {
        removeCallbacks(aimLongPress);
        aimPointer = -1;
        buttonPointer = -1;
        buttonHeld = -1;
        backPointer = -1;
        menuPointer = -1;
        ui.settingsPressed = -1;
        HudScene.setTouchHeld(-1);
    }

    /** A finger resting on a square: the other mark, with a tick you can feel. */
    private void longPressSquare() {
        if (aimPointer < 0 || aimMoved || aimCellX < 0 || ui.screen != UiState.GAME
                || ui.won) {
            return;
        }
        aimLongPressed = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        placeCursor(0, aimCellX, aimCellY);
        markAtCursor(!ui.crossPen);
        invalidate();
    }

    private void pressTouchButton(int button) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        switch (button) {
            case HudScene.TOUCH_FILL:
            case HudScene.TOUCH_CROSS:
                HudScene.setTouchHeld(button);
                choosePen(button == HudScene.TOUCH_CROSS);
                break;
            case HudScene.TOUCH_MARK:
                buttonHeld = button;
                HudScene.setTouchHeld(button);
                paintFrom = game.markUnder(0);
                markAtCursor(ui.crossPen);
                break;
            case HudScene.TOUCH_HINT:
                HudScene.setTouchHeld(button);
                useHint(0);
                checkForWin();
                break;
            default:
                HudScene.setTouchHeld(-1);
                openSettings(0);
                break;
        }
    }

    /** Picks up the other pen, and says so once. */
    private void choosePen(boolean cross) {
        if (ui.crossPen == cross) {
            return;
        }
        ui.crossPen = cross;
        sfx.play(CozySfx.Sound.MOVE);
        // tell() already speaks it for TalkBack.
        tell(cross ? "Taps cross squares out" : "Taps fill squares",
                cross ? Theme.GOLD : Theme.PINK);
    }

    /**
     * Holding MARK while the other thumb slides paints the squares on the way, but only
     * ones that look like the square the press started on. With the fill pen over an
     * empty square that fills a run of empties and leaves filled and crossed squares
     * alone; over a filled one it clears a run of fills. That is the whole difference
     * between painting a line and scribbling over it.
     */
    private void paintIfHeld() {
        if (buttonHeld != HudScene.TOUCH_MARK || ui.won || game.markUnder(0) != paintFrom) {
            return;
        }
        markAtCursor(ui.crossPen);
    }

    /** Puts a player's cursor on a square, with everything a step there would do. */
    private void placeCursor(int who, int x, int y) {
        int dx = Integer.signum(x - game.cursorX[who]);
        int dy = Integer.signum(y - game.cursorY[who]);
        if (dx == 0 && dy == 0) {
            return;
        }
        game.cursorX[who] = x;
        game.cursorY[who] = y;
        ui.cursorMovedAt[who] = now();
        forgetHeldSquare(who);
        sfx.move(who, dx, dy, false);
        noticeTheOtherCushion(who);
        if (speaking()) {
            announce(describeSquare(game, who));
        }
    }

    private static int cellColumn(BoardLayout board, float x) {
        if (board == null) {
            return -1;
        }
        int column = (int) Math.floor((x - board.left) / board.cell);
        return column >= 0 && column < board.size ? column : -1;
    }

    private static int cellRow(BoardLayout board, float y) {
        if (board == null) {
            return -1;
        }
        int row = (int) Math.floor((y - board.top) / board.cell);
        return row >= 0 && row < board.size ? row : -1;
    }

    /**
     * A drag in the cozy corner scrolls the list itself, following the finger a pixel at a
     * time. It used to walk the highlight a row per row's-height of travel instead, so the
     * lit row raced up and down under the finger and the list only moved once the lit row
     * reached the middle — the right answer for a remote and a strange one for a thumb.
     * A drag that starts on a row lets go of it, so scrolling is never taken for a tap.
     */
    private void slideMenu(float x, float y) {
        if (ui.screen == UiState.HELP) {
            if (!menuMoved && Math.hypot(x - menuDownX, y - menuDownY) < touchSlop()) {
                return;
            }
            menuMoved = true;
            // A tall page follows the finger, as any page on a phone does.
            ui.helpScroll = clampHelpScroll(ui.helpScroll - (y - menuLastY));
            menuLastY = y;
            return;
        }
        if (ui.screen != UiState.SETTINGS) {
            return;
        }
        float pitch = renderer.settings().rowPitch();
        if (pitch <= 0) {
            return;
        }
        if (!menuMoved && Math.hypot(x - menuDownX, y - menuDownY) < touchSlop()) {
            return;
        }
        menuMoved = true;
        ui.settingsPressed = -1;
        // Dragging up brings later rows into view, as every list on a phone does.
        ui.settingsScroll = SettingsScene.clampScroll(
                SettingsScene.clampScroll(ui.settingsScroll) - (y - menuLastY) / pitch);
        menuLastY = y;
    }

    /**
     * A tap on a menu row, a stepper, or the win card.
     *
     * @return true when it landed on something that acted — the win card, a home row or
     *         its stepper, a Cozy Corner row — and false for blank space between them
     */
    private boolean tapMenu(float x, float y) {
        if (ui.screen == UiState.GAME && ui.won) {
            // Anywhere on the card: it either deals the next picture or finishes the
            // entrance, and handleWinKey always acts on a confirm.
            handleWinKey(KeyEvent.KEYCODE_BUTTON_A, false);
            return true;
        }
        if (ui.screen == UiState.HOME) {
            HomeScene home = renderer.home();
            int item = home.itemAt(x, y);
            if (item < 0) {
                return false;
            }
            int step = home.stepAt(item, x);
            if (ui.menu != item) {
                HomeScene.disarmRestart();
                ui.menu = item;
                sfx.play(CozySfx.Sound.MOVE);
            }
            if (step != 0) {
                if (item == HomeScene.ITEM_SIZE) {
                    nudgeBoardSize(step * 5);
                } else {
                    browseStoryChapter(step);
                }
                return true;
            }
            chooseHomeItem(0);
            return true;
        }
        if (ui.screen == UiState.HELP) {
            HelpScene help = renderer.help();
            int tab = help.tabAt(x, y);
            if (tab >= 0) {
                showHelpPage(tab);
                return true;
            }
            if (help.tourAgainAt(x, y)) {
                startTour();
                return true;
            }
            return false;
        }
        if (ui.screen == UiState.SETTINGS) {
            int item = renderer.settings().itemAt(x, y);
            if (item < 0) {
                return false;
            }
            if (ui.menu != item) {
                // Moving onto a question is not an answer to it.
                SettingsScene.disarmDefaults();
                ui.menu = item;
            }
            ui.settingsTouched = item;
            chooseSetting();
            return true;
        }
        return false;
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

    private void armCursorHold() {
        removeCallbacks(cursorHoldTick);
        postDelayed(cursorHoldTick, 16);
    }

    private void armMenuHold() {
        removeCallbacks(menuHoldTick);
        postDelayed(menuHoldTick, 16);
    }

    /**
     * Walks every held board direction that is still down. Runs off the view's own
     * clock so a pad that never sends a second event still crosses the board.
     */
    private void tickCursorHolds() {
        if (ui.screen != UiState.GAME || ui.won) {
            cursorHolds[0].clear();
            cursorHolds[1].clear();
            return;
        }
        long moment = now();
        boolean any = false;
        for (int who = 0; who < cursorHolds.length; who++) {
            HoldRepeat hold = cursorHolds[who];
            if (!hold.active) {
                continue;
            }
            if (hold.keyCode == HoldRepeat.FROM_STICK
                    && !players.stickDeflected(hold.deviceId)) {
                hold.clear();
                continue;
            }
            any = true;
            if (hold.due(moment, Theme.REPEAT_FIRST_MS, Theme.BOARD_REPEAT_MS)) {
                moveCursor(hold.who, hold.dx, hold.dy);
            }
        }
        if (any) {
            invalidate();
            postDelayed(cursorHoldTick, 16);
        }
    }

    private void tickMenuHold() {
        if (!menuHold.active
                || (ui.screen != UiState.HOME && ui.screen != UiState.SETTINGS)) {
            menuHold.clear();
            return;
        }
        if (menuHold.keyCode == HoldRepeat.FROM_STICK
                && !players.stickDeflected(menuHold.deviceId)) {
            menuHold.clear();
            return;
        }
        long moment = now();
        if (menuHold.due(moment, Theme.REPEAT_FIRST_MS, Theme.MENU_REPEAT_MS)
                && menuHold.dy != 0) {
            int count = ui.screen == UiState.HOME
                    ? HomeScene.ITEM_COUNT : SettingsScene.ITEM_COUNT;
            stepMenu(menuHold.dy, count, false);
        }
        invalidate();
        postDelayed(menuHoldTick, 16);
    }

    /**
     * Starts travelling in this direction. The opening square is taken here; everything
     * after that comes from {@link #tickCursorHolds}. A matching hold already in flight
     * — the D-pad key that follows a hat event, or Android's own key-repeat — is ignored
     * so the same push cannot take two squares.
     */
    private void beginCursorHold(int who, int dx, int dy, int deviceId, int keyCode) {
        long moment = now();
        if (cursorHolds[who].matches(who, dx, dy)) {
            cursorHolds[who].adopt(who, dx, dy, deviceId, keyCode, moment);
            armCursorHold();
            return;
        }
        cursorHolds[who].adopt(who, dx, dy, deviceId, keyCode, moment);
        moveCursor(who, dx, dy);
        armCursorHold();
    }

    private void beginMenuHold(int who, int dy, int deviceId, int keyCode, int itemCount) {
        long moment = now();
        if (menuHold.matches(who, 0, dy)) {
            menuHold.adopt(who, 0, dy, deviceId, keyCode, moment);
            armMenuHold();
            return;
        }
        menuHold.adopt(who, 0, dy, deviceId, keyCode, moment);
        stepMenu(dy, itemCount, false);
        armMenuHold();
    }

    // ---- Home screen ---------------------------------------------------------------

    private boolean handleHomeKey(int key, KeyEvent event, int who, boolean repeat) {
        if (PlayerRegistry.isBack(key)) {
            store.save(game, ui);
            return super.onKeyDown(key, event);
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            beginMenuHold(0, -1, event.getDeviceId(), key, HomeScene.ITEM_COUNT);
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            beginMenuHold(0, 1, event.getDeviceId(), key, HomeScene.ITEM_COUNT);
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
        }
        // Anything else that reaches here is still one of the game's own keys — the router
        // has already handed the volume and the rest back — and it stays ours even though
        // this screen has no use for it. Unhandled gamepad buttons come back from the
        // platform as fallback keys, and B's fallback is BACK: returning false for a cross
        // press on the title screen would drop the player out to the launcher.
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
        ui.menu = ui.screen == UiState.SETTINGS ? SettingsScene.step(ui.menu, direction)
                : Math.floorMod(ui.menu + direction, itemCount);
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
        // A finger or a centre button still down from the last board belongs to that
        // board: carried over, its squares would land on (or past the edge of) this one.
        removeCallbacks(aimLongPress);
        aimPointer = -1;
        aimBoard = null;
        for (int who = 0; who < centreDownAt.length; who++) {
            centreDownAt[who] = 0;
            forgetHeldSquare(who);
        }
    }

    // ---- Cozy corner ---------------------------------------------------------------

    private boolean handleSettingsKey(int key, KeyEvent event, boolean repeat) {
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            beginMenuHold(0, -1, event.getDeviceId(), key, SettingsScene.ITEM_COUNT);
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            beginMenuHold(0, 1, event.getDeviceId(), key, SettingsScene.ITEM_COUNT);
        } else if (PlayerRegistry.isConfirm(key) && !repeat) {
            chooseSetting();
        } else if (!repeat && (PlayerRegistry.isBack(key) || PlayerRegistry.isCross(key)
                || PlayerRegistry.isMenu(key))) {
            // A held B leaves once; its repeats must not go on to cross squares out.
            leaveSettings();
        }
        // True for the same reason as the title screen: only the game's own keys get this
        // far, and an unhandled gamepad button would come back as a fallback BACK.
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
        int asked = SettingsScene.consumeRequest();
        if (asked >= 0) {
            actOnRequest(asked);
            return;
        }
        if (SettingsScene.consumeStoryRestart()) {
            // Only the book starts over. The pictures already on the wall (and the
            // endless count and tonight's tally that read them) stay where they are.
            game.storyFurthest = 0;
            game.storyCompleted = 0;
            game.startStory(0);
            HomeScene.setPendingChapter(0);
            leaveSettings();
            enterGame();
            return;
        }
        // Sound hushed for a call, pulled headphones or another app taking the room stays
        // hushed through an unrelated switch (Larger Text, say); only the Music or Sounds
        // row switched on is somebody asking for sound again. Switching one off is not: it
        // must not wake the other one up through the speaker.
        if ((row == SettingsScene.ITEM_MUSIC && ui.musicOn)
                || (row == SettingsScene.ITEM_SFX && ui.sfxOn)) {
            soundResting = false;
        }
        if (!soundResting) {
            music.setEnabled(ui.musicOn);
        }
        applyPlayerCount();
        // The chime rises for something turned on and falls for something turned off, so
        // the answer is audible even for a row whose effect is on another screen.
        boolean switchedOn = SettingsScene.hasSwitch(row) && SettingsScene.states(ui)[row];
        if (row == SettingsScene.ITEM_SFX && !ui.sfxOn) {
            // Say goodbye while the mixer is still listening, then make the room quiet.
            sfx.select(CozySfx.ROOM, false);
            sfx.setEnabled(false);
        } else if (!soundResting) {
            sfx.setEnabled(ui.sfxOn);
            sfx.select(CozySfx.ROOM, switchedOn);
        }
        announce(SettingsScene.bottomLine(row, now()));
        store.save(game, ui);
    }

    /**
     * Opens or shuts Sky's seat to match {@link UiState#twoPlayers}, after the switch or a
     * reset has changed it. Rose keeps her seat without a controller only where a finger
     * has actually been playing her — the finger is no controller the registry has ever
     * heard of — and nowhere else, so a television whose Rose pad has gone does not go
     * on drawing her cursor.
     */
    private void applyPlayerCount() {
        if (players.solo() == !ui.twoPlayers) {
            return;
        }
        players.setSolo(!ui.twoPlayers);
        ui.joined[0] = players.seatOccupied(PlayerRegistry.ROSE) || fingerIsRose;
        ui.joined[1] = players.seatOccupied(PlayerRegistry.SKY);
        music.setPresence(players.playerCount() >= 2 ? 2 : 1);
    }

    /** Opens the cozy corner, remembering where to return to and who reached for it. */
    private void openSettings(int who) {
        ui.screenBeforeSettings = ui.screen;
        ui.menuBeforeSettings = ui.menu;
        ui.screen = UiState.SETTINGS;
        ui.menu = 0;
        ui.settingsScroll = 0;
        ui.settingsPressed = -1;
        ui.settingsTouched = -1;
        // Only worth saying whose hand it was when there are two hands in the room.
        SettingsScene.setTidyingPlayer(ui.twoPlayers ? who : -1);
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

    /**
     * The cozy corner rows whose work is done out here: How to play opens a screen, and
     * Share and Send feedback hand an intent to Android. Neither intent can fail loudly: a
     * television with no share sheet or no browser gets the address in words instead.
     */
    private void actOnRequest(int row) {
        sfx.play(CozySfx.Sound.SELECT);
        switch (row) {
            case SettingsScene.ITEM_HELP:
                openHelp();
                return;
            case SettingsScene.ITEM_SHARE:
                if (!launch(Intent.createChooser(shareIntent(), "Share CozyGrams"),
                        shareIntent())) {
                    ui.canShare = false;
                    SettingsScene.say("Tell a friend: CozyGrams is on Google Play", now());
                    announce("There's nothing to share with here. Tell a friend: "
                            + "CozyGrams is on Google Play.");
                }
                return;
            case SettingsScene.ITEM_RATE:
                // The store app if there is one, its web page if not.
                if (!launch(rateIntent(), rateIntent())
                        && !launch(ratePageIntent(), ratePageIntent())) {
                    SettingsScene.say("CozyGrams is on Google Play", now());
                    announce("There's no store here. CozyGrams is on Google Play.");
                }
                return;
            case SettingsScene.ITEM_FEEDBACK:
                if (!launch(feedbackIntent(), feedbackIntent())) {
                    ui.canBrowse = false;
                    SettingsScene.say(SettingsScene.FEEDBACK_URL, now());
                    announce("There's no browser here. Visit "
                            + SettingsScene.FEEDBACK_URL + " on a phone or computer.");
                }
                return;
            default:
                return;
        }
    }

    /** The Google Play page, which is the one link the share sheet carries. */
    static final String PLAY_URL =
            "https://play.google.com/store/apps/details?id=com.cozygrams.tv";

    /** The new-issue page, where an idea or a bug can be written down. */
    static final String FEEDBACK_PAGE = "https://github.com/bradflaugher/CozyGrams/issues/new";

    /** What a share says: one warm line and the link. */
    static String shareText() {
        return "CozyGrams is a cozy nonogram puzzle game for TV, phone and tablet, "
                + "made to solve together: " + PLAY_URL;
    }

    private static Intent shareIntent() {
        return new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "CozyGrams")
                .putExtra(Intent.EXTRA_TEXT, shareText());
    }

    /** The Play Store app's own page for the game. */
    private static Intent rateIntent() {
        return new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.cozygrams.tv"));
    }

    /** The same page on the web, for a device with a browser but no store app. */
    private static Intent ratePageIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_URL))
                .addCategory(Intent.CATEGORY_BROWSABLE);
    }

    private static Intent feedbackIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(FEEDBACK_PAGE))
                .addCategory(Intent.CATEGORY_BROWSABLE);
    }

    /** True when some app on this device would take the intent. */
    private boolean resolves(Intent intent) {
        try {
            return intent.resolveActivity(getContext().getPackageManager()) != null;
        } catch (RuntimeException problem) {
            return false;
        }
    }

    /**
     * Starts {@code intent} if {@code probe} has somewhere to go, and says whether it did.
     * Every way this can fail is caught, because a television that has no browser must
     * answer with words, never with a crash.
     */
    private boolean launch(Intent intent, Intent probe) {
        if (!resolves(probe)) {
            return false;
        }
        try {
            Context context = getContext();
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException problem) {
            return false;
        }
    }

    // ---- How to play ---------------------------------------------------------------

    private void openHelp() {
        ui.screen = UiState.HELP;
        ui.helpScroll = 0;
        ui.helpPage = 0;
        announce(HelpScene.spoken(ui.helpPage));
    }

    /** Back to the cozy corner, with How to play still highlighted. */
    private void leaveHelp() {
        ui.screen = UiState.SETTINGS;
        ui.menu = SettingsScene.ITEM_HELP;
        ui.settingsScroll = SettingsScene.clampScroll(
                SettingsScene.slotOf(ui.menu) - SettingsScene.VISIBLE_ROWS / 2);
        if (speaking()) {
            announce(describeMenu());
        }
    }

    private void showHelpPage(int page) {
        int next = Math.floorMod(page, HelpScene.PAGE_COUNT);
        if (next != ui.helpPage) {
            sfx.play(CozySfx.Sound.MOVE);
        }
        ui.helpPage = next;
        ui.helpScroll = 0;
        announce(HelpScene.spoken(ui.helpPage));
    }

    private float clampHelpScroll(float pixels) {
        return Math.max(0, Math.min(renderer.help().scrollMax(), pixels));
    }

    private boolean handleHelpKey(int key, KeyEvent event, boolean repeat) {
        if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // A hat switch has already turned the page for this press; see handleHomeKey.
            if (players.stickSteppedRecently(event.getDeviceId(), now())) {
                return true;
            }
            if (menuStepIsAllowed(repeat)) {
                showHelpPage(ui.helpPage + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1));
            }
        } else if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (players.stickSteppedRecently(event.getDeviceId(), now())) {
                return true;
            }
            scrollHelp(key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
        } else if (PlayerRegistry.isConfirm(key) && !repeat) {
            sfx.play(CozySfx.Sound.SELECT);
            startTour();
        } else if (!repeat && (PlayerRegistry.isBack(key) || PlayerRegistry.isCross(key)
                || PlayerRegistry.isMenu(key))) {
            leaveHelp();
        }
        // Ours either way, for the same fallback-BACK reason as the other menus.
        return true;
    }

    /** Moves a tall page by a few lines; a page that fits does not move at all. */
    private void scrollHelp(int direction) {
        HelpScene help = renderer.help();
        ui.helpScroll = clampHelpScroll(ui.helpScroll + direction * help.lineStep() * 2);
    }

    // ---- The tour ------------------------------------------------------------------

    /** Starts the little tour from its first step, over whatever is showing. */
    private void startTour() {
        ui.tutorial = true;
        ui.tour.start(now());
        ui.tips.hide(now());
        releaseAllTouches();
        announce(ui.tour.spoken(TutorialScene.hands()));
    }

    /** Finishes or skips the tour; either way it is not offered again on its own. */
    private void endTour() {
        ui.tutorial = false;
        removeCallbacks(tourLongPress);
        store.welcomeSeen();
        ui.screenSince = now();
        sfx.play(CozySfx.Sound.SELECT);
        if (speaking()) {
            announce(ui.screen == UiState.HELP ? HelpScene.spoken(ui.helpPage)
                    : describeMenu());
        }
    }

    /**
     * A key on the tour. Left and right move between Skip, Back and Next; the confirm
     * button presses the one in focus — or, on a step that asks for it, fills the glowing
     * square first. B and X cross it out where crossing is asked for, a remote's OK gets
     * there through fill as it does on the board, and Back or Menu leave the tour.
     */
    private void handleTourKey(int key) {
        Tutorial tour = ui.tour;
        long moment = now();
        int hands = TutorialScene.hands();
        if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            moveTourFocus(key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
        } else if (PlayerRegistry.isConfirm(key)) {
            if (tour.awaitingAction() && tour.focus == Tutorial.FOCUS_NEXT) {
                if (tour.step == Tutorial.STEP_FILL) {
                    tourActed(tour.fill(moment));
                    return;
                }
                if (hands == Tutorial.HANDS_REMOTE || hands == Tutorial.HANDS_UNKNOWN) {
                    boolean done = tour.cycle(moment);
                    sfx.play(CozySfx.Sound.SELECT);
                    if (tour.acted) {
                        tourActed(done);
                    }
                    return;
                }
            }
            pressTourButton(tour.focus);
        } else if (PlayerRegistry.isCross(key)) {
            tourActed(tour.cross(moment));
        } else if (PlayerRegistry.isBack(key) || PlayerRegistry.isMenu(key)) {
            endTour();
        }
    }

    private void moveTourFocus(int direction) {
        int before = ui.tour.focus;
        ui.tour.moveFocus(direction);
        if (ui.tour.focus != before) {
            sfx.play(CozySfx.Sound.MOVE);
            announce(Tutorial.buttonName(ui.tour.focus, ui.tour.isLast()) + " button");
        }
    }

    private void pressTourButton(int button) {
        long moment = now();
        switch (button) {
            case Tutorial.FOCUS_SKIP:
                endTour();
                return;
            case Tutorial.FOCUS_BACK:
                ui.tour.back(moment);
                sfx.play(CozySfx.Sound.MOVE);
                break;
            default:
                if (!ui.tour.next(moment)) {
                    endTour();
                    return;
                }
                sfx.play(CozySfx.Sound.SELECT);
                break;
        }
        announce(ui.tour.spoken(TutorialScene.hands()));
    }

    /** The player filled or crossed the glowing square: a chime, and the words change. */
    private void tourActed(boolean acted) {
        if (!acted) {
            return;
        }
        sfx.play(ui.tour.step == Tutorial.STEP_FILL ? CozySfx.Sound.FILL : CozySfx.Sound.CROSS);
        announce(ui.tour.title() + ". " + ui.tour.body(TutorialScene.hands()));
    }

    // ---- One-time tips --------------------------------------------------------------

    private int tipScreen = -1;
    private boolean tipWon;

    /**
     * Offers the next tip that belongs on this screen, once the screen has settled, and
     * lets the one showing go when its time is up. Called before each frame is drawn.
     */
    private void updateTips(long moment) {
        if (ui.tutorial) {
            return;
        }
        if (ui.screen != tipScreen || ui.won != tipWon) {
            tipScreen = ui.screen;
            tipWon = ui.won;
            ui.screenSince = moment;
        }
        boolean touch = HudScene.touch();
        ui.tips.tick(moment, ui, touch);
        if (ui.tips.showing >= 0) {
            return;
        }
        int tip = ui.tips.next(ui, touch);
        if (tip < 0) {
            return;
        }
        if (ui.tips.ready(moment, ui.screenSince) && renderer.tipAnchor(tip, ui) != null) {
            ui.tips.show(tip, moment);
            store.writeTips(ui.tips.seen);
            announce(Tips.text(tip, TutorialScene.hands()));
        } else if (moment - ui.screenSince < 15_000) {
            // A tip is due on this screen; make sure a frame comes to show it, even when
            // the screen has otherwise gone still.
            postInvalidateDelayed(Tips.GAP_MS / 2);
        }
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
            goHome();
            return true;
        }

        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                    beginCursorHold(who, -1, 0, event.getDeviceId(), key);
                } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    beginCursorHold(who, 1, 0, event.getDeviceId(), key);
                } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    beginCursorHold(who, 0, -1, event.getDeviceId(), key);
                } else {
                    beginCursorHold(who, 0, 1, event.getDeviceId(), key);
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

    /** Leaves a puzzle for the title screen, keeping the board exactly as it is. */
    private void goHome() {
        ui.screen = UiState.HOME;
        ui.menu = 0;
        if (speaking()) {
            announce(describeMenu());
        }
        store.save(game, ui);
    }

    /**
     * Back delivered by the platform rather than as a key — see
     * {@code MainActivity.catchBackIfTheKeyStopsComing}.
     *
     * @return false on the title screen, where Back belongs to the system
     */
    public boolean backFromSystem() {
        noticeSomebody();
        if (ui.screen == UiState.HOME && !ui.tutorial) {
            store.save(game, ui);
            return false;
        }
        goBack();
        invalidate();
        return true;
    }

    /**
     * The on-screen back button: exactly what Back does on each screen it is drawn on, by
     * the same paths, so the button and the gesture can never disagree.
     */
    private void goBack() {
        if (ui.tutorial) {
            endTour();
        } else if (ui.screen == UiState.HELP) {
            leaveHelp();
        } else if (ui.screen == UiState.SETTINGS) {
            leaveSettings();
        } else if (ui.screen == UiState.GAME && ui.won) {
            handleWinKey(KeyEvent.KEYCODE_BACK, false);
        } else if (ui.screen == UiState.GAME) {
            goHome();
        }
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
        // A press that began on another board (the partner dealt a new one mid-hold) has
        // no square here to put back.
        boolean sameBoard = holdX[who] < game.size && holdY[who] < game.size;
        // Only take the fill back when a hint is actually coming to replace it: with hints
        // resting, or nothing left to reveal, the hold would otherwise just erase a square.
        if (sameBoard && ui.hintsOn && game.hintAvailable()
                && game.puzzle.marks[holdY[who]][holdX[who]] != holdMark[who]) {
            game.undoMark(who, holdX[who], holdY[who], holdMark[who]);
        }
        forgetHeldSquare(who);
        centreDownAt[who] = 0;
        useHint(who);
        checkForWin();
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
            announce(describeSquare(game, who));
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
        if (event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            // A mouse or a trackpad. Moving the pointer is somebody being here; the wheel
            // is the only part of it the game has a use for here.
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_HOVER_MOVE
                    || action == MotionEvent.ACTION_SCROLL) {
                noticeSomebody();
            }
            if (action == MotionEvent.ACTION_SCROLL
                    && scrollMenu(event.getAxisValue(MotionEvent.AXIS_VSCROLL))) {
                invalidate();
                return true;
            }
            return super.onGenericMotionEvent(event);
        }
        int[] step = players.stickStep(event, now());
        if (step != null) {
            // Only a stick that actually produced a step counts as somebody here. A noisy
            // axis sends events all night, and it must not keep the screen awake with them.
            noticeSomebody();
        }
        if (step == null) {
            releaseStickHolds(event.getDeviceId());
            if (players.justStirred()) {
                // Somebody pushed a stick on a controller that has not joined yet. Nothing
                // moved, but the empty seat can show that the room noticed.
                HudScene.setSeatStirredAt(openSeat(), now());
                invalidate();
            }
            // The stick rule above has already decided this movement means nothing.
            // Handed back, the platform would turn the same axes into D-pad key presses of
            // its own, with their own repeat: an unarmed pad's first twitch would take a
            // seat after all, and a stick drifting inside our dead zone would walk a cursor.
            if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
                return true;
            }
            return super.onGenericMotionEvent(event);
        }
        int who = registerDevice(event.getDeviceId());
        ui.lastActive[who] = now();

        ui.tips.dismiss(now());
        if (ui.tutorial) {
            // The stick moves the tour's focus, as the D-pad does.
            if (step[0] != 0) {
                moveTourFocus(step[0]);
            }
            invalidate();
            return true;
        }
        if (ui.screen == UiState.HELP) {
            if (step[0] != 0) {
                showHelpPage(ui.helpPage + step[0]);
            } else if (step[1] != 0) {
                scrollHelp(step[1]);
            }
            invalidate();
            return true;
        }
        if (ui.screen == UiState.GAME && !ui.won) {
            beginCursorHold(who, step[0], step[1], event.getDeviceId(),
                    HoldRepeat.FROM_STICK);
        } else if (ui.screen == UiState.HOME) {
            // Xbox D-pads report as hat axes, not as KEYCODE_DPAD_LEFT/RIGHT. Vertical
            // still moves the highlight; horizontal is the story/size stepper — and used
            // to be swallowed, which is why the chevrons never moved.
            if (step[1] != 0) {
                beginMenuHold(0, step[1], event.getDeviceId(), HoldRepeat.FROM_STICK,
                        HomeScene.ITEM_COUNT);
            } else if (step[0] != 0) {
                if (ui.menu == HomeScene.ITEM_SIZE) {
                    nudgeBoardSize(step[0] * 5);
                } else if (ui.menu == HomeScene.ITEM_STORY) {
                    browseStoryChapter(step[0]);
                }
            }
        } else if (step[1] != 0 && ui.screen == UiState.SETTINGS) {
            beginMenuHold(0, step[1], event.getDeviceId(), HoldRepeat.FROM_STICK,
                    SettingsScene.ITEM_COUNT);
        }
        invalidate();
        return true;
    }

    /** Wheel travel not yet spent on a row: a trackpad scrolls in fractions of a notch. */
    private float wheelCarry;

    /**
     * The wheel moves the highlight on the title screen and in the cozy corner, a row per
     * notch — wheel up is the row above, as in every list. On the board it does nothing:
     * there is no single direction a wheel could mean there that would not surprise
     * somebody.
     *
     * @return true when a menu took the scroll
     */
    private boolean scrollMenu(float amount) {
        int count;
        if (ui.tutorial) {
            return true;
        }
        if (ui.screen == UiState.HELP) {
            ui.helpScroll = clampHelpScroll(ui.helpScroll - amount * renderer.help().lineStep());
            return true;
        }
        if (ui.screen == UiState.HOME) {
            count = HomeScene.ITEM_COUNT;
        } else if (ui.screen == UiState.SETTINGS) {
            count = SettingsScene.ITEM_COUNT;
        } else {
            wheelCarry = 0;
            return false;
        }
        if (amount == 0) {
            return true;
        }
        if (ui.screen == UiState.SETTINGS && HudScene.touch()) {
            // A finger's list is drawn where it was dragged to, not around the focus, so
            // the wheel scrolls it the way a drag does — wheel up shows the rows above.
            wheelCarry = 0;
            ui.settingsScroll = SettingsScene.clampScroll(
                    SettingsScene.clampScroll(ui.settingsScroll) - amount);
            return true;
        }
        if (Math.signum(amount) != Math.signum(wheelCarry)) {
            // Changing direction starts afresh, so a trackpad's leftover fraction from the
            // last swipe cannot eat the first part of this one.
            wheelCarry = 0;
        }
        wheelCarry += amount;
        while (Math.abs(wheelCarry) >= 1) {
            int direction = wheelCarry > 0 ? -1 : 1;
            wheelCarry += direction;
            stepMenu(direction, count, false);
        }
        return true;
    }

    /** A stick or hat that has come back through centre must stop walking. */
    private void releaseStickHolds(int deviceId) {
        if (players.stickDeflected(deviceId)) {
            return;
        }
        cursorHolds[0].releaseStick(deviceId);
        cursorHolds[1].releaseStick(deviceId);
        menuHold.releaseStick(deviceId);
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
        updateTips(now);
        ui.animateCursors(game, frameGap(lastFrameAt, now));
        lastFrameAt = now;
        renderer.draw(canvas, getWidth(), getHeight(), game, ui, effects, now);
        // Whatever this frame does next replaces the slow frame already waiting, if any.
        removeCallbacks(settledFrame);
        long next = idle.frameDelay(renderer.animating(game, ui, effects, now), now);
        if (next == IdleWatch.EVERY_VSYNC) {
            postInvalidateOnAnimation();
        } else if (next > 0) {
            postDelayed(settledFrame, next);
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
