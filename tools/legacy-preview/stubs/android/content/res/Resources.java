package android.content.res;

import java.util.HashMap;
import java.util.Map;

/**
 * Desktop stand-in for {@code android.content.res.Resources}.
 *
 * <p>The old view only ever uses this to hand a resource id to
 * {@code BitmapFactory.decodeResource}, so all it has to do is remember which file each
 * {@code R.drawable} id points at. {@code render-legacy.sh} decodes the shipped WebP
 * backdrops into PNGs under {@code build/assets} and the driver registers them here.
 */
public class Resources {

    private final Map<Integer, String> drawables = new HashMap<>();

    /** Harness hook: bind an {@code R.drawable} id to a decodable file on disk. */
    public void registerDrawable(int id, String path) {
        drawables.put(id, path);
    }

    /** Harness hook: the file bound to {@code id}, or {@code null}. */
    public String drawablePath(int id) {
        return drawables.get(id);
    }

    public float getDisplayMetricsDensity() {
        return 1f;
    }
}
