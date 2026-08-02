package com.cozygrams.tv;

/**
 * Draws the endless boards.
 *
 * <p>Every board is a deterministic function of three small numbers pulled out of the
 * seed - which <em>subject</em> (a cat, a mug, a mitten), and which <em>look</em>
 * (mirrored or not, which little accent floats beside it, which pose) - plus the board
 * size. That keeps {@code generate} pure and repeatable, and it keeps the whole universe
 * of boards finite and small enough that the unit tests can check every single one.
 *
 * <p>Subjects are painted with shape primitives on a normalised canvas rather than
 * sampled from a formula, so a mug is a mug at 5x5 and still a mug at 20x20 - and the
 * bigger boards get extra detail (a window, a wisp of steam, an eye) instead of the same
 * blob upscaled. After painting, every board is tidied (no lonely specks, no pinholes)
 * and then run through {@link NonogramSolver}: if pure line logic cannot finish the
 * board, single boundary squares are nudged until it can. The player never has to guess.
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

    /** Mirrored or not (2) x which accent floats beside it (3) x which pose (2). */
    static final int VARIANTS = 12;

    /** How many single squares the fairness pass may nudge before giving up. */
    private static final int MAX_NUDGES = 18;

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
     */
    private static final double DENSEST = 0.70;

    /** How many times {@link #fitted} measures the silhouette and repaints it larger. */
    private static final int FIT_PASSES = 5;

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

    /** Builds one board directly. Package-visible so the tests can sweep every look. */
    static Puzzle compose(int subject, int variant, int size) {
        int subj = Math.floorMod(subject, NAMES.length);
        Ink ink = fitted(subj, variant, size);
        if (isMirrored(variant)) {
            ink.flip();
        }
        stampAccent(ink, accentOf(variant));
        ink.rescueIfBlank();
        tidy(ink);
        makeFair(ink);
        ink.centre();
        return new Puzzle(ink.cell, NAMES[subj]);
    }

    /**
     * Paints the subject at a size that actually uses the board.
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
    private static Ink fitted(int subject, int variant, int n) {
        double offU = 0;
        double sclU = 1;
        double offV = 0;
        double sclV = 1;
        double wantW = 1;
        double wantH = 1;
        double bareW = 1;
        double bareH = 1;
        Ink ink = new Ink(n);
        if (n <= 7) {
            paint(ink, subject, variant, n);
            return ink;
        }
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
                bareW = curW;
                bareH = curH;
                double cap = Math.min(1 / curW, 1 / curH) * STRETCH;
                wantW = Math.min(1, curW * Math.min(1 / curW, cap));
                wantH = Math.min(1, curH * Math.min(1 / curH, cap));
            }
            // Enlarging multiplies the inked area, so a subject that is already mostly
            // solid is let out less than a spindly one - the difference between a heart
            // that grows until it touches the border and an envelope that would have
            // become a slab. This reads the density that actually came out rather than
            // predicting it, and never asks for less than the subject covers unaided.
            boolean solid = inked > DENSEST;
            if (solid) {
                double ease = Math.sqrt(DENSEST / inked);
                wantW = Math.max(bareW, Math.min(wantW, curW * ease));
                wantH = Math.max(bareH, Math.min(wantH, curH * ease));
            }
            if (!solid && spanX >= (int) (wantW * n) && spanY >= (int) (wantH * n)) {
                break;
            }
            // Re-aim the viewport at what actually came out, so shapes that answer back -
            // a star that will not land, an ear the border clipped - still converge.
            double midU = (box[0] + box[2] + 1) / (2.0 * n);
            double midV = (box[1] + box[3] + 1) / (2.0 * n);
            double growW = wantW / curW;
            double growH = wantH / curH;
            offU = .5 - (midU - offU) * growW;
            sclU *= growW;
            offV = .5 - (midV - offV) * growH;
            sclV *= growH;
        }
        return ink;
    }

    private static boolean isMirrored(int variant) {
        return (Math.floorMod(variant, VARIANTS) & 1) == 1;
    }

    private static int accentOf(int variant) {
        return (Math.floorMod(variant, VARIANTS) >> 1) % 3;
    }

    private static int poseOf(int variant) {
        return Math.floorMod(variant, VARIANTS) / 6;
    }

    /**
     * The smallest boards get hand-drawn icons instead of painted shapes. Twenty-five
     * squares is not enough room for a spout or a whisker, so each subject has a 5x5
     * portrait that is scaled up for 6x6 and 7x7 boards. Each is locked once drawn, so
     * nothing tidies away the one square that makes a mug a mug.
     *
     * <p>Every portrait touches all four edges. Nearest-neighbour scaling preserves that,
     * so a 5x5, 6x6 or 7x7 board is filled corner to corner without any fitting pass -
     * which is just as well, because at this size one blank margin row is a fifth of the
     * puzzle. {@code PuzzleGeneratorTest} checks the span on the finished boards.
     */
    private static final String[] TINY = {
        ".#.#." + "#####" + "#####" + ".###." + "..#..",   // heart
        "#...#" + "#####" + "#.#.#" + ".###." + ".###.",   // kitty
        "..#.." + ".###." + "#####" + "#.#.#" + "##.##",   // home
        ".#..." + "####." + "###.#" + "#####" + ".###.",   // mug, steaming
        ".####" + "##..." + "##..." + "##..." + ".####",   // moon
        ".###." + "#####" + ".###." + "..#.." + ".###.",   // flower
        "..#.." + ".###." + "#####" + ".###." + ".#.#.",   // star
        "..##." + ".###." + "#####" + "..#.." + "#####",   // bird on a branch
        "..#.." + ".###." + "#####" + "#####" + ".###.",   // teapot
        ".#.#." + "##.##" + "##.##" + "##.##" + ".##..",   // open book with a ribbon
        "..#.." + "..#.." + ".###." + ".###." + "#####",   // candle
        "#####" + "#.#.#" + "#.#.#" + "#####" + "..#..",   // window with a raindrop
        "#...#" + "#####" + "#.#.#" + ".###." + "..#..",   // fox
        ".##.." + "####." + "#####" + ".###." + ".###.",   // mitten
        "..#.." + "..#.." + "#####" + "#####" + ".###.",   // pie, steaming
        "#.#.#" + "#####" + ".###." + ".###." + ".###.",   // sweater
        ".###." + "#.#.#" + "#####" + ".###." + ".#.#.",   // owl
        "...##" + ".###." + "#####" + ".###." + "..#..",   // yarn, trailing a thread
        ".###." + "#####" + "..#.." + "..#.." + ".##..",   // umbrella
        "..#.." + ".###." + "..#.." + "#####" + ".###.",   // plant
        "#####" + "##.##" + "#####" + ".###." + "..#..",   // letter, sealed with a heart
        "..#.." + ".###." + "#####" + "#.#.#" + "#.#.#",   // cupcake
        "..#.." + "#.#.#" + "#.#.#" + "#.#.#" + "..#..",   // lantern
        ".###." + "##.##" + "##.##" + ".###." + "..#.."    // leaf
    };

    // ---- The subjects -----------------------------------------------------------------

    private static void paint(Ink k, int subject, int variant, int n) {
        int pose = poseOf(variant);
        if (n <= 7) {
            tiny(k, subject, n);
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

    /** Scales the 5x5 portrait up to a 5, 6 or 7 square board. */
    private static void tiny(Ink k, int subject, int n) {
        String art = TINY[subject];
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
        double lift = pose == 0 ? 0 : .04;
        k.oval(.29, .36 - lift, .25, .23);
        k.oval(.71, .36 - lift, .25, .23);
        k.poly(true, .03, .42 - lift, .97, .42 - lift, .5, .97);
        k.band(.46, .30 - lift, .52);
        if (n >= 13) {
            k.wipe(.26, .32, .38, .46);          // a little shine
            k.lock(.26, .32, .38, .46);
        }
    }

    private static void kitty(Ink k, int n, int pose) {
        double tilt = pose == 0 ? 0 : .04;
        k.poly(true, .04, .44, .16, .02 + tilt, .46, .30);
        k.poly(true, .96, .44, .84, .02 + tilt, .54, .30);
        k.oval(.5, .58, .40, .36);               // a round face
        if (n >= 8) {
            k.wipeOval(.32, .50, .08, .07);      // sleeping eyes
            k.wipeOval(.68, .50, .08, .07);
            k.lockOval(.32, .50, .09, .08);
            k.lockOval(.68, .50, .09, .08);
        } else {
            k.dot(.32, .50, false);
            k.dot(.68, .50, false);
        }
        if (n >= 10) {
            k.wipe(.44, .64, .56, .72);          // a little nose
            k.lock(.44, .64, .56, .72);
        }
        if (n >= 15) {
            double row = pose == 0 ? .72 : .76;  // whiskers
            k.wipe(.14, row, .30, row + .06);
            k.wipe(.70, row, .86, row + .06);
            k.lock(.14, row, .30, row + .06);
            k.lock(.70, row, .86, row + .06);
        }
    }

    private static void home(Ink k, int n, int pose) {
        if (n >= 9) {
            k.box(.74, .08, .86, .27);           // chimney
        }
        k.poly(true, .5, .03, .01, .45, .99, .45);
        k.band(.40, .43, .95);
        if (n >= 7) {
            k.wipeBand(.11, .68, .95);           // doorway
            k.lockBand(.11, .68, .95);
        }
        if (n >= 9) {
            k.wipe(.19, .54, .31, .64);          // windows
            k.wipe(.69, .54, .81, .64);
            k.lock(.19, .54, .31, .64);
            k.lock(.69, .54, .81, .64);
        }
        if (n >= 16 && pose == 1) {
            k.wipe(.19, .74, .31, .84);
            k.wipe(.69, .74, .81, .84);
            k.lock(.19, .74, .31, .84);
            k.lock(.69, .74, .81, .84);
        }
        if (n >= 15) {
            k.box(.56, .02, .66, .10);           // a curl of smoke
        }
    }

    private static void mug(Ink k, int n, int pose) {
        k.box(.58, .40, .99, .74);               // handle
        k.wipe(.70, .50, .90, .64);              // ... with a hole to hook a finger in
        k.lock(.70, .50, .90, .64);
        k.box(.08, .26, .62, .84);               // body
        k.box(.02, .84, .76, .94);               // saucer
        if (n >= 13) {
            k.box(.14, .04, .24, .18);           // steam
            k.box(.34, .00, .44, .14);
        }
        if (n >= 14) {
            double top = pose == 0 ? .46 : .52;  // a heart on the cup
            k.wipe(.16, top, .26, top + .09);
            k.wipe(.32, top, .42, top + .09);
            k.wipe(.22, top + .07, .36, top + .16);
            k.lock(.14, top - .02, .44, top + .18);
        }
    }

    private static void moon(Ink k, int n, int pose) {
        double bite = pose == 0 ? .42 : .38;
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
        int petals = pose == 0 ? 5 : 6;
        double cy = .32;
        for (int i = 0; i < petals; i++) {
            double a = Math.PI * 2 * i / petals - Math.PI / 2;
            k.oval(.5 + Math.cos(a) * .22, cy + Math.sin(a) * .21, .145, .135);
        }
        k.oval(.5, cy, .11, .10);
        if (n >= 12) {
            k.wipeOval(.5, cy, .05, .045);       // seed head
            k.lockOval(.5, cy, .05, .045);
        }
        k.band(.05, cy + .14, .98);              // stem
        if (n >= 9) {
            k.oval(.25, .74, .16, .10);          // leaves
            k.oval(.75, .74, .16, .10);
        }
    }

    private static void star(Ink k, int n, int pose) {
        int points = pose == 0 ? 5 : 6;
        double[] uv = new double[points * 4];
        double outer = .50;
        double inner = points == 5 ? .25 : .28;
        for (int i = 0; i < points * 2; i++) {
            double a = Math.PI * i / points - Math.PI / 2;
            double r = (i & 1) == 0 ? outer : inner;
            uv[i * 2] = .5 + Math.cos(a) * r;
            uv[i * 2 + 1] = .46 + Math.sin(a) * r * 1.04;
        }
        k.poly(true, uv);
        if (n >= 14) {
            k.wipeOval(.5, .48, .09, .08);
            k.lockOval(.5, .48, .09, .08);
        }
    }

    private static void birds(Ink k, int n, int pose) {
        if (n < 12) {
            double lift = pose == 0 ? 0 : .04;
            k.oval(.44, .54 - lift, .28, .23);   // one plump bird
            k.oval(.68, .28 - lift, .18, .16);
            k.poly(true, .84, .24 - lift, .99, .30 - lift, .84, .38 - lift);
            k.poly(true, .22, .46 - lift, .01, .34 - lift, .20, .74 - lift);
            if (n >= 9) {
                k.box(.02, .88, .98, .96);
            }
            if (n >= 10) {
                k.wipe(.60, .24 - lift, .70, .30 - lift);
                k.lock(.60, .24 - lift, .70, .30 - lift);
            }
            return;
        }
        bird(k, .26, pose);
        bird(k, .74, pose);
        k.box(.02, .88, .98, .96);               // the branch they share
        if (n >= 14) {
            k.box(.42, .10, .50, .20);           // a heart between them
            k.box(.52, .10, .60, .20);
            k.box(.44, .18, .58, .28);
        }
    }

    private static void bird(Ink k, double cu, int pose) {
        double lean = cu < .5 ? 1 : -1;
        double lift = pose == 0 ? 0 : .04;
        k.oval(cu, .62 - lift, .17, .17);
        k.oval(cu + .10 * lean, .38 - lift, .11, .11);
        k.poly(true, cu + .19 * lean, .34 - lift, cu + .27 * lean, .40 - lift,
                cu + .19 * lean, .44 - lift);
        k.poly(true, cu - .12 * lean, .52 - lift, cu - .24 * lean, .40 - lift,
                cu - .13 * lean, .74 - lift);
    }

    private static void teapot(Ink k, int n, int pose) {
        k.box(.72, .46, .99, .80);                // handle
        if (n >= 11) {
            k.wipe(.80, .54, .93, .72);
            k.lock(.80, .54, .93, .72);
        }
        k.box(.02, .44, .30, .58);                // spout
        k.oval(.46, .62, .34, .26);               // body
        k.band(.26, .28, .40);                    // lid
        k.band(.09, .16, .30);                    // knob
        if (n >= 13) {
            k.box(.26, .00, .36, .12);            // steam
            k.box(.56, .02, .66, .14);
        }
        if (n >= 14) {
            double top = pose == 0 ? .62 : .68;   // a painted band
            k.wipe(.34, top, .60, top + .07);
            k.lock(.34, top, .60, top + .07);
        }
    }

    private static void book(Ink k, int n, int pose) {
        k.poly(true, .03, .27, .47, .14, .47, .98, .03, .87);
        k.poly(true, .97, .27, .53, .14, .53, .98, .97, .87);
        k.wipeBand(.02, .12, 1.);                // the spine gap
        k.lockBand(.02, .12, 1.);
        if (n >= 11) {
            k.wipe(.14, .40, .38, .48);          // lines of story
            k.wipe(.62, .40, .86, .48);
            k.lock(.14, .40, .38, .48);
            k.lock(.62, .40, .86, .48);
        }
        if (n >= 14) {
            double row = pose == 0 ? .61 : .66;
            k.wipe(.14, row, .38, row + .07);
            k.wipe(.62, row, .86, row + .07);
            k.lock(.14, row, .38, row + .07);
            k.lock(.62, row, .86, row + .07);
        }
    }

    private static void candle(Ink k, int n, int pose) {
        k.oval(.5, .19, .11, .13);               // flame
        k.poly(true, .5, .01, .38, .22, .62, .22);
        k.band(.17, .34, .80);                   // candle
        k.band(.30, .80, .89);                   // holder
        k.band(.40, .89, .97);                   // base
        if (n >= 11) {
            k.band(.03, .27, .36);               // wick
        }
        if (n >= 13) {
            double row = pose == 0 ? .46 : .54;  // wax running down the side
            k.box(.66, row, .74, row + .16);
        }
        if (n >= 15) {
            k.wipe(.42, .84, .58, .90);          // a groove in the holder
            k.lock(.42, .84, .58, .90);
        }
    }

    private static void window(Ink k, int n, int pose) {
        k.box(.08, .06, .92, .84);
        k.wipe(.19, .17, .81, .74);
        k.band(.05, .06, .84);                   // upright mullion
        k.box(.08, .42, .92, .50);               // cross mullion
        k.box(.02, .84, .98, .92);               // sill
        if (n >= 11) {
            k.box(.26, .17, .31, .34);           // rain running down the glass
            k.box(.62, .17, .67, .30);
            k.box(.32, .50, .37, .66);
            k.lock(.26, .17, .31, .34);
            k.lock(.62, .17, .67, .30);
            k.lock(.32, .50, .37, .66);
        }
        if (n >= 14 && pose == 1) {
            k.box(.68, .50, .73, .62);
            k.lock(.68, .50, .73, .62);
        }
    }

    private static void fox(Ink k, int n, int pose) {
        double tilt = pose == 0 ? 0 : .04;
        k.poly(true, .02, .38, .12, .02 + tilt, .40, .30);     // big pointed ears
        k.poly(true, .98, .38, .88, .02 + tilt, .60, .30);
        k.poly(true, .08, .28, .92, .28, .60, .92, .40, .92);  // the sharp little face
        k.oval(.26, .42, .18, .14);                            // cheeks
        k.oval(.74, .42, .18, .14);
        if (n >= 10) {
            k.wipe(.28, .40, .38, .48);                        // sleeping eyes
            k.wipe(.62, .40, .72, .48);
            k.lock(.28, .40, .38, .48);
            k.lock(.62, .40, .72, .48);
        } else {
            k.dot(.30, .42, false);
            k.dot(.70, .42, false);
        }
        if (n >= 14) {
            k.wipe(.40, .56, .60, .64);                        // a pale muzzle
            k.lock(.40, .56, .60, .64);
        }
        if (n >= 16) {
            k.wipe(.16, .34, .26, .40);                        // cheek fur
            k.wipe(.74, .34, .84, .40);
            k.lock(.16, .34, .26, .40);
            k.lock(.74, .34, .84, .40);
        }
    }

    private static void mitten(Ink k, int n, int pose) {
        k.oval(.60, .40, .27, .25);
        k.box(.34, .34, .87, .68);
        k.oval(.22, .50, .16, .15);              // thumb
        k.box(.28, .66, .90, .90);               // cuff
        if (n >= 12) {
            k.wipe(.40, .72, .78, .76);          // knitted ribbing
            k.lock(.40, .72, .78, .76);
        }
        if (n >= 16) {
            k.wipe(.40, .80, .78, .84);
            k.lock(.40, .80, .78, .84);
        }
        if (n >= 14) {
            double top = pose == 0 ? .38 : .42;  // a heart on the back
            k.wipe(.44, top, .52, top + .08);
            k.wipe(.60, top, .68, top + .08);
            k.wipe(.50, top + .06, .62, top + .14);
            k.lock(.42, top - .02, .70, top + .16);
        }
    }

    private static void pie(Ink k, int n, int pose) {
        k.oval(.5, .42, .42, .34);               // a high domed crust
        k.wipe(.0, .60, 1., 1.);
        k.box(.04, .58, .96, .72);               // dish rim
        k.poly(true, .10, .70, .90, .70, .78, .97, .22, .97);
        if (n >= 11) {
            k.wipe(.45, .28, .55, .38);          // steam vent
            k.lock(.45, .28, .55, .38);
        }
        if (n >= 15) {
            double row = pose == 0 ? .36 : .42;
            k.wipe(.28, row, .36, row + .07);
            k.wipe(.64, row, .72, row + .07);
            k.lock(.28, row, .36, row + .07);
            k.lock(.64, row, .72, row + .07);
        }
    }

    private static void sweater(Ink k, int n, int pose) {
        k.box(.24, .15, .76, .97);               // a long body
        k.box(.02, .15, .26, .56);               // sleeves
        k.box(.74, .15, .98, .56);
        k.wipeBand(.13, .08, .24);               // collar
        k.lockBand(.13, .08, .24);
        if (n >= 12) {
            double top = pose == 0 ? .42 : .48;  // a knitted heart
            k.wipe(.36, top, .44, top + .08);
            k.wipe(.56, top, .64, top + .08);
            k.wipe(.42, top + .06, .58, top + .15);
            k.lock(.34, top - .02, .66, top + .17);
        }
        if (n >= 14) {
            k.wipe(.36, .86, .64, .92);          // ribbed hem
            k.lock(.36, .86, .64, .92);
        }
        if (n >= 16) {
            k.wipe(.06, .44, .14, .50);          // cuffs
            k.wipe(.86, .44, .94, .50);
            k.lock(.06, .44, .14, .50);
            k.lock(.86, .44, .94, .50);
        }
    }

    private static void owl(Ink k, int n, int pose) {
        k.oval(.5, .60, .36, .35);               // body
        k.oval(.5, .34, .32, .24);               // head
        k.poly(true, .16, .28, .24, .03, .42, .26);
        k.poly(true, .84, .28, .76, .03, .58, .26);
        if (n >= 8) {
            k.wipeOval(.33, .34, .10, .09);      // big soft eyes
            k.wipeOval(.67, .34, .10, .09);
            k.lockOval(.33, .34, .11, .10);
            k.lockOval(.67, .34, .11, .10);
        } else {
            k.dot(.32, .32, false);
            k.dot(.68, .32, false);
        }
        if (n >= 10) {
            k.wipe(.46, .44, .54, .52);          // beak
            k.lock(.46, .44, .54, .52);
        }
        if (n >= 9) {
            k.wipe(.44, .90, .56, 1.);           // between the feet
            k.lock(.44, .90, .56, 1.);
        }
        if (n >= 15) {
            double row = pose == 0 ? .62 : .66;  // folded wings
            k.wipe(.25, row, .33, row + .13);
            k.wipe(.67, row, .75, row + .13);
            k.lock(.25, row, .33, row + .13);
            k.lock(.67, row, .75, row + .13);
        }
    }

    private static void yarn(Ink k, int n, int pose) {
        k.oval(.46, .56, .39, .39);
        if (n >= 10) {
            k.seg(.76, .22, .94, .08, .11);      // the loose end
        }
        if (n >= 11) {
            double slide = pose == 0 ? 0 : .06;
            k.wipeSeg(.28, .40 + slide, .64, .76 + slide, .05);
            k.lockSeg(.28, .40 + slide, .64, .76 + slide, .05);
            k.wipeSeg(.30, .76 - slide, .66, .40 - slide, .05);
            k.lockSeg(.30, .76 - slide, .66, .40 - slide, .05);
        }
    }

    private static void umbrella(Ink k, int n, int pose) {
        k.oval(.5, .52, .47, .34);
        k.wipe(.0, .52, 1., 1.);
        k.band(.06, .50, .86);                   // shaft
        if (n >= 9) {
            k.box(.26, .86, .54, .94);           // the crook of the handle
            k.box(.26, .78, .36, .94);
        } else {
            k.band(.06, .50, .96);
        }
        if (n >= 13) {
            double lift = pose == 0 ? 0 : .04;   // panel seams
            k.wipe(.24, .30 + lift, .30, .44 + lift);
            k.wipe(.70, .30 + lift, .76, .44 + lift);
            k.lock(.24, .30 + lift, .30, .44 + lift);
            k.lock(.70, .30 + lift, .76, .44 + lift);
        }
    }

    private static void plant(Ink k, int n, int pose) {
        k.poly(true, .22, .64, .78, .64, .70, .97, .30, .97);
        k.box(.16, .58, .84, .68);               // rim
        k.oval(.5, .22, .14, .17);               // leaves
        k.oval(.24, .38, .18, .12);
        k.oval(.76, .38, .18, .12);
        k.band(.05, .28, .64);                   // stem
        if (n >= 13) {
            double row = pose == 0 ? .76 : .80;  // a painted line on the pot
            k.wipe(.38, row, .62, row + .05);
            k.lock(.38, row, .62, row + .05);
        }
        if (n >= 16) {
            k.oval(.5, .06, .09, .06);           // one bud opening
        }
    }

    private static void letter(Ink k, int n, int pose) {
        k.box(.06, .13, .94, .93);
        k.wipeSeg(.06, .17, .5, .57, .09);       // the flap
        k.wipeSeg(.94, .17, .5, .57, .09);
        k.lockSeg(.06, .17, .5, .57, .09);
        k.lockSeg(.94, .17, .5, .57, .09);
        if (n >= 12) {
            double top = pose == 0 ? .65 : .70;  // a wax heart seal
            k.wipe(.41, top, .59, top + .08);
            k.wipe(.44, top + .07, .56, top + .15);
            k.lock(.39, top - .02, .61, top + .17);
        }
        if (n >= 16) {
            k.wipe(.14, .21, .24, .30);          // a stamp corner
            k.lock(.14, .21, .24, .30);
        }
    }

    private static void cupcake(Ink k, int n, int pose) {
        k.poly(true, .18, .52, .82, .52, .70, .96, .30, .96);
        k.box(.14, .48, .86, .58);               // wrapper rim
        k.oval(.32, .42, .19, .15);              // frosting
        k.oval(.68, .42, .19, .15);
        k.oval(.5, .28, .24, .17);
        if (n >= 9) {
            k.oval(.5, .09, .10, .08);           // cherry
        }
        if (n >= 11) {
            k.wipe(.38, .62, .44, .92);          // wrapper pleats
            k.wipe(.56, .62, .62, .92);
            k.lock(.38, .62, .44, .92);
            k.lock(.56, .62, .62, .92);
        }
        if (n >= 14) {
            double row = pose == 0 ? .28 : .32;  // sprinkles
            k.wipe(.28, row, .36, row + .07);
            k.wipe(.64, row, .72, row + .07);
            k.lock(.28, row, .36, row + .07);
            k.lock(.64, row, .72, row + .07);
        }
    }

    private static void lantern(Ink k, int n, int pose) {
        k.oval(.5, .55, .37, .33);
        k.band(.20, .18, .27);                   // top cap
        k.band(.20, .83, .92);                   // bottom cap
        k.band(.05, .02, .20);                   // hanger
        if (n >= 9) {
            k.band(.04, .90, .99);               // tassel
        }
        if (n >= 11) {
            double slide = pose == 0 ? 0 : .06;  // paper ribs
            k.wipe(.34 + slide, .30, .40 + slide, .80);
            k.wipe(.60 - slide, .30, .66 - slide, .80);
            k.lock(.34 + slide, .30, .40 + slide, .80);
            k.lock(.60 - slide, .30, .66 - slide, .80);
        }
    }

    private static void leaf(Ink k, int n, int pose) {
        k.poly(true, .5, .03, .82, .28, .87, .58, .5, .86, .13, .58, .18, .28);
        k.band(.06, .82, .99);                   // stem
        if (n >= 12) {
            k.wipeBand(.03, .12, .84);           // the central vein
            k.lockBand(.03, .12, .84);
        }
        if (n >= 16) {
            double row = pose == 0 ? .40 : .46;  // one side vein each way
            k.wipeSeg(.5, row, .74, row - .07, .05);
            k.lockSeg(.5, row, .74, row - .07, .05);
            k.wipeSeg(.5, row + .14, .26, row + .07, .05);
            k.lockSeg(.5, row + .14, .26, row + .07, .05);
        }
    }

    // ---- Accents ----------------------------------------------------------------------

    private static final String[] STAR_STAMP = {".#.", "###", ".#."};
    private static final String[] HEART_STAMP = {"#.#", "###", ".#."};

    /**
     * Drops a tiny star or heart into whatever corner of the board has room for it, so
     * two boards of the same subject read as different pictures rather than as the same
     * one with a square nibbled off. If nothing has room, the board simply goes without.
     */
    private static void stampAccent(Ink k, int accent) {
        if (accent == 0 || k.n < 9) {
            return;
        }
        String[] stamp = accent == 1 ? STAR_STAMP : HEART_STAMP;
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
                // As far out of the way as possible, and on opposite sides for the two
                // accents so a star and a heart never land in the same spot.
                int dx = Math.abs(x0 * 2 + w - k.n);
                int dy = Math.abs(y0 * 2 + h - k.n);
                int score = (dx + dy) * 4 + (accent == 1 ? x0 - y0 : y0 - x0);
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
     */
    private static void makeFair(Ink k) {
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
