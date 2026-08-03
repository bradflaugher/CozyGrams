package com.cozygrams.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The living room, written down.
 *
 * <p>Unit tests run against a stubbed android.jar, so there is no real {@code InputDevice}
 * and no way to build a {@code MotionEvent}. Both are seams instead: controllers are
 * described through {@link PlayerRegistry.Devices}, and the stick rule is exercised
 * through the plain-numbers form of {@code stickStep} that the {@code MotionEvent} overload
 * delegates to.
 */
public class PlayerRegistryTest {

    /** A pretend set of connected controllers. */
    private static final class FakeDevices implements PlayerRegistry.Devices {
        private final Map<Integer, String> descriptors = new HashMap<>();
        private final Set<Integer> connected = new HashSet<>();

        FakeDevices plug(int deviceId, String descriptor) {
            descriptors.put(deviceId, descriptor);
            connected.add(deviceId);
            return this;
        }

        FakeDevices unplug(int deviceId) {
            connected.remove(deviceId);
            return this;
        }

        @Override
        public String descriptorOf(int deviceId) {
            return descriptors.get(deviceId);
        }

        @Override
        public boolean isConnected(int deviceId) {
            return connected.contains(deviceId);
        }
    }

    /** Two gamepads, the shapes a couch usually holds. */
    private static FakeDevices twoPads() {
        return new FakeDevices().plug(11, "pad-rose").plug(12, "pad-sky");
    }

    // ---- Who is who ------------------------------------------------------------------

    @Test
    public void theFirstControllerIsRoseAndTheSecondIsSky() {
        PlayerRegistry registry = new PlayerRegistry(twoPads());

        assertEquals(PlayerRegistry.ROSE, registry.playerFor(11));
        assertEquals(PlayerRegistry.ROSE, registry.justJoined());
        assertTrue(registry.joined(PlayerRegistry.ROSE));
        assertFalse(registry.joined(PlayerRegistry.SKY));

        assertEquals(PlayerRegistry.SKY, registry.playerFor(12));
        assertEquals(PlayerRegistry.SKY, registry.justJoined());
        assertEquals(2, registry.playerCount());
    }

    @Test
    public void aControllerKeepsItsSlotForTheRestOfTheSession() {
        PlayerRegistry registry = new PlayerRegistry(twoPads());
        registry.playerFor(11);
        registry.playerFor(12);

        for (int round = 0; round < 5; round++) {
            assertEquals(PlayerRegistry.ROSE, registry.playerFor(11));
            assertEquals(PlayerRegistry.SKY, registry.playerFor(12));
            assertEquals(-1, registry.justJoined());
            assertFalse(registry.justShared());
        }
    }

    /**
     * Two gamepads plus the TV's own remote: the remote shares Rose rather than taking
     * Sky's seat, and says so, so that a third person picking up the remote does not
     * quietly start driving somebody else's cursor without an explanation.
     */
    @Test
    public void aThirdControllerSharesRoseAndSaysSo() {
        PlayerRegistry registry = new PlayerRegistry(
                twoPads().plug(13, "tv-remote"));
        registry.playerFor(11);
        registry.playerFor(12);

        assertEquals(PlayerRegistry.ROSE, registry.playerFor(13));
        assertTrue(registry.justShared());
        assertEquals("no new player has arrived", -1, registry.justJoined());
        assertEquals(2, registry.playerCount());
        assertEquals(3, registry.deviceCount());

        // And it stays Rose's, rather than bouncing to Sky on the next press.
        assertEquals(PlayerRegistry.ROSE, registry.playerFor(13));
        assertFalse(registry.justShared());
    }

    /**
     * The bug that ruins an evening: a controller that sleeps and wakes comes back with a
     * different device id, and used to be handed the next free slot — so Sky would start
     * driving Rose's cursor half way through a puzzle.
     */
    @Test
    public void aReconnectedControllerKeepsItsIdentity() {
        FakeDevices room = twoPads();
        PlayerRegistry registry = new PlayerRegistry(room);
        registry.playerFor(11);
        assertEquals(PlayerRegistry.SKY, registry.playerFor(12));

        // Sky's pad drops off and comes back as device 77.
        room.unplug(12).plug(77, "pad-sky");

        assertEquals(PlayerRegistry.SKY, registry.playerFor(77));
        assertEquals("nobody joined; Sky simply came back", -1, registry.justJoined());
        assertFalse(registry.justShared());
        assertEquals(PlayerRegistry.ROSE, registry.playerFor(11));
        assertEquals(2, registry.playerCount());
    }

