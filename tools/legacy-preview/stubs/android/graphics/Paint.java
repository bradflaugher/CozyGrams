package android.graphics;

import java.awt.BasicStroke;

/**
 * Desktop stand-in for {@code android.graphics.Paint}.
 *
 * <p>Holds exactly the state the CozyGrams renderer touches; {@link Canvas} reads it back
 * out and translates it into AWT colours, strokes and fonts.
 */
public class Paint {

    public static final int ANTI_ALIAS_FLAG = 0x01;
    public static final int FILTER_BITMAP_FLAG = 0x02;
    public static final int DITHER_FLAG = 0x04;

    public enum Style {
        FILL, STROKE, FILL_AND_STROKE
    }

    public enum Cap {
        BUTT, ROUND, SQUARE
    }

    public enum Join {
        MITER, ROUND, BEVEL
    }

    public enum Align {
        LEFT, CENTER, RIGHT
    }

    private int color = Color.BLACK;
    private Style style = Style.FILL;
    private Cap cap = Cap.BUTT;
    private Join join = Join.MITER;
    private float strokeWidth;
    private float strokeMiter = 4f;
    private float textSize = 12f;
    private Align align = Align.LEFT;
    private Typeface typeface = Typeface.DEFAULT;
    private boolean antiAlias;
    private boolean filterBitmap;
    private boolean dither;

    public Paint() {
    }

    public Paint(int flags) {
        antiAlias = (flags & ANTI_ALIAS_FLAG) != 0;
        filterBitmap = (flags & FILTER_BITMAP_FLAG) != 0;
        dither = (flags & DITHER_FLAG) != 0;
    }

    public Paint(Paint other) {
        set(other);
    }

    public void set(Paint other) {
        color = other.color;
        style = other.style;
        cap = other.cap;
        join = other.join;
        strokeWidth = other.strokeWidth;
        strokeMiter = other.strokeMiter;
        textSize = other.textSize;
        align = other.align;
        typeface = other.typeface;
        antiAlias = other.antiAlias;
        filterBitmap = other.filterBitmap;
        dither = other.dither;
    }

    public void reset() {
        set(new Paint());
    }

    // ---- Colour --------------------------------------------------------------------

    public void setColor(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    public void setAlpha(int alpha) {
        color = Color.argb(alpha & 0xFF, Color.red(color), Color.green(color),
                Color.blue(color));
    }

    public int getAlpha() {
        return Color.alpha(color);
    }

    // ---- Geometry ------------------------------------------------------------------

    public void setStyle(Style style) {
        this.style = style;
    }

    public Style getStyle() {
        return style;
    }

    public void setStrokeWidth(float width) {
        this.strokeWidth = width;
    }

    public float getStrokeWidth() {
        return strokeWidth;
    }

    public void setStrokeCap(Cap cap) {
        this.cap = cap;
    }

    public Cap getStrokeCap() {
        return cap;
    }

    public void setStrokeJoin(Join join) {
        this.join = join;
    }

    public Join getStrokeJoin() {
        return join;
    }

    public void setStrokeMiter(float miter) {
        this.strokeMiter = miter;
    }

    // ---- Flags ---------------------------------------------------------------------

    public void setAntiAlias(boolean on) {
        antiAlias = on;
    }

    public boolean isAntiAlias() {
        return antiAlias;
    }

    public void setFilterBitmap(boolean on) {
        filterBitmap = on;
    }

    public boolean isFilterBitmap() {
        return filterBitmap;
    }

    public void setDither(boolean on) {
        dither = on;
    }

    public boolean isDither() {
        return dither;
    }

    // ---- Text ----------------------------------------------------------------------

    public void setTextSize(float size) {
        this.textSize = size;
    }

    public float getTextSize() {
        return textSize;
    }

    public void setTextAlign(Align align) {
        this.align = align;
    }

    public Align getTextAlign() {
        return align;
    }

    public Typeface setTypeface(Typeface typeface) {
        this.typeface = typeface == null ? Typeface.DEFAULT : typeface;
        return this.typeface;
    }

    public Typeface getTypeface() {
        return typeface;
    }

    /** Advance width of {@code text} at the current size and typeface. */
    public float measureText(String text) {
        return TextEngine.measure(text, typeface, textSize);
    }

    public float measureText(String text, int start, int end) {
        return measureText(text == null ? null : text.substring(start, end));
    }

    /** Harness extra: which host fonts the stub resolved, for the render banner. */
    public static String describeFonts() {
        return TextEngine.describe();
    }

    // ---- Bridge to AWT -------------------------------------------------------------

    java.awt.Color awtColor() {
        return Color.awt(color);
    }

    BasicStroke awtStroke() {
        int awtCap;
        switch (cap) {
            case ROUND:
                awtCap = BasicStroke.CAP_ROUND;
                break;
            case SQUARE:
                awtCap = BasicStroke.CAP_SQUARE;
                break;
            default:
                awtCap = BasicStroke.CAP_BUTT;
                break;
        }
        int awtJoin;
        switch (join) {
            case ROUND:
                awtJoin = BasicStroke.JOIN_ROUND;
                break;
            case BEVEL:
                awtJoin = BasicStroke.JOIN_BEVEL;
                break;
            default:
                awtJoin = BasicStroke.JOIN_MITER;
                break;
        }
        // Android treats width 0 as a one-pixel hairline rather than "invisible".
        float width = strokeWidth > 0 ? strokeWidth : 1f;
        return new BasicStroke(width, awtCap, awtJoin, Math.max(1f, strokeMiter));
    }
}
