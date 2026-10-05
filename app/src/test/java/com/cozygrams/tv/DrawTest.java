package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Paint;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The plumbing the text-fit guard stands on. Whether words actually fit can only be
 * measured by drawing them — {@code tools/preview/TextAudit.java}, run by the
 * {@code textFitAudit} task — but the lanes a scene declares and what the probe is told
 * are plain bookkeeping, and that is held here.
 */
public class DrawTest {

    @After
    public void noProbe() {
        Draw.setProbe(null);
    }

    /** Records what the probe hears. */
    private static final class Listener implements Draw.Probe {
        final List<String> heard = new ArrayList<>();
        final List<float[]> boxes = new ArrayList<>();
        int frames;

        @Override
        public void frame(Canvas canvas) {
            frames++;
        }

        @Override
        public void text(Canvas canvas, String value, float x, float y, float size, int color,
                         Paint.Align align, boolean strong, float spacing, float[] box) {
            heard.add(value);
            boxes.add(box == null ? null : box.clone());
        }

        @Override
        public void surface(Canvas canvas, float left, float top, float right, float bottom,
                            float radius, int color, int kind) {
            heard.add((kind == Draw.KEYCAP ? "cap " : "surface ") + (int) left);
        }
    }

    @Test
    public void theProbeHearsTextWithTheInnermostDeclaredLane() {
        Listener listener = new Listener();
        Draw.setProbe(listener);
        Draw draw = new Draw();
        Canvas canvas = new Canvas();
        draw.beginFrame(canvas);
        draw.text(canvas, "loose", 0, 0, 10, 0xffffffff, Paint.Align.LEFT, false);
        draw.beginBox(1, 2, 3, 4);
        draw.beginBox(5, 6, 7, 8);
        draw.text(canvas, "inner", 0, 0, 10, 0xffffffff, Paint.Align.LEFT, false);
        draw.endBox();
        draw.text(canvas, "outer", 0, 0, 10, 0xffffffff, Paint.Align.LEFT, false);
        draw.endBox();

        assertEquals(1, listener.frames);
        assertNull(listener.boxes.get(0));
        assertArrayEquals(new float[]{5, 6, 7, 8}, listener.boxes.get(1), 0);
        assertArrayEquals(new float[]{1, 2, 3, 4}, listener.boxes.get(2), 0);
        assertEquals(0, draw.openBoxes());
    }

    @Test
    public void surfacesAndButtonCapsAreReported() {
        Listener listener = new Listener();
        Draw.setProbe(listener);
        Draw draw = new Draw();
        Canvas canvas = new Canvas();
        draw.roundRect(canvas, 10, 0, 20, 10, 2, 0xffffffff);
        draw.dpadKeycap(canvas, 30, 5, 10, 0xffffffff);
        assertTrue(listener.heard.contains("surface 10"));
        assertTrue(listener.heard.contains("cap 30"));
    }

    @Test
    public void aShadowIsNotASecondLine() {
        Listener listener = new Listener();
        Draw.setProbe(listener);
        new Draw().shadowedText(new Canvas(), "title", 0, 0, 10, 0xffffffff,
                Paint.Align.CENTER, true);
        assertEquals(1, listener.heard.size());
    }

    @Test
    public void aLaneLeftOpenFailsTheNextFrameUnderTheProbe() {
        Draw.setProbe(new Listener());
        Draw draw = new Draw();
        draw.beginBox(0, 0, 1, 1);
        try {
            draw.beginFrame(new Canvas());
            fail("an open lane should be refused");
        } catch (IllegalStateException expected) {
            assertEquals(0, draw.openBoxes());
        }
    }

    @Test(expected = IllegalStateException.class)
    public void closingALaneThatWasNeverOpenedIsRefused() {
        new Draw().endBox();
    }

    @Test
    public void wrapKeepsEveryWord() {
        // Every measurement is zero here, so it all fits on one line; what matters is that
        // nothing is lost on the way.
        String[] lines = new Draw().wrap("one  two three", 10, 100, false);
        assertArrayEquals(new String[]{"one two three"}, lines);
    }
}
