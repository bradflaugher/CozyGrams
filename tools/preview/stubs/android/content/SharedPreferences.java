package android.content;

import java.util.HashMap;
import java.util.Map;

/**
 * Desktop stand-in for {@code android.content.SharedPreferences}, backed by a plain map.
 *
 * <p>The screenshot harness never persists anything — it exists only so app classes that
 * touch preferences still compile and, if they are ever exercised, behave like an empty
 * store rather than throwing.
 */
public interface SharedPreferences {

    interface Editor {
        Editor putInt(String key, int value);

        Editor putLong(String key, long value);

        Editor putFloat(String key, float value);

        Editor putBoolean(String key, boolean value);

        Editor putString(String key, String value);

        Editor remove(String key);

        Editor clear();

        void apply();

        boolean commit();
    }

    int getInt(String key, int fallback);

    long getLong(String key, long fallback);

    float getFloat(String key, float fallback);

    boolean getBoolean(String key, boolean fallback);

    String getString(String key, String fallback);

    boolean contains(String key);

    Map<String, ?> getAll();

    Editor edit();

    /** The in-memory implementation handed out by {@link Context}. */
    final class InMemory implements SharedPreferences {

        private final Map<String, Object> values = new HashMap<>();

        @Override
        public int getInt(String key, int fallback) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : fallback;
        }

        @Override
        public long getLong(String key, long fallback) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : fallback;
        }

        @Override
        public float getFloat(String key, float fallback) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : fallback;
        }

        @Override
        public boolean getBoolean(String key, boolean fallback) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : fallback;
        }

        @Override
        public String getString(String key, String fallback) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : fallback;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(values);
        }

        @Override
        public Editor edit() {
            return new MapEditor();
        }

        private final class MapEditor implements Editor {
            private final Map<String, Object> pending = new HashMap<>();
            private boolean cleared;

            @Override
            public Editor putInt(String key, int value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putString(String key, String value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                pending.put(key, null);
                return this;
            }

            @Override
            public Editor clear() {
                cleared = true;
                return this;
            }

            @Override
            public void apply() {
                commit();
            }

            @Override
            public boolean commit() {
                if (cleared) {
                    values.clear();
                }
                for (Map.Entry<String, Object> entry : pending.entrySet()) {
                    if (entry.getValue() == null) {
                        values.remove(entry.getKey());
                    } else {
                        values.put(entry.getKey(), entry.getValue());
                    }
                }
                pending.clear();
                cleared = false;
                return true;
            }
        }
    }
}
