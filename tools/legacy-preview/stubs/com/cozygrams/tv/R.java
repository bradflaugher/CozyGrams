package com.cozygrams.tv;

/**
 * Stand-in for the {@code R} class aapt generates at build time.
 *
 * <p>It lives with the platform stubs rather than with the vendored v1.2.0 sources
 * because it is a build artefact, not something anyone wrote. The old view only reaches
 * for two ids; the driver binds them to the decoded backdrops through
 * {@code Resources.registerDrawable}. The literal values are arbitrary — aapt's are too.
 */
public final class R {

    private R() {
    }

    public static final class drawable {

        public static final int cozy_room = 0x7f080001;
        public static final int moon_garden = 0x7f080002;

        private drawable() {
        }
    }
}