    /**
     * Two controllers of the same model can report the same descriptor. While the first
     * one is still plugged in, the second is a second player and not a reconnect.
     */
    @Test
    public void twinControllersThatShareADescriptorStillGetTheirOwnSlots() {
        PlayerRegistry registry = new PlayerRegistry(
                new FakeDevices().plug(21, "same-model").plug(22, "same-model"));

        assertEquals(PlayerRegistry.ROSE, registry.playerFor(21));
        assertEquals(PlayerRegistry.SKY, registry.playerFor(22));
        assertEquals(2, registry.playerCount());
    }

    // ---- Leaving, and coming back ----------------------------------------------------

    /**
     * The failure nothing was watching for: a pad goes flat and its cursor, its tinted
     * bands and its beating dot stay on the board for the rest of the evening, because
     * "has Sky joined" was answered by a map that never had anything removed from it.
     */
    @Test
    public void aControllerThatLeavesEmptiesItsSeatAndKeepsIt() {
        FakeDevices room = twoPads();
        PlayerRegistry registry = new PlayerRegistry(room);
        registry.playerFor(11);
        registry.playerFor(12);
        assertEquals(PlayerRegistry.SKY, registry.slotOf(12));

        room.unplug(12);
        assertEquals("the seat Sky's pad just left", PlayerRegistry.SKY,
                registry.releaseDevice(12));
        assertFalse("nobody is sitting there now",
                registry.seatOccupied(PlayerRegistry.SKY));
        assertTrue("but the chair still has her name on it",
                registry.joined(PlayerRegistry.SKY));
        assertEquals(1, registry.playerCount());
        assertTrue(registry.seatOccupied(PlayerRegistry.ROSE));

        // And back she comes, under the new id the platform gives a woken controller.
        room.plug(77, "pad-sky");
        assertEquals(PlayerRegistry.SKY, registry.playerFor(77));
        assertTrue(registry.seatOccupied(PlayerRegistry.SKY));
        assertEquals("nobody joined; Sky simply came back", -1, registry.justJoined());
        assertEquals(2, registry.playerCount());
    }

    @Test
    public void releasingSomethingWeNeverHeardFromEmptiesNothing() {
        PlayerRegistry registry = new PlayerRegistry(twoPads());
        registry.playerFor(11);
        assertEquals(-1, registry.slotOf(99));
        assertEquals(-1, registry.releaseDevice(99));
        assertTrue(registry.seatOccupied(PlayerRegistry.ROSE));
    }

    /**
     * Rose has two controllers on her seat. One of them dying must not empty the chair she
     * is still sitting in.
     */
    @Test
    public void aSharedSeatSurvivesLosingOneOfItsControllers() {
        PlayerRegistry registry = new PlayerRegistry(
                twoPads().plug(13, "tv-remote"));
        registry.playerFor(11);
        registry.playerFor(12);
        registry.playerFor(13);

        assertEquals("the seat still has Rose's own pad in it", -1,
                registry.releaseDevice(13));
        assertTrue(registry.seatOccupied(PlayerRegistry.ROSE));
        assertEquals(2, registry.playerCount());
    }

    /**
     * Once a seat is genuinely empty a different controller may take it. It used to be
     * locked for the session: a pad that had been seen and lost left the chair lit, so the
     * next controller shared Rose and two people drove one cursor.
     */
    @Test
    public void aFreshControllerTakesASeatNobodyIsSittingIn() {
        FakeDevices room = twoPads().plug(31, "pad-guest");
        PlayerRegistry registry = new PlayerRegistry(room);
        registry.playerFor(11);
        registry.playerFor(12);
        room.unplug(12);
        registry.releaseDevice(12);

        assertEquals(PlayerRegistry.SKY, registry.playerFor(31));
        assertFalse("nobody is sharing anything here", registry.justShared());
        assertEquals(PlayerRegistry.SKY, registry.justJoined());
        assertEquals(2, registry.playerCount());
    }

