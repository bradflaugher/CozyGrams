import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.cozygrams.tv.BoardLayout;
import com.cozygrams.tv.Comfort;
import com.cozygrams.tv.Effects;
import com.cozygrams.tv.GameState;
import com.cozygrams.tv.HomeScene;
import com.cozygrams.tv.HudScene;
import com.cozygrams.tv.Puzzle;
import com.cozygrams.tv.PuzzleGenerator;
import com.cozygrams.tv.PuzzleLibrary;
import com.cozygrams.tv.Renderer;
import com.cozygrams.tv.SettingsScene;
import com.cozygrams.tv.Theme;
import com.cozygrams.tv.UiState;
import com.cozygrams.tv.WinScene;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;

/**
 * Renders CozyGrams to PNG on a desktop JVM — as fixed screens, or as motion.
 *
 * <p>The app draws its whole interface onto one {@code android.graphics.Canvas}, so with
 * the Java2D-backed stubs in {@code tools/preview/stubs} the real renderer runs unchanged
 * here — no emulator, no device, no {@code View}.
 *
 * <p><b>Two modes.</b> The default writes one still per scenario. {@code --clips} instead
 * winds the same clock forward one device frame at a time while replaying a script of
 * controller presses, and writes a numbered PNG sequence plus a contact sheet per clip —
 * which {@code record.sh} encodes to video. Stills answer "does it look right"; clips
 * answer "does it feel right", and that is the half of a cozy interface a screenshot
 * cannot show.
 *
 * <p><b>Determinism.</b> Every timestamp is derived from {@link #T0} and every board from
 * a fixed seed. Nothing calls {@code System.currentTimeMillis()} or {@code Math.random()},
 * so re-running the harness produces byte-identical files and screenshots can be diffed
 * across commits. A clip's presses fire at their scripted instant rather than at whichever
 * frame happens to notice them, so the same clip captured at 15 fps and at 60 emits
 * exactly the same effects at exactly the same milliseconds. The corner stamp
 * {@link Provenance} burns into every still is part of that guarantee and not an exception
 * to it — see the note there on why it carries a commit but never a clock.
 *
 * <pre>
 *   Preview &lt;outputDir&gt; [width] [height]
 *   Preview --clips &lt;outputDir&gt; [width] [height] [fps] [clipName]
 *   Preview --list-clips
 * </pre>
 */
public final class Preview {

    /**
     * The frozen clock, in the "milliseconds of uptime" units the game uses. Half an hour
     * in, so idle breathing and pulse phases land somewhere representative rather than at
     * the degenerate t=0 pose.
     */
    private static final long T0 = 1_800_000L;

    // Subjects in PuzzleGenerator's endless deck, selected through the seed. Each one is
    // picked for what it exercises: density, or how many clue groups the gutters must
    // hold at that board size.
    /** "Sweetheart" — dense and symmetric, the picture the win card assembles. */
    private static final int SUBJECT_SWEETHEART = 0;
    /** "Love Birds" — two lobes, so pulses and bursts land on separated clusters. */
    private static final int SUBJECT_LOVE_BIRDS = 7;
    /** "Rain on the Window" — the busiest gutters the deck produces at 15x15 (5 and 4). */
    private static final int SUBJECT_RAIN = 11;
    /** "Fresh Baked Pie" — five-group row clues at 20x20: the worst case for the gutter. */
    private static final int SUBJECT_PIE = 14;
    /** "Little Owl" — the densest 10x10 with three-group rows. */
    private static final int SUBJECT_OWL = 16;

    /** Story chapter 9 is "Sleepy Cat", a 10x10 authored board. */
    private static final int STORY_CHAPTER = 9;

    // ---- Motion ------------------------------------------------------------------------

    /**
     * How often the live view redraws, and therefore the rate every clip is <em>simulated</em>
     * at whatever rate it is captured at.
     *
     * <p>This mattered more when {@code UiState.animateCursors} closed a fixed fraction of the
     * remaining distance <em>per frame</em>: a harness that stepped it once per captured frame
     * at 30 fps then showed a cursor gliding at half the speed a television showed it, and a
     * reviewer was judging an animation curve the game did not have. The glide is an
     * exponential in real milliseconds now, so it no longer cares — but the scripted input
     * {@code Take.playThrough} replays still lands on this grid, and stepping the simulation
     * at the rate the game actually redraws at is what keeps a clip a recording rather than a
     * reconstruction. A clip is stepped sixty times a second and sampled every
     * {@code 60 / fps} steps, which leaves the capture rate a pure sampling choice.
     */
    private static final int DEVICE_HZ = 60;

    /**
     * Capture rate when none is given. Thirty divides {@link #DEVICE_HZ} exactly, so every
     * captured frame lands on a simulation step; and ffmpeg's GIF muxer alternates 3 and 4
     * centisecond delays around it, so the animated GIF's total duration stays exact rather
     * than running eleven percent fast the way a constant 3 would.
     */
    private static final int DEFAULT_FPS = 30;

    // ---- Contact sheets ------------------------------------------------------------------
    //
    // Eight samples, four to a row. Left to right and then down rather than one long strip:
    // a single row of eight generous tiles is four thousand pixels wide, which no reviewer
    // sees all of at once, and a contact sheet nobody can take in at a glance has failed at
    // the one job it has.

    private static final int SHEET_FRAMES = 8;
    private static final int SHEET_COLUMNS = 4;
    private static final int SHEET_TILE_WIDTH = 520;
    private static final int SHEET_GUTTER = 18;
    private static final int SHEET_LABEL_HEIGHT = 54;
    private static final int SHEET_TITLE_HEIGHT = 88;

    private static final Color SHEET_BACKGROUND = new Color(0x14110F);
    private static final Color SHEET_LABEL_FACE = new Color(0x2A2320);
    private static final Color SHEET_EDGE = new Color(0x4A3E38);
    private static final Color SHEET_TITLE_INK = new Color(0xF6ECE3);
    private static final Color SHEET_SOFT_INK = new Color(0xB2A197);
    private static final Color SHEET_STAMP_INK = new Color(0xE8B85C);

    private static int width = 1920;
    private static int height = 1080;
    private static File outDir;
    private static Bitmap room;
    private static Bitmap garden;

    private Preview() {
    }

    /** One screenshot: a name and the state it needs. */
    private interface Shot {
        void draw(Canvas canvas, Renderer renderer, int w, int h);
    }

    /**
     * One entry in the still set: the file it writes, the sentence that says what it is
     * for, and the state it needs.
     *
     * <p>The caption lives here rather than in a Javadoc comment because it has two
     * readers. A Javadoc line only reaches somebody already inside this file; the caption
     * is also written into {@code out/README.md}, which is what somebody opening the
     * folder full of PNGs actually has to go on.
     *
     * <p>{@code fixedWidth}/{@code fixedHeight} are zero for the ordinary frames, which
     * render at whatever resolution the harness was asked for. The three shots that pin a
     * resolution instead are the ones that exist <em>to</em> compare resolutions, and they
     * go into the {@code sizes/} sub-directory: {@code render.sh} checks that every PNG
     * beside it is exactly the resolution it was asked for, and it does not recurse.
     */
    private static final class Frame {
        final String name;
        final String caption;
        final Shot shot;
        final int fixedWidth;
        final int fixedHeight;

        Frame(String name, String caption, Shot shot) {
            this(name, caption, shot, 0, 0);
        }

        Frame(String name, String caption, Shot shot, int fixedWidth, int fixedHeight) {
            this.name = name;
            this.caption = caption;
            this.shot = shot;
            this.fixedWidth = fixedWidth;
            this.fixedHeight = fixedHeight;
        }

        int width() {
            return fixedWidth > 0 ? fixedWidth : Preview.width;
        }

        int height() {
            return fixedHeight > 0 ? fixedHeight : Preview.height;
        }
    }

    public static void main(String[] args) throws IOException {
        // Answered before anything is loaded, so record.sh can ask what there is to record
        // without needing decoded backdrops or a resolution.
        if (args.length > 0 && "--list-clips".equals(args[0])) {
            for (Clip clip : clips()) {
                System.out.println(clip.name + "\t" + clip.durationMs);
            }
            return;
        }

        boolean motion = args.length > 0 && "--clips".equals(args[0]);
        String[] rest = motion ? Arrays.copyOfRange(args, 1, args.length) : args;

        outDir = new File(rest.length > 0 && !rest[0].isEmpty() ? rest[0]
                : "tools/preview/out").getAbsoluteFile();
        if (rest.length > 1) {
            width = Integer.parseInt(rest[1]);
        }
        if (rest.length > 2) {
            height = Integer.parseInt(rest[2]);
        }
        int fps = rest.length > 3 && !rest[3].isEmpty() ? Integer.parseInt(rest[3])
                : DEFAULT_FPS;
        String only = rest.length > 4 ? rest[4] : "";

        if (width < 320 || height < 240) {
            throw new IllegalArgumentException("resolution too small: " + width + "x" + height);
        }
        if (motion && (fps < 1 || fps > DEVICE_HZ || DEVICE_HZ % fps != 0)) {
            throw new IllegalArgumentException("fps must divide " + DEVICE_HZ
                    + " exactly (60, 30, 20, 15, 12, 10, 6, 5, …), not " + fps);
        }
        if (!outDir.isDirectory() && !outDir.mkdirs()) {
            throw new IOException("cannot create output directory " + outDir);
        }

        File assets = assetDir();
        room = load(assets, "cozy_room.png");
        garden = load(assets, "moon_garden.png");
        Provenance.resolve();

        System.out.println("CozyGrams preview  " + width + "x" + height
                + (motion ? "  motion at " + fps + " fps" : ""));
        System.out.println("  fonts     : " + Paint.describeFonts());
        System.out.println("  backdrops : " + assets);
        System.out.println("  output    : " + outDir);
        System.out.println("  tree      : " + Provenance.describe());
        System.out.println();

        if (motion) {
            recordClips(fps, only);
            return;
        }

        List<File> written = new ArrayList<>();
        for (Frame frame : frames()) {
            written.add(render(frame));
        }
        Provenance.writeManifest(outDir, written);
        Provenance.writeIndex(outDir, frames());

        System.out.println();
        System.out.println(written.size() + " screenshots written, plus "
                + Provenance.MANIFEST + " and " + Provenance.INDEX + ".");
    }

    // ---- Scenarios -------------------------------------------------------------------

