package android.view;

/**
 * Desktop stand-in for {@code android.view.MotionEvent}: compile-time only, like
 * {@link KeyEvent}. Values are the real platform values.
 */
public class MotionEvent {

    public static final int ACTION_MOVE = 2;
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;

    public int getSource() {
        return 0;
    }

    public int getAction() {
        return 0;
    }

    public int getDeviceId() {
        return 0;
    }

    public float getAxisValue(int axis) {
        return 0f;
    }
}