    /** A device the platform will not name still works; it just cannot be recognised again. */
    @Test
    public void anUnnamedDeviceStillGetsASlot() {
        PlayerRegistry registry = new PlayerRegistry(new FakeDevices());

        assertEquals(PlayerRegistry.ROSE, registry.playerFor(5));
        assertEquals(PlayerRegistry.SKY, registry.playerFor(6));
        assertEquals(PlayerRegistry.ROSE, registry.playerFor(5));
    }

    // ---- Sticks and hat switches ------------------------------------------------------

    /** Arms a device the way a real one does: by sitting at rest for one sample. */
    private static PlayerRegistry armed(FakeDevices room, int deviceId) {
        PlayerRegistry registry = new PlayerRegistry(room);
        assertNull(registry.stickStep(deviceId, 0, 0, 0, 0, 0));
        return registry;
    }

    @Test
    public void aCentredStickNeverFires() {
        PlayerRegistry registry = armed(twoPads(), 11);
        for (long now = 0; now < 2000; now += 16) {
            assertNull("centred at " + now, registry.stickStep(11, 0, 0, 0, 0, now));
        }
        // Nor does a stick resting just off centre, which is what worn hardware does.
        assertNull(registry.stickStep(11, .3f, -.25f, 0, 0, 2100));
        assertNull(registry.stickStep(11, .5f, 0, 0, 0, 2200));
        assertFalse(registry.joined(PlayerRegistry.ROSE));
    }

    /**
     * A controller that reports a deflected axis the moment it connects must not be able
     * to sign a player up. Until it has been seen at rest, or a human has pressed a
     * button, its stick does nothing at all.
     */
    @Test
    public void aTwitchOnConnectCannotJoinAPlayer() {
        PlayerRegistry registry = new PlayerRegistry(twoPads());

        assertNull("a stuck axis is not a person", registry.stickStep(11, 1f, 0, 0, 0, 0));
        assertEquals(0, registry.playerCount());

        // Once it has been seen at rest, the very next push works normally.
        assertNull(registry.stickStep(11, 0, 0, 0, 0, 100));
        assertArrayEquals(new int[]{1, 0}, registry.stickStep(11, 1f, 0, 0, 0, 200));
    }

    /**
     * The twitch guard is right and it used to be silent. A stick at rest sends no event,
     * so a brand-new pad's very first push is always the swallowed one — and somebody whose
     * instinct is the stick rather than a button got no sign at all that the game had
     * noticed them.
     */
    @Test
    public void aFirstStickPushIsAcknowledgedOnceEvenThoughItCannotJoin() {
        PlayerRegistry registry = new PlayerRegistry(twoPads());

        assertNull(registry.stickStep(11, 1f, 0, 0, 0, 0));
        assertTrue("the room noticed the push", registry.justStirred());
        assertEquals("and nobody joined on the strength of it", 0, registry.playerCount());

        for (long now = 16; now < 400; now += 16) {
            assertNull(registry.stickStep(11, 1f, 0, 0, 0, now));
            assertFalse("a stuck axis must not pulse sixty times a second",
                    registry.justStirred());
        }

        // Back to rest, out again: a second deliberate push is acknowledged again.
        assertNull(registry.stickStep(11, 0, 0, 0, 0, 500));
        assertFalse(registry.justStirred());
        assertArrayEquals("and by now it is armed, so it moves instead",
                new int[]{1, 0}, registry.stickStep(11, 1f, 0, 0, 0, 600));
        assertFalse(registry.justStirred());
    }

    /** A human who pressed a button first is trusted, twitch or no twitch. */
    @Test
    public void aControllerThatPressedAButtonIsTrustedImmediately() {
        PlayerRegistry registry = new PlayerRegistry(twoPads());
        registry.playerFor(11);

        assertArrayEquals(new int[]{0, 1}, registry.stickStep(11, 0, 1f, 0, 0, 0));
    }