    /**
     * The still set, in the order a reviewer should walk it: the two menus, the board at
     * every size it deals, the two states with an authored chapter behind them, the
     * feedback, the win, and then the comfort options and the cases that break layout.
     *
     * <p>Numbers are never reused and never renumbered. Several reviews at once diff
     * {@code 05-game-10x10-mid.png} against an earlier copy of {@code
     * 05-game-10x10-mid.png}, and a set that renumbers itself when a scenario is added
     * silently compares two different scenes. New scenarios go on the end even when that
     * puts them out of thematic order.
     */
    private static Frame[] frames() {
        return new Frame[]{
                new Frame("01-home.png",
                        "The title screen, CONTINUE focused, Rose alone at the table.",
                        Preview::home),
                new Frame("02-home-size.png",
                        "The same menu with the board-size stepper focused and both "
                                + "players present.",
                        Preview::homeSize),
                new Frame("03-settings.png",
                        "The cozy corner as it opens: music, sound and hints all on.",
                        Preview::settings),
                new Frame("04-game-5x5-fresh.png",
                        "An untouched 5x5, the largest cells the game draws, one player.",
                        Preview::gameFresh),
                new Frame("05-game-10x10-mid.png",
                        "The reference position: 10x10 about halfway home, both playing. "
                                + "Shots 12-15 are this same board.",
                        Preview::gameTen),
                new Frame("06-game-15x15-mid.png",
                        "The same idea at 15x15, where the clue gutters start to crowd.",
                        Preview::gameFifteen),
                new Frame("07-game-20x20-mid.png",
                        "20x20: the smallest cells and the busiest clue gutters the game "
                                + "can produce.",
                        Preview::gameTwenty),
                new Frame("08-game-story.png",
                        "An authored story chapter part way through, with the chapter "
                                + "named in the header.",
                        Preview::gameStory),
                new Frame("09-game-effects.png",
                        "A fill that finishes a row, caught 150 ms later — every particle "
                                + "on screen was emitted by the game's own input path.",
                        Preview::gameEffects),
                new Frame("10-win-early.png",
                        "The win card 300 ms in: the picture is still assembling itself.",
                        Preview::winEarly),
                new Frame("11-win-settled.png",
                        "The same win card at 2200 ms: fully revealed, prompt showing.",
                        Preview::winSettled),
                new Frame("12-game-cursors-overlap.png",
                        "Both cursors on one square, which earns a shared heart.",
                        Preview::cursorsOverlap),
                new Frame("13-game-bigtext.png",
                        "The reference position with LARGER TEXT on.",
                        Preview::bigText),
                new Frame("14-game-contrast.png",
                        "The reference position with EXTRA CONTRAST on.",
                        Preview::highContrast),
                new Frame("15-game-toast.png",
                        "The widest message the ribbon has to hold, freshly shown.",
                        Preview::toast),
                new Frame("16-home-worst-case.png",
                        "Every text-overflow path on the title screen at once: story mode "
                                + "deep in the book, a returning-player greeting, the "
                                + "restart row armed, and LARGER TEXT on.",
                        Preview::homeWorstCase),
                new Frame("17-game-solo.png",
                        "The reference position with only Rose present, so the rail's "
                                + "empty seat can be judged against shot 05.",
                        Preview::gameSolo),
                new Frame("18-win-story.png",
                        "A chapter finished: the win card in story mode, with the row of "
                                + "chapter hearts only the book draws.",
                        Preview::winStory),
                new Frame("19-settings-switches.png",
                        "Four switches on and five off, focus resting on an off one — the "
                                + "frame that says whether OFF reads as off from ten feet.",
                        Preview::settingsSwitches),
                new Frame("20-settings-armed.png",
                        "PUT EVERYTHING BACK armed and waiting for a second press.",
                        Preview::settingsArmed),
                new Frame("21-game-page-turn.png",
                        "85 ms into the 340 ms wash between one chapter and the next.",
                        Preview::pageTurn),
                new Frame("sizes/07-game-20x20-1280x720.png",
                        "Shot 07 at 720p, the smallest panel an Android TV ships with.",
                        Preview::gameTwenty, 1280, 720),
                new Frame("sizes/07-game-20x20-3840x2160.png",
                        "Shot 07 at 4K, where every scaled dimension is doubled.",
                        Preview::gameTwenty, 3840, 2160),
        };
    }

    // What each of these frames is *for* is the caption in frames() above, which is also
    // what out/README.md prints beside it. Repeating it here as a Javadoc line would give
    // the set two descriptions to drift apart; the comments below are only for the ones
    // that need a reason as well as a description.

