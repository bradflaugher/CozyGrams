package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * The pause and options screen, framed as tidying the room rather than configuration.
 *
 * <p>The room is read from a sofa, with a remote that has a D-pad, a centre button and
 * Back — nothing else is assumed. So every row is a single line with a switch, the whole
 * list moves under up and down, and the explanation for whichever row is highlighted is
 * given once, in full size, along the bottom. Only seven generous rows are shown at once:
 * twelve tiny labels are a control panel; a calm, focus-following list is a cozy corner.
 *
 * <p><b>Every row answers back.</b> The switch used to be the only reply: a 56 x 29 px
 * pill whose knob travelled 27 px, which is 40 x 21 arcminutes from ten feet and a state
 * difference of 19.5 — barely one capital letter, for the control carrying all nine
 * options. The state is printed as a word now, and anything whose effect is off this
 * screen (the music, the hint button, the gentle check) also says what it did in the
 * explanation slot, because a setting you cannot see change is a setting you cannot trust.
 *
 * <p>{@link #ITEM_COUNT} and the {@code ITEM_} constants are the single source of truth
 * for the row order. {@link #toggle} is the matching source of truth for what each row
 * does, so a caller only has to route the centre button here.
 */
public final class SettingsScene {

    public static final int ITEM_MUSIC = 0;
    public static final int ITEM_SFX = 1;
    public static final int ITEM_GENTLE = 2;
    public static final int ITEM_HINTS = 3;
    public static final int ITEM_BIG_TEXT = 4;
    public static final int ITEM_CONTRAST = 5;
    public static final int ITEM_DISTINCT_PLAYERS = 6;
    public static final int ITEM_BOLD_CURSOR = 7;
    public static final int ITEM_CALM_MOTION = 8;
    public static final int ITEM_DEFAULTS = 9;
    public static final int ITEM_START_STORY = 10;
    public static final int ITEM_BACK = 11;
    public static final int ITEM_COUNT = 12;

    /** The last row that carries a switch; everything after it is an action. */
    private static final int LAST_SWITCH = ITEM_CALM_MOTION;

    /** Enough choices to scan at once from a sofa without turning the screen into a ledger. */
    static final int VISIBLE_ROWS = 7;

    /** The band of the screen the rows live in, as a fraction of its height. */
    static final float ROWS_TOP = .215f;
    static final float ROWS_BOTTOM = .820f;

    /**
     * How long a note stays in the explanation slot before the highlighted row's own
     * sentence comes back. Long enough to read a short line twice from a sofa; short
     * enough that it is gone before anyone wonders whether it is stuck.
     */
    static final long NOTE_MS = 3400;

    /**
     * The switch, in multiples of a row's half height: the track's width, its height, and
     * how much of the knob's own radius the knob fills.
     *
     * <p>2.4 / 1.24 / .76 gave a 56 x 29 px control at 1080p, whose knob travelled 27.1 px
     * — 19.5 arcminutes from ten feet, for the cue carrying all nine settings. Widening it
     * alone was not the answer, because a bigger version of a 27 px slide is still a 27 px
     * slide; the word beside it does the naming now, and the pill grew only as far as the
     * label lane could afford. Measured through the harness at 1920x1080 with LARGER TEXT
     * on: a plain switch row leaves 560.6 px of label lane against the longest label's
     * 528.8 px ("GENTLE MISTAKE CHECK"), and the device's Roboto runs about 2% wider than
     * the harness's Liberation Sans, so 32 px of slack is 21 px on a television. 3.0 would
     * have taken 7 of those for 7 px of extra travel, which the printed word has already
     * made the smaller half of the cue. Knob travel goes 27.1 -> 29.4 px and the knob
     * itself 22.2 -> 26.9 px across.
     */
    private static final float SWITCH_WIDTH_ROWS = 2.7f;
    private static final float SWITCH_HEIGHT_ROWS = 1.44f;
    private static final float KNOB_FILL = .80f;

    /**
     * When {@link #ITEM_DEFAULTS} was armed, or 0 when it is not.
     *
     * <p>Static, and for the same reason {@code HomeScene.restartArmedAt} is: it belongs to
     * the screen rather than to the game, and the scene is drawn from a renderer that is
     * handed no state of its own. The two arm/disarm pairs are deliberately spelled the
     * same way so a reader who has met one has met both.
     */
    private static long defaultsArmedAt;
    private static long storyRestartArmedAt;
    private static boolean storyRestartConfirmed;

    /** A short line to show instead of the row's explanation, and when it was said. */
    private static String note = "";
    private static long noteAt;

    /** Which player opened the corner, or -1 when nobody has said. */
    private static int tidyingPlayer = -1;

    private final Draw draw;
    private final HomeScene.MenuFrame frame;

    public SettingsScene(Draw draw) {
        this.draw = draw;
        this.frame = new HomeScene.MenuFrame(draw);
    }

    public static String[] labels() {
        return labels(new UiState());
    }

    /** Labels adjusted to the place the corner will return to. */
    public static String[] labels(UiState ui) {
        return new String[]{
                "Music",
                "Sound effects",
                "Gentle guidance",
                "Hints",
                "Larger text",
                "Extra contrast",
                // "TELL ROSE & SKY APART" measured 528.9 px at LARGER TEXT against a lane
                // that is 468.0 px wide once the row carries a printed state and two
                // identity chips — and a label can never shrink to fit, because
                // textSize(17) is already sitting on the prose floor there. This is
                // 389.6 px, and the two chips beside it are Rose and Sky, so they are what
                // names the "them".
                "Player colors",
                "Bolder cursors",
                "Reduce motion",
                defaultsArmed() ? "Reset all settings?" : "Reset settings",
                storyRestartArmed() ? "Start the story over?" : "Start story over",
                ui.screenBeforeSettings == UiState.GAME
                        ? "Back to the puzzle" : "Back to the menu"
        };
    }

    /**
     * One line about the highlighted row. Kept short so it still fits on one line at the
     * largest text size, on the narrowest panel we draw.
     */
    public static String[] descriptions() {
        return descriptions(new UiState());
    }

    /** Supporting copy adjusted to the place the corner will return to. */
    public static String[] descriptions(UiState ui) {
        return new String[]{
                "a soft music box in the background",
                "little chimes for every square",
                "gently catch a square that isn't right",
                "light up one square when you ask",
                "bigger type for the far side of the room",
                "deepen the backdrop so the clues pop",
                "Sky takes a deeper teal and a dashed ring",
                "thicker cursor rings that are easy to find",
                "fewer sparkles, with no pulsing",
                "every option back the way it started",
                "chapter one, a fresh book",
                ui.screenBeforeSettings == UiState.GAME
                        ? "we'll keep your place" : "back to choosing a picture"
        };
    }

    public static boolean[] states(UiState ui) {
        Comfort comfort = Comfort.get();
        return new boolean[]{
                ui.musicOn,
                ui.sfxOn,
                ui.gentleCheck,
                ui.hintsOn,
                ui.bigTextOn,
                ui.highContrastOn,
                comfort.distinctPlayers,
                comfort.boldCursor,
                comfort.calmMotion,
                false,
                false,
                false
        };
    }

    /** True when the row shows a switch, rather than being something you simply do. */
    public static boolean hasSwitch(int item) {
        return item >= 0 && item <= LAST_SWITCH;
    }

    /** The word printed beside a switch. Nine settings, and none of them is a colour. */
    static String stateWord(boolean on) {
        return on ? "ON" : "OFF";
    }

    /**
     * Acts on a row, exactly as pressing the centre button should.
     *
     * <p>{@link #ITEM_DEFAULTS} is the one row that cannot be taken back, and it sits
     * directly above the row every visit ends on — one overshoot downward from CALMER
     * ANIMATION lands on it. So it asks first: the first press arms it and changes its own
     * label to a question, and only a second press inside
     * {@value #CONFIRM_WINDOW_MS} ms actually puts everything back. Moving off the row is
     * an answer of "no"; see {@link #disarmDefaults()}.
     *
     * @return true when the row was handled here, false for {@link #ITEM_BACK} and
     *         anything out of range — which the caller should treat as "close the corner".
     */
    public static boolean toggle(UiState ui, int item, long now) {
        Comfort comfort = Comfort.get();
        switch (item) {
            case ITEM_MUSIC:
                ui.musicOn = !ui.musicOn;
                return sayState(item, ui, now);
            case ITEM_SFX:
                ui.sfxOn = !ui.sfxOn;
                return sayState(item, ui, now);
            case ITEM_GENTLE:
                ui.gentleCheck = !ui.gentleCheck;
                return sayState(item, ui, now);
            case ITEM_HINTS:
                ui.hintsOn = !ui.hintsOn;
                return sayState(item, ui, now);
            case ITEM_BIG_TEXT:
                ui.bigTextOn = !ui.bigTextOn;
                return sayState(item, ui, now);
            case ITEM_CONTRAST:
                ui.highContrastOn = !ui.highContrastOn;
                return sayState(item, ui, now);
            case ITEM_DISTINCT_PLAYERS:
                comfort.distinctPlayers = !comfort.distinctPlayers;
                return sayState(item, ui, now);
            case ITEM_BOLD_CURSOR:
                comfort.boldCursor = !comfort.boldCursor;
                return sayState(item, ui, now);
            case ITEM_CALM_MOTION:
                comfort.calmMotion = !comfort.calmMotion;
                return sayState(item, ui, now);
            case ITEM_DEFAULTS:
                return putEverythingBack(ui, now);
            case ITEM_START_STORY:
                return askToStartTheStoryAgain(now);
            default:
                return false;
        }
    }

    /**
     * The clockless form, for the unit tests and anything that only cares what a row
     * changes. A note with no timestamp is simply never shown, and the confirmation
     * window is measured from zero, so this arms and fires on two calls exactly as a
     * deliberate pair of presses does.
     */
    public static boolean toggle(UiState ui, int item) {
        return toggle(ui, item, 0);
    }

    /** Puts the new state of a switch row into the explanation slot, and says it was handled. */
    private static boolean sayState(int item, UiState ui, long now) {
        disarmDefaults();
        disarmStoryRestart();
        say(friendlyName(item) + " is " + (states(ui)[item] ? "on" : "off"), now);
        return true;
    }

    /**
     * The row's label as it would be spoken — "Larger text is on" rather than
     * "LARGER TEXT is on". All-caps is a drawing decision; a sentence is not.
     */
    static String friendlyName(int item) {
        return friendlyName(new UiState(), item);
    }

    static String friendlyName(UiState ui, int item) {
        return labels(ui)[Math.floorMod(item, ITEM_COUNT)];
    }

    private static boolean putEverythingBack(UiState ui, long now) {
        if (confirmationIsFresh(defaultsArmedAt, now)) {
            disarmDefaults();
            ui.restoreDefaults();
            say("Everything is back the way it started  ♥", now);
            return true;
        }
        disarmStoryRestart();
        armDefaults(now);
        return true;
    }

    private static boolean askToStartTheStoryAgain(long now) {
        if (confirmationIsFresh(storyRestartArmedAt, now)) {
            disarmStoryRestart();
            storyRestartConfirmed = true;
            return true;
        }
        disarmDefaults();
        armStoryRestart(now);
        return true;
    }

    /** A confirmation must still be fresh when the second press arrives. */
    static boolean confirmationIsFresh(long armedAt, long now) {
        if (armedAt == 0) {
            return false;
        }
        // The clockless overload deliberately uses 0 for both presses in unit tests.
        return now == 0 || (now >= armedAt && now - armedAt <= CONFIRM_WINDOW_MS);
    }

    // ---- Asking before something that cannot be undone --------------------------------

    /** How long the question waits for its answer. Mirrors {@code HomeScene}'s window. */
    static final long CONFIRM_WINDOW_MS = 6000;

    /** True while PUT EVERYTHING BACK is waiting for a second, deliberate press. */
    public static boolean defaultsArmed() {
        return defaultsArmedAt != 0;
    }

    public static void armDefaults(long now) {
        defaultsArmedAt = now == 0 ? 1 : now;
    }

    public static void disarmDefaults() {
        defaultsArmedAt = 0;
    }

    public static boolean storyRestartArmed() {
        return storyRestartArmedAt != 0;
    }

    public static void armStoryRestart(long now) {
        storyRestartArmedAt = now == 0 ? 1 : now;
    }

    public static void disarmStoryRestart() {
        storyRestartArmedAt = 0;
    }

    /**
     * True once the pair have confirmed they want chapter one again. Consuming it clears
     * the flag so a later visit to settings cannot fire it by accident.
     */
    public static boolean consumeStoryRestart() {
        if (!storyRestartConfirmed) {
            return false;
        }
        storyRestartConfirmed = false;
        return true;
    }

    /** Lets the question lapse once it has been on screen long enough to have been read. */
    static void expireDefaults(long now) {
        if (defaultsArmedAt != 0 && now - defaultsArmedAt > CONFIRM_WINDOW_MS) {
            defaultsArmedAt = 0;
        }
        if (storyRestartArmedAt != 0 && now - storyRestartArmedAt > CONFIRM_WINDOW_MS) {
            storyRestartArmedAt = 0;
        }
    }

    // ---- The one line at the bottom ---------------------------------------------------

    /**
     * Says something in the explanation slot for {@value #NOTE_MS} ms.
     *
     * <p>The corner cannot use {@code HudScene}'s message ribbon — that is drawn with the
     * board, and the board is not on screen here — so a setting whose effect is somewhere
     * else used to produce no visible response at all. Reusing the slot that is already
     * the screen's one voice keeps it to one voice.
     */
    public static void say(String what, long now) {
        note = what == null ? "" : what;
        noteAt = now;
    }

    /** What the bottom line should read, given the highlighted row and the clock. */
    static String bottomLine(int focus, long now) {
        if (defaultsArmed() || storyRestartArmed()) {
            return "Choose again to be sure — this cannot be undone";
        }
        if (!note.isEmpty() && noteAt > 0 && now - noteAt < NOTE_MS) {
            return note;
        }
        return descriptions()[Math.floorMod(focus, ITEM_COUNT)];
    }

    static String bottomLine(int focus, long now, UiState ui) {
        if (defaultsArmed() || storyRestartArmed()) {
            return bottomLine(focus, now);
        }
        if (!note.isEmpty() && noteAt > 0 && now - noteAt < NOTE_MS) {
            return note;
        }
        return descriptions(ui)[Math.floorMod(focus, ITEM_COUNT)];
    }

    /** Puts the screen's memory back, for tests that share one static corner between them. */
    static void forgetTheRoom() {
        defaultsArmedAt = 0;
        storyRestartArmedAt = 0;
        storyRestartConfirmed = false;
        note = "";
        noteAt = 0;
        tidyingPlayer = -1;
    }

    /**
     * Says who reached for the menu, so the other cushion can see whose hand took the
     * board away. -1 when nobody has said, which is what the screenshot harness and the
     * unit tests leave it at.
     */
    public static void setTidyingPlayer(int player) {
        tidyingPlayer = player == 0 || player == 1 ? player : -1;
    }

    /** The subtitle: who is tidying, or what the screen is for when nobody has said. */
    static String subtitle() {
        return tidyingPlayer < 0 ? "Make the room feel just right"
                : Theme.playerName(tidyingPlayer) + " is tidying the room";
    }

    // ---- Layout ----------------------------------------------------------------------

    /** The first item in the seven-row window, keeping focus near its calm centre. */
    static int windowStart(int focus) {
        int selected = Math.floorMod(focus, ITEM_COUNT);
        return Math.max(0, Math.min(ITEM_COUNT - VISIBLE_ROWS,
                selected - VISIBLE_ROWS / 2));
    }

    /** The current group, used as an eyebrow above the focus-following list. */
    static String sectionName(int item) {
        int selected = Math.floorMod(item, ITEM_COUNT);
        if (selected <= ITEM_SFX) return "SOUND";
        if (selected <= ITEM_HINTS) return "HELPING HANDS";
        if (selected <= ITEM_CALM_MOTION) return "COMFORT & ACCESS";
        return "STORY & RESET";
    }

    /** Vertical centres for the visible rows only. */
    static float[] rowCentres(float top, float bottom) {
        float step = rowStep(top, bottom);
        float[] centres = new float[VISIBLE_ROWS];
        for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
            centres[slot] = top + step * (slot + .5f);
        }
        return centres;
    }

    /** Half the height of a row's pill, leaving a clear gap between neighbours. */
    static float rowHalfHeight(float top, float bottom) {
        return rowStep(top, bottom) * .43f;
    }

    private static float rowStep(float top, float bottom) {
        return (bottom - top) / VISIBLE_ROWS;
    }

    // ---- Drawing ---------------------------------------------------------------------

    public void draw(Canvas canvas, float width, float height, UiState ui, long now) {
        expireDefaults(now);
        boolean bold = ui.highContrastOn;

        // Same frame as the title screen, so opening the corner does not rebuild the
        // panel 38 px wider mid-transition. Laid out from measurements the way HomeScene
        // is: header down from the top margin, footer up from the bottom, rows in what
        // is left. Fraction-of-height placement is what let the last row and the footer
        // bleed out of the card on a 5×5, 4K board with larger type.
        frame.panel(canvas, width, height, bold);

        float top = frame.top(height) + Theme.scale(16);
        float bottom = frame.bottom(height) - Theme.scale(16);

        float titleSize = Theme.textSize(Theme.HEADING);
        float titleY = top + titleSize * .70f;
        draw.shadowedText(canvas, "COZY CORNER", width / 2, titleY, titleSize, Theme.CREAM,
                Paint.Align.CENTER, true);

        float subSize = Theme.textSize(Theme.CAPTION);
        float subY = titleY + titleSize * .18f + Theme.scale(10) + subSize * .70f;
        draw.text(canvas, subtitle(), width / 2, subY, subSize,
                tidyingPlayer < 0 ? Theme.secondaryText(bold)
                        : Theme.readableOn(Theme.playerColor(tidyingPlayer), Theme.PANEL, 4.5),
                Paint.Align.CENTER, false);
        float headerBottom = subY + subSize * .18f + Theme.scale(10);

        float capSize = Theme.textSize(Theme.CAPTION);
        float hintY = bottom - capSize * .18f;
        float explainSize = Theme.textSize(Theme.BODY);
        float explainY = hintY - capSize * .70f - Theme.scale(14) - explainSize * .18f;
        float footerTop = explainY - explainSize * .70f - Theme.scale(10);

        int focus = Math.floorMod(ui.menu, ITEM_COUNT);
        float sectionSize = Theme.textSize(Theme.CAPTION);
        float sectionY = headerBottom + sectionSize * .70f;
        float rowLeft = frame.rowLeft(width);
        float rowRight = frame.rowRight(width);
        draw.text(canvas, sectionName(focus), rowLeft, sectionY, sectionSize, Theme.GOLD,
                Paint.Align.LEFT, true);
        int start = windowStart(focus);
        String place = (start + 1) + "–" + (start + VISIBLE_ROWS) + "  OF  " + ITEM_COUNT;
        draw.text(canvas, place, rowRight, sectionY, sectionSize,
                Theme.secondaryText(bold), Paint.Align.RIGHT, false);

        float rowTop = sectionY + sectionSize * .22f + Theme.scale(10);
        float rowBottom = Math.max(rowTop + Theme.scale(80), footerTop);
        String[] labels = labels(ui);
        boolean[] states = states(ui);
        float[] centres = rowCentres(rowTop, rowBottom);
        float half = rowHalfHeight(rowTop, rowBottom);
        for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
            int item = start + slot;
            drawRow(canvas, width, centres[slot], half, labels[item], states[item],
                    item == focus, item, ui, now);
        }

        drawBottomLine(canvas, width, explainY, bottomLine(focus, now, ui), bold);
        drawFooter(canvas, width, hintY, bold);
    }

    private void drawRow(Canvas canvas, float width, float centreY, float half,
                         String label, boolean on, boolean focused, int item, UiState ui,
                         long now) {
        float left = frame.rowLeft(width);
        float right = frame.rowRight(width);
        float radius = half * .82f;
        boolean asking = (item == ITEM_DEFAULTS && defaultsArmed())
                || (item == ITEM_START_STORY && storyRestartArmed());
        int fill = asking ? Theme.CAUTION : Theme.PINK;

        drawRowSurface(canvas, left, right, centreY, half, radius, focused, fill,
                ui.highContrastOn, now);

        // Right to left: the row's edge, the printed state, the switch, then — on the one
        // row that previews a colour — the two identity chips. What is left is the label's.
        float edge = Theme.scale(20);
        float gap = Theme.scale(12);
        float pad = Theme.scale(24);
        float stateSize = Theme.textSize(Theme.CAPTION);
        float switchWidth = half * SWITCH_WIDTH_ROWS;
        float switchRight = right - edge - stateWordWidth(stateSize) - gap;
        float switchLeft = switchRight - switchWidth;
        float chipsWidth = item == ITEM_DISTINCT_PLAYERS ? chipPlateWidth(half) : 0;
        float labelRight = hasSwitch(item)
                ? switchLeft - gap - (chipsWidth > 0 ? chipsWidth + gap : 0)
                : right - edge;

        int ink = focused ? Theme.textOn(fill) : Theme.CREAM;
        // Theme.CAPTION, not the 17 this used to ask for: the prose floor clamps both to the
        // same 36 px at 1080p, so the literal was a caption size that did not say so.
        float labelSize = fit(label, labelRight - left - pad,
                Theme.textSize(Theme.CAPTION), true);
        draw.text(canvas, label, left + pad, centreY + draw.capCentreOffset(labelSize),
                labelSize, ink, Paint.Align.LEFT, true);

        if (chipsWidth > 0) {
            drawIdentityChips(canvas, switchLeft - gap, centreY, half);
        }
        if (hasSwitch(item)) {
            drawSwitch(canvas, switchLeft, centreY, switchWidth, half * SWITCH_HEIGHT_ROWS,
                    on, focused, ui.highContrastOn);
            draw.text(canvas, stateWord(on), right - edge,
                    centreY + draw.capCentreOffset(stateSize), stateSize, ink,
                    Paint.Align.RIGHT, true);
        } else {
            if (focused) {
                drawActionAccessory(canvas, right - edge, centreY,
                        item == ITEM_BACK ? "RETURN" : "CHOOSE", stateSize,
                        Theme.textOn(fill));
            }
        }
    }

    private void drawActionAccessory(Canvas canvas, float right, float centreY,
                                     String label, float size, int textColor) {
        float labelWidth = draw.measure(label, size, true);
        float gap = Theme.scale(8);
        float height = size * 1.05f;
        String key = HomeScene.confirmName();
        float width = draw.keycapWidth(key, height);
        int color = HudScene.remoteOnly() ? Theme.INK : Theme.BUTTON_A;
        draw.keycap(canvas, right - labelWidth - gap - width, centreY, height, key, color);
        draw.text(canvas, label, right, centreY + draw.capCentreOffset(size), size,
                textColor, Paint.Align.RIGHT, true);
    }

    /**
     * The pill under a row.
     *
     * <p>Not {@code Draw.menuRow}, which the title screen uses: that recipe bakes in
     * {@link Theme#PINK} and a fixed hairline, and a row here has to be able to turn
     * {@link Theme#CAUTION} while it is asking a question, and to answer EXTRA CONTRAST.
     * The colours are the shared ones either way, so the two screens still look like one
     * game.
     */
    private void drawRowSurface(Canvas canvas, float left, float right, float centreY,
                                float half, float radius, boolean focused, int fill,
                                boolean bold, long now) {
        if (focused) {
            // A steady glow rather than a heartbeat when movement is unwelcome.
            float beat = Comfort.get().calmMotion ? .55f
                    : (float) Math.abs(Math.sin(now / 900f));
            float glow = half * .2f;
            draw.roundRect(canvas, left - glow, centreY - half - glow, right + glow,
                    centreY + half + glow, radius + glow,
                    Draw.withAlpha(fill, (int) (55 + beat * 45)));
            draw.roundRect(canvas, left, centreY - half, right, centreY + half, radius,
                    fill);
            draw.roundRectStroke(canvas, left, centreY - half, right, centreY + half,
                    radius, Theme.hairline(),
                    Draw.withAlpha(Theme.CREAM, bold ? 255 : 215));
            return;
        }
        // The resting card is the title screen's, warm rather than the flat cream wash
        // this screen used to draw — 1.76:1 on the panel, which is a row you have to
        // already know is there. Extra contrast lifts it to a surface of its own.
        draw.roundRect(canvas, left, centreY - half, right, centreY + half, radius,
                Draw.withAlpha(Theme.ROW_REST, bold ? 78 : 44));
        draw.roundRectStroke(canvas, left, centreY - half, right, centreY + half, radius,
                Theme.keyline(), Draw.withAlpha(Theme.ROW_REST_STROKE, bold ? 110 : 54));
    }

    /** The lane the printed state needs, sized for the wider of the two words. */
    private float stateWordWidth(float size) {
        return Math.max(draw.measure(stateWord(true), size, true),
                draw.measure(stateWord(false), size, true));
    }

    /** How wide the identity preview is, plate and all. */
    private static float chipPlateWidth(float half) {
        float radius = chipRadius(half);
        return radius * (CHIP_GAP_RADII + 2 * PLATE_OVERHANG_RADII);
    }

    private static float chipRadius(float half) {
        return half * .52f;
    }

    /** Centre-to-centre between the two chips, and how far the plate reaches past them. */
    private static final float CHIP_GAP_RADII = 2.4f;
    private static final float PLATE_OVERHANG_RADII = 1.75f;

    /**
     * Two little chips showing Rose's and Sky's current colours, so the effect of the
     * colour row can be seen from the row itself rather than taken on trust.
     *
     * <p>They used to be two discs each sunk in its own dark well, and the well was a
     * hairline: on the focused row that is Rose's pink on a pink pill, so her chip read as
     * an empty ring — at exactly the moment a player is deciding whether to press. One
     * plate behind both, at {@link Theme#INK}, gives Rose 4.2:1 and Sky's deep teal 2.4:1
     * whatever the pill underneath is doing, and the cream hairline finishes the teal.
     */
    private void drawIdentityChips(Canvas canvas, float rightEdge, float centreY,
                                   float half) {
        float radius = chipRadius(half);
        float skyX = rightEdge - radius * PLATE_OVERHANG_RADII;
        float roseX = skyX - radius * CHIP_GAP_RADII;
        draw.roundRect(canvas, roseX - radius * PLATE_OVERHANG_RADII, centreY - radius * 1.5f,
                skyX + radius * PLATE_OVERHANG_RADII, centreY + radius * 1.5f, radius * 1.5f,
                Draw.withAlpha(Theme.INK, 245));
        drawChip(canvas, roseX, centreY, radius, Theme.PINK);
        drawChip(canvas, skyX, centreY, radius, Comfort.skyColor());
    }

    private void drawChip(Canvas canvas, float cx, float centreY, float radius, int color) {
        draw.circle(canvas, cx, centreY, radius, color);
        draw.circleStroke(canvas, cx, centreY, radius, Math.max(1f, radius * .12f),
                Draw.withAlpha(Theme.CREAM, 150));
    }

    /**
     * A pill switch. The knob's position and the word beside it both carry the state, so
     * neither colour nor a 27 px slide is ever alone.
     */
    private void drawSwitch(Canvas canvas, float left, float centreY, float width,
                            float height, boolean on, boolean focused, boolean bold) {
        float radius = height / 2;
        int trackOn = focused ? Theme.PINK_DARK : Theme.PINK;
        int trackOff = focused ? Draw.withAlpha(Theme.INK, bold ? 130 : 90)
                : (bold ? Draw.blend(Theme.TOGGLE_OFF, Theme.CREAM, .18f) : Theme.TOGGLE_OFF);
        draw.roundRect(canvas, left, centreY - radius, left + width, centreY + radius,
                radius, on ? trackOn : trackOff);
        float knobX = on ? left + width - radius : left + radius;
        draw.circle(canvas, knobX, centreY, radius * KNOB_FILL,
                on ? Theme.CREAM : (focused ? Draw.withAlpha(Theme.INK, 150)
                        : Theme.SOFT_TEXT));
        if (on) {
            draw.heart(canvas, left + radius * .95f, centreY, radius * .8f,
                    focused ? Draw.withAlpha(Theme.PINK_LIGHT, 220)
                            : Draw.withAlpha(Theme.CREAM, 200));
        }
    }

    /** The one line at the bottom, given properly rather than whispered. */
    private void drawBottomLine(Canvas canvas, float width, float baseline, String line,
                                boolean bold) {
        if (line.isEmpty()) {
            return;
        }
        float room = frame.rowRight(width) - frame.rowLeft(width);
        float size = fit(line, room, Theme.textSize(Theme.BODY), false);
        draw.text(canvas, line, width / 2, baseline, size,
                Draw.withAlpha(Theme.CREAM, bold ? 255 : 225), Paint.Align.CENTER, false);
    }

    /**
     * How to move, in the button names of whatever is actually in the room.
     *
     * <p>This was drawn straight, unlike every other string on the screen: with LARGER
     * TEXT it measured 944 px inside a 940 px panel and touched both rounded edges. It is
     * fitted now, and it says "A" on a gamepad where it used to say "OK" on both — the
     * title screen has always asked {@link HomeScene#confirmName()} and the two menus were
     * disagreeing about the name of the same button.
     */
    private void drawFooter(Canvas canvas, float width, float baseline, boolean bold) {
        float left = frame.rowLeft(width);
        float right = frame.rowRight(width);
        String confirm = HomeScene.confirmName();
        String back = HudScene.backName();
        float size = Theme.textSize(Theme.CAPTION);
        float gap = Theme.scale(9);
        float group = Theme.scale(22);
        float height;
        float confirmWidth;
        float backWidth;
        float total;
        do {
            height = size * 1.08f;
            confirmWidth = draw.keycapWidth(confirm, height);
            backWidth = draw.keycapWidth(back, height);
            total = height + gap + draw.measure("Move", size, false) + group
                    + confirmWidth + gap + draw.measure("Change", size, false) + group
                    + backWidth + gap + draw.measure("Close", size, false);
            if (total <= right - left || size <= Theme.scale(20)) break;
            size = Math.max(Theme.scale(20), size * (right - left) / total);
        } while (true);

        float centreY = baseline - draw.capCentreOffset(size);
        float x = (left + right - total) / 2;
        int text = Theme.secondaryText(bold);
        draw.dpadKeycap(canvas, x, centreY, height, Theme.SOFT_TEXT);
        x += height + gap;
        draw.text(canvas, "Move", x, baseline, size, text, Paint.Align.LEFT, false);
        x += draw.measure("Move", size, false) + group;
        int confirmColor = HudScene.remoteOnly() ? Theme.SOFT_TEXT : Theme.BUTTON_A;
        draw.keycap(canvas, x, centreY, height, confirm, confirmColor);
        x += confirmWidth + gap;
        draw.text(canvas, "Change", x, baseline, size, text, Paint.Align.LEFT, false);
        x += draw.measure("Change", size, false) + group;
        draw.keycap(canvas, x, centreY, height, back, Theme.SOFT_TEXT);
        x += backWidth + gap;
        draw.text(canvas, "Close", x, baseline, size, text, Paint.Align.LEFT, false);
    }

    /**
     * The largest size at or below {@code preferred} that fits the width — but never
     * below the ten-foot legibility floor, because unreadable text is worse than tight
     * text.
     *
     * <p>The floor was spelled {@code Theme.textSize(0)}, which is the same number and only
     * reads as a floor once you have gone and read {@link Theme#textSize(float)} and noticed
     * that it clamps everything up to {@link Theme#MIN_PROSE_SP}. Asking for the floor by
     * name says so here.
     */
    private float fit(String text, float maxWidth, float preferred, boolean strong) {
        return draw.fit(text, preferred, maxWidth, strong,
                Theme.textSize(Theme.MIN_PROSE_SP));
    }
}
