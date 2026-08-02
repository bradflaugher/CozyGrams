package com.cozygrams.tv;

/**
 * The Story Book: a lovingly authored progression, drawn square by square.
 *
 * <p>The chapters are a journey. The first eight are 5x5 - one warm little idea each, a
 * few minutes together on the sofa. The middle seven open out to 10x10, where a picture
 * can have a door and two windows, or two birds and the branch they share. The last
 * three are 15x15 and take their time: two mugs steaming side by side, a cat with its
 * ball of yarn, and a heart with a heart inside it.
 *
 * <p>Every chapter is checked by {@link NonogramSolver} in the unit tests: each one can
 * be finished by logic alone, with no guessing and exactly one answer.
 */
public final class PuzzleLibrary {

    private static final String[] NAMES = {
        "First Heart", "Love Letter", "Candlelight", "Tiny Tulip",
        "Warm Cocoa", "Little Home", "Lucky Star", "Moon Kiss",
        "Love Birds", "Sleepy Cat", "Garden Rose", "Tea for Two",
        "Cozy Cottage", "Rainy Window", "Dancing Hearts",
        "Two Mugs", "Cat and Yarn", "Forever"
    };

    private static final String[][] ART = {
        // ---- Chapter one: five squares by five, and a whole feeling ----
        {".#.#.",
         "#####",
         "#####",
         ".###.",
         "..#.."},
        {".....",
         "#####",
         "##.##",
         "#####",
         "....."},
        {"..#..",
         "..#..",
         ".###.",
         ".###.",
         "#####"},
        {".#.#.",
         "#####",
         ".###.",
         "..#..",
         ".###."},
        {".....",
         "####.",
         "###.#",
         "#####",
         ".###."},
        {"..#..",
         ".###.",
         "#####",
         "#.#.#",
         "##.##"},
        {"..#..",
         ".###.",
         "#####",
         ".###.",
         ".#.#."},
        {"..##.",
         ".###.",
         "##...",
         ".###.",
         "..##."},

        // ---- The book opens out ----
        {"...##.##..",
         "...#####..",
         "....###...",
         ".....#....",
         ".###..###.",
         "####..####",
         ".###..###.",
         "..#....#..",
         "##########",
         ".........."},
        {".#......#.",
         "###....###",
         ".########.",
         "##########",
         "##.####.##",
         "##########",
         ".###..###.",
         ".########.",
         "..######..",
         ".........."},
        {"..######..",
         ".########.",
         "##.####.##",
         "##.#..#.##",
         "##.####.##",
         ".########.",
         "..######..",
         "....##....",
         ".###..###.",
         "....##...."},
        {"...####...",
         "..######..",
         ".########.",
         "##########",
         "##########",
         ".########.",
         "..######..",
         "..........",
         ".###..###.",
         ".###..###."},
        {"....##.##.",
         "...######.",
         "..######..",
         ".########.",
         "##########",
         ".########.",
         ".#..##..#.",
         ".########.",
         ".###..###.",
         ".###..###."},
        {"##########",
         "#.#.##...#",
         "#.#.##...#",
         "#...##...#",
         "##########",
         "#...##...#",
         "#...##.#.#",
         "#...##.#.#",
         "##########",
         "##########"},
        {"..........",
         ".#.#......",
         "#####.....",
         "#####.....",
         ".###.##.##",
         "..#.######",
         "....######",
         ".....####.",
         "......##..",
         ".........."},

        // ---- The long evenings ----
        {"...............",
         "..#.........#..",
         "..#.#.....#.#..",
         "..#.#.....#.#..",
         "...............",
         "#####.....#####",
         "#####.....#####",
         "#######.#######",
         "#####.#.#.#####",
         "#######.#######",
         "#####.....#####",
         "#####.....#####",
         "#####.....#####",
         "######...######",
         "..............."},
        {"...............",
         ".#...#.........",
         ".#####.........",
         ".#####.........",
         ".#.#.#.........",
         ".#####.........",
         "..###..........",
         "..####.........",
         "..#####........",
         "..######.......",
         "..######..##...",
         "..######.####..",
         "..######.####..",
         ".########.##...",
         "..............."},
        {"...............",
         "..###.....###..",
         ".#####...#####.",
         "###############",
         "######.#.######",
         "#####.....#####",
         "######...######",
         ".######.######.",
         "..###########..",
         "...#########...",
         "....#######....",
         ".....#####.....",
         "......###......",
         ".......#.......",
         "..............."}
    };

    private PuzzleLibrary() {
    }

    public static int count() {
        return ART.length;
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
