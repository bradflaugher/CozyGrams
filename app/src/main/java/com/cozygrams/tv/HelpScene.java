package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.List;

/**
 * How to play: the reference pages behind the first-evening tour.
 *
 * <p>Read from a sofa as often as from a hand's length away, so nothing here asks for
 * anything a bare remote cannot do. How to play is five pages turned with left and right,
 * each a short list of a few words in gold and a sentence in cream; the page in front is a
 * lit tab along the top, so where you are is never a colour alone. It is also where the
 * first-evening tour ({@link Tutorial}) can be taken again.
 *
 * <p>The controls page names the buttons of whatever is actually in the room, the same way
 * the rail's legend does: a gamepad's A and B, a remote's OK, or a finger's pen.
 *
 * <p>Like the other scenes this one is pure drawing. It knows nothing about input or
 * {@code Context}; {@link CozyGameView} turns the pages and says the words aloud.
 */
public final class HelpScene {

    public static final int PAGE_BASICS = 0;
    public static final int PAGE_CONTROLS = 1;
    public static final int PAGE_TOGETHER = 2;
    public static final int PAGE_STORY = 3;
    public static final int PAGE_QUESTIONS = 4;
    public static final int PAGE_COUNT = 5;

    /** The tab names, short enough that five sit side by side at the largest text size. */
    static final String[] TABS = {"Basics", "Controls", "Together", "Story Book", "Questions"};

    /** What the button at the foot of How to play does: starts the little tour again. */
    static final String TOUR_AGAIN = "Take the tour again";

    private final Draw draw;

    public HelpScene(Draw draw) {
        this.draw = draw;
    }

    // ---- The words ---------------------------------------------------------------------

    /** The page's heading, as read aloud and as drawn under the tabs. */
    static String title(int page) {
        switch (Math.floorMod(page, PAGE_COUNT)) {
            case PAGE_BASICS:
                return "How nonograms work";
            case PAGE_CONTROLS:
                if (HudScene.touch()) {
                    return "Playing with your fingers";
                }
                return HudScene.remoteOnly() ? "Playing with a TV remote"
                        : "Playing with a controller";
            case PAGE_TOGETHER:
                return "Playing with Rose and Sky";
            case PAGE_STORY:
                return "The Story Book and Endless";
            default:
                return "Questions";
        }
    }

    /**
     * A page's entries, each a pair: a few words to find it by, and the sentence itself.
     * Sentences are kept to two lines at the default size on a television.
     */
    static String[][] entries(int page) {
        switch (Math.floorMod(page, PAGE_COUNT)) {
            case PAGE_BASICS:
                return new String[][]{
                        {"The clues", "Each number is a run of filled squares in that row "
                                + "or column, in order."},
                        {"The gaps", "Runs are kept apart by at least one empty square: "
                                + "3 1 means three, a gap, then one."},
                        {"Crosses", "Cross out a square you know is empty, so the board "
                                + "remembers what you worked out."},
                        {"No guessing", "Every picture is checked by a solver. The clues "
                                + "alone are always enough, with only one answer."}
                };
            case PAGE_CONTROLS:
                return controls();
            case PAGE_TOGETHER:
                return new String[][]{
                        {"Joining", "Hand someone a second controller. The first to press "
                                + "a button is Rose, the next is Sky."},
                        {"Telling apart", "Each cursor wears its letter, R or S, as well as "
                                + "its color. Player colors deepens Sky's and dashes her ring."},
                        {"Sharing", "Both of you work on one picture. Every finished line "
                                + "is shared, and nobody keeps score."},
                        {"On a phone", "Your finger is Rose. Turn on Two players in the "
                                + "Cozy Corner and a paired controller joins as Sky."}
                };
            case PAGE_STORY:
                return new String[][]{
                        {"Chapters", "Twenty-four handmade pictures, from little 5×5 "
                                + "evenings up to two 20×20 nights."},
                        {"Choosing", "Step left or right on the Story Book row to pick a "
                                + "chapter, then open it."},
                        {"Endless", "A fresh picture whenever you like, at the size you "
                                + "choose, from 5×5 up to 20×20."},
                        {"Starting over", "Start story over, in the Cozy Corner, opens "
                                + "chapter one again. Endless keeps its count."}
                };
            default:
                return new String[][]{
                        {"Stuck?", "A hint lights up one square. Gentle guidance softly "
                                + "catches a square that isn't right."},
                        {"Is it saved?", "Every square, as you go. Turn the screen off and "
                                + "the picture is waiting tomorrow."},
                        {"Online?", "Never. CozyGrams has no internet access, no ads and "
                                + "collects nothing at all."},
                        {"Hard to see?", "Larger text, Extra contrast, Bolder cursors and "
                                + "Reduce motion live in the Cozy Corner."},
                        {"Ideas or bugs?", "Choose Send feedback in the Cozy Corner, or "
                                + "visit " + SettingsScene.FEEDBACK_URL + "."}
                };
        }
    }

