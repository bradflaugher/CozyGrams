package android.content;

import android.content.res.Resources;

import java.util.HashMap;
import java.util.Map;

/**
 * Desktop stand-in for {@code android.content.Context}.
 *
 * <p>Copied from {@code tools/preview/stubs} and extended with {@link #getResources()},
 * because unlike the new renderer the v1.2.0 view is a real {@code View}: it is handed a
 * Context, reads its saved game out of {@code SharedPreferences} in the constructor, and
 * decodes its backdrops through {@code Resources}. The preference store is a plain map,
 * which is exactly what the driver wants — it poses a saved game by writing the same keys
 * {@code CozyGameView.save()} writes, so the view restores itself the way it would on a
 * real cold start.
 */
public class Context {

    public static final int MODE_PRIVATE = 0;
    public static final int MODE_APPEND = 0x8000;

    private final Map<String, SharedPreferences> stores = new HashMap<>();
    private final Resources resources = new Resources();

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return stores.computeIfAbsent(name, key -> new SharedPreferences.InMemory());
    }

    public Resources getResources() {
        return resources;
    }

    public Context getApplicationContext() {
        return this;
    }

    public String getPackageName() {
        return "com.cozygrams.tv";
    }
}
