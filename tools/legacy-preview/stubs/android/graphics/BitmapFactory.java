package android.graphics;

import android.content.res.Resources;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * Desktop stand-in for {@code android.graphics.BitmapFactory}.
 *
 * <p>Like the platform version, every decode returns {@code null} rather than throwing
 * when the source cannot be read.
 *
 * <p>Copied from {@code tools/preview/stubs} and extended with
 * {@link #decodeResource(Resources, int)}: the v1.2.0 view loads its backdrops by
 * {@code R.drawable} id rather than by path. The bytes are the same either way —
 * {@code render-legacy.sh} decodes the shipped WebP into a PNG and registers it against
 * the id, so the pixels the old view draws are the shipped pixels.
 */
public final class BitmapFactory {

    /** Present so callers that pass options still compile; nothing here is honoured. */
    public static class Options {
        public boolean inScaled = true;
        public int inSampleSize = 1;
        public int outWidth;
        public int outHeight;
    }

    private BitmapFactory() {
    }

    public static Bitmap decodeFile(String path) {
        return decodeFile(path, null);
    }

    public static Bitmap decodeFile(String path, Options options) {
        if (path == null) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new File(path));
            record(options, image);
            return Bitmap.wrap(image);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Bitmap decodeResource(Resources resources, int id) {
        return decodeResource(resources, id, null);
    }

    public static Bitmap decodeResource(Resources resources, int id, Options options) {
        if (resources == null) {
            return null;
        }
        return decodeFile(resources.drawablePath(id), options);
    }

    public static Bitmap decodeStream(InputStream stream) {
        return decodeStream(stream, null, null);
    }

    public static Bitmap decodeStream(InputStream stream, Rect padding, Options options) {
        if (stream == null) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(stream);
            record(options, image);
            return Bitmap.wrap(image);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
        if (data == null) {
            return null;
        }
        try {
            return decodeStream(new java.io.ByteArrayInputStream(data, offset, length));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void record(Options options, BufferedImage image) {
        if (options != null && image != null) {
            options.outWidth = image.getWidth();
            options.outHeight = image.getHeight();
        }
    }
}
