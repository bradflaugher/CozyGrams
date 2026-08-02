package android.graphics;

import java.awt.image.BufferedImage;

/** Desktop stand-in for {@code android.graphics.Bitmap}, backed by a BufferedImage. */
public final class Bitmap {

    public enum Config {
        ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE
    }

    private final BufferedImage image;

    private Bitmap(BufferedImage image) {
        this.image = image;
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        int type = config == Config.RGB_565
                ? BufferedImage.TYPE_USHORT_565_RGB : BufferedImage.TYPE_INT_ARGB;
        return new Bitmap(new BufferedImage(Math.max(1, width), Math.max(1, height), type));
    }

    /** Harness helper: adopt an image decoded by {@link BitmapFactory} or ImageIO. */
    public static Bitmap wrap(BufferedImage image) {
        return image == null ? null : new Bitmap(image);
    }

    public int getWidth() {
        return image.getWidth();
    }

    public int getHeight() {
        return image.getHeight();
    }

    public boolean isRecycled() {
        return false;
    }

    public void recycle() {
    }

    /** Harness helper: the backing image, so the driver can write a PNG. */
    public BufferedImage image() {
        return image;
    }
}
