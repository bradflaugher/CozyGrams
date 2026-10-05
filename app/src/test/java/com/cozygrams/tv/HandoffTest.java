package com.cozygrams.tv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Android TV's placeholder activities never count as somewhere to send a link. */
public class HandoffTest {

    private static final String STUBS = "com.android.tv.frameworkpackagestubs";

    @Test
    public void theTelevisionsStubBrowserIsNotABrowser() {
        assertFalse(Handoff.opens(STUBS,
                "com.android.tv.frameworkpackagestubs.Stubs$BrowserStub"));
        assertFalse(Handoff.opens("com.google.android.tv.frameworkpackagestubs",
                "com.google.android.tv.frameworkpackagestubs.Stubs$BrowserStub"));
        // Any vendor's copy of the same package of placeholders.
        assertFalse(Handoff.opens("com.example.tv.frameworkpackagestubs", "Anything"));
        assertFalse(Handoff.opens("com.example.vendor", "com.example.vendor.Stubs$WebStub"));
        assertFalse(Handoff.opens(null, null));
    }

    @Test
    public void realBrowsersAndThePlayStoreStillOpen() {
        assertTrue(Handoff.opens("com.android.chrome", "com.google.android.apps.chrome.Main"));
        assertTrue(Handoff.opens("com.android.vending", "com.google.android.finsky"
                + ".inlinedetails.activities.tv.TvMarketDeepLinkHandlerActivity"));
        assertTrue(Handoff.opens("org.mozilla.firefox", "org.mozilla.fenix.IntentReceiverActivity"));
    }

    /**
     * The API 36 television's whole share list: Bluetooth's file sender and the stub
     * "email" app. Neither is somewhere to tell a friend, so the link is shown instead.
     */
    @Test
    public void bluetoothAndTheStubMailAppAreNotShareTargets() {
        assertFalse(Handoff.shares("com.google.android.bluetooth",
                "com.android.bluetooth.opp.BluetoothOppLauncherActivity"));
        assertFalse(Handoff.shares("com.android.bluetooth",
                "com.android.bluetooth.opp.BluetoothOppLauncherActivity"));
        assertFalse(Handoff.shares(STUBS,
                "com.android.tv.frameworkpackagestubs.Stubs$EmailStub"));
        assertTrue(Handoff.shares("com.google.android.apps.messaging",
                "com.google.android.apps.messaging.ui.conversationlist.ShareIntentActivity"));
        assertTrue(Handoff.shares("com.google.android.gm",
                "com.google.android.gm.ComposeActivityGmailExternal"));
    }
}