    private static void home(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_OWL, 10);
        UiState ui = new UiState();
        ui.screen = UiState.HOME;
        ui.menu = HomeScene.ITEM_CONTINUE;
        ui.joined[0] = true;
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    private static void homeSize(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_OWL, 10);
        UiState ui = new UiState();
        ui.screen = UiState.HOME;
        ui.menu = HomeScene.ITEM_SIZE;
        ui.joined[0] = true;
        ui.joined[1] = true;
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    private static void settings(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_OWL, 10);
        UiState ui = new UiState();
        ui.screen = UiState.SETTINGS;
        ui.menu = SettingsScene.ITEM_MUSIC;
        ui.musicOn = true;
        ui.sfxOn = true;
        ui.hintsOn = true;
        ui.joined[0] = true;
        ui.joined[1] = true;
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    private static void gameFresh(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_SWEETHEART, 5);
        UiState ui = playing(false);
        game.cursorX[0] = 2;
        game.cursorY[0] = 1;
        ui.snapCursors(game);
        ui.showToast("Sky can join any time — just press a button", Theme.BLUE, T0 - 900);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    private static void gameTen(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = midGameTen();
        UiState ui = playing(true);
        placeCursors(game, ui, 3, 4, 7, 2);
        ui.showToast("Lovely, Rose — that row is finished", Theme.PINK, T0 - 1400);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    private static void gameFifteen(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_RAIN, 15);
        fillPicture(game, .55f);
        scatterCrosses(game, 4);
        game.moves[0] = 96;
        game.moves[1] = 81;
        UiState ui = playing(true);
        placeCursors(game, ui, 5, 7, 11, 3);
        ui.showToast("Take your time", Theme.CREAM, T0 - 2000);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    private static void gameTwenty(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_PIE, 20);
        game.solved = 6;
        fillPicture(game, .55f);
        scatterCrosses(game, 5);
        game.moves[0] = 174;
        game.moves[1] = 158;
        UiState ui = playing(true);
        placeCursors(game, ui, 6, 11, 14, 5);
        ui.showToast("Halfway there", Theme.GOLD, T0 - 2600);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /**
     * How the book announces a chapter, built the way {@code CozyGameView.chapterLine} builds
     * it.
     *
     * <p>Both of these shots used to spell it for themselves. One carried a hand-typed
     * "Chapter ten — Sleepy Cat" while the game concatenated the raw integer, so the frame
     * advertised a string the build could not produce; the other concatenated the integer and
     * agreed with the game only by accident. A preview that says something the game does not
     * is not evidence, so there is one expression here and it goes through
     * {@link WinScene#word}, exactly as the game does.
     */
    private static String chapterLine(GameState game) {
        return "Chapter " + WinScene.word(game.storyIndex + 1) + " — " + game.puzzle.name;
    }

    private static void gameStory(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = storyGame(STORY_CHAPTER);
        game.solved = 1;
        fillPicture(game, .5f);
        scatterCrosses(game, 4);
        game.moves[0] = 34;
        game.moves[1] = 27;
        UiState ui = playing(true);
        placeCursors(game, ui, 4, 5, 6, 2);
        ui.showToast(chapterLine(game), Theme.GOLD, T0 - 1100);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /**
     * The four shots that show the game reacting are scripted, not staged.
     *
     * <p>This one used to hand {@link Effects} its own numbers, and they were not the
     * game's: bursts of 14, 18 and 12 particles at spreads of 420, 520 and 380 px, where
     * a real fill throws 5 at {@code board.cell * 2.4} — 151 px at 10x10 — and a real hint
     * throws 6. It stacked a FILL, a CROSS, a HINT and a 900 ms LINE crest into one frame,
     * which no single press can produce, and it invented a cross spray the game has never
     * emitted at all. A reviewer looking at it was judging particle work nobody had
     * written, and the two audits that asked for the sparkles to be calmed were arguing
     * with this file rather than with {@code Effects}.
     *
     * <p>So it presses the button instead and lets {@link Take} — which mirrors
     * {@code CozyGameView}'s input path call for call — decide what comes out. The press
     * lands at +120 ms and the shutter opens 150 ms later, while the line sweep is four
     * squares into its run.
     */
    private static void gameEffects(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_LOVE_BIRDS, 10);
        fillPicture(game, .5f);
        scatterCrosses(game, 3);
        game.moves[0] = 38;
        game.moves[1] = 31;
        int[] gap = armRow(game);

        Take take = new Take(game, renderer);
        take.park(0, gap[0], gap[1]);
        take.park(1, wrap(gap[0] + 5, game.size), wrap(gap[1] + 4, game.size));
        take.at(120, (t, now) -> t.fill(0, now));
        moment(canvas, w, h, take, 270);
    }

    private static void winEarly(Canvas canvas, Renderer renderer, int w, int h) {
        drawWin(canvas, renderer, w, h, 300, false);
    }

    private static void winSettled(Canvas canvas, Renderer renderer, int w, int h) {
        drawWin(canvas, renderer, w, h, 2200, false);
    }

    private static void winStory(Canvas canvas, Renderer renderer, int w, int h) {
        drawWin(canvas, renderer, w, h, 2200, true);
    }

    /**
     * The win card, reached by finishing the picture rather than by declaring it finished.
     *
     * <p>The confetti used to be written here by hand — six bursts of hearts and petals at
     * spreads of 520 and 560, plus a sixteen-heart rise. {@code showWinCelebration} emits
     * none of that: it is {@value Effects#WIN_DRIFT_EMISSIONS} two-particle rises from
     * {@code height * .96} at {@code height * .30}, staggered
     * {@value Effects#WIN_DRIFT_STAGGER_MS} ms apart. The invented version put warmth
     * where the real one does not, which is exactly the complaint two reviews then made
     * about the real one.
     *
     * <p>Both moments come from one timeline: the last square goes in at +200 ms, and the
     * shutter opens {@code elapsed} ms after that, so 300 and 2200 mean the same thing
     * here as they do in {@code WinScene}'s beat sheet.
     */
    private static void drawWin(Canvas canvas, Renderer renderer, int w, int h,
                                long elapsed, boolean story) {
        GameState game = story ? storyGame(STORY_CHAPTER) : endless(SUBJECT_SWEETHEART, 10);
        game.solved = 3;
        game.moves[0] = 63;
        game.moves[1] = 58;
        int[] last = armWin(game);

        Take take = new Take(game, renderer);
        take.park(0, last[0], last[1]);
        take.park(1, wrap(last[0] + 3, game.size), wrap(last[1] + 2, game.size));
        take.at(WIN_PRESS_MS, (t, now) -> t.fill(0, now));
        moment(canvas, w, h, take, WIN_PRESS_MS + elapsed);
    }

    /** When the last square of the picture goes in, on the win shots' shared timeline. */
    private static final long WIN_PRESS_MS = 200;

    private static void cursorsOverlap(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = midGameTen();
        UiState ui = playing(true);
        placeCursors(game, ui, 5, 5, 5, 5);
        ui.showToast("Together again ♥", Theme.PINK, T0 - 700);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    private static void bigText(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = midGameTen();
        UiState ui = playing(true);
        ui.bigTextOn = true;
        placeCursors(game, ui, 3, 4, 7, 2);
        ui.showToast("Larger text is on", Theme.CREAM, T0 - 1200);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    private static void highContrast(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = midGameTen();
        UiState ui = playing(true);
        ui.highContrastOn = true;
        placeCursors(game, ui, 3, 4, 7, 2);
        ui.showToast("Extra contrast is on", Theme.CREAM, T0 - 1200);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    private static void toast(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = midGameTen();
        UiState ui = playing(true);
        placeCursors(game, ui, 3, 4, 7, 2);
        ui.showToast("Sky filled in the last square of that column — the whole line is "
                + "put to bed now", Theme.GOLD, T0 - 150);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /**
     * Every string on the title screen at its longest, in one frame.
     *
     * <p>The three title-screen defects a theme audit found were all invisible to the
     * fifteen shots that came before this one, and no unit test can see them either:
     * {@code app/build.gradle} sets {@code returnDefaultValues}, so {@code
     * Paint.measureText} returns 0 in tests and every fitting guard passes trivially. The
     * only place a label running through its own value can be caught is a rendered frame,
     * so this is the frame — the longest label ("START THE STORY AGAIN", or the armed
     * question), the longest value, a returning-player greeting long enough to need two
     * lines, and LARGER TEXT on top of all of it.
     */
    private static void homeWorstCase(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = storyGame(STORY_CHAPTER);
        game.storyFurthest = PuzzleLibrary.count() - 7;
        game.solved = 128;
        UiState ui = new UiState();
        ui.screen = UiState.HOME;
        ui.menu = HomeScene.ITEM_RESTART;
        ui.bigTextOn = true;
        ui.joined[0] = true;
        ui.joined[1] = true;
        HomeScene.setWelcome("It's been a while — the room kept your seat warm, and the "
                + "kettle is still on");
        HomeScene.armRestart(T0);
        // A size chosen while a chapter was open, which is what puts "next" on the end of
        // the stepper's value and makes that row's longest string reachable too.
        HomeScene.setPendingSize(GameState.MAX_SIZE);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /**
     * The rail with one seat empty.
     *
     * <p>Deliberately the same board and the same cursor as shot 05 rather than a fresh
     * one: the question a solo frame has to answer is whether the rail looks lonely or
     * simply looks like it is waiting, and that is only answerable by putting it next to
     * the two-player frame it differs from by exactly one field. Shot 04 is also solo, but
     * it is a fresh 5x5 whose rail has no progress and no history in it, so it cannot
     * settle the question.
     */
    private static void gameSolo(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = midGameTen();
        UiState ui = playing(false);
        placeCursors(game, ui, 3, 4, 7, 2);
        ui.showToast("Sky can join any time — just press a button", Theme.BLUE, T0 - 1400);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /**
     * The cozy corner with its switches in a mixture of states.
     *
     * <p>Shot 03 has every switch on, which is the one arrangement that cannot show
     * whether an off switch reads as off. Four on and five off, with the focus resting on
     * an off row, puts both states on the same screen at the same size — the comparison
     * the "make an OFF toggle look off from ten feet" finding needs and could not make.
     */
    private static void settingsSwitches(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_OWL, 10);
        UiState ui = new UiState();
        ui.screen = UiState.SETTINGS;
        ui.menu = SettingsScene.ITEM_SFX;
        ui.musicOn = true;
        ui.sfxOn = false;
        ui.gentleCheck = true;
        ui.hintsOn = false;
        ui.bigTextOn = false;
        ui.highContrastOn = false;
        Comfort.get().distinctPlayers = true;
        Comfort.get().boldCursor = true;
        Comfort.get().calmMotion = false;
        ui.joined[0] = true;
        ui.joined[1] = true;
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    private static void settingsArmed(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_OWL, 10);
        UiState ui = new UiState();
        ui.screen = UiState.SETTINGS;
        ui.menu = SettingsScene.ITEM_DEFAULTS;
        ui.joined[0] = true;
        ui.joined[1] = true;
        SettingsScene.armDefaults(T0);
        SettingsScene.setTidyingPlayer(1);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /**
     * The wash between two chapters, part way through.
     *
     * <p>{@code Renderer} starts the page turn by noticing that the {@link Puzzle} on the
     * table is not the one it drew last time, so this cannot be posed — it has to be
     * played. Three frames: chapter ten, then chapter eleven (which is the frame the turn
     * starts on), then the same board {@value #PAGE_TURN_MS} ms later, which is the one
     * kept. A quarter of the way through {@code WinScene.HANDOVER_MS} is where the wash is
     * unmistakably a wash rather than either of the pages it is between.
     */
    private static void pageTurn(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = storyGame(STORY_CHAPTER);
        fillPicture(game, .5f);
        scatterCrosses(game, 4);
        game.solved = 9;
        UiState ui = playing(true);
        placeCursors(game, ui, 4, 5, 6, 2);

        long turnAt = T0 - PAGE_TURN_MS;
        renderer.draw(canvas, w, h, game, ui, new Effects(), turnAt - 1);
        game.startStory(STORY_CHAPTER + 1);
        ui.snapCursors(game);
        ui.showToast(chapterLine(game), Theme.CREAM, turnAt);
        renderer.draw(canvas, w, h, game, ui, new Effects(), turnAt);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /** How far into the page turn shot 21 is caught. */
    private static final long PAGE_TURN_MS = 85;

    // ---- Motion scenarios --------------------------------------------------------------

    /**
     * The clips, in the order a reviewer should watch them: the two things a cursor does,
     * the three marks a square can take, the reward for a finished line, the reward for a
     * finished picture, and the menu.
     *
     * <p>Every one of them opens on a <em>settled</em> state and holds it for a few frames
     * before anything happens, so what changes on screen is the motion under review and not
     * the scene arriving. Nothing here stages an effect by emitting it directly: each clip
     * presses the buttons and lets {@link Take}, which mirrors {@code CozyGameView}'s input
     * path call for call, decide what comes out. Staging the pretty half of a beat and
     * skipping the auto-crossing, the message and the sound that arrive with it is how a
     * harness ends up reviewing something the game does not do.
     */
    private static Clip[] clips() {
        return new Clip[]{
                new Clip("01-cursor-walk", 1900, Preview::cursorWalk),
                new Clip("02-fill-run", 2000, Preview::fillRun),
                new Clip("03-cross-run", 1800, Preview::crossRun),
                new Clip("04-line-complete", 2000, Preview::lineComplete),
                new Clip("05-hint-reveal", 2200, Preview::hintReveal),
                new Clip("06-gentle-mistake", 1800, Preview::gentleMistake),
                new Clip("07-win-reveal", 3600, Preview::winReveal),
                new Clip("08-home-focus", 2100, Preview::homeFocus),
        };
    }

    /**
     * 01 — a cursor walk. Five unhurried steps, both players moving at once for part of
     * it, and a closing strafe of two steps inside one glide: the case that decides whether
     * the easing keeps up or smears.
     */
    private static Take cursorWalk() {
        Take take = new Take(midGameTen());
        take.park(0, 2, 3);
        take.park(1, 7, 6);
        // 160 ms apart — slow enough that each glide has nearly landed before the next
        // begins, which is where an ease is easiest to read.
        take.at(240, (t, now) -> t.move(0, 1, 0, now));
        take.at(400, (t, now) -> t.move(0, 1, 0, now));
        take.at(560, (t, now) -> t.move(0, 0, 1, now));
        take.at(720, (t, now) -> t.move(0, 0, 1, now));
        take.at(880, (t, now) -> t.move(0, 1, 0, now));
        take.at(940, (t, now) -> t.move(1, -1, 0, now));
        take.at(1100, (t, now) -> t.move(1, 0, -1, now));
        take.at(1420, (t, now) -> t.move(0, 1, 0, now));
        take.at(1480, (t, now) -> t.move(0, 1, 0, now));
        return take;
    }

    /** 02 — Rose fills a run of four, one square at a time, at a relaxed 400 ms a square. */
    private static Take fillRun() {
        GameState game = midGameTen();
        int[] run = findFillRun(game, 4);
        Take take = new Take(game);
        take.park(0, run[0], run[1]);
        take.park(1, wrap(run[0] + 6, game.size), wrap(run[1] + 4, game.size));
        long when = 260;
        for (int step = 0; step < 4; step++) {
            take.at(when, (t, now) -> t.fill(0, now));
            when += 200;
            if (step < 3) {
                take.at(when, (t, now) -> t.move(0, 1, 0, now));
                when += 200;
            }
        }
        return take;
    }

    /**
     * 03 — Sky rules three squares out, then presses the same button on the last of them
     * again. The quiet undo is the other half of what crossing has to communicate, and it
     * belongs in the same clip as the thing it undoes.
     */
    private static Take crossRun() {
        // The shared mid-game position, which it did not used to be: this clip ran on Love
        // Birds because that board had three untouched empty squares in a row and the
        // reference one had every third square already ruled out. Then a wave redrew the
        // deck — Love Birds' longest empty row at 10x10 is now two squares — and the clip
        // died. findCrossRun rubs the run out itself now, so the only thing the board has
        // to supply is the shape, and Little Owl's longest empty row is six.
        GameState game = midGameTen();
        int[] run = findCrossRun(game, 3);
        Take take = new Take(game);
        take.park(1, run[0], run[1]);
        take.park(0, wrap(run[0] + 5, game.size), wrap(run[1] + 3, game.size));
        take.at(260, (t, now) -> t.cross(1, now));
        take.at(460, (t, now) -> t.move(1, 1, 0, now));
        take.at(660, (t, now) -> t.cross(1, now));
        take.at(860, (t, now) -> t.move(1, 1, 0, now));
        take.at(1060, (t, now) -> t.cross(1, now));
        take.at(1420, (t, now) -> t.cross(1, now));
        return take;
    }

    /** 04 — the press that finishes a row, and the wave that runs down it afterwards. */
    private static Take lineComplete() {
        GameState game = midGameTen();
        int[] gap = armRow(game);
        int startX = Math.max(0, gap[0] - 2);
        Take take = new Take(game);
        take.park(0, startX, gap[1]);
        take.park(1, wrap(gap[0] + 4, game.size), wrap(gap[1] + 5, game.size));
        long when = 300;
        for (int x = startX; x < gap[0]; x++) {
            take.at(when, (t, now) -> t.move(0, 1, 0, now));
            when += 200;
        }
        take.at(when + 120, (t, now) -> t.fill(0, now));
        return take;
    }

    /**
     * 05 — two hints. The interesting half is not the shimmer but the cursor: a hint moves
     * it to the square it found, which is the one time in the game the board takes the
     * cursor somewhere the player did not.
     */
    private static Take hintReveal() {
        Take take = new Take(midGameTen());
        take.park(0, 1, 6);
        take.park(1, 8, 2);
        take.at(320, (t, now) -> t.hint(0, now));
        take.at(1240, (t, now) -> t.hint(1, now));
        return take;
    }

    /**
     * 06 — gentle mode. The same button on two neighbouring squares: one that is not part
     * of the picture and one that is, so the sigh and the settle can be compared inside a
     * single clip rather than across two.
     */
    private static Take gentleMistake() {
        GameState game = midGameTen();
        int[] pair = findMistakePair(game);
        Take take = new Take(game);
        take.ui.gentleCheck = true;
        take.park(0, pair[0], pair[1]);
        take.park(1, wrap(pair[0] + 5, game.size), wrap(pair[1] + 4, game.size));
        take.at(320, (t, now) -> t.fill(0, now));
        take.at(900, (t, now) -> t.move(0, 1, 0, now));
        take.at(1160, (t, now) -> t.fill(0, now));
        return take;
    }

    /**
     * 07 — the last square of the picture and everything that follows it: the line wave the
     * final mark sets off, the board dimming, the picture lifting into its frame, the words
     * arriving, and the drift going quiet again.
     */
    private static Take winReveal() {
        GameState game = endless(SUBJECT_SWEETHEART, 10);
        game.solved = 3;
        int[] last = armWin(game);
        game.moves[0] = 63;
        game.moves[1] = 58;
        Take take = new Take(game);
        int from = last[0] > 0 ? last[0] - 1 : last[0] + 1;
        int towards = last[0] > from ? 1 : -1;
        take.park(0, from, last[1]);
        take.park(1, wrap(last[0] + 3, game.size), wrap(last[1] + 2, game.size));
        take.at(300, (t, now) -> t.move(0, towards, 0, now));
        take.at(560, (t, now) -> t.fill(0, now));
        return take;
    }

    /** 08 — the home menu: focus walking down the rows and part of the way back up. */
    private static Take homeFocus() {
        Take take = new Take(endless(SUBJECT_OWL, 10));
        take.ui.screen = UiState.HOME;
        take.ui.menu = HomeScene.ITEM_CONTINUE;
        // 260 ms apart, comfortably clear of Theme.MENU_REPEAT_MS, so every press lands.
        take.at(300, (t, now) -> t.stepMenu(1, HomeScene.ITEM_COUNT, now));
        take.at(560, (t, now) -> t.stepMenu(1, HomeScene.ITEM_COUNT, now));
        take.at(820, (t, now) -> t.stepMenu(1, HomeScene.ITEM_COUNT, now));
        take.at(1080, (t, now) -> t.stepMenu(1, HomeScene.ITEM_COUNT, now));
        take.at(1480, (t, now) -> t.stepMenu(-1, HomeScene.ITEM_COUNT, now));
        take.at(1740, (t, now) -> t.stepMenu(-1, HomeScene.ITEM_COUNT, now));
        return take;
    }

    // ---- Arranging a board for a clip ----------------------------------------------------

    /**
     * The first horizontal run of {@code length} picture squares whose filling completes no
     * line, cleared ready to be pressed. A clip about filling should be about filling: a
     * line wave arriving halfway through buries the very thing it is there to show.
     *
     * <p>These three used to require the squares to be <em>already</em> unmarked, and
     * threw when the deck did not oblige. That is exactly what happened: a wave redrew the
     * subjects, {@code 03-cross-run} stopped finding three untouched empty squares side by
     * side on Love Birds, and a working clip died with "no run of 3 empty squares on this
     * board". A clip's setup is allowed to arrange the board — {@link #armRow} has always
     * done so — and arranging it is strictly more robust than hoping for it, so they now
     * rub out whatever is in the way. What is still a hard failure is a board with no such
     * run in its <em>picture</em>, because that is a real change in what the deck deals
     * and a reviewer should hear about it.
     */
    private static int[] findFillRun(GameState game, int length) {
        for (int y = 0; y < game.size; y++) {
            for (int x = 0; x + length <= game.size; x++) {
                if (!runIs(game, x, y, length, true)) {
                    continue;
                }
                byte[] saved = clearRun(game, x, y, length);
                if (!completesALine(game, x, y, length)) {
                    return new int[]{x, y};
                }
                restoreRun(game, x, y, saved);
            }
        }
        throw new IllegalStateException("no quiet run of " + length
                + " picture squares on this board");
    }

    /** The first horizontal run of {@code length} squares outside the picture, cleared. */
    private static int[] findCrossRun(GameState game, int length) {
        int longest = 0;
        for (int y = 0; y < game.size; y++) {
            int run = 0;
            for (int x = 0; x < game.size; x++) {
                run = game.puzzle.solution[y][x] ? 0 : run + 1;
                longest = Math.max(longest, run);
                if (run >= length) {
                    int start = x - length + 1;
                    clearRun(game, start, y, length);
                    return new int[]{start, y};
                }
            }
        }
        // Named, and with the number that would have worked: this failed once after a wave
        // redrew the deck, and finding out why meant writing a throwaway program to print
        // the board. The message is the program.
        throw new IllegalStateException("\"" + game.puzzle.name + "\" at " + game.size + "x"
                + game.size + " has no run of " + length
                + " squares outside the picture; its longest is " + longest);
    }

    /** A square outside the picture with a picture square beside it, both cleared. */
    private static int[] findMistakePair(GameState game) {
        for (int y = 0; y < game.size; y++) {
            for (int x = 0; x + 1 < game.size; x++) {
                if (!runIs(game, x, y, 1, false) || !runIs(game, x + 1, y, 1, true)) {
                    continue;
                }
                byte[] saved = clearRun(game, x, y, 2);
                if (!completesALine(game, x + 1, y, 1)) {
                    return new int[]{x, y};
                }
                restoreRun(game, x, y, saved);
            }
        }
        throw new IllegalStateException("no square to get wrong on this board");
    }

    /** True when {@code length} squares from (x,y) all match {@code picture}. */
    private static boolean runIs(GameState game, int x, int y, int length, boolean picture) {
        for (int i = 0; i < length; i++) {
            if (game.puzzle.solution[y][x + i] != picture) {
                return false;
            }
        }
        return true;
    }

    /** Rubs out {@code length} squares from (x,y), handing back what was there. */
    private static byte[] clearRun(GameState game, int x, int y, int length) {
        byte[] saved = new byte[length];
        for (int i = 0; i < length; i++) {
            saved[i] = game.puzzle.marks[y][x + i];
            game.puzzle.marks[y][x + i] = Puzzle.UNKNOWN;
        }
        return saved;
    }

    private static void restoreRun(GameState game, int x, int y, byte[] saved) {
        for (int i = 0; i < saved.length; i++) {
            game.puzzle.marks[y][x + i] = saved[i];
        }
    }

    /** Whether filling that run would finish its row or any of its columns. Leaves no trace. */
    private static boolean completesALine(GameState game, int x, int y, int length) {
        for (int i = 0; i < length; i++) {
            game.puzzle.marks[y][x + i] = Puzzle.FILLED;
        }
        boolean any = game.puzzle.rowSolved(y);
        for (int i = 0; i < length && !any; i++) {
            any = game.puzzle.colSolved(x + i);
        }
        for (int i = 0; i < length; i++) {
            game.puzzle.marks[y][x + i] = Puzzle.UNKNOWN;
        }
        return any;
    }

    /**
     * Fills every picture square of some unfinished row but its last, and hands back the
     * square left out — the one press that will complete the line.
     */
    private static int[] armRow(GameState game) {
        for (int y = 0; y < game.size; y++) {
            int count = 0;
            int last = -1;
            for (int x = 0; x < game.size; x++) {
                if (game.puzzle.solution[y][x]) {
                    count++;
                    last = x;
                }
            }
            if (count < 3 || game.puzzle.rowSolved(y)) {
                continue;
            }
            for (int x = 0; x < game.size; x++) {
                if (game.puzzle.solution[y][x]) {
                    game.puzzle.marks[y][x] = x == last ? Puzzle.UNKNOWN : Puzzle.FILLED;
                }
            }
            return new int[]{last, y};
        }
        throw new IllegalStateException("no row worth finishing on this board");
    }

    /**
     * Puts a board one square from finished: the whole picture found, every other square
     * ruled out, and then one picture square taken back out. Returns the square left to
     * press, so the clip can start from the move before the win rather than from the win.
     */
    private static int[] armWin(GameState game) {
        fillPicture(game, 1f);
        crossEveryEmptySquare(game);
        for (int y = game.size - 1; y >= 0; y--) {
            for (int x = game.size - 1; x >= 0; x--) {
                if (game.puzzle.solution[y][x]) {
                    game.puzzle.marks[y][x] = Puzzle.UNKNOWN;
                    return new int[]{x, y};
                }
            }
        }
        throw new IllegalStateException("an empty picture cannot be finished");
    }

    private static int wrap(int value, int size) {
        return Math.floorMod(value, size);
    }

    // ---- State helpers ---------------------------------------------------------------

    /**
     * The seed that pins a given subject and look. {@code PuzzleGenerator} splits a seed
     * into {@code subject = seed mod subjectCount()} and
     * {@code variant = (seed / subjectCount()) mod 12}, so this inverts that exactly and
     * keeps tracking it if the deck grows.
     */
    private static long seedFor(int subject, int variant) {
        return subject + (long) PuzzleGenerator.subjectCount() * variant;
    }

    private static GameState endless(int subject, int size) {
        return new GameState(seedFor(subject, 0), size);
    }

    /**
     * A game sitting on an authored chapter. The endless seed it is built from never
     * shows: {@code startStory} replaces the board outright, and the size argument only
     * decides how big the throwaway board was.
     */
    private static GameState storyGame(int chapter) {
        GameState game = endless(SUBJECT_SWEETHEART, 5);
        game.startStory(chapter);
        return game;
    }

    private static UiState playing(boolean bothJoined) {
        UiState ui = new UiState();
        ui.screen = UiState.GAME;
        ui.joined[0] = true;
        ui.joined[1] = bothJoined;
        ui.hintsOn = true;
        return ui;
    }

    /**
     * The shared 10x10 position, so shots 05 and 12-15 compare directly. The "cat" kind
     * is the densest board the generator produces at this size and has two-group clues,
     * which is what makes the gutters worth reviewing.
     */
    private static GameState midGameTen() {
        GameState game = endless(SUBJECT_OWL, 10);
        game.solved = 2;
        fillPicture(game, .55f);
        scatterCrosses(game, 3);
        game.moves[0] = 41;
        game.moves[1] = 33;
        return game;
    }

    private static void placeCursors(GameState game, UiState ui, int x0, int y0, int x1,
                                     int y1) {
        game.cursorX[0] = x0;
        game.cursorY[0] = y0;
        game.cursorX[1] = x1;
        game.cursorY[1] = y1;
        ui.snapCursors(game);
    }

    /** Fills the first {@code fraction} of the picture's squares, in row-major order. */
    private static void fillPicture(GameState game, float fraction) {
        int want = Math.round(game.puzzle.pictureCount() * fraction);
        int done = 0;
        for (int y = 0; y < game.size && done < want; y++) {
            for (int x = 0; x < game.size && done < want; x++) {
                if (game.puzzle.solution[y][x]) {
                    game.puzzle.marks[y][x] = Puzzle.FILLED;
                    done++;
                }
            }
        }
    }

    /** Crosses out every {@code stride}-th square that is not part of the picture. */
    private static void scatterCrosses(GameState game, int stride) {
        int seen = 0;
        for (int y = 0; y < game.size; y++) {
            for (int x = 0; x < game.size; x++) {
                if (game.puzzle.solution[y][x]
                        || game.puzzle.marks[y][x] != Puzzle.UNKNOWN) {
                    continue;
                }
                if (seen++ % stride == 0) {
                    game.puzzle.marks[y][x] = Puzzle.CROSSED;
                }
            }
        }
    }

    /** The state a solved board ends in: every non-picture square ruled out. */
    private static void crossEveryEmptySquare(GameState game) {
        for (int y = 0; y < game.size; y++) {
            for (int x = 0; x < game.size; x++) {
                if (!game.puzzle.solution[y][x]) {
                    game.puzzle.marks[y][x] = Puzzle.CROSSED;
                }
            }
        }
    }

    // ---- The take --------------------------------------------------------------------

    /** One scripted controller press, played at the instant it was scheduled for. */
    private interface Press {
        void play(Take take, long now);
    }

    /** Builds the state and the script for a clip, fresh each time it is recorded. */
    private interface Motion {
        Take build();
    }

    /** A named clip: how long it runs for, and what it is. */
    private static final class Clip {
        final String name;
        final long durationMs;
        final Motion motion;

        Clip(String name, long durationMs, Motion motion) {
            this.name = name;
            this.durationMs = durationMs;
            this.motion = motion;
        }
    }

    private static final class Beat {
        final long at;
        final Press press;

        Beat(long at, Press press) {
            this.at = at;
            this.press = press;
        }
    }

    /**
     * The live state of one clip: the model the game would have, and the script of presses
     * that disturbs it.
     *
     * <p>Everything here that touches the board goes through the same calls
     * {@code CozyGameView} makes on the real input path — the same pulses, the same bursts,
     * the same auto-crossing, in the same order — so a clip shows the game's own motion
     * rather than a second, prettier implementation of it. Each method is named after the
     * one it mirrors, and none of them is a place to be creative: if a beat looks wrong in a
     * clip, the fix belongs in the app.
     *
     * <p>What is deliberately <em>not</em> mirrored is everything with no picture in it —
     * sound, saving, controller registration, and the six rotating line-complete sentences,
     * of which a clip always shows the first so successive runs stay diffable.
     */
    private static final class Take {
        final GameState game;
        final UiState ui = new UiState();
        final Effects effects = new Effects();
        final Renderer renderer;

        private final List<Beat> beats = new ArrayList<>();
        private final long[] lastGentleNudgeAt = {0, 0};
        private int next;
        private long lastMenuStepAt;

        /**
         * The size of the frame this take is being drawn into.
         *
         * <p>Only {@link #checkForWin} needs it, and only because {@code
         * showWinCelebration} launches the drift from {@code getWidth()} and {@code
         * getHeight()} — the <em>view's</em> size, not the board's. Clips are always the
         * harness resolution, but a still may not be: shot 10 is 1920 wide and the same
         * scene in {@code sizes/} is 3840, and a take that read the global would have
         * thrown the 4K frame's confetti up the left-hand half of the screen.
         */
        int viewWidth = width;
        int viewHeight = height;

        Take(GameState game) {
            this(game, null);
        }

        /**
         * @param renderer the renderer the frames will be drawn with, or null for a fresh
         *                 one. It matters which: {@code burstAtCell} and {@code sweepLine}
         *                 both read {@code renderer.board()}, so a take that emits through
         *                 one renderer while its frames are drawn with another throws its
         *                 particles at the geometry of a board nobody is looking at.
         */
        Take(GameState game, Renderer renderer) {
            this.renderer = renderer != null ? renderer : new Renderer();
            this.game = game;
            this.renderer.setScenes(room, garden);
            ui.screen = UiState.GAME;
            ui.joined[0] = true;
            ui.joined[1] = true;
            ui.hintsOn = true;
            ui.snapCursors(game);
            // A settled message rather than none at all: with toastAt still zero the HUD
            // shows the join prompt for ever, which is not what a table mid-puzzle looks
            // like. Aged just enough to be past its entrance and nowhere near its fade.
            ui.showToast("Take your time", Theme.CREAM, T0 - 800);
        }

        /** Puts a cursor on a square and settles the drawn cursor onto it. */
        Take park(int who, int x, int y) {
            game.cursorX[who] = x;
            game.cursorY[who] = y;
            ui.snapCursors(game);
            return this;
        }

        /** Schedules a press for {@code atMs} after the clip starts. */
        Take at(long atMs, Press press) {
            if (atMs <= 0) {
                throw new IllegalArgumentException("a clip opens settled: no press at " + atMs);
            }
            if (!beats.isEmpty() && atMs < beats.get(beats.size() - 1).at) {
                throw new IllegalArgumentException("presses must be scheduled in order, "
                        + atMs + " follows " + beats.get(beats.size() - 1).at);
            }
            beats.add(new Beat(atMs, press));
            return this;
        }

        /** Plays every press due at or before {@code offset} milliseconds into the clip. */
        void playThrough(long offset) {
            while (next < beats.size() && beats.get(next).at <= offset) {
                Beat beat = beats.get(next++);
                // Fired at its scripted instant rather than at the frame that noticed it, so
                // the capture rate cannot move an effect by up to a frame.
                beat.press.play(this, T0 + beat.at);
            }
        }

        /** {@code CozyGameView.moveCursor}. */
        void move(int who, int dx, int dy, long now) {
            game.move(who, dx, dy);
            ui.cursorMovedAt[who] = now;
            ui.lastActive[who] = now;
        }

        /** {@code CozyGameView.fillSquare}. */
        void fill(int who, long now) {
            int x = game.cursorX[who];
            int y = game.cursorY[who];
            ui.lastActive[who] = now;
            if (ui.gentleCheck && !game.puzzle.solution[y][x]
                    && game.puzzle.marks[y][x] != Puzzle.FILLED) {
                markWrongSquare(who, x, y, now);
                return;
            }
            if (game.wouldUndoPartner(who, now)) {
                agreeWithThePartner(x, y, now);
                return;
            }
            boolean clearing = game.puzzle.marks[y][x] == Puzzle.FILLED;
            game.mark(who, Puzzle.FILLED, now);
            effects.pulse(clearing ? Effects.Pulse.CLEAR : Effects.Pulse.FILL, x, y,
                    Theme.playerColor(who), now);
            if (!clearing) {
                burstAtCell(x, y, 5, Theme.playerColorDark(who), Effects.SHAPE_DOT, now);
                celebrateCompletedLines(who, x, y, now);
            }
            checkForWin(now);
        }

        /** {@code CozyGameView.agreeWithThePartner}: both of them reached for one square. */
        private void agreeWithThePartner(int x, int y, long now) {
            effects.pulse(Effects.Pulse.FILL, x, y, Theme.togetherColor(), now);
            ui.showToast("Great minds  ♥", Theme.togetherColor(), now);
        }

        /** {@code CozyGameView.crossSquare}. */
        void cross(int who, long now) {
            int x = game.cursorX[who];
            int y = game.cursorY[who];
            ui.lastActive[who] = now;
            boolean clearing = game.puzzle.marks[y][x] == Puzzle.CROSSED;
            game.mark(who, Puzzle.CROSSED, now);
            effects.pulse(clearing ? Effects.Pulse.CLEAR : Effects.Pulse.CROSS, x, y,
                    Theme.playerColor(who), now);
        }

        /** {@code CozyGameView.useHint}. */
        void hint(int who, long now) {
            ui.lastActive[who] = now;
            if (!ui.hintsOn) {
                ui.showToast("Hints are resting — see the cozy corner", Theme.SOFT_TEXT, now);
                return;
            }
            if (!game.hint(who, now)) {
                ui.showToast("The whole picture is already here  ✦", Theme.CANDLE, now);
                return;
            }
            int x = game.cursorX[who];
            int y = game.cursorY[who];
            ui.cursorMovedAt[who] = now;
            effects.pulse(Effects.Pulse.HINT, x, y, Theme.CANDLE, now);
            burstAtCell(x, y, 6, Theme.CANDLE, Effects.SHAPE_SPARK, now);
            ui.showToast("A little starlight showed the way  ✦", Theme.CANDLE, now);
            celebrateCompletedLines(who, x, y, now);
            checkForWin(now);
        }

        /** {@code CozyGameView.markWrongSquare}: gentle mode's soft correction. */
        private void markWrongSquare(int who, int x, int y, long now) {
            game.crossOut(who, x, y, now);
            effects.pulse(Effects.Pulse.ERROR, x, y, Theme.playerColorDark(who), now);
            if (now - lastGentleNudgeAt[who] > GENTLE_NUDGE_GAP_MS) {
                lastGentleNudgeAt[who] = now;
                ui.showToast("Not that one — try its neighbour", Theme.SOFT_TEXT, now);
            }
        }

        /** {@code CozyGameView.celebrateCompletedLines}. */
        private void celebrateCompletedLines(int who, int x, int y, long now) {
            boolean rowDone = game.puzzle.rowSolved(y);
            boolean colDone = game.puzzle.colSolved(x);
            boolean shared = (rowDone && game.lineWasShared(x, y, true))
                    || (colDone && game.lineWasShared(x, y, false));
            int crossed = game.puzzle.autoCrossCompletedLines(x, y);
            if (crossed == 0 && !rowDone && !colDone) {
                return;
            }
            long stagger = lineStaggerMs(game.size);
            if (rowDone) {
                sweepLine(true, x, y, now, stagger);
            }
            if (colDone) {
                sweepLine(false, x, y, now, stagger);
            }
            if (shared) {
                game.sharedLines++;
            }
            ui.showToast(lineCompleteMessage(who, rowDone, shared), Theme.CANDLE, now);
        }

        /** {@code CozyGameView.sweepLine}: the crest, and a mote off each square behind it. */
        private void sweepLine(boolean row, int x, int y, long when, long stagger) {
            BoardLayout board = renderer.board();
            for (int step = 0; step < game.size; step++) {
                int cx = row ? step : x;
                int cy = row ? y : step;
                long at = when + step * stagger;
                effects.pulse(Effects.Pulse.LINE, cx, cy, Theme.CANDLE, at);
                if (board != null) {
                    effects.burst(board.centreX(cx), board.centreY(cy), 2,
                            board.cell * 1.1f, Theme.CANDLE, Effects.SHAPE_MOTE, at);
                }
            }
        }

        /**
         * The first of the six line-complete sentences, or the shared one.
         *
         * <p>{@code CozyGameView} steps through all six so the words never repeat back to
         * back. A harness that stepped with it would put a different sentence on screen
         * every time a clip was re-recorded, which is the one thing a diffable frame may
         * not do — so the counter is pinned at nought and the frame always shows the
         * sentence a reviewer can compare against the last run.
         */
        private String lineCompleteMessage(int who, boolean rowDone, boolean shared) {
            String line = rowDone ? "row" : "column";
            return shared ? "You finished that " + line + " together  ♥"
                    : "Lovely — that " + line + " is complete  ✦";
        }

        /** {@code CozyGameView.burstAtCell}, geometry and all. */
        private void burstAtCell(int x, int y, int count, int color, int shape, long now) {
            BoardLayout board = renderer.board();
            if (board == null) {
                return;
            }
            effects.burst(board.centreX(x), board.centreY(y), count, board.cell * 2.4f,
                    color, shape, now);
        }

        /** {@code CozyGameView.checkForWin} together with {@code showWinCelebration}. */
        private void checkForWin(long now) {
            if (ui.won || !game.puzzle.complete()) {
                return;
            }
            ui.won = true;
            ui.winAt = now;
            if (Comfort.get().calmMotion) {
                return;
            }
            for (int i = 0; i < Effects.WIN_DRIFT_EMISSIONS; i++) {
                float x = viewWidth * (.16f + (i * 37 % 100) / 100f * .68f);
                effects.rise(x, viewHeight * .96f, 2, viewHeight * .30f,
                        Effects.confettiColor(i), i % 3 == 0 ? Effects.SHAPE_HEART
                                : (i % 3 == 1 ? Effects.SHAPE_PETAL : Effects.SHAPE_DOT),
                        now + i * Effects.WIN_DRIFT_STAGGER_MS, Effects.WIN_DRIFT_LIFE_MS);
            }
        }

        /** {@code CozyGameView.stepMenu}, repeat gate and all. */
        void stepMenu(int direction, int itemCount, long now) {
            if (now - lastMenuStepAt < Theme.MENU_REPEAT_MS) {
                return;
            }
            lastMenuStepAt = now;
            ui.menu = Math.floorMod(ui.menu + direction, itemCount);
        }
    }

    /**
     * {@code CozyGameView.lineStaggerMs}, copied rather than called.
     *
     * <p>It is package-private in {@code com.cozygrams.tv} and this class is not, so the
     * harness cannot reach it. Copied deliberately and marked as a copy: the alternative
     * was to leave the flat 18 ms the sweep used to run at, which on a 20-wide board lit
     * eight squares in one frame and made every clip of a completed line show a flash the
     * game stopped emitting. If the app's version moves, this one has to move with it.
     */
    private static long lineStaggerMs(int size) {
        return Math.max(26L, Math.min(46L, size <= 0 ? 46L : 640L / size));
    }

    /** {@code CozyGameView.GENTLE_NUDGE_GAP_MS}: how often gentle mode says it out loud. */
    private static final long GENTLE_NUDGE_GAP_MS = 1500;

    // ---- Recording -------------------------------------------------------------------

    private static void recordClips(int fps, String only) throws IOException {
        File frameRoot = new File(outDir, "frames");
        int made = 0;
        for (Clip clip : clips()) {
            if (!only.isEmpty() && !only.equals(clip.name)) {
                continue;
            }
            record(clip, new File(frameRoot, clip.name), fps);
            made++;
        }
        if (made == 0) {
            throw new IOException("no clip called \"" + only + "\"; try --list-clips");
        }
        System.out.println();
        System.out.println(made + (made == 1 ? " clip" : " clips") + " rendered.");
    }

    /**
     * Renders one clip as a numbered PNG sequence, and its contact sheet.
     *
     * <p>The clock is the still harness's own {@link #T0}, wound forward {@link #DEVICE_HZ}
     * steps a second: presses land, cursors ease and a frame is written every
     * {@code 60 / fps} steps, in exactly the order {@code CozyGameView} does those things.
     */
    private static void record(Clip clip, File dir, int fps) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create frame directory " + dir);
        }
        File[] stale = dir.listFiles((unused, name) -> name.endsWith(".png"));
        for (File file : stale == null ? new File[0] : stale) {
            if (!file.delete()) {
                throw new IOException("cannot replace " + file);
            }
        }

        Take take = clip.motion.build();
        warmUp(take, width, height);

        int step = DEVICE_HZ / fps;
        int frames = (int) (clip.durationMs * fps / 1000L) + 1;
        int samples = Math.min(SHEET_FRAMES, frames);
        BufferedImage[] tiles = new BufferedImage[samples];
        long[] stamps = new long[samples];
        int tileHeight = Math.round(SHEET_TILE_WIDTH * height / (float) width);

        long bytes = 0;
        int frame = 0;
        long previousOffset = 0;
        for (int tick = 0; frame < frames; tick++) {
            long offset = Math.round(tick * 1000d / DEVICE_HZ);
            advance(take, offset, previousOffset);
            previousOffset = offset;
            if (tick % step != 0) {
                continue;
            }
            BufferedImage image = draw(take, T0 + offset);
            File file = new File(dir, String.format("frame-%04d.png", frame));
            writeFrame(image, file, clip, frames, frame);
            bytes += file.length();
            for (int slot = 0; slot < samples; slot++) {
                if (sampleFrame(slot, frames, samples) == frame) {
                    tiles[slot] = scaleDown(image, SHEET_TILE_WIDTH, tileHeight);
                    stamps[slot] = offset;
                }
            }
            frame++;
        }

        File sheet = new File(outDir, clip.name + "-contact.png");
        contactSheet(clip, fps, frames, tiles, stamps, sheet);
        System.out.printf("  %-18s %4d frames  %5d ms  %6.1f MB of PNG  + %s%n",
                clip.name, frames, clip.durationMs, bytes / 1048576f, sheet.getName());
    }

    /**
     * Writes one frame of a clip, and says something useful when it cannot.
     *
     * <p>A clip holds its whole PNG sequence on disk until ffmpeg has read it: a 3.6 s clip
     * at 1920x1080 is about 165 MB, and the machine that recorded these has a 3.8 GB
     * {@code /tmp}. When that filled up, the harness died on
     * {@code javax.imageio.IIOException: I/O error writing PNG file!} eleven stack frames
     * deep, which says nothing about which clip, how far in, or what to do — so it says it
     * here instead.
     */
    private static void writeFrame(BufferedImage image, File file, Clip clip, int frames,
                                   int frame) throws IOException {
        try {
            if (!ImageIO.write(image, "png", file)) {
                throw new IOException("no PNG writer available for " + file);
            }
        } catch (IOException e) {
            throw new IOException("could not write frame " + (frame + 1) + " of " + frames
                    + " for " + clip.name + " — " + file.getParentFile().getUsableSpace()
                    / 1048576L + " MB free on " + file.getParent()
                    + ". A clip keeps every frame until ffmpeg has read it; set"
                    + " COZY_BUILD_DIR and the output directory somewhere with room,"
                    + " or record one clip at a time.", e);
        }
    }

    /**
     * One simulation step: play whatever the script owes by {@code offset}, then move the
     * cursors on by the time that has actually passed.
     *
     * <p>The glide is eased in real milliseconds, so it is handed the gap between this step
     * and the last one — 16 or 17 ms, alternating, exactly as a 60 Hz panel reports it —
     * rather than a nominal frame that would drift from the offsets the rest of the script
     * is timed against.
     */
    private static void advance(Take take, long offset, long previousOffset) {
        take.playThrough(offset);
        take.ui.animateCursors(take.game, offset - previousOffset);
    }

    /**
     * One throwaway frame before a script starts, drawn into a bitmap nobody keeps.
     *
     * <p>A burst is emitted in the board coordinates of the last frame <em>drawn</em> —
     * exactly as {@code CozyGameView.burstAtCell} reads them, because on a television
     * there has always been a previous frame. Without this the first fill of a clip, and
     * every particle in the two win stills, would come out at no coordinates at all.
     */
    private static void warmUp(Take take, int w, int h) {
        Bitmap scratch = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(scratch);
        take.renderer.draw(canvas, w, h, take.game, take.ui, take.effects, T0);
        canvas.release();
    }

    /**
     * Plays a scripted take forward to {@code atMs} and draws that one moment.
     *
     * <p>This is how a still gets to show something the game emitted rather than something
     * the harness posed. It is the same clock, the same script and the same order as
     * {@link #record}; the only difference is that the frames in between are simulated and
     * not drawn, which costs two draws instead of a hundred and thirty.
     *
     * <p>The last step lands on {@code atMs} exactly rather than on the 60 Hz grid below
     * it. A television would have drawn the nearest frame up to 17 ms earlier, but the
     * glide is an exponential in real milliseconds and integrates correctly over a short
     * final step — so "150 ms after the press" in a caption means 150.
     */
    private static void moment(Canvas canvas, int w, int h, Take take, long atMs) {
        take.viewWidth = w;
        take.viewHeight = h;
        warmUp(take, w, h);

        long previousOffset = 0;
        for (int tick = 1; ; tick++) {
            long offset = Math.round(tick * 1000d / DEVICE_HZ);
            if (offset >= atMs) {
                break;
            }
            advance(take, offset, previousOffset);
            previousOffset = offset;
        }
        advance(take, atMs, previousOffset);
        take.renderer.draw(canvas, w, h, take.game, take.ui, take.effects, T0 + atMs);
    }

    /** Which frame the {@code slot}-th contact-sheet tile samples, evenly across the clip. */
    private static int sampleFrame(int slot, int frames, int samples) {
        return samples < 2 ? 0
                : (int) Math.round(slot * (frames - 1) / (double) (samples - 1));
    }

    private static BufferedImage draw(Take take, long now) {
        Bitmap target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        take.renderer.draw(canvas, width, height, take.game, take.ui, take.effects, now);
        canvas.release();
        return target.image();
    }

    /**
     * Eight frames tiled with the millisecond offset burned under each one.
     *
     * <p>This is the artefact a reviewer actually reads, so it is built to be read: tiles
     * large enough to see a cursor in, a label strip that is part of the tile rather than
     * floating near it, and a hairline track under each label showing where in the clip the
     * tile sits — eight numbers are easy to read one at a time and hard to read as a
     * rhythm, and the rhythm is the thing being judged.
     */
    private static void contactSheet(Clip clip, int fps, int frames, BufferedImage[] tiles,
                                     long[] stamps, File file) throws IOException {
        int columns = Math.min(SHEET_COLUMNS, tiles.length);
        int rows = (tiles.length + columns - 1) / columns;
        int tileWidth = tiles[0].getWidth();
        int tileHeight = tiles[0].getHeight();
        int cellHeight = tileHeight + SHEET_LABEL_HEIGHT;
        int sheetWidth = SHEET_GUTTER + columns * (tileWidth + SHEET_GUTTER);
        int sheetHeight = SHEET_TITLE_HEIGHT + rows * (cellHeight + SHEET_GUTTER);

        BufferedImage sheet = new BufferedImage(sheetWidth, sheetHeight,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        quality(g);
        g.setColor(SHEET_BACKGROUND);
        g.fillRect(0, 0, sheetWidth, sheetHeight);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        g.setColor(SHEET_TITLE_INK);
        g.drawString(clip.name, SHEET_GUTTER, 46);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 21));
        g.setColor(SHEET_SOFT_INK);
        g.drawString(clip.durationMs + " ms  ·  " + frames + " frames at " + fps + " fps  ·  "
                        + width + "x" + height + "  ·  " + tiles.length
                        + " even samples, left to right then down  ·  "
                        + Provenance.sheetLine(),
                SHEET_GUTTER, 74);

        for (int i = 0; i < tiles.length; i++) {
            int x = SHEET_GUTTER + (i % columns) * (tileWidth + SHEET_GUTTER);
            int y = SHEET_TITLE_HEIGHT + (i / columns) * (cellHeight + SHEET_GUTTER);
            g.drawImage(tiles[i], x, y, null);
            g.setColor(SHEET_LABEL_FACE);
            g.fillRect(x, y + tileHeight, tileWidth, SHEET_LABEL_HEIGHT);

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            g.setColor(SHEET_STAMP_INK);
            g.drawString("+" + stamps[i] + " ms", x + 14, y + tileHeight + 34);

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 19));
            g.setColor(SHEET_SOFT_INK);
            String label = "frame " + sampleFrame(i, frames, tiles.length);
            g.drawString(label, x + tileWidth - 14 - g.getFontMetrics().stringWidth(label),
                    y + tileHeight + 33);

            int trackY = y + tileHeight + SHEET_LABEL_HEIGHT - 12;
            int trackWidth = tileWidth - 28;
            g.setColor(SHEET_EDGE);
            g.fillRect(x + 14, trackY, trackWidth, 4);
            g.setColor(SHEET_STAMP_INK);
            g.fillRect(x + 14, trackY, Math.max(4, Math.round(trackWidth * stamps[i]
                    / (float) Math.max(1, clip.durationMs))), 4);

            g.setColor(SHEET_EDGE);
            g.drawRect(x, y, tileWidth - 1, cellHeight - 1);
        }
        g.dispose();

        if (!ImageIO.write(sheet, "png", file)) {
            throw new IOException("no PNG writer available for " + file);
        }
    }

    /**
     * Halves repeatedly before the final step. One 3.7x bilinear draw turns the clue digits
     * and the cursor's ring into aliased mush, which are the two things a reviewer most
     * needs to see survive.
     */
    private static BufferedImage scaleDown(BufferedImage source, int targetWidth,
                                           int targetHeight) {
        BufferedImage current = source;
        int w = source.getWidth();
        int h = source.getHeight();
        while (w / 2 > targetWidth && h / 2 > targetHeight) {
            w /= 2;
            h /= 2;
            current = redraw(current, w, h);
        }
        return redraw(current, targetWidth, targetHeight);
    }

    private static BufferedImage redraw(BufferedImage source, int w, int h) {
        BufferedImage target = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        quality(g);
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();
        return target;
    }

    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    // ---- Plumbing --------------------------------------------------------------------

    private static File render(Frame frame) throws IOException {
        forgetSceneMemory();
        int w = frame.width();
        int h = frame.height();

        Bitmap target = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        Renderer renderer = new Renderer();
        renderer.setScenes(room, garden);
        frame.shot.draw(canvas, renderer, w, h);
        canvas.release();

        BufferedImage image = target.image();
        Provenance.stamp(image);

        File file = new File(outDir, frame.name);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create output directory " + parent);
        }
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("no PNG writer available for " + file);
        }
        System.out.printf("  %-38s %dx%d  %d bytes%n", frame.name, image.getWidth(),
                image.getHeight(), file.length());
        return file;
    }

    /**
     * Puts back everything the two menus remember in static fields.
     *
     * <p>The armed "are you sure?" question, the returning-player greeting, the pending
     * board size and who is tidying the room all live on the scene classes rather than on
     * {@link UiState}, and the whole still set is rendered inside one JVM. Without this,
     * shot 16 arming the restart row would leave it armed for every frame after it, and
     * the set would depend on the order it happens to be listed in — which is exactly the
     * kind of thing that is only noticed once somebody has spent a day arguing about a
     * screenshot. Called before every frame rather than after the ones that dirty
     * something, so a new scenario cannot forget.
     *
     * <p>{@code HudScene.puzzlesBeforeTonight} is the one piece of room memory not reset
     * here: its setter clamps at zero and its "nobody has said" value is -1, so there is
     * no public way back. No frame sets it.
     */
    private static void forgetSceneMemory() {
        HomeScene.disarmRestart();
        HomeScene.setWelcome("");
        HomeScene.setPendingSize(0);
        SettingsScene.disarmDefaults();
        SettingsScene.setTidyingPlayer(-1);
        HudScene.setRemoteOnly(false);
        HudScene.setJoinedAt(0);
        HudScene.setSeatStirredAt(0, 0);
        HudScene.setSeatStirredAt(1, 0);
        Comfort.get().restoreDefaults();
    }

    private static Bitmap load(File assets, String name) throws IOException {
        File file = new File(assets, name);
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath());
        if (bitmap == null) {
            throw new IOException("could not decode backdrop " + file
                    + " (render.sh converts the .webp sources into this directory)");
        }
        return bitmap;
    }

