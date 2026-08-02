package android.content;

import java.util.HashMap;
import java.util.Map;

/**
 * Desktop stand-in for {@code android.content.Context}.
 *
 * <p>Only the preferences entry point is implemented; the harness never builds a Context,
 * so this exists purely so app classes that take one still compile.
 */
public class Context {

    public static final int MODE_PRIVATE = 0;
    public static final int MODE_APPEND = 0x8000;

    private final Map<String, SharedPreferences> stores = new HashMap<>();

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return stores.computeIfAbsent(name, key -> new SharedPreferences.InMemory());
    }

    public Context getApplicationContext() {
        return this;
    }

    public String getPackageName() {
        return "com.cozygrams.tv";
    }
}
