package android.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;

/**
 * Desktop stand-in for {@code android.view.View}: just enough of the base class for
 * v1.2.0's {@code CozyGameView} to extend it, construct, and be asked to draw a frame.
 *
 * <p>Nothing here draws anything. The size is set by the harness instead of coming from
 * a measure/layout pass, the focus calls are no-ops, and the invalidation hooks do
 * nothing because the driver renders exactly one frame per screenshot. The subclass's
 * {@code onDraw} is reached through reflection, so the original source is compiled
 * completely unmodified.
 */
public class View {

    // Only the flags MainActivity referenced; kept so the constants resolve if the
    // vendored sources ever grow back to include it.
    public static final int SYSTEM_UI_FLAG_FULLSCREEN = 0x00000004;
    public static final int SYSTEM_UI_FLAG_HIDE_NAVIGATION = 0x00000002;
    public static final int SYSTEM_UI_FLAG_IMMERSIVE_STICKY = 0x00001000;

    private final Context context;
    private int width;
    private int height;

    public View(Context context) {
        this.context = context;
    }

    public final Context getContext() {
        return context;
    }

    public Resources getResources() {
        return context.getResources();
    }

    /** Harness hook: stand in for the layout pass. */
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public final int getWidth() {
        return width;
    }

    public final int getHeight() {
        return height;
    }

    public void setFocusable(boolean focusable) {
    }

    public void setFocusableInTouchMode(boolean focusable) {
    }

    public final boolean requestFocus() {
        return true;
    }

    public boolean isFocused() {
        return true;
    }

    public void invalidate() {
    }

    public void postInvalidateOnAnimation() {
    }

    public void setSystemUiVisibility(int visibility) {
    }

    protected void onDraw(Canvas canvas) {
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        return false;
    }

    protected void onDetachedFromWindow() {
    }
}
