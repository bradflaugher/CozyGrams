package android.graphics;

/** Desktop stand-in for {@code android.graphics.RectF}: four public floats. */
public class RectF {

    public float left;
    public float top;
    public float right;
    public float bottom;

    public RectF() {
    }

    public RectF(float left, float top, float right, float bottom) {
        set(left, top, right, bottom);
    }

    public void set(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public void set(RectF other) {
        set(other.left, other.top, other.right, other.bottom);
    }

    public void setEmpty() {
        left = top = right = bottom = 0;
    }

    public float width() {
        return right - left;
    }

    public float height() {
        return bottom - top;
    }

    public float centerX() {
        return (left + right) * .5f;
    }

    public float centerY() {
        return (top + bottom) * .5f;
    }

    public boolean isEmpty() {
        return left >= right || top >= bottom;
    }

    @Override
    public String toString() {
        return "RectF(" + left + ", " + top + ", " + right + ", " + bottom + ")";
    }
}