    /** The controls page, in the names of whatever is in the room. */
    private static String[][] controls() {
        if (HudScene.touch()) {
            return new String[][]{
                    {"The pen", "Tap FILL or CROSS. The pen decides what touching the "
                            + "board does."},
                    {"Marking", "Tap a square, or drag along a row or column to mark a "
                            + "whole line at once."},
                    {"The other mark", "Hold a square to use the other mark, just once."},
                    {"Tiny squares", "Slide to aim the cursor, then tap it again or "
                            + "press MARK."},
                    {"Hint and menu", "HINT lights up one square. MENU opens the Cozy "
                            + "Corner, and ‹ steps back."}
            };
        }
        if (HudScene.remoteOnly()) {
            return new String[][]{
                    {"Moving", "The D-pad moves your cursor. Hold it to keep going; it "
                            + "wraps around the edges."},
                    {"OK", "Changes the square: fill, then cross, then clear again."},
                    {"Hold OK", "Lights up one square when you are stuck."},
                    {"Menu and Back", "Menu opens the Cozy Corner. Back steps out, one "
                            + "screen at a time."}
            };
        }
        return new String[][]{
                {"Moving", "D-pad or left stick. Hold to keep going; the cursor wraps "
                        + "around the edges."},
                {"A, B and X", "A fills a square. B or X crosses it out."},
                {"Y", "Lights up one square when you are stuck."},
                {"Menu and Back", "Menu or Start opens the Cozy Corner. Back steps out."},
                {"A keyboard", "Arrows or WASD, Enter to fill, X to cross, H for a hint, "
                        + "M for the menu."}
        };
    }

    /** Everything a page says, as one passage for a screen reader. */
    public static String spoken(int page) {
        int shown = Math.floorMod(page, PAGE_COUNT);
        StringBuilder words = new StringBuilder("How to play, page ")
                .append(shown + 1).append(" of ").append(PAGE_COUNT).append(". ")
                .append(title(shown)).append(". ");
        for (String[] entry : entries(shown)) {
            words.append(entry[0]).append(": ").append(entry[1]).append(' ');
        }
        words.append(HudScene.touch()
                ? "Tap a tab to turn the page."
                : "Left and right turn the page. Back closes.");
        return words.toString().trim();
    }

    // ---- Where things were drawn, for a finger --------------------------------------------

    private final float[][] tabRects = new float[PAGE_COUNT][4];
    private boolean tabsDrawn;
    private final float[] againRect = new float[4];
    private boolean againDrawn;
    private final float[] panel = new float[4];
    private float drawnScrollMax;
    private float drawnLineStep;

    /** The tab under a point, or -1. */
    public int tabAt(float x, float y) {
        if (!tabsDrawn) {
            return -1;
        }
        float slop = Theme.scale(6);
        for (int page = 0; page < PAGE_COUNT; page++) {
            float[] r = tabRects[page];
            if (x >= r[0] - slop && x <= r[2] + slop && y >= r[1] - slop && y <= r[3] + slop) {
                return page;
            }
        }
        return -1;
    }

    /** True when a point lands on the "take the tour again" button. */
    public boolean tourAgainAt(float x, float y) {
        float slop = Theme.scale(6);
        return againDrawn && x >= againRect[0] - slop && x <= againRect[2] + slop
                && y >= againRect[1] - slop && y <= againRect[3] + slop;
    }

    /** The panel as last drawn, for the back button to keep clear of. */
    public float[] panelRect(float width, float height) {
        panel[0] = helpInset(width);
        panel[1] = panelTop(height);
        panel[2] = width - helpInset(width);
        panel[3] = panelBottom(height);
        return panel;
    }

