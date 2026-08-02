package android.graphics;

/**
 * Desktop stand-in for {@code android.graphics.Typeface}.
 *
 * <p>A typeface is just a family key plus a style bitmask; {@link TextEngine} maps that
 * onto a real font file on the host machine.
 */
public class Typeface {

    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;

    public static final String FAMILY_SANS = "sans-serif";
    public static final String FAMILY_SERIF = "serif";
    public static final String FAMILY_MONO = "monospace";

    public static final Typeface DEFAULT = new Typeface(FAMILY_SANS, NORMAL);
    public static final Typeface DEFAULT_BOLD = new Typeface(FAMILY_SANS, BOLD);
    public static final Typeface SANS_SERIF = new Typeface(FAMILY_SANS, NORMAL);
    public static final Typeface SERIF = new Typeface(FAMILY_SERIF, NORMAL);
    public static final Typeface MONOSPACE = new Typeface(FAMILY_MONO, NORMAL);

    private final String family;
    private final int style;

    private Typeface(String family, int style) {
        this.family = family;
        this.style = style;
    }

    public static Typeface create(Typeface family, int style) {
        String name = family == null ? FAMILY_SANS : family.family;
        return new Typeface(name, style & BOLD_ITALIC);
    }

    public static Typeface create(String familyName, int style) {
        String name = familyName == null ? FAMILY_SANS : familyName;
        return new Typeface(name, style & BOLD_ITALIC);
    }

    public static Typeface defaultFromStyle(int style) {
        return new Typeface(FAMILY_SANS, style & BOLD_ITALIC);
    }

    public int getStyle() {
        return style;
    }

    public boolean isBold() {
        return (style & BOLD) != 0;
    }

    public boolean isItalic() {
        return (style & ITALIC) != 0;
    }

    /** Package-private: which host font family {@link TextEngine} should resolve. */
    String family() {
        return family;
    }

    @Override
    public String toString() {
        return "Typeface(" + family + ", " + style + ")";
    }
}