    /**
     * Where the decoded backdrops live. {@code render.sh} sets {@code cozy.assets}; the
     * fallback walks up from the working directory so the class can also be run by hand.
     */
    private static File assetDir() throws IOException {
        String property = System.getProperty("cozy.assets");
        if (property != null && !property.isEmpty()) {
            return new File(property).getAbsoluteFile();
        }
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (dir != null) {
            File candidate = new File(dir, "tools/preview/build/assets");
            if (candidate.isDirectory()) {
                return candidate;
            }
            dir = dir.getParentFile();
        }
        throw new IOException("cannot locate tools/preview/build/assets; "
                + "pass -Dcozy.assets=<dir> or run tools/preview/render.sh");
    }

    // ---- Provenance ----------------------------------------------------------------------

    /**
     * Where these pictures came from — burned into every still, and written out beside them.
     *
     * <p>This exists because of one specific, expensive failure. A set of frames rendered
     * before v2.0.0 sat in {@code tools/preview/out/} and in a copy of it, and a review was
     * then built on them: pink filled tiles, per-player move counters, "16 little chapters".
     * None of those had been in the game for a commit and a half. Roughly half of the
     * resulting problem list described a build nobody was running, and the work of finding
     * that out cost more than the work of fixing anything on it.
     *
     * <p><b>Why the stamp is in the pixels.</b> {@code tools/preview/out/} is in
     * {@code .gitignore} and always has been — {@code git log -- tools/preview/out} is
     * empty — so these PNGs are never committed, no CI job can ever see them, and there is
     * nothing for a repository-side freshness check to check. What actually happens to
     * them is that they get copied somewhere and opened one at a time in an image viewer, a
     * long way from any folder they were once next to. A sidecar file does not survive that
     * journey. Seven characters in the corner do, and they are enough: {@code git log -1
     * <sha>} answers "what am I looking at" in one command.
     *
     * <p><b>What it costs.</b> A stamp puts pixels that are not the interface into a frame
     * people measure the interface with, so it goes in the extreme bottom-left corner,
     * outside the TV-safe area the game itself never draws past, where it can occlude
     * backdrop and nothing else. Bottom-<em>right</em> was the first attempt and was wrong:
     * the rail lives in that corner, and at 1280x720 the badge landed across the rail's
     * "Six pictures done" hearts — a screenshot mechanism hiding the thing the screenshot
     * was taken to show.
     *
     * <p><b>What is not stamped.</b> The frames inside a clip. They exist for a few minutes
     * between being rendered and being handed to ffmpeg, they are deleted afterwards, and a
     * badge burned into fifty-eight of them in a row is a distraction in the one artefact
     * that is about motion. The clip's contact sheet — which is the thing a review actually
     * reads and keeps — carries the same line in its header instead.
     *
     * <p><b>Why there is no clock in it.</b> The whole harness rests on the same input
     * producing the same bytes, so two renders of one tree can be compared with
     * {@code cmp}. A rendering date in the corner would break that on the second day. The
     * date goes in {@link #MANIFEST}, where it costs nothing; what goes in the corner is
     * the commit plus a fingerprint of the sources that were actually compiled, so a dirty
     * tree is visibly not its commit and two runs of the same tree still agree to the byte.
     */
    private static final class Provenance {

