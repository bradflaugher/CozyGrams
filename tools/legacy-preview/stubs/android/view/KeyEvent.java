package android.view;

/**
 * Desktop stand-in for {@code android.view.KeyEvent}.
 *
 * <p>Nothing in the screenshot harness presses a key — the driver poses the view's state
 * directly — but {@code CozyGameView.onKeyDown} has to compile, so the key codes it
 * switches on have to exist. The values are the real platform values.
 */
public class KeyEvent {

    public static final int KEYCODE_BACK = 4;
    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;
    public static final int KEYCODE_DPAD_CENTER = 23;
    public static final int KEYCODE_ENTER = 66;
    public static final int KEYCODE_BUTTON_A = 96;
    public static final int KEYCODE_BUTTON_B = 97;
    public static final int KEYCODE_BUTTON_X = 99;
    public static final int KEYCODE_BUTTON_Y = 100;
    public static final int KEYCODE_BUTTON_START = 108;

    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;

    private final int deviceId;
    private final int repeatCount;

    public KeyEvent(int deviceId, int repeatCount) {
        this.deviceId = deviceId;
        this.repeatCount = repeatCount;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public int getRepeatCount() {
        return repeatCount;
    }
}
