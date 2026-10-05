package com.cozygrams.tv;

/**
 * Which of the apps that answer a hand-off intent can actually do something with it.
 *
 * <p>Share CozyGrams, Send feedback and Rate on Google Play pass an intent to another app,
 * and fall back to words on screen when there is nobody to pass it to. Asking Android
 * "does anything resolve this?" is not the same question. Android TV ships
 * {@code com.android.tv.frameworkpackagestubs}, whose {@code Stubs$BrowserStub} and
 * {@code Stubs$EmailStub} claim {@code https://} links and {@code ACTION_SEND} so that apps
 * written for phones do not crash; all they do is toast "You don't have an app that can do
 * this". With the stub answering, Send feedback resolved, launched, and showed that toast
 * instead of the address. The share sheet on the same television auto-launched the one
 * other candidate, Bluetooth's file sender, which opens a device picker and closes again,
 * so Share CozyGrams appeared to do nothing at all.
 *
 * <p>So the view lists every handler and asks this class about each one. The rules are plain
 * string tests, so they run as unit tests with no device.
 */
final class Handoff {

    private Handoff() {
    }

    /**
     * Packages that register placeholder activities rather than real ones. The AOSP name
     * and the Google TV one; both hold nothing but stubs.
     */
    static final String[] STUB_PACKAGES = {
            "com.android.tv.frameworkpackagestubs",
            "com.google.android.tv.frameworkpackagestubs",
    };

    /**
     * Bluetooth's object-push sender. It does accept {@code text/plain}, but it turns the
     * line into a file for a nearby device, which is not a way to tell a friend about a
     * game; on a television it is often the only "share target" there is. It is never
     * hidden from a share sheet that has real targets as well; it just does not count as
     * one on its own.
     */
    static final String[] BLUETOOTH_PACKAGES = {
            "com.android.bluetooth",
            "com.google.android.bluetooth",
    };

    /** True when this activity is a framework placeholder that can only refuse. */
    static boolean isStub(String packageName, String className) {
        if (packageName != null) {
            for (String stub : STUB_PACKAGES) {
                if (stub.equals(packageName)) {
                    return true;
                }
            }
            if (packageName.endsWith(".frameworkpackagestubs")) {
                return true;
            }
        }
        return className != null && className.contains(".Stubs$");
    }

    /** True for Bluetooth's sender, which does not count as somewhere to share a link. */
    static boolean isBluetooth(String packageName) {
        if (packageName == null) {
            return false;
        }
        for (String bluetooth : BLUETOOTH_PACKAGES) {
            if (bluetooth.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /** True when a handler would really open a link or a store page. */
    static boolean opens(String packageName, String className) {
        return packageName != null && !isStub(packageName, className);
    }

    /** True when a handler is a real place to share a line of text with a friend. */
    static boolean shares(String packageName, String className) {
        return opens(packageName, className) && !isBluetooth(packageName);
    }
}