        /** A {@code sha256sum -c} file: run it from the repository root. */
        static final String MANIFEST = "MANIFEST.sha256";

        /** The folder's own index, for whoever opens the directory rather than a file. */
        static final String INDEX = "README.md";

        /** How many hex characters of a digest are shown. Seven, as git does. */
        private static final int SHORT = 7;

        private static final Color STAMP_PLATE = new Color(0x14110F);
        private static final Color STAMP_INK = new Color(0xD8C4B4);

        private static File repo;
        private static String commit = "unknown";
        private static String fingerprint = "unknown";
        private static List<String> inputs = new ArrayList<>();

        private Provenance() {
        }

        /**
         * Works out the tree once, before the first frame is drawn.
         *
         * <p>Best effort by design: a harness that refused to render because it could not
         * find {@code .git} would be worse than one that renders and says so. Everything
         * unknown says "unknown" in the corner, which is itself a true and useful statement
         * about the picture.
         */
        static void resolve() {
            repo = repoRoot();
            if (repo == null) {
                System.err.println("Preview: not inside the repository, so these frames "
                        + "carry no commit. Run from the repository root.");
                return;
            }
            commit = readCommit(new File(repo, ".git"));
            inputs = renderInputs();
            fingerprint = shorten(digestOf(String.join("\n", hashLines(inputs))));
        }