    /** How far the page can scroll, in pixels, as last drawn: zero when it all fits. */
    public float scrollMax() {
        return drawnScrollMax;
    }

    /** One line of the page, in pixels: how far a press of up or down scrolls. */
    public float lineStep() {
        return drawnLineStep;
    }

    // ---- Layout ----------------------------------------------------------------------

    private static float panelLeft(float width) {
        return Math.max(width * .085f, Theme.safeLeft(width));
    }

    private static float panelRight(float width) {
        return Math.min(width * .915f, Theme.safeRight(width));
    }

    /**
     * How far How to play's panel keeps in from each side. On a touch screen the back
     * button sits in the top-left corner, so the panel steps clear of it — on both sides,
     * so it stays centred — rather than letting the chevron cover the heading.
     */
    private static float helpInset(float width) {
        float inset = panelLeft(width);
        if (HudScene.touch()) {
            inset = Math.max(inset, Theme.safeLeft(width) + HudScene.backButtonHeight()
                    + Theme.scale(12));
        }
        return inset;
    }

    private static float panelTop(float height) {
        return Math.max(Theme.stageTop(height) + Theme.unitHeight() * Theme.SAFE_AREA,
                Theme.safeTop(height));
    }

    private static float panelBottom(float height) {
        return Math.min(Theme.stageBottom(height) - Theme.unitHeight() * Theme.SAFE_AREA,
                Theme.safeBottom(height));
    }

    // ---- Drawing How to play ---------------------------------------------------------

    public void draw(Canvas canvas, float width, float height, UiState ui, long now) {
        boolean bold = ui.highContrastOn;
        int page = Math.floorMod(ui.helpPage, PAGE_COUNT);
        float left = helpInset(width);
        float right = width - helpInset(width);
        float top = panelTop(height);
        float bottom = panelBottom(height);
        draw.panel(canvas, left, top, right, bottom, bold ? 244 : 232);

        float pad = Theme.scale(30);
        float innerLeft = left + pad;
        float innerRight = right - pad;
        float y = top + Theme.scale(18);

        // The heading, with where you are in the book of pages beside it in words.
        float titleSize = Theme.textSize(Theme.HEADING);
        float titleBase = y + titleSize * .80f;
        draw.shadowedText(canvas, "HOW TO PLAY", innerLeft, titleBase, titleSize, Theme.CREAM,
                Paint.Align.LEFT, true);
        float counterSize = Theme.textSize(Theme.CAPTION);
        draw.text(canvas, "PAGE " + (page + 1) + " OF " + PAGE_COUNT, innerRight,
                titleBase, counterSize, Theme.secondaryText(bold), Paint.Align.RIGHT, true);
        y = titleBase + titleSize * .25f + Theme.scale(12);

        // The tabs: the page in front is lit and outlined, the rest rest.
        float tabHeight = Math.max(Theme.textSize(Theme.CAPTION) * 1.9f, HudScene.touchMinPx());
        float tabGap = Theme.scale(10);
        float tabWidth = (innerRight - innerLeft - tabGap * (PAGE_COUNT - 1)) / PAGE_COUNT;
        float tabSize = Theme.textSize(Theme.CAPTION);
        for (int tab = 0; tab < PAGE_COUNT; tab++) {
            float tl = innerLeft + tab * (tabWidth + tabGap);
            float tr = tl + tabWidth;
            boolean lit = tab == page;
            float radius = tabHeight / 2;
            if (lit) {
                draw.roundRect(canvas, tl, y, tr, y + tabHeight, radius, Theme.PINK);
                draw.roundRectStroke(canvas, tl, y, tr, y + tabHeight, radius,
                        Theme.hairline(), Draw.withAlpha(Theme.CREAM, bold ? 255 : 215));
            } else {
                draw.roundRect(canvas, tl, y, tr, y + tabHeight, radius,
                        Draw.withAlpha(Theme.ROW_REST, bold ? 78 : 44));
                draw.roundRectStroke(canvas, tl, y, tr, y + tabHeight, radius,
                        Theme.keyline(), Draw.withAlpha(Theme.ROW_REST_STROKE, bold ? 110 : 54));
            }
            String label = TABS[tab];
            float size = draw.fit(label, tabSize, tabWidth - Theme.scale(16), true,
                    Theme.scale(14));
            draw.text(canvas, label, (tl + tr) / 2, y + tabHeight / 2 + draw.capCentreOffset(size),
                    size, lit ? Theme.textOn(Theme.PINK) : Theme.CREAM, Paint.Align.CENTER, true);
            tabRects[tab][0] = tl;
            tabRects[tab][1] = y;
            tabRects[tab][2] = tr;
            tabRects[tab][3] = y + tabHeight;
        }
        tabsDrawn = true;
        y += tabHeight + Theme.scale(14);

        // The footer is laid out from the bottom up, so the page gets whatever is between.
        float footSize = Theme.textSize(Theme.CAPTION);
        float footHeight = HudScene.touch()
                ? Math.max(footSize * 2.0f, HudScene.touchMinPx()) : footSize * 1.3f;
        float footBottom = bottom - Theme.scale(16);
        float footTop = footBottom - footHeight;

        // The page itself.
        float subSize = Theme.textSize(Theme.SUBHEAD);
        float subBase = y + subSize * .80f;
        draw.text(canvas, title(page), innerLeft, subBase, subSize, Theme.GOLD,
                Paint.Align.LEFT, true);
        float bodyTop = subBase + subSize * .30f + Theme.scale(10);
        float bodyBottom = footTop - Theme.scale(12);
        drawEntries(canvas, entries(page), innerLeft, innerRight, bodyTop, bodyBottom, ui, bold);
        // After the page, which is what knows whether it scrolls.
        drawHelpFooter(canvas, innerLeft, innerRight, footTop, footBottom, footSize, bold,
                drawnScrollMax > 0);
    }