    @Test
    public void aHeldStickIsRateLimited() {
        PlayerRegistry registry = armed(twoPads(), 11);

        assertArrayEquals("the push itself", new int[]{1, 0},
                registry.stickStep(11, 1f, 0, 0, 0, 1000));
        assertNull("still the same push", registry.stickStep(11, 1f, 0, 0, 0, 1100));
        assertNull("a tap must not become two squares",
                registry.stickStep(11, 1f, 0, 0, 0, 1300));
        assertArrayEquals("the first repeat, once it is clearly a hold",
                new int[]{1, 0}, registry.stickStep(11, 1f, 0, 0, 0, 1400));

        // From there it repeats at a steady, countable rate rather than a machine gun.
        assertNull(registry.stickStep(11, 1f, 0, 0, 0, 1500));
        assertArrayEquals(new int[]{1, 0}, registry.stickStep(11, 1f, 0, 0, 0, 1600));
        assertNull(registry.stickStep(11, 1f, 0, 0, 0, 1700));
        assertArrayEquals(new int[]{1, 0}, registry.stickStep(11, 1f, 0, 0, 0, 1800));

        // Letting go and pushing again is immediate, however quickly it is done.
        assertNull(registry.stickStep(11, 0, 0, 0, 0, 1810));
        assertArrayEquals(new int[]{1, 0}, registry.stickStep(11, 1f, 0, 0, 0, 1820));
    }

    /** Sixty samples a second of a held stick must not become sixty steps. */
    @Test
    public void aHeldStickDoesNotRunAwayAtFrameRate() {
        PlayerRegistry registry = armed(twoPads(), 11);
        int steps = 0;
        for (long now = 0; now < 1000; now += 16) {
            if (registry.stickStep(11, 0, -1f, 0, 0, now) != null) {
                steps++;
            }
        }
        assertTrue("a second of holding gave " + steps + " steps", steps >= 3);
        assertTrue("a second of holding gave " + steps + " steps", steps <= 7);
    }

    @Test
    public void aDiagonalProducesExactlyOneStep() {
        PlayerRegistry registry = armed(twoPads(), 11);

        // Down and right together: the dominant axis wins, and only one of them moves.
        int[] step = registry.stickStep(11, .95f, .90f, 0, 0, 1000);
        assertNotNull(step);
        assertEquals("exactly one axis moves", 1, Math.abs(step[0]) + Math.abs(step[1]));
        assertArrayEquals(new int[]{1, 0}, step);

        assertNull(registry.stickStep(11, 0, 0, 0, 0, 1010));
        assertArrayEquals("a hair more vertical goes down instead", new int[]{0, 1},
                registry.stickStep(11, .90f, .95f, 0, 0, 1020));

        // A perfect diagonal is a tie, and a tie must still be one step, not none or two.
        assertNull(registry.stickStep(11, 0, 0, 0, 0, 1030));
        int[] tie = registry.stickStep(11, -1f, -1f, 0, 0, 1040);
        assertNotNull(tie);
        assertEquals(1, Math.abs(tie[0]) + Math.abs(tie[1]));
    }

    /** Some TV gamepads report their D-pad only on the hat axes. */
    @Test
    public void theHatAxesDriveTheCursorToo() {
        PlayerRegistry registry = armed(twoPads(), 11);

        assertArrayEquals("hat left", new int[]{-1, 0},
                registry.stickStep(11, 0, 0, -1f, 0, 1000));
        assertNull(registry.stickStep(11, 0, 0, 0, 0, 1010));
        assertArrayEquals("hat up", new int[]{0, -1},
                registry.stickStep(11, 0, 0, 0, -1f, 1020));
        assertNull(registry.stickStep(11, 0, 0, 0, 0, 1030));
        assertArrayEquals("hat down", new int[]{0, 1},
                registry.stickStep(11, 0, 0, 0, 1f, 1040));

        // A hat pressed hard beats a stick drifting gently, whichever way each is going.
        assertNull(registry.stickStep(11, 0, 0, 0, 0, 1050));
        assertArrayEquals(new int[]{1, 0}, registry.stickStep(11, -.4f, 0, 1f, 0, 1060));
    }

