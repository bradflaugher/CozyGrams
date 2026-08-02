package android.graphics;

import java.awt.geom.Path2D;

/** Desktop stand-in for {@code android.graphics.Path}, backed by {@link Path2D.Float}. */
public class Path {

    private final Path2D.Float path = new Path2D.Float(Path2D.WIND_NON_ZERO);
    private boolean empty = true;
    private float lastX;
    private float lastY;

    public void reset() {
        path.reset();
        empty = true;
        lastX = 0;
        lastY = 0;
    }

    public void rewind() {
        reset();
    }

    public void moveTo(float x, float y) {
        path.moveTo(x, y);
        empty = false;
        lastX = x;
        lastY = y;
    }

    public void lineTo(float x, float y) {
        if (empty) {
            // Android implicitly starts a contour at (0,0) if lineTo comes first.
            moveTo(0, 0);
        }
        path.lineTo(x, y);
        lastX = x;
        lastY = y;
    }

    public void quadTo(float cx, float cy, float x, float y) {
        if (empty) {
            moveTo(0, 0);
        }
        path.quadTo(cx, cy, x, y);
        lastX = x;
        lastY = y;
    }

    public void cubicTo(float c1x, float c1y, float c2x, float c2y, float x, float y) {
        if (empty) {
            moveTo(0, 0);
        }
        path.curveTo(c1x, c1y, c2x, c2y, x, y);
        lastX = x;
        lastY = y;
    }

    public void close() {
        if (!empty) {
            path.closePath();
        }
    }

    public boolean isEmpty() {
        return empty;
    }

    /** Package-private view for {@link Canvas}. */
    Path2D.Float shape() {
        return path;
    }
}