    /**
     * The entries, two columns: the words to find it by on the left, the sentence wrapped
     * beside it. If a page is still taller than its room at the largest text size, it
     * scrolls under up and down (or a drag), and a thin bar says there is more.
     */
    private void drawEntries(Canvas canvas, String[][] entries, float left, float right,
                             float top, float bottom, UiState ui, boolean bold) {
        float size = Theme.textSize(Theme.BODY);
        // The lead column is as wide as this page's widest lead, within reason, so a page
        // of short words does not push its sentences halfway across the panel.
        float leadWidth = 0;
        for (String[] entry : entries) {
            leadWidth = Math.max(leadWidth, draw.measure(entry[0], size, true));
        }
        leadWidth = Math.min(leadWidth, (right - left) * .30f);
        float gap = Theme.scale(30);
        float bodyLeft = left + leadWidth + gap;
        float bodyWidth = right - Theme.scale(14) - bodyLeft;
        float lineHeight = size * 1.28f;
        float entryGap = size * .55f;

        List<String[]> wrapped = new ArrayList<>();
        float total = 0;
        for (String[] entry : entries) {
            String[] lines = wrap(entry[1], size, bodyWidth, false);
            wrapped.add(lines);
            total += lines.length * lineHeight + entryGap;
        }
        total -= entryGap;
        float room = bottom - top;
        drawnScrollMax = Math.max(0, total - room);
        drawnLineStep = lineHeight;
        float scroll = Math.max(0, Math.min(drawnScrollMax, ui.helpScroll));
        ui.helpScroll = scroll;

        canvas.save();
        canvas.clipRect(left - Theme.scale(4), top, right, bottom);
        float y = top - scroll;
        int ink = Draw.withAlpha(Theme.CREAM, bold ? 255 : 235);
        for (int i = 0; i < entries.length; i++) {
            String[] lines = wrapped.get(i);
            float leadSize = draw.fit(entries[i][0], size, leadWidth, true,
                    Theme.textSize(Theme.MIN_PROSE_SP) * .85f);
            draw.text(canvas, entries[i][0], left, y + size * .95f, leadSize,
                    Theme.PINK_LIGHT, Paint.Align.LEFT, true);
            for (int line = 0; line < lines.length; line++) {
                draw.text(canvas, lines[line], bodyLeft, y + size * .95f + line * lineHeight,
                        size, ink, Paint.Align.LEFT, false);
            }
            y += lines.length * lineHeight + entryGap;
        }
        canvas.restore();

        if (drawnScrollMax > 0) {
            float x = right - Theme.scale(5);
            float barWidth = Theme.scale(5);
            float thumb = room * room / total;
            float thumbTop = top + (room - thumb) * (scroll / drawnScrollMax);
            draw.roundRect(canvas, x, top, x + barWidth, bottom, barWidth / 2,
                    Draw.withAlpha(Theme.CREAM, bold ? 60 : 34));
            draw.roundRect(canvas, x, thumbTop, x + barWidth, thumbTop + thumb, barWidth / 2,
                    Draw.withAlpha(Theme.CREAM, bold ? 220 : 160));
        }
    }

