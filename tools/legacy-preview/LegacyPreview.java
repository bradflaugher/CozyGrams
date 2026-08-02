import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;

import com.cozygrams.tv.CozyGameView;
import com.cozygrams.tv.GameState;
import com.cozygrams.tv.R;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Renders the <b>v1.2.0</b> CozyGrams screens to PNG on a desktop JVM, as a baseline the
 * current build can be compared against frame by frame.
 *
 * <h2>What is actually being drawn</h2>
 *
 * <p>Every pixel comes from the real, unmodified {@code CozyGameView} of commit
 * {@code a970d65} — the file under {@code tools/legacy-preview/legacy-src} is byte-for-byte
 * the shipped one, checked against {@code git show} by {@code render-legacy.sh}. No
 * drawing code was extracted, retyped or "ported": the class extends the stub
 * {@code android.view.View}, is constructed with a stub {@code Context}, and is asked to
 * draw through {@code onDraw}. Nothing in this file can change a colour, a size or an
 * offset, which is the whole point — an approximated baseline would make the comparison
 * worthless.
 *
 * <h2>How a screen is posed</h2>
 *
 * <p>Two mechanisms, both of which leave the view's own code untouched:
 *
 * <ul>
 *   <li><b>SharedPreferences.</b> The v1.2.0 constructor restores the whole saved game
 *       from preferences, so seed, board size, puzzles solved, story mode and move counts
 *       are supplied by writing exactly the keys {@code CozyGameView.save()} writes. The
 *       view then restores itself the way it would on a real cold start.</li>
 *   <li><b>Reflection.</b> The transient screen state ({@code screen}, {@code menu},
 *       {@code won}, {@code winAt}, {@code toast}, {@code toastAt}) is private and has no
 *       preference key, so it is set directly. Board marks and cursors go through
 *       {@code GameState}'s public fields, the same way {@code tools/preview/Preview.java}
 *       poses the new renderer.</li>
 * </ul>
 *
 * <h2>Determinism</h2>
 *
 * <p>The stub {@code SystemClock} is injected, never a real clock, and every board comes
 * from a fixed seed, so re-running writes byte-identical files. Seeds, fill fractions,
 * cross strides, cursor squares and move counts are copied from
 * {@code tools/preview/Preview.java} so the two sets line up.
 *
 * <pre>
 *   LegacyPreview &lt;outputDir&gt; [width] [height]
 * </pre>
 */
public final class LegacyPreview {

    /** The frozen clock, in the "milliseconds of uptime" units the game uses. */
    private static final long T0 = 1_800_000L;

    // Seeds. tools/preview/Preview.java picks its subjects as
    // seedFor(subject, 0) == subject, so these integers are literally the seeds the new
    // harness feeds its generator. v1.2.0's PuzzleGenerator reads the same seed with
    // kind = floorMod(seed, 8), so it draws whatever it drew — which is the point.
    /** New harness "Sweetheart"; v1.2.0 seed 0 -> kind 0, also "Sweetheart" (heart). */
    private static final long SEED_SWEETHEART = 0;
    /** New harness "Rain on the Window"; v1.2.0 seed 11 -> kind 3, "Cocoa Date" (mug). */
    private static final long SEED_RAIN = 11;
    /** New harness "Fresh Baked Pie"; v1.2.0 seed 14 -> kind 6, "Starlight Wish". */
    private static final long SEED_PIE = 14;
    /** New harness "Little Owl"; v1.2.0 seed 16 -> kind 0, "Sweetheart" (heart). */
    private static final long SEED_OWL = 16;

    /** Story chapter 9 is "Sleepy Cat" in both versions' libraries. */
    private static final int STORY_CHAPTER = 9;

    // v1.2.0's screen ids, from the private constants at the top of CozyGameView.
    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_GAME = 1;
    private static final int SCREEN_SETTINGS = 2;

    private static int width = 1920;
    private static int height = 1080;
    private static File outDir;
    private static File assets;

    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static Method onDraw;

    private LegacyPreview() {
    }

    /** One screenshot: a name and the state it needs. */
    private interface Shot {
        /** Poses {@code view} and returns the uptime its single frame is drawn at. */
        long pose(CozyGameView view);
    }

