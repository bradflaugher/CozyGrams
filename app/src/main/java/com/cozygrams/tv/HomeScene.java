package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Paint;

/** The title screen: the invitation to sit down and play. */
public final class HomeScene {

    public static final int ITEM_CONTINUE = 0;
    public static final int ITEM_STORY = 1;
    public static final int ITEM_SIZE = 2;
    public static final int ITEM_SETTINGS = 3;
    public static final int ITEM_RESTART = 4;
    public static final int ITEM_COUNT = 5;

    /**
     * The returning-player greeting, e.g. "Back after 3 days — 12 pictures finished
     * together". {@link SaveStore} knows the answer and this scene never sees a
     * {@code SaveStore}, so the line is handed in from outside exactly once, the way
     * {@link Comfort} hands the comfort options to the drawing code.
     *
     * <p><b>To wire it up</b>, add one line to {@code CozyGameView}'s constructor, after
     * {@code store.loadGame()} (which is what populates the journey):
     *
     * <pre>
     *   HomeScene.setWelcome(store.welcomeBack(System.currentTimeMillis()));
     * </pre>
     *
     * <p>The clock must be {@code System.currentTimeMillis()} and not the view's
     * {@code now()}: {@code welcomeBack} subtracts the stored {@code previousVisitAt},
     * which {@code SaveStore} writes from wall-clock time. {@code SystemClock.uptimeMillis()}
     * would produce a negative gap and the line would read "Welcome back" forever.
     *
     * <p>Until something sets it the slot is simply empty and the title screen closes up
     * around it, so shipping the wiring later costs nothing.
     */
    private static String welcome = "";

    private final Draw draw;

    public HomeScene(Draw draw) {
        this.draw = draw;
    }

    /**
     * How long a "press again to be sure" stays armed. Long enough to read the question,
     * short enough that it cannot be answered by accident minutes later.
     */
    private static final long CONFIRM_WINDOW_MS = 6000;

    /** When the destructive row was armed, or 0 when it is not armed. */
    private static long restartArmedAt;

    /** The size the next endless picture will use, when it differs from the current one. */
    private static int pendingSize;

    /** True while START THE STORY AGAIN is waiting for a second, deliberate press. */
    public static boolean restartArmed() {
        return restartArmedAt != 0;
    }

    public static void armRestart(long now) {
        restartArmedAt = now == 0 ? 1 : now;
    }

    public static void disarmRestart() {
        restartArmedAt = 0;
    }

    /** Lets the arming lapse once the question has been on screen long enough. */
    static void expireRestart(long now) {
        if (restartArmedAt != 0 && now - restartArmedAt > CONFIRM_WINDOW_MS) {
            restartArmedAt = 0;
        }
    }

    /** Records a board size chosen while a picture was in progress. 0 clears it. */
    public static void setPendingSize(int size) {
        pendingSize = size;
    }

    /** The name of the confirm button on whatever is actually in the player's hands. */
    public static String confirmName() {
        return HudScene.remoteOnly() ? "OK" : "A";
    }

    /** Sets the returning-player line. See the notes on the field for the call site. */
    public static void setWelcome(String line) {
        welcome = line == null ? "" : line.trim();
    }

    /** The returning-player line, or an empty string when nobody has set one. */
    public static String welcome() {
        return welcome;
    }

    /** Labels for each row, in menu order. */
    public static String[] items(GameState game) {
        return new String[]{
                "CONTINUE",
                "STORY BOOK",
                "BOARD SIZE",
                "COZY CORNER",
                restartArmed() ? "START AGAIN?  PRESS " + confirmName() + " TO BE SURE"
                        : "START THE STORY AGAIN"
        };
    }

    /** Secondary text shown to the right of each row. */
    public static String[] values(GameState game) {
        String mode = game.storyMode
                ? "Story  " + (game.storyIndex + 1) + " / " + PuzzleLibrary.count()
                : "Endless  " + game.size + " × " + game.size;
        int endlessSize = game.storyMode ? GameState.MIN_SIZE
                : (pendingSize > 0 ? pendingSize : game.size);
        String sizeValue = "‹  " + endlessSize + " × " + endlessSize + "  ›";
        if (pendingSize > 0 && !game.storyMode && pendingSize != game.size) {
            sizeValue += "  next";
        }
        // The restart row says what it would cost, so nobody has to already know.
        String restartValue = game.storyFurthest > 0
                ? "you're on chapter " + (game.storyIndex + 1) : "";
        return new String[]{
                mode,
                PuzzleLibrary.count() + " little chapters",
                sizeValue,
                "sound · hints · comfort",
                restartArmed() ? "this cannot be undone" : restartValue
        };
    }

    public void draw(Canvas canvas, float width, float height, GameState game, UiState ui,
                     Effects effects, long now) {
        float panelLeft = width * .27f;
        float panelRight = width * .73f;
        draw.panel(canvas, panelLeft, height * Theme.SAFE_AREA, panelRight,
                height * (1 - Theme.SAFE_AREA), ui.highContrastOn ? 244 : 230);

        expireRestart(now);
        drawWordmark(canvas, width, height, ui, now);

        String[] labels = items(game);
        String[] values = values(game);
        float first = height * .420f;
        float step = height * .0865f;
        for (int i = 0; i < labels.length; i++) {
            drawRow(canvas, panelLeft, panelRight, first + i * step, labels[i], values[i],
                    i == ui.menu, now);
        }

        drawFooter(canvas, width, height, ui);
        effects.drawParticles(canvas, draw, now);
    }

