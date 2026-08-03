package com.cozygrams.tv;

/**
 * Draws the endless boards.
 *
 * <p>Every board is a deterministic function of three small numbers pulled out of the
 * seed - which <em>subject</em> (a cat, a mug, a mitten), and which <em>look</em>
 * (mirrored or not, which pose it strikes, whether a little companion joins it) - plus
 * the board size. That keeps {@code generate} pure and repeatable, and it keeps the whole
 * universe of boards finite and small enough that the unit tests can check every single
 * one.
 *
 * <p>Subjects are painted with shape primitives on a normalised canvas rather than
 * sampled from a formula, so a mug is a mug at 5x5 and still a mug at 20x20 - and the
 * bigger boards get <em>holes</em> (a handle to hook a finger in, a gap between two
 * slats of pastry, the middle lifted out of a heart) rather than more solid squares. That
 * is the difference between a twenty-square board and a five-square board with more
 * squares: an inked ratio around 0.46 gives two or three clue groups to a line, where the
 * 0.57 this generator used to produce gave one long run and handed away most of the grid
 * on the first sweep.
 *
 * <p>After painting, every board is tidied (no lonely specks, no pinholes) and then run
 * through {@link NonogramSolver}: if pure line logic cannot finish the board, single
 * boundary squares are nudged until it can. The player never has to guess. Because the
 * whole pipeline costs a fraction of a millisecond, {@link #compose} does not settle for
 * the first framing it gets - it draws several, scores them, and keeps the one that reads
 * best.
 */
public final class PuzzleGenerator {

    private static final String[] NAMES = {
        "Sweetheart", "Sleepy Kitty", "Our Little Home", "Cocoa Date",
        "Moonlit Garden", "Love in Bloom", "Starlight Wish", "Love Birds",
        "Teapot for Two", "Bedtime Story", "Candlelight", "Rain on the Window",
        "Sleepy Fox", "Cozy Mitten", "Fresh Baked Pie", "Knitted Sweater",
        "Little Owl", "Ball of Yarn", "Umbrella Weather", "Windowsill Plant",
        "Love Letter", "Sweet Cupcake", "Paper Lantern", "Autumn Leaf"
    };

    /**
     * Mirrored or not (2) x which of three poses it strikes (3) x whether a little
     * companion joins it (2).
     *
     * <p>This used to be 2 x 3 accents x 2 poses, and it under-delivered badly: the pose
     * branches were all gated at eleven squares and up, so at the default 10x10 eighteen
     * of the twenty-four subjects drew the same picture for all twelve looks. Three poses
     * that change the silhouette at every size beat three stickers that only ever landed
     * on 4 boards in 48.
     */
    static final int VARIANTS = 12;

    /** How many single squares the fairness pass may nudge before giving up. */
    private static final int MAX_NUDGES = 18;

    /**
     * How many boards {@link #makeFair} could not rescue with nudges alone and had to
     * fall back to the hand-drawn portrait for. Package-visible telemetry, not app API:
     * it is zero across the whole deck today, and {@code PuzzleGeneratorTest} asserts
     * that, so the day someone adds a subject that fights the solver it is a loud test
     * failure rather than a quiet guessy board on somebody's television.
     */
    static int rescuedBoards;

    /**
     * How far a silhouette may be pulled out of shape while it is being fitted to the
     * board. Every subject is enlarged until its long side touches both edges; its short
     * side is then stretched too, but only by this much, because a moon squashed square
     * stops reading as a moon. See {@link #fitted}.
     */
    private static final double STRETCH = 1.34;

    /**
     * How much of the board a silhouette may be enlarged into. Filling the frame is only
     * worth having while the result is still a picture: past about two thirds inked, a
     * solid subject - an envelope, a sleeping cat's face - stops reading as anything and
     * the clues turn into a column of twenties. Subjects that dense are enlarged as far as
     * this allows and no further. See {@link #fitted}.
     *
     * <p>This is an enlargement guard, not a density dial. Lowering it does not thin a
     * board out, it shrinks the picture away from the border: measured over the whole
     * deck, dropping it from 0.70 to 0.52 moved mean fill at 20x20 only from 0.570 to
     * 0.512 while nearly tripling the number of boards with a completely blank clue line,
     * from 382 to 1065. Thinness has to come from holes in the subject, which is where
     * the per-subject detail gates below spend their effort.
     */
    private static final double DENSEST = 0.70;

    /** How many times {@link #fitted} measures the silhouette and repaints it larger. */
    private static final int FIT_PASSES = 5;

    /**
     * The share of the board a finished picture wants to ink. Nothing forces it - it is
     * the target {@link #legibility} scores framings against - but a board near it reads
     * as a drawing and gives a line two or three clue groups instead of one long run.
     */
    private static final double IDEAL_FILL = .46;

    private PuzzleGenerator() {
    }

    public static Puzzle generate(long seed, int size) {
        if (size < 5 || size > 20) {
            throw new IllegalArgumentException("size must be 5..20");
        }
        int subject = Math.floorMod(seed, NAMES.length);
        int variant = Math.floorMod(Math.floorDiv(seed, NAMES.length), VARIANTS);
        return compose(subject, variant, size);
    }

    /** How many different things the endless deck can draw. */
    public static int subjectCount() {
        return NAMES.length;
    }

    public static String subjectName(int subject) {
        return NAMES[Math.floorMod(subject, NAMES.length)];
    }

