package com.cozygrams.tv;

/**
 * The Story Book: a lovingly authored progression, drawn square by square.
 *
 * <p>Twenty-four chapters, and the size ladder is the story. Four little 5x5 evenings to
 * learn on, three 7x7s where a picture first gets a spout and a doorway, six 10x10s where
 * it can have two birds and the branch they share, four 12x12s, five long 15x15s, and two
 * 20x20s to finish on. The book used to jump straight from eight 5x5 chapters to 10x10 and
 * stop at fifteen; five of its eighteen chapters solved completely on the first sweep of
 * the clues, which is dictation rather than a puzzle.
 *
 * <p>Every chapter is checked by {@link NonogramSolver} in the unit tests: each one can be
 * finished by logic alone, with no guessing and exactly one answer. The tests also check
 * that a chapter is not a board the endless deck already deals - four of the old ones were
 * byte-identical to a {@link PuzzleGenerator} board and three more were within six squares
 * of one - and that no chapter gives itself away in a single sweep.
 *
 * <p>Each chapter carries one line of copy in {@link #LINES}, read out on the board card.
 * It is the only place in the game that talks to the pair in sentences, so it is kept to
 * one breath each.
 */
public final class PuzzleLibrary {

    private static final String[] NAMES = {
        "First Heart", "Candlelight", "Lucky Star", "Moon Kiss",
        "Tiny Tulip", "Warm Cocoa", "Little Home", "Love Letter",
        "Love Birds", "Sleepy Cat", "Garden Rose", "Rainy Window",
        "Knitted Sweater", "Paper Lantern", "Fresh Baked Pie", "Little Owl",
        "Autumn Leaf", "Dancing Hearts", "Two Mugs", "Cat and Yarn",
        "Bedtime Story", "Cozy Cottage", "Moonlit Garden", "Forever"
    };

    /** One line each, in the order the chapters are read. */
    private static final String[] LINES = {
        "Where every love story starts: one small heart, drawn together.",
        "One flame is plenty when there are two of you to see by.",
        "Someone wished on this one. It may as well have been you.",
        "The moon leans in and the whole room goes quiet.",
        "The first thing up in the window box, and worth the wait.",
        "Two spoons, one mug. Nobody is counting marshmallows.",
        "Not big. Not fancy. The light is always on.",
        "Written slowly, sealed carefully, read twice.",
        "They have been on that branch all afternoon and neither has moved.",
        "She has claimed the warm end of the sofa. That is simply how it is now.",
        "One rose from the garden, in the little jug with the chip in it.",
        "Let it rain. Nobody has anywhere to be.",
        "Slightly too big, which is exactly right.",
        "Strung along the porch, swaying whenever the door opens.",
        "Out of the oven. Nobody is allowed to touch it for ten whole minutes.",
        "Awake when you are awake. Very serious about it.",
        "It came in on somebody's coat and now it lives on the windowsill.",
        "Two hearts, out of step and perfectly in time.",
        "Both of them still too hot. Both of them still worth it.",
        "The scarf will get finished. Eventually. She is helping.",
        "One more chapter, and then really and truly lights out.",
        "Smoke from the chimney means somebody put the kettle on.",
        "Everything out here is asleep except the two of you.",
        "A heart with a heart inside it, and all the evenings still to come."
    };