        static String describe() {
            return commit + " · src " + fingerprint + " · " + inputs.size() + " inputs";
        }

        /** What the corner of every still says. Derived from the tree, never from a clock. */
        private static String badge(int w, int h) {
            return "preview · " + commit + " · src " + fingerprint + " · " + w + "×" + h;
        }

        /**
         * Draws the badge into the bottom-left corner, on the {@link BufferedImage} rather
         * than through the {@code android.graphics} stubs.
         *
         * <p>Deliberately drawn here and not by any scene: nothing the app owns should be
         * able to see, move or style it, and anyone reading a frame should be able to tell
         * at a glance that it is not part of the interface.
         */
        static void stamp(BufferedImage image) {
            int w = image.getWidth();
            int h = image.getHeight();
            float scale = Math.max(1f, h / 1080f);

            Graphics2D g = image.createGraphics();
            quality(g);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.round(17 * scale)));
            FontMetrics metrics = g.getFontMetrics();
            String text = badge(w, h);

            int padX = Math.round(12 * scale);
            int padY = Math.round(6 * scale);
            int margin = Math.round(14 * scale);
            int plateWidth = metrics.stringWidth(text) + padX * 2;
            int plateHeight = metrics.getAscent() + metrics.getDescent() + padY * 2;
            int left = margin;
            int top = h - margin - plateHeight;