    /**
     * Builds one board directly. Package-visible so the tests can sweep every look.
     *
     * <p>Painting, tidying and fairness-checking one framing costs about a fifth of a
     * millisecond against a five millisecond budget, so rather than shipping whatever the
     * first framing produced this draws four and keeps the most legible. The candidates
     * are evaluated in a fixed order and {@link #legibility} is a pure function of the
     * finished grid, so the same seed still gives the same board.
     */
    static Puzzle compose(int subject, int variant, int size) {
        int subj = Math.floorMod(subject, NAMES.length);
        if (size <= 7) {
            return new Puzzle(finish(subj, variant, size, null).cell, NAMES[subj]);
        }
        double[] view = new double[4];
        fitted(subj, variant, size, view);
        boolean[][] best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int framing = 0; framing < FRAMINGS; framing++) {
            Ink ink = finish(subj, variant, size, aimed(subj, variant, size, view, framing));
            int score = legibility(ink);
            if (score > bestScore) {
                bestScore = score;
                best = ink.cell;
            }
        }
        return new Puzzle(best, NAMES[subj]);
    }

    /**
     * Paints the subject through a ready-made viewport (or, for the little boards, with
     * no viewport at all) and runs everything that turns a painted shape into a board:
     * the mirror, the companion, the tidy-up and the fairness pass.
     */
    private static Ink finish(int subject, int variant, int n, double[] view) {
        Ink ink = new Ink(n);
        if (view != null) {
            ink.frame(view[0], view[1], view[2], view[3]);
        }
        paint(ink, subject, variant, n);
        if (isMirrored(variant)) {
            ink.flip();
        }
        stampCompanion(ink, subject, accentOf(variant));
        ink.rescueIfBlank();
        tidy(ink);
        makeFair(ink, subject, poseOf(variant));
        ink.centre();
        return ink;
    }

    // ---- Framing ------------------------------------------------------------------------

    /** How many framings {@link #compose} draws before choosing one. */
    private static final int FRAMINGS = 4;

    /**
     * Works out the viewport for one candidate framing.
     *
     * <p>Framing 0 is exactly what the fitting passes converged on, which is what this
     * generator used to ship unconditionally. The rest all end with an <em>edge lock</em>:
     * the silhouette is measured once more and re-framed so it touches all four borders to
     * the square. That is what closes the blank clue lines - 382 of the 1,728 boards at
     * fifteen squares and up used to have at least one row or column reading "0", and 92
     * of them had a complete one-square dead frame. Framing 1 is the edge lock on its own;
     * 2 crops in a tenth first, for a bolder picture; 3 nudges the sampling grid half a
     * square each way first, which changes nothing about the shape but everything about
     * which squares it lands on - often the difference between a clean silhouette and one
     * with a stray two-square nub.
     */
    private static double[] aimed(int subject, int variant, int n, double[] converged,
            int framing) {
        double[] view = converged.clone();
        if (framing == 0) {
            return view;
        }
        if (framing == 2) {
            zoom(view, 1.10);
        } else if (framing == 3) {
            shift(view, .5 / n, .5 / n);
        }
        Ink probe = new Ink(n);
        probe.frame(view[0], view[1], view[2], view[3]);
        paint(probe, subject, variant, n);
        // Twice, because re-framing re-samples: a shape stretched to touch the border can
        // land a square short of it once the squares are counted again. The second pass
        // measures what the first one actually produced and closes the last square.
        //
        // The probe is tidied before it is measured, because that is what the finished
        // board will be. A cat's ear comes to a single square at eight or nine squares
        // across, tidy() sweeps single squares away as noise, and the row it was holding
        // out to the border went blank - which is how every 8x8 kitty ended up with an
        // empty first row and a clue reading "0".
        for (int lock = 0; lock < 2; lock++) {
            tidy(probe);
            edgeLock(probe, view);
            probe = new Ink(n);
            probe.frame(view[0], view[1], view[2], view[3]);
            paint(probe, subject, variant, n);
        }
        return view;
    }

    /** Scales the viewport about the middle of the board. */
    private static void zoom(double[] view, double factor) {
        view[0] = .5 + (view[0] - .5) * factor;
        view[1] *= factor;
        view[2] = .5 + (view[2] - .5) * factor;
        view[3] *= factor;
    }

    /** Slides the viewport, which moves the sampling grid under the shape. */
    private static void shift(double[] view, double du, double dv) {
        view[0] += du;
        view[2] += dv;
    }

    /**
     * Re-frames {@code view} so that what {@code probe} painted would touch all four
     * borders exactly. Board coordinate {@code b} of shape coordinate {@code u} is
     * {@code off + u * scl}; mapping the measured span onto 0..1 is one multiply and one
     * add per axis.
     */
    private static void edgeLock(Ink probe, double[] view) {
        int[] box = probe.bounds();
        if (box == null) {
            return;
        }
        double n = probe.n;
        double scaleU = n / (box[2] - box[0] + 1);
        double scaleV = n / (box[3] - box[1] + 1);
        view[0] = scaleU * view[0] - scaleU * box[0] / n;
        view[1] *= scaleU;
        view[2] = scaleV * view[2] - scaleV * box[1] / n;
        view[3] *= scaleV;
    }

    /**
     * Paints the subject at a size that actually uses the board, and reports the viewport
     * it settled on.
     *
     * <p>A subject is described once, in board-relative coordinates, and nothing in that
     * description knows how tall a pie or how wide an envelope happens to come out. Left
     * alone, a 20x20 pie filled twelve of its twenty rows and left the other eight blank -
     * a player who asked for a big puzzle got a small one with a wide frame around it.
     *
     * <p>So the silhouette is painted, measured, and painted again through a viewport that
     * scales and centres it: the long side is enlarged until it touches both edges, and
     * the short side is pulled out with it, up to {@link #STRETCH}. Because painting is a
     * <em>vector</em> description rather than a bitmap, enlarging costs no detail - the
     * eyes, windows and whiskers grow with the shape instead of turning into stair-steps.
     * Measuring again after each repaint catches the shapes that answer back: a star that
     * only lands when it has room, an ear clipped by the border.
     *
     * <p>The 5x5, 6x6 and 7x7 boards are hand-drawn portraits that already touch all four
     * edges (see {@link #TINY}), so they are painted once and left alone.
     */
    private static Ink fitted(int subject, int variant, int n, double[] view) {
        double offU = 0;
        double sclU = 1;
        double offV = 0;
        double sclV = 1;
        // The span each axis is aiming for, 0..1 of the board, and the span the subject
        // covers unaided — the floor the density clamp below is never allowed to go under.
        double targetW = 1;
        double targetH = 1;
        double naturalW = 1;
        double naturalH = 1;
        Ink ink = new Ink(n);
        for (int pass = 0; pass < FIT_PASSES; pass++) {
            if (pass > 0) {
                ink = new Ink(n);
            }
            ink.frame(offU, sclU, offV, sclV);
            paint(ink, subject, variant, n);
            int[] box = ink.bounds();
            if (box == null) {
                break;                       // nothing painted; rescueIfBlank will cope
            }
            int spanX = box[2] - box[0] + 1;
            int spanY = box[3] - box[1] + 1;
            double curW = spanX / (double) n;
            double curH = spanY / (double) n;
            double inked = ink.filled() / (double) (n * n);
            if (pass == 0) {
                naturalW = curW;
                naturalH = curH;
                // The most either axis may be multiplied by: enough to make the *long* side
                // touch both edges, times STRETCH for how far out of shape the short side
                // may be pulled along with it.
                double grow = Math.min(1 / curW, 1 / curH) * STRETCH;
                // The inner min looks redundant and algebraically is: curW * min(1/curW,
                // grow) reduces to min(1, curW * grow), which the outer min then repeats.
                // It is kept because it is not redundant in *doubles* — curW * (1 / curW)
                // need not be exactly 1.0, and the difference decides an (int) cast four
                // lines down, so simplifying it can re-roll a boundary square on some of
                // the 4,608 deck boards. Written out, not tidied away.
                targetW = Math.min(1, curW * Math.min(1 / curW, grow));
                targetH = Math.min(1, curH * Math.min(1 / curH, grow));
            }
            // Enlarging multiplies the inked area, so a subject that is already mostly
            // solid is let out less than a spindly one - the difference between a heart
            // that grows until it touches the border and an envelope that would have
            // become a slab. This reads the density that actually came out rather than
            // predicting it, and never asks for less than the subject covers unaided.
            boolean solid = inked > DENSEST;
            if (solid) {
                double ease = Math.sqrt(DENSEST / inked);
                targetW = Math.max(naturalW, Math.min(targetW, curW * ease));
                targetH = Math.max(naturalH, Math.min(targetH, curH * ease));
            }
            if (!solid && spanX >= (int) (targetW * n) && spanY >= (int) (targetH * n)) {
                break;
            }
            // Re-aim the viewport at what actually came out, so shapes that answer back -
            // a star that will not land, an ear the border clipped - still converge.
            double midU = (box[0] + box[2] + 1) / (2.0 * n);
            double midV = (box[1] + box[3] + 1) / (2.0 * n);
            double growW = targetW / curW;
            double growH = targetH / curH;
            offU = .5 - (midU - offU) * growW;
            sclU *= growW;
            offV = .5 - (midV - offV) * growH;
            sclV *= growH;
        }
        view[0] = offU;
        view[1] = sclU;
        view[2] = offV;
        view[3] = sclV;
        return ink;
    }

    // ---- Scoring ------------------------------------------------------------------------

    /**
     * How much a finished board looks like the thing it is meant to be, in one number.
     *
     * <p>Everything here was a real complaint about a real board. Fill wants to sit near
     * {@link #IDEAL_FILL}, because a board at 0.65 is a slab and a board at 0.25 is a
     * doodle. Small detached pieces are the "two floating ears above a mug" bug. A blank
     * clue line is four characters of nothing in the gutter and a row of board that is
     * decided before anybody sits down. Clue groups are the puzzle itself: a line with
     * three runs asks a question, a line with one full-length run answers itself. And a
     * board the fairness pass had to nudge is a board whose picture was quietly edited, so
     * a framing that needs no nudging beats one that needs six.
     */
    private static int legibility(Ink k) {
        int n = k.n;
        double fill = k.filled() / (double) (n * n);
        double points = -Math.abs(fill - IDEAL_FILL) * 300;
        points -= strayCells(k.cell, n) * 10;
        points -= blankLines(k.cell, n) * 120;
        points += Math.min(2.6, clueGroupsPerLine(k.cell, n)) * 30;
        points -= dictationLines(k.cell, n) * 55;
        points -= k.nudges * 6;
        return (int) Math.round(points);
    }

    /**
     * Lines that are a copy of the line before them, counted only where the line has enough
     * groups to have been worth solving.
     *
     * <p>A blank line gives nothing away and this file already scores it. Its opposite costs
     * just as much and was not scored at all: ten consecutive rows reading {@code 1 1 1 1 1}
     * are one deduction and nine transcriptions. Measured across the whole deck - 24 subjects
     * by 12 variants at each size - boards holding a run of four or more identical fragmented
     * lines numbered 4/288 at 10x10, 20/288 at 15x15 and 52/288 at 20x20, and they were
     * concentrated in the subjects painted with tall parallel slots: Rain on the Window, Paper
     * Lantern, Fresh Baked Pie. The finished picture is a waffle rather than rain.
     *
     * <p>Weighted at 55 - between a stray cell and a blank line - because a repeat is a
     * genuine loss of puzzle but, unlike a blank line, the line above it did at least teach
     * something. {@code compose} draws four framings and keeps the best, so this is what lets
     * it walk away from the one that came out as dictation.
     *
     * <p>The scorer can only choose between the framings it is given, which is why the three
     * subjects above also had their slots staggered at the paint stage - see {@link #window},
     * {@link #lantern} and {@link #pie}. A penalty cannot fix a silhouette that is a grid of
     * parallel bars from every angle.
     */
    private static int dictationLines(boolean[][] g, int n) {
        return repeats(g, n, true) + repeats(g, n, false);
    }

    /**
     * Copies that extend a run to three lines or more.
     *
     * <p>Deliberately not "every line equal to the one above it". Two neighbouring rows of a
     * drawing are often identical - that is what a straight edge is - and charging for them
     * made the scorer prefer whichever framing had the fewest fragmented lines at all, which
     * is the solid one: it took Sleepy Kitty at 8x8 to 50 squares of 64 and straight through
     * the slab bar {@code PuzzleGeneratorTest} holds every board to. Charging from the third
     * copy leaves an edge alone and still catches the thing that is actually dictation.
     */
    private static int repeats(boolean[][] g, int n, boolean rows) {
        int copies = 0;
        int run = 1;
        for (int i = 1; i < n; i++) {
            if (groupsIn(g, n, i, rows) >= 3 && sameLine(g, n, i, i - 1, rows)) {
                run++;
                if (run >= 3) {
                    copies++;
                }
            } else {
                run = 1;
            }
        }
        return copies;
    }

    private static int groupsIn(boolean[][] g, int n, int i, boolean rows) {
        int groups = 0;
        boolean run = false;
        for (int j = 0; j < n; j++) {
            boolean on = rows ? g[i][j] : g[j][i];
            if (on && !run) {
                groups++;
            }
            run = on;
        }
        return groups;
    }

    /** Whether two lines are inked identically, which is what makes one a transcription. */
    private static boolean sameLine(boolean[][] g, int n, int a, int b, boolean rows) {
        for (int j = 0; j < n; j++) {
            if ((rows ? g[a][j] : g[j][a]) != (rows ? g[b][j] : g[j][b])) {
                return false;
            }
        }
        return true;
    }

    /**
     * How many squares belong to a piece smaller than five squares that is not the main
     * silhouette - a nub, a stray steam puff, a detached branch.
     */
    private static int strayCells(boolean[][] g, int n) {
        boolean[][] seen = new boolean[n][n];
        int[] stack = new int[n * n];
        int biggest = 0;
        int small = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (!g[y][x] || seen[y][x]) {
                    continue;
                }
                int top = 0;
                int size = 0;
                stack[top++] = y * n + x;
                seen[y][x] = true;
                while (top > 0) {
                    int at = stack[--top];
                    int ax = at % n;
                    int ay = at / n;
                    size++;
                    if (ax > 0 && g[ay][ax - 1] && !seen[ay][ax - 1]) {
                        seen[ay][ax - 1] = true;
                        stack[top++] = ay * n + ax - 1;
                    }
                    if (ax + 1 < n && g[ay][ax + 1] && !seen[ay][ax + 1]) {
                        seen[ay][ax + 1] = true;
                        stack[top++] = ay * n + ax + 1;
                    }
                    if (ay > 0 && g[ay - 1][ax] && !seen[ay - 1][ax]) {
                        seen[ay - 1][ax] = true;
                        stack[top++] = (ay - 1) * n + ax;
                    }
                    if (ay + 1 < n && g[ay + 1][ax] && !seen[ay + 1][ax]) {
                        seen[ay + 1][ax] = true;
                        stack[top++] = (ay + 1) * n + ax;
                    }
                }
                if (size < 5) {
                    small += size;
                }
                biggest = Math.max(biggest, size);
            }
        }
        return biggest < 5 ? 0 : small;
    }

    /** How many rows and columns are completely blank, so their clue reads "0". */
    private static int blankLines(boolean[][] g, int n) {
        int blank = 0;
        for (int i = 0; i < n; i++) {
            boolean row = false;
            boolean col = false;
            for (int j = 0; j < n; j++) {
                row |= g[i][j];
                col |= g[j][i];
            }
            if (!row) {
                blank++;
            }
            if (!col) {
                blank++;
            }
        }
        return blank;
    }

    /** The average number of clue groups in a row or a column - the puzzle's depth. */
    private static double clueGroupsPerLine(boolean[][] g, int n) {
        int groups = 0;
        for (int i = 0; i < n; i++) {
            boolean runRow = false;
            boolean runCol = false;
            for (int j = 0; j < n; j++) {
                if (g[i][j] && !runRow) {
                    groups++;
                }
                runRow = g[i][j];
                if (g[j][i] && !runCol) {
                    groups++;
                }
                runCol = g[j][i];
            }
        }
        return groups / (2.0 * n);
    }

    private static boolean isMirrored(int variant) {
        return (Math.floorMod(variant, VARIANTS) & 1) == 1;
    }

    private static int poseOf(int variant) {
        return (Math.floorMod(variant, VARIANTS) >> 1) % 3;
    }

    private static int accentOf(int variant) {
        return Math.floorMod(variant, VARIANTS) / 6;
    }

    /**
     * The smallest boards get hand-drawn icons instead of painted shapes. Twenty-five
     * squares is not enough room for a spout or a whisker, so each subject has three 5x5
     * portraits - one per pose - that are scaled up for 6x6 and 7x7 boards. Each is locked
     * once drawn, so nothing tidies away the one square that makes a mug a mug.
     *
     * <p>Every portrait touches all four edges and leaves no row or column empty. Nearest-
     * neighbour scaling preserves both, so a 5x5, 6x6 or 7x7 board is filled corner to
     * corner without any fitting pass - which is just as well, because at this size one
     * blank margin row is a fifth of the puzzle.
     *
     * <p>All seventy-two are uniquely line-solvable at 5, 6 <em>and</em> 7, which is not
     * a free property: the 6x6 upscale doubles the middle row and column and the 7x7 one
     * doubles the second and fourth, and several perfectly good icons stop being solvable
     * when they are stretched that way. The old set had eight portraits that did not
     * survive it - the fairness pass then quietly rubbed out one of the fox's ears and
     * turned its muzzle into a solid bar - so every one of these was checked at all three
     * sizes before it was allowed in. {@code PuzzleGeneratorTest} keeps checking.
     */
    private static final String[][] TINY = {
        // heart: a deep-clefted one, a wide one, one tipped over.
        //
        // Pose 0 used to be the plain symmetric heart with a one-square shine, which is one
        // square from the storybook's "First Heart" - and the book, not the deck, was the
        // one bent out of shape to keep them apart. The cleft runs a row deeper here
        // instead: three squares of daylight between the two, and both are honest hearts.
        {"##.##" + "##.##" + "#####" + ".###." + "..#..",
         "#.#.#" + "#####" + ".###." + ".###." + "..#..",
         "##.#." + "#####" + "####." + ".##.." + ".#..."},
        // kitty: facing you, head tilted, sitting up with two paws
        {"#...#" + "#####" + "#.#.#" + ".###." + ".###.",
         "#..#." + "#####" + "#.#.#" + ".###." + ".###.",
         "#...#" + "#####" + "#.#.#" + "#####" + ".#.#."},
        // home: door in the middle, door to one side, with a chimney
        {"..#.." + ".###." + "#####" + "#.#.#" + "##.##",
         "..#.." + ".###." + "#####" + "#.#.#" + "###.#",
         "#.#.." + "####." + "#####" + "#.#.#" + "##.##"},
        // mug: one wisp of steam, two, and a mug on its saucer
        {".#..." + "####." + "###.#" + "###.#" + ".###.",
         "#.#.." + "####." + "###.#" + "###.#" + ".###.",
         "..#.." + "####." + "###.#" + "###.#" + "####."},
        // moon: a crescent, a crescent the other way, one with two stars
        {".####" + "##..." + "##..." + "##..." + ".####",
         "####." + "...##" + "....#" + "...##" + "####.",
         ".####" + "##..#" + "##..." + "##.#." + ".####"},
        // flower: five petals, a tulip, one leaning over
        {".###." + "#####" + ".###." + "..#.." + ".###.",
         "#...#" + "#.#.#" + "#####" + "..#.." + ".###.",
         ".#.#." + "#####" + ".###." + "..#.." + "..###"},
        // star: a plain one, a sparkle, a five-pointed one
        {"..#.." + ".###." + "#####" + ".###." + "..#..",
         "..#.." + "#.#.#" + "#####" + "#.#.#" + "..#..",
         "..#.." + ".###." + "#####" + "##.##" + ".#.#."},
        // birds: perched, leaning the other way, with both feet down
        {"..##." + ".###." + "#####" + "..#.." + "#####",
         ".##.." + "####." + "#####" + "..#.." + "#####",
         "..##." + ".####" + "#####" + ".#.#." + "#####"},
        // teapot: spout and handle, a low spout, a raised one
        {"..#.." + ".###." + "#####" + ".###." + ".###.",
         "..#.." + ".###." + "#####" + "####." + ".###.",
         ".#..." + "####." + "#####" + ".###." + ".###."},
        // book: open with a ribbon, open flat, standing up
        {".#.#." + "##.##" + "##.##" + "##.##" + ".##..",
         ".#.#." + "##.##" + "##.##" + "#####" + "..#..",
         "##.##" + "##.##" + "##.##" + ".#.#." + ".###."},
        // candle: a small flame, a tall one, and one running with wax
        {"..#.." + "..#.." + ".###." + ".###." + "#####",
         "..#.." + ".###." + ".###." + ".###." + "#####",
         "..#.." + "..#.." + ".###." + ".####" + "#####"},
        // window: four panes, two big ones, rain on the sill
        {"#####" + "#.#.#" + "#.#.#" + "#####" + "..#..",
         "#####" + "#...#" + "#####" + "#...#" + ".###.",
         "#####" + "#.#.#" + "#.#.#" + "#####" + ".#.#."},
        // fox: ears up and a pointed chin, eyes open, glancing aside
        {"##.##" + "#####" + ".###." + ".###." + "..#..",
         "##.##" + "#####" + ".#.#." + ".###." + "..#..",
         "##.##" + "####." + "#.###" + ".###." + "..#.."},
        // mitten: thumb out, a long cuff, a short one
        {".##.." + "####." + "#####" + ".###." + ".###.",
         ".##.." + "#####" + "####." + ".###." + ".###.",
         ".##.." + "####." + "#####" + ".###." + ".##.."},
        // pie: a latticed crust, one steam vent, two
        {".###." + "#.#.#" + "##.##" + "#####" + ".###.",
         "..#.." + ".###." + "#.#.#" + "#####" + ".###.",
         ".#.#." + ".###." + "#.#.#" + "#####" + ".###."},
        // sweater: sleeves out, a ribbed hem, long sleeves
        {"#.#.#" + "#####" + ".###." + ".###." + ".###.",
         "#.#.#" + "#####" + ".###." + ".###." + ".#.#.",
         "#...#" + "#####" + "#####" + ".###." + ".###."},
        // owl: tufts down, tufts up, wings folded
        {".###." + "#.#.#" + "#####" + ".###." + ".#.#.",
         ".#.#." + "#####" + "#.#.#" + ".###." + ".#.#.",
         ".###." + "#.#.#" + "#####" + "#####" + ".#.#."},
        // yarn: a neat ball, one trailing a thread, one half unwound
        {"..##." + ".###." + "#####" + ".###." + "..#..",
         "..##." + ".###." + "#####" + "###.#" + ".###.",
         ".###." + "#.#.#" + "#####" + ".###." + "..##."},
        // umbrella: up, wide open, and one catching the rain
        {".###." + "#####" + "..#.." + "..#.." + ".##..",
         ".###." + "#####" + "#####" + "..#.." + ".##..",
         "#.#.#" + "#####" + "..#.." + "..#.." + ".##.."},
        // plant: one shoot, two, and one leaning to the light
        {"..#.." + ".###." + "..#.." + "#####" + ".###.",
         ".#.#." + ".###." + "..#.." + "#####" + ".###.",
         "##..." + ".###." + "..#.." + "#####" + ".###."},
        // letter: sealed with a heart, with a stamp, with a written line
        {"#####" + "##.##" + "#####" + ".###." + "..#..",
         "#####" + "#.#.#" + "##.##" + "#####" + "..#..",
         "#####" + "##.##" + "#.#.#" + "#####" + "..#.."},
        // cupcake: pleated wrapper, a short wrapper, a cherry either side
        {"..#.." + ".###." + "#####" + "#.#.#" + "#.#.#",
         "..#.." + ".###." + "#####" + "#.#.#" + ".###.",
         ".#.#." + ".###." + "#####" + "#.#.#" + "#.#.#"},
        // lantern: two ribs, a narrow one, a wide one
        {"..#.." + "#####" + "#.#.#" + "#####" + "..#..",
         "..#.." + ".###." + "#.#.#" + "#####" + "..#..",
         "..#.." + "#####" + "##.##" + "#####" + "..#.."},
        // leaf: a central vein, one leaning, one with a side vein
        {".###." + "##.##" + "#####" + ".###." + "..#..",
         ".##.." + "####." + "#####" + ".###." + "..#..",
         ".###." + "#.###" + "#####" + ".###." + "..#.."}
    };

    // ---- The subjects -----------------------------------------------------------------

    private static void paint(Ink k, int subject, int variant, int n) {
        int pose = poseOf(variant);
        if (n <= 7) {
            tiny(k, subject, pose, n);
            return;
        }
        switch (subject) {
            case 0: heart(k, n, pose); break;
            case 1: kitty(k, n, pose); break;
            case 2: home(k, n, pose); break;
            case 3: mug(k, n, pose); break;
            case 4: moon(k, n, pose); break;
            case 5: flower(k, n, pose); break;
            case 6: star(k, n, pose); break;
            case 7: birds(k, n, pose); break;
            case 8: teapot(k, n, pose); break;
            case 9: book(k, n, pose); break;
            case 10: candle(k, n, pose); break;
            case 11: window(k, n, pose); break;
            case 12: fox(k, n, pose); break;
            case 13: mitten(k, n, pose); break;
            case 14: pie(k, n, pose); break;
            case 15: sweater(k, n, pose); break;
            case 16: owl(k, n, pose); break;
            case 17: yarn(k, n, pose); break;
            case 18: umbrella(k, n, pose); break;
            case 19: plant(k, n, pose); break;
            case 20: letter(k, n, pose); break;
            case 21: cupcake(k, n, pose); break;
            case 22: lantern(k, n, pose); break;
            default: leaf(k, n, pose); break;
        }
    }

    /** Scales one of the subject's 5x5 portraits up to a 5, 6 or 7 square board. */
    private static void tiny(Ink k, int subject, int pose, int n) {
        String art = TINY[subject][pose];
        for (int y = 0; y < n; y++) {
            int sy = (y * 2 + 1) * 5 / (n * 2);
            for (int x = 0; x < n; x++) {
                int sx = (x * 2 + 1) * 5 / (n * 2);
                k.cell[y][x] = art.charAt(sy * 5 + sx) == '#';
                k.locked[y][x] = true;
            }
        }
    }

    private static void heart(Ink k, int n, int pose) {
        double tip = pose == 2 ? -.20 : 0;                 // pose 2 tips the point over
        heartShape(k, .5, .13, .47, .84, tip, true);
        if (pose == 1) {
            k.poly(false, .42, .10, .58, .10, .5, .44);    // pose 1 opens a deeper cleft
        }
        // There used to be "a little shine" here - wipeOval(.28, .38, .08, .08) from nine
        // squares up. A radius of .08 resolves to well under one square on every board that
        // gate let through, so it never came out as a round highlight: at 10x10 it was a
        // two-square horizontal slot at row 3, off-centre, with no mirror partner on the
        // right-hand lobe. On the one picture the win card assembles, under the words "Look
        // what happened", a heart with an unmatched notch in it reads as damage rather than
        // as gloss. A symmetric subject should not be thinned by an asymmetric hole; the
        // heart's thinning is the heart-shaped hole below, which is mirrored by
        // construction.
        if (n >= 10) {
            // A heart with a heart inside it. Solid, a heart past a dozen squares is a
            // slab - seventeen of a 20x20's twenty rows came out as one long run - so the
            // middle is lifted out and what is left is a thick outline: two or three runs
            // to a row, and it still reads as a heart from the sofa.
            //
            // The gate came down from eleven squares to ten when the off-centre shine was
            // removed. Without either, a 10x10 heart is 66% inked with rows 2, 3 and 4 all
            // reading a bare "10" - the slab this paragraph exists to prevent, one square
            // below where it started looking. The hole is mirrored by construction, which
            // is the whole difference between thinning a heart and chipping one.
            //
            // The hole leans with the outline and stops well above the point, so the two
            // walls stay the same thickness. Left upright while the outline leaned over,
            // it shaved the right wall away to nothing and stranded single squares.
            heartShape(k, .5 + tip * .18, .33, .25, .42, tip, false);
        }
    }

    /**
     * Two lobes, a bar and a point, drawn either into the picture or out of it.
     *
     * <p>A heart-shaped <em>hole</em> is locked so nothing fills it back in, but only from
     * a quarter of the way down. The notch between the two lobes leaves a spike of ink
     * pointing into the hole, and on a wax seal a few squares across that spike is one
     * square with nothing beside it - locking it was what let a lonely square survive the
     * tidy-up on every 17x17 Love Letter.
     */
    private static void heartShape(Ink k, double cu, double top, double w, double h,
            double tip, boolean on) {
        k.oval(cu - w * .45, top + h * .27, w * .53, h * .27, on);
        k.oval(cu + w * .45, top + h * .27, w * .53, h * .27, on);
        k.poly(on, cu - w, top + h * .35, cu + w, top + h * .35, cu + w * tip, top + h);
        k.box(cu - w * .98, top + h * .20, cu + w * .98, top + h * .46, on);
        if (!on) {
            k.lock(cu - w * 1.05, top + h * .28, cu + w * 1.05, top + h * 1.02);
        }
    }

    private static void kitty(Ink k, int n, int pose) {
        double perk = pose == 1 ? .06 : 0;
        k.poly(true, .04, .44, .16, .02 + perk, .46, .30);
        if (pose == 2) {
            k.poly(true, .96, .50, .94, .14, .58, .30);      // one ear folded over
        } else {
            k.poly(true, .96, .44, .84, .02 + perk, .54, .30);
        }
        k.oval(.5, .58, .40, .36);                           // a round face
        if (n >= 8) {
            k.wipeOval(.32, .50, .09, .08);                  // sleeping eyes
            k.wipeOval(.68, .50, .09, .08);
            k.lockOval(.32, .50, .10, .09);
            k.lockOval(.68, .50, .10, .09);
        } else {
            k.dot(.32, .50, false);
            k.dot(.68, .50, false);
        }
        if (n >= 9) {
            k.wipe(.44, .62, .56, .72);                      // a little nose
            k.lock(.44, .62, .56, .72);
        }
        if (n >= 10) {
            double row = pose == 0 ? .74 : (pose == 1 ? .78 : .70);
            k.wipe(.08, row, .30, row + .07);                // whiskers, reaching the edge
            k.wipe(.70, row, .92, row + .07);
            k.lock(.08, row, .30, row + .07);
            k.lock(.70, row, .92, row + .07);
        }
        if (n >= 15) {
            k.wipe(.20, .22, .27, .31);                      // the inside of each ear
            k.wipe(.73, .22, .80, .31);
            k.lock(.20, .22, .27, .31);
            k.lock(.73, .22, .80, .31);
        }
    }

    private static void home(Ink k, int n, int pose) {
        // The chimney reaches the top border on its own. Sitting it beside the ridge with
        // a separate curl of smoke above it put the highest square of the picture off to
        // one side, and the fitting pass then squashed the roof into a triangle that
        // leaned - the 20x20 house had its apex three squares right of its own base.
        if (n >= 9) {
            k.box(.72, .00, .84, .26);
        }
        k.poly(true, .5, n >= 9 ? .04 : .02, .00, .50, 1., .50);
        k.band(.40, .48, .99);
        double door = pose == 1 ? .30 : (pose == 2 ? .70 : .50);
        if (n >= 7) {
            k.wipe(door - .11, .70, door + .11, .99);        // doorway
            k.lock(door - .11, .70, door + .11, .99);
        }
        if (n >= 9) {
            k.wipe(.16, .57, .30, .68);                      // windows
            k.wipe(.70, .57, .84, .68);
            k.lock(.16, .57, .30, .68);
            k.lock(.70, .57, .84, .68);
        }
        if (n >= 10) {
            k.wipe(.42, .32, .58, .43);                      // a light in the loft
            k.lock(.42, .32, .58, .43);
        }
        if (n >= 12) {
            double spare = pose == 2 ? .26 : .74;            // and one more window
            k.wipe(spare - .07, .78, spare + .07, .90);
            k.lock(spare - .07, .78, spare + .07, .90);
        }
    }

    private static void mug(Ink k, int n, int pose) {
        k.box(.56, .38, .99, .76);                           // handle
        k.wipe(.68, .47, .93, .67);                          // ... with a hole for a finger
        k.lock(.68, .47, .93, .67);
        k.box(.08, .24, .62, .84);                           // body
        k.box(.02, .80, .94, .92);                           // a saucer wide enough for both
        if (n >= 9) {
            // Steam that starts at the rim. The two free-floating boxes this replaces came
            // out as a pair of ears hovering above the mug at fifteen and twenty squares -
            // three disconnected pieces, and the component count said so.
            double sway = pose == 2 ? -.10 : (pose == 1 ? .10 : 0);
            k.seg(.24 + sway, .22, .34 + sway, .12, .09);
            k.seg(.34 + sway, .12, .24 + sway, .01, .09);
        }
        if (n >= 9) {
            double top = pose == 1 ? .48 : .42;              // a heart on the cup
            heartShape(k, .33, top, .13, .22, 0, false);
        }
        if (n >= 14) {
            k.wipe(.14, .30, .56, .34);                      // the rim of the cup
            k.lock(.14, .30, .56, .34);
        }
    }

    private static void moon(Ink k, int n, int pose) {
        if (pose == 2) {
            k.oval(.5, .50, .47, .47);                       // a full moon
            if (n >= 9) {
                k.wipeOval(.34, .34, .12, .11);              // craters
                k.wipeOval(.62, .60, .16, .15);
                k.lockOval(.34, .34, .13, .12);
                k.lockOval(.62, .60, .17, .16);
            }
            if (n >= 11) {
                k.wipeOval(.30, .70, .09, .08);
                k.lockOval(.30, .70, .10, .09);
            }
            return;
        }
        double bite = pose == 0 ? .42 : .36;
        k.oval(.48, .52, .45, .46);
        k.wipeOval(.70, .42, bite, .44);
        if (n >= 9) {
            k.plusIfClear(.84, .80, 1);
        }
        if (n >= 13) {
            k.plusIfClear(.68, .12, 1);
        }
    }

    private static void flower(Ink k, int n, int pose) {
        int petals = pose == 0 ? 5 : (pose == 1 ? 6 : 4);
        double cy = .30;
        for (int i = 0; i < petals; i++) {
            double a = Math.PI * 2 * i / petals - Math.PI / 2;
            k.oval(.5 + Math.cos(a) * .23, cy + Math.sin(a) * .22, .15, .14);
        }
        k.oval(.5, cy, .12, .11);
        if (n >= 9) {
            k.wipeOval(.5, cy, .06, .055);                   // seed head
            k.lockOval(.5, cy, .06, .055);
        }
        // The stem starts inside the bloom rather than below it: cut at cy + .14 it left
        // the flower head as one piece and the stem and leaves as two more, which is how
        // a 15x15 "Love in Bloom" came out as three separate things.
        k.band(.06, cy, .99);
        if (n >= 9) {
            k.oval(.28, .76, .17, .10);                      // leaves, touching the stem
            k.oval(.72, .76, .17, .10);
        }
        if (n >= 11) {
            k.wipe(.18, .74, .30, .78);                      // a vein in each leaf
            k.wipe(.70, .74, .82, .78);
            k.lock(.18, .74, .30, .78);
            k.lock(.70, .74, .82, .78);
        }
        if (n >= 9) {
            for (int i = 0; i < petals; i++) {               // the gaps between the petals
                double a = Math.PI * 2 * (i + .5) / petals - Math.PI / 2;
                k.wipeSeg(.5 + Math.cos(a) * .12, cy + Math.sin(a) * .11,
                        .5 + Math.cos(a) * .30, cy + Math.sin(a) * .29, .05);
                k.lockSeg(.5 + Math.cos(a) * .12, cy + Math.sin(a) * .11,
                        .5 + Math.cos(a) * .30, cy + Math.sin(a) * .29, .05);
            }
        }
    }

    private static void star(Ink k, int n, int pose) {
        int points = pose == 1 ? 6 : 5;
        double[] uv = new double[points * 4];
        double outer = .50;
        // Pose 2's arms are longer, so they are also thinner - and a thin arm plus the
        // hollow middle below came out as five separate splinters at twenty squares. It
        // keeps the smaller hollow and skips the ring.
        double inner = pose == 2 ? .21 : (points == 5 ? .25 : .28);
        for (int i = 0; i < points * 2; i++) {
            double a = Math.PI * i / points - Math.PI / 2;
            double r = (i & 1) == 0 ? outer : inner;
            uv[i * 2] = .5 + Math.cos(a) * r;
            uv[i * 2 + 1] = .46 + Math.sin(a) * r * 1.04;
        }
        k.poly(true, uv);
        if (n >= 10) {
            k.wipeOval(.5, .46, .11, .10);                   // the wish in the middle
            k.lockOval(.5, .46, .12, .11);
        }
        if (n >= 13 && pose != 2) {
            k.wipeOval(.5, .46, .20, .19);                   // a star drawn in one line
            k.lockOval(.5, .46, .21, .20);
        }
    }

    private static void birds(Ink k, int n, int pose) {
        if (n < 10) {
            double lift = pose == 1 ? .04 : 0;
            k.oval(.44, .54 - lift, .28, .23);               // one plump bird
            k.oval(.68, .28 - lift, .18, .16);
            k.poly(true, .84, .24 - lift, .99, .30 - lift, .84, .38 - lift);
            k.poly(true, .22, .46 - lift, .01, .34 - lift, .20, .78 - lift);
            k.box(.02, .78, .98, .96);
            return;
        }
        // Two birds from ten squares up rather than twelve. At 10x10 the single-bird
        // branch drew a blob with a bar under it; the pair, facing each other, is the
        // whole point of the picture.
        bird(k, .26, pose, n);
        bird(k, .74, pose, n);
        // The branch starts where the tails end. Set two squares lower it left a whole
        // blank row across the middle of the board on every Love Birds board there is.
        k.box(.02, .74, .98, .92);
        if (n >= 12) {
            k.wipe(.20, .79, .34, .83);                      // knots in the branch
            k.wipe(.62, .82, .76, .86);
            k.lock(.20, .79, .34, .83);
            k.lock(.62, .82, .76, .86);
        }
    }

    private static void bird(Ink k, double cu, int pose, int n) {
        double lean = cu < .5 ? 1 : -1;
        double lift = pose == 1 ? .05 : 0;
        double turn = pose == 2 ? -1 : 1;                    // pose 2 turns them back to back
        k.oval(cu, .62 - lift, .18, .18);
        // The heads lean towards each other, but only so far. Four hundredths closer and
        // they met in the middle of a 15x15 board: the pair came out as one blob with two
        // tails hanging off it.
        k.oval(cu + .06 * lean * turn, .36 - lift, .11, .12);
        k.poly(true, cu + .16 * lean * turn, .32 - lift, cu + .25 * lean * turn, .38 - lift,
                cu + .16 * lean * turn, .44 - lift);
        k.poly(true, cu - .12 * lean * turn, .52 - lift, cu - .25 * lean * turn, .40 - lift,
                cu - .13 * lean * turn, .76 - lift);
        if (n >= 11) {
            k.wipeOval(cu - .04 * lean * turn, .62 - lift, .08, .09);   // a folded wing
            k.lockOval(cu - .04 * lean * turn, .62 - lift, .09, .10);
        }
    }

    private static void teapot(Ink k, int n, int pose) {
        k.box(.70, .46, .99, .84);                           // handle
        k.wipe(.78, .54, .93, .76);
        k.lock(.78, .54, .93, .76);
        k.poly(true, .01, .46, .32, .54, .32, .76);          // a spout joined to the body
        k.oval(.46, .64, .35, .31);                          // body
        k.band(.25, .26, .38);                               // lid
        k.band(.07, .16, .28);                               // knob
        if (n >= 14) {
            double sway = pose == 2 ? -.07 : (pose == 1 ? .07 : 0);
            k.seg(.50 + sway, .14, .57 + sway, .08, .06);    // one wisp off the lid
            k.seg(.57 + sway, .08, .50 + sway, .02, .06);
        }
        if (n >= 12) {
            k.wipe(.34, .32, .66, .36);                      // the seam under the lid
            k.lock(.34, .32, .66, .36);
        }
        if (n >= 10) {
            double top = pose == 1 ? .74 : .68;              // a painted band
            k.wipe(.26, top, .58, top + .06);
            k.lock(.26, top, .58, top + .06);
        }
        if (n >= 14) {
            k.wipeOval(.44, .58, .10, .08);                  // and a flower on the side
            k.lockOval(.44, .58, .11, .09);
        }
    }

    private static void book(Ink k, int n, int pose) {
        double lift = pose == 2 ? .06 : 0;                   // pose 2 stands the pages up
        // The two halves meet at the spine and the crease between them stops short of the
        // top and bottom. Drawn as two separate pages with a full-height gap, the column
        // down the middle of the book was empty on every board the generator ever dealt -
        // one clue in twenty reading "0", at every size from eight squares up.
        k.poly(true, .03, .27 + lift, .50, .14, .50, .98, .03, .87);
        k.poly(true, .97, .27 + lift, .50, .14, .50, .98, .97, .87);
        double crease = n >= 12 ? .02 : (n >= 10 ? .04 : .06);
        k.wipeBand(crease, .26, .90);                        // the crease
        k.lockBand(crease, .26, .90);
        if (n >= 10) {
            k.wipe(.12, .38, .40, .46);                      // lines of story
            k.wipe(.60, .38, .88, .46);
            k.lock(.12, .38, .40, .46);
            k.lock(.60, .38, .88, .46);
        }
        if (n >= 10) {
            double row = pose == 0 ? .58 : .62;
            k.wipe(.12, row, .40, row + .08);
            k.wipe(.60, row, .88, row + .08);
            k.lock(.12, row, .40, row + .08);
            k.lock(.60, row, .88, row + .08);
        }
        if (n >= 13) {
            k.wipe(.12, .76, .34, .84);                      // one more line each side
            k.wipe(.66, .76, .88, .84);
            k.lock(.12, .76, .34, .84);
            k.lock(.66, .76, .88, .84);
        }
    }

    private static void candle(Ink k, int n, int pose) {
        double flame = pose == 1 ? .06 : 0;                  // pose 1 burns taller
        k.oval(.5, .19 - flame, .11, .13 + flame);
        k.poly(true, .5, .01, .38, .22, .62, .22);
        k.band(.17, .34, .80);                               // candle
        k.band(.30, .80, .89);                               // holder
        k.band(.40, .89, .97);                               // base
        if (n >= 9) {
            k.band(.03, .26, .36);                           // wick
        }
        if (n >= 10) {
            double row = pose == 2 ? .40 : .50;              // wax running down the side
            k.box(.66, row, .74, row + .18);
        }
        if (n >= 10) {
            k.wipe(.40, .44, .60, .52);                      // and a hollow burnt into it
            k.lock(.40, .44, .60, .52);
        }
        if (n >= 13) {
            k.wipe(.40, .84, .60, .90);                      // a groove in the holder
            k.lock(.40, .84, .60, .90);
        }
        if (n >= 15) {
            k.wipe(.40, .62, .60, .68);
            k.lock(.40, .62, .60, .68);
        }
    }

    private static void window(Ink k, int n, int pose) {
        k.box(.07, .04, .93, .86);
        // The frame is drawn thicker while the squares are big: nine tenths of a square
        // rounds to nothing, and a 9x9 window came out as three bars with no frame at all.
        double wall = n >= 12 ? .09 : (n >= 10 ? .12 : .15);
        k.wipe(.07 + wall, .04 + wall, .93 - wall, .86 - wall);
        // The glass is locked as well as wiped. Unlocked, a pane one square deep has a
        // filled neighbour above and below and tidy() reads it as a pinhole: every 10x10
        // and 11x11 window with the low rail filled itself back in and shipped as a solid
        // rectangle, 82% inked with one clue per line.
        k.lock(.07 + wall, .04 + wall, .93 - wall, .86 - wall);
        k.band(n >= 12 ? .04 : .07, .04, .86);               // upright mullion
        double rail = pose == 1 ? .34 : (pose == 2 ? .58 : .44);
        k.box(.07, rail, .93, rail + .07);                   // cross mullion
        k.box(.02, .86, .98, .94);                           // sill
        if (n >= 10) {
            // Rain that reaches the rail or the sill, so it is weather and not dust: drawn
            // as short streaks floating in the middle of the glass it came out as two and
            // three square specks the picture had no connection to, and the stray-fragment
            // count agreed.
            //
            // But every streak used to span the whole pane, and a pane of parallel
            // full-depth bars gives every one of its rows the same clue. Measured at 20x20,
            // rows 3-8 and rows 10-15 each read "2 1 2 1 2" six times running: one
            // deduction and five transcriptions, twice a board, and a finished picture that
            // reads as a waffle rather than as rain. Each rivulet now starts and stops at
            // its own height - which is what rain on glass actually does - so the row a
            // player is looking at differs from the one above it.
            // Every rivulet still reaches the rail or the sill, so none of them can strand
            // itself as a two-square speck in the middle of the glass; what differs is where
            // each one starts. One long streak and one short one per pane splits the pane
            // into two bands with different clues instead of leaving all six of its rows
            // identical.
            double top = .17;
            double upper = rail + .04;
            double lower = rail + .06;
            double drift = pose == 2 ? .05 : 0;
            k.box(.26, top, .31, upper);
            k.box(.62, top + (upper - top) * (.42 + drift), .67, upper);
            k.box(.32, lower, .37, .86);
            k.lock(.26, top, .31, upper);
            k.lock(.62, top + (upper - top) * (.42 + drift), .67, upper);
            k.lock(.32, lower, .37, .86);
        }
        if (n >= 12) {
            // A third rivulet in each pane, starting lower again. Two per pane split six
            // rows of glass into two bands of three; three splits them into bands of two,
            // which is where the last of the transcription goes. Every one of them still
            // runs down onto the rail or the sill.
            double top = .17;
            double upper = rail + .04;
            double lower = rail + .06;
            double drift = pose == 2 ? .05 : 0;
            k.box(.68, lower + (.86 - lower) * (.34 + drift), .73, .86);
            k.lock(.68, lower + (.86 - lower) * (.34 + drift), .73, .86);
            // Clear of the upright mullion, which sits across the middle of the board: a
            // rivulet drawn there lands on ink that is already down and changes nothing.
            k.box(.38, top + (upper - top) * (.67 + drift), .43, upper);
            k.lock(.38, top + (upper - top) * (.67 + drift), .43, upper);
            k.box(.78, lower + (.86 - lower) * (.67 + drift), .83, .86);
            k.lock(.78, lower + (.86 - lower) * (.67 + drift), .83, .86);
        }
    }

    private static void fox(Ink k, int n, int pose) {
        double perk = pose == 1 ? .04 : 0;
        // Big ears out to the border and a chin that comes to a point at the bottom of
        // the board. Round-faced and pointed-faced is the whole difference between this
        // and Sleepy Kitty; before, the two were two squares apart at 5x5 and impossible
        // to tell apart at 15.
        k.poly(true, .00, .40, .10, .01 + perk, .44, .30);
        if (pose == 2) {
            k.poly(true, 1., .46, .96, .14, .60, .30);       // one ear turned aside
        } else {
            k.poly(true, 1., .40, .90, .01 + perk, .56, .30);
        }
        k.poly(true, .06, .26, .94, .26, .58, .99, .42, .99);
        if (n >= 9) {
            k.wipe(.24, .38, .38, .48);                      // sleeping eyes
            k.wipe(.62, .38, .76, .48);
            k.lock(.24, .38, .38, .48);
            k.lock(.62, .38, .76, .48);
        } else {
            k.dot(.30, .42, false);
            k.dot(.70, .42, false);
        }
        if (n >= 10) {
            k.wipe(.36, .60, .64, .72);                      // a pale muzzle
            k.lock(.36, .60, .64, .72);
        }
        if (n >= 11) {
            k.wipe(.10, .28, .24, .36);                      // white cheeks
            k.wipe(.76, .28, .90, .36);
            k.lock(.10, .28, .24, .36);
            k.lock(.76, .28, .90, .36);
        }
        if (n >= 15) {
            k.wipe(.16, .10, .26, .22);                      // the inside of each ear
            k.wipe(.74, .10, .84, .22);
            k.lock(.16, .10, .26, .22);
            k.lock(.74, .10, .84, .22);
        }
    }

    private static void mitten(Ink k, int n, int pose) {
        double thumb = pose == 2 ? .58 : .50;
        k.oval(.60, .38, .28, .26);
        k.box(.34, .32, .88, .68);
        k.oval(.20, thumb, .18, .16);                        // thumb
        k.box(.28, .70, .92, .94);                           // cuff
        if (n >= 11) {
            // Ribbing as two short slots at different heights, not as a pair of level
            // bands. The pair of them, with the heart above, read as two eyes and a mouth
            // at fifteen and twenty squares - and once you have seen the face you cannot
            // unsee it. Slots also keep the cuff in one piece, which the diagonal stripe
            // they replaced did not: it shredded the bottom six rows of a 20x20 mitten.
            k.wipe(.42, .74, .49, .88);
            k.wipe(.60, .78, .67, .92);
            k.lock(.42, .74, .49, .88);
            k.lock(.60, .78, .67, .92);
        }
        if (n >= 14) {
            k.wipe(.78, .74, .85, .88);
            k.lock(.78, .74, .85, .88);
        }
        if (n >= 10) {
            double top = pose == 1 ? .32 : .26;              // a heart knitted in, off centre
            heartShape(k, .64, top, .15, .26, 0, false);
        }
    }

    private static void pie(Ink k, int n, int pose) {
        k.oval(.5, .40, .43, .34);                           // a high domed crust
        k.wipe(.0, .58, 1., 1.);
        k.box(.04, .56, .96, .70);                           // dish rim
        k.poly(true, .10, .68, .90, .68, .78, .97, .22, .97);
        if (n >= 9) {
            // A lattice, not a face. Two slots a third and two thirds of the way across
            // are not a mirrored pair, so they read as strips of pastry rather than as a
            // pair of eyes over a mouth. Neither reaches the edge of the crust, because a
            // wipe that crosses the whole picture cuts it in two.
            double slide = pose == 2 ? .06 : 0;
            k.wipe(.28 + slide, .18, .37 + slide, .52);
            k.wipe(.55 + slide, .14, .64 + slide, .48);
            k.lock(.28 + slide, .18, .37 + slide, .52);
            k.lock(.55 + slide, .14, .64 + slide, .48);
        }
        if (n >= 13) {
            k.wipe(.72, .26, .80, .50);                      // one more slat, nearer the rim
            k.lock(.72, .26, .80, .50);
            // No steam vent here. One was tried for the four columns of identical crust and
            // it worked, at a price the board could not afford: a hole in the middle of a
            // column adds a fifth clue group to it, the column gutter is charged to the
            // scarce vertical axis, and the busiest 20x20 board lost a pixel off every one
            // of its four hundred squares to buy it. The stepped strip below does the same
            // work out of run lengths instead of groups.
        }
        if (n >= 11) {
            // One strip laid across the slats, stepped rather than level. A level strip cuts
            // every column it crosses at the same height, so the columns either side of it
            // differ only where a slat happens to be - which is what left four columns of
            // crust reading the same clue. Stepping it changes the run lengths without
            // adding a clue group to any column, and the column gutter is charged to the
            // board's scarce axis, so a fifth group there would cost every square on the
            // board a pixel.
            double row = pose == 1 ? .44 : .38;
            k.wipe(.30, row, .44, row + .05);
            k.wipe(.44, row + .05, .58, row + .10);
            k.wipe(.58, row + .02, .72, row + .07);
            k.lock(.30, row, .44, row + .05);
            k.lock(.44, row + .05, .58, row + .10);
            k.lock(.58, row + .02, .72, row + .07);
        }
        if (n >= 14) {
            k.wipe(.30, .60, .70, .64);                      // the rim of the dish
            k.wipe(.30, .80, .70, .85);                      // and the foot it stands on
            k.lock(.30, .60, .70, .64);
            k.lock(.30, .80, .70, .85);
        }
    }

    private static void sweater(Ink k, int n, int pose) {
        k.box(.21, .15, .79, .97);                           // a long body
        double sleeve = pose == 1 ? .62 : .56;
        k.box(.02, .15, .23, sleeve);                        // sleeves
        k.box(.77, .15, .98, sleeve);
        k.wipeBand(.13, .08, .24);                           // collar
        k.lockBand(.13, .08, .24);
        if (n >= 8) {
            // Two cables at different heights instead of a heart flanked by two cuffs.
            // Symmetric pairs are what turned this into a robot's face at 20x20 - and the
            // cables have to stay one square wide and stop short of the hem, or the body
            // comes out as three separate strips and the sweater reads as a garden fork.
            k.wipe(.38, .30, .42, .62);
            k.lock(.38, .30, .42, .62);
        }
        if (n >= 16) {
            k.wipe(.58, .38, .62, .70);
            k.lock(.58, .38, .62, .70);
        }
        if (n >= 11) {
            k.wipe(.34, .86, .66, .90);                      // ribbed hem
            k.lock(.34, .86, .66, .90);
        }
        if (n >= 13) {
            k.wipe(.28, .72, .46, .76);                      // ribbing across the body
            k.wipe(.54, .78, .72, .82);
            k.lock(.28, .72, .46, .76);
            k.lock(.54, .78, .72, .82);
        }
        if (n >= 13) {
            double cuff = pose == 2 ? .40 : .46;
            k.wipe(.04, cuff, .16, cuff + .06);              // cuffs
            k.wipe(.84, cuff, .96, cuff + .06);
            k.lock(.04, cuff, .16, cuff + .06);
            k.lock(.84, cuff, .96, cuff + .06);
        }
    }

    private static void owl(Ink k, int n, int pose) {
        double tuft = pose == 1 ? .06 : 0;
        k.oval(.5, .60, .36, .35);                           // body
        k.oval(.5, .34, .32, .24);                           // head
        k.poly(true, .16, .28, .24, .03 - tuft, .42, .26);
        k.poly(true, .84, .28, .76, .03 - tuft, .58, .26);
        if (n >= 8) {
            k.wipeOval(.33, .34, .11, .10);                  // big soft eyes
            k.wipeOval(.67, .34, .11, .10);
            k.lockOval(.33, .34, .12, .11);
            k.lockOval(.67, .34, .12, .11);
        } else {
            k.dot(.32, .32, false);
            k.dot(.68, .32, false);
        }
        if (n >= 10) {
            k.wipe(.45, .44, .55, .53);                      // beak
            k.lock(.45, .44, .55, .53);
        }
        if (n >= 9) {
            k.wipe(.44, .90, .56, 1.);                       // between the feet
            k.lock(.44, .90, .56, 1.);
        }
        if (n >= 11) {
            double row = pose == 2 ? .56 : .60;              // folded wings
            k.wipe(.20, row, .30, row + .22);
            k.wipe(.70, row, .80, row + .22);
            k.lock(.20, row, .30, row + .22);
            k.lock(.70, row, .80, row + .22);
        }
        if (n >= 13) {
            k.wipe(.42, .62, .58, .68);                      // and a barred chest
            k.wipe(.42, .74, .58, .80);
            k.lock(.42, .62, .58, .68);
            k.lock(.42, .74, .58, .80);
        }
    }

    private static void yarn(Ink k, int n, int pose) {
        k.oval(.46, .56, .40, .40);
        if (n >= 8) {
            k.seg(.76, .22, .96, .06, .12);                  // the loose end
        }
        if (n >= 8) {
            // Two winds of thread crossing the ball. They stop well short of its edge:
            // run to the rim they cut the ball into quarters instead of wrapping it.
            double slide = pose == 1 ? .06 : (pose == 2 ? -.06 : 0);
            k.wipeSeg(.30, .44 + slide, .64, .74 + slide, .05);
            k.lockSeg(.30, .44 + slide, .64, .74 + slide, .05);
            k.wipeSeg(.32, .74 - slide, .66, .44 - slide, .05);
            k.lockSeg(.32, .74 - slide, .66, .44 - slide, .05);
        }
        if (n >= 13) {
            k.wipeSeg(.32, .58, .62, .58, .05);              // one more wind
            k.lockSeg(.32, .58, .62, .58, .05);
        }
    }

    private static void umbrella(Ink k, int n, int pose) {
        k.oval(.5, .52, .47, .34);
        k.wipe(.0, .52, 1., 1.);
        if (n >= 9) {
            // Panel seams stop a little short of the canopy's hem, so the panels stay
            // joined along the bottom. Run to the edge, three of them cut a 13x13 canopy
            // into four separate strips with the shaft floating underneath.
            double lift = pose == 1 ? .04 : 0;
            k.wipeSeg(.27, .45, .31, .22 + lift, .05);
            k.wipeSeg(.73, .45, .69, .22 + lift, .05);
            k.lockSeg(.27, .45, .31, .22 + lift, .05);
            k.lockSeg(.73, .45, .69, .22 + lift, .05);
        }
        if (n >= 11) {
            k.wipeSeg(.5, .42, .5, .20, .05);
            k.lockSeg(.5, .42, .5, .20, .05);
        }
        k.band(.06, .40, .86);                               // shaft, rooted in the canopy
        if (pose == 2) {
            k.box(.46, .86, .74, .94);                       // the crook, hooked the other way
            k.box(.64, .78, .74, .94);
        } else {
            k.box(.26, .86, .54, .94);
            k.box(.26, .78, .36, .94);
        }
    }

    private static void plant(Ink k, int n, int pose) {
        k.poly(true, .22, .64, .78, .64, .70, .97, .30, .97);
        k.box(.16, .58, .84, .68);                           // rim
        double reach = pose == 2 ? .10 : 0;                  // pose 2 leans to the light
        k.oval(.5 + reach, .20, .15, .18);                   // leaves
        k.oval(.24 + reach, .38, .19, .13);
        k.oval(.76 + reach, .38, .19, .13);
        k.band(.06, .24, .64);                               // stem
        if (n >= 10) {
            k.wipe(.12 + reach, .36, .28 + reach, .40);      // a vein in each leaf
            k.wipe(.72 + reach, .36, .88 + reach, .40);
            k.lock(.12 + reach, .36, .28 + reach, .40);
            k.lock(.72 + reach, .36, .88 + reach, .40);
        }
        if (n >= 11) {
            double row = pose == 1 ? .82 : .76;              // a painted line on the pot
            k.wipe(.32, row, .68, row + .06);
            k.lock(.32, row, .68, row + .06);
        }
        if (n >= 13) {
            k.wipeOval(.5 + reach, .20, .05, .07);
            k.lockOval(.5 + reach, .20, .06, .08);
        }
        if (n >= 15) {
            k.oval(.5 + reach, .04, .09, .06);               // one bud opening
        }
    }

    private static void letter(Ink k, int n, int pose) {
        k.box(.06, .13, .94, .93);
        k.wipeSeg(.06, .17, .5, .58, .13);                   // the flap
        k.wipeSeg(.94, .17, .5, .58, .13);
        k.lockSeg(.06, .17, .5, .58, .13);
        k.lockSeg(.94, .17, .5, .58, .13);
        if (n >= 9) {
            double top = pose == 1 ? .70 : .64;              // a wax heart seal
            heartShape(k, .5, top, .13, .24, 0, false);
        }
        if (n >= 10) {
            double corner = pose == 2 ? .70 : .16;           // a stamp in one corner
            k.wipe(corner - .08, .20, corner + .08, .34);
            k.lock(corner - .08, .20, corner + .08, .34);
        }
        if (n >= 12) {
            k.wipe(.16, .70, .50, .75);                      // and an address, written small
            k.wipe(.16, .80, .42, .85);
            k.lock(.16, .70, .50, .75);
            k.lock(.16, .80, .42, .85);
        }
        if (n >= 15) {
            k.wipe(.60, .80, .86, .85);                      // the corner it was posted from
            k.lock(.60, .80, .86, .85);
        }
    }

    private static void cupcake(Ink k, int n, int pose) {
        k.poly(true, .18, .52, .82, .52, .70, .96, .30, .96);
        k.box(.14, .48, .86, .58);                           // wrapper rim
        k.oval(.32, .42, .20, .16);                          // frosting
        k.oval(.68, .42, .20, .16);
        k.oval(.5, .26, .25, .18);
        if (n >= 9) {
            k.oval(.5, .06, .10, .08);                       // cherry
        }
        if (n >= 8) {
            double slide = pose == 2 ? .04 : 0;
            k.wipe(.36 + slide, .60, .43 + slide, .94);      // wrapper pleats
            k.wipe(.57 + slide, .60, .64 + slide, .94);
            k.lock(.36 + slide, .60, .43 + slide, .94);
            k.lock(.57 + slide, .60, .64 + slide, .94);
        }
        if (n >= 11) {
            double row = pose == 1 ? .34 : .30;              // a swirl in the frosting
            k.wipe(.26, row, .40, row + .07);
            k.wipe(.58, row + .06, .74, row + .13);
            k.lock(.26, row, .40, row + .07);
            k.lock(.58, row + .06, .74, row + .13);
        }
    }

    private static void lantern(Ink k, int n, int pose) {
        double squat = pose == 1 ? .04 : 0;
        k.oval(.5, .55, .38, .33 + squat);
        k.band(.20, .18, .27);                               // top cap
        k.band(.20, .83, .92);                               // bottom cap
        k.band(.05, .02, .20);                               // hanger
        if (n >= 9) {
            k.band(.04, .90, .99);                           // tassel
        }
        if (n >= 10) {
            // Paper ribs, each broken by the seam its own fold is gathered at.
            //
            // Unbroken ribs of equal height gave the lantern's whole waist one clue: at
            // 20x20 rows 8 to 13 all read "4 3 3 4", six identical lines in the middle of
            // the picture, so five of them were transcription rather than deduction. The
            // seams sit at three different heights, which is both what a folded paper
            // lantern looks like and what gives each row of the waist a clue of its own.
            double slide = pose == 2 ? .06 : 0;
            rib(k, .30 + slide, .37 + slide, .28, .82, .47);
            rib(k, .63 - slide, .70 - slide, .28, .82, .61);
        }
        if (n >= 12) {
            // The middle rib is left unbroken on purpose. Its seam would be the only ink at
            // its own height between two slots that are both open there, so it floated free:
            // Paper Lantern look 4 at 16x16 stranded exactly that two-square fragment. The
            // outer seams attach to the lantern's solid edge on one side and so cannot.
            k.wipe(.46, .30, .54, .80);
            k.lock(.46, .30, .54, .80);
        }
    }

    /**
     * One rib of the lantern: a pale fold from {@code top} to {@code bottom}, interrupted by
     * the seam it is gathered at. Locked, because a one-square gap in a rib is exactly what
     * {@code tidy} reads as a pinhole and fills back in.
     */
    private static void rib(Ink k, double left, double right, double top, double bottom,
                            double seam) {
        double gap = (bottom - top) * .09;
        k.wipe(left, top, right, seam - gap);
        k.wipe(left, seam + gap, right, bottom);
        k.lock(left, top, right, seam - gap);
        k.lock(left, seam + gap, right, bottom);
    }

    private static void leaf(Ink k, int n, int pose) {
        double tilt = pose == 2 ? .06 : 0;
        k.poly(true, .5 + tilt, .03, .82 + tilt, .28, .87, .58, .5, .86, .13, .58,
                .18 + tilt, .28);
        k.band(.06, .82, .99);                               // stem
        if (n >= 9) {
            // The vein stops short of the tip and the base. Run the whole length of the
            // leaf it did not draw a vein at all - it cut the leaf into two halves with a
            // stem floating below, which is what a 9x9 Autumn Leaf came out as.
            k.wipeBand(.03, .20, .72);
            k.lockBand(.03, .20, .72);
        }
        if (n >= 11) {
            // Side veins alternate rather than pair off, and stop two thirds of the way
            // out. Run to the edge, four of them took the 20x20 leaf apart into islands.
            double row = pose == 1 ? .44 : .38;
            k.wipeSeg(.5, row, .70, row - .07, .05);
            k.lockSeg(.5, row, .70, row - .07, .05);
            k.wipeSeg(.5, row + .16, .30, row + .09, .05);
            k.lockSeg(.5, row + .16, .30, row + .09, .05);
        }
        if (n >= 14) {
            double row = pose == 1 ? .62 : .58;
            k.wipeSeg(.5, row, .68, row + .07, .05);
            k.lockSeg(.5, row, .68, row + .07, .05);
        }
    }

    // ---- Companions ---------------------------------------------------------------------

    private static final String[] STAR_STAMP = {".#.", "###", ".#."};
    private static final String[] HEART_STAMP = {"#.#", "###", ".#."};

    /**
     * Which little companion keeps each subject company: a star for the pictures that
     * happen after dark or out of doors, a heart for the ones that are about the two of
     * you. It never changes for a given subject, so the pair come to expect it.
     */
    private static final boolean[] STARRY_COMPANION = {
        false, false, false, false,   // heart, kitty, home, mug
        true,  true,  true,  false,   // moon, flower, star, birds
        false, true,  true,  true,    // teapot, book, candle, window
        true,  false, false, false,   // fox, mitten, pie, sweater
        true,  false, true,  true,    // owl, yarn, umbrella, plant
        false, false, true,  true     // letter, cupcake, lantern, leaf
    };

    /**
     * Drops the subject's companion into whatever corner of the board has room for it, so
     * two boards of the same subject read as different pictures rather than as the same
     * one with a square nibbled off. If nothing has room, the board simply goes without.
     */
    private static void stampCompanion(Ink k, int subject, int accent) {
        if (accent == 0 || k.n < 9) {
            return;
        }
        String[] stamp = STARRY_COMPANION[subject] ? STAR_STAMP : HEART_STAMP;
        int h = stamp.length;
        int w = stamp[0].length();
        int bestX = -1;
        int bestY = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int y0 = 0; y0 + h <= k.n; y0++) {
            for (int x0 = 0; x0 + w <= k.n; x0++) {
                if (!k.clearAround(x0, y0, w, h)) {
                    continue;
                }
                // As far out of the way as possible, and always on the same side of the
                // board for a given subject so the companion has a home to come back to.
                int dx = Math.abs(x0 * 2 + w - k.n);
                int dy = Math.abs(y0 * 2 + h - k.n);
                int score = (dx + dy) * 4 + (STARRY_COMPANION[subject] ? x0 - y0 : y0 - x0);
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x0;
                    bestY = y0;
                }
            }
        }
        if (bestX < 0) {
            return;
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (stamp[y].charAt(x) == '#') {
                    k.cell[bestY + y][bestX + x] = true;
                }
                k.locked[bestY + y][bestX + x] = true;
            }
        }
    }

    // ---- Tidying and fairness ----------------------------------------------------------

    /**
     * Sweeps up after the brush: lonely single squares go, and one-square pinholes and
     * notches fill in. This is what turns a sampled shape into a clean silhouette.
     */
    private static void tidy(Ink k) {
        int n = k.n;
        boolean[][] was = new boolean[n][n];
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < n; y++) {
                System.arraycopy(k.cell[y], 0, was[y], 0, n);
            }
            boolean changed = false;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    if (k.locked[y][x]) {
                        continue;
                    }
                    int near = neighbours(was, n, x, y);
                    if (was[y][x] && near == 0) {
                        k.cell[y][x] = false;
                        changed = true;
                    } else if (!was[y][x] && near >= 3) {
                        k.cell[y][x] = true;
                        changed = true;
                    }
                }
            }
            if (!changed) {
                return;
            }
        }
    }

    private static int neighbours(boolean[][] g, int n, int x, int y) {
        int count = 0;
        if (x > 0 && g[y][x - 1]) count++;
        if (x + 1 < n && g[y][x + 1]) count++;
        if (y > 0 && g[y - 1][x]) count++;
        if (y + 1 < n && g[y + 1][x]) count++;
        return count;
    }

    /**
     * Makes the board solvable by logic alone. If line solving stalls, the squares it
     * could not pin down are exactly where the picture is ambiguous, so one of them is
     * nudged - preferring to shave a nub or fill a notch, never to punch a hole in the
     * middle of the shape, never to strand a lonely square, and never to rub out the last
     * square of the top, bottom, left or right edge of the picture, which would undo the
     * fitting pass and hand the board back its blank margin.
     *
     * <p>Protected details are skipped outright on the little boards. There, every square
     * is a locked square of a hand-drawn portrait, so the old -200 score penalty still let
     * the pass pick one: it deleted one of Sleepy Fox's ears and turned its face into a
     * solid bar, at which point the board was fair and no longer a fox. The portraits are
     * drawn solvable at 5, 6 and 7 instead, and this pass leaves them alone.
     *
     * <p>Records how many nudges it took in {@code k.nudges}, which is one of the things
     * {@link #legibility} scores framings by - a picture nobody had to edit is a better
     * picture.
     */
    private static void makeFair(Ink k, int subject, int pose) {
        int n = k.n;
        NonogramSolver solver = new NonogramSolver(n);
        NonogramSolver.Result result = solver.solve(k.cell);
        if (result.solved()) {
            return;
        }
        boolean[][] best = copy(k.cell, n);
        int fewest = result.undetermined;
        boolean[][] tried = new boolean[n][n];

        for (int step = 0; step < MAX_NUDGES; step++) {
            int[] box = k.bounds();
            int pickX = -1;
            int pickY = -1;
            int pickScore = Integer.MIN_VALUE;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    if (result.state[y][x] != NonogramSolver.UNKNOWN
                            || tried[y][x] || strands(k.cell, n, x, y)) {
                        continue;
                    }
                    if (k.locked[y][x] && n <= 7) {
                        continue;
                    }
                    int near = neighbours(k.cell, n, x, y);
                    int score = k.cell[y][x] ? 30 - near * 9 : 4 + near * 9;
                    // Protected details are a last resort: better a star with three arms
                    // than a board that asks the player to guess.
                    if (k.locked[y][x]) {
                        score -= 200;
                    }
                    // So is the square that is holding an edge of the picture out to the
                    // border - rubbing it out would shrink the silhouette off the canvas.
                    if (k.cell[y][x] && onlyOneOnAnEdge(k.cell, n, box, x, y)) {
                        score -= 120;
                    }
                    if (score > pickScore) {
                        pickScore = score;
                        pickX = x;
                        pickY = y;
                    }
                }
            }
            if (pickX < 0) {
                break;
            }
            k.cell[pickY][pickX] = !k.cell[pickY][pickX];
            tried[pickY][pickX] = true;
            k.nudges++;
            result = solver.solve(k.cell);
            if (result.solved()) {
                return;
            }
            if (result.undetermined < fewest) {
                fewest = result.undetermined;
                best = copy(k.cell, n);
            }
        }
        for (int y = 0; y < n; y++) {
            System.arraycopy(best[y], 0, k.cell[y], 0, n);
        }
        rescue(k, subject, pose, solver);
    }

    /**
     * The last resort, and unreachable today: if eighteen nudges could not make a board
     * fair, the detail is usually what is arguing with the solver, so everything is
     * unlocked and the pass is run again. Failing even that, the subject's hand-drawn
     * portrait is scaled up instead - a plainer picture, but never a guess.
     */
    private static void rescue(Ink k, int subject, int pose, NonogramSolver solver) {
        int n = k.n;
        for (int y = 0; y < n; y++) {
            java.util.Arrays.fill(k.locked[y], false);
        }
        tidy(k);
        if (solver.solve(k.cell).solved()) {
            return;
        }
        rescuedBoards++;
        String art = TINY[subject][pose];
        for (int y = 0; y < n; y++) {
            int sy = (y * 2 + 1) * 5 / (n * 2);
            for (int x = 0; x < n; x++) {
                int sx = (x * 2 + 1) * 5 / (n * 2);
                k.cell[y][x] = art.charAt(sy * 5 + sx) == '#';
            }
        }
    }

    /**
     * True when this filled square is the last one on the picture's top, bottom, left or
     * right edge - so rubbing it out would pull the silhouette in off the border.
     */
    private static boolean onlyOneOnAnEdge(boolean[][] g, int n, int[] box, int x, int y) {
        if (box == null) {
            return false;
        }
        if (y == box[1] || y == box[3]) {
            int alone = 0;
            for (int at = 0; at < n && alone < 2; at++) {
                if (g[y][at]) {
                    alone++;
                }
            }
            if (alone < 2) {
                return true;
            }
        }
        if (x == box[0] || x == box[2]) {
            int alone = 0;
            for (int at = 0; at < n && alone < 2; at++) {
                if (g[at][x]) {
                    alone++;
                }
            }
            return alone < 2;
        }
        return false;
    }

    /** True when flipping this square would leave some square with no orthogonal friend. */
    private static boolean strands(boolean[][] g, int n, int x, int y) {
        g[y][x] = !g[y][x];
        boolean bad = false;
        for (int dy = -1; dy <= 1 && !bad; dy++) {
            for (int dx = -1; dx <= 1 && !bad; dx++) {
                if (Math.abs(dx) + Math.abs(dy) > 1) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= n || ny >= n || !g[ny][nx]) {
                    continue;
                }
                bad = neighbours(g, n, nx, ny) == 0;
            }
        }
        g[y][x] = !g[y][x];
        return bad;
    }

    private static boolean[][] copy(boolean[][] g, int n) {
        boolean[][] out = new boolean[n][n];
        for (int y = 0; y < n; y++) {
            System.arraycopy(g[y], 0, out[y], 0, n);
        }
        return out;
    }

    // ---- The brush ---------------------------------------------------------------------

    /**
     * A little painter that works in board-relative coordinates (0..1 across and down),
     * so the same description of a mug fills a 5x5 board and a 20x20 board alike.
     * Squares can be <em>locked</em> to protect deliberate details - an eye, a window,
     * a star - from the tidy and fairness passes.
     */
    static final class Ink {
        final int n;
        final boolean[][] cell;
        final boolean[][] locked;
        /** How many squares the fairness pass had to move. See {@link #legibility}. */
        int nudges;

        // The viewport: shape coordinate u lands on the board at offU + u * sclU. Identity
        // until fitted() aims it, so a subject is described once and framed afterwards.
        private double offU;
        private double sclU = 1;
        private double offV;
        private double sclV = 1;

        Ink(int n) {
            this.n = n;
            this.cell = new boolean[n][n];
            this.locked = new boolean[n][n];
        }

        /** Points the brush at a sub-rectangle of the board, or back at the whole of it. */
        void frame(double offU, double sclU, double offV, double sclV) {
            this.offU = offU;
            this.sclU = sclU;
            this.offV = offV;
            this.sclV = sclV;
        }

        /** Shape coordinates to board coordinates, and back for the sampled shapes. */
        private double mapU(double u) {
            return offU + u * sclU;
        }

        private double mapV(double v) {
            return offV + v * sclV;
        }

        private double shapeU(int x) {
            return ((x + .5) / n - offU) / sclU;
        }

        private double shapeV(int y) {
            return ((y + .5) / n - offV) / sclV;
        }

        private int edge(double t) {
            int i = (int) Math.round(t * n);
            return i < 0 ? 0 : (i > n ? n : i);
        }

        /**
         * The filled silhouette's box as {min x, min y, max x, max y}, or null when
         * nothing has been painted.
         */
        int[] bounds() {
            int minX = n;
            int minY = n;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    if (!cell[y][x]) {
                        continue;
                    }
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
            return maxX < 0 ? null : new int[] {minX, minY, maxX, maxY};
        }

        /** How many squares are inked. */
        int filled() {
            int total = 0;
            for (boolean[] row : cell) {
                for (boolean on : row) {
                    if (on) {
                        total++;
                    }
                }
            }
            return total;
        }

        /**
         * Slides the finished picture so the blank margins are the same on both sides,
         * to within a square. Only ever moves the picture into space it already has, so
         * every row keeps its runs and every column keeps its clue - which means a board
         * that line-solved before this still line-solves after it.
         */
        void centre() {
            int[] box = bounds();
            if (box == null) {
                return;
            }
            int dx = ((n - 1 - box[2]) - box[0]) / 2;
            int dy = ((n - 1 - box[3]) - box[1]) / 2;
            if (dx == 0 && dy == 0) {
                return;
            }
            boolean[][] shifted = new boolean[n][n];
            boolean[][] shiftedLocks = new boolean[n][n];
            for (int y = box[1]; y <= box[3]; y++) {
                for (int x = box[0]; x <= box[2]; x++) {
                    shifted[y + dy][x + dx] = cell[y][x];
                    shiftedLocks[y + dy][x + dx] = locked[y][x];
                }
            }
            for (int y = 0; y < n; y++) {
                System.arraycopy(shifted[y], 0, cell[y], 0, n);
                System.arraycopy(shiftedLocks[y], 0, locked[y], 0, n);
            }
        }

        void box(double u0, double v0, double u1, double v1) {
            rect(u0, v0, u1, v1, true, false);
        }

        /** A box painted in or out of the picture, for shapes drawn both ways. */
        void box(double u0, double v0, double u1, double v1, boolean on) {
            rect(u0, v0, u1, v1, on, false);
        }

        void wipe(double u0, double v0, double u1, double v1) {
            rect(u0, v0, u1, v1, false, false);
        }

        void lock(double u0, double v0, double u1, double v1) {
            rect(u0, v0, u1, v1, false, true);
        }

        private void rect(double u0, double v0, double u1, double v1, boolean on, boolean lockOnly) {
            int x0 = edge(mapU(u0));
            int x1 = edge(mapU(u1));
            int y0 = edge(mapV(v0));
            int y1 = edge(mapV(v1));
            if (x1 <= x0) {
                if (x0 >= n) {
                    x0 = n - 1;
                }
                x1 = x0 + 1;
            }
            if (y1 <= y0) {
                if (y0 >= n) {
                    y0 = n - 1;
                }
                y1 = y0 + 1;
            }
            for (int y = y0; y < y1 && y < n; y++) {
                for (int x = x0; x < x1 && x < n; x++) {
                    if (lockOnly) {
                        locked[y][x] = true;
                    } else {
                        cell[y][x] = on;
                    }
                }
            }
        }

        /** A box centred left-to-right; {@code half} is half its width. */
        void band(double half, double v0, double v1) {
            centred(half, v0, v1, true, false);
        }

        void wipeBand(double half, double v0, double v1) {
            centred(half, v0, v1, false, false);
        }

        void lockBand(double half, double v0, double v1) {
            centred(half, v0, v1, false, true);
        }

        private void centred(double half, double v0, double v1, boolean on, boolean lockOnly) {
            int x0 = edge(mapU(.5 - half));
            int x1 = edge(mapU(.5 + half));
            if (x1 <= x0) {
                x0 = Math.min(x0, n - 1);
                x1 = x0 + 1;
            }
            int y0 = edge(mapV(v0));
            int y1 = edge(mapV(v1));
            if (y1 <= y0) {
                if (y0 >= n) {
                    y0 = n - 1;
                }
                y1 = y0 + 1;
            }
            for (int y = y0; y < y1 && y < n; y++) {
                for (int x = x0; x < x1 && x < n; x++) {
                    if (lockOnly) {
                        locked[y][x] = true;
                    } else {
                        cell[y][x] = on;
                    }
                }
            }
        }

        void oval(double cu, double cv, double ru, double rv) {
            ellipse(cu, cv, ru, rv, true, false);
        }

        /** An oval painted in or out of the picture, for shapes drawn both ways. */
        void oval(double cu, double cv, double ru, double rv, boolean on) {
            ellipse(cu, cv, ru, rv, on, false);
        }

        void wipeOval(double cu, double cv, double ru, double rv) {
            ellipse(cu, cv, ru, rv, false, false);
        }

        void lockOval(double cu, double cv, double ru, double rv) {
            ellipse(cu, cv, ru, rv, false, true);
        }

        private void ellipse(double cu, double cv, double ru, double rv, boolean on, boolean lockOnly) {
            boolean any = false;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double du = (shapeU(x) - cu) / ru;
                    double dv = (shapeV(y) - cv) / rv;
                    if (du * du + dv * dv <= 1.0) {
                        any = true;
                        if (lockOnly) {
                            locked[y][x] = true;
                        } else {
                            cell[y][x] = on;
                        }
                    }
                }
            }
            if (!any) {
                dot(cu, cv, on, lockOnly);
            }
        }

        /** Fills the polygon given as u,v,u,v,... pairs. */
        void poly(boolean on, double... uv) {
            int corners = uv.length / 2;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double u = shapeU(x);
                    double v = shapeV(y);
                    boolean inside = false;
                    for (int i = 0, j = corners - 1; i < corners; j = i++) {
                        double ui = uv[i * 2];
                        double vi = uv[i * 2 + 1];
                        double uj = uv[j * 2];
                        double vj = uv[j * 2 + 1];
                        if ((vi > v) != (vj > v)
                                && u < (uj - ui) * (v - vi) / (vj - vi) + ui) {
                            inside = !inside;
                        }
                    }
                    if (inside) {
                        cell[y][x] = on;
                    }
                }
            }
        }

        /** A thick stroke from one point to another; {@code w} is its width. */
        void seg(double u0, double v0, double u1, double v1, double w) {
            stroke(u0, v0, u1, v1, w, true, false);
        }

        void wipeSeg(double u0, double v0, double u1, double v1, double w) {
            stroke(u0, v0, u1, v1, w, false, false);
        }

        void lockSeg(double u0, double v0, double u1, double v1, double w) {
            stroke(u0, v0, u1, v1, w, false, true);
        }

        private void stroke(double u0, double v0, double u1, double v1, double w,
                boolean on, boolean lockOnly) {
            double du = u1 - u0;
            double dv = v1 - v0;
            double len = du * du + dv * dv;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double u = shapeU(x);
                    double v = shapeV(y);
                    double t = len == 0 ? 0 : ((u - u0) * du + (v - v0) * dv) / len;
                    t = t < 0 ? 0 : (t > 1 ? 1 : t);
                    double ou = u - (u0 + du * t);
                    double ov = v - (v0 + dv * t);
                    if (ou * ou + ov * ov <= w * w / 4) {
                        if (lockOnly) {
                            locked[y][x] = true;
                        } else {
                            cell[y][x] = on;
                        }
                    }
                }
            }
        }

        void dot(double u, double v, boolean on) {
            dot(u, v, on, false);
        }

        private void dot(double u, double v, boolean on, boolean lockOnly) {
            int x = (int) (mapU(u) * n);
            int y = (int) (mapV(v) * n);
            x = x < 0 ? 0 : (x >= n ? n - 1 : x);
            y = y < 0 ? 0 : (y >= n ? n - 1 : y);
            if (lockOnly) {
                locked[y][x] = true;
            } else {
                cell[y][x] = on;
            }
        }

        /**
         * A small twinkling star, but only where it has room to sit on its own - a star
         * that merges into the moon beside it just reads as a lump.
         */
        void plusIfClear(double cu, double cv, int arm) {
            int x = (int) (mapU(cu) * n);
            int y = (int) (mapV(cv) * n);
            x = x < arm ? arm : (x >= n - arm ? n - 1 - arm : x);
            y = y < arm ? arm : (y >= n - arm ? n - 1 - arm : y);
            if (!clearAround(x - arm, y - arm, arm * 2 + 1, arm * 2 + 1)) {
                return;
            }
            for (int d = -arm; d <= arm; d++) {
                cell[y + d][x] = true;
                cell[y][x + d] = true;
            }
            for (int dy = -arm - 1; dy <= arm + 1; dy++) {
                for (int dx = -arm - 1; dx <= arm + 1; dx++) {
                    int lx = x + dx;
                    int ly = y + dy;
                    if (lx >= 0 && ly >= 0 && lx < n && ly < n) {
                        locked[ly][lx] = true;
                    }
                }
            }
        }

        /** True when a stamp of this size at this spot would not touch anything. */
        boolean clearAround(int x0, int y0, int w, int h) {
            if (x0 < 0 || y0 < 0 || x0 + w > n || y0 + h > n) {
                return false;
            }
            for (int y = y0 - 1; y <= y0 + h; y++) {
                for (int x = x0 - 1; x <= x0 + w; x++) {
                    if (x < 0 || y < 0 || x >= n || y >= n) {
                        continue;
                    }
                    if (cell[y][x]) {
                        return false;
                    }
                }
            }
            return true;
        }

        void flip() {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n / 2; x++) {
                    boolean swap = cell[y][x];
                    cell[y][x] = cell[y][n - 1 - x];
                    cell[y][n - 1 - x] = swap;
                    boolean lockSwap = locked[y][x];
                    locked[y][x] = locked[y][n - 1 - x];
                    locked[y][n - 1 - x] = lockSwap;
                }
            }
        }

        /** Insurance: a board must never come out blank. */
        void rescueIfBlank() {
            int filled = 0;
            for (boolean[] row : cell) {
                for (boolean on : row) {
                    if (on) {
                        filled++;
                    }
                }
            }
            if (filled >= Math.max(4, n * n / 10)) {
                return;
            }
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    locked[y][x] = false;
                }
            }
            frame(0, 1, 0, 1);
            oval(.5, .5, .48, .48);
        }
    }
}
