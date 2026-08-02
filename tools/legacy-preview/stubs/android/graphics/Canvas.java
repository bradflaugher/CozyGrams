package android.graphics;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Desktop stand-in for {@code android.graphics.Canvas}, backed by {@link Graphics2D}.
 *
 * <p>Semantics worth calling out, because getting them wrong silently produces a
 * plausible-but-wrong screenshot:
 *
 * <ul>
 *   <li>{@code drawText} puts the <em>baseline</em> at {@code y}, and honours
 *       {@code Paint.Align}. AWT's {@code drawString} already uses a baseline origin, so
 *       only the horizontal anchor needs translating.</li>
 *   <li>{@code drawRoundRect} takes corner <em>radii</em>; AWT's
 *       {@code RoundRectangle2D} takes arc <em>diameters</em>, hence the doubling. Radii
 *       are clamped to half the box, exactly as Skia does.</li>
 *   <li>Antialiasing follows the paint's {@code ANTI_ALIAS_FLAG} rather than being forced
 *       on, and {@code STROKE_PURE} is used so strokes are not snapped to the pixel grid
 *       (Skia never snaps them).</li>
 * </ul>
 */
public class Canvas {

    private final BufferedImage target;
    private final Graphics2D g;
    private final Deque<AffineTransform> stack = new ArrayDeque<>();

    private final Rectangle2D.Float rectShape = new Rectangle2D.Float();
    private final RoundRectangle2D.Float roundShape = new RoundRectangle2D.Float();
    private final Ellipse2D.Float ovalShape = new Ellipse2D.Float();
    private final Line2D.Float lineShape = new Line2D.Float();

    public Canvas(Bitmap bitmap) {
        this.target = bitmap.image();
        this.g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    public int getWidth() {
        return target.getWidth();
    }

    public int getHeight() {
        return target.getHeight();
    }

    /** Harness helper: releases the AWT graphics context once a frame is finished. */
    public void release() {
        g.dispose();
    }

    // ---- Transform stack -----------------------------------------------------------

    public int save() {
        stack.push(g.getTransform());
        return stack.size();
    }

    public void restore() {
        if (!stack.isEmpty()) {
            g.setTransform(stack.pop());
        }
    }

    public void translate(float dx, float dy) {
        g.translate(dx, dy);
    }

    public void scale(float sx, float sy) {
        g.scale(sx, sy);
    }

    public void rotate(float degrees) {
        g.rotate(Math.toRadians(degrees));
    }

    public void rotate(float degrees, float px, float py) {
        g.rotate(Math.toRadians(degrees), px, py);
    }

    // ---- Shapes --------------------------------------------------------------------

    public void drawColor(int color) {
        java.awt.Composite previous = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(Color.awt(color));
        g.fillRect(0, 0, target.getWidth(), target.getHeight());
        g.setComposite(previous);
    }

    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        rectShape.setRect(left, top, right - left, bottom - top);
        paint(rectShape, paint);
    }

    public void drawRect(RectF rect, Paint paint) {
        drawRect(rect.left, rect.top, rect.right, rect.bottom, paint);
    }

    public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
        drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, paint);
    }

    public void drawRoundRect(float left, float top, float right, float bottom, float rx,
                              float ry, Paint paint) {
        float width = right - left;
        float height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }
        float clampedX = Math.min(Math.max(0, rx), width * .5f);
        float clampedY = Math.min(Math.max(0, ry), height * .5f);
        // RoundRectangle2D wants arc diameters, Android supplies radii.
        roundShape.setRoundRect(left, top, width, height, clampedX * 2, clampedY * 2);
        paint(roundShape, paint);
    }

    public void drawCircle(float cx, float cy, float radius, Paint paint) {
        if (radius <= 0) {
            return;
        }
        ovalShape.setFrame(cx - radius, cy - radius, radius * 2, radius * 2);
        paint(ovalShape, paint);
    }

    public void drawOval(RectF rect, Paint paint) {
        ovalShape.setFrame(rect.left, rect.top, rect.width(), rect.height());
        paint(ovalShape, paint);
    }

    public void drawLine(float startX, float startY, float stopX, float stopY,
                         Paint paint) {
        lineShape.setLine(startX, startY, stopX, stopY);
        applyAntiAlias(paint);
        g.setColor(paint.awtColor());
        g.setStroke(paint.awtStroke());
        g.draw(lineShape);
    }

    public void drawPath(Path path, Paint paint) {
        paint(path.shape(), paint);
    }

    // ---- Text ----------------------------------------------------------------------

    /** Draws {@code text} with its baseline at {@code y}, anchored per the paint align. */
    public void drawText(String text, float x, float y, Paint paint) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float start = x;
        if (paint.getTextAlign() != Paint.Align.LEFT) {
            float width = paint.measureText(text);
            start = paint.getTextAlign() == Paint.Align.CENTER ? x - width * .5f
                    : x - width;
        }
        applyAntiAlias(paint);
        g.setColor(paint.awtColor());
        TextEngine.draw(g, text, start, y, paint.getTypeface(), paint.getTextSize());
    }

    public void drawText(char[] text, int index, int count, float x, float y,
                         Paint paint) {
        drawText(new String(text, index, count), x, y, paint);
    }

    // ---- Bitmaps -------------------------------------------------------------------

    /**
     * Scales {@code bitmap} (or {@code src} within it) into {@code dst}. The paint's
     * alpha is applied, as on Android, and {@code FILTER_BITMAP_FLAG} selects bilinear
     * versus nearest-neighbour sampling.
     */
    public void drawBitmap(Bitmap bitmap, Rect src, RectF dst, Paint paint) {
        if (bitmap == null || dst == null || dst.width() <= 0 || dst.height() <= 0) {
            return;
        }
        BufferedImage image = bitmap.image();
        BufferedImage source = image;
        if (src != null && !src.isEmpty()) {
            source = image.getSubimage(Math.max(0, src.left), Math.max(0, src.top),
                    Math.min(src.width(), image.getWidth() - src.left),
                    Math.min(src.height(), image.getHeight() - src.top));
        }

        boolean smooth = paint == null || paint.isFilterBitmap();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smooth
                ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int alpha = paint == null ? 255 : Color.alpha(paint.getColor());
        java.awt.Composite previous = g.getComposite();
        if (alpha < 255) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    alpha / 255f));
        }

        AffineTransform transform = AffineTransform.getTranslateInstance(dst.left, dst.top);
        transform.scale(dst.width() / source.getWidth(), dst.height() / source.getHeight());
        g.drawImage(source, transform, null);

        g.setComposite(previous);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    public void drawBitmap(Bitmap bitmap, float left, float top, Paint paint) {
        if (bitmap == null) {
            return;
        }
        drawBitmap(bitmap, null,
                new RectF(left, top, left + bitmap.getWidth(), top + bitmap.getHeight()),
                paint);
    }

    // ---- Internals -----------------------------------------------------------------

    private void paint(Shape shape, Paint paint) {
        applyAntiAlias(paint);
        g.setColor(paint.awtColor());
        Paint.Style style = paint.getStyle();
        if (style != Paint.Style.STROKE) {
            g.fill(shape);
        }
        if (style != Paint.Style.FILL) {
            g.setStroke(paint.awtStroke());
            g.draw(shape);
        }
    }

    private void applyAntiAlias(Paint paint) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                paint.isAntiAlias() ? RenderingHints.VALUE_ANTIALIAS_ON
                        : RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                paint.isAntiAlias() ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                        : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    }
}