            g.setColor(new Color(STAMP_PLATE.getRed(), STAMP_PLATE.getGreen(),
                    STAMP_PLATE.getBlue(), 205));
            g.fillRoundRect(left, top, plateWidth, plateHeight, Math.round(8 * scale),
                    Math.round(8 * scale));
            g.setColor(STAMP_INK);
            g.drawString(text, left + padX, top + padY + metrics.getAscent());
            g.dispose();
        }

        /** The same provenance, for the header of a clip's contact sheet. */
        static String sheetLine() {
            return commit + " · src " + fingerprint;
        }

        // ---- The manifest ----------------------------------------------------------------

        /**
         * Every input that decides what these pixels are, and every file that came out.
         *
         * <p>Written in {@code sha256sum -c} format on purpose. The one question worth
         * being able to ask mechanically is "were these rendered from this tree?", and from
         * the repository root
         *
         * <pre>
         *   sha256sum -c tools/preview/out/MANIFEST.sha256
         * </pre>
         *
         * answers it in both directions at once — a source that has moved since the render,
         * and a PNG that has been edited since it was written, both come out as a named
         * failing line rather than as an argument.
         */
        static void writeManifest(File dir, List<File> written) throws IOException {
            StringBuilder out = new StringBuilder();
            out.append("# CozyGrams preview screenshots\n")
                    .append("# rendered   ").append(stampedDate()).append('\n')
                    .append("# commit     ").append(commit)
                    .append("   (sources fingerprint ").append(fingerprint).append(")\n")
                    .append("# resolution ").append(width).append('x').append(height)
                    .append(", ").append(written.size()).append(" frames\n")
                    .append("#\n")
                    .append("# regenerate  tools/preview/render.sh tools/preview/out ")
                    .append(width).append(' ').append(height).append('\n')
                    .append("# verify      sha256sum -c tools/preview/out/")
                    .append(MANIFEST).append("   (from the repository root)\n")
                    .append("#\n")
                    .append("# The input paths are relative to the repository root, so the "
                            + "check has to be run\n")
                    .append("# from there; frames rendered outside the repository are "
                            + "recorded absolutely and\n")
                    .append("# resolve from anywhere. A failure names the file: a source "
                            + "means the frames are\n")
                    .append("# stale, a PNG means somebody has edited one.\n");

            List<String> lines = new ArrayList<>(hashLines(inputs));
            for (File file : written) {
                lines.add(hashLine(relative(file), file.toPath()));
            }
            for (String line : lines) {
                out.append(line).append('\n');
            }
            Files.writeString(new File(dir, MANIFEST).toPath(), out.toString(),
                    StandardCharsets.UTF_8);
        }

        /**
         * The folder's own index: what each frame is for, and where it came from.
         *
         * <p>The captions are the ones in {@link #frames()}, so a scenario cannot be
         * described one way in the source and another way here.
         */
        static void writeIndex(File dir, Frame[] frames) throws IOException {
            StringBuilder out = new StringBuilder();
            out.append("# CozyGrams preview frames\n\n")
                    .append("Rendered from **").append(commit)
                    .append("** (sources fingerprint `").append(fingerprint)
                    .append("`) on ").append(stampedDate()).append(", at ")
                    .append(width).append('x').append(height).append(".\n\n")
                    .append("Every frame carries the same commit and fingerprint in its "
                            + "bottom-left corner, because\nthis directory is in "
                            + "`.gitignore` and a PNG that leaves it takes nothing else "
                            + "with it.\nIf the corner of a picture disagrees with `git "
                            + "log -1`, the picture is old.\n\n")
                    .append("```sh\n")
                    .append("tools/preview/render.sh tools/preview/out ")
                    .append(width).append(' ').append(height)
                    .append("     # regenerate\n")
                    .append("sha256sum -c tools/preview/out/").append(MANIFEST)
                    .append("   # from the repository root\n")
                    .append("```\n\n")
                    .append("| frame | what it is for |\n| --- | --- |\n");
            for (Frame frame : frames) {
                out.append("| [`").append(frame.name).append("`](").append(frame.name)
                        .append(") | ").append(frame.caption).append(" |\n");
            }
            out.append("\nThe motion clips are a separate artefact — "
                    + "`tools/preview/record.sh` — because half of\na cozy interface is "
                    + "its timing and no still can show that.\n");
            Files.writeString(new File(dir, INDEX).toPath(), out.toString(),
                    StandardCharsets.UTF_8);
        }

        // ---- Reading the tree ------------------------------------------------------------

        /** The repository root, found by walking up from wherever the JVM was started. */
        private static File repoRoot() {
            File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
            while (dir != null) {
                if (new File(dir, "app/src/main/java/com/cozygrams/tv").isDirectory()) {
                    return dir;
                }
                dir = dir.getParentFile();
            }
            return null;
        }

        /**
         * The short commit, read out of {@code .git} directly.
         *
         * <p>No {@code git} process: the harness has no dependencies and is not the place
         * to acquire one. {@code HEAD} is either a ref to follow or a detached hash, and a
         * ref that has no loose file is in {@code packed-refs}.
         */
        private static String readCommit(File gitDir) {
            String head = readLine(new File(gitDir, "HEAD"));
            if (head == null) {
                return "no-git";
            }
            if (!head.startsWith("ref:")) {
                return shorten(head);
            }
            String ref = head.substring(4).trim();
            String loose = readLine(new File(gitDir, ref));
            if (loose != null) {
                return shorten(loose);
            }
            return shorten(fromPackedRefs(new File(gitDir, "packed-refs"), ref));
        }

        private static String fromPackedRefs(File packed, String ref) {
            if (!packed.isFile()) {
                return null;
            }
            try {
                for (String line : Files.readAllLines(packed.toPath())) {
                    int space = line.indexOf(' ');
                    if (space > 0 && line.substring(space + 1).trim().equals(ref)) {
                        return line.substring(0, space);
                    }
                }
            } catch (IOException ignored) {
                return null;
            }
            return null;
        }

        private static String readLine(File file) {
            if (!file.isFile()) {
                return null;
            }
            try {
                return Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
            } catch (IOException ignored) {
                return null;
            }
        }

        /**
         * Every file whose bytes can change what a frame looks like, repository-relative.
         *
         * <p>The app half is read back off the classpath rather than from a list kept here:
         * {@code render.sh} decides which sources are compiled, and a second copy of that
         * decision would be wrong the first time the render set grew. Whatever is in
         * {@code com/cozygrams/tv} as a class file was compiled into this run, so its
         * source is an input by definition — and a source that is <em>not</em> compiled in
         * cannot change a pixel and is deliberately left out, so that touching the audio
         * engine does not mark the screenshots stale.
         */
        private static List<String> renderInputs() {
            TreeSet<String> paths = new TreeSet<>();
            for (String name : compiledClassNames()) {
                String source = "app/src/main/java/com/cozygrams/tv/" + name + ".java";
                if (new File(repo, source).isFile()) {
                    paths.add(source);
                }
            }
            paths.add("tools/preview/Preview.java");
            paths.add("tools/preview/render.sh");
            paths.add("tools/preview/record.sh");
            paths.add("app/src/main/res/drawable-nodpi/cozy_room.webp");
            paths.add("app/src/main/res/drawable-nodpi/moon_garden.webp");
            collectStubs(new File(repo, "tools/preview/stubs"), paths);
            paths.removeIf(path -> !new File(repo, path).isFile());
            return new ArrayList<>(paths);
        }

        /** The app classes this JVM was actually given, inner classes folded into their outer. */
        private static Set<String> compiledClassNames() {
            Set<String> names = new TreeSet<>();
            for (String entry : System.getProperty("java.class.path", "")
                    .split(File.pathSeparator)) {
                File dir = new File(entry, "com/cozygrams/tv");
                File[] classes = dir.listFiles((unused, name) -> name.endsWith(".class"));
                for (File file : classes == null ? new File[0] : classes) {
                    String name = file.getName();
                    name = name.substring(0, name.length() - ".class".length());
                    int inner = name.indexOf('$');
                    names.add(inner < 0 ? name : name.substring(0, inner));
                }
            }
            return names;
        }

        private static void collectStubs(File dir, TreeSet<String> into) {
            File[] children = dir.listFiles();
            for (File child : children == null ? new File[0] : children) {
                if (child.isDirectory()) {
                    collectStubs(child, into);
                } else if (child.getName().endsWith(".java")) {
                    into.add(relative(child));
                }
            }
        }

        // ---- Hashing ---------------------------------------------------------------------

        private static List<String> hashLines(List<String> paths) {
            List<String> lines = new ArrayList<>();
            for (String path : paths) {
                lines.add(hashLine(path, new File(repo, path).toPath()));
            }
            return lines;
        }

        /** One {@code sha256sum} line: the digest, two spaces, the path. */
        private static String hashLine(String path, Path file) {
            try {
                return digestOf(Files.readAllBytes(file)) + "  " + path;
            } catch (IOException e) {
                return "0".repeat(64) + "  " + path;
            }
        }

        private static String digestOf(String text) {
            return digestOf(text.getBytes(StandardCharsets.UTF_8));
        }

        private static String digestOf(byte[] bytes) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
                StringBuilder hex = new StringBuilder(digest.length * 2);
                for (byte b : digest) {
                    hex.append(Character.forDigit((b >> 4) & 0xf, 16))
                            .append(Character.forDigit(b & 0xf, 16));
                }
                return hex.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("no SHA-256 on this JVM", e);
            }
        }

        private static String shorten(String hash) {
            return hash == null || hash.length() < SHORT ? "unknown"
                    : hash.substring(0, SHORT);
        }

        /**
         * A path relative to the repository root, with forward slashes on every platform.
         *
         * <p>Anything outside the repository — and everything at all when the repository
         * could not be found — stays absolute rather than being made up. {@code sha256sum
         * -c} takes both, so a mixed manifest still checks; it simply has to be run from
         * the root for the relative half to resolve.
         */
        private static String relative(File file) {
            String path = file.getAbsolutePath();
            String root = repo == null ? null : repo.getAbsolutePath() + File.separator;
            return (root != null && path.startsWith(root) ? path.substring(root.length())
                    : path).replace(File.separatorChar, '/');
        }

        /** The rendering date, in the manifest and the index only. Never in a pixel. */
        private static String stampedDate() {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'Z'")
                    .format(ZonedDateTime.now(ZoneOffset.UTC));
        }
    }
}