    private void drawWordmark(Canvas canvas, float width, float height, UiState ui,
                              long now) {
        // The heartbeat is the one idle movement on this screen, so it is also the first
        // thing calm motion should switch off.
        float beat = Comfort.get().calmMotion ? .5f
                : (float) Math.abs(Math.sin(now / 1700f));
        draw.heart(canvas, width / 2, height * .123f,
                Theme.scale(46) * (.94f + beat * .12f), Theme.PINK);

        float titleSize = Theme.textSize(Theme.TITLE);
        draw.shadowedText(canvas, "COZYGRAMS", width / 2, height * .232f, titleSize,
                Theme.CREAM, Paint.Align.CENTER, true);

        float ruleWidth = Theme.scale(64);
        draw.roundRect(canvas, width / 2 - ruleWidth, height * .256f, width / 2 + ruleWidth,
                height * .256f + Theme.scale(3), Theme.scale(2),
                Draw.withAlpha(Theme.PINK, 190));

        boolean greeting = !welcome.isEmpty();
        draw.text(canvas, "Puzzles are better together", width / 2,
                height * (greeting ? .298f : .312f), Theme.textSize(Theme.BODY),
                Theme.BLUE, Paint.Align.CENTER, false);

        if (greeting) {
            // The slot the returning-player line lives in. It sits with the tagline
            // rather than in the footer, because it is a greeting and not a control hint.
            draw.text(canvas, welcome, width / 2, height * .346f,
                    Theme.textSize(Theme.CAPTION), Theme.GOLD, Paint.Align.CENTER, false);
        }
    }

    private void drawRow(Canvas canvas, float panelLeft, float panelRight, float centreY,
                         String label, String value, boolean focused, long now) {
        float inset = Theme.scale(26);
        float left = panelLeft + inset;
        float right = panelRight - inset;
        float half = Theme.textSize(Theme.BODY) * .95f;
        float radius = half * .7f;

        if (focused) {
            float beat = Comfort.get().calmMotion ? .55f
                    : (float) Math.abs(Math.sin(now / 900f));
            draw.roundRect(canvas, left - Theme.scale(4), centreY - half - Theme.scale(4),
                    right + Theme.scale(4), centreY + half + Theme.scale(4),
                    radius + Theme.scale(4),
                    Draw.withAlpha(Theme.PINK, (int) (55 + beat * 45)));
            draw.roundRect(canvas, left, centreY - half, right, centreY + half, radius,
                    Theme.PINK);
            draw.roundRectStroke(canvas, left, centreY - half, right, centreY + half,
                    radius, Math.max(1.5f, Theme.scale(2.4f)),
                    Draw.withAlpha(Theme.CREAM, 215));
        } else {
            // Warm, not grey. A flat wash of cream over the plum panel comes out the
            // colour of a filing cabinet; a rose-tinted fill under a warmer hairline
            // reads as a card sitting in lamplight, which is the point of the screen.
            draw.roundRect(canvas, left, centreY - half, right, centreY + half, radius,
                    Draw.withAlpha(Theme.PINK_LIGHT, 44));
            draw.roundRectStroke(canvas, left, centreY - half, right, centreY + half,
                    radius, Math.max(1f, Theme.scale(1.4f)),
                    Draw.withAlpha(Theme.CREAM, 54));
        }

        float labelSize = Theme.textSize(Theme.BODY);
        float valueSize = Theme.textSize(Theme.CAPTION);
        int labelColor = focused ? Theme.INK : Theme.CREAM;
        boolean stacked = !value.isEmpty();
        draw.text(canvas, label, left + Theme.scale(26),
                centreY + draw.capCentreOffset(labelSize) - (stacked ? half * .28f : 0),
                labelSize, labelColor, Paint.Align.LEFT, true);

        if (stacked) {
            draw.text(canvas, value, right - Theme.scale(26),
                    centreY + draw.capCentreOffset(valueSize), valueSize,
                    focused ? Draw.withAlpha(Theme.INK, 215) : Theme.SOFT_TEXT,
                    Paint.Align.RIGHT, false);
        }
    }

    /**
     * Who is at the table, and how to move. These used to be two dim lines of the same
     * size sitting almost on top of each other at the bottom of the panel; the first is
     * now a chip with its own colour and the second is the only quiet thing on screen, so
     * they read as a status and a footnote rather than as small print twice.
     */
    private void drawFooter(Canvas canvas, float width, float height, UiState ui) {
        boolean together = ui.joined[1];
        String hint = together ? "Rose and Sky are both here"
                : "Sky can join any time — press a button";
        int accent = together ? Theme.PINK : Comfort.skyColor();

        float size = Theme.textSize(Theme.CAPTION);
        float centreY = height * .862f;
        float half = size * 1.02f;
        float textWidth = draw.measure(hint, size, together);
        float heart = together ? size * 1.5f : 0;
        float chipWidth = textWidth + heart + Theme.scale(48);
        float left = width / 2 - chipWidth / 2;
        float right = width / 2 + chipWidth / 2;

        draw.roundRect(canvas, left, centreY - half, right, centreY + half, half,
                Draw.withAlpha(accent, together ? 52 : 30));
        draw.roundRectStroke(canvas, left, centreY - half, right, centreY + half, half,
                Math.max(1f, Theme.scale(1.4f)), Draw.withAlpha(accent, 130));

        float textLeft = left + Theme.scale(24);
        draw.text(canvas, hint, textLeft, centreY + draw.capCentreOffset(size), size,
                together ? Theme.PINK_LIGHT : Theme.CREAM, Paint.Align.LEFT, together);
        if (together) {
            draw.heart(canvas, textLeft + textWidth + heart * .6f, centreY, size * .95f,
                    Theme.PINK);
        }

        draw.text(canvas, "D-pad to move   ·   " + confirmName() + " to choose",
                width / 2, height * .935f,
                Theme.textSize(Theme.CAPTION),
                Theme.secondaryText(ui.highContrastOn), Paint.Align.CENTER, false);
    }
}