    private void drawHelpFooter(Canvas canvas, float left, float right, float top,
                                float bottom, float size, boolean bold, boolean scrolls) {
        float centreY = (top + bottom) / 2;
        int text = Theme.secondaryText(bold);
        if (HudScene.touch()) {
            // A real button, at least 48dp tall, for the one thing on this screen a tap does
            // besides turning a page.
            float label = draw.measure(TOUR_AGAIN, size, true);
            float half = (bottom - top) / 2;
            float w = label + half * 2.4f;
            float bl = (left + right - w) / 2;
            draw.roundRect(canvas, bl, top, bl + w, bottom, half,
                    Draw.withAlpha(Theme.ROW_REST, bold ? 110 : 70));
            draw.roundRectStroke(canvas, bl, top, bl + w, bottom, half, Theme.hairline(),
                    Draw.withAlpha(Theme.CREAM, bold ? 220 : 150));
            draw.text(canvas, TOUR_AGAIN, (left + right) / 2,
                    centreY + draw.capCentreOffset(size), size, Theme.CREAM,
                    Paint.Align.CENTER, true);
            againRect[0] = bl;
            againRect[1] = top;
            againRect[2] = bl + w;
            againRect[3] = bottom;
            againDrawn = true;
            return;
        }
        againDrawn = false;
        String confirm = HomeScene.confirmName();
        String back = HudScene.backName();
        String turn = scrolls ? "Turn the page, scroll" : "Turn the page";
        String close = "Close";
        float gap = Theme.scale(9);
        float group = Theme.scale(24);
        float height;
        float total;
        float s = size;
        do {
            height = s * 1.08f;
            total = height + gap + draw.measure(turn, s, false) + group
                    + draw.keycapWidth(confirm, height) + gap
                    + draw.measure(TOUR_AGAIN, s, false) + group
                    + draw.keycapWidth(back, height) + gap + draw.measure(close, s, false);
            if (total <= right - left || s <= Theme.scale(18)) {
                break;
            }
            s = Math.max(Theme.scale(18), s * (right - left) / total);
        } while (true);
        float baseline = centreY + draw.capCentreOffset(s);
        float x = (left + right - total) / 2;
        draw.dpadKeycap(canvas, x, centreY, height, Theme.SOFT_TEXT);
        x += height + gap;
        draw.text(canvas, turn, x, baseline, s, text, Paint.Align.LEFT, false);
        x += draw.measure(turn, s, false) + group;
        draw.keycap(canvas, x, centreY, height, confirm,
                HudScene.remoteOnly() ? Theme.SOFT_TEXT : Theme.BUTTON_A);
        x += draw.keycapWidth(confirm, height) + gap;
        draw.text(canvas, TOUR_AGAIN, x, baseline, s, text, Paint.Align.LEFT, false);
        x += draw.measure(TOUR_AGAIN, s, false) + group;
        draw.keycap(canvas, x, centreY, height, back, Theme.SOFT_TEXT);
        x += draw.keycapWidth(back, height) + gap;
        draw.text(canvas, close, x, baseline, s, text, Paint.Align.LEFT, false);
    }

    /** Forgets every hit rectangle, for a frame that does not draw this scene. */
    public void forget() {
        tabsDrawn = false;
        againDrawn = false;
    }

    // ---- Words into lines ------------------------------------------------------------

    /**
     * Greedy word wrap at {@code size}. A single word wider than the lane is left whole on
     * its own line rather than broken mid-word; nothing on these pages is that long.
     */
    String[] wrap(String text, float size, float width, boolean strong) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String tried = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && draw.measure(tried, size, strong) > width) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(tried);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines.toArray(new String[0]);
    }
}
