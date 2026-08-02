import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.cozygrams.tv.BoardLayout;
import com.cozygrams.tv.Effects;
import com.cozygrams.tv.GameState;
import com.cozygrams.tv.HomeScene;
import com.cozygrams.tv.Puzzle;
import com.cozygrams.tv.PuzzleGenerator;
import com.cozygrams.tv.Renderer;
import com.cozygrams.tv.SettingsScene;
import com.cozygrams.tv.Theme;
import com.cozygrams.tv.UiState;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Renders a fixed set of CozyGrams screens to PNG on a desktop JVM.
 *
 * <p>The app draws its whole interface onto one {@code android.graphics.Canvas}, so with
 * the Java2D-backed stubs in {@code tools/preview/stubs} the real renderer runs unchanged
 * here — no emulator, no device, no {@code View}.
 *
 * <p><b>Determinism.</b> Every timestamp is derived from {@link #T0} and every board from
 * a fixed seed. Nothing calls {@code System.currentTimeMillis()} or {@code Math.random()},
 * so re-running the harness produces byte-identical files and screenshots can be diffed
 * across commits.
 *
 * <pre>
 *   Preview &lt;outputDir&gt; [width] [height]
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

    public static void main(String[] args) throws IOException {
        outDir = new File(args.length > 0 && !args[0].isEmpty() ? args[0]
                : "tools/preview/out").getAbsoluteFile();
        if (args.length > 1) {
            width = Integer.parseInt(args[1]);
        }
        if (args.length > 2) {
            height = Integer.parseInt(args[2]);
        }
        if (width < 320 || height < 240) {
            throw new IllegalArgumentException("resolution too small: " + width + "x" + height);
        }
        if (!outDir.isDirectory() && !outDir.mkdirs()) {
            throw new IOException("cannot create output directory " + outDir);
        }

        File assets = assetDir();
        room = load(assets, "cozy_room.png");
        garden = load(assets, "moon_garden.png");

        System.out.println("CozyGrams preview  " + width + "x" + height);
        System.out.println("  fonts     : " + Paint.describeFonts());
        System.out.println("  backdrops : " + assets);
        System.out.println("  output    : " + outDir);
        System.out.println();

        List<String> written = new ArrayList<>();
        for (Object[] entry : shots()) {
            written.add(render((String) entry[0], (Shot) entry[1]));
        }

        System.out.println();
        System.out.println(written.size() + " screenshots written.");
    }

    // ---- Scenarios -------------------------------------------------------------------

    private static Object[][] shots() {
        return new Object[][]{
                {"01-home.png", (Shot) Preview::home},
                {"02-home-size.png", (Shot) Preview::homeSize},
                {"03-settings.png", (Shot) Preview::settings},
                {"04-game-5x5-fresh.png", (Shot) Preview::gameFresh},
                {"05-game-10x10-mid.png", (Shot) Preview::gameTen},
                {"06-game-15x15-mid.png", (Shot) Preview::gameFifteen},
                {"07-game-20x20-mid.png", (Shot) Preview::gameTwenty},
                {"08-game-story.png", (Shot) Preview::gameStory},
                {"09-game-effects.png", (Shot) Preview::gameEffects},
                {"10-win-early.png", (Shot) Preview::winEarly},
                {"11-win-settled.png", (Shot) Preview::winSettled},
                {"12-game-cursors-overlap.png", (Shot) Preview::cursorsOverlap},
                {"13-game-bigtext.png", (Shot) Preview::bigText},
                {"14-game-contrast.png", (Shot) Preview::highContrast},
                {"15-game-toast.png", (Shot) Preview::toast},
        };
    }

    /** 01 — the title screen with CONTINUE focused. */
    private static void home(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_OWL, 10);
        UiState ui = new UiState();
        ui.screen = UiState.HOME;
        ui.menu = HomeScene.ITEM_CONTINUE;
        ui.joined[0] = true;
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /** 02 — the same menu with the BOARD SIZE stepper focused and both players present. */
    private static void homeSize(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_OWL, 10);
        UiState ui = new UiState();
        ui.screen = UiState.HOME;
        ui.menu = HomeScene.ITEM_SIZE;
        ui.joined[0] = true;
        ui.joined[1] = true;
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /** 03 — the cozy corner with music, sound and hints all on. */
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

    /** 04 — an untouched 5x5 endless board with only Rose at the table. */
    private static void gameFresh(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_SWEETHEART, 5);
        UiState ui = playing(false);
        game.cursorX[0] = 2;
        game.cursorY[0] = 1;
        ui.snapCursors(game);
        ui.showToast("Sky can join any time — just press a button", Theme.BLUE, T0 - 900);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /** 05 — a 10x10 board about halfway home, both players playing. */
    private static void gameTen(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = midGameTen();
        UiState ui = playing(true);
        placeCursors(game, ui, 3, 4, 7, 2);
        ui.showToast("Lovely, Rose — that row is finished", Theme.PINK, T0 - 1400);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /** 06 — the same idea at 15x15. */
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

    /** 07 — 20x20: the smallest cells and the busiest clue gutters the game can produce. */
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

    /** 08 — an authored story-book chapter part way through. */
    private static void gameStory(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_SWEETHEART, 5);
        game.startStory(STORY_CHAPTER);
        game.solved = 1;
        fillPicture(game, .5f);
        scatterCrosses(game, 4);
        game.moves[0] = 34;
        game.moves[1] = 27;
        UiState ui = playing(true);
        placeCursors(game, ui, 4, 5, 6, 2);
        ui.showToast("Chapter ten — Sleepy Cat", Theme.GOLD, T0 - 1100);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /** 09 — ripples and particles caught 180 ms after they were emitted. */
    private static void gameEffects(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = endless(SUBJECT_LOVE_BIRDS, 10);
        fillPicture(game, .5f);
        scatterCrosses(game, 3);
        game.moves[0] = 38;
        game.moves[1] = 31;
        UiState ui = playing(true);
        placeCursors(game, ui, 4, 5, 8, 4);

        // Effects are emitted in board / screen coordinates, so the layout has to be
        // resolved the same way the renderer will resolve it a moment from now.
        Theme.setScreenHeight(h);
        BoardLayout board = new BoardLayout(w, h, game.puzzle, true);

        long emit = T0 - 180;
        Effects effects = new Effects();
        effects.pulse(Effects.Pulse.FILL, 4, 5, Theme.PINK, emit);
        effects.pulse(Effects.Pulse.CROSS, 6, 2, Theme.BLUE, emit - 70);
        effects.pulse(Effects.Pulse.HINT, 2, 7, Theme.GOLD, emit - 40);
        effects.pulse(Effects.Pulse.LINE, 8, 4, Theme.GOLD, emit - 120, 900);
        effects.burst(board.centreX(4), board.centreY(5), 14, 420, Theme.PINK,
                Effects.SHAPE_HEART, emit);
        effects.burst(board.centreX(8), board.centreY(4), 18, 520, Theme.GOLD,
                Effects.SHAPE_SPARK, emit - 120);
        effects.burst(board.centreX(6), board.centreY(2), 12, 380, Theme.BLUE,
                Effects.SHAPE_PETAL, emit - 70);

        ui.showToast("That whole line is done!", Theme.GOLD, T0 - 400);
        renderer.draw(canvas, w, h, game, ui, effects, T0);
    }

    /** 10 — the win card 300 ms in: the picture is still assembling itself. */
    private static void winEarly(Canvas canvas, Renderer renderer, int w, int h) {
        drawWin(canvas, renderer, w, h, 300);
    }

    /** 11 — the same win card at 2200 ms: fully revealed, with the prompt showing. */
    private static void winSettled(Canvas canvas, Renderer renderer, int w, int h) {
        drawWin(canvas, renderer, w, h, 2200);
    }

    private static void drawWin(Canvas canvas, Renderer renderer, int w, int h,
                                long elapsed) {
        GameState game = endless(SUBJECT_SWEETHEART, 10);
        game.solved = 3;
        fillPicture(game, 1f);
        crossEveryEmptySquare(game);
        game.moves[0] = 63;
        game.moves[1] = 58;

        UiState ui = playing(true);
        placeCursors(game, ui, 9, 9, 0, 9);
        ui.won = true;
        ui.winAt = T0;
        ui.toast = "";

        // One celebration timeline, sampled at two different moments. Emitters sit low and
        // to either side of the centre column, so the assembling picture and the win copy
        // stay reviewable while the effects are still obviously live.
        Effects effects = new Effects();
        effects.burst(w * .28f, h * .72f, 12, 520, Theme.PINK, Effects.SHAPE_HEART, T0);
        effects.burst(w * .72f, h * .72f, 12, 520, Theme.PINK, Effects.SHAPE_HEART,
                T0 + 90);
        for (int i = 0; i < 4; i++) {
            effects.burst(w * (.26f + i * .16f), h * .84f, 9, 560,
                    Effects.confettiColor(i), Effects.SHAPE_PETAL, T0 + 120L * i);
        }
        effects.rise(w / 2f, h * .80f, 16, 240, Theme.PINK, Effects.SHAPE_HEART,
                T0 + 1500, 2600);

        renderer.draw(canvas, w, h, game, ui, effects, T0 + elapsed);
    }

    /** 12 — both cursors on the same square, which earns a shared heart. */
    private static void cursorsOverlap(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = midGameTen();
        UiState ui = playing(true);
        placeCursors(game, ui, 5, 5, 5, 5);
        ui.showToast("Together again ♥", Theme.PINK, T0 - 700);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /** 13 — the same position with the larger-text comfort option on. */
    private static void bigText(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = midGameTen();
        UiState ui = playing(true);
        ui.bigTextOn = true;
        placeCursors(game, ui, 3, 4, 7, 2);
        ui.showToast("Larger text is on", Theme.CREAM, T0 - 1200);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /** 14 — the same position with extra contrast on. */
    private static void highContrast(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = midGameTen();
        UiState ui = playing(true);
        ui.highContrastOn = true;
        placeCursors(game, ui, 3, 4, 7, 2);
        ui.showToast("Extra contrast is on", Theme.CREAM, T0 - 1200);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
    }

    /** 15 — a long message ribbon, freshly shown, to check the widest toast case. */
    private static void toast(Canvas canvas, Renderer renderer, int w, int h) {
        GameState game = midGameTen();
        UiState ui = playing(true);
        placeCursors(game, ui, 3, 4, 7, 2);
        ui.showToast("Sky filled in the last square of that column — the whole line is "
                + "put to bed now", Theme.GOLD, T0 - 150);
        renderer.draw(canvas, w, h, game, ui, new Effects(), T0);
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

    // ---- Plumbing --------------------------------------------------------------------

    private static String render(String name, Shot shot) throws IOException {
        Bitmap target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        Renderer renderer = new Renderer();
        renderer.setScenes(room, garden);
        shot.draw(canvas, renderer, width, height);
        canvas.release();

        File file = new File(outDir, name);
        BufferedImage image = target.image();
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("no PNG writer available for " + file);
        }
        System.out.printf("  %-30s %dx%d  %d bytes%n", name, image.getWidth(),
                image.getHeight(), file.length());
        return name;
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
}