    private static final String[][] ART = {
        // ---- 1 First Heart (5x5)
        //
        // A heart that is actually symmetric. The taper used to be columns 2-3 and then
        // column 3, so the point leaned a square left of centre and the column clues read
        // 2,4,4,3,2 instead of 2,4,4,4,2 - which is a lopsided heart, drawn at the largest
        // squares the game makes, as the very first thing a pair finish together. It was
        // bent to clear chaptersAreNotDeckBoards; the deck's own 5x5 heart has been given
        // the deeper cleft instead, because the test should move the generated board rather
        // than the hand-drawn one.
        {".#.#.",
         "#####",
         "#####",
         ".###.",
         "..#.."},
        // ---- 2 Candlelight (5x5)
        {".#...",
         ".###.",
         "..#..",
         ".###.",
         "#####"},
        // ---- 3 Lucky Star (5x5)
        {"..#..",
         "#####",
         "#####",
         ".###.",
         ".#.#."},
        // ---- 4 Moon Kiss (5x5)
        //
        // A crescent with two horns, which is what the name promised. It used to be
        // "..### / .##.. / ###.. / .##.. / ..###" - a left-pointing wedge with two arms off
        // the right-hand side, which reads as a bracket and not as a moon, and certainly not
        // as a kiss. The limb is solid down the left, the two arms sweep right along the top
        // and the bottom, and each one curls back inward at its tip - so the shape closes
        // the way a crescent does and leaves the moon's dark side open on the right.
        {".####",
         "##..#",
         "##...",
         "##..#",
         ".####"},
        // ---- 5 Tiny Tulip (7x7)
        {".#.#.#.",
         "##.#.##",
         "#######",
         ".#####.",
         "..###..",
         "...#...",
         "..###.."},
        // ---- 6 Warm Cocoa (7x7)
        {"..#.#..",
         "..#.#..",
         "#######",
         "#....##",
         "#....#.",
         "#....##",
         ".#####."},
        // ---- 7 Little Home (7x7)
        {"...#...",
         "..###..",
         ".#.#.#.",
         "#######",
         "#.###.#",
         "#.#.#.#",
         "###.###"},
        // ---- 8 Love Letter (10x10)
        {"##########",
         "#........#",
         "##......##",
         "#.##..##.#",
         "#..####..#",
         "#........#",
         "#.##..##.#",
         "#.######.#",
         "#..####..#",
         "##########"},
        // ---- 9 Love Birds (10x10)
        {"...##.##..",
         "...#####..",
         "....###...",
         ".....#....",
         ".###..###.",
         "####..####",
         ".###..###.",
         "..#....#..",
         "##########",
         "..##..##.."},
        // ---- 10 Sleepy Cat (10x10)
        {".#......#.",
         "###....###",
         ".########.",
         "##########",
         "##.####.##",
         "##########",
         ".###..###.",
         ".########.",
         "..######..",
         "..##..##.."},
        // ---- 11 Garden Rose (10x10)
        {"..######..",
         ".########.",
         "##.####.##",
         "##.#..#.##",
         "##.####.##",
         ".########.",
         "..######..",
         "....##....",
         ".########.",
         "....##...."},
        // ---- 12 Rainy Window (10x10)
        {".########.",
         "#.#.##...#",
         "#.#.##...#",
         "#...##...#",
         "##########",
         "#...##...#",
         "#...##.#.#",
         "#...##.#.#",
         "##########",
         "##########"},
        // ---- 13 Knitted Sweater (10x10)
        {"###....###",
         "##########",
         "##########",
         "##########",
         "..######..",
         "..######..",
         "..#.##.#..",
         "..######..",
         "..#.##.#..",
         "..######.."},
        // ---- 14 Paper Lantern (12x12)
        {".....##.....",
         ".....##.....",
         "..########..",
         ".##.####.##.",
         "###.####.###",
         "###.####.###",
         "###.####.###",
         "###.####.###",
         ".##.####.##.",
         "..########..",
         ".....##.....",
         ".....##....."},
        // ---- 15 Fresh Baked Pie (12x12)
        {"...#....#...",
         "...#....#...",
         "..########..",
         ".##.##.##.#.",
         "###.##.##.##",
         "###.##.##.##",
         "############",
         "############",
         ".##########.",
         "..########..",
         "...######...",
         "....####...."},
        // ---- 16 Little Owl (12x12)
        {".##......##.",
         ".###....###.",
         "############",
         "##.##..##.##",
         "##.##..##.##",
         "############",
         ".####..####.",
         ".##########.",
         ".##########.",
         ".####..####.",
         "..########..",
         "...##..##..."},
        // ---- 17 Autumn Leaf (12x12)
        {".....##.....",
         "....####....",
         "...##..##...",
         "..###..###..",
         ".####..####.",
         "#####..#####",
         "#####..#####",
         ".####..####.",
         "..###..###..",
         "...#####....",
         ".....##.....",
         ".....##....."},
        // ---- 18 Dancing Hearts (15x15)
        {".##..##........",
         "########.......",
         "########.......",
         "########.......",
         ".######........",
         "..####..##..##.",
         "..####.########",
         "...##..########",
         "...##..########",
         "........######.",
         "........######.",
         ".........####..",
         ".........####..",
         "..........##...",
         "..........##..."},
        // ---- 19 Two Mugs (15x15)
        {"..#.......#....",
         "..##......##...",
         "..#.......#....",
         ".####....####..",
         ".######..######",
         ".####.#..####.#",
         ".######..######",
         ".####....####..",
         ".####....####..",
         ".####....####..",
         "..###.....###..",
         "###############",
         "###############",
         "...#########...",
         ".....#####....."},
        // ---- 20 Cat and Yarn (15x15)
        {".##...##.......",
         ".#######.......",
         ".#######.......",
         ".#.###.#.......",
         ".#######.......",
         "..#####........",
         "..######.......",
         "..#######......",
         "..########.....",
         "..######..####.",
         "..######.######",
         "..######.######",
         "..######.######",
         ".########.####.",
         "###############"},
        // ---- 21 Bedtime Story (15x15)
        {"......#.#......",
         "....###.###....",
         "..#####.#####..",
         "###############",
         "##...##.##...##",
         "##...##.##...##",
         "###############",
         "###############",
         "##...##.##...##",
         "##...##.##...##",
         "###############",
         "###############",
         "##...##.##...##",
         ".#####...#####.",
         "...###...###..."},
        // ---- 22 Cozy Cottage (15x15)
        {".........##....",
         "........##.....",
         ".........##....",
         "......#..##....",
         ".....###.##....",
         "....#######....",
         "...###########.",
         "..#############",
         "###############",
         "##...#####...##",
         "##...#####...##",
         "##...#####...##",
         "###############",
         "######...######",
         "######...######"},
        // ---- 23 Moonlit Garden (20x20)
        {"....######..........",
         "...###..###.........",
         "..###.....##...#....",
         "..##.......#..###...",
         ".###...........#....",
         ".###................",
         ".###................",
         ".###................",
         ".###................",
         ".###................",
         "..##.......#........",
         "..###.....##........",
         "...###..###.........",
         "....######..........",
         "......##......##....",
         ".....####....####...",
         "......##......##....",
         ".......#......#.....",
         "####################",
         "####################"},
        // ---- 24 Forever (20x20)
        {"...####......####...",
         "..######....######..",
         ".########..########.",
         "####################",
         "####################",
         "######..####..######",
         "#####..........#####",
         ".####..........####.",
         ".#####........#####.",
         "..#####......#####..",
         "..######....######..",
         "...######..######...",
         "....############....",
         ".....##########.....",
         "......########......",
         ".......######.......",
         "........####........",
         "........####........",
         ".........##.........",
         ".........##........."},
    };

    private PuzzleLibrary() {
    }

    public static int count() {
        return ART.length;
    }

    /** The chapter's one line of copy, wrapping the same way {@link #get} does. */
    public static String line(int index) {
        return LINES[Math.floorMod(index, LINES.length)];
    }

    public static Puzzle get(int index) {
        int chapter = Math.floorMod(index, ART.length);
        String[] rows = ART[chapter];
        boolean[][] cells = new boolean[rows.length][rows.length];
        for (int y = 0; y < rows.length; y++) {
            if (rows[y].length() != rows.length) {
                throw new IllegalStateException("Non-square story art " + chapter);
            }
            for (int x = 0; x < rows.length; x++) {
                cells[y][x] = rows[y].charAt(x) == '#';
            }
        }
        return new Puzzle(cells, NAMES[chapter]);
    }
}