    @Test
    public void eachControllerRepeatsOnItsOwnClock() {
        FakeDevices room = twoPads();
        PlayerRegistry registry = armed(room, 11);
        assertNull(registry.stickStep(12, 0, 0, 0, 0, 0));

        assertArrayEquals(new int[]{1, 0}, registry.stickStep(11, 1f, 0, 0, 0, 1000));
        assertArrayEquals("Sky is not held back by Rose's repeat clock",
                new int[]{0, 1}, registry.stickStep(12, 0, 1f, 0, 0, 1010));
    }

    /** A reconnected controller does not have to be taught its stick all over again. */
    @Test
    public void armingSurvivesAReconnect() {
        FakeDevices room = twoPads();
        PlayerRegistry registry = armed(room, 12);
        room.unplug(12).plug(77, "pad-sky");

        assertArrayEquals(new int[]{1, 0}, registry.stickStep(77, 1f, 0, 0, 0, 500));
    }

    // ---- Button vocabulary ------------------------------------------------------------

    /**
     * The four controllers that have to work. Android maps a PlayStation pad's
     * Cross/Circle/Square/Triangle onto A/B/X/Y and an NVIDIA Shield controller onto the
     * standard gamepad keys, so those two are covered by the same codes as an Xbox pad.
     */
    @Test
    public void everyControllerCanPlay() {
        // Xbox, PlayStation and Shield pads: fill, cross, hint, menu, back.
        assertTrue(PlayerRegistry.isConfirm(KeyEvent.KEYCODE_BUTTON_A));
        assertTrue(PlayerRegistry.isCross(KeyEvent.KEYCODE_BUTTON_B));
        assertTrue(PlayerRegistry.isCross(KeyEvent.KEYCODE_BUTTON_X));
        assertTrue(PlayerRegistry.isHint(KeyEvent.KEYCODE_BUTTON_Y));
        assertTrue(PlayerRegistry.isHint(KeyEvent.KEYCODE_BUTTON_L1));
        assertTrue(PlayerRegistry.isMenu(KeyEvent.KEYCODE_BUTTON_START));
        assertTrue(PlayerRegistry.isMenu(KeyEvent.KEYCODE_BUTTON_SELECT));
        assertTrue(PlayerRegistry.isBack(KeyEvent.KEYCODE_BACK));

        // A bare TV remote: a D-pad, a centre, Back, and usually a menu key.
        assertTrue(PlayerRegistry.isConfirm(KeyEvent.KEYCODE_DPAD_CENTER));
        assertTrue(PlayerRegistry.isConfirm(KeyEvent.KEYCODE_ENTER));
        assertTrue(PlayerRegistry.isBack(KeyEvent.KEYCODE_BACK));
        assertTrue(PlayerRegistry.isMenu(KeyEvent.KEYCODE_MENU));
    }

    /**
     * The caller tests these in order, so an overlap would silently shadow whichever comes
     * later — a key that both crossed a square and opened the menu would only ever do one
     * of them.
     */
    @Test
    public void noKeyMeansTwoThings() {
        for (int key = 0; key <= KeyEvent.KEYCODE_PROG_RED + 40; key++) {
            int meanings = (PlayerRegistry.isConfirm(key) ? 1 : 0)
                    + (PlayerRegistry.isCross(key) ? 1 : 0)
                    + (PlayerRegistry.isBack(key) ? 1 : 0)
                    + (PlayerRegistry.isMenu(key) ? 1 : 0)
                    + (PlayerRegistry.isHint(key) ? 1 : 0);
            assertTrue("keycode " + key + " has " + meanings + " meanings", meanings <= 1);
        }
    }

    /** The D-pad steers and nothing else, on every controller. */
    @Test
    public void theDirectionKeysAreNeverAnAction() {
        int[] directions = {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        };
        for (int key : directions) {
            assertFalse(PlayerRegistry.isConfirm(key));
            assertFalse(PlayerRegistry.isCross(key));
            assertFalse(PlayerRegistry.isBack(key));
            assertFalse(PlayerRegistry.isMenu(key));
            assertFalse(PlayerRegistry.isHint(key));
        }
    }
}
