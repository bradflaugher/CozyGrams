package com.cozygrams.tv;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decides which physical controller is Rose and which is Sky.
 *
 * <p>The first controller to send input becomes Rose, the second becomes Sky, and both
 * keep their identity for the rest of the session. A TV's own remote is deliberately
 * treated as a controller too, so a solo player can start without hunting for a gamepad.
 *
 * <p>Three living-room details decide whether that promise actually holds.
 *
 * <ul>
 *   <li><b>A controller that sleeps and wakes up gets a brand new device id.</b> Slots are
 *       therefore keyed by {@link InputDevice#getDescriptor()}, which is stable across a
 *       reconnect, and never by the id. Whoever was Rose before the batteries hiccuped is
 *       still Rose afterwards.</li>
 *   <li><b>A third controller shares Rose</b> rather than taking Sky's seat or bouncing
 *       between the two. Nobody is ever locked out, and {@link #justShared()} lets the
 *       caller say so out loud instead of leaving someone confused.</li>
 *   <li><b>A stick can twitch on connect.</b> A joystick that has never been seen at rest
 *       cannot produce a step, so a controller can never "join" without a human touching
 *       it.</li>
 * </ul>
 */
public final class PlayerRegistry {

    public static final int ROSE = 0;
    public static final int SKY = 1;

    /** Analog sticks repeat no faster than this so a held stick is not a machine gun. */
    private static final long STICK_REPEAT_MS = 165;
    /**
     * The first repeat waits longer than the rest. Without it a stick pushed once and
     * released a beat later moves two squares, which is exactly how someone overshoots.
     */
    private static final long STICK_FIRST_REPEAT_MS = 340;
    /** How far a stick must travel before it counts as a direction. */
    private static final float STICK_THRESHOLD = .62f;
    /** The stick must return inside this before it can fire the same way again. */
    private static final float STICK_RELEASE = .38f;

    /** Prefix for devices the platform will not name, so their keys stay distinct. */
    private static final String UNNAMED = "device:";

    /**
     * What the registry needs to know about the physical world. Swapped out in tests,
     * where {@code android.view.InputDevice} is a stub that knows nothing.
     */
    public interface Devices {
        /** A name that survives a reconnect, or null when the device cannot be named. */
        String descriptorOf(int deviceId);

        /** Whether that device id still belongs to something plugged in right now. */
        boolean isConnected(int deviceId);

        /**
         * Whether the device has face buttons of its own, as a gamepad does.
         *
         * <p>A bare TV remote has a D-pad, a centre key and Back, and nothing else — so it
         * can never reach {@link #isCross}. Knowing which kind of thing is in someone's
         * hand is what lets the centre key mean more on a remote than it does on a pad.
         * Defaults to true so a device we cannot ask about keeps the richer controls.
         */
        default boolean hasFaceButtons(int deviceId) {
            return true;
        }
    }

    /** The real platform, wrapped so a surprise from an OEM build cannot crash a frame. */
    private static final class PlatformDevices implements Devices {
        @Override
        public String descriptorOf(int deviceId) {
            try {
                InputDevice device = InputDevice.getDevice(deviceId);
                return device == null ? null : device.getDescriptor();
            } catch (RuntimeException problem) {
                return null;
            }
        }

        @Override
        public boolean isConnected(int deviceId) {
            try {
                return InputDevice.getDevice(deviceId) != null;
            } catch (RuntimeException problem) {
                return false;
            }
        }

        @Override
        public boolean hasFaceButtons(int deviceId) {
            try {
                InputDevice device = InputDevice.getDevice(deviceId);
                if (device == null) {
                    return true;
                }
                if ((device.getSources() & InputDevice.SOURCE_GAMEPAD)
                        == InputDevice.SOURCE_GAMEPAD) {
                    return true;
                }
                // Some remotes report a gamepad source without the buttons to match, so
                // ask about the actual keys rather than trusting the source bits alone.
                boolean[] present = device.hasKeys(KeyEvent.KEYCODE_BUTTON_B,
                        KeyEvent.KEYCODE_BUTTON_X);
                return present[0] || present[1];
            } catch (RuntimeException problem) {
                return true;
            }
        }
    }

    /** What one device's stick is doing, so repeats and twitches can be told apart. */
    private static final class Stick {
        /** True once the stick has been seen at rest, which is what arms it. */
        boolean armed;
        /** True while it is inside the release radius. Unknown, so false, to begin with. */
        boolean centred;
        long lastStepAt;
        int dx;
        int dy;
        /** Steps produced since it last passed through the centre. */
        int repeats;
    }

    private final Devices devices;

    /** Stable device name to player slot, in the order the controllers turned up. */
    private final Map<String, Integer> playerByDevice = new LinkedHashMap<>();
    /** Device id to the name it resolved to, so the platform is asked only once per id. */
    private final Map<Integer, String> nameByDeviceId = new HashMap<>();
    /** Stick state, kept per name so a reconnect keeps its arming as well as its slot. */
    private final Map<String, Stick> sticks = new HashMap<>();
    /** Whether each device id lacks face buttons, asked of the platform only once. */
    private final Map<Integer, Boolean> cycleInput = new HashMap<>();

    /** Set when {@link #playerFor} assigns a slot for the first time. */
    private int justJoined = -1;
    /** Set when {@link #playerFor} attaches an extra controller to an existing slot. */
    private boolean justShared;

    public PlayerRegistry() {
        this(new PlatformDevices());
    }

    /** For tests, which need to describe a living room the stubs cannot. */
    public PlayerRegistry(Devices devices) {
        this.devices = devices;
    }

    // ---- Who is who ------------------------------------------------------------------

    /**
     * The player slot for an input device, registering it if this is its first event.
     *
     * <p>A third or later controller shares Rose so nobody is ever locked out; that is
     * reported by {@link #justShared()} rather than {@link #justJoined()}, because no new
     * player has arrived — someone has simply picked up a second way to play.
     */
    public int playerFor(int deviceId) {
        justJoined = -1;
        justShared = false;

        String name = nameOf(deviceId);
        Integer known = playerByDevice.get(name);
        if (known != null) {
            // Either a device we have seen before, or the same controller back from a
            // reconnect under a new id. Both keep the identity they already had.
            return known;
        }

        int player;
        if (!joined(ROSE)) {
            player = ROSE;
        } else if (!joined(SKY)) {
            player = SKY;
        } else {
            player = ROSE;
            justShared = true;
        }
        playerByDevice.put(name, player);
        if (!justShared) {
            justJoined = player;
        }
        return player;
    }

    /** The slot that was claimed by the most recent {@link #playerFor}, or -1. */
    public int justJoined() {
        return justJoined;
    }

    /**
     * True when this device has no button of its own for crossing a square — a bare TV
     * remote. Such a player needs the centre key to do more work, so the caller cycles
     * empty → filled → crossed with it instead of only toggling a fill.
     *
     * <p>The answer is cached per device id: it cannot change while a controller is
     * plugged in, and asking the platform on every keypress would be wasteful.
     */
    public boolean needsCycleInput(int deviceId) {
        Boolean known = cycleInput.get(deviceId);
        if (known == null) {
            known = !devices.hasFaceButtons(deviceId);
            cycleInput.put(deviceId, known);
        }
        return known;
    }

    /**
     * True when the most recent {@link #playerFor} handed an extra controller to a slot
     * that was already taken. Worth saying out loud — "that one is playing as Rose too" —
     * because a silently doubled-up controller is otherwise a small mystery.
     */
    public boolean justShared() {
        return justShared;
    }

    /** How many player slots are being played. */
    public int playerCount() {
        return (joined(ROSE) ? 1 : 0) + (joined(SKY) ? 1 : 0);
    }

    /** How many distinct physical controllers have been seen, including extras. */
    public int deviceCount() {
        return playerByDevice.size();
    }

    /** True once a controller has claimed the given slot. */
    public boolean joined(int player) {
        return playerByDevice.containsValue(player);
    }

    /**
     * Drops everything remembered about a device id. Optional: the game works without it,
     * but a caller that listens for {@code onInputDeviceRemoved} can call this to release
     * the id, and the controller will still get its slot back when it returns.
     */
    public void forgetDevice(int deviceId) {
        nameByDeviceId.remove(deviceId);
    }

    /**
     * The name a device answers to, resolved once per id and then cached — asking the
     * platform is a binder call, and joystick events arrive sixty times a second.
     *
     * <p>Two controllers of the same model can report the same descriptor. When the id
     * that already holds a name is still connected, this is a genuine second controller
     * rather than a reconnect, so it is given a name of its own.
     */
    private String nameOf(int deviceId) {
        String cached = nameByDeviceId.get(deviceId);
        if (cached != null) {
            return cached;
        }
        String name = devices.descriptorOf(deviceId);
        if (name == null || name.isEmpty()) {
            name = UNNAMED + deviceId;
        } else if (heldByALiveDevice(name, deviceId)) {
            name = name + "#" + deviceId;
        }
        nameByDeviceId.put(deviceId, name);
        return name;
    }

    private boolean heldByALiveDevice(String name, int deviceId) {
        for (Map.Entry<Integer, String> entry : nameByDeviceId.entrySet()) {
            if (entry.getKey() != deviceId && entry.getValue().equals(name)
                    && devices.isConnected(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    // ---- Analog sticks and hat switches -----------------------------------------------

    /**
     * Converts a joystick event into a single step, or null when the stick has not moved
     * far enough, is repeating too quickly, or has not yet been seen at rest.
     *
     * @return a two element array of {dx, dy}, or null for "no movement this frame"
     */
    public int[] stickStep(MotionEvent event, long now) {
        if (event == null
                || (event.getSource() & InputDevice.SOURCE_JOYSTICK) == 0
                || event.getActionMasked() != MotionEvent.ACTION_MOVE) {
            return null;
        }
        return stickStep(event.getDeviceId(),
                event.getAxisValue(MotionEvent.AXIS_X),
                event.getAxisValue(MotionEvent.AXIS_Y),
                event.getAxisValue(MotionEvent.AXIS_HAT_X),
                event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                now);
    }

    /**
     * The whole of the stick rule, as plain numbers. Some TV gamepads report their D-pad
     * only on the hat axes, so the larger of each pair wins and both kinds of controller
     * behave identically from here on.
     *
     * @param stickX -1 (left) to 1 (right) on the analog stick
     * @param stickY -1 (up) to 1 (down) on the analog stick
     * @param hatX   the same for the hat switch, which most D-pads report at exactly ±1
     * @param hatY   the same for the hat switch
     */
    public int[] stickStep(int deviceId, float stickX, float stickY, float hatX, float hatY,
                           long now) {
        float x = strongest(stickX, hatX);
        float y = strongest(stickY, hatY);
        Stick stick = stickFor(deviceId);

        if (Math.abs(x) < STICK_RELEASE && Math.abs(y) < STICK_RELEASE) {
            // At rest: the next push counts immediately, and from now on we know this
            // controller has a centre to come back to.
            stick.armed = true;
            stick.centred = true;
            stick.repeats = 0;
            stick.dx = 0;
            stick.dy = 0;
            return null;
        }
        if (Math.abs(x) < STICK_THRESHOLD && Math.abs(y) < STICK_THRESHOLD) {
            // Between the two radii: deliberately nothing at all, which is what stops a
            // stick resting slightly off centre from stuttering.
            return null;
        }
        if (!stick.armed && !playerByDevice.containsKey(nameOf(deviceId))) {
            // A deflection is the first thing we have ever heard from this controller.
            // That is what a stuck or badly calibrated axis looks like on connect, and it
            // must not be able to sign a player up on its own.
            stick.centred = false;
            return null;
        }

        // Favour the dominant axis so a diagonal never moves a cursor twice.
        int dx = 0;
        int dy = 0;
        if (Math.abs(x) >= Math.abs(y)) {
            dx = x > 0 ? 1 : -1;
        } else {
            dy = y > 0 ? 1 : -1;
        }

        // A push from rest always counts. A change of direction while still held counts
        // as soon as the ordinary repeat allows, so rolling the stick around its rim
        // cannot fire faster than holding it in one direction.
        boolean fromRest = stick.centred;
        boolean firstEver = stick.repeats == 0;
        boolean turned = dx != stick.dx || dy != stick.dy;
        long wait = turned ? STICK_REPEAT_MS
                : (stick.repeats <= 1 ? STICK_FIRST_REPEAT_MS : STICK_REPEAT_MS);
        if (!fromRest && !firstEver && now - stick.lastStepAt < wait) {
            return null;
        }

        stick.armed = true;
        stick.centred = false;
        stick.lastStepAt = now;
        stick.repeats = fromRest || turned ? 1 : stick.repeats + 1;
        stick.dx = dx;
        stick.dy = dy;
        return new int[]{dx, dy};
    }

    private Stick stickFor(int deviceId) {
        String name = nameOf(deviceId);
        Stick stick = sticks.get(name);
        if (stick == null) {
            stick = new Stick();
            sticks.put(name, stick);
        }
        return stick;
    }

    private static float strongest(float primary, float secondary) {
        return Math.abs(secondary) > Math.abs(primary) ? secondary : primary;
    }

    // ---- Button vocabulary ---------------------------------------------------------
    //
    // Four controllers have to work: an Xbox-style pad, a PlayStation-style pad (Android
    // maps Cross/Circle/Square/Triangle onto A/B/X/Y), an NVIDIA Shield controller, and a
    // bare TV remote with nothing but a D-pad, a centre and Back. The sets below are kept
    // disjoint, because the caller tests them in order and an overlap would silently
    // shadow whichever comes later.

    /** Buttons that mean "yes, do it": gamepad A, the remote's centre, and Enter. */
    public static boolean isConfirm(int key) {
        return key == KeyEvent.KEYCODE_BUTTON_A
                || key == KeyEvent.KEYCODE_DPAD_CENTER
                || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER
                || key == KeyEvent.KEYCODE_SPACE;
    }

    /**
     * Buttons that mean "cross this out". B and X on a gamepad are the ones on the legend;
     * C and R1 are there for pads that report a third face button of their own, and the
     * remote's red key for the TV remotes that carry one.
     */
    public static boolean isCross(int key) {
        return key == KeyEvent.KEYCODE_BUTTON_B
                || key == KeyEvent.KEYCODE_BUTTON_X
                || key == KeyEvent.KEYCODE_BUTTON_C
                || key == KeyEvent.KEYCODE_BUTTON_R1
                || key == KeyEvent.KEYCODE_PROG_RED;
    }

    /** Buttons that mean "step back": Back and Escape. */
    public static boolean isBack(int key) {
        return key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE;
    }

    /** Buttons that open the cozy corner: Start, Menu, and the remote's own Menu key. */
    public static boolean isMenu(int key) {
        return key == KeyEvent.KEYCODE_BUTTON_START
                || key == KeyEvent.KEYCODE_MENU
                || key == KeyEvent.KEYCODE_BUTTON_SELECT
                || key == KeyEvent.KEYCODE_BUTTON_MODE
                || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
    }

    /**
     * Buttons that ask for a hint. A bare remote has neither of these, which is why
     * {@code CozyGameView} also treats a held centre button as a hint — the one spare
     * gesture a remote-only player has.
     */
    public static boolean isHint(int key) {
        return key == KeyEvent.KEYCODE_BUTTON_Y || key == KeyEvent.KEYCODE_BUTTON_L1;
    }
}
