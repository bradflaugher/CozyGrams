package android.graphics;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a {@link Typeface} plus a text size onto real host fonts, and lays text out the
 * way Android does.
 *
 * <p>Two things matter for a screenshot harness to be trustworthy:
 *
 * <ul>
 *   <li><b>Advance widths.</b> {@code Paint.measureText} and {@code Canvas.drawText}
 *       must agree exactly, or every centred and right-aligned string drifts. Both go
 *       through {@link #advance} here, using one shared {@link FontRenderContext} that
 *       matches the rendering hints {@link Canvas} installs.</li>
 *   <li><b>Glyph fallback.</b> Android falls back per glyph. The app draws {@code ♥},
 *       {@code ☰}, {@code ·}, {@code ×}, {@code …} and {@code ‹ ›}, and no single host
 *       font covers all of them, so text is split into runs and each run is drawn with
 *       the first font that can display it. That keeps alignment correct instead of
 *       dropping a whole string to a fallback face (or worse, drawing tofu).</li>
 * </ul>
 */
final class TextEngine {

    /**
     * Antialiased with fractional metrics — the same configuration {@link Canvas}
     * installs, so measurement and drawing use identical layout.
     */
    static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    /**
     * Preferred host faces, best first. Liberation Sans is metrically close to the
     * Roboto-ish grotesque Android would use and ships a real bold face.
     */
    private static final String[][] CANDIDATES = {
            {"/usr/share/fonts/liberation-sans-fonts/LiberationSans-Regular.ttf",
                    "/usr/share/fonts/liberation-sans-fonts/LiberationSans-Bold.ttf"},
            // Debian and Ubuntu, which is what CI runs the text-fit audit on: without this
            // the JDK's own SansSerif there is DejaVu Sans, a tenth wider, and the audit
            // would fail on CI for lines that fit everywhere else.
            {"/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
                    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"},
            {"/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
                    "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf"},
            // Arch.
            {"/usr/share/fonts/liberation/LiberationSans-Regular.ttf",
                    "/usr/share/fonts/liberation/LiberationSans-Bold.ttf"},
            {"/usr/share/fonts/adwaita-sans-fonts/AdwaitaSans-Regular.ttf", null},
            {"/usr/share/fonts/google-noto-vf/NotoSans[wght].ttf", null},
            {"/usr/share/fonts/dejavu-sans-fonts/DejaVuSans.ttf",
                    "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans-Bold.ttf"},
            {"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"},
    };

    private static final Font[] REGULAR_CHAIN;
    private static final Font[] BOLD_CHAIN;

    private static final Map<String, Font> SIZED = new HashMap<>();

    static {
        Font physicalRegular = null;
        Font physicalBold = null;
        for (String[] pair : CANDIDATES) {
            Font regular = load(pair[0]);
            if (regular == null) {
                continue;
            }
            physicalRegular = regular;
            Font bold = pair.length > 1 ? load(pair[1]) : null;
            // No dedicated bold file: let AWT embolden algorithmically.
            physicalBold = bold != null ? bold : regular.deriveFont(Font.BOLD);
            break;
        }

        if (physicalRegular == null) {
            // Not at any of the paths above — macOS, Windows, a font installed for one user:
            // ask the platform for the family by name.
            for (String family : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames()) {
                if (family.equals("Liberation Sans")) {
                    physicalRegular = new Font(family, Font.PLAIN, 1);
                    physicalBold = new Font(family, Font.BOLD, 1);
                    break;
                }
            }
        }

        // The logical family is a composite font, so it covers glyphs (☰) that no single
        // installed face carries. It is always the last resort in the chain.
        Font logicalRegular = new Font(Font.SANS_SERIF, Font.PLAIN, 1);
        Font logicalBold = new Font(Font.SANS_SERIF, Font.BOLD, 1);

        REGULAR_CHAIN = physicalRegular == null
                ? new Font[]{logicalRegular}
                : new Font[]{physicalRegular, logicalRegular};
        BOLD_CHAIN = physicalBold == null
                ? new Font[]{logicalBold}
                : new Font[]{physicalBold, logicalBold};
    }

    private TextEngine() {
    }

    private static Font load(String path) {
        if (path == null) {
            return null;
        }
        File file = new File(path);
        if (!file.isFile()) {
            return null;
        }
        try {
            return Font.createFont(Font.TRUETYPE_FONT, file).deriveFont(1f);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * True when text is measured with Liberation Sans, the face every committed render and
     * the text-fit audit's verdicts are calibrated against. Other faces measure differently
     * (DejaVu Sans is about a tenth wider), so the audit refuses to judge with them.
     */
    static boolean canonical() {
        return REGULAR_CHAIN[0].getFontName().startsWith("Liberation Sans");
    }

    /** Human-readable description of the resolved faces, for the harness banner. */
    static String describe() {
        return REGULAR_CHAIN[0].getFontName() + " / " + BOLD_CHAIN[0].getFontName()
                + " (fallback: " + REGULAR_CHAIN[REGULAR_CHAIN.length - 1].getFontName()
                + ")";
    }

    private static Font[] chain(Typeface typeface) {
        boolean bold = typeface != null && typeface.isBold();
        return bold ? BOLD_CHAIN : REGULAR_CHAIN;
    }

    private static Font sized(Font base, float size) {
        String key = base.getFontName() + '|' + base.getStyle() + '|' + size;
        Font cached = SIZED.get(key);
        if (cached == null) {
            cached = base.deriveFont(size);
            SIZED.put(key, cached);
        }
        return cached;
    }

    /** One stretch of text that a single font can render. */
    private static final class Run {
        final Font font;
        final String text;

        Run(Font font, String text) {
            this.font = font;
            this.text = text;
        }
    }

    private static List<Run> split(String text, Typeface typeface, float size) {
        Font[] chain = chain(typeface);
        List<Run> runs = new ArrayList<>(2);
        int start = 0;
        int current = -1;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int width = Character.charCount(codePoint);
            int pick = 0;
            for (int c = 0; c < chain.length; c++) {
                if (chain[c].canDisplay(codePoint)) {
                    pick = c;
                    break;
                }
            }
            if (current == -1) {
                current = pick;
            } else if (pick != current) {
                runs.add(new Run(sized(chain[current], size), text.substring(start, i)));
                start = i;
                current = pick;
            }
            i += width;
        }
        if (current == -1) {
            current = 0;
        }
        if (start < text.length()) {
            runs.add(new Run(sized(chain[current], size), text.substring(start)));
        }
        return runs;
    }

    private static float advance(Font font, String text) {
        return (float) font.getStringBounds(text, FRC).getWidth();
    }

    /** The advance width Android's {@code Paint.measureText} would report. */
    static float measure(String text, Typeface typeface, float size) {
        if (text == null || text.isEmpty() || size <= 0) {
            return 0f;
        }
        float total = 0;
        for (Run run : split(text, typeface, size)) {
            total += advance(run.font, run.text);
        }
        return total;
    }

    /**
     * The ink {@code text} really covers, as {left, top, right, bottom} relative to a
     * left-aligned origin on the baseline — the glyph outlines, not the advance box, so a
     * line with no descenders really does end at the baseline. Harness only: this is what
     * the text-fit audit measures against.
     */
    static float[] ink(String text, Typeface typeface, float size) {
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        if (text == null || text.isEmpty() || size <= 0) {
            return new float[]{0, 0, 0, 0};
        }
        float cursor = 0;
        for (Run run : split(text, typeface, size)) {
            java.awt.geom.Rectangle2D bounds =
                    run.font.createGlyphVector(FRC, run.text).getVisualBounds();
            if (!bounds.isEmpty()) {
                box[0] = Math.min(box[0], cursor + (float) bounds.getMinX());
                box[1] = Math.min(box[1], (float) bounds.getMinY());
                box[2] = Math.max(box[2], cursor + (float) bounds.getMaxX());
                box[3] = Math.max(box[3], (float) bounds.getMaxY());
            }
            cursor += advance(run.font, run.text);
        }
        if (box[0] > box[2]) {
            return new float[]{0, 0, 0, 0};
        }
        return box;
    }

    /**
     * Draws {@code text} with its baseline at {@code y} and its left edge at {@code x},
     * exactly like {@code Canvas.drawText} with {@code Align.LEFT}.
     */
    static void draw(Graphics2D g, String text, float x, float y, Typeface typeface,
                     float size) {
        if (text == null || text.isEmpty() || size <= 0) {
            return;
        }
        float cursor = x;
        for (Run run : split(text, typeface, size)) {
            g.setFont(run.font);
            g.drawString(run.text, cursor, y);
            cursor += advance(run.font, run.text);
        }
    }
}
