package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * The side panel's two contracts with the rest of the game.
 *
 * <p><b>The legend tells the truth about the controller in the room.</b> A bare TV remote
 * has a D-pad, a centre key and Back — it can never reach {@code PlayerRegistry.isCross},
 * so its centre key cycles empty → filled → crossed and hints move to a held press. A
 * legend still reading "A / B / Y" would be three wrong answers in a row.
 *
 * <p><b>Nothing is drawn on a colour it cannot be read on.</b> The chips carry a glyph on
 * a coloured fill, and every colour they can take — including a resting one, and including
 * Sky's deep teal under {@link Comfort#distinctPlayers} — has to leave that glyph legible.
 */
public class HudSceneTest {

    /** WCAG AA for normal text; a one-character glyph on a chip is held to the same bar. */
    private static final double AA = 4.5;

    @After
    public void restoreDefaults() {
        HudScene.setRemoteOnly(false);
        Comfort.get().restoreDefaults();
    }

    // ---- The legend ------------------------------------------------------------------

    @Test
    public void aGamepadIsTheDefaultUntilSomethingSaysOtherwise() {
        assertFalse("the remote legend must never be the assumption",
                HudScene.remoteOnly());
        assertEquals("A", HudScene.legendButtons()[0]);
    }

    @Test
    public void everyLegendRowHasAButtonAndAnExplanation() {
        UiState ui = new UiState();
        for (boolean remote : new boolean[]{false, true}) {
            HudScene.setRemoteOnly(remote);
            String[] buttons = HudScene.legendButtons();
            String[] labels = HudScene.legendLabels(ui);
            assertEquals("remoteOnly=" + remote + ": the legend arrays disagree",
                    buttons.length, labels.length);
            assertTrue(buttons.length > 0);
            for (int row = 0; row < buttons.length; row++) {
                assertFalse(buttons[row].isEmpty());
                assertFalse(labels[row].isEmpty());
            }
        }
    }

    /**
     * The panel's height is {@code legendButtons().length} rows tall, so the two legends
     * having the same number of rows is not required — but them each having a row for
     * every reachable action is.
     */
    @Test
    public void aRemoteOnlyLegendNamesNoButtonARemoteDoesNotHave() {
        UiState ui = new UiState();
        HudScene.setRemoteOnly(true);
        for (String button : HudScene.legendButtons()) {
            assertFalse("a bare remote has no " + button + " button",
                    button.equals("A") || button.equals("B") || button.equals("Y")
                            || button.equals("X"));
        }
        // The cycle is the whole point: one key has to cover fill, cross and clear.
        assertTrue("the remote legend never explains the centre-key cycle",
                HudScene.legendLabels(ui)[0].toLowerCase(java.util.Locale.US)
                        .contains("cross"));
    }

    @Test
    public void restingHintsAreSaidOutLoudInBothLegends() {
        UiState ui = new UiState();
        ui.hintsOn = false;
        for (boolean remote : new boolean[]{false, true}) {
            HudScene.setRemoteOnly(remote);
            // Asserts the meaning, not the exact wording: the rail is narrow and the
            // phrasing has to be free to shorten without this test having to be edited.
            boolean mentioned = false;
            for (String label : HudScene.legendLabels(ui)) {
                mentioned |= label.toLowerCase(java.util.Locale.US).contains("resting");
            }
            assertTrue("remoteOnly=" + remote + ": nothing says hints are off", mentioned);
        }
    }

    // ---- Colour on colour --------------------------------------------------------------

    /**
     * A chip's glyph is asked for rather than assumed. Plum ink on a lit chip and cream on
     * a resting one both have to clear AA, in every legend and with either identity
     * palette — the case that used to break was simply never checked.
     */
    @Test
    public void everyChipGlyphReadsOnItsChip() {
        UiState ui = new UiState();
        for (boolean distinct : new boolean[]{false, true}) {
            Comfort.get().distinctPlayers = distinct;
            for (boolean remote : new boolean[]{false, true}) {
                HudScene.setRemoteOnly(remote);
                for (int color : chipColours()) {
                    assertReads("a lit chip's glyph", Theme.textOn(color), color);
                    int resting = Draw.blend(Theme.PANEL, color, .34f);
                    assertReads("a resting chip's glyph", Theme.textOn(resting), resting);
                }
                assertEquals(HudScene.legendButtons().length,
                        HudScene.legendLabels(ui).length);
            }
        }
    }

    /**
     * Sky's card with {@link Comfort#distinctPlayers} on: the deep teal exists precisely
     * because it is darker than Rose, so nothing on her card may assume a light fill.
     */
    @Test
    public void skysCardSurvivesHerColourBlindFriendlyColour() {
        Comfort.get().distinctPlayers = true;
        int sky = Theme.playerColor(1);
        assertTrue("the alternate teal is no longer the hard case",
                Theme.contrastRatio(sky, Theme.PANEL) < AA);

        for (int player = 0; player < 2; player++) {
            int color = Theme.playerColor(player);
            // The card fill and accent, worked out exactly as HudScene works them out.
            int fill = Draw.blend(Theme.PANEL, color, .23f);
            assertReads("player " + player + "'s name", Theme.readableOn(color, fill, AA),
                    fill);
            assertTrue("player " + player + "'s dot vanishes into its own card",
                    Theme.contrastRatio(Theme.readableOn(color, fill, 3), fill) >= 3);
        }

        // And the two players still differ in lightness, which is the point of the mode.
        assertTrue("Rose and Sky collapsed back into the same lightness",
                Theme.contrastRatio(Theme.PINK, sky) >= 2.5);
    }

    @Test
    public void inkAndCreamAreChosenNotAssumed() {
        // Light identity colours take ink; the deep teal takes cream. If textOn ever
        // stopped choosing, one of these would flip.
        assertEquals(Theme.INK, Theme.textOn(Theme.PINK));
        assertEquals(Theme.INK, Theme.textOn(Theme.BLUE));
        assertEquals(Theme.INK, Theme.textOn(Theme.GOLD));
        assertEquals(Theme.INK, Theme.textOn(Theme.SOFT_TEXT));
        assertEquals(Theme.CREAM, Theme.textOn(Comfort.SKY_ALT));
        assertEquals(Theme.CREAM, Theme.textOn(Theme.PANEL));
    }

    private static int[] chipColours() {
        return new int[]{Theme.PINK, Theme.BLUE, Theme.GOLD, Theme.SOFT_TEXT,
                Theme.playerColor(0), Theme.playerColor(1)};
    }

    private static void assertReads(String what, int color, int background) {
        double ratio = Theme.contrastRatio(color, background);
        assertTrue(what + " is only " + Math.round(ratio * 100) / 100.0 + ":1",
                ratio >= AA);
    }
}