    public static void main(String[] args) throws Exception {
        outDir = new File(args.length > 0 && !args[0].isEmpty() ? args[0]
                : "tools/legacy-preview/out").getAbsoluteFile();
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

        assets = assetDir();
        requireAsset("cozy_room.png");
        requireAsset("moon_garden.png");

        onDraw = CozyGameView.class.getDeclaredMethod("onDraw", Canvas.class);
        onDraw.setAccessible(true);

        System.out.println("CozyGrams v1.2.0 baseline preview  " + width + "x" + height);
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
    //
    // Numbered to line up with tools/preview/out. The gaps are shots the new harness has
    // and v1.2.0 has no equivalent for: 02 (a focusable size stepper that the old menu
    // does not have as a separate row is present, but the new 02 also shows a second
    // joined player, which v1.2.0 has no notion of), 09 (ripples and particle bursts),
    // 10 (an early win frame is included in the settled one's timeline), 12-15 (cursor
    // overlap hearts, larger text, extra contrast, long toasts). Rendering an invented
    // stand-in for those would be fiction, so they are simply absent.

    private static Object[][] shots() {
        return new Object[][]{
                {"01-home.png", (Shot) LegacyPreview::home},
                {"03-settings.png", (Shot) LegacyPreview::settings},
                {"04-game-5x5-fresh.png", (Shot) LegacyPreview::gameFresh},
                {"05-game-10x10-mid.png", (Shot) LegacyPreview::gameTen},
                {"06-game-15x15-mid.png", (Shot) LegacyPreview::gameFifteen},
                {"07-game-20x20-mid.png", (Shot) LegacyPreview::gameTwenty},
                {"08-game-story.png", (Shot) LegacyPreview::gameStory},
                {"11-win-settled.png", (Shot) LegacyPreview::winSettled},
        };
    }

    /** 01 — the title screen with the first row (CONTINUE) focused. */
    private static long home(CozyGameView view) {
        set(view, "screen", SCREEN_HOME);
        set(view, "menu", 0);
        return T0;
    }

    /**
     * 03 — the cozy corner. Music, sound effects and hints are on, which is the shipped
     * default and matches the new harness's settings shot. "Gentle mistake check" has no
     * counterpart there and is left at its shipped default of off.
     */
    private static long settings(CozyGameView view) {
        set(view, "screen", SCREEN_SETTINGS);
        set(view, "menu", 0);
        return T0;
    }

    /**
     * 04 — an untouched 5x5 endless board. The toast is the one the field initialiser
     * puts there, with {@code toastAt == 0}, so this is literally the first thing v1.2.0
     * showed you.
     */
    private static long gameFresh(CozyGameView view) {
        set(view, "screen", SCREEN_GAME);
        GameState game = game(view);
        game.cursorX[0] = 2;
        game.cursorY[0] = 1;
        return T0;
    }

    /** 05 — a 10x10 board about halfway home. */
    private static long gameTen(CozyGameView view) {
        set(view, "screen", SCREEN_GAME);
        GameState game = game(view);
        fillPicture(game, .55f);
        scatterCrosses(game, 3);
        placeCursors(game, 3, 4, 7, 2);
        showToast(view, "Lovely — that line is complete  ✦", T0 - 1400);
        return T0;
    }

    /** 06 — the same idea at 15x15. */
    private static long gameFifteen(CozyGameView view) {
        set(view, "screen", SCREEN_GAME);
        GameState game = game(view);
        fillPicture(game, .55f);
        scatterCrosses(game, 4);
        placeCursors(game, 5, 7, 11, 3);
        showToast(view, "A little starlight showed the way  ✦", T0 - 2000);
        return T0;
    }

    /** 07 — 20x20: the smallest cells and the busiest clue gutters v1.2.0 can produce. */
    private static long gameTwenty(CozyGameView view) {
        set(view, "screen", SCREEN_GAME);
        GameState game = game(view);
        fillPicture(game, .55f);
        scatterCrosses(game, 5);
        placeCursors(game, 6, 11, 14, 5);
        showToast(view, "Lovely — that line is complete  ✦", T0 - 2600);
        return T0;
    }

    /** 08 — an authored story-book chapter part way through. */
    private static long gameStory(CozyGameView view) {
        set(view, "screen", SCREEN_GAME);
        GameState game = game(view);
        fillPicture(game, .5f);
        scatterCrosses(game, 4);
        placeCursors(game, 4, 5, 6, 2);
        showToast(view, "A new page of your story…", T0 - 1100);
        return T0;
    }

    /**
     * 11 — the win card 2200 ms after the puzzle was finished: fully revealed, with the
     * "press any button" prompt showing.
     *
     * <p>The toast is pushed past its 4 s life so the ribbon has gone, matching the new
     * harness's cleared toast. v1.2.0 has no way to show an empty ribbon tidily — a blank
     * toast draws a stub of a pill — so an expired one is both the honest and the
     * charitable reading.
     */
    private static long winSettled(CozyGameView view) {
        set(view, "screen", SCREEN_GAME);
        GameState game = game(view);
        fillPicture(game, 1f);
        crossEveryEmptySquare(game);
        placeCursors(game, 9, 9, 0, 9);
        set(view, "won", true);
        set(view, "winAt", T0);
        showToast(view, "Lovely — that line is complete  ✦", T0 - 5000);
        return T0 + 2200;
    }

    // ---- State helpers ---------------------------------------------------------------

    /**
     * The saved game each shot starts from, written into preferences before the view is
     * constructed. Keys and defaults are v1.2.0's own; see {@code CozyGameView.save()}.
     */
    private static void savedGame(SharedPreferences prefs, String name) {
        SharedPreferences.Editor save = prefs.edit()
                .putBoolean("music", true)
                .putBoolean("sfx", true)
                .putBoolean("gentle", false)
                .putBoolean("hints", true)
                .putBoolean("storyMode", false)
                .putInt("storyIndex", 0)
                .putInt("solved", 0)
                .putInt("moves0", 0)
                .putInt("moves1", 0);
        switch (name) {
            case "01-home.png":
            case "03-settings.png":
                save.putLong("seed", SEED_OWL).putInt("size", 10);
                break;
            case "04-game-5x5-fresh.png":
                save.putLong("seed", SEED_SWEETHEART).putInt("size", 5);
                break;
            case "05-game-10x10-mid.png":
                save.putLong("seed", SEED_OWL).putInt("size", 10)
                        .putInt("solved", 2).putInt("moves0", 41).putInt("moves1", 33);
                break;
            case "06-game-15x15-mid.png":
                save.putLong("seed", SEED_RAIN).putInt("size", 15)
                        .putInt("moves0", 96).putInt("moves1", 81);
                break;
            case "07-game-20x20-mid.png":
                save.putLong("seed", SEED_PIE).putInt("size", 20)
                        .putInt("solved", 6).putInt("moves0", 174).putInt("moves1", 158);
                break;
            case "08-game-story.png":
                // The new harness builds a 5x5 endless game and then calls startStory(9);
                // v1.2.0 does exactly that from preferences, in restoreGame().
                save.putLong("seed", SEED_SWEETHEART).putInt("size", 5)
                        .putBoolean("storyMode", true).putInt("storyIndex", STORY_CHAPTER)
                        .putInt("solved", 1).putInt("moves0", 34).putInt("moves1", 27);
                break;
            case "11-win-settled.png":
                save.putLong("seed", SEED_SWEETHEART).putInt("size", 10)
                        .putInt("solved", 3).putInt("moves0", 63).putInt("moves1", 58);
                break;
            default:
                throw new IllegalArgumentException("no saved game for " + name);
        }
        save.apply();
    }

    private static void placeCursors(GameState game, int x0, int y0, int x1, int y1) {
        game.cursorX[0] = x0;
        game.cursorY[0] = y0;
        game.cursorX[1] = x1;
        game.cursorY[1] = y1;
    }

    /** Fills the first {@code fraction} of the picture's squares, in row-major order. */
    private static void fillPicture(GameState game, float fraction) {
        int picture = 0;
        for (int y = 0; y < game.size; y++) {
            for (int x = 0; x < game.size; x++) {
                if (game.puzzle.solution[y][x]) {
                    picture++;
                }
            }
        }
        int want = Math.round(picture * fraction);
        int done = 0;
        for (int y = 0; y < game.size && done < want; y++) {
            for (int x = 0; x < game.size && done < want; x++) {
                if (game.puzzle.solution[y][x]) {
                    game.puzzle.marks[y][x] = 1;
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
                if (game.puzzle.solution[y][x] || game.puzzle.marks[y][x] != 0) {
                    continue;
                }
                if (seen++ % stride == 0) {
                    game.puzzle.marks[y][x] = 2;
                }
            }
        }
    }

    /** The state a solved board ends in: every non-picture square ruled out. */
    private static void crossEveryEmptySquare(GameState game) {
        for (int y = 0; y < game.size; y++) {
            for (int x = 0; x < game.size; x++) {
                if (!game.puzzle.solution[y][x]) {
                    game.puzzle.marks[y][x] = 2;
                }
            }
        }
    }

    private static void showToast(CozyGameView view, String message, long at) {
        set(view, "toast", message);
        set(view, "toastAt", at);
    }

    // ---- Reflection ------------------------------------------------------------------

    private static Field field(String name) {
        return FIELDS.computeIfAbsent(name, key -> {
            try {
                Field found = CozyGameView.class.getDeclaredField(key);
                found.setAccessible(true);
                return found;
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(
                        "v1.2.0 CozyGameView has no field '" + key + "'", e);
            }
        });
    }

    private static void set(CozyGameView view, String name, Object value) {
        try {
            field(name).set(view, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot set " + name, e);
        }
    }

    private static GameState game(CozyGameView view) {
        try {
            return (GameState) field("game").get(view);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read the game state", e);
        }
    }

    /**
     * The alpha the view's shared {@code Paint} is left holding after a frame. v1.2.0
     * never resets it, so on a real device the <em>next</em> frame draws the backdrop
     * bitmap at whatever alpha the last {@code setColor} left behind. Reported so the
     * baseline's one deliberate simplification — always rendering a first frame — is
     * visible rather than hidden.
     */
    private static int trailingAlpha(CozyGameView view) {
        try {
            Field paint = CozyGameView.class.getDeclaredField("paint");
            paint.setAccessible(true);
            return android.graphics.Color.alpha(((Paint) paint.get(view)).getColor());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read the shared paint", e);
        }
    }

    // ---- Plumbing --------------------------------------------------------------------

    private static String render(String name, Shot shot) throws Exception {
        Context context = new Context();
        context.getResources().registerDrawable(R.drawable.cozy_room,
                new File(assets, "cozy_room.png").getPath());
        context.getResources().registerDrawable(R.drawable.moon_garden,
                new File(assets, "moon_garden.png").getPath());
        savedGame(context.getSharedPreferences("save", 0), name);

        // The constructor reads preferences and decodes backdrops, so the clock has to be
        // sane before it runs even though nothing in it draws.
        SystemClock.setUptimeMillis(T0);
        CozyGameView view = new CozyGameView(context);
        view.setSize(width, height);

        long when = shot.pose(view);
        SystemClock.setUptimeMillis(when);

        Bitmap target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        onDraw.invoke(view, canvas);
        canvas.release();

        File file = new File(outDir, name);
        BufferedImage image = target.image();
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("no PNG writer available for " + file);
        }
        int alpha = trailingAlpha(view);
        System.out.printf("  %-30s %dx%d  %d bytes%s%n", name, image.getWidth(),
                image.getHeight(), file.length(),
                alpha == 255 ? "" : "   [next frame would tint the backdrop: shared paint "
                        + "left at alpha " + alpha + "]");
        return name;
    }

    private static void requireAsset(String name) throws IOException {
        File file = new File(assets, name);
        if (!file.isFile()) {
            throw new IOException("missing backdrop " + file
                    + " (render-legacy.sh converts the .webp sources into this directory)");
        }
    }

    /**
     * Where the decoded backdrops live. {@code render-legacy.sh} sets {@code cozy.assets};
     * the fallback walks up from the working directory so the class can also be run by
     * hand.
     */
    private static File assetDir() throws IOException {
        String property = System.getProperty("cozy.assets");
        if (property != null && !property.isEmpty()) {
            return new File(property).getAbsoluteFile();
        }
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (dir != null) {
            File candidate = new File(dir, "tools/legacy-preview/build/assets");
            if (candidate.isDirectory()) {
                return candidate;
            }
            dir = dir.getParentFile();
        }
        throw new IOException("cannot locate tools/legacy-preview/build/assets; "
                + "pass -Dcozy.assets=<dir> or run tools/legacy-preview/render-legacy.sh");
    }
}
